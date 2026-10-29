package com.clinicalintelligence.extraction.application;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Explicit, per-operation diagnostic port for controlled synthetic evaluation.
 * No Spring bean, environment switch, storage, logger or shared capture state.
 * Implementations may see clinical content: never enable on ordinary requests.
 */
public interface ExtractionTrace {
    ExtractionTrace NONE = new ExtractionTrace() {};

    enum Reason {
        NAME_LITERAL_REJECTED, TEXT_LITERAL_REJECTED,
        CONTEXT_EVIDENCE_REJECTED, MEDICATION_ASSOCIATION_REJECTED
    }
    record Source(int proposalIndex, Kind kind, int familyIndex) {}
    record Rejection(String field, Reason reason) {}
    record MentionDecision(Source source, String outputPath, List<Rejection> rejections) {
        public MentionDecision { rejections = List.copyOf(rejections); }
    }

    /** Actual note/locale validated for this operation, before calling the provider. */
    default void started(ProviderRequest request) {}
    /** Schema-valid output text only: never the HTTP envelope, key or thoughts. */
    default void structuredProposal(String json) {}
    /** Typed proposal received by the service, before local sanitization. */
    default void decodedProposal(Proposal proposal) {}
    default void mentionDecision(MentionDecision decision) {}
    default void completed(Result result) {}
    /** Controlled code only, no exception message or original cause. */
    default void failed(String code) {}

    static void emit(ExtractionTrace trace, Consumer<ExtractionTrace> event) {
        if (trace == NONE) return;
        try { event.accept(trace); }
        catch (RuntimeException ignored) {
            // Diagnostic sink failures must not alter outcomes or enter logs.
        }
    }
}
