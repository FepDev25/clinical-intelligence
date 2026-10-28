package com.clinicalintelligence.extraction.application;

/** Only controlled codes leave the use case; no provider messages or causes. */
public final class ExtractionException extends RuntimeException {
    public enum Code {
        INVALID_REQUEST, INVALID_LOCALE, UNSUPPORTED_LOCALE, INPUT_LIMIT_EXCEEDED,
        PROVIDER_INVALID_RESPONSE, PROVIDER_TRUNCATED_RESPONSE, PROVIDER_UNAVAILABLE,
        PROVIDER_TIMEOUT, PROVIDER_CAPACITY_EXCEEDED, SERVICE_OVERLOADED
    }
    private final Code code;
    public ExtractionException(Code code) { super(code.name()); this.code = code; }
    public Code code() { return code; }
}
