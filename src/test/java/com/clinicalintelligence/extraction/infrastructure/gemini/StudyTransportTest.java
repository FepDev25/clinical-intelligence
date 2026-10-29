package com.clinicalintelligence.extraction.infrastructure.gemini;
import static org.junit.jupiter.api.Assertions.*;
import static com.clinicalintelligence.extraction.infrastructure.gemini.GeminiProtocolTest.*;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
class StudyTransportTest {
    HttpServer server;ExecutorService workers;List<JsonNode> bodies;List<JsonNode> events;AtomicInteger calls;
    volatile String generation;volatile int code;
    @BeforeEach void setup() throws Exception {
        bodies=new ArrayList<>();events=new ArrayList<>();calls=new AtomicInteger();code=200;
        generation=new String(envelope(proposal("asma",0),"STOP"),java.nio.charset.StandardCharsets.UTF_8);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);workers=Executors.newCachedThreadPool();server.setExecutor(workers);
        server.createContext("/",e -> {try {
            calls.incrementAndGet();assertEquals("synthetic-test-key",e.getRequestHeaders().getFirst("x-goog-api-key"));
            synchronized(bodies){bodies.add(JSON.readTree(e.getRequestBody().readAllBytes()));}
            boolean count=e.getRequestURI().getPath().endsWith(":countTokens");
            byte[] data=(count?"{\"totalTokens\":100}":"data: "+generation+"\n\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            e.sendResponseHeaders(count?200:code,data.length);e.getResponseBody().write(data);
        } finally {e.close();}});server.start();
    }
    @AfterEach void close(){server.stop(0);workers.shutdownNow();}
    StudyTransport transport(StudyBudget b,int bytes) {return new StudyTransport("synthetic-test-key",b,URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1beta/"),bytes,Duration.ofSeconds(2),() -> LocalDate.of(2026,10,28));}
    @Test void countsIdenticalCompleteRequestAndPersistsIntentBeforeAnyGeneration() throws Exception {
        var budget=new StudyBudget(new BigDecimal("20"));var p=new GeminiProtocol();
        try(var t=transport(budget,16777216)) {
            var r=t.execute(p,new ProviderRequest("asma","es"),p.request(new ProviderRequest("asma","es"),65536),System.nanoTime()+TimeUnit.SECONDS.toNanos(5),"unit:single",e -> events.add(e.deepCopy()));
            assertEquals(Completion.COMPLETE,r.decoded().completion());assertEquals(2,calls.get());
            assertEquals(bodies.get(1),bodies.get(0).path("generateContentRequest"));
            assertEquals("GENERATION_INTENT",events.stream().filter(e -> e.path("kind").asText().equals("GENERATION_INTENT")).findFirst().orElseThrow().path("kind").asText());
            assertFalse(JSON.writeValueAsString(events).contains("synthetic-test-key"));
            assertTrue(events.stream().anyMatch(e -> e.path("structuredProposalText").asText().contains("diagnoses")));
        }
    }
    @Test void insufficientBudgetOrBrokenAuditPreventsNetwork() throws Exception {
        var p=new GeminiProtocol();var input=new ProviderRequest("asma","es");
        try(var t=transport(new StudyBudget(new BigDecimal("1")),16777216)) {
            assertThrows(StudyTransport.Stop.class,() -> t.execute(p,input,p.request(input,65536),System.nanoTime()+TimeUnit.SECONDS.toNanos(5),"x",events::add));
        }
        try(var t=transport(new StudyBudget(new BigDecimal("20")),16777216)) {
            assertThrows(StudyTransport.Stop.class,() -> t.execute(p,input,p.request(input,65536),System.nanoTime()+TimeUnit.SECONDS.toNanos(5),"y",e -> {throw new IllegalStateException("sensitive fake detail");}));
        }
        assertEquals(0,calls.get());
    }
    @Test void httpFailureKeepsReservationStopsFurtherCallsAndNeverSavesErrorBody() throws Exception {
        code=403;generation="sensitive synthetic error detail";
        var b=new StudyBudget(new BigDecimal("20"));var p=new GeminiProtocol();var input=new ProviderRequest("asma","es");
        try(var t=transport(b,16777216)) {
            var ex=assertThrows(StudyTransport.Stop.class,() -> t.execute(p,input,p.request(input,65536),System.nanoTime()+TimeUnit.SECONDS.toNanos(5),"a",events::add));
            assertNull(ex.getCause());assertEquals(new BigDecimal("1.032192"),b.committed());
            assertThrows(StudyTransport.Stop.class,() -> t.execute(p,input,p.request(input,65536),System.nanoTime()+TimeUnit.SECONDS.toNanos(5),"b",events::add));
            assertEquals(2,calls.get());assertFalse(JSON.writeValueAsString(events).contains(generation));
        }
    }
    @Test void rawThoughtPartsAreNeverRetained() throws Exception {
        var raw=JSON.readTree(generation);((com.fasterxml.jackson.databind.node.ArrayNode)raw.at("/candidates/0/content/parts")).addObject().put("thought",true).put("text","sensitive synthetic thoughts");generation=raw.toString();
        var p=new GeminiProtocol();var input=new ProviderRequest("asma","es");
        try(var t=transport(new StudyBudget(new BigDecimal("20")),16777216)) {
            t.execute(p,input,p.request(input,65536),System.nanoTime()+TimeUnit.SECONDS.toNanos(5),"x",events::add);
            assertFalse(JSON.writeValueAsString(events).contains("sensitive synthetic thoughts"));
        }
    }
    @Test void expiredDeadlineAndOversizedResponseAreControlled() throws Exception {
        var p=new GeminiProtocol();var input=new ProviderRequest("asma","es");
        try(var t=transport(new StudyBudget(new BigDecimal("20")),16777216)) {
            assertThrows(StudyTransport.Stop.class,() -> t.execute(p,input,p.request(input,65536),System.nanoTime()-1,"expired",events::add));
            assertEquals(0,calls.get());
        }
        generation="x".repeat(2000);
        try(var t=transport(new StudyBudget(new BigDecimal("20")),1000)) {
            assertThrows(StudyTransport.Stop.class,() -> t.execute(p,input,p.request(input,65536),System.nanoTime()+TimeUnit.SECONDS.toNanos(5),"oversized",events::add));
        }
    }
    @Test void presentReceiptWithMissingUsageIsExplicitlyUnreconciled() throws Exception {
        var raw=(com.fasterxml.jackson.databind.node.ObjectNode)JSON.readTree(generation);raw.remove("usageMetadata");generation=raw.toString();
        var b=new StudyBudget(new BigDecimal("20"));var p=new GeminiProtocol();var input=new ProviderRequest("asma","es");
        try(var t=transport(b,16777216)) {
            var stop=assertThrows(StudyTransport.Stop.class,() -> t.execute(p,input,p.request(input,65536),System.nanoTime()+TimeUnit.SECONDS.toNanos(5),"unknown",events::add));
            assertEquals("USAGE_UNRECONCILED",stop.code);
            var receipt=events.stream().filter(e -> e.path("kind").asText().equals("GENERATION_RESULT")).findFirst().orElseThrow();
            assertTrue(receipt.path("usageReconciled").isBoolean());assertFalse(receipt.path("usageReconciled").asBoolean());
            assertEquals(new BigDecimal("1.032192"),b.committed());
        }
    }
    @Test void foreignModelIdentityCannotReleaseAReservationUsingTheWrongTariff() throws Exception {
        var raw=JSON.readTree(generation);((com.fasterxml.jackson.databind.node.ObjectNode)raw).put("modelVersion","gemini-4-argon");generation=raw.toString();
        var b=new StudyBudget(new BigDecimal("20"));var p=new GeminiProtocol();var input=new ProviderRequest("asma","es");
        try(var t=transport(b,16777216)) {
            assertThrows(StudyTransport.Stop.class,() -> t.execute(p,input,p.request(input,65536),System.nanoTime()+TimeUnit.SECONDS.toNanos(5),"wrong-model",events::add));
            assertEquals(new BigDecimal("1.032192"),b.committed());assertTrue(b.stopped());
        }
    }
}
