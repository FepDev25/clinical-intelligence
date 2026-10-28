package com.clinicalintelligence.extraction.application;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static com.clinicalintelligence.extraction.application.ExtractionException.Code.*;
import static com.clinicalintelligence.extraction.testing.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.clinicalintelligence.extraction.infrastructure.ScriptedExtractionProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ExtractionServiceTest {
    private ExtractionService service(Proposal proposal) { return new ExtractionService(new ScriptedExtractionProvider(ignored -> proposal)); }

    @Test void orchestratesAllKindsAndResolvesOnlyClientSuppliedCatalog() {
        var catalog = new Catalog("client", "2026", Kind.MEDICATION, List.of(new CatalogEntry("client-id", "ALFA", List.of())));
        var result = service(basic(NOTE)).extract(new Command(NOTE, "es-EC", List.of(catalog)));
        assertEquals(3, result.findings().size());
        assertEquals("asma", result.diagnoses().getFirst().mention().name().value());
        assertEquals(List.of(new Candidate("client", "2026", "client-id")), result.medications().getFirst().catalogMatch().candidates());
        assertEquals(PerformanceStatus.ORDERED, ((Procedure) result.procedures().getFirst().mention()).performanceStatus().value());
        assertEquals("es-EC", result.locale());
        assertTrue(result.reviewRequired());
        assertEquals("completed", result.processingStatus());
        assertEquals(ExecutionMode.SIMULATED, result.processing().executionMode());
        assertEquals("script-v1", result.processing().promptVersion());
    }

    @Test void providerReceivesOriginalNoteAndLocaleButNoCatalogOrTenant() {
        var provider = new ScriptedExtractionProvider(request -> {
            assertEquals("  " + NOTE + "\r\n", request.clinicalNote());
            assertEquals("ES-ec", request.locale());
            return basic(request.clinicalNote());
        });
        var result = new ExtractionService(provider).extract(new Command("  " + NOTE + "\r\n", "ES-ec", List.of()));
        assertEquals(3, result.findings().size());
        assertEquals("ES-ec", result.locale());
    }

    @Test void discardsUnsupportedEntityButKeepsOtherFindings() {
        var invalid = new Diagnosis(new Text("inventado", List.of(span(NOTE, "asma"))), null, diagnosis(NOTE).context());
        var result = service(new Proposal(List.of(invalid, medication(NOTE)), List.of())).extract(Command.note(NOTE));
        assertEquals(1, result.findings().size());
        assertTrue(result.diagnoses().isEmpty());
        assertEquals("Alfa", result.medications().getFirst().mention().name().value());
        assertEquals(WarningCode.ENTITY_DISCARDED, result.warnings().getFirst().code());
        assertEquals(List.of(), result.warnings().getFirst().targets());
        assertEquals("/clinicalNote", result.warnings().getFirst().source());
    }

    @Test void removesUnbackedAttributeWithoutRemovingMedicationOrInventingValues() {
        var m = medication(NOTE);
        var invalid = new Medication(m.name(), new Text("J99", List.of(span(NOTE, "asma"))), m.context(), m.prescriptionStatus(),
                m.dosage(), null, null, new Text("7 días", List.of(span(NOTE, "5 mg"))));
        var result = service(new Proposal(List.of(invalid), List.of())).extract(Command.note(NOTE));
        assertEquals(1, result.medications().size());
        var accepted = (Medication) result.medications().getFirst().mention();
        assertEquals("5 mg", accepted.dosage().value());
        assertNull(accepted.duration());
        assertNull(accepted.frequency());
        assertNull(accepted.mentionedCode());
        assertEquals(Set.of("/medications/0/mentionedCode", "/medications/0/duration"),
                Set.copyOf(result.warnings().stream().flatMap(w -> w.targets().stream()).toList()));
        assertEquals("completed_with_warnings", result.processingStatus());
    }

    @Test void invalidKnownContextBecomesUnknownAndNeverDefaultsToAffirmed() {
        var d = diagnosis(NOTE);
        var context = new Context(new Grounded<>(Assertion.AFFIRMED, List.of(new Evidence(1, 3, "not present"))), d.context().temporality(), d.context().subject());
        var result = service(new Proposal(List.of(new Diagnosis(d.name(), null, context)), List.of())).extract(Command.note(NOTE));
        assertEquals(1, result.diagnoses().size());
        assertEquals(Assertion.UNKNOWN, result.diagnoses().getFirst().mention().context().assertion().value());
        assertTrue(result.diagnoses().getFirst().mention().context().assertion().evidence().isEmpty());
        assertEquals(List.of("/diagnoses/0/context/assertion"), result.warnings().getFirst().targets());
    }

    @Test void controlDurationIsNotAcceptedJustBecauseItsTextExistsInNote() {
        String note = NOTE + " Control en 7 días.";
        var m = medication(note);
        var proposed = new Medication(m.name(), null, m.context(), m.prescriptionStatus(), m.dosage(), null, null, text(note, "7 días"));
        var result = service(new Proposal(List.of(proposed), List.of())).extract(Command.note(note));
        assertEquals(1, result.medications().size());
        assertNull(((Medication) result.medications().getFirst().mention()).duration());
        assertEquals(List.of("/medications/0/duration"), result.warnings().getFirst().targets());
    }

    @Test void dosageEvidenceFromAnotherMedicationClauseIsDiscarded() {
        String note = NOTE + " Prescripción actual del paciente: Beta, dosis 9 mg.";
        var m = medication(note);
        var proposed = new Medication(m.name(), null, m.context(), m.prescriptionStatus(), text(note, "9 mg"), null, null, null);
        var result = service(new Proposal(List.of(proposed), List.of())).extract(Command.note(note));
        assertNull(((Medication) result.medications().getFirst().mention()).dosage());
        assertEquals(List.of("/medications/0/dosage"), result.warnings().getFirst().targets());
    }

    @Test void repeatedMedicationNameDoesNotAssociateDoseFromAnotherOccurrence() {
        String note = "😀 Alfa: se prescriben 5 mg. Alfa: se reportan 9 mg.";
        String second = "Alfa: se reportan 9 mg";
        var context = context(note, second);
        var proposed = new Medication(text(note, "Alfa"), null, context,
                grounded(PrescriptionStatus.REPORTED, note, second), text(note, "9 mg"), null, null, null);
        var result = service(new Proposal(List.of(proposed), List.of())).extract(Command.note(note));
        assertEquals("evidence-2", result.processing().validationVersion());
        assertNull(((Medication) result.medications().getFirst().mention()).dosage());
        assertTrue(result.warnings().stream().anyMatch(w -> w.targets().contains("/medications/0/dosage")));
    }

    @Test void doseForSecondMedicationOccurrenceRemainsAvailable() {
        String note = "😀 Alfa: se prescriben 5 mg. Alfa: se reportan 9 mg.";
        String second = "Alfa: se reportan 9 mg";
        var proposed = new Medication(new Text("Alfa", List.of(span(note, second))), null, context(note, second),
                grounded(PrescriptionStatus.REPORTED, note, second), text(note, "9 mg"), null, null, null);
        var result = service(new Proposal(List.of(proposed), List.of())).extract(Command.note(note));
        assertEquals("9 mg", ((Medication) result.medications().getFirst().mention()).dosage().value());
        assertTrue(result.warnings().isEmpty());
    }

    @Test void missingContextProducesExplicitUnknownFieldsAndWarnings() {
        var d = diagnosis(NOTE);
        var result = service(new Proposal(List.of(new Diagnosis(d.name(), null, null)), List.of())).extract(Command.note(NOTE));
        assertEquals(1, result.diagnoses().size());
        var context = result.diagnoses().getFirst().mention().context();
        assertEquals(Assertion.UNKNOWN, context.assertion().value());
        assertEquals(Temporality.UNKNOWN, context.temporality().value());
        assertEquals(Subject.UNKNOWN, context.subject().value());
        assertEquals(3, result.warnings().size());
    }

    @Test void legitimateUnknownAndAbsentAttributesDoNotProduceWarnings() {
        var unknown = new Context(new Grounded<>(Assertion.UNKNOWN, List.of()), new Grounded<>(Temporality.UNKNOWN, List.of()), new Grounded<>(Subject.UNKNOWN, List.of()));
        var m = new Medication(text(NOTE, "Alfa"), null, unknown, new Grounded<>(PrescriptionStatus.UNKNOWN, List.of()), null, null, null, null);
        var result = service(new Proposal(List.of(m), List.of())).extract(Command.note(NOTE));
        assertEquals(1, result.medications().size());
        assertEquals("completed", result.processingStatus());
        assertTrue(result.warnings().isEmpty());
    }

    @Test void distinguishesCleanEmptyFromAllDiscarded() {
        var clean = service(new Proposal(List.of(), List.of())).extract(Command.note(NOTE));
        assertEquals("completed", clean.processingStatus());
        assertTrue(clean.findings().isEmpty());
        var invalid = new Diagnosis(new Text("inventado", List.of()), null, null);
        var discarded = service(new Proposal(List.of(invalid), List.of())).extract(Command.note(NOTE));
        assertEquals("completed_with_warnings", discarded.processingStatus());
        assertTrue(discarded.findings().isEmpty());
        assertFalse(discarded.warnings().isEmpty());
    }

    @Test void truncationAndMalformedOutputAreFailuresNotEmptyResults() {
        for (Completion completion : List.of(Completion.TRUNCATED, Completion.MALFORMED)) {
            var service = new ExtractionService(ignored -> new ProviderResponse(completion, new Proposal(List.of(), List.of()), metadata()));
            var code = completion == Completion.TRUNCATED ? PROVIDER_TRUNCATED_RESPONSE : PROVIDER_INVALID_RESPONSE;
            assertEquals(code, assertThrows(ExtractionException.class, () -> service.extract(Command.note(NOTE))).code());
        }
    }

    @Test void nullOrIncompleteEnvelopeIsAControlledProviderFailure() {
        var invalid = new ArrayList<ProviderResponse>();
        invalid.add(null);
        invalid.add(new ProviderResponse(null, new Proposal(List.of(), List.of()), metadata()));
        invalid.add(new ProviderResponse(Completion.COMPLETE, null, metadata()));
        invalid.add(new ProviderResponse(Completion.COMPLETE, new Proposal(null, List.of()), metadata()));
        invalid.add(new ProviderResponse(Completion.COMPLETE, new Proposal(List.of(), null), metadata()));
        invalid.add(new ProviderResponse(Completion.COMPLETE, new Proposal(List.of(), List.of()), null));
        for (ProviderResponse response : invalid) {
            assertEquals(PROVIDER_INVALID_RESPONSE, assertThrows(ExtractionException.class,
                    () -> new ExtractionService(ignored -> response).extract(Command.note(NOTE))).code());
        }
    }

    @Test void providerFailuresDoNotExposeOriginalExceptionOrClinicalContent() {
        var service = new ExtractionService(ignored -> { throw new IllegalStateException("SECRET " + NOTE); });
        var error = assertThrows(ExtractionException.class, () -> service.extract(Command.note(NOTE)));
        assertEquals(PROVIDER_UNAVAILABLE, error.code());
        assertEquals("PROVIDER_UNAVAILABLE", error.getMessage());
        assertNull(error.getCause());
        var timeout = new ExtractionService(ignored -> { throw new ExtractionProvider.ProviderFailure(ExtractionProvider.Failure.TIMEOUT); });
        assertEquals(PROVIDER_TIMEOUT, assertThrows(ExtractionException.class, () -> timeout.extract(Command.note(NOTE))).code());
    }

    @Test void invalidUnicodeInProviderMetadataIsNotExposedInTheResult() {
        var metadata = new ProviderMetadata("prompt-v1", ExecutionMode.SIMULATED, "\ud800", "model");
        var service = new ExtractionService(ignored -> new ProviderResponse(Completion.COMPLETE, new Proposal(List.of(), List.of()), metadata));
        assertEquals(PROVIDER_INVALID_RESPONSE, assertThrows(ExtractionException.class, () -> service.extract(Command.note(NOTE))).code());
    }

    @Test void validationAndLimitsRunBeforeAnyProviderCall() {
        ExtractionProvider forbidden = ignored -> { fail("Provider must not be invoked"); return null; };
        assertEquals(INVALID_REQUEST, assertThrows(ExtractionException.class, () -> new ExtractionService(forbidden).extract(Command.note("\u00a0"))).code());
        assertEquals(INPUT_LIMIT_EXCEEDED, assertThrows(ExtractionException.class, () -> new ExtractionService(forbidden, new InputLimits(2, 1, 1, 1)).extract(Command.note(NOTE))).code());
    }

    @Test void hugeMalformedLocaleIsControlledAndNeverInvokesProvider() {
        ExtractionProvider forbidden = ignored -> { fail("Provider must not be invoked"); return null; };
        var command = new Command("asma", "es" + "-a".repeat(5000), List.of());
        var failure = assertThrowsExactly(ExtractionException.class, () -> new ExtractionService(forbidden).extract(command));
        assertEquals(INVALID_LOCALE, failure.code());
    }

    @Test void concurrentRequestsNeverShareCatalogsNotesOrRequestIds() throws Exception {
        var service = new ExtractionService(new ScriptedExtractionProvider(request -> basic(request.clinicalNote())));
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Result>> calls = IntStream.range(0, 80).mapToObj(i -> (Callable<Result>) () -> {
                var catalog = new Catalog("consumer-" + i, "1", Kind.MEDICATION, List.of(new CatalogEntry("entry-" + i, "Alfa", List.of())));
                var result = service.extract(new Command("Caso " + i + ". " + NOTE, "es", List.of(catalog)));
                assertEquals(3, result.findings().size());
                assertEquals(List.of(new Candidate("consumer-" + i, "1", "entry-" + i)), result.medications().getFirst().catalogMatch().candidates());
                return result;
            }).toList();
            var results = executor.invokeAll(calls);
            var ids = new java.util.HashSet<java.util.UUID>();
            for (var future : results) {
                Result result = assertDoesNotThrow((org.junit.jupiter.api.function.ThrowingSupplier<Result>) future::get);
                ids.add(result.requestId());
            }
            assertEquals(80, ids.size());
        }
    }

    @Test void rawProposalCannotBeChangedThroughAnAliasedList() {
        var source = new ArrayList<Mention>(List.of(diagnosis(NOTE)));
        var proposal = new Proposal(source, List.of());
        source.clear();
        var result = service(proposal).extract(Command.note(NOTE));
        assertEquals(1, result.diagnoses().size());
        assertThrows(UnsupportedOperationException.class, () -> result.findings().clear());
    }

    @Test void preservesConflictingContextsAndRemapsNoticesAfterDiscardingEntity() {
        String note = "Diagnóstico actual del paciente: asma. El paciente niega asma actualmente.";
        var a = diagnosis(note);
        String negative = "El paciente niega asma actualmente.";
        var b = new Diagnosis(new Text("asma", List.of(span(note, negative))), null,
                new Context(grounded(Assertion.NEGATED, note, negative), grounded(Temporality.CURRENT, note, negative), grounded(Subject.PATIENT, note, negative)));
        var invalid = new Diagnosis(new Text("inventado", List.of()), null, null);
        var notice = new Notice(WarningCode.CONTRADICTORY_MENTIONS, List.of(new Target(0, Field.ENTITY), new Target(2, Field.ENTITY)));
        var result = service(new Proposal(List.of(a, invalid, b), List.of(notice))).extract(Command.note(note));
        assertEquals(2, result.diagnoses().size());
        assertEquals(Assertion.AFFIRMED, result.diagnoses().get(0).mention().context().assertion().value());
        assertEquals(Assertion.NEGATED, result.diagnoses().get(1).mention().context().assertion().value());
        var conflict = result.warnings().stream().filter(w -> w.code() == WarningCode.CONTRADICTORY_MENTIONS).findFirst().orElseThrow();
        assertEquals(List.of("/diagnoses/0", "/diagnoses/1"), conflict.targets());
    }

    @Test void rejectsProviderNoticeWithNonexistentReference() {
        var notice = new Notice(WarningCode.AMBIGUOUS_CONTEXT, List.of(new Target(99, Field.ENTITY)));
        assertEquals(PROVIDER_INVALID_RESPONSE, assertThrows(ExtractionException.class,
                () -> service(new Proposal(List.of(diagnosis(NOTE)), List.of(notice))).extract(Command.note(NOTE))).code());
    }
}
