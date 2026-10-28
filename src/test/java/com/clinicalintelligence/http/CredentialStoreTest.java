package com.clinicalintelligence.http;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class CredentialStoreTest {
    static String token() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return "ci_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    static String digest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch (Exception impossible) { throw new AssertionError(impossible); }
    }
    static String entry(String key, String consumer, String scopes) {
        return "{\"sha256\":\"" + digest(key) + "\",\"consumerId\":\"" + consumer + "\",\"scopes\":" + scopes + "}";
    }
    @Test void derivesIdentityAndScopesFromVerifier() {
        String key = token();
        var store = CredentialStore.parse("[" + entry(key,"consumer-a","[\"extractions:write\"]") + "]");
        var identity = store.authenticate(key);
        assertEquals("consumer-a", identity.consumerId());
        assertEquals(Set.of("extractions:write"), identity.scopes());
        assertNull(store.authenticate(token()));
        assertNull(store.authenticate(null));
        assertNull(store.authenticate("invalid"));
    }
    @Test void rotationSupportsTwoKeysAndReloadRemovesOldKey() {
        String old = token(), fresh = token();
        var both = CredentialStore.parse("[" + entry(old,"a","[]") + "," + entry(fresh,"a","[]") + "]");
        assertNotNull(both.authenticate(old)); assertNotNull(both.authenticate(fresh));
        var revoked = CredentialStore.parse("[" + entry(fresh,"a","[]") + "]");
        assertNull(revoked.authenticate(old)); assertNotNull(revoked.authenticate(fresh));
    }
    @Test void rejectsAmbiguousOrUnsafeConfigWithoutLeakingValues() {
        String key = token(), e = entry(key,"a","[]");
        for (String config : List.of("[]","null","{}", "["+e+","+e+"]",
                "["+e.replace("\"sha256\"","\"secret\"")+"]", "["+e.replace("\"a\"","null")+"]",
                "["+e.replace("[]","[\"extractions:write\",\"extractions:write\"]")+"]",
                "["+e.replace("[]","null")+"]", "["+e.replace(digest(key),"bad")+"]",
                "["+e+",]", "["+e+"] {}")) {
            var error = assertThrows(IllegalArgumentException.class, () -> CredentialStore.parse(config));
            assertEquals("Invalid credentials configuration", error.getMessage());
            assertNull(error.getCause());
            assertFalse(error.toString().contains(key));
        }
    }
}

