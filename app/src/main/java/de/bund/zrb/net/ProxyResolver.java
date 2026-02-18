package de.bund.zrb.net;

import de.bund.zrb.model.Settings;

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

public class ProxyResolver {

    public enum Mode {
        WINDOWS_PAC,
        MANUAL
    }

    public static ProxyResolution resolveForUrl(String url, Settings settings) {
        if (settings == null || !settings.proxyEnabled) {
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

        return resolveViaPowerShell(url, settings.proxyPacScript);
    }

    public static ProxyResolution testPacScript(String url, String script) {
        return resolveViaPowerShell(url, script);
    }

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
            builder.redirectErrorStream(true);
            Process process = builder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            process.waitFor(15, TimeUnit.SECONDS);
            Files.deleteIfExists(temp);

            String result = output.toString().trim();
            return parseProxyOutput(result);
        } catch (Exception e) {
            return ProxyResolution.direct("pac-error: " + e.getMessage());
        }
    }

    static ProxyResolution parseProxyOutput(String result) {
        if (result == null || result.trim().isEmpty()) {
            return ProxyResolution.direct("pac-direct");
        }
        String trimmed = result.trim();
        if ("DIRECT".equalsIgnoreCase(trimmed)) {
            return ProxyResolution.direct("pac-direct");
        }
        int idx = trimmed.lastIndexOf(':');
        if (idx > 0 && idx < trimmed.length() - 1) {
            String host = trimmed.substring(0, idx).trim();
            String portRaw = trimmed.substring(idx + 1).trim();
            try {
                int port = Integer.parseInt(portRaw);
                return ProxyResolution.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)), "pac");
            } catch (NumberFormatException ignore) {
                return ProxyResolution.direct("pac-invalid-port");
            }
        }
        return ProxyResolution.direct("pac-invalid");
    }

    private static Mode parseMode(String raw) {
        if (raw == null) {
            return Mode.WINDOWS_PAC;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("MANUAL".equals(normalized)) {
            return Mode.MANUAL;
        }
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
