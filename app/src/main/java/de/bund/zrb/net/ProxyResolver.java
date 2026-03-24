package de.bund.zrb.net;

import de.bund.zrb.model.Settings;
import de.bund.zrb.winproxy.WindowsProxyResolver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Application-level proxy resolver that bridges between {@link Settings} and
 * the underlying proxy detection mechanisms.
 * <p>
 * Four modes are supported:
 * <ul>
 *   <li>{@link Mode#REGISTRY} — reads proxy from Windows Registry via
 *       {@link WindowsProxyResolver} (default, no PowerShell needed)</li>
 *   <li>{@link Mode#PAC_URL} — evaluates an explicit PAC URL (user-configured,
 *       no registry/PowerShell needed)</li>
 *   <li>{@link Mode#WINDOWS_PAC} — PowerShell-based PAC/WPAD resolution</li>
 *   <li>{@link Mode#MANUAL} — fixed host:port configuration</li>
 * </ul>
 */
public class ProxyResolver {

    private static final Logger LOG = Logger.getLogger(ProxyResolver.class.getName());

    public enum Mode {
        /** PowerShell PAC/WPAD script. */
        WINDOWS_PAC,
        /** Static proxy read from Windows Registry via {@code reg.exe} (no PowerShell needed). */
        REGISTRY,
        /** Explicit PAC URL — downloads and evaluates a user-configured PAC auto-config URL via GraalJS. */
        PAC_URL,
        /** Manual host:port configuration. */
        MANUAL
    }

    /**
     * Resolves the proxy for the given URL using the global proxy settings.
     * The proxy mode (PAC/WPAD vs MANUAL) and proxy host/port are taken from settings.
     * <p>
     * Callers should check the per-entry {@code useProxy} flag before calling this method.
     * If the entry does not want a proxy, callers should use {@link ProxyResolution#direct(String)} directly.
     *
     * @see #resolveForUrl(String, Settings, boolean)
     */
    public static ProxyResolution resolveForUrl(String url, Settings settings) {
        return resolveForUrl(url, settings, true);
    }

    /**
     * Resolves the proxy for the given URL. If {@code useProxy} is false, returns DIRECT immediately.
     * This is the preferred overload when the caller has a per-entry proxy flag.
     *
     * @param url       target URL
     * @param settings  global proxy configuration (mode, host, port, PAC script)
     * @param useProxy  per-entry proxy flag (from password manager)
     */
    public static ProxyResolution resolveForUrl(String url, Settings settings, boolean useProxy) {
        if (settings == null || !useProxy) {
            return ProxyResolution.direct("disabled");
        }

        Mode mode = parseMode(settings.proxyMode);
        if (mode == Mode.MANUAL) {
            if (settings.proxyNoProxyLocal && isLocalTarget(url)) {
                return ProxyResolution.direct("local-bypass");
            }
            if (settings.proxyHost == null || settings.proxyHost.trim().isEmpty() || settings.proxyPort <= 0) {
                return ProxyResolution.direct("manual-missing");
            }
            InetSocketAddress address = new InetSocketAddress(settings.proxyHost.trim(), settings.proxyPort);
            return ProxyResolution.proxy(new Proxy(Proxy.Type.HTTP, address), "manual");
        }

        if (mode == Mode.REGISTRY) {
            return resolveViaRegistry(url);
        }

        if (mode == Mode.PAC_URL) {
            return resolveViaPacUrl(url, settings.proxyPacUrl);
        }

        return resolveViaPowerShell(url, settings.proxyPacScript);
    }

    public static ProxyResolution testPacScript(String url, String script) {
        return resolveViaPowerShell(url, script);
    }

    /** Tests the PAC_URL proxy detection for a given URL and explicit PAC URL. */
    public static ProxyResolution testPacUrl(String url, String pacUrl) {
        return resolveViaPacUrl(url, pacUrl);
    }

    /** Tests the Registry proxy detection for a given URL. */
    public static ProxyResolution testRegistry(String url) {
        return resolveViaRegistry(url);
    }

    // ── Registry proxy detection — delegates to win-proxy module ──

    /**
     * Delegates to {@link WindowsProxyResolver#resolve(String)} and adapts the result.
     */
    private static ProxyResolution resolveViaRegistry(String url) {
        de.bund.zrb.winproxy.ProxyResult result = WindowsProxyResolver.resolve(url);
        return adaptResult(result, "registry");
    }

    // ── Explicit PAC URL — delegates to win-proxy module ────────

    /**
     * Downloads and evaluates an explicit PAC URL via {@link WindowsProxyResolver#evaluatePac}.
     * Used when the user has manually configured the PAC URL in settings (Mode PAC_URL).
     */
    private static ProxyResolution resolveViaPacUrl(String url, String pacUrl) {
        if (pacUrl == null || pacUrl.trim().isEmpty()) {
            return ProxyResolution.direct("pac-url-empty");
        }
        de.bund.zrb.winproxy.ProxyResult result = WindowsProxyResolver.evaluatePac(pacUrl.trim(), url);
        return adaptResult(result, "pac-url");
    }

    /**
     * Adapts a {@link de.bund.zrb.winproxy.ProxyResult} to a {@link ProxyResolution}.
     */
    private static ProxyResolution adaptResult(de.bund.zrb.winproxy.ProxyResult result, String prefix) {
        if (result.isDirect()) {
            return ProxyResolution.direct(prefix + "-" + result.getReason());
        }
        return ProxyResolution.proxy(result.toJavaProxy(), prefix + "-" + result.getReason());
    }

    /** Queries a single registry value via {@link WindowsProxyResolver#readRegistryValue}. */
    static String regQueryValue(String key, String valueName) {
        return WindowsProxyResolver.readRegistryValue(key, valueName);
    }

    /** Delegates to {@link WindowsProxyResolver#isBypassed(String, String)}. */
    static boolean isHostBypassed(String host, String proxyOverride) {
        return WindowsProxyResolver.isBypassed(host, proxyOverride);
    }

    // ── PowerShell PAC/WPAD resolution ──────────────────────────

    private static ProxyResolution resolveViaPowerShell(String url, String script) {
        if (script == null || script.trim().isEmpty()) {
            return ProxyResolution.direct("pac-empty");
        }

        try {
            Path temp = Files.createTempFile("proxy-test", ".ps1");
            try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write(script);
            }

            ProcessBuilder builder = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-File", temp.toAbsolutePath().toString(),
                    "-TestUrl", url
            );
            // Do NOT merge stderr — PowerShell error/warning messages would corrupt host:port parsing
            builder.redirectErrorStream(false);
            Process process = builder.start();

            // Drain stderr on a background thread to avoid deadlock
            // (if stderr buffer fills before stdout is consumed, the process blocks)
            final StringBuilder stderr = new StringBuilder();
            Thread stderrDrainer = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderr.append(line).append('\n');
                    }
                } catch (Exception ignored) { }
            }, "proxy-stderr-drain");
            stderrDrainer.setDaemon(true);
            stderrDrainer.start();

            // Read stdout (pipeline output = Write-Output) on the calling thread
            StringBuilder stdout = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdout.append(line).append('\n');
                }
            }

            stderrDrainer.join(5000); // wait for stderr drain to finish

            process.waitFor(15, TimeUnit.SECONDS);
            Files.deleteIfExists(temp);

            String result = stdout.toString().trim();
            String err = stderr.toString().trim();

            ProxyResolution res = parseProxyOutput(result);
            // If PAC resolved to DIRECT but stderr has content, attach it for diagnosis
            if (res.isDirect() && !err.isEmpty()) {
                return ProxyResolution.direct(res.getReason() + " [stderr: " + truncate(err, 120) + "]");
            }
            return res;
        } catch (Exception e) {
            return ProxyResolution.direct("pac-error: " + e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        return (s != null && s.length() > max) ? s.substring(0, max) + "…" : s;
    }

    /**
     * Parses the stdout output of the PAC/WPAD PowerShell script.
     * <p>
     * Expected format: a single line {@code "host:port"} or empty/no output for DIRECT.
     * This method is robust against:
     * <ul>
     *   <li>Multi-line output (takes the LAST non-empty line — the actual Write-Output result)</li>
     *   <li>BOM characters (U+FEFF) and other invisible Unicode</li>
     *   <li>URL-formatted output like {@code http://host:port/}</li>
     *   <li>Trailing slashes, whitespace, carriage returns</li>
     *   <li>ANSI escape sequences</li>
     * </ul>
     */
    static ProxyResolution parseProxyOutput(String result) {
        if (result == null || result.trim().isEmpty()) {
            return ProxyResolution.direct("pac-direct");
        }

        // Take the LAST non-empty line — Write-Output result is the final pipeline output
        String candidate = null;
        for (String line : result.split("\\r?\\n")) {
            String stripped = stripNonPrintable(line).trim();
            if (!stripped.isEmpty()) {
                candidate = stripped;
            }
        }

        if (candidate == null || "DIRECT".equalsIgnoreCase(candidate)) {
            return ProxyResolution.direct("pac-direct");
        }

        // Strip URL scheme prefix (e.g. "http://10.130.165.20:3128/" → "10.130.165.20:3128")
        String normalized = candidate;
        if (normalized.toLowerCase(Locale.ROOT).startsWith("http://")) {
            normalized = normalized.substring(7);
        } else if (normalized.toLowerCase(Locale.ROOT).startsWith("https://")) {
            normalized = normalized.substring(8);
        }
        // Strip trailing slash
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        normalized = normalized.trim();

        int idx = normalized.lastIndexOf(':');
        if (idx > 0 && idx < normalized.length() - 1) {
            String host = normalized.substring(0, idx).trim();
            // Strip only digits from port part (guard against trailing garbage)
            String portRaw = normalized.substring(idx + 1).trim().replaceAll("[^0-9]", "");
            if (!portRaw.isEmpty()) {
                try {
                    int port = Integer.parseInt(portRaw);
                    if (port > 0 && port <= 65535) {
                        return ProxyResolution.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)), "pac");
                    }
                } catch (NumberFormatException ignore) {
                    // fall through
                }
            }
            return ProxyResolution.direct("pac-invalid-port: '" + candidate + "'");
        }
        return ProxyResolution.direct("pac-invalid: '" + candidate + "'");
    }

    /**
     * Strips BOM (U+FEFF), ANSI escape sequences, and other non-printable characters
     * that PowerShell may emit depending on console encoding and version.
     */
    private static String stripNonPrintable(String s) {
        if (s == null) return "";
        // Remove BOM
        if (s.length() > 0 && s.charAt(0) == '\uFEFF') {
            s = s.substring(1);
        }
        // Remove ANSI escape sequences (ESC [ … m)
        s = s.replaceAll("\\x1B\\[[0-9;]*[a-zA-Z]", "");
        // Remove remaining non-printable chars (except space)
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x20 || c == '\t') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Mode parseMode(String raw) {
        if (raw == null) {
            return Mode.REGISTRY;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("MANUAL".equals(normalized)) {
            return Mode.MANUAL;
        }
        if ("WINDOWS_PAC".equals(normalized)) {
            return Mode.WINDOWS_PAC;
        }
        if ("PAC_URL".equals(normalized)) {
            return Mode.PAC_URL;
        }
        return Mode.REGISTRY;
    }

    private static boolean isLocalTarget(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                return true;
            }
            if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host)) {
                return true;
            }
            InetAddress address = InetAddress.getByName(host);
            return address.isLoopbackAddress() || address.isSiteLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }

    public static final class ProxyResolution {
        private final boolean direct;
        private final Proxy proxy;
        private final String reason;

        private ProxyResolution(boolean direct, Proxy proxy, String reason) {
            this.direct = direct;
            this.proxy = proxy;
            this.reason = reason;
        }

        public static ProxyResolution direct(String reason) {
            return new ProxyResolution(true, Proxy.NO_PROXY, reason);
        }

        public static ProxyResolution proxy(Proxy proxy, String reason) {
            return new ProxyResolution(false, proxy, reason);
        }

        public boolean isDirect() {
            return direct;
        }

        public Proxy getProxy() {
            return proxy;
        }

        public String getReason() {
            return reason;
        }
    }
}
