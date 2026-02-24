package de.bund.zrb.archive.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Filters URLs based on include/exclude glob patterns.
 * A glob like "*.yahoo.com" is converted to a regex.
 */
public class DomainFilter {

    private final List<Pattern> includePatterns = new ArrayList<Pattern>();
    private final List<Pattern> excludePatterns = new ArrayList<Pattern>();

    public DomainFilter(List<String> includes, List<String> excludes) {
        if (includes != null) {
            for (String p : includes) {
                if (p != null && !p.trim().isEmpty()) {
                    includePatterns.add(globToRegex(p.trim()));
                }
            }
        }
        if (excludes != null) {
            for (String p : excludes) {
                if (p != null && !p.trim().isEmpty()) {
                    excludePatterns.add(globToRegex(p.trim()));
                }
            }
        }
    }

    /**
     * Returns true if the URL passes the filter.
     */
    public boolean accepts(String url) {
        if (url == null || url.trim().isEmpty()) return false;

        String normalized = normalizeUrl(url);

        // Exclude check first
        for (Pattern p : excludePatterns) {
            if (p.matcher(normalized).matches()) {
                return false;
            }
        }

        // If no include patterns → everything passes
        if (includePatterns.isEmpty()) {
            return true;
        }

        // At least one include pattern must match
        for (Pattern p : includePatterns) {
            if (p.matcher(normalized).matches()) {
                return true;
            }
        }
        return false;
    }

    private String normalizeUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost() != null ? uri.getHost() : "";
            String path = uri.getPath() != null ? uri.getPath() : "/";
            return host + path;
        } catch (Exception e) {
            // If URI parsing fails, just use the URL as-is minus scheme
            String s = url;
            if (s.startsWith("https://")) s = s.substring(8);
            else if (s.startsWith("http://")) s = s.substring(7);
            return s;
        }
    }

    /**
     * Converts a simple glob pattern to a regex.
     * Supports * (anything except /) and ** (anything including /).
     */
    static Pattern globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    sb.append(".*");
                    i++; // skip second *
                } else {
                    sb.append("[^/]*");
                }
            } else if (c == '?') {
                sb.append("[^/]");
            } else if (c == '.') {
                sb.append("\\.");
            } else {
                sb.append(c);
            }
        }
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }
}
