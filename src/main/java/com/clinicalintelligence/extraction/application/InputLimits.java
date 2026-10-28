package com.clinicalintelligence.extraction.application;

/** Body bytes belong to the future HTTP adapter, not to a decoded domain command. */
public record InputLimits(int noteCodePoints, int catalogs, int entries, int aliases) {
    public InputLimits {
        if (noteCodePoints <= 0 || catalogs <= 0 || entries <= 0 || aliases <= 0)
            throw new IllegalArgumentException("Limits must be positive");
    }
    public static InputLimits defaults() { return new InputLimits(1_000_000, 1_000, 100_000, 1_000_000); }
}
