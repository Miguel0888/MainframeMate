package de.bund.zrb.security;


import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central service for managing per-connection-type whitelist/blacklist for
 * caching and indexing. Persists rules to {@code ~/.mainframemate/security-filter.json}.
 *
 * <h3>Concepts:</h3>
 * <ul>
 *   <li><b>Connection Group</b> — one of LOCAL, FTP, NDV, WIKI, MAIL, BETAVIEW, etc.</li>
 *   <li><b>Blacklist All</b> — if true, only whitelisted paths are cached/indexed.
 *       Default: true for LOCAL and FTP, false for others.</li>
 *   <li><b>Whitelist</b> — paths (files or folders) that are always allowed.</li>
 *   <li><b>Blacklist</b> — paths that are never cached/indexed.
 *       Blacklist wins over whitelist (file-level blacklist overrides folder-level whitelist).</li>
 * </ul>
 *
 * <h3>Path Format:</h3>
 * Paths use the bookmark prefix convention (e.g. {@code ftp://host/dir/file},
 * {@code ndv://LIBRARY/OBJECT}, {@code local://C:/path/to/file}).
 * Folder paths end with {@code /} (or not — the check is prefix-based).
 */
public class SecurityFilterService {

    private static final Logger LOG = Logger.getLogger(SecurityFilterService.class.getName());

    private static final File FILTER_FILE = new File(
            System.getProperty("user.home"), ".mainframemate/security-filter.json");

    /** Connection groups that have "Blacklist All" enabled by default. */
    private static final Set<String> DEFAULT_BLACKLIST_ALL_GROUPS = new HashSet<String>(
            Arrays.asList("LOCAL", "FTP"));

    /** All known connection groups (order matters for UI). */
    public static final String[] ALL_GROUPS = {
            "LOCAL", "FTP", "NDV", "WIKI", "MAIL", "BETAVIEW", "SHAREPOINT"
    };

    /** German display names for groups. */
    public static final Map<String, String> GROUP_LABELS;
    static {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("LOCAL", "Lokal");
        m.put("FTP", "FTP");
        m.put("NDV", "NDV (Natural)");
        m.put("WIKI", "Wiki");
        m.put("MAIL", "Mail");
        m.put("BETAVIEW", "BetaView");
        m.put("SHAREPOINT", "SharePoint");
        GROUP_LABELS = Collections.unmodifiableMap(m);
    }

    // ── Per-group state ──
    private final Map<String, Boolean> blacklistAll = new ConcurrentHashMap<String, Boolean>();
    private final Map<String, Set<String>> whitelists = new ConcurrentHashMap<String, Set<String>>();
    private final Map<String, Set<String>> blacklists = new ConcurrentHashMap<String, Set<String>>();

    // ── Listeners ──
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<Runnable>();

    private static volatile SecurityFilterService instance;

    public static synchronized SecurityFilterService getInstance() {
        if (instance == null) {
            instance = new SecurityFilterService();
        }
        return instance;
    }

    private SecurityFilterService() {
        // Initialize defaults
        for (String group : ALL_GROUPS) {
            blacklistAll.put(group, DEFAULT_BLACKLIST_ALL_GROUPS.contains(group));
            whitelists.put(group, Collections.synchronizedSet(new LinkedHashSet<String>()));
            blacklists.put(group, Collections.synchronizedSet(new LinkedHashSet<String>()));
        }
        load();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Public Query API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if a given path is allowed for caching/indexing.
     *
     * @param group connection group (e.g. "FTP", "NDV", "LOCAL")
     * @param path  full prefixed path (e.g. "ftp://host/dir/file.jcl")
     * @return true if the path is allowed (not blocked)
     */
    public boolean isAllowed(String group, String path) {
        if (group == null || path == null) return true;
        String g = group.toUpperCase();

        Set<String> bl = blacklists.get(g);
        if (bl != null && matchesAny(bl, path)) {
            return false; // Blacklist always wins
        }

        Boolean blAll = blacklistAll.get(g);
        if (blAll != null && blAll) {
            // Blacklist-all mode: only whitelisted paths pass
            Set<String> wl = whitelists.get(g);
            return wl != null && matchesAny(wl, path);
        }

        return true; // Not blacklisted, and not in blacklist-all mode
    }

    /**
     * Check if a path is explicitly blacklisted (regardless of blacklistAll mode).
     */
    public boolean isBlacklisted(String group, String path) {
        if (group == null || path == null) return false;
        Set<String> bl = blacklists.get(group.toUpperCase());
        return bl != null && matchesAny(bl, path);
    }

    /**
     * Check if a path is explicitly whitelisted.
     */
    public boolean isWhitelisted(String group, String path) {
        if (group == null || path == null) return false;
        Set<String> wl = whitelists.get(group.toUpperCase());
        return wl != null && matchesAny(wl, path);
    }

    /**
     * Check if "Blacklist All" is enabled for the given group.
     */
    public boolean isBlacklistAll(String group) {
        if (group == null) return false;
        Boolean val = blacklistAll.get(group.toUpperCase());
        return val != null && val;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Mutation API
    // ═══════════════════════════════════════════════════════════════

    public void setBlacklistAll(String group, boolean enabled) {
        blacklistAll.put(group.toUpperCase(), enabled);
        save();
        fireChange();
    }

    public void addToWhitelist(String group, String path) {
        ensureGroup(group);
        whitelists.get(group.toUpperCase()).add(path);
        // Remove from blacklist if present (mutually exclusive at same level)
        blacklists.get(group.toUpperCase()).remove(path);
        save();
        fireChange();
    }

    public void addToBlacklist(String group, String path) {
        ensureGroup(group);
        blacklists.get(group.toUpperCase()).add(path);
        // Remove from whitelist if present
        whitelists.get(group.toUpperCase()).remove(path);
        save();
        fireChange();
    }

    public void removeFromWhitelist(String group, String path) {
        Set<String> wl = whitelists.get(group.toUpperCase());
        if (wl != null) {
            wl.remove(path);
            save();
            fireChange();
        }
    }

    public void removeFromBlacklist(String group, String path) {
        Set<String> bl = blacklists.get(group.toUpperCase());
        if (bl != null) {
            bl.remove(path);
            save();
            fireChange();
        }
    }

    /**
     * Bulk-set all filter rules for a group (used by settings panel).
     */
    public void setGroupRules(String group, boolean blAll,
                              Collection<String> whitelist,
                              Collection<String> blacklist) {
        String g = group.toUpperCase();
        ensureGroup(g);
        blacklistAll.put(g, blAll);
        whitelists.get(g).clear();
        whitelists.get(g).addAll(whitelist);
        blacklists.get(g).clear();
        blacklists.get(g).addAll(blacklist);
        save();
        fireChange();
    }

    /** Get whitelist entries for a group (read-only copy). */
    public List<String> getWhitelist(String group) {
        Set<String> wl = whitelists.get(group.toUpperCase());
        return wl != null ? new ArrayList<String>(wl) : Collections.<String>emptyList();
    }

    /** Get blacklist entries for a group (read-only copy). */
    public List<String> getBlacklist(String group) {
        Set<String> bl = blacklists.get(group.toUpperCase());
        return bl != null ? new ArrayList<String>(bl) : Collections.<String>emptyList();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Listener API
    // ═══════════════════════════════════════════════════════════════

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    private void fireChange() {
        for (Runnable r : changeListeners) {
            try {
                r.run();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "SecurityFilter listener error", e);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Path Matching
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if path matches any rule entry.
     * A rule entry matches if the path equals it or starts with it (folder prefix match).
     */
    private static boolean matchesAny(Set<String> rules, String path) {
        String pathNorm = stripTrailingSlash(path);
        for (String rule : rules) {
            String ruleNorm = stripTrailingSlash(rule);
            // Exact match (ignoring trailing slash differences)
            if (ruleNorm.equals(pathNorm)) return true;
            // Folder prefix match: rule "ftp://host/dir" matches "ftp://host/dir/file.txt"
            if (pathNorm.startsWith(ruleNorm + "/")) return true;
        }
        return false;
    }

    private static String stripTrailingSlash(String s) {
        return (s != null && s.endsWith("/")) ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * Check if any blacklist entry is a descendant of the given path prefix.
     * E.g. if pathPrefix is "ftp://host/DIR" and the blacklist contains
     * "ftp://host/DIR/FILE.JCL", this returns true.
     */
    public boolean hasBlacklistedDescendants(String group, String pathPrefix) {
        if (group == null || pathPrefix == null) return false;
        Set<String> bl = blacklists.get(group.toUpperCase());
        if (bl == null || bl.isEmpty()) return false;
        String prefix = pathPrefix.endsWith("/") ? pathPrefix : pathPrefix + "/";
        for (String rule : bl) {
            if (rule.startsWith(prefix) || rule.equals(pathPrefix)) return true;
        }
        return false;
    }

    /**
     * Count how many blacklist entries are descendants of the given path prefix.
     */
    public int countBlacklistedDescendants(String group, String pathPrefix) {
        if (group == null || pathPrefix == null) return 0;
        Set<String> bl = blacklists.get(group.toUpperCase());
        if (bl == null || bl.isEmpty()) return 0;
        String prefix = pathPrefix.endsWith("/") ? pathPrefix : pathPrefix + "/";
        int count = 0;
        for (String rule : bl) {
            if (rule.startsWith(prefix) || rule.equals(pathPrefix)) count++;
        }
        return count;
    }

    /**
     * Check if any whitelist entry is a descendant of the given path prefix.
     */
    public boolean hasWhitelistedDescendants(String group, String pathPrefix) {
        if (group == null || pathPrefix == null) return false;
        Set<String> wl = whitelists.get(group.toUpperCase());
        if (wl == null || wl.isEmpty()) return false;
        String prefix = pathPrefix.endsWith("/") ? pathPrefix : pathPrefix + "/";
        for (String rule : wl) {
            if (rule.startsWith(prefix) || rule.equals(pathPrefix)) return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Persistence
    // ═══════════════════════════════════════════════════════════════

    private void ensureGroup(String group) {
        String g = group.toUpperCase();
        if (!blacklistAll.containsKey(g)) {
            blacklistAll.put(g, DEFAULT_BLACKLIST_ALL_GROUPS.contains(g));
        }
        if (!whitelists.containsKey(g)) {
            whitelists.put(g, Collections.synchronizedSet(new LinkedHashSet<String>()));
        }
        if (!blacklists.containsKey(g)) {
            blacklists.put(g, Collections.synchronizedSet(new LinkedHashSet<String>()));
        }
    }

    private void load() {
        if (!FILTER_FILE.exists()) return;
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.io.Reader reader = new java.io.InputStreamReader(
                    new java.io.FileInputStream(FILTER_FILE), "UTF-8");
            try {
                FilterData data = gson.fromJson(reader, FilterData.class);
                if (data != null && data.groups != null) {
                    for (Map.Entry<String, GroupData> entry : data.groups.entrySet()) {
                        String g = entry.getKey().toUpperCase();
                        GroupData gd = entry.getValue();
                        ensureGroup(g);
                        blacklistAll.put(g, gd.blacklistAll);
                        if (gd.whitelist != null) {
                            whitelists.get(g).addAll(gd.whitelist);
                        }
                        if (gd.blacklist != null) {
                            blacklists.get(g).addAll(gd.blacklist);
                        }
                    }
                }
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load security-filter.json", e);
        }
    }

    private synchronized void save() {
        try {
            FILTER_FILE.getParentFile().mkdirs();
            FilterData data = new FilterData();
            data.groups = new LinkedHashMap<String, GroupData>();
            for (String g : ALL_GROUPS) {
                GroupData gd = new GroupData();
                Boolean blAll = blacklistAll.get(g);
                gd.blacklistAll = blAll != null && blAll;
                Set<String> wl = whitelists.get(g);
                gd.whitelist = wl != null ? new ArrayList<String>(wl) : new ArrayList<String>();
                Set<String> bl = blacklists.get(g);
                gd.blacklist = bl != null ? new ArrayList<String>(bl) : new ArrayList<String>();
                data.groups.put(g, gd);
            }
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            java.io.Writer writer = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(FILTER_FILE), "UTF-8");
            try {
                gson.toJson(data, writer);
            } finally {
                writer.close();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to save security-filter.json", e);
        }
    }

    // ── JSON model ──

    private static class FilterData {
        Map<String, GroupData> groups;
    }

    private static class GroupData {
        boolean blacklistAll;
        List<String> whitelist;
        List<String> blacklist;
    }
}

