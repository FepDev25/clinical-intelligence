package com.clinicalintelligence.extraction.infrastructure.gemini;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/** Standalone, opt-in synthetic study application; never a production bean or HTTP mode. */
public final class CausalStudyMain {
    private static final ObjectMapper JSON=JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public static void main(String[] args) {
        try {
            if(args.length==2 && args[0].equals("--verify-only")) {
                Path root=Path.of("").toRealPath();JsonNode m=JSON.readTree(Files.readAllBytes(Path.of(args[1])));
                StudyManifest.verifyArtifacts(m,root);verifyLaunchClasspath(m,root);verifyDesign(m);
                System.out.println("FROZEN_STUDY_PREFLIGHT_OK; credentialsLoaded=false; networkCalls=0");return;
            }
            if(args.length!=3)throw new IllegalArgumentException();
            launch(Path.of("").toRealPath(),Path.of(args[0]),Path.of(args[1]),Path.of(args[2]),CausalStudyMain::applicationCredential);
        } catch(Exception failure){System.err.println("CAUSAL_STUDY_START_OR_STORAGE_FAILED");System.exit(2);}
    }
    static void launch(Path root,Path manifestFile,Path authorizationFile,Path output,Supplier<String> credential) throws Exception {
        JsonNode manifest=JSON.readTree(Files.readAllBytes(manifestFile)),authorization=JSON.readTree(Files.readAllBytes(authorizationFile));
        String manifestDigest=StudyManifest.sha(manifestFile);
        BigDecimal cap=StudyManifest.authorized(manifest,authorization,manifestDigest);
        StudyManifest.verifyArtifacts(manifest,root);verifyLaunchClasspath(manifest,root);verifyDesign(manifest);
        var cases=new HashMap<String,JsonNode>();
        for(var row:JSON.readTree(Files.readAllBytes(root.resolve(manifest.path("inputs").path("development").path("path").asText())))) {
            if(cases.put(row.path("id").asText(),row)!=null)throw new IllegalArgumentException();
        }
        if(cases.size()!=39 || Files.exists(output))throw new IllegalArgumentException();
        var configs=new HashMap<String,JsonNode>();for(var c:manifest.path("configurations"))configs.put(c.path("id").asText(),c);
        if(!output.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize()))throw new IllegalArgumentException("Study output must remain in project");
        Path claim=StudyManifest.claimOnce(root,manifestDigest);var attemptStarted=new java.util.concurrent.atomic.AtomicBoolean();
        try {
        String key=credential.get(); // The application alone loads GOOGLE_API_KEY, after every gate above.
        if(key!=null && key.length()>=2 && (key.startsWith("\"") && key.endsWith("\"") || key.startsWith("'") && key.endsWith("'")))key=key.substring(1,key.length()-1);
        var budget=new StudyBudget(cap);
        try(var transport=new StudyTransport(key,budget,URI.create("https://generativelanguage.googleapis.com/v1beta/"),16_777_216,Duration.ofSeconds(120),() -> LocalDate.now(ZoneOffset.UTC))) {
            Files.createDirectory(output);Files.createDirectory(output.resolve("units"));
            var start=JSON.createObjectNode().put("manifestSha256",manifestDigest).put("syntheticData",true).put("inferenceExecuted",true)
                    .put("effectiveMaxUsd",cap.toPlainString()).put("startedUtc",Instant.now().toString()).put("javaVersion",System.getProperty("java.version")).put("pid",ProcessHandle.current().pid());
            start.putObject("authorization").put("granted",true).put("maxUsd",cap.toPlainString()).put("approvalReference",authorization.path("approvalReference").asText());writeNew(output.resolve("run-start.json"),start);
            int completed=0,failed=0;String stopped=null;
            var unitDigests=JSON.createObjectNode();
            try(var journal=FileChannel.open(output.resolve("events.jsonl"),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                for(var unit:manifest.path("schedule")) {
                    if(Files.exists(output.resolve("STOP"))){stopped="OPERATOR_STOP";break;}
                    if(!StudyManifest.sha(manifestFile).equals(manifestDigest)){stopped="MANIFEST_CHANGED";break;}
                    var fixture=cases.get(unit.path("caseId").asText());
                    if(fixture==null)throw new IllegalArgumentException();
                    var result=CausalStudyEngine.runUnit(unit,configs.get(unit.path("configuration").asText()),fixture.path("request"),true,(p,input,body,end,id) -> {if(Files.exists(output.resolve("STOP")))throw new StudyTransport.Stop("OPERATOR_STOP");return transport.execute(p,input,body,end,id,event -> {append(journal,event);if(event.path("kind").asText().equals("COUNT_INTENT"))attemptStarted.set(true);});});
                    result.put("manifestSha256",manifestDigest);
                    Path unitFile=output.resolve("units").resolve(String.format(Locale.ROOT,"%04d.json",completed));writeNew(unitFile,result);
                    unitDigests.put("units/"+unitFile.getFileName(),StudyManifest.sha(unitFile));completed++;
                    if(!result.path("status").asText().equals("complete"))failed++;
                    if(result.path("status").asText().equals("stopped")){stopped=result.path("stopCode").asText();break;}
                }
            }
            var summary=JSON.createObjectNode().put("manifestSha256",manifestDigest).put("finishedUtc",Instant.now().toString())
                    .put("plannedUnits",manifest.path("schedule").size()).put("finishedUnits",completed).put("failedUnits",failed)
                    .put("complete",completed==manifest.path("schedule").size() && stopped==null).put("stopCode",stopped)
                    .put("conservativeChargedUsd",budget.committed().toPlainString()).put("effectiveMaxUsd",cap.toPlainString()).put("releaseApproved",false);
summary.set("unitRecordsSha256",unitDigests);summary.put("eventsSha256",StudyManifest.sha(output.resolve("events.jsonl")));
            writeNew(output.resolve("run-summary.json"),summary);
        }
        } finally {if(!attemptStarted.get())Files.deleteIfExists(claim);}
    }
    private static void verifyLaunchClasspath(JsonNode manifest,Path root) throws Exception {
        var expected=new ArrayList<Path>();for(var entry:manifest.path("classpaths").path("study"))expected.add(root.resolve(entry.asText()).toRealPath());
        var actual=Arrays.stream(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))).map(Path::of).map(p -> {try{return p.toRealPath();}catch(Exception bad){throw new IllegalArgumentException();}}).toList();
        if(expected.isEmpty() || !expected.equals(actual))throw new IllegalArgumentException("Run only the frozen study classpath");
    }
    private static void verifyDesign(JsonNode m) {
        if(!m.path("version").asText().equals("t8-causal-study-1") || m.path("schedule").size()!=702 || m.path("configurations").size()!=6 || m.path("arms").size()!=8
            || m.at("/limits/outputTokens").asInt()!=65_536 || m.at("/limits/retries").asInt(-1)!=0 || m.at("/limits/concurrency").asInt()!=1)throw new IllegalArgumentException("Invalid frozen study design");
        for(var c:m.path("configurations")) {
            var level=GeminiGeneration.ThinkingLevel.valueOf(c.path("thinkingLevel").asText());
            if(c.path("workflow").asText().equals("single")) {
                var prompt=Arrays.stream(GeminiGeneration.Prompt.values()).filter(p -> p.version().equals(c.path("promptVersion").asText())).findFirst().orElseThrow();
                if(!prompt.singlePass())throw new IllegalArgumentException();new GeminiGeneration(c.path("model").asText(),level,prompt);
            } else if(!c.path("workflow").asText().equals("two-stage") || !c.path("model").asText().equals(GeminiProtocol.MODEL) || level!=GeminiGeneration.ThinkingLevel.LOW)throw new IllegalArgumentException();
        }
    }
    private static void writeNew(Path path,JsonNode value) throws Exception {
        Path temporary=path.resolveSibling(path.getFileName()+".tmp");
        try(var channel=FileChannel.open(temporary,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {write(channel,JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));write(channel,new byte[]{10});channel.force(true);}
        Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE);
    }
    private static void append(FileChannel channel,JsonNode value) {
        try {write(channel,JSON.writeValueAsBytes(value));write(channel,new byte[]{10});channel.force(true);}
        catch(Exception storage){throw new IllegalStateException("Study journal unavailable");}
    }
    private static void write(FileChannel channel,byte[] bytes) throws Exception {var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);}
    @Configuration(proxyBeanMethods=false) static class SecretConfiguration {}
    private static String applicationCredential() {
        var env=new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("study-application-bootstrap",Map.of("spring.config.location","optional:file:.env[.properties]","spring.config.import","","spring.main.web-application-type","none","spring.main.banner-mode","off","spring.main.log-startup-info","false","logging.level.root","OFF","debug","false","trace","false")));
        try(var context=new SpringApplicationBuilder(SecretConfiguration.class).environment(env).web(WebApplicationType.NONE).registerShutdownHook(false).logStartupInfo(false).run()) {
            return context.getEnvironment().getRequiredProperty("GOOGLE_API_KEY");
        }
    }
}
