package com.clinicalintelligence.http;

/** Only trusted codes and schema-owned pointers; never attach parser messages or causes. */
public final class HttpFailure extends RuntimeException {
    private final String code;
    private final String pointer;
    public HttpFailure(String code) { this(code, null); }
    public HttpFailure(String code, String pointer) {
        super(code); this.code = code; this.pointer = pointer;
    }
    public String code() { return code; }
    public String pointer() { return pointer; }
}
