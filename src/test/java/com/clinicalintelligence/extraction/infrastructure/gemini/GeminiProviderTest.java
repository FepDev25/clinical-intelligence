package com.clinicalintelligence.extraction.infrastructure.gemini;

import static org.junit.jupiter.api.Assertions.*;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static com.clinicalintelligence.extraction.application.ExtractionProvider.*;
import com.sun.net.httpserver.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

class GeminiProviderTest {
    final ObjectMapper json=new ObjectMapper();
    HttpServer server; java.util.concurrent.ExecutorService workers;
    final List<String> paths=new CopyOnWriteArrayList<>();
    final List<JsonNode> bodies=new CopyOnWriteArrayList<>();
    final List<GeminiProvider.Observation> observations=new CopyOnWriteArrayList<>();
    volatile String key;
    volatile BiConsumer<HttpExchange,String> handler;
    @BeforeEach void start() throws Exception {
        key="synthetic-test-key-"+UUID.randomUUID();
        server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        workers=Executors.newCachedThreadPool(); server.setExecutor(workers);
        handler=(exchange,path) -> {
            try { reply(exchange,200,path.endsWith(":countTokens")?"{\"totalTokens\":10}":new String(GeminiProtocolTest.envelope(GeminiProtocolTest.proposal("asma",0),"STOP"),StandardCharsets.UTF_8)); }
            catch(Exception e) { throw new AssertionError(e); }
        };
        server.createContext("/",exchange -> {
            try {
                assertEquals(key,exchange.getRequestHeaders().getFirst("x-goog-api-key"));
                assertNull(exchange.getRequestURI().getQuery());
                String path=exchange.getRequestURI().getPath();paths.add(path);
                bodies.add(json.readTree(exchange.getRequestBody().readAllBytes()));handler.accept(exchange,path);
            } finally { exchange.close(); }
        });server.start();
    }
    @AfterEach void stop() { server.stop(0);workers.shutdownNow(); }
    static void reply(HttpExchange exchange,int code,String body) throws Exception {
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(code,bytes.length);exchange.getResponseBody().write(bytes);
    }
    GeminiProvider provider(GeminiSettings settings) {
        return new GeminiProvider(key,settings,observations::add,URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1beta/"));
    }
    static ProviderRequest request() { return new ProviderRequest("asma","es"); }
    @Test void explicitCaptureIsPerCallAndNeverRetainedOnSharedProvider() throws Exception {
        var trace = new SyntheticTraceReplayTest.RecordingTrace();
        try (var provider = provider(GeminiSettings.defaults())) {
            var result = new com.clinicalintelligence.extraction.application.ExtractionService(
                    request -> provider.extract(request, trace)).extract(Command.note("asma"), trace);
            assertNotNull(trace.structured);
            assertSame(result, trace.result);
            assertEquals(1, trace.structuredCalls);
            provider.extract(request());
            assertEquals(1, trace.structuredCalls);
            assertEquals(2, observations.size());
            assertTrue(observations.stream().noneMatch(o -> o.toString().contains("asma") || o.toString().contains(key)));
        }
    }
    @Test void concurrentCallsKeepSeparateTraceSinksAndOriginalOffsets() throws Exception {
        var a = new SyntheticTraceReplayTest.RecordingTrace();
        var b = new SyntheticTraceReplayTest.RecordingTrace();
        try (var provider = provider(GeminiSettings.defaults()); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> new com.clinicalintelligence.extraction.application.ExtractionService(
                    request -> provider.extract(request, a)).extract(Command.note("asma"), a));
            var second = executor.submit(() -> new com.clinicalintelligence.extraction.application.ExtractionService(
                    request -> provider.extract(request, b)).extract(Command.note("🩺 otro texto: asma"), b));
            assertSame(first.get(), a.result); assertSame(second.get(), b.result);
            assertEquals(0, a.decoded.mentions().getFirst().name().evidence().getFirst().start());
            assertEquals(14, b.decoded.mentions().getFirst().name().evidence().getFirst().start());
            assertEquals(1,a.structuredCalls); assertEquals(1,b.structuredCalls);
        }
    }
    @Test void traceDeliveryDoesNotHoldGenerationCapacityOrChangeFixedOutcome() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var trace=new com.clinicalintelligence.extraction.application.ExtractionTrace() {
            @Override public void structuredProposal(String value) {
                entered.countDown();
                try { assertTrue(release.await(3,TimeUnit.SECONDS)); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        };
        var settings=new GeminiSettings(Duration.ofSeconds(2),Duration.ofSeconds(1),100,1,10000);
        try(var provider=provider(settings);var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=executor.submit(() -> provider.extract(request(),trace));
            try {
                assertTrue(entered.await(2,TimeUnit.SECONDS));
                assertEquals(Completion.COMPLETE,provider.extract(request()).completion());
                assertTrue(observations.stream().allMatch(o -> o.status().equals("COMPLETE")));
            } finally { release.countDown(); }
            assertEquals(Completion.COMPLETE,first.get().completion());
        }
    }
    @Test void countsCompleteRequestBeforeGeneratingAndRecordsSafeUsage() throws Exception {
        try(var provider=provider(GeminiSettings.defaults())) {
            var response=provider.extract(request());
            assertEquals(Completion.COMPLETE,response.completion());assertEquals(ExecutionMode.PROVIDER,response.metadata().executionMode());
            assertEquals("gemini-3.8-flash",response.metadata().model());
            assertEquals(2,paths.size());assertTrue(paths.get(0).endsWith(":countTokens"));assertTrue(paths.get(1).endsWith(":generateContent"));
            assertEquals(bodies.get(1),bodies.get(0).get("generateContentRequest"));
            assertFalse(bodies.get(1).has("tools"));
            assertEquals(1,observations.size());assertEquals("COMPLETE",observations.getFirst().status());
            assertEquals(100L,observations.getFirst().usage().inputTokens());
            assertFalse(observations.getFirst().toString().contains(key));assertFalse(observations.getFirst().toString().contains("asma"));
        }
    }
    @Test void refusesCapacityWithoutCallingGeneration() throws Exception {
        handler=(exchange,path)->{try{reply(exchange,200,"{\"totalTokens\":1048576}");}catch(Exception e){throw new AssertionError(e);}};
        try(var provider=provider(GeminiSettings.defaults())) {
            assertEquals(Failure.CAPACITY_EXCEEDED,assertThrows(ProviderFailure.class,()->provider.extract(request())).failure());
            assertEquals(1,paths.size());
        }
    }
    @Test void providerHttpErrorsAreSafeAndNeverRetried() throws Exception {
        for(int status:new int[]{400,401,403,404,429,500,503}) {
            paths.clear();
            handler=(exchange,path)->{try{reply(exchange,status,"SENSITIVE_MARKER "+key);}catch(Exception e){throw new AssertionError(e);}};
            try(var provider=provider(GeminiSettings.defaults())) {
                var e=assertThrows(ProviderFailure.class,()->provider.extract(request()));
                assertEquals(Failure.UNAVAILABLE,e.failure());assertNull(e.getCause());
                assertFalse(e.toString().contains(key));assertEquals(1,paths.size());
            }
        }
    }
    @Test void deadlineCoversSlowBodyAndReleasesCapacity() throws Exception {
        handler=(exchange,path)->{try{exchange.sendResponseHeaders(200,0);exchange.getResponseBody().flush();Thread.sleep(2000);}catch(Exception ignored){}};
        var settings=new GeminiSettings(Duration.ofMillis(250),Duration.ofMillis(100),100,1,10000);
        try(var provider=provider(settings)) {
            long start=System.nanoTime();
            assertEquals(Failure.TIMEOUT,assertThrows(ProviderFailure.class,()->provider.extract(request())).failure());
            assertTrue(Duration.ofNanos(System.nanoTime()-start).toMillis()<1500);
            handler=(exchange,path)->{try{reply(exchange,200,path.endsWith(":countTokens")?"{\"totalTokens\":10}":new String(GeminiProtocolTest.envelope("{\"diagnoses\":[],\"medications\":[],\"procedures\":[],\"notices\":[]}","STOP"),StandardCharsets.UTF_8));}catch(Exception e){throw new AssertionError(e);}};
            // Deadline return and interrupted worker cleanup are asynchronous;
            // capacity remains held until that worker actually exits.
            ProviderResponse recovered=null;
            long recoveryDeadline=System.nanoTime()+Duration.ofSeconds(2).toNanos();
            while(recovered==null && System.nanoTime()<recoveryDeadline) {
                try { recovered=provider.extract(request()); }
                catch(ProviderFailure transientCleanup) {
                    assertEquals(Failure.OVERLOADED,transientCleanup.failure());Thread.sleep(10);
                }
            }
            assertNotNull(recovered);assertEquals(Completion.COMPLETE,recovered.completion());
        }
    }
    @Test void saturatedProviderDoesNotQueueOrStartAnotherCall() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        handler=(exchange,path)->{try{entered.countDown();release.await(3,TimeUnit.SECONDS);reply(exchange,200,"{\"totalTokens\":1048576}");}catch(Exception ignored){}};
        var settings=new GeminiSettings(Duration.ofSeconds(3),Duration.ofSeconds(1),100,1,10000);
        try(var provider=provider(settings);var executor=Executors.newSingleThreadExecutor()) {
            var running=executor.submit(()->assertThrows(ProviderFailure.class,()->provider.extract(request())));
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            assertEquals(Failure.OVERLOADED,assertThrows(ProviderFailure.class,()->provider.extract(request())).failure());
            assertEquals(1,paths.size());release.countDown();running.get();
        } finally {release.countDown();}
    }
    @Test void boundsResponseBytesAndRejectsMalformedTokenCount() throws Exception {
        for(String body:new String[]{"x".repeat(20000),"{}","{\"totalTokens\":-1}","{\"totalTokens\":1.5}"}) {
            paths.clear();handler=(exchange,path)->{try{reply(exchange,200,body);}catch(Exception ignored){}};
            try(var provider=provider(new GeminiSettings(Duration.ofSeconds(3),Duration.ofSeconds(1),100,1,10000))) {
                assertEquals(Failure.INVALID_RESPONSE,assertThrows(ProviderFailure.class,()->provider.extract(request())).failure());assertEquals(1,paths.size());
            }
        }
    }
    @Test void deadlineIsSharedByCountingAndGeneration() throws Exception {
        handler=(exchange,path)->{try{
            Thread.sleep(path.endsWith(":countTokens")?500:1700);
            reply(exchange,200,path.endsWith(":countTokens")?"{\"totalTokens\":10}":new String(GeminiProtocolTest.envelope("{\"diagnoses\":[],\"medications\":[],\"procedures\":[],\"notices\":[]}","STOP"),StandardCharsets.UTF_8));
        }catch(Exception ignored){}};
        try(var provider=provider(new GeminiSettings(Duration.ofSeconds(2),Duration.ofMillis(500),100,1,10000))) {
            assertEquals(Failure.TIMEOUT,assertThrows(ProviderFailure.class,()->provider.extract(request())).failure());
            assertEquals(2,paths.size());
        }
    }
    @Test void generationFinalizationAndUnknownUsageAreObservedWithoutText() throws Exception {
        for(String finish:List.of("STOP","MAX_TOKENS","SAFETY","")) {
            observations.clear();
            handler=(exchange,path)->{try{
                if(path.endsWith(":countTokens")) reply(exchange,200,"{\"totalTokens\":10}");
                else {
                    var raw=json.readTree(GeminiProtocolTest.envelope("{\"diagnoses\":[],\"medications\":[],\"procedures\":[],\"notices\":[]}",finish));
                    ((com.fasterxml.jackson.databind.node.ObjectNode)raw).remove("usageMetadata");reply(exchange,200,raw.toString());
                }
            }catch(Exception e){throw new AssertionError(e);}};
            try(var provider=provider(GeminiSettings.defaults())) {
                assertEquals(finish.equals("STOP")?Completion.COMPLETE:finish.equals("MAX_TOKENS")?Completion.TRUNCATED:Completion.MALFORMED,provider.extract(request()).completion());
                assertNull(observations.getFirst().usage().inputTokens());assertNull(observations.getFirst().estimatedUsd());
            }
        }
    }
    @Test void callerCancellationInterruptsWorkAndReportsTimeout() throws Exception {
        var entered=new CountDownLatch(1);
        handler=(exchange,path)->{try{entered.countDown();Thread.sleep(4000);reply(exchange,200,"{\"totalTokens\":10}");}catch(Exception ignored){}};
        try(var provider=provider(new GeminiSettings(Duration.ofSeconds(5),Duration.ofSeconds(1),100,1,10000));
                var callers=Executors.newSingleThreadExecutor()) {
            var call=callers.submit(()->provider.extract(request()));
            assertTrue(entered.await(2,TimeUnit.SECONDS));call.cancel(true);
            long end=System.nanoTime()+Duration.ofSeconds(2).toNanos();
            while(observations.isEmpty() && System.nanoTime()<end) Thread.sleep(10);
            assertEquals("TIMEOUT",observations.getFirst().status());
            assertEquals(1,paths.size());
        }
    }
    @Test void redirectNeverForwardsTheApiKeyToAnotherPath() throws Exception {
        handler=(exchange,path)->{try{
            exchange.getResponseHeaders().set("Location","http://127.0.0.1:"+server.getAddress().getPort()+"/stolen");
            reply(exchange,302,"redirect");
        }catch(Exception ignored){}};
        try(var provider=provider(GeminiSettings.defaults())) {
            assertEquals(Failure.UNAVAILABLE,assertThrows(ProviderFailure.class,()->provider.extract(request())).failure());
            assertEquals(1,paths.size());assertFalse(paths.contains("/stolen"));
        }
    }
    @Test void evaluationCanRecordTotalUsageAndEffectiveVersionWithoutGuessingMissingFields() throws Exception {
        handler=(exchange,path)->{try {
            if(path.endsWith(":countTokens")) reply(exchange,200,"{\"totalTokens\":10}");
            else {
                var raw=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(GeminiProtocolTest.envelope("{\"diagnoses\":[],\"medications\":[],\"procedures\":[],\"notices\":[]}","STOP"));
                raw.put("modelVersion","gemini-3.8-flash-test");
                ((com.fasterxml.jackson.databind.node.ObjectNode)raw.get("usageMetadata")).put("totalTokenCount",130);
                reply(exchange,200,raw.toString());
            }
        }catch(Exception error){throw new AssertionError(error);}};
        try(var provider=provider(GeminiSettings.defaults())) {
            provider.extract(request());
            assertEquals(130L,observations.getFirst().usage().totalTokens());
            assertEquals("gemini-3.8-flash-test",observations.getFirst().effectiveModel());
        }
    }
}
