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
 * Persisted in ~/.mainframemate/navigation-policy.json.
 * <p>
 * Entries are domain-based (e.g. "www.bundestag.de", "*.wikipedia.org").
 */
public class NavigationPolicy {

    private static final File POLICY_FILE = new File(SettingsHelper.getSettingsFolder(), "navigation-policy.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<PolicyData>() {}.getType();

    /** In-memory session-level grants/blocks (cleared on session reset). */
    private final Set<String> sessionAllowed = new LinkedHashSet<String>();
    private final Set<String> sessionBlocked = new LinkedHashSet<String>();

    private static volatile NavigationPolicy instance;

    private NavigationPolicy() {}

    public static synchronized NavigationPolicy getInstance() {
        if (instance == null) {
            instance = new NavigationPolicy();
        }
        return instance;
    }

    // ── Session-level ──

    public void allowForSession(String domain) {
        sessionAllowed.add(normalizeDomain(domain));
        sessionBlocked.remove(normalizeDomain(domain));
    }

    public void blockForSession(String domain) {
        sessionBlocked.add(normalizeDomain(domain));
        sessionAllowed.remove(normalizeDomain(domain));
    }

    public void clearSession() {
        sessionAllowed.clear();
        sessionBlocked.clear();
    }

    // ── Persistent whitelist/blacklist ──

    public void addToWhitelist(String domain) {
        PolicyData data = loadData();
        String d = normalizeDomain(domain);
        if (!data.whitelist.contains(d)) data.whitelist.add(d);
        data.blacklist.remove(d);
        saveData(data);
    }

    public void addToBlacklist(String domain) {
        PolicyData data = loadData();
        String d = normalizeDomain(domain);
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

    /**
     * Check a URL against the policy.
     * @return ALLOWED, BLOCKED, or ASK (needs user decision)
     */
    public Decision check(String url) {
        String domain = extractDomain(url);
        if (domain == null || domain.isEmpty()) return Decision.ASK;

        String normalized = normalizeDomain(domain);

        // 1. Session overrides first
        if (sessionBlocked.contains(normalized)) return Decision.BLOCKED;
        if (sessionAllowed.contains(normalized)) return Decision.ALLOWED;

        // 2. Persistent blacklist (takes priority)
        PolicyData data = loadData();
        if (matchesList(normalized, data.blacklist)) return Decision.BLOCKED;

        // 3. Persistent whitelist
        if (matchesList(normalized, data.whitelist)) return Decision.ALLOWED;

        // 4. Not decided
        return Decision.ASK;
    }

    private boolean matchesList(String domain, List<String> list) {
        for (String pattern : list) {
            if (pattern.equals(domain)) return true;
            if (pattern.startsWith("*.")) {
                String suffix = pattern.substring(1); // ".example.com"
                if (domain.endsWith(suffix) || domain.equals(pattern.substring(2))) return true;
            }
        }
        return false;
    }

    public static String extractDomain(String url) {
        if (url == null) return null;
        try {
            String u = url.trim();
            if (!u.contains("://")) u = "https://" + u;
            java.net.URI uri = new java.net.URI(u);
            String host = uri.getHost();
            return host != null ? host.toLowerCase() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeDomain(String domain) {
        return domain != null ? domain.toLowerCase().trim() : "";
    }

    // ── Persistence ──

    private PolicyData loadData() {
        if (!POLICY_FILE.exists()) return new PolicyData();
        try (Reader reader = new InputStreamReader(new FileInputStream(POLICY_FILE), StandardCharsets.UTF_8)) {
            PolicyData data = GSON.fromJson(reader, LIST_TYPE);
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
