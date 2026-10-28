package com.clinicalintelligence.http;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;

/** Test-only controlled proposals from public examples, never runtime or reserved annotations. */
final class PublicExamples {
    static final ObjectMapper JSON=new ObjectMapper();
    static Proposal proposal(JsonNode expected) {
        var mentions=new ArrayList<Mention>();
        Map<String,Integer> indices=new HashMap<>();
        for(String family:List.of("diagnoses","medications","procedures")) {
            int i=0;
            for(JsonNode n:expected.path(family)) {
                indices.put("/"+family+"/"+i++,mentions.size());
                Text name=text(n.get("name")), code=text(n.get("mentionedCode"));
                JsonNode ctx=n.get("context");
                var context=new Context(grounded(ctx.get("assertion"),Assertion.class),
                        grounded(ctx.get("temporality"),Temporality.class),grounded(ctx.get("subject"),Subject.class));
                Mention mention=switch(family) {
                    case "diagnoses" -> new Diagnosis(name,code,context);
                    case "medications" -> new Medication(name,code,context,grounded(n.get("prescriptionStatus"),PrescriptionStatus.class),
                            text(n.get("dosage")),text(n.get("frequency")),text(n.get("route")),text(n.get("duration")));
                    default -> new Procedure(name,code,context,grounded(n.get("performanceStatus"),PerformanceStatus.class));
                };
                mentions.add(mention);
            }
        }
        var notices=new ArrayList<Notice>();
        for(JsonNode warning:expected.path("warnings")) {
            String code=warning.path("code").asText();
            if(code.equals("ENTITY_DISCARDED")) {
                var unknown=new Context(new Grounded<>(Assertion.UNKNOWN,List.of()),new Grounded<>(Temporality.UNKNOWN,List.of()),new Grounded<>(Subject.UNKNOWN,List.of()));
                mentions.add(new Diagnosis(new Text("unsupported",List.of()),null,unknown));
            } else if(code.equals("ATTRIBUTE_DISCARDED")) {
                String target=warning.get("targets").get(0).asText();
                int index=indices.get(target.substring(0,target.lastIndexOf('/')));
                Medication med=(Medication)mentions.get(index);
                mentions.set(index,new Medication(med.name(),med.mentionedCode(),med.context(),med.prescriptionStatus(),
                        med.dosage(),med.frequency(),med.route(),new Text("invented duration",List.of())));
            } else {
                List<Target> targets=new ArrayList<>();
                for(JsonNode target:warning.path("targets")) targets.add(new Target(indices.get(target.asText()),Field.ENTITY));
                notices.add(new Notice(WarningCode.valueOf(code),targets));
            }
        }
        return new Proposal(mentions,notices);
    }
    static Text text(JsonNode node) {
        if(node==null || node.isNull()) return null;
        return new Text(node.get("value").asText(),evidence(node.get("evidence")));
    }
    static List<Evidence> evidence(JsonNode node) {
        var spans=new ArrayList<Evidence>();
        for(JsonNode span:node) spans.add(new Evidence(span.get("start").asInt(),span.get("end").asInt(),span.get("quote").asText()));
        return spans;
    }
    static <T extends Enum<T>> Grounded<T> grounded(JsonNode node,Class<T> type) {
        return new Grounded<>(Enum.valueOf(type,node.get("value").asText().toUpperCase(Locale.ROOT)),evidence(node.get("evidence")));
    }
}

