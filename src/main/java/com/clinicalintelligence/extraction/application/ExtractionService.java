package com.clinicalintelligence.extraction.application;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static com.clinicalintelligence.extraction.application.ExtractionException.Code.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ExtractionService {
    private final ExtractionProvider provider;
    private final InputPolicy input;
    private final EvidencePolicy evidence = new EvidencePolicy();

    public ExtractionService(ExtractionProvider provider) { this(provider, InputLimits.defaults()); }
    public ExtractionService(ExtractionProvider provider, InputLimits limits) {
        this.provider = Objects.requireNonNull(provider);
        this.input = new InputPolicy(limits);
    }

    public Result extract(Command command) {
        return extract(command, ExtractionTrace.NONE);
    }

    /** Opt-in evaluation only; the HTTP application always calls extract(command). */
    public Result extract(Command command, ExtractionTrace trace) {
        Objects.requireNonNull(trace);
        try { return extractObserved(command, trace); }
        catch (ExtractionException failure) {
            ExtractionTrace.emit(trace, sink -> sink.failed(failure.code().name()));
            throw failure;
        } catch (RuntimeException failure) {
            ExtractionTrace.emit(trace, sink -> sink.failed("INTERNAL_FAILURE"));
            throw failure;
        }
    }

    private Result extractObserved(Command command, ExtractionTrace trace) {
        String locale = input.validate(command);
        var request = new ProviderRequest(command.clinicalNote(), locale);
        ExtractionTrace.emit(trace, sink -> sink.started(request));
        ProviderResponse response = callProvider(request);
        validateEnvelope(response);
        ExtractionTrace.emit(trace, sink -> sink.decodedProposal(response.proposal()));
        validateNotices(response.proposal());
        var resolver = new CatalogResolver(command.catalogs());
        var findings = new ArrayList<Accepted>();
        var warnings = new ArrayList<Warning>();
        var counts = new EnumMap<Kind, Integer>(Kind.class);
        var sourceCounts = trace == ExtractionTrace.NONE ? null : new EnumMap<Kind, Integer>(Kind.class);
        var paths = new HashMap<Integer, String>();
        List<Mention> proposals = response.proposal().mentions();
        for (int i = 0; i < proposals.size(); i++) {
            Mention proposed = proposals.get(i);
            int familyIndex = sourceCounts == null ? -1 : sourceCounts.getOrDefault(proposed.kind(), 0);
            if (sourceCounts != null) sourceCounts.put(proposed.kind(), familyIndex + 1);
            var source = sourceCounts == null ? null : new ExtractionTrace.Source(i, proposed.kind(), familyIndex);
            List<ExtractionTrace.Rejection> rejections = trace == ExtractionTrace.NONE ? null : new ArrayList<>();
            if (!evidence.validText(command.clinicalNote(), proposed.name())) {
                warnings.add(new Warning(WarningCode.ENTITY_DISCARDED, List.of(), "/clinicalNote"));
                ExtractionTrace.emit(trace, sink -> sink.mentionDecision(new ExtractionTrace.MentionDecision(
                        source, null, List.of(new ExtractionTrace.Rejection("name", ExtractionTrace.Reason.NAME_LITERAL_REJECTED)))));
                continue;
            }
            int index = counts.getOrDefault(proposed.kind(), 0);
            String path = "/" + collection(proposed.kind()) + "/" + index;
            Mention accepted = sanitize(command.clinicalNote(), proposed, path, warnings, rejections);
            String id = switch (accepted.kind()) { case DIAGNOSIS -> "d"; case MEDICATION -> "m"; case PROCEDURE -> "p"; } + (index + 1);
            findings.add(new Accepted(id, accepted, resolver.resolve(accepted.kind(), accepted.name().value())));
            paths.put(i, path);
            counts.put(accepted.kind(), index + 1);
            ExtractionTrace.emit(trace, sink -> sink.mentionDecision(new ExtractionTrace.MentionDecision(source, path, rejections)));
        }
        for (Notice notice : response.proposal().notices()) {
            List<String> targets = notice.targets().stream().filter(t -> paths.containsKey(t.mentionIndex()))
                    .map(t -> paths.get(t.mentionIndex()) + suffix(t.field())).distinct().toList();
            if (targets.isEmpty() || notice.code() == WarningCode.CONTRADICTORY_MENTIONS && targets.size() < 2) continue;
            warnings.add(new Warning(notice.code(), targets, "/clinicalNote"));
        }
        var metadata = response.metadata();
        var result = new Result(UUID.randomUUID(), locale, findings, warnings,
                new Processing("extraction-core-0.1.0", metadata.promptVersion(), "evidence-2",
                        metadata.executionMode(), metadata.provider(), metadata.model()));
        ExtractionTrace.emit(trace, sink -> sink.completed(result));
        return result;
    }

    private ProviderResponse callProvider(ProviderRequest request) {
        try { return provider.extract(request); }
        catch (ExtractionProvider.ProviderFailure failure) {
            throw new ExtractionException(switch(failure.failure()) {
                case TIMEOUT -> PROVIDER_TIMEOUT;
                case UNAVAILABLE -> PROVIDER_UNAVAILABLE;
                case CAPACITY_EXCEEDED -> PROVIDER_CAPACITY_EXCEEDED;
                case OVERLOADED -> SERVICE_OVERLOADED;
                case INVALID_RESPONSE -> PROVIDER_INVALID_RESPONSE;
                case TRUNCATED_RESPONSE -> PROVIDER_TRUNCATED_RESPONSE;
            });
        } catch (RuntimeException failure) {
            // No raw message or cause crosses the boundary, even on error paths.
            throw new ExtractionException(PROVIDER_UNAVAILABLE);
        }
    }

    private void validateEnvelope(ProviderResponse response) {
        if (response == null || response.completion() == null) invalidResponse();
        if (response.completion() == Completion.TRUNCATED) throw new ExtractionException(PROVIDER_TRUNCATED_RESPONSE);
        if (response.completion() != Completion.COMPLETE || response.proposal() == null
                || response.proposal().mentions() == null || response.proposal().notices() == null
                || response.proposal().mentions().stream().anyMatch(Objects::isNull) || response.metadata() == null) invalidResponse();
        var metadata = response.metadata();
        if (!EvidencePolicy.nonblankScalar(metadata.promptVersion()) || !EvidencePolicy.nonblankScalar(metadata.provider())
                || !EvidencePolicy.nonblankScalar(metadata.model()) || metadata.executionMode() == null) invalidResponse();
    }

    private Mention sanitize(String note, Mention proposed, String path, List<Warning> warnings, List<ExtractionTrace.Rejection> rejections) {
        Context raw = proposed.context();
        Context context = new Context(
                grounded(note, raw == null ? null : raw.assertion(), Assertion.UNKNOWN, path + suffix(Field.ASSERTION), warnings, rejections),
                grounded(note, raw == null ? null : raw.temporality(), Temporality.UNKNOWN, path + suffix(Field.TEMPORALITY), warnings, rejections),
                grounded(note, raw == null ? null : raw.subject(), Subject.UNKNOWN, path + suffix(Field.SUBJECT), warnings, rejections));
        Text code = text(note, proposed.mentionedCode(), path + suffix(Field.MENTIONED_CODE), warnings, rejections);
        return switch (proposed) {
            case Diagnosis d -> new Diagnosis(d.name(), code, context);
            case Medication m -> medication(note, m, code, context, path, warnings, rejections);
            case Procedure p -> new Procedure(p.name(), code, context,
                    grounded(note, p.performanceStatus(), PerformanceStatus.UNKNOWN, path + suffix(Field.PERFORMANCE_STATUS), warnings, rejections));
        };
    }

    private Medication medication(String note, Medication proposed, Text code, Context context, String path, List<Warning> warnings, List<ExtractionTrace.Rejection> rejections) {
        var status = grounded(note, proposed.prescriptionStatus(), PrescriptionStatus.UNKNOWN, path + suffix(Field.PRESCRIPTION_STATUS), warnings, rejections);
        var scopes = new ArrayList<Evidence>(context.assertion().evidence());
        scopes.addAll(status.evidence());
        return new Medication(proposed.name(), code, context, status,
                medicationText(note, proposed.name(), scopes, proposed.dosage(), path + suffix(Field.DOSAGE), warnings, rejections),
                medicationText(note, proposed.name(), scopes, proposed.frequency(), path + suffix(Field.FREQUENCY), warnings, rejections),
                medicationText(note, proposed.name(), scopes, proposed.route(), path + suffix(Field.ROUTE), warnings, rejections),
                medicationText(note, proposed.name(), scopes, proposed.duration(), path + suffix(Field.DURATION), warnings, rejections));
    }

    private Text medicationText(String note, Text name, List<Evidence> scopes, Text proposed, String target, List<Warning> warnings, List<ExtractionTrace.Rejection> rejections) {
        if (proposed == null) return null;
        boolean literal = evidence.validText(note, proposed);
        boolean associated = literal && proposed.evidence().stream().allMatch(span ->
                scopes.stream().anyMatch(scope -> containsNameOccurrence(scope, name)
                        && scope.start() <= span.start() && span.end() <= scope.end()));
        if (associated) return proposed;
        discardedAttribute(target, warnings, rejections, literal ? ExtractionTrace.Reason.MEDICATION_ASSOCIATION_REJECTED : ExtractionTrace.Reason.TEXT_LITERAL_REJECTED);
        return null;
    }

    private boolean containsNameOccurrence(Evidence scope, Text name) {
        for (Evidence anchor : name.evidence()) {
            int position = anchor.quote().indexOf(name.value());
            while (position >= 0) {
                int start = anchor.start() + anchor.quote().codePointCount(0, position);
                int end = start + name.value().codePointCount(0, name.value().length());
                if (scope.start() <= start && end <= scope.end()) return true;
                position = anchor.quote().indexOf(name.value(), position + 1);
            }
        }
        return false;
    }

    private Text text(String note, Text proposed, String target, List<Warning> warnings, List<ExtractionTrace.Rejection> rejections) {
        if (proposed == null || evidence.validText(note, proposed)) return proposed;
        discardedAttribute(target, warnings, rejections, ExtractionTrace.Reason.TEXT_LITERAL_REJECTED);
        return null;
    }

    private <T extends Enum<T>> Grounded<T> grounded(String note, Grounded<T> proposed, T unknown, String target, List<Warning> warnings, List<ExtractionTrace.Rejection> rejections) {
        boolean valid = proposed != null && proposed.value() != null && proposed.evidence() != null
                && (proposed.value() == unknown ? proposed.evidence().isEmpty()
                : !proposed.evidence().isEmpty() && proposed.evidence().stream().allMatch(e -> evidence.validEvidence(note, e)));
        if (valid) return proposed;
        discardedAttribute(target, warnings, rejections, ExtractionTrace.Reason.CONTEXT_EVIDENCE_REJECTED);
        return new Grounded<>(unknown, List.of());
    }

    private void discardedAttribute(String target, List<Warning> warnings, List<ExtractionTrace.Rejection> rejections, ExtractionTrace.Reason reason) {
        warnings.add(new Warning(WarningCode.ATTRIBUTE_DISCARDED, List.of(target), "/clinicalNote"));
        if (rejections != null) rejections.add(new ExtractionTrace.Rejection(target.split("/", 4)[3], reason));
    }

    private void validateNotices(Proposal proposal) {
        for (Notice notice : proposal.notices()) {
            if (notice == null || notice.targets() == null || notice.targets().isEmpty()
                    || notice.code() != WarningCode.CONTRADICTORY_MENTIONS && notice.code() != WarningCode.AMBIGUOUS_CONTEXT) invalidResponse();
            var unique = new HashSet<Target>();
            for (Target target : notice.targets()) {
                if (target == null || target.field() == null || target.mentionIndex() < 0
                        || target.mentionIndex() >= proposal.mentions().size() || !unique.add(target)) invalidResponse();
                Kind kind = proposal.mentions().get(target.mentionIndex()).kind();
                if (!validField(kind, target.field())) invalidResponse();
            }
            if (notice.code() == WarningCode.CONTRADICTORY_MENTIONS && (notice.targets().size() < 2
                    || notice.targets().stream().anyMatch(t -> t.field() != Field.ENTITY))) invalidResponse();
        }
    }

    private boolean validField(Kind kind, Field field) {
        return switch (field) {
            case PRESCRIPTION_STATUS, DOSAGE, FREQUENCY, ROUTE, DURATION -> kind == Kind.MEDICATION;
            case PERFORMANCE_STATUS -> kind == Kind.PROCEDURE;
            default -> true;
        };
    }

    private static String collection(Kind kind) {
        return switch (kind) { case DIAGNOSIS -> "diagnoses"; case MEDICATION -> "medications"; case PROCEDURE -> "procedures"; };
    }

    private static String suffix(Field field) {
        return switch (field) {
            case ENTITY -> "";
            case ASSERTION -> "/context/assertion";
            case TEMPORALITY -> "/context/temporality";
            case SUBJECT -> "/context/subject";
            case PRESCRIPTION_STATUS -> "/prescriptionStatus";
            case PERFORMANCE_STATUS -> "/performanceStatus";
            case MENTIONED_CODE -> "/mentionedCode";
            case DOSAGE -> "/dosage";
            case FREQUENCY -> "/frequency";
            case ROUTE -> "/route";
            case DURATION -> "/duration";
        };
    }

    private static void invalidResponse() { throw new ExtractionException(PROVIDER_INVALID_RESPONSE); }
}
