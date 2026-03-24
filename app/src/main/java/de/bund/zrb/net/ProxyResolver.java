package de.bund.zrb.net;

import de.bund.zrb.model.Settings;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
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
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProxyResolver {

    private static final Logger LOG = Logger.getLogger(ProxyResolver.class.getName());

    public enum Mode {
        /**
         * Automatic proxy detection via PowerShell PAC/WPAD script.
         * <p>
         * Fallback chain when PowerShell fails (e.g. Constrained Language Mode):
         * <ol>
         *   <li>Windows Registry ({@code reg query} — fast, handles static proxy)</li>
         *   <li>Java subprocess with {@code -Djava.net.useSystemProxies=true} — handles WPAD/PAC</li>
         * </ol>
         */
        WINDOWS_PAC,
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

        // ── WINDOWS_PAC: PowerShell → Registry → Java Subprocess ──────────

        // 1) Try PowerShell PAC/WPAD script
        ProxyResolution psResult = resolveViaPowerShell(url, settings.proxyPacScript);
        if (!psResult.isDirect()) {
            return psResult; // PowerShell found a proxy → use it
        }

        // PowerShell returned DIRECT — check if it was a genuine DIRECT or an error
        String reason = psResult.getReason();
        boolean psError = reason.contains("stderr:") || reason.contains("pac-error")
                || reason.contains("pac-invalid") || reason.contains("pac-empty");
        if (!psError) {
            return psResult; // genuine DIRECT from PAC script — trust it
        }

        LOG.info("[Proxy] PowerShell PAC failed (" + reason + "), trying registry fallback");

        // 2) Try Windows Registry (fast — handles static proxy config)
        ProxyResolution regResult = resolveViaRegistry(url);
        if (!regResult.isDirect()) {
            return regResult;
        }
        // Registry returned DIRECT — this may mean "no static proxy" but WPAD might still work
        boolean regHadAutoConfig = "registry-has-autoconfig".equals(regResult.getReason());
        if (!regHadAutoConfig && !"registry-proxy-disabled".equals(regResult.getReason())) {
            // Registry explicitly shows no proxy configured — genuine DIRECT
            return regResult;
        }

        // 3) Try Java subprocess with -Djava.net.useSystemProxies=true (handles WPAD)
        LOG.info("[Proxy] Registry has no static proxy, trying Java subprocess for WPAD detection");
        ProxyResolution subResult = resolveViaJavaSubprocess(url);
        if (!subResult.isDirect()) {
            return subResult;
        }
        return ProxyResolution.direct("all-methods-direct");
    }

    public static ProxyResolution testPacScript(String url, String script) {
        return resolveViaPowerShell(url, script);
    }

    /**
     * Tests the Windows Registry proxy detection for the given URL.
     */
    public static ProxyResolution testRegistry(String url) {
        return resolveViaRegistry(url);
    }

    /**
     * Tests the Java subprocess proxy detection for the given URL.
     * Spawns a fresh JVM with {@code -Djava.net.useSystemProxies=true}.
     */
    public static ProxyResolution testJavaSubprocess(String url) {
        return resolveViaJavaSubprocess(url);
    }

    // ── Windows Registry proxy detection ─────────────────────────

    /**
     * Reads proxy settings from the Windows Registry via {@code reg query}.
     * This works on <b>all</b> Windows machines regardless of PowerShell
     * Constrained Language Mode, because {@code reg.exe} is a native binary.
     * <p>
     * Reads from {@code HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings}:
     * <ul>
     *   <li>{@code ProxyEnable} (DWORD) — 1 = proxy enabled</li>
     *   <li>{@code ProxyServer} (REG_SZ) — e.g. "10.0.0.1:3128" or "http=host:port;https=host:port"</li>
     *   <li>{@code AutoConfigURL} (REG_SZ) — PAC URL (indicates auto-config is in use)</li>
     * </ul>
     */
    private static ProxyResolution resolveViaRegistry(String url) {
        try {
            String regKey = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings";

            // Check if a PAC/auto-config URL is set (WPAD)
            String autoConfigUrl = regQueryValue(regKey, "AutoConfigURL");
            boolean hasAutoConfig = autoConfigUrl != null && !autoConfigUrl.trim().isEmpty();

            // Check if static proxy is enabled
            String proxyEnable = regQueryValue(regKey, "ProxyEnable");
            boolean enabled = "0x1".equals(proxyEnable != null ? proxyEnable.trim() : "");

            if (!enabled) {
                // Static proxy disabled — but auto-config may still be active
                if (hasAutoConfig) {
                    LOG.fine("[Proxy] Registry: static proxy disabled, but AutoConfigURL set: " + autoConfigUrl);
                    return ProxyResolution.direct("registry-has-autoconfig");
                }
                return ProxyResolution.direct("registry-proxy-disabled");
            }

            // Read static proxy server
            String proxyServer = regQueryValue(regKey, "ProxyServer");
            if (proxyServer == null || proxyServer.trim().isEmpty()) {
                if (hasAutoConfig) {
                    return ProxyResolution.direct("registry-has-autoconfig");
                }
                return ProxyResolution.direct("registry-no-proxy-server");
            }

            // Check proxy bypass list
            String proxyOverride = regQueryValue(regKey, "ProxyOverride");
            if (proxyOverride != null && !proxyOverride.trim().isEmpty() && url != null) {
                try {
                    String targetHost = URI.create(url).getHost();
                    if (targetHost != null && isHostBypassed(targetHost, proxyOverride)) {
                        return ProxyResolution.direct("registry-bypass");
                    }
                } catch (Exception ignore) { /* proceed with proxy */ }
            }

            // Parse ProxyServer value
            String server = proxyServer.trim();

            // Handle protocol-specific format: "http=host:port;https=host:port;..."
            if (server.contains("=")) {
                String extracted = extractProxyForProtocol(server, url);
                if (extracted != null) {
                    server = extracted;
                } else {
                    return ProxyResolution.direct("registry-no-matching-protocol");
                }
            }

            // Parse host:port
            int idx = server.lastIndexOf(':');
            if (idx > 0 && idx < server.length() - 1) {
                String host = server.substring(0, idx).trim();
                String portStr = server.substring(idx + 1).trim().replaceAll("[^0-9]", "");
                if (!portStr.isEmpty()) {
                    int port = Integer.parseInt(portStr);
                    if (port > 0 && port <= 65535) {
                        LOG.fine("[Proxy] Registry resolved: " + host + ":" + port);
                        return ProxyResolution.proxy(
                                new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)), "registry");
                    }
                }
            }
            return ProxyResolution.direct("registry-invalid: " + proxyServer);
        } catch (Exception e) {
            LOG.log(Level.FINE, "[Proxy] Registry fallback failed", e);
            return ProxyResolution.direct("registry-error: " + e.getMessage());
        }
    }

    /**
     * Queries a single registry value via {@code reg query}.
     *
     * @return the value data, or {@code null} if not found
     */
    static String regQueryValue(String key, String valueName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query", key, "/v", valueName);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            p.waitFor(5, TimeUnit.SECONDS);

            // Parse output: "    ProxyServer    REG_SZ    host:port"
            for (String line : out.toString().split("\\r?\\n")) {
                String trimmed = line.trim();
                // Match lines that contain the value name followed by REG_SZ or REG_DWORD
                if (trimmed.contains(valueName)) {
                    // Split on 2+ whitespace characters
                    String[] parts = trimmed.split("\\s{2,}");
                    if (parts.length >= 3) {
                        return parts[parts.length - 1].trim();
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "[Proxy] reg query failed for " + valueName, e);
        }
        return null;
    }

    /**
     * Extracts the proxy for the target URL's protocol from a protocol-specific
     * ProxyServer value like {@code "http=10.0.0.1:3128;https=10.0.0.1:3129;ftp=..."}.
     */
    private static String extractProxyForProtocol(String proxyServer, String url) {
        String protocol = "http";
        if (url != null) {
            try {
                protocol = URI.create(url).getScheme();
            } catch (Exception ignore) { }
        }
        if (protocol == null) protocol = "http";

        for (String entry : proxyServer.split(";")) {
            String[] kv = entry.split("=", 2);
            if (kv.length == 2 && protocol.equalsIgnoreCase(kv[0].trim())) {
                return kv[1].trim();
            }
        }
        // Fallback: try "http" if original protocol not found
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

    /**
     * Checks if a host matches the ProxyOverride (bypass) list from the registry.
     * Supports patterns like {@code "*.local;10.*;localhost;<local>"}.
     */
    static boolean isHostBypassed(String host, String proxyOverride) {
        if (host == null || proxyOverride == null) return false;
        String lowerHost = host.toLowerCase(Locale.ROOT);
        for (String pattern : proxyOverride.split(";")) {
            String p = pattern.trim().toLowerCase(Locale.ROOT);
            if (p.isEmpty()) continue;
            if ("<local>".equals(p)) {
                if (!lowerHost.contains(".")) return true; // no dots = local name
                continue;
            }
            // Convert wildcard pattern to simple matching
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

    // ── Java subprocess proxy detection ──────────────────────────

    /**
     * Spawns a fresh Java subprocess with {@code -Djava.net.useSystemProxies=true}
     * to detect the system proxy. This is the most reliable method for WPAD/PAC
     * detection because the property is set at JVM startup (before
     * {@code DefaultProxySelector} class initialization).
     * <p>
     * Runs {@link ProxyProbe} which outputs {@code host:port} or {@code DIRECT}.
     */
    private static ProxyResolution resolveViaJavaSubprocess(String url) {
        try {
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
            String classpath = System.getProperty("java.class.path");

            ProcessBuilder pb = new ProcessBuilder(
                    javaBin,
                    "-Djava.net.useSystemProxies=true",
                    "-cp", classpath,
                    "de.bund.zrb.net.ProxyProbe",
                    url
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdout.append(line).append('\n');
                }
            }

            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ProxyResolution.direct("subprocess-timeout");
            }

            String result = stdout.toString().trim();
            if (result.isEmpty() || "DIRECT".equalsIgnoreCase(result)) {
                return ProxyResolution.direct("subprocess-direct");
            }

            // Parse host:port
            // Take the last non-empty line (in case JVM prints warnings before our output)
            String candidate = result;
            for (String line : result.split("\\r?\\n")) {
                String stripped = line.trim();
                if (!stripped.isEmpty()) {
                    candidate = stripped;
                }
            }

            int idx = candidate.lastIndexOf(':');
            if (idx > 0 && idx < candidate.length() - 1) {
                String host = candidate.substring(0, idx).trim();
                String portStr = candidate.substring(idx + 1).trim().replaceAll("[^0-9]", "");
                if (!portStr.isEmpty()) {
                    int port = Integer.parseInt(portStr);
                    if (port > 0 && port <= 65535) {
                        LOG.fine("[Proxy] Java subprocess resolved: " + host + ":" + port);
                        return ProxyResolution.proxy(
                                new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)), "subprocess");
                    }
                }
            }
            return ProxyResolution.direct("subprocess-invalid: " + candidate);
        } catch (Exception e) {
            LOG.log(Level.FINE, "[Proxy] Java subprocess fallback failed", e);
            return ProxyResolution.direct("subprocess-error: " + e.getMessage());
        }
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
            Thread stderrDrainer = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            stderr.append(line).append('\n');
                        }
                        reader.close();
                    } catch (Exception ignored) { }
                }
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
            return Mode.WINDOWS_PAC;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("MANUAL".equals(normalized)) {
            return Mode.MANUAL;
        }
        // JAVA_SYSTEM was removed — map old setting to WINDOWS_PAC
        // (WINDOWS_PAC now includes the same subprocess-based detection)
        return Mode.WINDOWS_PAC;
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
