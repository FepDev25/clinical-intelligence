package com.clinicalintelligence.testing;

import org.junit.jupiter.api.Test;
import java.net.*;
import static org.junit.jupiter.api.Assertions.*;

class NetworkIsolationTest {
    @Test void ordinaryTestsRejectRemoteRoutesBeforeConnecting() {
        assertThrows(SecurityException.class,()->ProxySelector.getDefault().select(URI.create("https://generativelanguage.googleapis.com")));
        assertEquals(java.util.List.of(Proxy.NO_PROXY),ProxySelector.getDefault().select(URI.create("http://127.0.0.1:1234")));
    }
}
