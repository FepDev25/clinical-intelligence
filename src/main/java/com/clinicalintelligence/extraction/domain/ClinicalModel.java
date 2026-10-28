package com.clinicalintelligence.extraction.domain;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

/** Transport- and provider-independent data. Proposed values remain untrusted. */
public final class ClinicalModel {
    private ClinicalModel() {}

    public enum Kind { DIAGNOSIS, MEDICATION, PROCEDURE }
    public enum Assertion { AFFIRMED, NEGATED, SUSPECTED, CONDITIONAL, UNKNOWN }
    public enum Temporality { CURRENT, HISTORICAL, FUTURE, UNKNOWN }
    public enum Subject { PATIENT, FAMILY, OTHER, UNKNOWN }
    public enum PrescriptionStatus { PRESCRIBED, REPORTED, SUSPENDED, UNKNOWN }
    public enum PerformanceStatus { PERFORMED, ORDERED, PLANNED, NOT_PERFORMED, CANCELLED, UNKNOWN }
    public enum MatchStatus { NOT_PROVIDED, NOT_FOUND, MATCHED, AMBIGUOUS }
    public enum Completion { COMPLETE, TRUNCATED, MALFORMED }
    public enum ExecutionMode { SIMULATED, PROVIDER }
    public enum WarningCode { ENTITY_DISCARDED, ATTRIBUTE_DISCARDED, CONTRADICTORY_MENTIONS, AMBIGUOUS_CONTEXT }
    public enum Field { ENTITY, ASSERTION, TEMPORALITY, SUBJECT, PRESCRIPTION_STATUS, PERFORMANCE_STATUS, MENTIONED_CODE, DOSAGE, FREQUENCY, ROUTE, DURATION }

    public record Evidence(int start, int end, String quote) {
        @Override public String toString() { return "Evidence[redacted]"; }
    }
    public record Text(String value, List<Evidence> evidence) {
        public Text { evidence = snapshot(evidence); }
        @Override public String toString() { return "Text[redacted]"; }
    }
    public record Grounded<T extends Enum<T>>(T value, List<Evidence> evidence) {
        public Grounded { evidence = snapshot(evidence); }
    }
    public record Context(Grounded<Assertion> assertion, Grounded<Temporality> temporality,
                          Grounded<Subject> subject) {}

    public sealed interface Mention permits Diagnosis, Medication, Procedure {
        Text name();
        Text mentionedCode();
        Context context();
        Kind kind();
    }
    public record Diagnosis(Text name, Text mentionedCode, Context context) implements Mention {
        @Override public Kind kind() { return Kind.DIAGNOSIS; }
    }
    public record Medication(Text name, Text mentionedCode, Context context,
                             Grounded<PrescriptionStatus> prescriptionStatus,
                             Text dosage, Text frequency, Text route, Text duration) implements Mention {
        @Override public Kind kind() { return Kind.MEDICATION; }
    }
    public record Procedure(Text name, Text mentionedCode, Context context,
                            Grounded<PerformanceStatus> performanceStatus) implements Mention {
        @Override public Kind kind() { return Kind.PROCEDURE; }
    }

    public record CatalogEntry(String entryId, String name, List<String> aliases) {
        public CatalogEntry { aliases = snapshot(aliases); }
        @Override public String toString() { return "CatalogEntry[redacted]"; }
    }
    public record Catalog(String catalogId, String version, Kind kind, List<CatalogEntry> entries) {
        public Catalog { entries = snapshot(entries); }
        @Override public String toString() { return "Catalog[redacted]"; }
    }
    public record Candidate(String catalogId, String version, String entryId) {}
    public record CatalogMatch(MatchStatus status, List<Candidate> candidates) {
        public CatalogMatch { candidates = snapshot(candidates); }
    }

    public record Command(String clinicalNote, String locale, List<Catalog> catalogs) {
        public Command { catalogs = snapshot(catalogs); }
        public static Command note(String note) { return new Command(note, null, List.of()); }
        @Override public String toString() { return "Command[redacted]"; }
    }
    public record ProviderRequest(String clinicalNote, String locale) {
        @Override public String toString() { return "ProviderRequest[redacted]"; }
    }
    /** References the original flat proposal list, before discarded mentions change indexes. */
    public record Target(int mentionIndex, Field field) {}
    public record Notice(WarningCode code, List<Target> targets) {
        public Notice { targets = snapshot(targets); }
    }
    public record Proposal(List<Mention> mentions, List<Notice> notices) {
        public Proposal { mentions = snapshot(mentions); notices = snapshot(notices); }
        @Override public String toString() { return "Proposal[redacted]"; }
    }
    public record ProviderMetadata(String promptVersion, ExecutionMode executionMode, String provider, String model) {}
    public record ProviderResponse(Completion completion, Proposal proposal, ProviderMetadata metadata) {
        @Override public String toString() { return "ProviderResponse[redacted]"; }
    }

    public record Accepted(String id, Mention mention, CatalogMatch catalogMatch) {}
    public record Warning(WarningCode code, List<String> targets, String source) {
        public Warning { targets = List.copyOf(targets); }
        public String message() {
            return switch (code) {
                case ENTITY_DISCARDED -> "Se descartó una entidad sin evidencia válida.";
                case ATTRIBUTE_DISCARDED -> "Se descartó un atributo sin respaldo válido.";
                case CONTRADICTORY_MENTIONS -> "La nota contiene menciones incompatibles que requieren revisión.";
                case AMBIGUOUS_CONTEXT -> "No se puede establecer el contexto de una mención.";
            };
        }
    }
    public record Processing(String version, String promptVersion, String validationVersion,
                             ExecutionMode executionMode, String provider, String model) {}
    public record Result(UUID requestId, String locale, List<Accepted> findings,
                         List<Warning> warnings, Processing processing) {
        public Result { findings = List.copyOf(findings); warnings = List.copyOf(warnings); }
        @Override public String toString() { return "Result[requestId=" + requestId + ", clinicalContent=redacted]"; }
        public String contractVersion() { return "0.1.0"; }
        public boolean reviewRequired() { return true; }
        public String processingStatus() { return warnings.isEmpty() ? "completed" : "completed_with_warnings"; }
        public List<Accepted> diagnoses() { return ofKind(Kind.DIAGNOSIS); }
        public List<Accepted> medications() { return ofKind(Kind.MEDICATION); }
        public List<Accepted> procedures() { return ofKind(Kind.PROCEDURE); }
        private List<Accepted> ofKind(Kind kind) { return findings.stream().filter(f -> f.mention().kind() == kind).toList(); }
    }

    private static <T> List<T> snapshot(List<T> values) {
        // Nulls remain representable at the untrusted provider boundary, so the
        // application can translate them to a controlled failure or discard.
        return values == null ? null : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
