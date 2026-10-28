package com.clinicalintelligence.http;

import com.clinicalintelligence.ClinicalIntelligenceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StartupTest {
    @Test void missingOrRealModeNeverFallsBackToFake() throws Exception {
        Path file = Files.createTempFile("ci-h3-startup-", ".json");
        try {
            Files.writeString(file,"["+CredentialStoreTest.entry(CredentialStoreTest.token(),"a","[]")+"]");
            for(String mode : List.of("", "provider", "unexpected")) {
                assertThrows(Exception.class, () -> {
                    try(var ignored = IsolatedApplication.builder(ClinicalIntelligenceApplication.class)
                            .web(WebApplicationType.NONE).properties(Map.of("ci.credentials-file",file.toString(),
                                    "ci.provider-mode",mode,"spring.main.banner-mode","off","logging.level.root","OFF")).run()) {
                        fail("Startup accepted an absent/unsupported provider mode");
                    }
                });
            }
        } finally { Files.deleteIfExists(file); }
    }
    @Test void missingCredentialsFailClosed() {
        assertThrows(Exception.class, () -> {
            try(var ignored = IsolatedApplication.builder(ClinicalIntelligenceApplication.class)
                    .web(WebApplicationType.NONE).properties(Map.of("ci.provider-mode","simulated",
                            "spring.main.banner-mode","off","logging.level.root","OFF")).run()) {
                fail("Startup accepted missing credentials");
            }
        });
    }
    @Test void geminiStartsOfflineWithConfiguredKeyAndRejectsMissingKey() throws Exception {
        Path file=Files.createTempFile("ci-h4-startup-", ".json");
        try {
            Files.writeString(file,"["+CredentialStoreTest.entry(CredentialStoreTest.token(),"a","[\"extractions:write\"]")+"]");
            var options=new HashMap<String,Object>();
            options.put("ci.credentials-file",file.toString());options.put("ci.provider-mode","gemini");
            options.put("GOOGLE_API_KEY","synthetic-config-key");options.put("logging.level.root","OFF");
            com.clinicalintelligence.extraction.infrastructure.gemini.GeminiProvider configured;
            try(var app=IsolatedApplication.builder(ClinicalIntelligenceApplication.class).web(WebApplicationType.NONE).properties(options).run()) {
                configured=assertInstanceOf(com.clinicalintelligence.extraction.infrastructure.gemini.GeminiProvider.class,app.getBean(com.clinicalintelligence.extraction.application.ExtractionProvider.class));
            }
            assertEquals(com.clinicalintelligence.extraction.application.ExtractionProvider.Failure.UNAVAILABLE,
                    assertThrows(com.clinicalintelligence.extraction.application.ExtractionProvider.ProviderFailure.class,
                            ()->configured.extract(null)).failure());
            options.put("GOOGLE_API_KEY","");
            assertThrows(Exception.class,()->{
                try(var ignored=IsolatedApplication.builder(ClinicalIntelligenceApplication.class).web(WebApplicationType.NONE).properties(options).run()) {
                    fail("Gemini started without a key");
                }
            });
        } finally { Files.deleteIfExists(file); }
    }
    @Test void inheritedConfigImportCannotLoadExternalFilesBeforeTestDefaults() throws Exception {
        Path credentials=Files.createTempFile("ci-h4-isolation-", ".json");
        Path canary=Files.createTempFile("ci-h4-import-canary-", ".properties");
        String previous=System.getProperty("spring.config.import");
        try {
            Files.writeString(credentials,"["+CredentialStoreTest.entry(CredentialStoreTest.token(),"isolation","[]")+"]");
            Files.writeString(canary,"ci.provider-mode=forbidden-inherited-mode\nGOOGLE_API_KEY=synthetic-import-canary\n");
            System.setProperty("spring.config.import","file:"+canary);
            try(var app=IsolatedApplication.builder(ClinicalIntelligenceApplication.class).web(WebApplicationType.NONE)
                    .properties(Map.of("ci.provider-mode","simulated","ci.credentials-file",credentials.toString(),"logging.level.root","OFF")).run()) {
                assertInstanceOf(com.clinicalintelligence.extraction.infrastructure.ScriptedExtractionProvider.class,
                        app.getBean(com.clinicalintelligence.extraction.application.ExtractionProvider.class));
                assertFalse(app.getEnvironment().containsProperty("GOOGLE_API_KEY"));
            }
        } finally {
            if(previous==null) System.clearProperty("spring.config.import");else System.setProperty("spring.config.import",previous);
            Files.deleteIfExists(credentials);Files.deleteIfExists(canary);
        }
    }
}
