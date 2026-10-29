package com.clinicalintelligence.extraction.infrastructure.gemini;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static com.clinicalintelligence.extraction.infrastructure.gemini.GeminiProtocolTest.*;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
class GeminiTwoStageTest {
    static final String EMPTY="{\"diagnoses\":[],\"medications\":[],\"procedures\":[],\"notices\":[]}";
    @Test void classificationReceivesOriginalUnicodeNoteAndLockedAnchorsAndReturnsPublicContract() throws Exception {
        var bodies=new ArrayList<JsonNode>();
        var workflow=new GeminiTwoStageExperiment(GeminiGeneration.ThinkingLevel.LOW);
        String note="🩺 asma\r\nasma";
        var out=workflow.run(new ProviderRequest(note,"es"),65536,(protocol,body) -> {bodies.add(body.deepCopy());return envelope(proposal("asma",1),"STOP");});
        assertEquals(2,bodies.size());var data=JSON.readTree(bodies.get(1).at("/contents/0/parts/0/text").asText());
        assertEquals(note,data.path("clinicalNote").asText());assertEquals(8,data.at("/detectedMentions/0/name/evidence/0/start").asInt());
        assertEquals("classification-gemini-v1-t7",out.stages().get(1).identity().promptVersion());
        assertEquals(200L,out.inputTokens());assertEquals(60L,out.outputAndThoughtTokens());
        var result=new com.clinicalintelligence.extraction.application.ExtractionService(ignored -> out.response()).extract(Command.note(note));
        assertEquals(1,result.diagnoses().size());assertEquals(8,result.diagnoses().getFirst().mention().name().evidence().getFirst().start());
    }
    @Test void emptyDetectionSkipsClassificationWithoutInventingSuccessFromAFailure() throws Exception {
        var out=new GeminiTwoStageExperiment(GeminiGeneration.ThinkingLevel.LOW).run(new ProviderRequest("nota sin entidades","es"),65536,(p,b) -> envelope(EMPTY,"STOP"));
        assertEquals(1,out.stages().size());assertTrue(out.response().proposal().mentions().isEmpty());
        assertTrue(out.response().metadata().promptVersion().contains("detection"));
    }
    @Test void malformedOrTruncatedDetectionNeverCallsClassification() {
        for(String finish:List.of("MAX_TOKENS","MALFORMED")) {
            int[] calls={0};assertThrows(IllegalArgumentException.class,() -> new GeminiTwoStageExperiment(GeminiGeneration.ThinkingLevel.LOW).run(new ProviderRequest("asma","es"),65536,(p,b) -> {calls[0]++;return envelope("{",finish);}));
            assertEquals(1,calls[0]);
        }
    }
    @Test void detectionCannotLeakClassificationOrInventAnAnchor() {
        for(String raw:List.of(proposal("not-present",0),proposal("asma",0).replace("\"assertion\":{\"value\":\"unknown\",\"evidence\":[]}","\"assertion\":{\"value\":\"affirmed\",\"evidence\":[]}")))
            assertThrows(IllegalArgumentException.class,() -> new GeminiTwoStageExperiment(GeminiGeneration.ThinkingLevel.LOW).run(new ProviderRequest("asma","es"),65536,(p,b) -> envelope(raw,"STOP")));
    }
    @Test void classificationCannotDropDuplicateOrMoveTheDetectedMention() throws Exception {
        var duplicated=JSON.readTree(proposal("asma",0));
        ((com.fasterxml.jackson.databind.node.ArrayNode)duplicated.path("diagnoses")).add(duplicated.path("diagnoses").get(0).deepCopy());
        for(String second:List.of(EMPTY,proposal("asma",1),duplicated.toString())) {
            int[] calls={0};assertThrows(IllegalArgumentException.class,() -> new GeminiTwoStageExperiment(GeminiGeneration.ThinkingLevel.LOW).run(new ProviderRequest("asma asma","es"),65536,(p,b) -> envelope(calls[0]++==0?proposal("asma",0):second,"STOP")));
        }
    }
    @Test void classificationProtocolCannotRunWithoutDetectedMentions() {
        var p=new GeminiProtocol(new GeminiGeneration(GeminiProtocol.MODEL,GeminiGeneration.ThinkingLevel.LOW,GeminiGeneration.Prompt.CLASSIFICATION));
        assertThrows(IllegalArgumentException.class,() -> p.request(new ProviderRequest("asma","es"),65536));
    }
    @Test void failedSecondStagePreservesUsageAndBothAttemptsWithoutPartialSuccess() {
        int[] calls={0};
        var failure=assertThrows(GeminiTwoStageExperiment.StageFailure.class,() -> new GeminiTwoStageExperiment(GeminiGeneration.ThinkingLevel.LOW).run(new ProviderRequest("asma","es"),65536,(p,b) -> envelope(calls[0]++==0?proposal("asma",0):"{",calls[0]==1?"STOP":"MAX_TOKENS")));
        assertEquals(2,failure.stages().size());assertEquals(Completion.TRUNCATED,failure.stages().get(1).completion());
        assertEquals(100L,failure.stages().get(1).usage().inputTokens());assertNull(failure.getCause());
    }
    @Test void validGroupingCanKeepBothDetectedAnchors() throws Exception {
        var detection=JSON.readTree(proposal("asma",0));var second=JSON.readTree(proposal("asma",1)).path("diagnoses").get(0);
        ((com.fasterxml.jackson.databind.node.ArrayNode)detection.path("diagnoses")).add(second);
        var classified=JSON.readTree(proposal("asma",0));
        ((com.fasterxml.jackson.databind.node.ArrayNode)classified.at("/diagnoses/0/name/evidence")).add(second.at("/name/evidence/0"));
        int[] calls={0};var out=new GeminiTwoStageExperiment(GeminiGeneration.ThinkingLevel.LOW).run(new ProviderRequest("asma asma","es"),65536,(p,b) -> envelope(calls[0]++==0?detection.toString():classified.toString(),"STOP"));
        assertEquals(1,out.response().proposal().mentions().size());assertEquals(2,out.response().proposal().mentions().getFirst().name().evidence().size());
    }
}
