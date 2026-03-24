package de.bund.zrb.winproxy;

import java.net.URI;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Detects proxy settings on Windows by reading the Windows Registry and
 * evaluating PAC/WPAD auto-configuration scripts via GraalJS.
 * <p>
 * <b>No PowerShell required.</b> This works on hardened systems where
 * PowerShell Constrained Language Mode (CLM) blocks .NET method calls
 * like {@code [System.Net.WebRequest]::GetSystemWebProxy()}.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>Reads {@code AutoConfigURL} from
 *       {@code HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings}.
 *       If present, downloads the PAC file and evaluates {@code FindProxyForURL()}
 *       via GraalJS.</li>
 *   <li>Falls back to static proxy settings ({@code ProxyEnable} + {@code ProxyServer}).</li>
 *   <li>Respects the {@code ProxyOverride} bypass list (wildcards, {@code <local>}).</li>
 * </ol>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Full auto-detection (PAC → static → DIRECT)
 * ProxyResult result = WindowsProxyResolver.resolve("https://example.com");
 *
 * if (result.isDirect()) {
 *     connection = url.openConnection();
 * } else {
 *     connection = url.openConnection(result.toJavaProxy());
 * }
 *
 * // Just evaluate a specific PAC URL
 * ProxyResult pac = WindowsProxyResolver.evaluatePac(
 *     "http://wpad.corp.local/wpad.dat", "https://example.com");
 *
 * // Raw registry access
 * String autoConfig = WindowsProxyResolver.readRegistryValue(
 *     WindowsProxyResolver.INTERNET_SETTINGS_KEY, "AutoConfigURL");
 * }</pre>
 *
 * <h3>Dependencies</h3>
 * <ul>
 *   <li><b>GraalJS 21.2.0</b> ({@code org.graalvm.js:js}) — for PAC script evaluation</li>
 *   <li><b>reg.exe</b> — ships with every Windows installation</li>
 * </ul>
 *
 * @see ProxyResult
 */
public final class WindowsProxyResolver {

    private static final Logger LOG = Logger.getLogger(WindowsProxyResolver.class.getName());

    /** The Windows Registry key containing Internet/proxy settings. */
    public static final String INTERNET_SETTINGS_KEY =
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings";

    private WindowsProxyResolver() {}

    // ── Public API ───────────────────────────────────────────────

    /**
     * Resolves the proxy for the given URL using the full Windows proxy detection chain:
     * <ol>
     *   <li>AutoConfigURL (explicit PAC) → downloads PAC, evaluates {@code FindProxyForURL()}</li>
     *   <li>WPAD auto-detect → tries {@code http://wpad/wpad.dat} if the auto-detect flag is set
     *       in {@code DefaultConnectionSettings}</li>
     *   <li>Static proxy ({@code ProxyEnable} + {@code ProxyServer})</li>
     *   <li>{@code ProxyOverride} bypass list</li>
     * </ol>
     *
     * @param url the target URL (e.g. {@code "https://example.com"})
     * @return a {@link ProxyResult} — never {@code null}
     */
    public static ProxyResult resolve(String url) {
        try {
            // 1) Check for explicit AutoConfigURL (PAC URL set in Internet Options)
            String autoConfigUrl = RegistryReader.queryValue(INTERNET_SETTINGS_KEY, "AutoConfigURL");
            if (autoConfigUrl != null && !autoConfigUrl.trim().isEmpty()) {
                LOG.fine("[WinProxy] AutoConfigURL = " + autoConfigUrl);
                ProxyResult pacResult = evaluatePacInternal(autoConfigUrl.trim(), url);
                if (pacResult != null) {
                    return pacResult;
                }
                // PAC evaluation failed — fall through
            }

            // 2) WPAD auto-detect: if the "Automatically detect settings" flag is set,
            //    try the standard WPAD URL http://wpad/wpad.dat
            //    (Windows resolves "wpad" using DNS devolution, appending domain suffixes)
            if (isWpadAutoDetectEnabled()) {
                LOG.fine("[WinProxy] WPAD auto-detect is enabled, trying http://wpad/wpad.dat");
                ProxyResult wpadResult = resolveViaWpad(url);
                if (wpadResult != null) {
                    return wpadResult;
                }
                LOG.fine("[WinProxy] WPAD evaluation failed or returned null, falling through");
            }

            // 3) Check static proxy
            return resolveStatic(url);
        } catch (Exception e) {
            LOG.log(Level.FINE, "[WinProxy] Resolution failed", e);
            return ProxyResult.direct("error: " + e.getMessage());
        }
    }

    /**
     * Resolves the proxy using <b>only</b> the static registry settings
     * ({@code ProxyEnable}, {@code ProxyServer}, {@code ProxyOverride}).
     * <p>
     * Skips AutoConfigURL / PAC evaluation entirely.
     *
     * @param url the target URL
     * @return a {@link ProxyResult} — never {@code null}
     */
    public static ProxyResult resolveStatic(String url) {
        try {
            String proxyEnable = RegistryReader.queryValue(INTERNET_SETTINGS_KEY, "ProxyEnable");
            if (!"0x1".equals(proxyEnable != null ? proxyEnable.trim() : "")) {
                return ProxyResult.direct("proxy-disabled");
            }

            String proxyServer = RegistryReader.queryValue(INTERNET_SETTINGS_KEY, "ProxyServer");
            if (proxyServer == null || proxyServer.trim().isEmpty()) {
                return ProxyResult.direct("no-proxy-server");
            }

            // Check bypass list
            String proxyOverride = RegistryReader.queryValue(INTERNET_SETTINGS_KEY, "ProxyOverride");
            if (proxyOverride != null && !proxyOverride.trim().isEmpty() && url != null) {
                try {
                    String targetHost = URI.create(url).getHost();
                    if (targetHost != null && isBypassed(targetHost, proxyOverride)) {
                        return ProxyResult.direct("bypass-match");
                    }
                } catch (Exception ignore) { }
            }

            String server = proxyServer.trim();

            // Handle protocol-specific format: "http=host:port;https=host:port"
            if (server.contains("=")) {
                String extracted = extractProxyForProtocol(server, url);
                if (extracted != null) {
                    server = extracted;
                } else {
                    return ProxyResult.direct("no-matching-protocol");
                }
            }

            return parseHostPort(server, "static");
        } catch (Exception e) {
            LOG.log(Level.FINE, "[WinProxy] Static resolution failed", e);
            return ProxyResult.direct("error: " + e.getMessage());
        }
    }

    /**
     * Evaluates a PAC auto-configuration script from the given URL.
     * <p>
     * Downloads the PAC file (bypassing any proxy) and evaluates
     * {@code FindProxyForURL(targetUrl, host)} via GraalJS.
     *
     * @param pacUrl    the PAC file URL (e.g. {@code "http://wpad.corp.local/wpad.dat"})
     * @param targetUrl the URL to resolve the proxy for
     * @return a {@link ProxyResult} — never {@code null}
     */
    public static ProxyResult evaluatePac(String pacUrl, String targetUrl) {
        ProxyResult result = evaluatePacInternal(pacUrl, targetUrl);
        return result != null ? result : ProxyResult.direct("pac-evaluation-failed");
    }

    /**
     * Evaluates a PAC script string (not a URL) against the given target URL.
     * <p>
     * Useful for testing PAC scripts without downloading them first.
     *
     * @param pacScript the JavaScript PAC script content
     * @param targetUrl the URL to resolve the proxy for
     * @return a {@link ProxyResult} — never {@code null}
     */
    public static ProxyResult evaluatePacScript(String pacScript, String targetUrl) {
        try {
            String pacResult = PacEvaluator.evaluateScript(pacScript, targetUrl);
            if (pacResult == null) {
                return ProxyResult.direct("pac-script-null");
            }
            return parsePacResult(pacResult.trim());
        } catch (Exception e) {
            LOG.log(Level.FINE, "[WinProxy] PAC script evaluation failed", e);
            return ProxyResult.direct("pac-script-error: " + e.getMessage());
        }
    }

    /**
     * Reads a single value from the Windows Registry via {@code reg.exe}.
     *
     * @param key       full registry key path (e.g. {@link #INTERNET_SETTINGS_KEY})
     * @param valueName name of the value to query (e.g. {@code "AutoConfigURL"})
     * @return the value string, or {@code null} if not found
     */
    public static String readRegistryValue(String key, String valueName) {
        return RegistryReader.queryValue(key, valueName);
    }

    /**
     * Checks if a host matches the Windows proxy bypass pattern list.
     * <p>
     * Supports:
     * <ul>
     *   <li>Exact match: {@code "localhost"}</li>
     *   <li>Wildcard prefix: {@code "*.corp.local"}</li>
     *   <li>Wildcard suffix: {@code "10.*"}</li>
     *   <li>{@code <local>} — matches hostnames without dots</li>
     * </ul>
     * Patterns are separated by semicolons, matching is case-insensitive.
     *
     * @param host          the target hostname
     * @param proxyOverride the {@code ProxyOverride} value from the registry
     * @return {@code true} if the host is bypassed
     */
    public static boolean isBypassed(String host, String proxyOverride) {
        if (host == null || proxyOverride == null) return false;
        String lowerHost = host.toLowerCase(Locale.ROOT);
        for (String pattern : proxyOverride.split(";")) {
            String p = pattern.trim().toLowerCase(Locale.ROOT);
            if (p.isEmpty()) continue;
            if ("<local>".equals(p)) {
                if (!lowerHost.contains(".")) return true;
                continue;
            }
            if (p.startsWith("*")) {
                if (lowerHost.endsWith(p.substring(1))) return true;
            } else if (p.endsWith("*")) {
                if (lowerHost.startsWith(p.substring(0, p.length() - 1))) return true;
            } else if (lowerHost.equals(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the "Automatically detect settings" (WPAD) flag is enabled
     * in the Windows connection settings.
     * <p>
     * Reads the {@code DefaultConnectionSettings} binary value from
     * {@code HKCU\...\Internet Settings\Connections} and checks bit 3 (0x08)
     * of the flags byte at offset 8.
     */
    public static boolean isWpadAutoDetectEnabled() {
        int flags = RegistryReader.queryConnectionFlags();
        if (flags < 0) return false; // unable to read
        return (flags & RegistryReader.FLAG_AUTO_DETECT) != 0;
    }

    /**
     * Reads the raw connection flags byte from {@code DefaultConnectionSettings}.
     * Useful for diagnostics. Returns -1 if unreadable.
     *
     * @see #isWpadAutoDetectEnabled()
     */
    public static int readConnectionFlags() {
        return RegistryReader.queryConnectionFlags();
    }

    // ── Internal ─────────────────────────────────────────────────

    /**
     * Tries WPAD auto-detection by downloading {@code http://wpad/wpad.dat}.
     * Windows DNS resolver appends configured domain suffixes, so "wpad" typically
     * resolves to e.g. "wpad.corp.local" automatically.
     *
     * @return a {@link ProxyResult}, or {@code null} if WPAD failed
     */
    private static ProxyResult resolveViaWpad(String targetUrl) {
        try {
            String wpadResult = PacEvaluator.evaluate("http://wpad/wpad.dat", targetUrl);
            if (wpadResult == null) {
                return null;
            }
            ProxyResult result = parsePacResult(wpadResult.trim());
            // Re-tag reason with "wpad" prefix so callers can see the source
            if (result.isDirect()) {
                return ProxyResult.direct("wpad-direct");
            } else {
                return ProxyResult.proxy(result.getHost(), result.getPort(), "wpad");
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "[WinProxy] WPAD evaluation failed", e);
            return null;
        }
    }

    private static ProxyResult evaluatePacInternal(String pacUrl, String targetUrl) {
        try {
            String pacResult = PacEvaluator.evaluate(pacUrl, targetUrl);
            if (pacResult == null) {
                return null; // evaluation failed — caller should try fallback
            }
            return parsePacResult(pacResult.trim());
        } catch (Exception e) {
            LOG.log(Level.FINE, "[WinProxy] PAC evaluation failed for " + pacUrl, e);
            return null;
        }
    }

    /**
     * Parses a PAC result string like {@code "PROXY 10.0.0.1:3128"},
     * {@code "PROXY host:port; DIRECT"}, or {@code "DIRECT"}.
     */
    static ProxyResult parsePacResult(String pacResult) {
        if (pacResult == null || pacResult.isEmpty() || "DIRECT".equalsIgnoreCase(pacResult)) {
            return ProxyResult.direct("pac-direct");
        }

        for (String entry : pacResult.split(";")) {
            String trimmed = entry.trim();
            if (trimmed.toUpperCase(Locale.ROOT).startsWith("PROXY ")) {
                String hostPort = trimmed.substring(6).trim();
                ProxyResult parsed = parseHostPort(hostPort, "pac");
                if (!parsed.isDirect()) {
                    return parsed;
                }
            }
        }
        return ProxyResult.direct("pac-direct");
    }

    private static ProxyResult parseHostPort(String hostPort, String source) {
        int idx = hostPort.lastIndexOf(':');
        if (idx > 0 && idx < hostPort.length() - 1) {
            String host = hostPort.substring(0, idx).trim();
            String portStr = hostPort.substring(idx + 1).trim().replaceAll("[^0-9]", "");
            if (!portStr.isEmpty()) {
                try {
                    int port = Integer.parseInt(portStr);
                    if (port > 0 && port <= 65535) {
                        return ProxyResult.proxy(host, port, source);
                    }
                } catch (NumberFormatException ignore) { }
            }
        }
        return ProxyResult.direct(source + "-invalid: " + hostPort);
    }

    static String extractProxyForProtocol(String proxyServer, String url) {
        String protocol = "http";
        if (url != null) {
            try { protocol = URI.create(url).getScheme(); } catch (Exception ignore) { }
        }
        if (protocol == null) protocol = "http";

        for (String entry : proxyServer.split(";")) {
            String[] kv = entry.split("=", 2);
            if (kv.length == 2 && protocol.equalsIgnoreCase(kv[0].trim())) {
                return kv[1].trim();
            }
        }
        // Fallback to http entry
        if (!"http".equalsIgnoreCase(protocol)) {
            for (String entry : proxyServer.split(";")) {
                String[] kv = entry.split("=", 2);
                if (kv.length == 2 && "http".equalsIgnoreCase(kv[0].trim())) {
                    return kv[1].trim();
                }
            }
        }
        return null;
    }
}
