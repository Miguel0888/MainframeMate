package de.bund.zrb.winproxy;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * Immutable result of a proxy resolution.
 * <p>
 * Provides both structured access ({@link #getHost()}, {@link #getPort()}) and a
 * convenience method {@link #toJavaProxy()} to create a {@link java.net.Proxy} for
 * direct use with {@link java.net.URLConnection} or OkHttp.
 *
 * <pre>{@code
 * ProxyResult result = WindowsProxyResolver.resolve("https://example.com");
 * if (result.isDirect()) {
 *     conn = url.openConnection();
 * } else {
 *     conn = url.openConnection(result.toJavaProxy());
 * }
 * }</pre>
 */
public final class ProxyResult {

    /** Whether this result indicates a direct (no-proxy) connection. */
    private final boolean direct;

    /** Proxy host — only meaningful when {@link #isDirect()} is {@code false}. */
    private final String host;

    /** Proxy port — only meaningful when {@link #isDirect()} is {@code false}. */
    private final int port;

    /** Human-readable reason for this result (useful for logging and diagnostics). */
    private final String reason;

    private ProxyResult(boolean direct, String host, int port, String reason) {
        this.direct = direct;
        this.host = host;
        this.port = port;
        this.reason = reason;
    }

    // ── Factory methods ──────────────────────────────────────────

    /**
     * Creates a DIRECT result (no proxy needed).
     *
     * @param reason diagnostic reason (e.g. "proxy-disabled", "bypass-match")
     */
    public static ProxyResult direct(String reason) {
        return new ProxyResult(true, null, 0, reason);
    }

    /**
     * Creates a PROXY result.
     *
     * @param host   proxy hostname or IP
     * @param port   proxy port (1–65535)
     * @param reason diagnostic reason (e.g. "registry-static", "pac-auto-config")
     */
    public static ProxyResult proxy(String host, int port, String reason) {
        return new ProxyResult(false, host, port, reason);
    }

    // ── Accessors ────────────────────────────────────────────────

    /** Returns {@code true} if the target URL should be accessed directly (no proxy). */
    public boolean isDirect() {
        return direct;
    }

    /**
     * Returns the proxy host, or {@code null} for DIRECT results.
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the proxy port, or {@code 0} for DIRECT results.
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns a human-readable diagnostic reason for this result.
     * Useful for logging and debugging proxy resolution issues.
     */
    public String getReason() {
        return reason;
    }

    // ── Convenience ──────────────────────────────────────────────

    /**
     * Converts this result to a {@link java.net.Proxy} instance.
     * <p>
     * Returns {@link Proxy#NO_PROXY} for DIRECT results, or an HTTP proxy for PROXY results.
     */
    public Proxy toJavaProxy() {
        if (direct) {
            return Proxy.NO_PROXY;
        }
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
    }

    @Override
    public String toString() {
        if (direct) {
            return "DIRECT (" + reason + ")";
        }
        return "PROXY " + host + ":" + port + " (" + reason + ")";
    }
}
