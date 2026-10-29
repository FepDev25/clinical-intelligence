package com.clinicalintelligence.http;

import com.clinicalintelligence.extraction.application.*;
import com.clinicalintelligence.extraction.infrastructure.ScriptedExtractionProvider;
import com.clinicalintelligence.extraction.infrastructure.gemini.*;
import com.clinicalintelligence.extraction.domain.ClinicalModel.Proposal;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import java.nio.file.*;
import java.util.List;
import java.time.Duration;

@Configuration(proxyBeanMethods=false)
public class ApiConfiguration {
    @Bean CredentialStore credentials(Environment environment) {
        try {
            String filename = environment.getRequiredProperty("ci.credentials-file");
            Path path = Path.of(filename);
            if (Files.size(path) > 1_048_576) throw new IllegalArgumentException();
            return CredentialStore.parse(Files.readString(path));
        } catch (Exception invalid) { throw new IllegalArgumentException("Invalid credentials configuration"); }
    }
    @Bean ExtractionProvider extractionProvider(Environment environment) {
        String mode=environment.getProperty("ci.provider-mode","");
        if("simulated".equals(mode)) return new ScriptedExtractionProvider(request -> new Proposal(List.of(),List.of()));
        if(!"gemini".equals(mode)) throw new IllegalArgumentException("Explicit supported provider mode required");
        GeminiSettings settings;
        try {
            settings=new GeminiSettings(Duration.ofMillis(limit(environment,"ci.gemini.deadline-ms",120_000)),
                    Duration.ofMillis(limit(environment,"ci.gemini.connect-timeout-ms",10_000)),
                    limit(environment,"ci.gemini.output-tokens",65_536),limit(environment,"ci.gemini.concurrency",2),
                    limit(environment,"ci.gemini.response-bytes",16_777_216));
        } catch(RuntimeException invalid) { throw new IllegalArgumentException("Invalid Gemini operation limits"); }
        String key=environment.getProperty("GOOGLE_API_KEY","");
        if(key.length()>=2 && (key.startsWith("\"") && key.endsWith("\"") || key.startsWith("'") && key.endsWith("'")))
            key=key.substring(1,key.length()-1);
        return new GeminiProvider(key,settings,providerMetrics());
    }
    static java.util.function.Consumer<GeminiProvider.Observation> providerMetrics() {
        var log=org.slf4j.LoggerFactory.getLogger("clinical.provider.metrics");
        return event -> log.info("provider=google-gemini model={} prompt={} validation={} processing=extraction-core-0.1.0 outcome={}",
                event.effectiveModel()==null?event.identity().model():event.effectiveModel(),event.identity().promptVersion(),ExtractionService.VALIDATION_VERSION,event);
    }
    @Bean InputLimits inputLimits(Environment env) {
        return new InputLimits(limit(env,"ci.note-code-points",1_000_000),limit(env,"ci.catalogs",1_000),
                limit(env,"ci.entries",100_000),limit(env,"ci.aliases",1_000_000));
    }
    @Bean WireContract wireContract(Environment env) { return new WireContract(limit(env,"ci.body-bytes",67_108_864)); }
    @Bean ExtractionService extractionService(ExtractionProvider provider,InputLimits limits) { return new ExtractionService(provider,limits); }
    @Bean FilterRegistrationBean<ApiFilter> apiFilter(CredentialStore credentials,Environment env) {
        var registration = new FilterRegistrationBean<>(new ApiFilter(credentials,limit(env,"ci.body-bytes",67_108_864)));
        registration.setOrder(Integer.MIN_VALUE);
        registration.addUrlPatterns("/*");
        return registration;
    }
    private static int limit(Environment env,String key,int fallback) {
        try {
            int value = Integer.parseInt(env.getProperty(key,Integer.toString(fallback)));
            if (value < 1 || value == Integer.MAX_VALUE) throw new IllegalArgumentException();
            return value;
        } catch (Exception invalid) { throw new IllegalArgumentException("Invalid positive input limit configuration"); }
    }
}

