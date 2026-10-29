package com.clinicalintelligence.extraction.infrastructure.gemini;
import static org.junit.jupiter.api.Assertions.*;
import static com.clinicalintelligence.extraction.infrastructure.gemini.GeminiGeneration.*;
import com.clinicalintelligence.extraction.domain.ClinicalModel.ProviderRequest;
import java.util.*;
import org.junit.jupiter.api.Test;
class GeminiGenerationTest {
    @Test void eachLevelAndSinglePassPromptHaveExplicitRequestAndIdentity() {
        for(var level:ThinkingLevel.values())for(var prompt:List.of(Prompt.V2,Prompt.V3)) {
            var config=from(Map.of("model","gemini-3.8-flash","thinkingLevel",level.name(),"promptVersion",prompt.version()));
            var protocol=new GeminiProtocol(config);var request=protocol.request(new ProviderRequest("nota sintética","es"),65536);
            assertEquals(level.name(),request.at("/generationConfig/thinkingConfig/thinkingLevel").asText());
            assertEquals(prompt.version(),protocol.identity().promptVersion());assertEquals(level.name(),protocol.identity().thinkingLevel());
            assertTrue(protocol.identity().promptSha256().matches("[a-f0-9]{64}"));assertTrue(protocol.identity().schemaSha256().matches("[a-f0-9]{64}"));
        }
    }
    @Test void rejectsUnsupportedModelsLevelsKeysAndNonTextValues() {
        var base=Map.<String,Object>of("model","gemini-3.8-flash","thinkingLevel","LOW","promptVersion",Prompt.V2.version());
        for(String field:List.of("temperature","candidateCount","thinkingBudget","includeThoughts","seed","arbitrary")) {
            var invalid=new HashMap<>(base);invalid.put(field,1);assertThrows(IllegalArgumentException.class,() -> from(invalid));
        }
        for(var change:List.of(Map.entry("model","unverified-model"),Map.entry("thinkingLevel","MINIMAL"),Map.entry("thinkingLevel","AUTO"),Map.entry("promptVersion","external-file"))) {
            var invalid=new HashMap<>(base);invalid.put(change.getKey(),change.getValue());assertThrows(IllegalArgumentException.class,() -> from(invalid));
        }
        var invalid=new HashMap<>(base);invalid.put("thinkingLevel",1);assertThrows(IllegalArgumentException.class,() -> from(invalid));
        assertThrows(IllegalArgumentException.class,() -> from(Map.of()));assertThrows(IllegalArgumentException.class,() -> from(null));
    }
    @Test void productionIsV2LowAndDifferentIdentitiesCannotMasqueradeAsIt() {
        assertEquals(new GeminiGeneration("gemini-3.8-flash",ThinkingLevel.LOW,Prompt.V2),production());
        var old=new GeminiProtocol();var candidate=new GeminiProtocol(new GeminiGeneration("gemini-3.8-flash",ThinkingLevel.LOW,Prompt.V3));
        assertNotEquals(old.identity().promptSha256(),candidate.identity().promptSha256());
        assertEquals(old.identity().schemaSha256(),candidate.identity().schemaSha256());
        assertEquals("gemini-generate-content-t7-1",old.identity().requestVersion());
    }
}
