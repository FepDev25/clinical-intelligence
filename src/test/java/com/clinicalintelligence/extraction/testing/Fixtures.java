package com.clinicalintelligence.extraction.testing;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import java.util.List;

public final class Fixtures {
    private Fixtures() {}
    public static final String NOTE = "Diagnóstico actual del paciente: asma. Prescripción actual del paciente: Alfa, dosis 5 mg. Se solicita radiografía al paciente para mañana.";
    public static Evidence span(String note, String quote) {
        int offset = note.indexOf(quote);
        if (offset < 0) throw new IllegalArgumentException("Missing synthetic fixture quote");
        int start = note.codePointCount(0, offset);
        return new Evidence(start, start + quote.codePointCount(0, quote.length()), quote);
    }
    public static Text text(String note, String value) { return new Text(value, List.of(span(note, value))); }
    public static <T extends Enum<T>> Grounded<T> grounded(T value, String note, String quote) { return new Grounded<>(value, List.of(span(note, quote))); }
    public static Context context(String note, String quote) {
        return new Context(grounded(Assertion.AFFIRMED, note, quote), grounded(Temporality.CURRENT, note, quote), grounded(Subject.PATIENT, note, quote));
    }
    public static Diagnosis diagnosis(String note) {
        return new Diagnosis(text(note, "asma"), null, context(note, "Diagnóstico actual del paciente: asma."));
    }
    public static Medication medication(String note) {
        String clause = "Prescripción actual del paciente: Alfa, dosis 5 mg.";
        return new Medication(text(note, "Alfa"), null, context(note, clause), grounded(PrescriptionStatus.PRESCRIBED, note, clause), text(note, "5 mg"), null, null, null);
    }
    public static Procedure procedure(String note) {
        String clause = "Se solicita radiografía al paciente para mañana.";
        var context = new Context(grounded(Assertion.AFFIRMED, note, clause), grounded(Temporality.FUTURE, note, clause), grounded(Subject.PATIENT, note, clause));
        return new Procedure(text(note, "radiografía"), null, context, grounded(PerformanceStatus.ORDERED, note, clause));
    }
    public static Proposal basic(String note) { return new Proposal(List.of(diagnosis(note), medication(note), procedure(note)), List.of()); }
    public static ProviderMetadata metadata() { return new ProviderMetadata("test-prompt", ExecutionMode.SIMULATED, "test-script", "none"); }
    public static ProviderResponse completed(Proposal proposal) { return new ProviderResponse(Completion.COMPLETE, proposal, metadata()); }
}
