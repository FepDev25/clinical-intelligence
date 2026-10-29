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
    @TestFactory Stream<DynamicTest> preservesDevelopmentExceptDocumentedRetrospectiveSubjectRejection() throws IOException {
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
                Mention expected = fixture.proposal().mentions().get(i);
                // T2 R7 is still disputed: T6 rejects a subject supported only retrospectively
                // from the laberintitis sentence. This is an output expectation, not a gold edit.
                if (fixture.id().equals("dev-consultation-02") && i == 0) {
                    Diagnosis d = (Diagnosis) expected;
                    assertEquals(Subject.PATIENT, d.context().subject().value());
                    expected = new Diagnosis(d.name(), d.mentionedCode(), new Context(d.context().assertion(),
                            d.context().temporality(), new Grounded<>(Subject.UNKNOWN, java.util.List.of())));
                }
                assertEquals(expected, result.findings().get(i).mention(), "Other development fields must survive");
                assertEquals(fixture.matches().get(i), result.findings().get(i).catalogMatch(), "Resolver must use input catalog only");
            }
            var expectedWarnings = new java.util.ArrayList<>(fixture.requiredWarnings());
            if (fixture.id().equals("dev-consultation-02")) expectedWarnings.add(new Warning(WarningCode.ATTRIBUTE_DISCARDED,
                    java.util.List.of("/diagnoses/0/context/subject"), "/clinicalNote"));
            assertEquals(expectedWarnings, result.warnings());
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
