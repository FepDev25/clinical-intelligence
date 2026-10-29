package com.clinicalintelligence.extraction.infrastructure.gemini;
import com.clinicalintelligence.extraction.domain.ClinicalModel.ProviderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
/** Snapshot against the actual pre-T7 classpath, never a handcrafted reconstruction. */
public final class LegacyRequestSnapshot {
    public static void main(String[] args) throws Exception {
        var json=new ObjectMapper();var result=json.createObjectNode().put("inferenceExecuted",false)
                .put("model",GeminiProtocol.MODEL).put("promptVersion",GeminiProtocol.PROMPT_VERSION);
        result.set("request",new GeminiProtocol().request(new ProviderRequest("🩺 El paciente niega asma.\r\nSe prescribe Alfa 5 mg.","es-EC"),65536));
        Files.writeString(Path.of(args[0]),json.writerWithDefaultPrettyPrinter().writeValueAsString(result)+"\n",StandardOpenOption.CREATE_NEW);
    }
}
