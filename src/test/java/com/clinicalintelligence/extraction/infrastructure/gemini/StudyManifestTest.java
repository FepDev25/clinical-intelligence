package com.clinicalintelligence.extraction.infrastructure.gemini;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class StudyManifestTest {
    @Test void authorizationCannotBeImplicitAndMustBindTheExactManifest() throws Exception {
        var json=GeminiProtocolTest.JSON;
        var manifest=json.createObjectNode().put("version","t8-causal-study-1");manifest.putObject("budget").put("proposedMaxUsd","20");
        var auth=json.createObjectNode().put("granted",false).put("manifestSha256","abc").put("maxUsd","20").put("approvalReference","synthetic test approval");
        assertThrows(IllegalArgumentException.class,() -> StudyManifest.authorized(manifest,auth,"abc"));
        auth.put("granted",true);assertEquals(0,StudyManifest.authorized(manifest,auth,"abc").compareTo(new java.math.BigDecimal("20")));
        assertThrows(IllegalArgumentException.class,() -> StudyManifest.authorized(manifest,auth,"changed"));
        auth.put("maxUsd","21");assertThrows(IllegalArgumentException.class,() -> StudyManifest.authorized(manifest,auth,"abc"));
    }
    @Test void aMissingOrChangedRequiredBinaryAlwaysFailsBeforeCredentials() throws Exception {
        Path dir=Files.createTempDirectory("ci-t8-manifest-");
        try {
            var m=GeminiProtocolTest.JSON.createObjectNode();m.putArray("artifacts").addObject().put("path","missing.jar").put("sha256","0".repeat(64));
            assertThrows(IllegalArgumentException.class,() -> StudyManifest.verifyArtifacts(m,dir));
            Files.writeString(dir.resolve("missing.jar"),"synthetic");assertThrows(IllegalArgumentException.class,() -> StudyManifest.verifyArtifacts(m,dir));
        } finally {try(var files=Files.walk(dir)){for(var p:files.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}}
    }
    @Test void aVerifiedSourceArchiveIsAValidFrozenArtifact() throws Exception {
        Path dir=Files.createTempDirectory("ci-t8-source-archive-");
        try {
            Path archive=dir.resolve("sources.zip");Files.writeString(archive,"synthetic source snapshot");
            var m=GeminiProtocolTest.JSON.createObjectNode();m.putArray("artifacts").addObject().put("path","sources.zip").put("sha256",StudyManifest.sha(archive));
            assertDoesNotThrow(() -> StudyManifest.verifyArtifacts(m,dir));
        } finally {try(var paths=Files.walk(dir)){for(var p:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}}
    }
    @Test void sameManifestApprovalCannotBeSpentTwiceByChangingRunDirectory() throws Exception {
        Path root=Files.createTempDirectory("ci-t8-once-");
        try {
            Path first=StudyManifest.claimOnce(root,"a".repeat(64));assertTrue(Files.isRegularFile(first));
            assertThrows(IllegalStateException.class,() -> StudyManifest.claimOnce(root,"a".repeat(64)));
            assertTrue(Files.isRegularFile(first));
        } finally {try(var paths=Files.walk(root)){for(var p:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}}
    }
}
