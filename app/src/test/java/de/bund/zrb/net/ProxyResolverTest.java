package de.bund.zrb.net;

import de.bund.zrb.model.Settings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyResolverTest {

    @Test
    void parseDirectOutput() {
        ProxyResolver.ProxyResolution res = ProxyResolver.parseProxyOutput("DIRECT");
        assertTrue(res.isDirect());
    }

    @Test
    void parseEmptyOutput() {
        assertTrue(ProxyResolver.parseProxyOutput("").isDirect());
        assertTrue(ProxyResolver.parseProxyOutput(null).isDirect());
        assertTrue(ProxyResolver.parseProxyOutput("   ").isDirect());
    }

    @Test
    void parseHostPortOutput() {
        ProxyResolver.ProxyResolution res = ProxyResolver.parseProxyOutput("proxy.example:8080");
        assertFalse(res.isDirect());
        assertTrue(res.getProxy().address().toString().contains("8080"));
    }

    @Test
    void parseMultiLineOutputTakesLastLine() {
        // PowerShell may emit warnings/info before the actual host:port
        String output = "WARNING: Some PowerShell warning\n\n10.130.165.20:3128\n";
        ProxyResolver.ProxyResolution res = ProxyResolver.parseProxyOutput(output);
        assertFalse(res.isDirect());
        assertTrue(res.getProxy().address().toString().contains("3128"));
    }

    @Test
    void parseUrlFormattedProxy() {
        // Some PAC implementations return full URL format
        ProxyResolver.ProxyResolution res = ProxyResolver.parseProxyOutput("http://10.130.165.20:3128/");
        assertFalse(res.isDirect());
        assertTrue(res.getProxy().address().toString().contains("3128"));
    }

    @Test
    void parseOutputWithBom() {
        // UTF-8 BOM (U+FEFF) at start of output
        String output = "\uFEFF10.130.165.20:3128";
        ProxyResolver.ProxyResolution res = ProxyResolver.parseProxyOutput(output);
        assertFalse(res.isDirect());
        assertTrue(res.getProxy().address().toString().contains("3128"));
    }

    @Test
    void parseOutputWithAnsiEscapes() {
        // ANSI color codes around the output
        String output = "\u001B[0m10.130.165.20:3128\u001B[0m";
        ProxyResolver.ProxyResolution res = ProxyResolver.parseProxyOutput(output);
        assertFalse(res.isDirect());
        assertTrue(res.getProxy().address().toString().contains("3128"));
    }

    @Test
    void manualNoProxyLocalBypasses() {
        Settings settings = new Settings();
        settings.proxyMode = "MANUAL";
        settings.proxyHost = "proxy";
        settings.proxyPort = 8080;
        settings.proxyNoProxyLocal = true;

        ProxyResolver.ProxyResolution res = ProxyResolver.resolveForUrl("http://localhost:11434", settings);
        assertTrue(res.isDirect());
    }

    @Test
    void useProxyFalseReturnsDirect() {
        Settings settings = new Settings();
        settings.proxyMode = "MANUAL";
        settings.proxyHost = "proxy";
        settings.proxyPort = 8080;

        ProxyResolver.ProxyResolution res = ProxyResolver.resolveForUrl("http://example.com", settings, false);
        assertTrue(res.isDirect());
        assertTrue(res.getReason().contains("disabled"));
    }

    @Test
    void useProxyTrueResolves() {
        Settings settings = new Settings();
        settings.proxyMode = "MANUAL";
        settings.proxyHost = "proxy.example.com";
        settings.proxyPort = 3128;

        ProxyResolver.ProxyResolution res = ProxyResolver.resolveForUrl("http://example.com", settings, true);
        assertFalse(res.isDirect());
        assertTrue(res.getProxy().address().toString().contains("3128"));
    }

    @Test
    void javaSystemDoesNotCrash() {
        // testJavaSystem should never throw — it returns a ProxyResolution in all cases
        ProxyResolver.ProxyResolution res = ProxyResolver.testJavaSystem("https://example.com");
        assertNotNull(res);
        assertNotNull(res.getReason());
        // On a developer machine without proxy, this is likely DIRECT — that's fine
        assertTrue(res.getReason().startsWith("java-system"));
    }

    @Test
    void javaSystemModeResolves() {
        Settings settings = new Settings();
        settings.proxyMode = "JAVA_SYSTEM";

        // Should not crash; on a dev machine likely returns DIRECT
        ProxyResolver.ProxyResolution res = ProxyResolver.resolveForUrl("https://example.com", settings, true);
        assertNotNull(res);
    }
}

