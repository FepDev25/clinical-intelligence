package com.clinicalintelligence.extraction.infrastructure.gemini;
import java.util.*;

/** Explicit, closed experiment configuration. No environment selection or implicit model fallback. */
public record GeminiGeneration(String model,ThinkingLevel thinkingLevel,Prompt prompt) {
    public enum ThinkingLevel { LOW, MEDIUM, HIGH }
    public enum Prompt {
        V2("extraction-gemini-v2","extraction-v2.txt","proposal-v1.schema.json"),
        V3("extraction-gemini-v3-t7","extraction-v3-t7.txt","proposal-v1.schema.json"),
        DETECTION("detection-gemini-v1-t7","detection-v1-t7.txt","detection-v1-t7.schema.json"),
        CLASSIFICATION("classification-gemini-v1-t7","classification-v1-t7.txt","proposal-v1.schema.json");
        private final String version,resource,schema;
        Prompt(String version,String resource,String schema){this.version=version;this.resource=resource;this.schema=schema;}
        public String version(){return version;}
        String resource(){return "/gemini/"+resource;}
        String schema(){return "/gemini/"+schema;}
        public boolean singlePass(){return this==V2 || this==V3;}
    }
    public GeminiGeneration {
        if(!GeminiProtocol.MODEL.equals(model) || thinkingLevel==null || prompt==null)
            throw new IllegalArgumentException("Unsupported Gemini experiment configuration");
    }
    public static GeminiGeneration production(){return new GeminiGeneration(GeminiProtocol.MODEL,ThinkingLevel.LOW,Prompt.V2);}
    public static GeminiGeneration from(Map<String,?> fields) {
        try {
            if(fields==null || !fields.keySet().equals(Set.of("model","thinkingLevel","promptVersion")) || fields.values().stream().anyMatch(v -> !(v instanceof String)))throw new IllegalArgumentException();
            var prompt=Arrays.stream(Prompt.values()).filter(p -> p.version().equals(fields.get("promptVersion"))).findFirst().orElseThrow();
            return new GeminiGeneration((String)fields.get("model"),ThinkingLevel.valueOf((String)fields.get("thinkingLevel")),prompt);
        } catch(RuntimeException invalid){throw new IllegalArgumentException("Unsupported Gemini experiment configuration");}
    }
}
