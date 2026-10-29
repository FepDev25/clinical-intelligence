package com.clinicalintelligence.extraction.infrastructure.gemini;
import static org.junit.jupiter.api.Assertions.*;
import static com.clinicalintelligence.extraction.infrastructure.gemini.GeminiProtocolTest.*;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
class CausalStudyEngineTest {
    JsonNode unit(){return JSON.createObjectNode().put("generationId","r1:case:v2-low").put("caseId","case").put("round",1).put("configuration","v2-low");}
    JsonNode request(){return JSON.createObjectNode().put("clinicalNote","🩺 asma\r\nasma").put("locale","es");}
    @Test void singlePassAndTwoStageUseExactFrozenConfigurationAndOriginalNote() throws Exception {
        for(String workflow:java.util.List.of("single","two-stage")) {
            var config=JSON.createObjectNode().put("workflow",workflow).put("model","gemini-3.8-flash").put("promptVersion","extraction-gemini-v2").put("thinkingLevel","LOW");
            int[] calls={0};
            var result=CausalStudyEngine.runUnit(unit(),config,request(),false,(p,input,body,end,id) -> {calls[0]++;assertEquals(request().path("clinicalNote").asText(),input.clinicalNote());
                var decoded=p.decode(input.clinicalNote(),envelope(proposal("asma",1),"STOP"));return new StudyTransport.Reply(decoded,proposal("asma",1),decoded.modelVersion(),new BigDecimal("0.001"));});
            assertEquals(workflow.equals("single")?1:2,calls[0]);assertEquals("complete",result.path("status").asText());assertFalse(result.path("inferenceExecuted").asBoolean());
        }
    }
    @Test void detectionFailureNeverInvokesClassificationOrReturnsAnEmptySuccess() throws Exception {
        var config=JSON.createObjectNode().put("workflow","two-stage").put("model","gemini-3.8-flash").put("thinkingLevel","LOW");int[] calls={0};
        var result=CausalStudyEngine.runUnit(unit(),config,request(),false,(p,i,b,e,id) -> {calls[0]++;return new StudyTransport.Reply(p.decode(i.clinicalNote(),envelope("{","MAX_TOKENS")),null,null,BigDecimal.ZERO);});
        assertEquals(1,calls[0]);assertEquals("stage_failed",result.path("status").asText());assertFalse(result.hasNonNull("structuredProposalText"));
    }
    @Test void incompleteClassificationIsNotRepairedOrRetried() throws Exception {
        var config=JSON.createObjectNode().put("workflow","two-stage").put("model","gemini-3.8-flash").put("thinkingLevel","LOW");int[] calls={0};
        var result=CausalStudyEngine.runUnit(unit(),config,request(),false,(p,i,b,e,id) -> {String raw=calls[0]++==0?proposal("asma",1):GeminiTwoStageTest.EMPTY;return new StudyTransport.Reply(p.decode(i.clinicalNote(),envelope(raw,"STOP")),raw,null,BigDecimal.ZERO);});
        assertEquals(2,calls[0]);assertEquals("stage_failed",result.path("status").asText());
    }
}
