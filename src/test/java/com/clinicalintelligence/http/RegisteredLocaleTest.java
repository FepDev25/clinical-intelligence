package com.clinicalintelligence.http;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.clinicalintelligence.extraction.application.ExtractionException;

class RegisteredLocaleTest {
    private final RegisteredLocale policy = new RegisteredLocale();
    @Test void acceptsSpanishRegisteredSubtagsAndPreservesInput() {
        for (String tag : new String[]{"es","ES-ec","es-419","es-Latn-MX","es-u-nu-latn","es-x-demo","es-ES-u-ca-gregory","es-QQ"})
            assertEquals(tag, policy.validate(tag));
        assertEquals("es", policy.validate(null));
    }
    @Test void distinguishesMalformedOrUnregisteredFromUnsupportedLanguage() {
        for (String tag : new String[]{"es_EC","es-AB","es-Abcd","zz","es-foobar","es-a-foo","es-aaa","es-u","es-u-foo-u-bar","es"+"-a".repeat(5000)}) {
            var e = assertThrows(ExtractionException.class, () -> policy.validate(tag), tag);
            assertEquals(ExtractionException.Code.INVALID_LOCALE, e.code(), tag);
        }
        for (String tag : new String[]{"en-US","fr","de-CH-1901","zh-cmn-Hans-CN"}) {
            var e = assertThrows(ExtractionException.class, () -> policy.validate(tag), tag);
            assertEquals(ExtractionException.Code.UNSUPPORTED_LOCALE, e.code(), tag);
        }
    }
}

