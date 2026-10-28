package com.clinicalintelligence.extraction.infrastructure.gemini;

import com.clinicalintelligence.extraction.application.ExtractionProvider;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** One operation owns counting, generation, cancellation and telemetry. No retries or SDK logging. */
public final class GeminiProvider implements ExtractionProvider,AutoCloseable {
    public record Observation(String status,long latencyMillis,GeminiProtocol.Usage usage,String estimatedUsd,String tariff,String effectiveModel) {
        public Observation(String status,long latencyMillis,GeminiProtocol.Usage usage,String estimatedUsd,String tariff) {
            this(status,latencyMillis,usage,estimatedUsd,tariff,null);
        }
    }
    private final GeminiSettings settings;
    private final Consumer<Observation> observer;
    private final String key;
    private final URI base;
    private final HttpClient client;
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore permits;
    private final AtomicBoolean closed=new AtomicBoolean();
    private final GeminiProtocol protocol=new GeminiProtocol();
    private final ObjectMapper json=JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public GeminiProvider(String key,GeminiSettings settings,Consumer<Observation> observer) {
        this(key,settings,observer,URI.create("https://generativelanguage.googleapis.com/v1beta/"));
    }
    GeminiProvider(String key,GeminiSettings settings,Consumer<Observation> observer,URI base) {
        if(key==null || key.isBlank() || key.length()>4096 || key.chars().anyMatch(c->c<=32 || c>=127))
            throw new IllegalArgumentException("Missing or invalid GOOGLE_API_KEY");
        if(base==null || base.getRawUserInfo()!=null || base.getQuery()!=null || base.getFragment()!=null
                || !(base.toString().equals("https://generativelanguage.googleapis.com/v1beta/")
                     || "http".equals(base.getScheme()) && "127.0.0.1".equals(base.getHost()) && base.getPath().equals("/v1beta/")))
            throw new IllegalArgumentException("Invalid Gemini endpoint");
        this.key=key;this.settings=Objects.requireNonNull(settings);this.observer=Objects.requireNonNull(observer);this.base=base;
        this.client=HttpClient.newBuilder().connectTimeout(settings.connectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
        permits=new Semaphore(settings.concurrency());
    }

    @Override public ProviderResponse extract(ProviderRequest request) {
        long start=System.nanoTime(),deadline=start+settings.deadline().toNanos();
        var usage=new AtomicReference<>(new GeminiProtocol.Usage(null,null,null,null));
        var effectiveModel=new AtomicReference<String>();
        var state=new AtomicInteger(0); // 0 reserved, 1 running, 2 released
        Future<ProviderResponse> task=null;
        String status="UNAVAILABLE";
        try {
            if(closed.get()) throw new ProviderFailure(Failure.UNAVAILABLE);
            if(!permits.tryAcquire()) throw new ProviderFailure(Failure.OVERLOADED);
            try {
                task=workers.submit(()->{
                    if(!state.compareAndSet(0,1)) throw new ProviderFailure(Failure.TIMEOUT);
                    try { return work(request,deadline,usage,effectiveModel); }
                    finally { if(state.getAndSet(2)!=2) permits.release(); }
                });
            } catch(RuntimeException scheduling) {
                if(state.compareAndSet(0,2)) permits.release();
                throw new ProviderFailure(Failure.UNAVAILABLE);
            }
            ProviderResponse result=task.get(remaining(deadline),TimeUnit.NANOSECONDS);
            status=result.completion().name();
            return result;
        } catch(TimeoutException timeout) {
            status="TIMEOUT";throw new ProviderFailure(Failure.TIMEOUT);
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();status="TIMEOUT";throw new ProviderFailure(Failure.TIMEOUT);
        } catch(ExecutionException execution) {
            ProviderFailure failure=safeFailure(execution);
            status=failure.failure().name();throw failure;
        } catch(ProviderFailure failure) { status=failure.failure().name();throw failure; }
        finally {
            if(task!=null && !task.isDone()) task.cancel(true);
            if(task!=null && state.compareAndSet(0,2)) permits.release();
            long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
            var current=usage.get();
            try { observer.accept(new Observation(status,elapsed,current,estimate(current,LocalDate.now(ZoneOffset.UTC)),"gemini-3.8-flash-standard-2026-12-31",effectiveModel.get())); }
            catch(RuntimeException ignored) { /* Observability cannot alter a clinical outcome or leak sink failures. */ }
        }
    }

    private ProviderResponse work(ProviderRequest input,long deadline,AtomicReference<GeminiProtocol.Usage> usage,AtomicReference<String> effectiveModel) {
        try {
            var generate=protocol.request(input,settings.outputTokens());
            // Include model in the full protobuf request used by countTokens.
            generate.put("model","models/"+GeminiProtocol.MODEL);
            var countRequest=json.createObjectNode();
            countRequest.set("generateContentRequest",generate);
            JsonNode count=json.readTree(post(":countTokens",json.writeValueAsBytes(countRequest),deadline));
            JsonNode total=count==null?null:count.get("totalTokens");
            if(total==null || !total.isIntegralNumber() || !total.canConvertToLong() || total.longValue()<0)
                throw new ProviderFailure(Failure.INVALID_RESPONSE);
            if(total.longValue()>1_048_576L-settings.outputTokens()) throw new ProviderFailure(Failure.CAPACITY_EXCEEDED);
            var decoded=protocol.decode(input.clinicalNote(),post(":generateContent",json.writeValueAsBytes(generate),deadline));
            usage.set(decoded.usage());
            effectiveModel.set(decoded.modelVersion());
            remaining(deadline);
            return new ProviderResponse(decoded.completion(),decoded.proposal(),
                    new ProviderMetadata(GeminiProtocol.PROMPT_VERSION,ExecutionMode.PROVIDER,"google-gemini",decoded.modelVersion()));
        } catch(ProviderFailure known) { throw known; }
        catch(TimeoutException timeout) { throw new ProviderFailure(Failure.TIMEOUT); }
        catch(Exception invalid) { throw new ProviderFailure(Failure.INVALID_RESPONSE); }
    }

    private byte[] post(String operation,byte[] body,long deadline) {
        CompletableFuture<HttpResponse<byte[]>> future=null;
        try {
            long remaining=remaining(deadline);
            HttpRequest request=HttpRequest.newBuilder(base.resolve("models/"+GeminiProtocol.MODEL+operation))
                    .timeout(Duration.ofNanos(remaining)).header("x-goog-api-key",key).header("Content-Type","application/json")
                    .header("Accept","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            future=client.sendAsync(request,ignored->new LimitedBody(settings.responseBytes()));
            HttpResponse<byte[]> response=future.get(remaining(deadline),TimeUnit.NANOSECONDS);
            if(response.statusCode()!=200) throw new ProviderFailure(Failure.UNAVAILABLE);
            return response.body();
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();throw new ProviderFailure(Failure.TIMEOUT);
        } catch(TimeoutException timeout) { throw new ProviderFailure(Failure.TIMEOUT); }
        catch(ExecutionException execution) { throw safeFailure(execution); }
        finally { if(future!=null && !future.isDone()) future.cancel(true); }
    }

    private static long remaining(long deadline) throws TimeoutException {
        long remaining=deadline-System.nanoTime();
        if(remaining<=0 || Thread.currentThread().isInterrupted()) throw new TimeoutException();
        return remaining;
    }
    private static ProviderFailure safeFailure(Throwable failure) {
        for(Throwable current=failure;current!=null;current=current.getCause()) {
            if(current instanceof ProviderFailure known) return new ProviderFailure(known.failure());
            if(current instanceof HttpTimeoutException || current instanceof TimeoutException || current instanceof CancellationException)
                return new ProviderFailure(Failure.TIMEOUT);
        }
        return new ProviderFailure(Failure.UNAVAILABLE);
    }

    static String estimate(GeminiProtocol.Usage u,LocalDate date) {
        if(date.isAfter(LocalDate.of(2026,12,31)) || u.inputTokens()==null || u.outputTokens()==null
                || u.thoughtTokens()==null || u.cachedTokens()==null || u.cachedTokens()>u.inputTokens()) return null;
        BigDecimal input=BigDecimal.valueOf(u.inputTokens()-u.cachedTokens()).multiply(new BigDecimal("0.75"));
        BigDecimal cached=BigDecimal.valueOf(u.cachedTokens()).multiply(new BigDecimal("0.075"));
        BigDecimal output=BigDecimal.valueOf(u.outputTokens()).add(BigDecimal.valueOf(u.thoughtTokens())).multiply(new BigDecimal("3.75"));
        return input.add(cached).add(output).movePointLeft(6).stripTrailingZeros().toPlainString();
    }

    @Override public void close() { if(closed.compareAndSet(false,true)) { workers.shutdownNow();client.shutdownNow(); } }
    @Override public String toString() { return "GeminiProvider[credentials=redacted]"; }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body=new CompletableFuture<>();
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private final int limit;
        private Flow.Subscription subscription;
        LimitedBody(int limit) { this.limit=limit; }
        public CompletionStage<byte[]> getBody() { return body; }
        public void onSubscribe(Flow.Subscription subscription) { this.subscription=subscription;subscription.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for(ByteBuffer buffer:buffers) {
                if(buffer.remaining()>limit-bytes.size()) {
                    subscription.cancel();body.completeExceptionally(new ProviderFailure(Failure.INVALID_RESPONSE));return;
                }
                byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { body.completeExceptionally(error); }
        public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}

