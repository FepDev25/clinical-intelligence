package com.clinicalintelligence.extraction.infrastructure.gemini;

import com.clinicalintelligence.extraction.application.ExtractionTrace;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

/** In-memory, one-operation recorder, compiled only into test classes. No file or log sink. */
final class SyntheticTraceCapture implements ExtractionTrace {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final UUID operationId = UUID.randomUUID();
    private final String caseId;
    private ProviderRequest request;
    private String structured;
    private Proposal decoded;
    private Result result;
    private String failure;
    private boolean invalid;
    private final List<MentionDecision> decisions = new ArrayList<>();

    SyntheticTraceCapture(String caseId) {
        if (caseId == null || !caseId.matches("[a-z0-9-]{1,80}")) throw new IllegalArgumentException("Invalid fixture ID");
        this.caseId = caseId;
    }
    private boolean active() {
        if (result != null || failure != null) { invalid = true; return false; }
        return true;
    }
    @Override public synchronized void started(ProviderRequest value) {
        if (!active() || request != null) { invalid = true; return; }
        request = value;
    }
    @Override public synchronized void structuredProposal(String value) {
        if (!active() || structured != null || request == null) { invalid = true; return; }
        structured = value;
    }
    @Override public synchronized void decodedProposal(Proposal value) {
        if (!active() || decoded != null || request == null) { invalid = true; return; }
        decoded = value;
    }
    @Override public synchronized void mentionDecision(MentionDecision value) {
        if (!active() || decoded == null || value.source().proposalIndex() != decisions.size()) { invalid = true; return; }
        decisions.add(value);
    }
    @Override public synchronized void completed(Result value) {
        if (!active() || decoded == null || decisions.size() != decoded.mentions().size()) { invalid = true; return; }
        result = value;
    }
    @Override public synchronized void failed(String code) {
        if (!active()) return;
        // The core supplies controlled codes, never original exception text.
        if (!code.matches("[A-Z_]{1,80}")) { invalid = true; return; }
        failure = code;
    }
    synchronized ObjectNode snapshot() {
        var out = JSON.createObjectNode();
        out.put("schemaVersion", "synthetic-trace-1.0.0").put("caseId",caseId).put("operationId",operationId.toString());
        out.put("captureValid",!invalid).put("operationClosed",result != null || failure != null);
        out.put("syntheticFixture",true).put("inferenceExecuted",false);
        out.set("request",JSON.valueToTree(request));
        out.put("structuredProposalText",structured);
        out.put("structuredStageAvailable",structured != null);
        out.set("decodedProposal",JSON.valueToTree(decoded));
        // Include kind explicitly; domain records intentionally have no wire annotations.
        if (decoded != null) {
            var mentions = (ArrayNode)out.path("decodedProposal").path("mentions");
            for (int i=0;i<mentions.size();i++) ((ObjectNode)mentions.get(i)).put("kind",decoded.mentions().get(i).kind().name());
        }
        out.set("finalResult",result == null ? NullNode.instance :
                new com.clinicalintelligence.http.WireContract(1_000_000).encode(result,result.requestId()));
        out.put("failureCode",failure);
        out.set("mentionDecisions",JSON.valueToTree(decisions));
        var transitions = out.putArray("fieldTransitions");
        for (var decision : decisions) {
            var source = decision.source();
            var before = JSON.valueToTree(decoded.mentions().get(source.proposalIndex()));
            JsonNode after = null;
            if (decision.outputPath() != null && result != null) {
                String[] path = decision.outputPath().split("/");
                var collection = switch(source.kind()) {
                    case DIAGNOSIS -> result.diagnoses();
                    case MEDICATION -> result.medications();
                    case PROCEDURE -> result.procedures();
                };
                after = JSON.valueToTree(collection.get(Integer.parseInt(path[2])).mention());
            }
            var fields = new ArrayList<>(List.of("name","mentionedCode","context/assertion","context/temporality","context/subject"));
            if (source.kind()==Kind.MEDICATION) fields.addAll(List.of("prescriptionStatus","dosage","frequency","route","duration"));
            if (source.kind()==Kind.PROCEDURE) fields.add("performanceStatus");
            for (String field : fields) {
                JsonNode original = before.at("/"+field);
                JsonNode accepted = after == null ? NullNode.instance : after.at("/"+field);
                var reason = decision.rejections().stream().filter(r -> r.field().equals(field)).map(Rejection::reason).findFirst().orElse(null);
                String disposition = decision.outputPath()==null ? "ENTITY_DISCARDED"
                        : reason != null ? accepted.path("value").asText().equals("UNKNOWN") ? "SANITIZED_TO_UNKNOWN" : "REMOVED_TO_NULL"
                        : original.isNull() ? "ORIGINAL_NULL"
                        : original.path("value").asText().equals("UNKNOWN") ? "ORIGINAL_UNKNOWN" : "PRESERVED";
                var transition = transitions.addObject().put("proposalIndex",source.proposalIndex()).put("field",field)
                        .put("sourcePath",sourcePath(source)+"/"+field).put("outputPath",decision.outputPath()==null ? null : decision.outputPath()+"/"+field)
                        .put("disposition",disposition).put("rejectionReason",reason==null ? null : reason.name());
                transition.set("decodedBefore",original);
                transition.set("sanitizedAfter",accepted);
            }
        }
        return out;
    }
    private static String sourcePath(Source source) {
        return "/"+switch(source.kind()) {
            case DIAGNOSIS -> "diagnoses";
            case MEDICATION -> "medications";
            case PROCEDURE -> "procedures";
        }+"/"+source.familyIndex();
    }
    @Override public String toString() { return "SyntheticTraceCapture[operationId="+operationId+", content=redacted]"; }
}
