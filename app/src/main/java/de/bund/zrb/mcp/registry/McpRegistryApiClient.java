package de.bund.zrb.mcp.registry;

import com.google.gson.JsonArray;
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
import java.util.LinkedHashMap;

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

    // ── Server list ─────────────────────────────────────────────────

    /**
     * Search/list servers from the registry.
     *
     * @param search  search query (may be null/empty)
     * @param cursor  pagination cursor (may be null for first page)
     * @param limit   max results per page
     * @return result containing servers and next cursor
     */
    public ListResult listServers(String search, String cursor, int limit) throws IOException {
        StringBuilder url = new StringBuilder(settings.getRegistryBaseUrl());
        url.append("/v0.1/servers?limit=").append(limit);
        if (search != null && !search.trim().isEmpty()) {
            url.append("&search=").append(URLEncoder.encode(search.trim(), "UTF-8"));
        }
        if (cursor != null && !cursor.isEmpty()) {
            url.append("&cursor=").append(URLEncoder.encode(cursor, "UTF-8"));
        }

        String cacheKey = "list_" + Math.abs(url.toString().hashCode());
        String json = fetchWithCache(url.toString(), cacheKey);

        System.err.println("[McpRegistryApi] Response preview: "
                + json.substring(0, Math.min(json.length(), 500)));

        JsonElement parsed = JsonParser.parseString(json);
        // Use LinkedHashMap to deduplicate by name, keeping first (newest) entry
        LinkedHashMap<String, McpRegistryServerInfo> dedup = new LinkedHashMap<>();
        String nextCursor = null;

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

            // Pagination cursor: try both snake_case and camelCase
            if (root.has("metadata") && root.get("metadata").isJsonObject()) {
                JsonObject meta = root.getAsJsonObject("metadata");
                JsonElement nc = meta.has("next_cursor") ? meta.get("next_cursor")
                        : meta.has("nextCursor") ? meta.get("nextCursor") : null;
                if (nc != null && !nc.isJsonNull()) nextCursor = nc.getAsString();
            }
        } else if (parsed.isJsonArray()) {
            for (JsonElement el : parsed.getAsJsonArray()) {
                if (el.isJsonObject()) {
                    McpRegistryServerInfo info = McpRegistryServerInfo.fromListEntry(el.getAsJsonObject());
                    if (info.getName() != null && !dedup.containsKey(info.getName())) {
                        dedup.put(info.getName(), info);
                    }
                }
            }
        }

        List<McpRegistryServerInfo> servers = new ArrayList<>(dedup.values());
        // Sort: known/official publishers first, then by name
        Collections.sort(servers, (a, b) -> {
            int pa = a.getSortPriority();
            int pb = b.getSortPriority();
            if (pa != pb) return Integer.compare(pa, pb);
            return String.CASE_INSENSITIVE_ORDER.compare(
                    a.getName() != null ? a.getName() : "",
                    b.getName() != null ? b.getName() : "");
        });
        System.err.println("[McpRegistryApi] Parsed " + servers.size() + " unique servers, nextCursor=" + nextCursor);
        return new ListResult(servers, nextCursor);
    }

    // ── Server details ──────────────────────────────────────────────

    /**
     * Fetch details for a specific server (latest version).
     */
    public McpRegistryServerInfo getServerDetails(String serverName) throws IOException {
        // URL-encode each path segment separately (org/name → org%2Fname)
        String encoded = URLEncoder.encode(serverName, "UTF-8").replace("+", "%20");
        String url = settings.getRegistryBaseUrl() + "/v0.1/servers/" + encoded + "/versions/latest";
        String cacheKey = "detail_" + Math.abs(url.hashCode());
        String json = fetchWithCache(url, cacheKey);
        System.err.println("[McpRegistryApi] Detail response preview: "
                + json.substring(0, Math.min(json.length(), 500)));
        JsonObject root = parseJson(json);
        return McpRegistryServerInfo.fromDetail(root);
    }

    // ── Cache management ────────────────────────────────────────────

    public void invalidateCache() {
        if (CACHE_DIR.exists()) {
            File[] files = CACHE_DIR.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }
    }

    // ── Internal ────────────────────────────────────────────────────

    private String fetchWithCache(String urlStr, String cacheKey) throws IOException {
        CACHE_DIR.mkdirs();
        File cacheFile = new File(CACHE_DIR, cacheKey + ".json");

        // Check cache
        if (cacheFile.exists()) {
            long age = System.currentTimeMillis() - cacheFile.lastModified();
            if (age < settings.getCacheTtlMs()) {
                return readFile(cacheFile);
            }
        }

        // Fetch from network
        try {
            String json = httpGet(urlStr);
            // Write to cache
            try (Writer w = new OutputStreamWriter(new FileOutputStream(cacheFile), StandardCharsets.UTF_8)) {
                w.write(json);
            }
            return json;
        } catch (IOException e) {
            // On network error, try stale cache
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
        if (status != 200) {
            throw new IOException("HTTP " + status + " from " + urlStr);
        }

        try (InputStream is = conn.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static String readFile(File f) throws IOException {
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        }
    }

    private static JsonObject parseJson(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    // ── Result container ────────────────────────────────────────────

    public static class ListResult {
        public final List<McpRegistryServerInfo> servers;
        public final String nextCursor;

        ListResult(List<McpRegistryServerInfo> servers, String nextCursor) {
            this.servers = servers;
            this.nextCursor = nextCursor;
        }
    }
}

