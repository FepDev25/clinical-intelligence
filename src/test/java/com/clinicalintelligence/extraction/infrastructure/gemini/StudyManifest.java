package com.clinicalintelligence.extraction.infrastructure.gemini;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.security.MessageDigest;
import java.math.BigDecimal;
import java.util.*;

/** Preflight runs before the application reads its provider credential. */
final class StudyManifest {
    static BigDecimal authorized(JsonNode manifest,JsonNode auth,String actualSha) {
        try {
            if(!manifest.path("version").asText().equals("t8-causal-study-1") || !auth.path("granted").isBoolean() || !auth.path("granted").booleanValue()
                || !auth.path("manifestSha256").asText().equals(actualSha) || auth.path("approvalReference").asText().isBlank())throw new IllegalArgumentException();
            String text=auth.path("maxUsd").asText();if(!text.matches("(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,8})?"))throw new IllegalArgumentException();
            var cap=new BigDecimal(text);var proposed=new BigDecimal(manifest.path("budget").path("proposedMaxUsd").asText());
            if(cap.signum()<=0 || cap.compareTo(proposed)>0)throw new IllegalArgumentException();return cap;
        } catch(RuntimeException invalid){throw new IllegalArgumentException("Explicit study authorization required");}
    }
    static void verifyArtifacts(JsonNode manifest,Path root) {
        try {
            Path realRoot=root.toRealPath();var artifacts=manifest.path("artifacts");
            if(!artifacts.isArray() || artifacts.isEmpty())throw new IllegalArgumentException();
            var seen=new HashSet<String>();
            for(var artifact:artifacts) {
                String relative=artifact.path("path").asText();Path path=Path.of(relative);
                if(path.isAbsolute() || relative.isBlank() || relative.contains("\\") || !seen.add(relative) || !relative.matches(".*\\.(?:jar|json|java|py|txt|md|properties|zip)$"))throw new IllegalArgumentException();
                Path resolved=realRoot.resolve(path).normalize();
                if(!resolved.startsWith(realRoot) || !Files.isRegularFile(resolved) || !resolved.toRealPath().startsWith(realRoot)
                    || !sha(resolved).equals(artifact.path("sha256").asText()))throw new IllegalArgumentException();
            }
            for(String profile:List.of("study","evidence-2","evidence-4")) {
                if(!manifest.path("classpaths").has(profile))continue; // simple unit manifests may omit launch profiles
                var cp=manifest.path("classpaths").path(profile);if(!cp.isArray() || cp.isEmpty())throw new IllegalArgumentException();
                for(var entry:cp)if(!entry.isTextual() || !seen.contains(entry.asText()) || !entry.asText().endsWith(".jar"))throw new IllegalArgumentException();
            }
        } catch(Exception invalid){throw new IllegalArgumentException("Study artifacts missing or changed");}
    }
    static Path claimOnce(Path root,String digest) {
        boolean created=false;Path file=null;
        try {
            if(!digest.matches("[a-f0-9]{64}"))throw new IllegalArgumentException();
            Path real=root.toRealPath(),parent=real.resolve("evaluation/quality-review/t8/authorization-claims"),existing=parent;
            while(!Files.exists(existing))existing=existing.getParent();
            if(!existing.toRealPath().startsWith(real))throw new IllegalArgumentException();
            Files.createDirectories(parent);file=parent.resolve(digest+".json");
            try(var channel=java.nio.channels.FileChannel.open(file,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                created=true;var bytes=java.nio.ByteBuffer.wrap(("{\"manifestSha256\":\""+digest+"\",\"claimedUtc\":\""+java.time.Instant.now()+"\"}\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                while(bytes.hasRemaining())channel.write(bytes);channel.force(true);
            }
            return file;
        } catch(Exception invalid) {
            if(created && file!=null)try{Files.delete(file);}catch(Exception ignored){}
            throw new IllegalStateException("Study manifest approval already claimed or unavailable");
        }
    }
    static String sha(Path path) throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
}
