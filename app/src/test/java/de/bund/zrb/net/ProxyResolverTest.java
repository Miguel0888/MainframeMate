package de.bund.zrb.net;

import de.bund.zrb.model.Settings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyResolverTest {

    @Test
    void parseDirectOutput() {
        ProxyResolver.ProxyResolution res = ProxyResolver.parseProxyOutput("DIRECT");
        assertTrue(res.isDirect());
    }

    @Test
    void parseHostPortOutput() {
        ProxyResolver.ProxyResolution res = ProxyResolver.parseProxyOutput("proxy.example:8080");
        assertFalse(res.isDirect());
        assertTrue(res.getProxy().address().toString().contains("8080"));
    }

    @Test
    void manualNoProxyLocalBypasses() {
        Settings settings = new Settings();
        settings.proxyEnabled = true;
        settings.proxyMode = "MANUAL";
        settings.proxyHost = "proxy";
        settings.proxyPort = 8080;
        settings.proxyNoProxyLocal = true;

        ProxyResolver.ProxyResolution res = ProxyResolver.resolveForUrl("http://localhost:11434", settings);
        assertTrue(res.isDirect());
    }
}

