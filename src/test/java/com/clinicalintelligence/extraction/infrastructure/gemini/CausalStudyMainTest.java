package com.clinicalintelligence.extraction.infrastructure.gemini;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
class CausalStudyMainTest {
    @Test void rejectedAuthorizationNeverLoadsCredentialsOrCreatesARun() throws Exception {
        Path dir=Files.createTempDirectory("ci-t8-gate-");var called=new AtomicBoolean();
        try {
            var json=GeminiProtocolTest.JSON;Path m=dir.resolve("manifest.json"),a=dir.resolve("authorization.json"),out=dir.resolve("run");
            var manifest=json.createObjectNode().put("version","t8-causal-study-1");manifest.putObject("budget").put("proposedMaxUsd","20");Files.writeString(m,manifest.toString());
            Files.writeString(a,json.createObjectNode().put("granted",false).toString());
            assertThrows(Exception.class,() -> CausalStudyMain.launch(dir,m,a,out,() -> {called.set(true);return "synthetic-key";}));
            assertFalse(called.get());assertFalse(Files.exists(out));
        } finally {try(var paths=Files.walk(dir)){for(var p:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}}
    }
    @Test void missingFrozenArtifactsFailBeforeCredentialLoading() throws Exception {
        Path dir=Files.createTempDirectory("ci-t8-artifact-gate-");var called=new AtomicBoolean();
        try {
            var json=GeminiProtocolTest.JSON;Path m=dir.resolve("manifest.json"),a=dir.resolve("authorization.json");
            var manifest=json.createObjectNode().put("version","t8-causal-study-1");manifest.putObject("budget").put("proposedMaxUsd","20");manifest.putArray("artifacts").addObject().put("path","missing.jar").put("sha256","0".repeat(64));Files.writeString(m,manifest.toString());
            Files.writeString(a,json.createObjectNode().put("granted",true).put("maxUsd","20").put("manifestSha256",StudyManifest.sha(m)).put("approvalReference","synthetic approval").toString());
            assertThrows(Exception.class,() -> CausalStudyMain.launch(dir,m,a,dir.resolve("run"),() -> {called.set(true);return "synthetic-key";}));assertFalse(called.get());
        } finally {try(var paths=Files.walk(dir)){for(var p:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}}
    }
}
