package de.bund.zrb.archive.service;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;
import java.util.logging.Logger;

/**
 * Normalizes URLs to produce stable canonical forms.
 * Removes volatile parameters (tracking, session, cache-busting),
 * sorts query parameters, removes fragments, lowercases scheme+host.
 */
public class UrlNormalizer {

    private static final Logger LOG = Logger.getLogger(UrlNormalizer.class.getName());

    /** Parameters that are removed during normalization (tracking, session, cache). */
    private static final Set<String> VOLATILE_PARAMS = new HashSet<String>(Arrays.asList(
            // Tracking / Analytics
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "utm_id", "utm_source_platform", "utm_creative_format", "utm_marketing_tactic",
            "fbclid", "gclid", "gclsrc", "dclid", "msclkid", "twclid",
            "mc_cid", "mc_eid",
            "yclid", "wickedid", "irclickid",
            "li_fat_id", "li_lean_id",
            // Session / Cache
            "jsessionid", "phpsessid", "sessionid", "sid", "session_id",
            "aspsessionid", "cfid", "cftoken",
            "cachebust", "cache_bust", "cb", "_", "__",
            "t", "ts", "timestamp", "_t", "_ts",
            "rand", "random", "rnd", "nonce",
            "crumb", "token",
            // Yahoo-specific
            "soc_src", "soc_trk", ".tsrc", "guccounter", "guce_referrer",
            "guce_referrer_sig", "ncid"
    ));

    /**
     * Compute canonical URL from a raw URL.
     * Returns the normalized form, or the original URL if parsing fails.
     */
    public static String canonicalize(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return rawUrl;
        try {
            URI uri = new URI(rawUrl.trim());

            // Lowercase scheme + host
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "http";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
            int port = uri.getPort();
            String path = uri.getRawPath();

            // Normalize path
            if (path == null || path.isEmpty()) path = "/";
            // Remove trailing slash (except root)
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            // Remove default ports
            if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) {
                port = -1;
            }

            // Parse and filter query parameters
            String query = uri.getRawQuery();
            String filteredQuery = filterAndSortQuery(query);

            // Rebuild URL (no fragment)
            StringBuilder sb = new StringBuilder();
            sb.append(scheme).append("://").append(host);
            if (port > 0) sb.append(":").append(port);
            sb.append(path);
            if (filteredQuery != null && !filteredQuery.isEmpty()) {
                sb.append("?").append(filteredQuery);
            }

            return sb.toString();
        } catch (Exception e) {
            LOG.fine("[UrlNormalizer] Failed to canonicalize: " + rawUrl + " – " + e.getMessage());
            return rawUrl;
        }
    }

    /**
     * Filter out volatile parameters and sort remaining alphabetically.
     */
    private static String filterAndSortQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) return null;

        // Parse key=value pairs
        TreeMap<String, String> sorted = new TreeMap<String, String>();
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            String key, value;
            if (eq >= 0) {
                key = pair.substring(0, eq);
                value = pair.substring(eq + 1);
            } else {
                key = pair;
                value = "";
            }

            // Skip volatile parameters
            if (VOLATILE_PARAMS.contains(key.toLowerCase())) continue;
            // Skip empty keys
            if (key.trim().isEmpty()) continue;

            sorted.put(key, value);
        }

        if (sorted.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!first) sb.append("&");
            sb.append(entry.getKey());
            if (!entry.getValue().isEmpty()) {
                sb.append("=").append(entry.getValue());
            }
            first = false;
        }
        return sb.toString();
    }

    /**
     * Extract host from URL (lowercase).
     */
    public static String extractHost(String url) {
        if (url == null) return "unknown";
        try {
            URI uri = new URI(url);
            return uri.getHost() != null ? uri.getHost().toLowerCase() : "unknown";
        } catch (Exception e) {
            // Fallback: manual extraction
            String s = url;
            if (s.startsWith("https://")) s = s.substring(8);
            else if (s.startsWith("http://")) s = s.substring(7);
            int slash = s.indexOf('/');
            return slash > 0 ? s.substring(0, slash).toLowerCase() : s.toLowerCase();
        }
    }
}
