package com.clinicalintelligence.extraction.infrastructure.gemini;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.*;
import java.nio.charset.*;

/** One bounded SSE generation, no continuation hops and no thought/signature retention. */
final class StudySse {
    record Envelope(byte[] envelope,int events,String finishReason) {}
    private static final ObjectMapper JSON=JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    static Envelope assemble(byte[] bytes,String model) {
        try {
            String raw=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            var state=new State(model);var event=new StringBuilder();
            for(String line:raw.split("\\r\\n|[\\r\\n]",-1)) {
                if(line.isEmpty()){if(!event.isEmpty()){state.accept(event.toString());event.setLength(0);}continue;}
                if(line.startsWith(":"))continue;
                if(line.startsWith("data:")){if(!event.isEmpty())event.append('\n');String data=line.substring(5);event.append(data.startsWith(" ")?data.substring(1):data);}
                else if(!line.startsWith("event:") && !line.startsWith("id:") && !line.startsWith("retry:"))throw new StudyTransport.Stop("SSE_INVALID_FRAMING");
            }
            if(!event.isEmpty())state.accept(event.toString());return state.finish();
        } catch(StudyTransport.Stop controlled){throw controlled;}
        catch(Exception invalid){throw new StudyTransport.Stop("SSE_INVALID_RESPONSE");}
    }
    private static final class State {
        private final String model;private final StringBuilder text=new StringBuilder();private String version,finish="";private JsonNode usage,feedback;private int events;private boolean invalidPart,done;
        State(String model){this.model=model;}
        void accept(String data) throws Exception {
            if(data.equals("[DONE]")){done=true;return;}
            if(done)throw new StudyTransport.Stop("SSE_DATA_AFTER_DONE");
            JsonNode node=JSON.readTree(data);if(node==null || !node.isObject())throw new StudyTransport.Stop("SSE_INVALID_OBJECT");events++;
            if(node.has("modelVersion")) {
                String next=node.path("modelVersion").asText("");
                if(next.matches("[A-Za-z0-9._-]{1,128}")) {
                    if(!next.equals(model) && !next.startsWith(model+"-"))throw new StudyTransport.Stop("MODEL_IDENTITY_MISMATCH");
                    if(version!=null && !version.equals(model) && !next.equals(model) && !version.equals(next))throw new StudyTransport.Stop("MODEL_VERSION_CHANGED_IN_STREAM");
                    version=next;
                }
            }
            JsonNode candidates=node.get("candidates");
            if(candidates!=null) {
                if(!candidates.isArray() || candidates.size()>1)throw new StudyTransport.Stop("SSE_CANDIDATE_MISMATCH");
                if(!candidates.isEmpty()) {
                    var candidate=candidates.get(0);
                    if(candidate.has("index") && (!candidate.path("index").isIntegralNumber() || candidate.path("index").asLong()!=0))throw new StudyTransport.Stop("SSE_CANDIDATE_INDEX");
                    var parts=candidate.path("content").path("parts");
                    if(parts.isArray())for(var part:parts) {
                        if(part.path("thought").asBoolean(false))continue;
                        if(part.has("functionCall") || part.has("inlineData")){invalidPart=true;continue;}
                        if(!part.path("text").isTextual()) {if(part.has("thoughtSignature"))continue;invalidPart=true;continue;}
                        String piece=part.path("text").textValue();if(!finish.isEmpty() && !piece.isEmpty())throw new StudyTransport.Stop("SSE_CONTENT_AFTER_FINISH");text.append(piece);
                    }
                    String nextFinish=candidate.path("finishReason").asText("");
                    if(!nextFinish.isEmpty()){if(!finish.isEmpty() && !finish.equals(nextFinish))throw new StudyTransport.Stop("SSE_FINISH_CHANGED");finish=nextFinish;}
                }
            }
            // Never interpret an intermediate cumulative token count as the final receipt.
            if(node.has("usageMetadata") && !finish.isEmpty())usage=node.get("usageMetadata").deepCopy();
            if(node.has("promptFeedback"))feedback=node.get("promptFeedback").deepCopy();
        }
        Envelope finish() throws Exception {
            if(events==0)throw new StudyTransport.Stop("SSE_EMPTY_RESPONSE");
            var root=JSON.createObjectNode();if(version!=null)root.put("modelVersion",version);if(usage!=null)root.set("usageMetadata",usage);if(feedback!=null)root.set("promptFeedback",feedback);
            var candidate=root.putArray("candidates").addObject().put("finishReason",finish.equals("CONTINUATION")?"MAX_TOKENS":finish);
            var parts=candidate.putObject("content").putArray("parts");
            if(invalidPart)parts.addObject().putObject("functionCall");else parts.addObject().put("text",text.toString());
            return new Envelope(JSON.writeValueAsBytes(root),events,finish);
        }
    }
}
