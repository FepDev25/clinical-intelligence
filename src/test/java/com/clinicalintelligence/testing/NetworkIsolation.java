package com.clinicalintelligence.testing;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import java.net.*;
import java.io.IOException;
import java.util.List;

/** Fail closed before any test class: ordinary verification can only contact loopback. */
public final class NetworkIsolation implements BeforeAllCallback {
    @Override public void beforeAll(ExtensionContext context) {
        ProxySelector.setDefault(new ProxySelector() {
            public List<Proxy> select(URI uri) {
                String host=uri.getHost();
                if(!List.of("127.0.0.1","localhost","[::1]","::1").contains(host))
                    throw new SecurityException("External network disabled in ordinary tests");
                return List.of(Proxy.NO_PROXY);
            }
            public void connectFailed(URI uri,SocketAddress address,IOException failure) {}
        });
    }
}
