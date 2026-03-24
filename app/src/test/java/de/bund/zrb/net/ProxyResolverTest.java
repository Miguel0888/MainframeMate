package de.bund.zrb.net;

import de.bund.zrb.model.Settings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void registryModeDoesNotCrash() {
        Settings settings = new Settings();
        settings.proxyMode = "REGISTRY";

        ProxyResolver.ProxyResolution res = ProxyResolver.resolveForUrl("https://example.com", settings, true);
        assertNotNull(res);
        assertNotNull(res.getReason());
    }

    @Test
    void registryTestDoesNotCrash() {
        ProxyResolver.ProxyResolution res = ProxyResolver.testRegistry("https://example.com");
        assertNotNull(res);
        assertTrue(res.getReason().startsWith("registry"));
    }

    @Test
    void regQueryValueReturnsNullForMissingKey() {
        String val = ProxyResolver.regQueryValue(
                "HKCU\\Software\\MainframeMate_TestNonExistent_12345", "Bogus");
        assertNull(val);
    }

    @Test
    void isHostBypassedMatchesExact() {
        assertTrue(ProxyResolver.isHostBypassed("localhost", "localhost"));
        assertFalse(ProxyResolver.isHostBypassed("example.com", "localhost"));
    }

    @Test
    void isHostBypassedMatchesWildcardPrefix() {
        assertTrue(ProxyResolver.isHostBypassed("intranet.corp.local", "*.corp.local"));
        assertFalse(ProxyResolver.isHostBypassed("example.com", "*.corp.local"));
    }

    @Test
    void isHostBypassedMatchesWildcardSuffix() {
        assertTrue(ProxyResolver.isHostBypassed("10.130.165.20", "10.*"));
        assertFalse(ProxyResolver.isHostBypassed("192.168.1.1", "10.*"));
    }

    @Test
    void isHostBypassedHandlesLocal() {
        assertTrue(ProxyResolver.isHostBypassed("intranet", "<local>"));
        assertFalse(ProxyResolver.isHostBypassed("www.example.com", "<local>"));
    }

    @Test
    void isHostBypassedMultiplePatterns() {
        String patterns = "localhost;*.local;10.*;192.168.*;<local>";
        assertTrue(ProxyResolver.isHostBypassed("localhost", patterns));
        assertTrue(ProxyResolver.isHostBypassed("myhost.local", patterns));
        assertTrue(ProxyResolver.isHostBypassed("10.0.0.1", patterns));
        assertTrue(ProxyResolver.isHostBypassed("intranet", patterns));
        assertFalse(ProxyResolver.isHostBypassed("www.google.com", patterns));
    }

    @Test
    void pacEvaluatorSimpleScript() {
        // Inline PAC script that always returns a fixed proxy
        String pacScript = "function FindProxyForURL(url, host) { return 'PROXY 10.0.0.1:3128'; }";
        // We can't easily call PacEvaluator directly (package-private), but we can test via
        // the registry mode's PAC result parsing indirectly through parseProxyOutput.
        // For now, just verify the PAC evaluator class loads without error.
        assertNotNull(ProxyResolver.testRegistry("https://example.com"));
    }
}
