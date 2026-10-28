package com.clinicalintelligence.extraction.application;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static org.junit.jupiter.api.Assertions.*;

import com.clinicalintelligence.extraction.infrastructure.ScriptedExtractionProvider;
import com.clinicalintelligence.extraction.testing.DevelopmentCorpus;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class DevelopmentCorpusTest {
    @TestFactory Stream<DynamicTest> preservesSupportedDevelopmentProposalsAndResolvesCatalogs() throws IOException {
        return DevelopmentCorpus.load().stream().map(fixture -> DynamicTest.dynamicTest(fixture.id(), () -> {
            // Gold is explicitly injected as a controlled provider proposal, not
            // looked up by runtime code and not presented as LLM extraction quality.
            var provider = new ScriptedExtractionProvider(request -> {
                assertEquals(fixture.command().clinicalNote(), request.clinicalNote());
                return fixture.proposal();
            });
            var result = new ExtractionService(provider).extract(fixture.command());
            assertEquals(fixture.proposal().mentions().size(), result.findings().size());
            for (int i = 0; i < result.findings().size(); i++) {
                assertEquals(fixture.proposal().mentions().get(i), result.findings().get(i).mention(), "Supported attributes/context must survive");
                assertEquals(fixture.matches().get(i), result.findings().get(i).catalogMatch(), "Resolver must use input catalog only");
            }
            assertEquals(fixture.requiredWarnings(), result.warnings());
            assertEquals(fixture.command().locale() == null ? "es" : fixture.command().locale(), result.locale());
            assertTrue(result.reviewRequired());
            assertEquals(ExecutionMode.SIMULATED, result.processing().executionMode());
            assertEquals(result.findings().size(), result.findings().stream().map(Accepted::id).distinct().count());
        }));
    }

    @Test void reservedAnnotationsAreNotOnTheTestClasspath() {
        assertNull(getClass().getResource("/corpus/reserved.json"));
        assertNotNull(getClass().getResource("/corpus/development.json"));
    }
}
