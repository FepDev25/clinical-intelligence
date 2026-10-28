package com.clinicalintelligence.http;

import com.clinicalintelligence.ClinicalIntelligenceApplication;
import com.clinicalintelligence.extraction.application.ExtractionProvider;
import com.clinicalintelligence.extraction.infrastructure.ScriptedExtractionProvider;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiIntegrationTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String KEY = CredentialStoreTest.token(), DENIED = CredentialStoreTest.token(), SECOND = CredentialStoreTest.token();
    static final AtomicInteger CALLS = new AtomicInteger();
    static final AtomicReference<ExtractionProvider> SCRIPT = new AtomicReference<>();
    static ServletWebServerApplicationContext context;
    static Path credentials;
    static URI uri;
    static final HttpClient CLIENT = HttpClient.newHttpClient();

    static class ProviderConfig {
        @Bean @Primary ExtractionProvider controlledProvider() {
            return request -> { CALLS.incrementAndGet(); return SCRIPT.get().extract(request); };
        }
    }
    @BeforeAll static void start() throws Exception {
        credentials = Files.createTempFile("ci-h3-test-credentials-", ".json");
        Files.writeString(credentials,"["+CredentialStoreTest.entry(KEY,"a","[\"extractions:write\"]")+","+CredentialStoreTest.entry(DENIED,"b","[]")+","+CredentialStoreTest.entry(SECOND,"b","[\"extractions:write\"]")+"]");
        context = (ServletWebServerApplicationContext) IsolatedApplication.builder(ClinicalIntelligenceApplication.class,ProviderConfig.class)
                .properties(Map.of("ci.credentials-file",credentials.toString(),"ci.provider-mode","simulated",
                        "ci.body-bytes","16384","ci.note-code-points","2000","ci.catalogs","5","ci.entries","10","ci.aliases","10",
                        "server.address","127.0.0.1","spring.main.banner-mode","off")).run("--server.port=0");
        uri = URI.create("http://127.0.0.1:"+context.getWebServer().getPort()+"/api/v1/extractions");
    }
    @AfterAll static void stop() throws Exception { if(context!=null) context.close(); Files.deleteIfExists(credentials); }
    @BeforeEach void reset() {
        CALLS.set(0); SCRIPT.set(new ScriptedExtractionProvider(request -> new Proposal(List.of(),List.of())));
    }
    HttpResponse<String> send(String body,String key,Map<String,String> headers) throws Exception {
        var builder = HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.ofString(body));
        if(key!=null) builder.header("X-API-Key",key);
        headers.forEach(builder::header);
        return CLIENT.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> send(String body) throws Exception { return send(body,KEY,Map.of("Content-Type","application/json")); }
    JsonNode body(HttpResponse<String> r) throws Exception { return JSON.readTree(r.body()); }
    void problem(HttpResponse<String> r,int status,String code) throws Exception {
        assertEquals(status,r.statusCode(),r.body());
        var json = body(r);
        assertEquals(code,json.path("code").asText());
        assertEquals(status,json.path("status").asInt());
        assertEquals("urn:clinical-intelligence:problem:"+code.toLowerCase(Locale.ROOT).replace('_','-'),json.path("type").asText());
        assertEquals("urn:uuid:"+json.path("requestId").asText(),json.path("instance").asText());
        assertEquals(json.path("requestId").asText(),r.headers().firstValue("X-Request-Id").orElseThrow());
        assertEquals("no-store",r.headers().firstValue("Cache-Control").orElseThrow());
        assertTrue(r.headers().firstValue("Content-Type").orElseThrow().startsWith("application/problem+json"));
        assertFalse(r.body().contains(KEY)); assertFalse(r.body().contains("SENSITIVE_MARKER"));
        capture(r);
    }
    static void capture(HttpResponse<String> r) throws Exception {
        ContractSchemas.validate(JSON.readTree(r.body()),r.statusCode());
        Path dir=Path.of("target/h3-contract"); Files.createDirectories(dir);
        String id=UUID.randomUUID().toString();
        Files.writeString(dir.resolve(id+".json"),JSON.writeValueAsString(Map.of("status",r.statusCode(),"body",JSON.readTree(r.body()),
                "headers",r.headers().map())));
    }

    @Test void successUsesServerIdExplicitSimulationAndNoSession() throws Exception {
        var r=send("{\"clinicalNote\":\"nota\"}",KEY,Map.of("Content-Type","application/json","X-Request-Id","SENSITIVE_MARKER"));
        assertEquals(200,r.statusCode(),r.body()); var json=body(r);
        assertEquals("simulated",json.at("/processing/executionMode").asText());
        assertEquals("es",json.path("locale").asText()); assertTrue(json.path("reviewRequired").asBoolean());
        assertEquals("completed",json.path("processingStatus").asText());
        assertEquals(json.path("requestId").asText(),r.headers().firstValue("X-Request-Id").orElseThrow());
        assertNotEquals("SENSITIVE_MARKER",json.path("requestId").asText());
        assertEquals("no-store",r.headers().firstValue("Cache-Control").orElseThrow());
        assertTrue(r.headers().firstValue("Set-Cookie").isEmpty()); assertEquals(1,CALLS.get()); capture(r);
    }
    @Test void authAndScopeRunBeforeParsingOrProvider() throws Exception {
        for(String key:new String[]{null,CredentialStoreTest.token()}) {
            var r=send("SENSITIVE_MARKER",key,Map.of("Content-Type","application/json"));
            problem(r,401,"INVALID_API_KEY");
            assertEquals("ApiKey realm=\"clinical-intelligence\"",r.headers().firstValue("WWW-Authenticate").orElseThrow());
        }
        problem(send("SENSITIVE_MARKER",DENIED,Map.of("Content-Type","application/json")),403,"INSUFFICIENT_SCOPE");
        var duplicate = HttpRequest.newBuilder(uri).header("X-API-Key",KEY).header("X-API-Key",KEY)
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        problem(CLIENT.send(duplicate,HttpResponse.BodyHandlers.ofString()),401,"INVALID_API_KEY");
        assertEquals(0,CALLS.get());
    }
    @Test void mediaEncodingNegotiationAndMethodUseProblemContract() throws Exception {
        problem(send("{}",KEY,Map.of("Content-Type","text/plain")),415,"UNSUPPORTED_MEDIA_TYPE");
        problem(send("{}",KEY,Map.of("Content-Type","application/json; charset=iso-8859-1")),415,"UNSUPPORTED_MEDIA_TYPE");
        problem(send("{}",KEY,Map.of("Content-Type","application/json","Content-Encoding","gzip")),415,"UNSUPPORTED_CONTENT_ENCODING");
        problem(send("{}",KEY,Map.of("Content-Type","application/json","Accept","text/html")),406,"NOT_ACCEPTABLE");
        problem(send("{}",KEY,Map.of("Content-Type","application/json","Accept","application/json;q=0, */*;q=1")),406,"NOT_ACCEPTABLE");
        var r=CLIENT.send(HttpRequest.newBuilder(uri).header("X-API-Key",KEY).GET().build(),HttpResponse.BodyHandlers.ofString());
        problem(r,405,"METHOD_NOT_ALLOWED"); assertEquals("POST",r.headers().firstValue("Allow").orElseThrow());
        assertEquals(0,CALLS.get());
    }
    @Test void invalidInputsNeverInvokeProvider() throws Exception {
        for(String json:List.of("{","{}","{\"clinicalNote\":null}","{\"clinicalNote\":42}",
                "{\"clinicalNote\":\"SENSITIVE_MARKER\",\"tenantId\":\"a\"}",
                "{\"clinicalNote\":\"a\",\"clinicalNote\":\"b\"}","{\"clinicalNote\":\"\\ud800\"}")) {
            var r=send(json); assertEquals(400,r.statusCode(),r.body()); capture(r);
            assertFalse(r.body().contains("SENSITIVE_MARKER"));
        }
        problem(send("{\"clinicalNote\":\"nota\",\"locale\":\"es-AB\"}"),400,"INVALID_LOCALE");
        problem(send("{\"clinicalNote\":\"nota\",\"locale\":\"en-US\"}"),422,"UNSUPPORTED_LOCALE");
        assertEquals(0,CALLS.get());
    }
    @Test void rawBytesAndChunkedBodiesAreBoundedWithoutTruncation() throws Exception {
        problem(send("{\"clinicalNote\":\""+"a".repeat(2001)+"\"}"),413,"INPUT_LIMIT_EXCEEDED");
        String raw="{\"clinicalNote\":\"nota\"}"+" ".repeat(17000);
        problem(send(raw),413,"INPUT_LIMIT_EXCEEDED");
        var publisher=HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
        var r=CLIENT.send(HttpRequest.newBuilder(uri).header("X-API-Key",KEY).header("Content-Type","application/json").POST(publisher).build(),HttpResponse.BodyHandlers.ofString());
        problem(r,413,"INPUT_LIMIT_EXCEEDED"); assertEquals(0,CALLS.get());
    }
    @Test void limitsUseOriginalBytesCodePointsAndAggregateStructure() throws Exception {
        String exact="{\"clinicalNote\":\"nota\"}"+" ".repeat(16384-"{\"clinicalNote\":\"nota\"}".getBytes(StandardCharsets.UTF_8).length);
        assertEquals(200,send(exact).statusCode());
        problem(send(exact+" "),413,"INPUT_LIMIT_EXCEEDED");
        assertEquals(200,send("{\"clinicalNote\":\""+"🩺".repeat(2000)+"\"}").statusCode());
        int before=CALLS.get();
        List<Map<String,Object>> catalogs=new ArrayList<>();
        for(int i=0;i<6;i++) catalogs.add(Map.of("catalogId","c"+i,"version","1","kind","diagnosis","entries",List.of()));
        problem(send(JSON.writeValueAsString(Map.of("clinicalNote","nota","catalogs",catalogs))),413,"INPUT_LIMIT_EXCEEDED");
        List<Map<String,Object>> entries=new ArrayList<>();
        for(int i=0;i<11;i++) entries.add(Map.of("entryId","e"+i,"name","A"));
        var catalog=Map.of("catalogId","c","version","1","kind","diagnosis","entries",entries);
        problem(send(JSON.writeValueAsString(Map.of("clinicalNote","nota","catalogs",List.of(catalog)))),413,"INPUT_LIMIT_EXCEEDED");
        List<String> aliases=java.util.stream.IntStream.range(0,11).mapToObj(i -> "alias"+i).toList();
        var aliasCatalog=Map.of("catalogId","c","version","1","kind","diagnosis","entries",List.of(Map.of("entryId","e","name","A","aliases",aliases)));
        problem(send(JSON.writeValueAsString(Map.of("clinicalNote","nota","catalogs",List.of(aliasCatalog)))),413,"INPUT_LIMIT_EXCEEDED");
        assertEquals(before,CALLS.get());
    }

    @Test void rejectsInvalidUtf8AndNestedDuplicateKeysOverHttp() throws Exception {
        byte[] invalid = new byte[]{'{','"', 'c', '"',':','"',(byte)0xc0,(byte)0xaf,'"','}'};
        var request=HttpRequest.newBuilder(uri).header("X-API-Key",KEY).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(invalid)).build();
        problem(CLIENT.send(request,HttpResponse.BodyHandlers.ofString()),400,"MALFORMED_JSON");
        problem(send("{\"clinicalNote\":\"nota\",\"catalogs\":[{\"catalogId\":\"a\",\"version\":\"1\",\"kind\":\"diagnosis\",\"entries\":[],\"entries\":[]}]}"),400,"MALFORMED_JSON");
        assertEquals(0,CALLS.get());
    }
    @Test void mapsProviderFailuresWithoutRawMessages() throws Exception {
        SCRIPT.set(request -> { throw new RuntimeException("SENSITIVE_MARKER "+KEY); });
        problem(send("{\"clinicalNote\":\"nota\"}"),503,"PROVIDER_UNAVAILABLE");
        SCRIPT.set(request -> new ProviderResponse(Completion.TRUNCATED,null,null));
        problem(send("{\"clinicalNote\":\"nota\"}"),502,"PROVIDER_TRUNCATED_RESPONSE");
        SCRIPT.set(request -> new ProviderResponse(Completion.MALFORMED,null,null));
        problem(send("{\"clinicalNote\":\"nota\"}"),502,"PROVIDER_INVALID_RESPONSE");
        SCRIPT.set(request -> { throw new ExtractionProvider.ProviderFailure(ExtractionProvider.Failure.TIMEOUT); });
        problem(send("{\"clinicalNote\":\"nota\"}"),504,"PROVIDER_TIMEOUT");
        for(var pair:new String[][]{{"CAPACITY_EXCEEDED","422","PROVIDER_CAPACITY_EXCEEDED"},{"OVERLOADED","503","SERVICE_OVERLOADED"},
                {"INVALID_RESPONSE","502","PROVIDER_INVALID_RESPONSE"}}) {
            SCRIPT.set(request->{throw new ExtractionProvider.ProviderFailure(ExtractionProvider.Failure.valueOf(pair[0]));});
            problem(send("{\"clinicalNote\":\"nota\"}"),Integer.parseInt(pair[1]),pair[2]);
        }
    }

    @Test void publicSuccessExamplesMatchActualWireShapes() throws Exception {
        JsonNode manifest=JSON.readTree(Files.readString(Path.of("openapi/examples/manifest.json")));
        for(JsonNode scenario:manifest.path("scenarios")) {
            if(scenario.path("status").asInt()!=200) continue;
            JsonNode expected=JSON.readTree(Files.readString(Path.of("openapi").resolve(scenario.path("responseExample").asText()))).get("value");
            JsonNode request=JSON.readTree(Files.readString(Path.of("openapi").resolve(scenario.path("requestExample").asText()))).get("value");
            Proposal proposal=PublicExamples.proposal(expected);
            SCRIPT.set(new ScriptedExtractionProvider(ignored -> proposal));
            var r=send(request.toString());
            assertEquals(200,r.statusCode(),scenario.path("id").asText()+r.body());
            var actual=body(r);
            for(String field:List.of("contractVersion","locale","processingStatus","reviewRequired","diagnoses","medications","procedures"))
                assertEquals(expected.get(field),actual.get(field),scenario.path("id").asText()+"/"+field);
            assertEquals(expected.path("warnings").size(),actual.path("warnings").size());
            for(int i=0;i<expected.path("warnings").size();i++)
                for(String field:List.of("code","targets","source"))
                    assertEquals(expected.path("warnings").get(i).get(field),actual.path("warnings").get(i).get(field));
            capture(r);
        }
    }

    @Test void concurrentConsumersKeepNotesAndCatalogsIsolated() throws Exception {
        SCRIPT.set(new ScriptedExtractionProvider(request -> {
            var unknown=new Context(new Grounded<>(Assertion.UNKNOWN,List.of()),new Grounded<>(Temporality.UNKNOWN,List.of()),new Grounded<>(Subject.UNKNOWN,List.of()));
            String note=request.clinicalNote();
            return new Proposal(List.of(new Diagnosis(new Text(note,List.of(new Evidence(0,note.codePointCount(0,note.length()),note))),null,unknown)),List.of());
        }));
        List<java.util.concurrent.CompletableFuture<HttpResponse<String>>> futures=new ArrayList<>();
        for(int i=0;i<20;i++) {
            String value="nota"+i, catalog="catalog"+i;
            String request=JSON.writeValueAsString(Map.of("clinicalNote",value,"catalogs",List.of(Map.of("catalogId",catalog,"version","1","kind","diagnosis",
                    "entries",List.of(Map.of("entryId","entry"+i,"name",value))))));
            var http=HttpRequest.newBuilder(uri).header("X-API-Key",i%2==0?KEY:SECOND).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(request)).build();
            futures.add(CLIENT.sendAsync(http,HttpResponse.BodyHandlers.ofString()));
        }
        Set<String> ids=new HashSet<>();
        for(int i=0;i<futures.size();i++) {
            var r=futures.get(i).get(); assertEquals(200,r.statusCode());
            var b=body(r); assertEquals("nota"+i,b.at("/diagnoses/0/name/value").asText());
            assertEquals("catalog"+i,b.at("/diagnoses/0/catalogMatch/candidates/0/catalogId").asText());
            assertTrue(ids.add(b.path("requestId").asText())); capture(r);
        }
        assertEquals(20,CALLS.get());
    }
}

