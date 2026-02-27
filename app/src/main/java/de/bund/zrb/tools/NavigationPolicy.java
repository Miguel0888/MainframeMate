package de.bund.zrb.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.bund.zrb.helper.SettingsHelper;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Manages navigation whitelist/blacklist for external URL access.
 * All checks are domain-based (never path-based).
 * <p>
 * Entries are domains (e.g. "www.bundestag.de") or wildcard patterns
 * (e.g. "*.wikipedia.org" which matches de.wikipedia.org, en.wikipedia.org, etc.).
 * <p>
 * Persisted in ~/.mainframemate/navigation-policy.json.
 */
public class NavigationPolicy {

    private static final File POLICY_FILE = new File(SettingsHelper.getSettingsFolder(), "navigation-policy.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<PolicyData>() {}.getType();

    /** In-memory session-level grants/blocks (cleared on session reset). */
    private final List<String> sessionAllowed = new ArrayList<String>();
    private final List<String> sessionBlocked = new ArrayList<String>();

    private static volatile NavigationPolicy instance;

    private NavigationPolicy() {}

    public static synchronized NavigationPolicy getInstance() {
        if (instance == null) {
            instance = new NavigationPolicy();
        }
        return instance;
    }

    // ── Session-level ──

    /**
     * Allow a domain (or wildcard pattern like "*.example.com") for this session.
     */
    public void allowForSession(String domainOrPattern) {
        String d = normalize(domainOrPattern);
        sessionBlocked.remove(d);
        if (!sessionAllowed.contains(d)) sessionAllowed.add(d);
    }

    public void blockForSession(String domainOrPattern) {
        String d = normalize(domainOrPattern);
        sessionAllowed.remove(d);
        if (!sessionBlocked.contains(d)) sessionBlocked.add(d);
    }

    public void clearSession() {
        sessionAllowed.clear();
        sessionBlocked.clear();
    }

    // ── Persistent whitelist/blacklist ──

    public void addToWhitelist(String domainOrPattern) {
        PolicyData data = loadData();
        String d = normalize(domainOrPattern);
        if (!data.whitelist.contains(d)) data.whitelist.add(d);
        data.blacklist.remove(d);
        saveData(data);
    }

    public void addToBlacklist(String domainOrPattern) {
        PolicyData data = loadData();
        String d = normalize(domainOrPattern);
        if (!data.blacklist.contains(d)) data.blacklist.add(d);
        data.whitelist.remove(d);
        saveData(data);
    }

    public List<String> getWhitelist() {
        return Collections.unmodifiableList(loadData().whitelist);
    }

    public List<String> getBlacklist() {
        return Collections.unmodifiableList(loadData().blacklist);
    }

    // ── Check ──

    /**
     * Check a DOMAIN (not URL!) against the policy.
     * Use {@link #extractDomain(String)} first to get the domain from a URL.
     *
     * @param domain the domain to check (e.g. "www.bundestag.de")
     * @return ALLOWED, BLOCKED, or ASK
     */
    public Decision checkDomain(String domain) {
        if (domain == null || domain.isEmpty()) return Decision.ASK;

        String normalized = normalize(domain);

        // 1. Session overrides first
        if (matchesList(normalized, sessionBlocked)) return Decision.BLOCKED;
        if (matchesList(normalized, sessionAllowed)) return Decision.ALLOWED;

        // 2. Persistent blacklist (takes priority)
        PolicyData data = loadData();
        if (matchesList(normalized, data.blacklist)) return Decision.BLOCKED;

        // 3. Persistent whitelist
        if (matchesList(normalized, data.whitelist)) return Decision.ALLOWED;

        // 4. Not decided
        return Decision.ASK;
    }

    /**
     * Matches a domain against a list of patterns.
     * Supports exact match and wildcard "*.example.com" which matches
     * "sub.example.com", "de.sub.example.com", and "example.com" itself.
     */
    private boolean matchesList(String domain, List<String> patterns) {
        for (String pattern : patterns) {
            if (pattern.equals(domain)) return true;
            if (pattern.startsWith("*.")) {
                String baseDomain = pattern.substring(2); // "example.com"
                // matches "example.com" itself and any subdomain
                if (domain.equals(baseDomain) || domain.endsWith("." + baseDomain)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ── Domain extraction ──

    /**
     * Extract the domain (host) from a URL. Returns null for relative URLs
     * or unparseable input.
     */
    public static String extractDomain(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        String u = url.trim();
        // Relative URL → no domain
        if (u.startsWith("/") || u.startsWith("#") || u.startsWith("?")) return null;
        if (!u.contains("://")) u = "https://" + u;
        try {
            java.net.URI uri = new java.net.URI(u);
            String host = uri.getHost();
            return host != null ? host.toLowerCase() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Derive the parent domain suitable for a "including subdomains" wildcard.
     * Computes the registrable domain (eTLD+1) and prefixes with "*.".
     * <p>
     * Examples:
     * <ul>
     *   <li>"de.finance.yahoo.com" → "*.yahoo.com"</li>
     *   <li>"www.bundestag.de"     → "*.bundestag.de"</li>
     *   <li>"de.wikipedia.org"     → "*.wikipedia.org"</li>
     *   <li>"news.bbc.co.uk"       → "*.bbc.co.uk"</li>
     *   <li>"bundestag.de"         → "*.bundestag.de"</li>
     * </ul>
     */
    public static String toWildcard(String domain) {
        if (domain == null) return null;
        String d = normalize(domain);
        String base = registrableDomain(d);
        return "*." + base;
    }

    /**
     * Heuristic to find the registrable domain (eTLD+1).
     * Handles common compound TLDs like co.uk, com.au, co.jp, etc.
     */
    private static String registrableDomain(String domain) {
        // Known compound TLDs (add more as needed)
        String[] compoundTlds = {
                "co.uk", "org.uk", "ac.uk", "gov.uk",
                "com.au", "net.au", "org.au",
                "co.jp", "or.jp", "ne.jp",
                "co.nz", "net.nz", "org.nz",
                "co.za", "org.za",
                "com.br", "org.br", "net.br",
                "co.in", "net.in", "org.in",
                "co.kr", "or.kr",
                "com.mx", "org.mx",
                "com.cn", "net.cn", "org.cn"
        };

        // Check if domain ends with a compound TLD
        for (String ctld : compoundTlds) {
            if (domain.endsWith("." + ctld)) {
                // Find one more label before the compound TLD
                String prefix = domain.substring(0, domain.length() - ctld.length() - 1);
                int lastDot = prefix.lastIndexOf('.');
                if (lastDot >= 0) {
                    return prefix.substring(lastDot + 1) + "." + ctld;
                }
                return prefix + "." + ctld; // already the registrable domain
            }
        }

        // Standard TLD: take last two labels
        String[] parts = domain.split("\\.");
        if (parts.length <= 2) {
            return domain; // already "example.com" or single label
        }
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    private static String normalize(String s) {
        return s != null ? s.toLowerCase().trim() : "";
    }

    // ── Persistence ──

    private PolicyData loadData() {
        if (!POLICY_FILE.exists()) return new PolicyData();
        try (Reader reader = new InputStreamReader(new FileInputStream(POLICY_FILE), StandardCharsets.UTF_8)) {
            PolicyData data = GSON.fromJson(reader, DATA_TYPE);
            return data != null ? data : new PolicyData();
        } catch (Exception e) {
            return new PolicyData();
        }
    }

    private void saveData(PolicyData data) {
        try {
            POLICY_FILE.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(POLICY_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Inner types ──

    public enum Decision {
        ALLOWED, BLOCKED, ASK
    }

    static class PolicyData {
        List<String> whitelist = new ArrayList<String>();
        List<String> blacklist = new ArrayList<String>();
    }
}
