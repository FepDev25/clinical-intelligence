package com.clinicalintelligence.extraction.infrastructure.gemini;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.clinicalintelligence.extraction.application.EvidencePolicy;
import com.clinicalintelligence.extraction.application.ExtractionTrace;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

public final class GeminiProtocol {
    public static final String MODEL="gemini-3.8-flash";
    public static final String PROMPT_VERSION="extraction-gemini-v2";
    private final ObjectMapper json;
    private final JsonNode schema;
    private final JsonSchema validator;
    private final String system;

    public record Usage(Long inputTokens, Long outputTokens, Long thoughtTokens, Long cachedTokens, Long totalTokens) {
        public Usage(Long inputTokens,Long outputTokens,Long thoughtTokens,Long cachedTokens) {
            this(inputTokens,outputTokens,thoughtTokens,cachedTokens,null);
        }
    }
    public record Decoded(Completion completion, Proposal proposal, Usage usage, String modelVersion) {}

    public GeminiProtocol() {
        var factory=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(40).maxStringLength(16_777_216).build()).build();
        json=JsonMapper.builder(factory).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
        try {
            schema=json.readTree(resource("/gemini/proposal-v1.schema.json"));
            system=new String(resource("/gemini/extraction-v2.txt"),StandardCharsets.UTF_8);
            validator=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schema);
        } catch(Exception invalid) { throw new IllegalStateException("Gemini protocol resources unavailable"); }
    }

    public ObjectNode request(ProviderRequest request,int outputTokens) {
        var root=json.createObjectNode();
        root.putObject("systemInstruction").putArray("parts").addObject().put("text",system);
        var data=json.createObjectNode().put("clinicalNote",request.clinicalNote()).put("locale",request.locale());
        root.putArray("contents").addObject().put("role","user").putArray("parts").addObject().put("text",data.toString());
        var generation=root.putObject("generationConfig").put("candidateCount",1).put("temperature",1.0)
                .put("maxOutputTokens",outputTokens).put("responseMimeType","application/json");
        generation.putObject("thinkingConfig").put("thinkingLevel","LOW").put("includeThoughts",false);
        generation.set("responseJsonSchema",schema.deepCopy());
        return root;
    }

    public Decoded decode(String note,byte[] bytes) {
        return decode(note, bytes, ExtractionTrace.NONE);
    }

    public Decoded decode(String note,byte[] bytes,ExtractionTrace trace) {
        Objects.requireNonNull(trace);
        Usage usage=new Usage(null,null,null,null);
        String model=MODEL;
        try {
            JsonNode root=read(bytes);
            usage=usage(root.path("usageMetadata"));
            String version=root.path("modelVersion").asText("");
            if(version.startsWith(MODEL) && version.matches("[A-Za-z0-9._-]{1,128}")) model=version;
            if(root.path("promptFeedback").has("blockReason")) return bad(Completion.MALFORMED,usage,model);
            var candidates=root.path("candidates");
            if(!candidates.isArray() || candidates.size()!=1) return bad(Completion.MALFORMED,usage,model);
            var candidate=candidates.get(0);
            String finish=candidate.path("finishReason").asText();
            if("MAX_TOKENS".equals(finish)) return bad(Completion.TRUNCATED,usage,model);
            if(!"STOP".equals(finish)) return bad(Completion.MALFORMED,usage,model);
            JsonNode parts=candidate.path("content").path("parts");
            if(!parts.isArray() || parts.isEmpty()) return bad(Completion.MALFORMED,usage,model);
            StringBuilder text=new StringBuilder();
            for(JsonNode part:parts) {
                if(part.path("thought").asBoolean(false)) continue;
                if(!part.path("text").isTextual() || part.has("functionCall") || part.has("inlineData"))
                    return bad(Completion.MALFORMED,usage,model);
                text.append(part.get("text").textValue());
            }
            if(text.toString().codePoints().anyMatch(cp->cp>=0xD800 && cp<=0xDFFF)) return bad(Completion.MALFORMED,usage,model);
            JsonNode proposal=read(text.toString().getBytes(StandardCharsets.UTF_8));
            if(proposal==null || !validator.validate(proposal).isEmpty()) return bad(Completion.MALFORMED,usage,model);
            if (trace != ExtractionTrace.NONE) {
                String structured = text.toString();
                ExtractionTrace.emit(trace, sink -> sink.structuredProposal(structured));
            }
            return new Decoded(Completion.COMPLETE,proposal(note,proposal),usage,model);
        } catch(Exception invalid) { return bad(Completion.MALFORMED,usage,model); }
    }

    private Proposal proposal(String note,JsonNode root) {
        var mentions=new ArrayList<Mention>();
        var starts=new EnumMap<Kind,Integer>(Kind.class);
        for(Kind kind:Kind.values()) {
            starts.put(kind,mentions.size());
            String family=switch(kind) { case DIAGNOSIS -> "diagnoses"; case MEDICATION -> "medications"; case PROCEDURE -> "procedures"; };
            for(JsonNode n:root.get(family)) {
                Text name=text(note,n.get("name")), code=text(note,n.get("mentionedCode"));
                JsonNode c=n.get("context");
                var context=new Context(grounded(note,c.get("assertion"),Assertion.class),
                        grounded(note,c.get("temporality"),Temporality.class),grounded(note,c.get("subject"),Subject.class));
                mentions.add(switch(kind) {
                    case DIAGNOSIS -> new Diagnosis(name,code,context);
                    case MEDICATION -> new Medication(name,code,context,grounded(note,n.get("prescriptionStatus"),PrescriptionStatus.class),
                            text(note,n.get("dosage")),text(note,n.get("frequency")),text(note,n.get("route")),text(note,n.get("duration")));
                    case PROCEDURE -> new Procedure(name,code,context,grounded(note,n.get("performanceStatus"),PerformanceStatus.class));
                });
            }
        }
        var notices=new ArrayList<Notice>();
        for(JsonNode notice:root.get("notices")) {
            var targets=new ArrayList<Target>();
            for(JsonNode target:notice.get("targets")) {
                Kind kind=Kind.valueOf(target.get("kind").asText().toUpperCase(Locale.ROOT));
                String family=switch(kind) { case DIAGNOSIS -> "diagnoses"; case MEDICATION -> "medications"; case PROCEDURE -> "procedures"; };
                if(!target.get("index").canConvertToInt()) throw new IllegalArgumentException();
                int index=target.get("index").intValue();
                if(index<0 || index>=root.get(family).size()) throw new IllegalArgumentException();
                targets.add(new Target(starts.get(kind)+index,Field.valueOf(target.get("field").asText().toUpperCase(Locale.ROOT))));
            }
            notices.add(new Notice(WarningCode.valueOf(notice.get("code").asText()),targets));
        }
        return new Proposal(mentions,notices);
    }

    private Text text(String note,JsonNode n) { return n.isNull()?null:new Text(n.get("value").textValue(),evidence(note,n.get("evidence"))); }
    private <T extends Enum<T>> Grounded<T> grounded(String note,JsonNode n,Class<T> type) {
        return new Grounded<>(Enum.valueOf(type,n.get("value").asText().toUpperCase(Locale.ROOT)),evidence(note,n.get("evidence")));
    }
    private List<Evidence> evidence(String note,JsonNode array) {
        var result=new ArrayList<Evidence>();
        for(JsonNode ref:array) {
            String quote=ref.get("quote").textValue();
            if(!ref.get("occurrence").canConvertToLong()) throw new IllegalArgumentException();
            long occurrence=ref.get("occurrence").longValue();
            int position=-1,from=0;
            if(occurrence<note.length() && EvidencePolicy.nonblankScalar(quote)) {
                for(long i=0;i<=occurrence;i++) {
                    if(Thread.currentThread().isInterrupted()) throw new IllegalStateException("Operation cancelled");
                    position=note.indexOf(quote,from);
                    if(position<0) break;
                    from=position+1;
                }
            }
            int start=position<0?-1:note.codePointCount(0,position);
            result.add(new Evidence(start,start<0?-1:start+quote.codePointCount(0,quote.length()),quote));
        }
        return result;
    }

    private Usage usage(JsonNode n) {
        return new Usage(count(n,"promptTokenCount"),count(n,"candidatesTokenCount"),count(n,"thoughtsTokenCount"),count(n,"cachedContentTokenCount"),count(n,"totalTokenCount"));
    }
    private static Long count(JsonNode node,String field) {
        JsonNode n=node.get(field);
        return n!=null && n.isIntegralNumber() && n.canConvertToLong() && n.longValue()>=0 ? n.longValue():null;
    }
    private JsonNode read(byte[] bytes) throws IOException {
        String value=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        return json.readTree(value);
    }
    private static Decoded bad(Completion completion,Usage usage,String model) { return new Decoded(completion,null,usage,model); }
    private static byte[] resource(String path) throws IOException {
        try(InputStream stream=GeminiProtocol.class.getResourceAsStream(path)) {
            if(stream==null) throw new IOException();
            return stream.readAllBytes();
        }
    }
}

