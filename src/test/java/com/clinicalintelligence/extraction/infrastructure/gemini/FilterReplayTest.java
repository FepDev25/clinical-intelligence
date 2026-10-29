package com.clinicalintelligence.extraction.infrastructure.gemini;
import static org.junit.jupiter.api.Assertions.*;
import static com.clinicalintelligence.extraction.infrastructure.gemini.GeminiProtocolTest.*;
import org.junit.jupiter.api.Test;
class FilterReplayTest {
    @Test void rawAndSanitizedRemainSeparateAndNoGoldIsConsulted() throws Exception {
        String n="El paciente niega asma.";
        var raw=JSON.readTree(proposal("asma",0));
        var a=(com.fasterxml.jackson.databind.node.ObjectNode)raw.at("/diagnoses/0/context/assertion");a.put("value","affirmed");a.withArray("evidence").addObject().put("quote",n).put("occurrence",0);
        var input=JSON.createObjectNode().put("structuredProposalText",raw.toString()).put("model","gemini-3.8-flash").put("promptVersion","extraction-gemini-v3-t7").put("filterVersion","evidence-4");input.putObject("request").put("clinicalNote",n);
        var out=FilterReplayMain.replay(input,JSON);
        assertEquals("affirmed",out.at("/rawBody/diagnoses/0/context/assertion/value").asText());
        assertEquals("unknown",out.at("/body/diagnoses/0/context/assertion/value").asText());
        assertEquals("simulated",out.at("/body/processing/executionMode").asText());
        assertFalse(out.at("/trace/inferenceExecuted").asBoolean());assertEquals(1,out.at("/trace/mentionDecisions").size());
    }
    @Test void wrongFilterCannotMasqueradeAsBaseline() throws Exception {
        var input=JSON.createObjectNode().put("structuredProposalText",proposal("asma",0)).put("model","gemini-3.8-flash").put("promptVersion","extraction-gemini-v2").put("filterVersion","evidence-2");input.putObject("request").put("clinicalNote","asma");
        assertThrows(IllegalStateException.class,() -> FilterReplayMain.replay(input,JSON));
    }
    @Test void batchReplayCarriesStableIdentifiersAndMeasuresFilterTime() throws Exception {
        var input=JSON.createObjectNode().put("generationId","r1:case:v2-low").put("caseId","case").put("round",1).put("armId","filter-only").put("structuredProposalText",proposal("asma",0)).put("model","gemini-3.8-flash").put("promptVersion","extraction-gemini-v2").put("filterVersion","evidence-4");input.putObject("request").put("clinicalNote","asma");
        var out=FilterReplayMain.batch(JSON.createArrayNode().add(input),JSON);
        assertEquals("r1:case:v2-low",out.get(0).path("generationId").asText());assertEquals("filter-only",out.get(0).path("armId").asText());assertTrue(out.get(0).path("filterLatencyMillis").isNumber());
    }
    @Test void invalidProviderNoticeIsARecordedFailureAndDoesNotDestroyTheWholeBatch() throws Exception {
        var bad=JSON.readTree(proposal("asma",0));
        var notice=((com.fasterxml.jackson.databind.node.ArrayNode)bad.path("notices")).addObject().put("code","AMBIGUOUS_CONTEXT");
        notice.putArray("targets").addObject().put("kind","diagnosis").put("index",0).put("field","dosage");
        var input=JSON.createObjectNode().put("generationId","r1:x:c").put("caseId","x").put("round",1).put("armId","a").put("structuredProposalText",bad.toString()).put("model","gemini-3.8-flash").put("promptVersion","extraction-gemini-v2").put("filterVersion","evidence-4");input.putObject("request").put("clinicalNote","asma");
        var good=input.deepCopy().put("generationId","r1:y:c").put("caseId","y").put("structuredProposalText",proposal("asma",0));
        var out=FilterReplayMain.batch(JSON.createArrayNode().add(input).add(good),JSON);
        assertEquals(2,out.size());assertEquals(502,out.get(0).path("status").asInt());assertEquals(200,out.get(1).path("status").asInt());
    }
}
