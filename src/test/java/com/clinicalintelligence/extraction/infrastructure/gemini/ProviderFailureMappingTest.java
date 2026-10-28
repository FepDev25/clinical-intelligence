package com.clinicalintelligence.extraction.infrastructure.gemini;

import com.clinicalintelligence.extraction.application.*;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ProviderFailureMappingTest {
    @Test void preservesDistinctOperationalFailuresThroughTheUseCase() {
        for(var pair:new String[][]{{"TIMEOUT","PROVIDER_TIMEOUT"},{"UNAVAILABLE","PROVIDER_UNAVAILABLE"},
                {"CAPACITY_EXCEEDED","PROVIDER_CAPACITY_EXCEEDED"},{"OVERLOADED","SERVICE_OVERLOADED"},
                {"INVALID_RESPONSE","PROVIDER_INVALID_RESPONSE"},{"TRUNCATED_RESPONSE","PROVIDER_TRUNCATED_RESPONSE"}}) {
            ExtractionProvider provider=request->{throw new ExtractionProvider.ProviderFailure(ExtractionProvider.Failure.valueOf(pair[0]));};
            var service=new ExtractionService(provider);
            assertEquals(pair[1],assertThrows(ExtractionException.class,()->service.extract(Command.note("nota"))).code().name());
        }
    }
    @Test void usageCostDistinguishesUnknownFromZeroAndIncludesThinking() {
        var date=java.time.LocalDate.of(2026,10,27);
        assertEquals("0.0001875",GeminiProvider.estimate(new GeminiProtocol.Usage(100L,20L,10L,0L),date));
        assertNull(GeminiProvider.estimate(new GeminiProtocol.Usage(100L,20L,null,0L),date));
        assertEquals("0",GeminiProvider.estimate(new GeminiProtocol.Usage(0L,0L,0L,0L),date));
        assertNull(GeminiProvider.estimate(new GeminiProtocol.Usage(100L,20L,0L,0L),java.time.LocalDate.of(2027,1,1)));
    }
}
