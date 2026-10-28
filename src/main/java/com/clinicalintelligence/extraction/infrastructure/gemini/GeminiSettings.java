package com.clinicalintelligence.extraction.infrastructure.gemini;

import java.time.Duration;

public record GeminiSettings(Duration deadline, Duration connectTimeout, int outputTokens, int concurrency, int responseBytes) {
    public GeminiSettings {
        if(deadline==null || deadline.isNegative() || deadline.isZero() || deadline.compareTo(Duration.ofMinutes(30))>0
                || connectTimeout==null || connectTimeout.isNegative() || connectTimeout.isZero() || connectTimeout.compareTo(deadline)>0
                || outputTokens<1 || outputTokens>65_536 || concurrency<1 || concurrency>10_000 || responseBytes<1 || responseBytes==Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid Gemini operation limits");
    }
    public static GeminiSettings defaults() { return new GeminiSettings(Duration.ofSeconds(120),Duration.ofSeconds(10),65_536,2,16_777_216); }
}
