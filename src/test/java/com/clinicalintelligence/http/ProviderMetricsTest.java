package com.clinicalintelligence.http;
import static org.junit.jupiter.api.Assertions.*;
import com.clinicalintelligence.extraction.application.ExtractionService;
import com.clinicalintelligence.extraction.infrastructure.gemini.*;
import ch.qos.logback.classic.*;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
class ProviderMetricsTest {
    @Test void reportsActualPromptThinkingAndCoreVersionWithoutClinicalContent() {
        var logger=(Logger)org.slf4j.LoggerFactory.getLogger("clinical.provider.metrics");
        var previousLevel=logger.getLevel();logger.setLevel(Level.INFO);
        var appender=new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();appender.start();logger.addAppender(appender);
        try {
            var config=new GeminiGeneration(GeminiProtocol.MODEL,GeminiGeneration.ThinkingLevel.HIGH,GeminiGeneration.Prompt.V3);
            var identity=new GeminiProtocol(config).identity();
            ApiConfiguration.providerMetrics().accept(new GeminiProvider.Observation("COMPLETE",10,new GeminiProtocol.Usage(1L,1L,0L,0L),null,"synthetic-tariff","gemini-3.8-flash-synthetic",identity));
            String log=appender.list.getLast().getFormattedMessage();
            assertTrue(log.contains("validation="+ExtractionService.VALIDATION_VERSION));assertTrue(log.contains("prompt="+identity.promptVersion()));
            assertTrue(log.contains("HIGH"));assertTrue(log.contains(identity.promptSha256()));assertTrue(log.contains(identity.requestVersion()));
            assertTrue(log.contains("gemini-3.8-flash-synthetic"));assertFalse(log.contains("evidence-1"));
        } finally {logger.detachAppender(appender);appender.stop();logger.setLevel(previousLevel);}
    }
}
