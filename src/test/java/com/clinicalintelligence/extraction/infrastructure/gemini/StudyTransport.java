package com.clinicalintelligence.extraction.infrastructure.gemini;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.clinicalintelligence.extraction.application.ExtractionTrace;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.*;
import java.net.http.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Explicit study-only transport. No retries, raw envelope retention or credential logging. */
final class StudyTransport implements AutoCloseable {
    static final class Stop extends RuntimeException {
        final String code;final Integer httpStatus;
        Stop(String code){this(code,null);}
        Stop(String code,Integer status){super(code);this.code=code;this.httpStatus=status;}
    }
    record Reply(GeminiProtocol.Decoded decoded,String structuredProposalText,String reportedModelVersion,BigDecimal chargedUsd) {}
    private final HttpClient client;
    private final URI base;
    private final String key;
    private final StudyBudget budget;
    private final int bytes;
    private final Duration deadline;
    private final Supplier<LocalDate> date;
    private boolean stopped;
    private final ObjectMapper json=JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    StudyTransport(String key,StudyBudget budget,URI base,int bytes,Duration deadline,Supplier<LocalDate> date) {
        if(key==null || key.isBlank() || key.length()>4096 || key.chars().anyMatch(c -> c<=32 || c>=127))throw new IllegalArgumentException("Invalid study credential");
        if(base==null || base.getRawUserInfo()!=null || base.getRawQuery()!=null || base.getRawFragment()!=null || !(base.toString().equals("https://generativelanguage.googleapis.com/v1beta/") || base.getScheme().equals("http") && base.getHost().equals("127.0.0.1") && base.getPath().equals("/v1beta/")))throw new IllegalArgumentException("Invalid study endpoint");
        if(bytes<1 || bytes>16_777_216 || deadline==null || deadline.isNegative() || deadline.isZero() || deadline.compareTo(Duration.ofSeconds(120))>0)throw new IllegalArgumentException("Invalid study limits");
        this.key=key;this.budget=Objects.requireNonNull(budget);this.base=base;this.bytes=bytes;this.deadline=deadline;this.date=date;
        client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    synchronized Reply execute(GeminiProtocol protocol,ProviderRequest input,ObjectNode body,long unitDeadline,String stageId,Consumer<JsonNode> sink) {
        if(stopped)throw new Stop("STUDY_ALREADY_STOPPED");
        String model=protocol.identity().model();StudyBudget.Hold hold=null;BigDecimal before=budget.committed();
        long start=System.nanoTime(),end=Math.min(unitDeadline,start+deadline.toNanos());
        try {
            remaining(end);StudyBudget.rate(model,date.get());budget.headroom(model,date.get());
            if(body.at("/generationConfig/maxOutputTokens").asInt()!=65_536)throw new Stop("FROZEN_REQUEST_LIMIT_CHANGED");
            var generate=body.deepCopy().put("model","models/"+model);var count=json.createObjectNode();count.set("generateContentRequest",generate);
            emit(sink,event("COUNT_INTENT",stageId).set("request",generate.deepCopy()));
            long countStart=System.nanoTime();byte[] counted=post(model,":countTokens",count,end);
            JsonNode total=json.readTree(counted).get("totalTokens");
            if(total==null || !total.isIntegralNumber() || !total.canConvertToLong() || total.longValue()<0 || total.longValue()>1_048_576L-65_536)throw new Stop("COUNT_INVALID_OR_CAPACITY_EXCEEDED");
            emit(sink,event("COUNT_RESULT",stageId).put("inputTokens",total.longValue()).put("latencyMillis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-countStart)));
            remaining(end);hold=budget.reserve(model,date.get());
            var intent=event("GENERATION_INTENT",stageId).put("reservationId",hold.id().toString()).put("reservedUsd",hold.usd().toPlainString()).put("transportVersion","t8-sse-1");intent.set("identity",json.valueToTree(protocol.identity()));emit(sink,intent);
            String[] structured={null};var trace=new ExtractionTrace(){@Override public void structuredProposal(String value){structured[0]=value;}};
            long generationStart=System.nanoTime();
            byte[] stream=post(model,":streamGenerateContent?alt=sse",generate,end);
            var assembled=StudySse.assemble(stream,model);byte[] raw=assembled.envelope();
            var decoded=protocol.decode(input.clinicalNote(),raw,trace);
            String reported=null;boolean foreign=false;
            try {
                JsonNode parsed=json.readTree(raw);String version=parsed.path("modelVersion").asText("");
                if(version.matches("[A-Za-z0-9._-]{1,128}")) {
                    if(version.equals(model) || version.startsWith(model+"-"))reported=version;else foreign=true;
                }
            } catch(Exception ignored){ /* malformed envelope is already a decoded failure */ }
            if(foreign)budget.uncertain(hold);else budget.settle(hold,decoded.usage());
            hold=null;
            var result=event("GENERATION_RESULT",stageId).put("completion",decoded.completion().name()).put("usageReconciled",!foreign && !budget.stopped())
                    .put("latencyMillis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)).put("chargedUsd",budget.committed().subtract(before).toPlainString())
                    .put("generationLatencyMillis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-generationStart)).put("streamBytes",stream.length).put("streamEvents",assembled.events()).put("vendorFinishReason",assembled.finishReason()).put("transportVersion","t8-sse-1").put("reportedModelVersion",reported).put("fallbackModel",decoded.modelVersion()).put("structuredProposalText",structured[0]);
            result.set("usage",json.valueToTree(decoded.usage()));result.set("identity",json.valueToTree(protocol.identity()));emit(sink,result);
            if(foreign)throw new Stop("MODEL_IDENTITY_MISMATCH");
            if(budget.stopped())throw new Stop("USAGE_UNRECONCILED");
            remaining(end);
            return new Reply(decoded,structured[0],reported,budget.committed().subtract(before));
        } catch(Exception failure) {
            if(hold!=null)budget.uncertain(hold);
            stopped=true;
            Stop stop=failure instanceof Stop known?known:new Stop(failure instanceof IllegalStateException?"BUDGET_OR_STATE_STOP":"STUDY_TRANSPORT_FAILED");
            try {emit(sink,event("STAGE_STOP",stageId).put("code",stop.code).put("httpStatus",stop.httpStatus).put("committedUsd",budget.committed().toPlainString()));}catch(Stop ignored){}
            throw stop;
        }
    }
    private ObjectNode event(String kind,String stageId){return json.createObjectNode().put("kind",kind).put("stageId",stageId);}
    private static void emit(Consumer<JsonNode> sink,JsonNode value){try{sink.accept(value);}catch(RuntimeException failed){throw new Stop("AUDIT_WRITE_FAILED");}}
    private byte[] post(String model,String operation,JsonNode body,long end) throws Exception {
        remaining(end);
        var request=HttpRequest.newBuilder(base.resolve("models/"+model+operation)).timeout(Duration.ofNanos(remaining(end)))
                .header("x-goog-api-key",key).header("Content-Type","application/json").header("Accept",operation.startsWith(":stream")?"text/event-stream":"application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build();
        CompletableFuture<HttpResponse<byte[]>> future=null;
        try {
            future=client.sendAsync(request,ignored -> new Bounded(bytes));var response=future.get(remaining(end),TimeUnit.NANOSECONDS);
            if(response.statusCode()!=200)throw new Stop(operation.equals(":countTokens")?"COUNT_HTTP_FAILURE":"GENERATION_HTTP_FAILURE",response.statusCode());
            return response.body();
        } catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new Stop("STUDY_INTERRUPTED");}
        catch(TimeoutException timeout){throw new Stop("STUDY_DEADLINE_EXCEEDED");}
        finally{if(future!=null && !future.isDone())future.cancel(true);}
    }
    private static long remaining(long end){long n=end-System.nanoTime();if(n<=0 || Thread.currentThread().isInterrupted())throw new Stop("STUDY_DEADLINE_EXCEEDED");return n;}
    @Override public void close(){client.shutdownNow();}
    @Override public String toString(){return "StudyTransport[credentials=redacted]";}
    private static final class Bounded implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body=new CompletableFuture<>();private final ByteArrayOutputStream data=new ByteArrayOutputStream();private final int limit;private Flow.Subscription subscription;
        Bounded(int limit){this.limit=limit;}
        public CompletionStage<byte[]> getBody(){return body;}
        public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
        public void onNext(List<ByteBuffer> buffers){for(var buffer:buffers){if(buffer.remaining()>limit-data.size()){subscription.cancel();body.completeExceptionally(new Stop("STUDY_RESPONSE_TOO_LARGE"));return;}byte[] b=new byte[buffer.remaining()];buffer.get(b);data.writeBytes(b);}subscription.request(1);}
        public void onError(Throwable failure){body.completeExceptionally(failure);}
        public void onComplete(){body.complete(data.toByteArray());}
    }
}
