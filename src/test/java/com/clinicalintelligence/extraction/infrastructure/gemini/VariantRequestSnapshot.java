package com.clinicalintelligence.extraction.infrastructure.gemini;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static com.clinicalintelligence.extraction.testing.Fixtures.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
/** All bundled variants built locally, with synthetic data and no HTTP provider. */
public final class VariantRequestSnapshot {
    public static void main(String[] args) throws Exception {
        var json=new ObjectMapper();var root=json.createObjectNode().put("inferenceExecuted",false);var rows=root.putArray("variants");
        String note="🩺 El paciente niega asma.\r\nSe prescribe Alfa 5 mg.";
        var unknown=new Context(new Grounded<>(Assertion.UNKNOWN,List.of()),new Grounded<>(Temporality.UNKNOWN,List.of()),new Grounded<>(Subject.UNKNOWN,List.of()));
        var detected=new Proposal(List.of(new Diagnosis(text(note,"asma"),null,unknown)),List.of());
        for(var prompt:GeminiGeneration.Prompt.values())for(var level:GeminiGeneration.ThinkingLevel.values()) {
            var protocol=new GeminiProtocol(new GeminiGeneration(GeminiProtocol.MODEL,level,prompt));
            var request=new ProviderRequest(note,"es-EC");
            var body=prompt==GeminiGeneration.Prompt.CLASSIFICATION?protocol.classificationRequest(request,detected,65536):protocol.request(request,65536);
            var row=rows.addObject();row.set("identity",json.valueToTree(protocol.identity()));row.set("request",body);
        }
        Files.writeString(Path.of(args[0]),json.writerWithDefaultPrettyPrinter().writeValueAsString(root)+"\n",StandardOpenOption.CREATE_NEW);
    }
}
