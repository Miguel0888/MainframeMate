package de.bund.zrb.mcp.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.helper.SettingsHelper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * HTTP client for the MCP Registry API.
 * Fetches server lists and server details, with file-based caching.
 */
public class McpRegistryApiClient {

    private static final File CACHE_DIR = new File(SettingsHelper.getSettingsFolder(), "mcp-registry-cache");

    private final McpRegistrySettings settings;

    public McpRegistryApiClient(McpRegistrySettings settings) {
        this.settings = settings;
    }

    // ── Complete server catalogue ───────────────────────────────────

    /**
     * Load ALL servers from the registry (follows pagination to completion).
     * Results are deduplicated by name and sorted by priority.
     *
     * @param progressCallback optional callback invoked on each page with (loadedSoFar, pageNumber)
     */
    public List<McpRegistryServerInfo> loadAllServers(ProgressCallback progressCallback) throws IOException {
        LinkedHashMap<String, McpRegistryServerInfo> dedup = new LinkedHashMap<>();
        String cursor = null;
        int pageCount = 0;
        int maxPages = 100;

        do {
            StringBuilder url = new StringBuilder(settings.getRegistryBaseUrl());
            url.append("/v0.1/servers?limit=100");
            if (cursor != null) {
                url.append("&cursor=").append(URLEncoder.encode(cursor, "UTF-8"));
            }

            String json = fetchWithCache(url.toString(),
                    "page_" + pageCount + "_" + (cursor != null ? Math.abs(cursor.hashCode()) : "first"));

            JsonElement parsed = JsonParser.parseString(json);
            cursor = null;

            if (parsed.isJsonObject()) {
                JsonObject root = parsed.getAsJsonObject();
                if (root.has("servers") && root.get("servers").isJsonArray()) {
                    for (JsonElement el : root.getAsJsonArray("servers")) {
                        if (el.isJsonObject()) {
                            McpRegistryServerInfo info = McpRegistryServerInfo.fromListEntry(el.getAsJsonObject());
                            if (info.getName() != null && !dedup.containsKey(info.getName())) {
                                dedup.put(info.getName(), info);
                            }
                        }
                    }
                }
                if (root.has("metadata") && root.get("metadata").isJsonObject()) {
                    JsonObject meta = root.getAsJsonObject("metadata");
                    JsonElement nc = meta.has("next_cursor") ? meta.get("next_cursor")
                            : meta.has("nextCursor") ? meta.get("nextCursor") : null;
                    if (nc != null && !nc.isJsonNull()) {
                        String val = nc.getAsString();
                        cursor = val.isEmpty() ? null : val;
                    }
                }
            }
            pageCount++;
            System.err.println("[McpRegistryApi] Page " + pageCount + ": " + dedup.size() + " unique servers");
            if (progressCallback != null) {
                progressCallback.onProgress(dedup.size(), pageCount);
            }
        } while (cursor != null && pageCount < maxPages);

        List<McpRegistryServerInfo> all = new ArrayList<>(dedup.values());
        sortByPriority(all);
        System.err.println("[McpRegistryApi] Total: " + all.size() + " unique servers in " + pageCount + " pages");
        return all;
    }

    public interface ProgressCallback {
        void onProgress(int totalLoaded, int pageNumber);
    }

    // ── Server details ──────────────────────────────────────────────

    public McpRegistryServerInfo getServerDetails(String serverName) throws IOException {
        String encoded = URLEncoder.encode(serverName, "UTF-8").replace("+", "%20");
        String url = settings.getRegistryBaseUrl() + "/v0.1/servers/" + encoded + "/versions/latest";
        String cacheKey = "detail_" + Math.abs(url.hashCode());
        String json = fetchWithCache(url, cacheKey);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return McpRegistryServerInfo.fromDetail(root);
    }

    // ── Client-side search ──────────────────────────────────────────

    /**
     * Smart client-side search over a pre-loaded server list.
     * Matches against the short name (after '/'), description, and publisher.
     * Results are sorted: exact name matches first, then by priority.
     */
    public static List<McpRegistryServerInfo> filterServers(List<McpRegistryServerInfo> all, String query) {
        if (query == null || query.trim().isEmpty()) return all;

        String q = query.trim().toLowerCase();
        String[] terms = q.split("\\s+");

        List<McpRegistryServerInfo> results = new ArrayList<>();
        for (McpRegistryServerInfo s : all) {
            if (matches(s, terms)) results.add(s);
        }

        // Sort: exact short-name match first, then by priority
        Collections.sort(results, (a, b) -> {
            int sa = matchScore(a, terms);
            int sb = matchScore(b, terms);
            if (sa != sb) return Integer.compare(sb, sa); // higher score first
            int pa = a.getSortPriority();
            int pb = b.getSortPriority();
            if (pa != pb) return Integer.compare(pa, pb);
            return String.CASE_INSENSITIVE_ORDER.compare(
                    a.getName() != null ? a.getName() : "",
                    b.getName() != null ? b.getName() : "");
        });
        return results;
    }

    private static boolean matches(McpRegistryServerInfo s, String[] terms) {
        String searchable = buildSearchString(s);
        for (String term : terms) {
            if (!searchable.contains(term)) return false;
        }
        return true;
    }

    private static int matchScore(McpRegistryServerInfo s, String[] terms) {
        int score = 0;
        String shortName = getShortName(s.getName()).toLowerCase();
        String publisher = getPublisher(s.getName()).toLowerCase();

        for (String term : terms) {
            if (shortName.equals(term)) score += 100;
            else if (shortName.contains(term)) score += 50;
            if (publisher.equals(term)) score += 30;
            else if (publisher.contains(term)) score += 10;
        }
        score += (3 - Math.min(s.getSortPriority(), 3)) * 5;
        return score;
    }

    private static String buildSearchString(McpRegistryServerInfo s) {
        StringBuilder sb = new StringBuilder();
        if (s.getName() != null) sb.append(s.getName().toLowerCase()).append(' ');
        sb.append(getShortName(s.getName()).toLowerCase()).append(' ');
        if (s.getDescription() != null) sb.append(s.getDescription().toLowerCase()).append(' ');
        if (s.getRepositoryUrl() != null) {
            String repo = s.getRepositoryUrl().toLowerCase();
            int ghIdx = repo.indexOf("github.com/");
            if (ghIdx >= 0) sb.append(repo.substring(ghIdx + 11)).append(' ');
        }
        return sb.toString();
    }

    /** Extract short name: "io.github.modelcontextprotocol/fetch" -> "fetch" */
    public static String getShortName(String name) {
        if (name == null) return "";
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    /** Extract publisher: "io.github.modelcontextprotocol/fetch" -> "modelcontextprotocol" */
    public static String getPublisher(String name) {
        if (name == null) return "";
        int slash = name.indexOf('/');
        String ns = slash >= 0 ? name.substring(0, slash) : name;
        int lastDot = ns.lastIndexOf('.');
        return lastDot >= 0 ? ns.substring(lastDot + 1) : ns;
    }

    // ── Cache management ────────────────────────────────────────────

    public void invalidateCache() {
        if (CACHE_DIR.exists()) {
            File[] files = CACHE_DIR.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
        }
    }

    // ── Internal ────────────────────────────────────────────────────

    private String fetchWithCache(String urlStr, String cacheKey) throws IOException {
        CACHE_DIR.mkdirs();
        File cacheFile = new File(CACHE_DIR, cacheKey + ".json");

        if (cacheFile.exists()) {
            long age = System.currentTimeMillis() - cacheFile.lastModified();
            if (age < settings.getCacheTtlMs()) return readFile(cacheFile);
        }

        try {
            String json = httpGet(urlStr);
            try (Writer w = new OutputStreamWriter(new FileOutputStream(cacheFile), StandardCharsets.UTF_8)) {
                w.write(json);
            }
            return json;
        } catch (IOException e) {
            if (cacheFile.exists()) {
                System.err.println("[McpRegistryApi] Network error, using stale cache: " + e.getMessage());
                return readFile(cacheFile);
            }
            throw e;
        }
    }

    private static String httpGet(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);

        int status = conn.getResponseCode();
        if (status != 200) throw new IOException("HTTP " + status + " from " + urlStr);

        try (InputStream is = conn.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private static String readFile(File f) throws IOException {
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
            return sb.toString();
        }
    }

    private static void sortByPriority(List<McpRegistryServerInfo> list) {
        Collections.sort(list, (a, b) -> {
            int pa = a.getSortPriority();
            int pb = b.getSortPriority();
            if (pa != pb) return Integer.compare(pa, pb);
            return String.CASE_INSENSITIVE_ORDER.compare(
                    a.getName() != null ? a.getName() : "",
                    b.getName() != null ? b.getName() : "");
        });
    }
}
