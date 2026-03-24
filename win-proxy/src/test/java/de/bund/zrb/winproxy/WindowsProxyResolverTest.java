package de.bund.zrb.winproxy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WindowsProxyResolverTest {

    // ── resolve() smoke test ─────────────────────────────────────

    @Test
    void resolveDoesNotCrash() {
        ProxyResult res = WindowsProxyResolver.resolve("https://example.com");
        assertNotNull(res);
        assertNotNull(res.getReason());
    }

    @Test
    void resolveStaticDoesNotCrash() {
        ProxyResult res = WindowsProxyResolver.resolveStatic("https://example.com");
        assertNotNull(res);
        assertNotNull(res.getReason());
    }

    // ── readRegistryValue() ──────────────────────────────────────

    @Test
    void readRegistryValueReturnsNullForMissingKey() {
        String val = WindowsProxyResolver.readRegistryValue(
                "HKCU\\Software\\WinProxyTest_NonExistent_12345", "Bogus");
        assertNull(val);
    }

    // ── readRegistryValueFromAllHives() ──────────────────────────

    @Test
    void readRegistryValueFromAllHivesDoesNotCrash() {
        // May return null (no AutoConfigURL set) or a valid URL — both are OK
        String val = WindowsProxyResolver.readRegistryValueFromAllHives("AutoConfigURL");
        // No assertion on value — just verifying no exception is thrown
    }

    @Test
    void readRegistryValueFromAllHivesReturnsNullForBogusValue() {
        String val = WindowsProxyResolver.readRegistryValueFromAllHives("WinProxyTest_Bogus_99999");
        assertNull(val);
    }

    // ── Registry hives are searched ──────────────────────────────

    @Test
    void settingsKeysContainPolicyAndUserHives() {
        // Verify the search order is correct: GPO first, then user/machine
        assertEquals(4, RegistryReader.SETTINGS_KEYS.length);
        assertTrue(RegistryReader.SETTINGS_KEYS[0].contains("Policies"),
                "First hive should be HKCU Policy");
        assertTrue(RegistryReader.SETTINGS_KEYS[0].startsWith("HKCU"),
                "First hive should be HKCU");
        assertTrue(RegistryReader.SETTINGS_KEYS[1].contains("Policies"),
                "Second hive should be HKLM Policy");
        assertTrue(RegistryReader.SETTINGS_KEYS[1].startsWith("HKLM"),
                "Second hive should be HKLM");
        assertFalse(RegistryReader.SETTINGS_KEYS[2].contains("Policies"),
                "Third hive should be normal user key");
        assertFalse(RegistryReader.SETTINGS_KEYS[3].contains("Policies"),
                "Fourth hive should be normal machine key");
    }

    // ── DefaultConnectionSettings blob parsing ───────────────────

    @Test
    void queryAutoConfigUrlFromBlobDoesNotCrash() {
        // May return null or a URL — both are valid depending on the machine
        String blobUrl = RegistryReader.queryAutoConfigUrlFromBlob();
        // No assertion on value
    }

    @Test
    void connectionFlagsConstants() {
        assertEquals(0x08, RegistryReader.FLAG_AUTO_DETECT);
        assertEquals(0x04, RegistryReader.FLAG_AUTO_CONFIG);
        assertEquals(0x02, RegistryReader.FLAG_PROXY);
    }

    // ── isBypassed() ─────────────────────────────────────────────

    @Test
    void isHostBypassedMatchesExact() {
        assertTrue(WindowsProxyResolver.isBypassed("localhost", "localhost"));
        assertFalse(WindowsProxyResolver.isBypassed("example.com", "localhost"));
    }

    @Test
    void isBypassedMatchesWildcardPrefix() {
        assertTrue(WindowsProxyResolver.isBypassed("intranet.corp.local", "*.corp.local"));
        assertFalse(WindowsProxyResolver.isBypassed("example.com", "*.corp.local"));
    }

    @Test
    void isBypassedMatchesWildcardSuffix() {
        assertTrue(WindowsProxyResolver.isBypassed("10.130.165.20", "10.*"));
        assertFalse(WindowsProxyResolver.isBypassed("192.168.1.1", "10.*"));
    }

    @Test
    void isBypassedHandlesLocal() {
        assertTrue(WindowsProxyResolver.isBypassed("intranet", "<local>"));
        assertFalse(WindowsProxyResolver.isBypassed("www.example.com", "<local>"));
    }

    @Test
    void isBypassedMultiplePatterns() {
        String patterns = "localhost;*.local;10.*;192.168.*;<local>";
        assertTrue(WindowsProxyResolver.isBypassed("localhost", patterns));
        assertTrue(WindowsProxyResolver.isBypassed("myhost.local", patterns));
        assertTrue(WindowsProxyResolver.isBypassed("10.0.0.1", patterns));
        assertTrue(WindowsProxyResolver.isBypassed("intranet", patterns));
        assertFalse(WindowsProxyResolver.isBypassed("www.google.com", patterns));
    }

    @Test
    void isBypassedNullSafe() {
        assertFalse(WindowsProxyResolver.isBypassed(null, "localhost"));
        assertFalse(WindowsProxyResolver.isBypassed("localhost", null));
        assertFalse(WindowsProxyResolver.isBypassed(null, null));
    }

    // ── parsePacResult() ─────────────────────────────────────────

    @Test
    void parsePacResultDirect() {
        ProxyResult res = WindowsProxyResolver.parsePacResult("DIRECT");
        assertTrue(res.isDirect());
    }

    @Test
    void parsePacResultProxy() {
        ProxyResult res = WindowsProxyResolver.parsePacResult("PROXY 10.0.0.1:3128");
        assertFalse(res.isDirect());
        assertEquals("10.0.0.1", res.getHost());
        assertEquals(3128, res.getPort());
    }

    @Test
    void parsePacResultMultipleEntries() {
        ProxyResult res = WindowsProxyResolver.parsePacResult("PROXY 10.0.0.1:3128; DIRECT");
        assertFalse(res.isDirect());
        assertEquals("10.0.0.1", res.getHost());
        assertEquals(3128, res.getPort());
    }

    @Test
    void parsePacResultEmptyIsDirect() {
        assertTrue(WindowsProxyResolver.parsePacResult("").isDirect());
        assertTrue(WindowsProxyResolver.parsePacResult(null).isDirect());
    }

    // ── extractProxyForProtocol() ────────────────────────────────

    @Test
    void extractsHttpsProtocol() {
        String server = "http=10.0.0.1:3128;https=10.0.0.2:3129;ftp=10.0.0.3:21";
        String result = WindowsProxyResolver.extractProxyForProtocol(server, "https://example.com");
        assertEquals("10.0.0.2:3129", result);
    }

    @Test
    void fallsBackToHttpProtocol() {
        String server = "http=10.0.0.1:3128";
        String result = WindowsProxyResolver.extractProxyForProtocol(server, "https://example.com");
        assertEquals("10.0.0.1:3128", result);
    }

    @Test
    void returnsNullForNoMatch() {
        String server = "ftp=10.0.0.3:21";
        String result = WindowsProxyResolver.extractProxyForProtocol(server, "https://example.com");
        assertNull(result);
    }

    // ── PAC script evaluation ────────────────────────────────────

    @Test
    void evaluatePacScriptReturnsProxy() {
        ProxyResult res = WindowsProxyResolver.evaluatePacScript(
                "function FindProxyForURL(url, host) { return 'PROXY 10.0.0.1:3128'; }",
                "https://example.com");
        assertFalse(res.isDirect());
        assertEquals("10.0.0.1", res.getHost());
        assertEquals(3128, res.getPort());
    }

    @Test
    void evaluatePacScriptReturnsDirect() {
        ProxyResult res = WindowsProxyResolver.evaluatePacScript(
                "function FindProxyForURL(url, host) { return 'DIRECT'; }",
                "https://example.com");
        assertTrue(res.isDirect());
    }

    @Test
    void evaluatePacScriptWithHelpers() {
        String script =
                "function FindProxyForURL(url, host) {\n" +
                "  if (isPlainHostName(host)) return 'DIRECT';\n" +
                "  if (dnsDomainIs(host, '.corp.local')) return 'DIRECT';\n" +
                "  return 'PROXY proxy.corp.local:8080';\n" +
                "}";

        ProxyResult local = WindowsProxyResolver.evaluatePacScript(script, "http://intranet");
        assertTrue(local.isDirect(), "plain hostname should be DIRECT");

        ProxyResult corp = WindowsProxyResolver.evaluatePacScript(script, "http://app.corp.local/path");
        assertTrue(corp.isDirect(), "corp.local domain should be DIRECT");

        ProxyResult ext = WindowsProxyResolver.evaluatePacScript(script, "https://www.google.com");
        assertFalse(ext.isDirect(), "external URL should use proxy");
        assertEquals("proxy.corp.local", ext.getHost());
        assertEquals(8080, ext.getPort());
    }

    @Test
    void evaluatePacScriptNullSafe() {
        ProxyResult res = WindowsProxyResolver.evaluatePacScript(null, "https://example.com");
        assertTrue(res.isDirect());
    }

    // ── WPAD auto-detect ────────────────────────────────────────

    @Test
    void isWpadAutoDetectDoesNotCrash() {
        // Just verifies no exception; actual value depends on machine
        boolean result = WindowsProxyResolver.isWpadAutoDetectEnabled();
        // result is either true or false — no assertion on the value
    }

    @Test
    void readConnectionFlagsDoesNotCrash() {
        int flags = WindowsProxyResolver.readConnectionFlags();
        // -1 means unable to read, otherwise 0–255
        assertTrue(flags >= -1 && flags <= 255,
                "Connection flags should be -1 or 0-255, was: " + flags);
    }

    // ── ProxyResult ──────────────────────────────────────────────

    @Test
    void proxyResultDirectToString() {
        ProxyResult res = ProxyResult.direct("test-reason");
        assertEquals("DIRECT (test-reason)", res.toString());
    }

    @Test
    void proxyResultProxyToString() {
        ProxyResult res = ProxyResult.proxy("10.0.0.1", 3128, "test");
        assertEquals("PROXY 10.0.0.1:3128 (test)", res.toString());
    }

    @Test
    void proxyResultToJavaProxy() {
        ProxyResult direct = ProxyResult.direct("x");
        assertEquals(java.net.Proxy.NO_PROXY, direct.toJavaProxy());

        ProxyResult proxy = ProxyResult.proxy("10.0.0.1", 3128, "x");
        assertNotEquals(java.net.Proxy.NO_PROXY, proxy.toJavaProxy());
        assertEquals(java.net.Proxy.Type.HTTP, proxy.toJavaProxy().type());
    }

    // ── PacUrlSource facade ──────────────────────────────────────

    @Test
    void defaultPacDiscoveryScriptIsNotEmpty() {
        assertNotNull(WindowsProxyResolver.DEFAULT_PAC_DISCOVERY_SCRIPT);
        assertFalse(WindowsProxyResolver.DEFAULT_PAC_DISCOVERY_SCRIPT.isEmpty());
        assertTrue(WindowsProxyResolver.DEFAULT_PAC_DISCOVERY_SCRIPT.contains("AutoConfigURL"),
                "Default script should query AutoConfigURL");
    }

    @Test
    void resolveWithDirectSourceAndEmptyUrlReturnsDirect() {
        ProxyResult res = WindowsProxyResolver.resolve("https://example.com", PacUrlSource.DIRECT, "");
        assertTrue(res.isDirect());
        assertTrue(res.getReason().contains("empty"), "reason: " + res.getReason());
    }

    @Test
    void resolveWithDirectSourceAndNullUrlReturnsDirect() {
        ProxyResult res = WindowsProxyResolver.resolve("https://example.com", PacUrlSource.DIRECT, null);
        assertTrue(res.isDirect());
    }

    @Test
    void resolveWithRegistrySourceDoesNotCrash() {
        ProxyResult res = WindowsProxyResolver.resolve("https://example.com", PacUrlSource.REGISTRY, null);
        assertNotNull(res);
        assertNotNull(res.getReason());
    }

    @Test
    void resolveWithNullSourceFallsBackToRegistry() {
        ProxyResult res = WindowsProxyResolver.resolve("https://example.com", null, null);
        assertNotNull(res);
    }

    @Test
    void pacUrlSourceEnumValues() {
        PacUrlSource[] values = PacUrlSource.values();
        assertEquals(3, values.length);
        assertNotNull(PacUrlSource.DIRECT);
        assertNotNull(PacUrlSource.REGISTRY);
        assertNotNull(PacUrlSource.POWERSHELL);
    }
}
