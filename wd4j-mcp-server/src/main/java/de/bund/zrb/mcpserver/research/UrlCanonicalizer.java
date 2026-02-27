package de.bund.zrb.mcpserver.research;

import java.net.URI;

/**
 * Lightweight URL canonicalization for deduplication and visited-tracking.
 * Normalizes scheme, host (lowercase), port, path (trailing-slash), and removes fragment.
 */
public final class UrlCanonicalizer {

    private UrlCanonicalizer() {}

    /**
     * Returns a canonical form of the URL suitable for equality comparisons.
     * Normalizations applied:
     * <ul>
     *   <li>Scheme and host lowercased</li>
     *   <li>Default ports removed (80 for http, 443 for https)</li>
     *   <li>Trailing slash normalized (single trailing slash kept for root, otherwise stripped)</li>
     *   <li>Fragment (#...) removed</li>
     *   <li>Query string preserved as-is</li>
     * </ul>
     *
     * @return canonical URL string, or the original if parsing fails
     */
    public static String canonicalize(String url) {
        if (url == null || url.isEmpty()) return url;
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "https";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
            int port = uri.getPort();

            // Strip default ports
            if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
                port = -1;
            }

            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            // Strip trailing slashes (keep root "/")
            while (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            String query = uri.getRawQuery();

            StringBuilder sb = new StringBuilder();
            sb.append(scheme).append("://").append(host);
            if (port > 0) sb.append(':').append(port);
            sb.append(path);
            if (query != null && !query.isEmpty()) {
                sb.append('?').append(query);
            }
            // Fragment intentionally omitted
            return sb.toString();
        } catch (Exception e) {
            // Fallback: simple normalization
            String result = url;
            int hash = result.indexOf('#');
            if (hash >= 0) result = result.substring(0, hash);
            while (result.length() > 1 && result.endsWith("/")) {
                result = result.substring(0, result.length() - 1);
            }
            return result.toLowerCase();
        }
    }

    /**
     * Extracts the effective domain from a URL (host without "www." prefix).
     * Returns null if parsing fails.
     */
    public static String extractDomain(String url) {
        if (url == null) return null;
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) return null;
            host = host.toLowerCase();
            if (host.startsWith("www.")) host = host.substring(4);
            return host;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extracts the registrable domain (eTLD+1 approximation).
     * E.g. "news.yahoo.com" → "yahoo.com", "de.finance.yahoo.com" → "yahoo.com".
     * Simple heuristic: take the last two domain parts.
     * For country-code TLDs like .co.uk this is imperfect but sufficient for link classification.
     */
    public static String extractBaseDomain(String url) {
        String domain = extractDomain(url);
        if (domain == null) return null;
        String[] parts = domain.split("\\.");
        if (parts.length <= 2) return domain;
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    /**
     * Returns true if both URLs resolve to the same canonical form.
     */
    public static boolean isSameCanonical(String url1, String url2) {
        return canonicalize(url1).equals(canonicalize(url2));
    }
}
