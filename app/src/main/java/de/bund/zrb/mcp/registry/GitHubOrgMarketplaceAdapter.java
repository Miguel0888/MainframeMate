package de.bund.zrb.mcp.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.helper.SettingsHelper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads MCP-related repositories from a GitHub organization
 * and presents them as {@link McpRegistryServerInfo} entries.
 * <p>
 * Uses the GitHub REST API: {@code GET /orgs/{org}/repos}
 * </p>
 */
public class GitHubOrgMarketplaceAdapter {

    private static final File CACHE_DIR = new File(SettingsHelper.getSettingsFolder(), "mcp-github-cache");
    private static final long CACHE_TTL = 3600_000L; // 1h

    private final String orgName;

    /**
     * @param orgUrl URL like "https://github.com/modelcontextprotocol" or just the org name
     */
    public GitHubOrgMarketplaceAdapter(String orgUrl) {
        // Extract org name from URL
        String s = orgUrl.trim();
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        int lastSlash = s.lastIndexOf('/');
        this.orgName = lastSlash >= 0 ? s.substring(lastSlash + 1) : s;
    }

    /**
     * Load all non-archived repos from the org, convert to McpRegistryServerInfo.
     */
    public List<McpRegistryServerInfo> loadServers(McpRegistryApiClient.ProgressCallback progress)
            throws IOException {
        List<McpRegistryServerInfo> result = new ArrayList<>();
        int page = 1;
        int maxPages = 20;

        while (page <= maxPages) {
            String url = "https://api.github.com/orgs/" + orgName
                    + "/repos?type=public&per_page=100&sort=stars&direction=desc&page=" + page;

            String json = fetchWithCache(url, "gh_" + orgName + "_page" + page);
            JsonElement parsed = JsonParser.parseString(json);

            if (!parsed.isJsonArray()) break;
            JsonArray arr = parsed.getAsJsonArray();
            if (arr.size() == 0) break;

            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject repo = el.getAsJsonObject();
                McpRegistryServerInfo info = repoToServerInfo(repo);
                if (info != null) result.add(info);
            }

            if (progress != null) progress.onProgress(result.size(), page);

            if (arr.size() < 100) break; // last page
            page++;
        }

        System.err.println("[GitHubOrgAdapter] Loaded " + result.size()
                + " repos from github.com/" + orgName);
        return result;
    }

    private McpRegistryServerInfo repoToServerInfo(JsonObject repo) {
        boolean archived = repo.has("archived") && repo.get("archived").getAsBoolean();
        boolean fork = repo.has("fork") && repo.get("fork").getAsBoolean();
        if (fork) return null; // skip forks

        String description = getStr(repo, "description");
        String repoUrl = getStr(repo, "html_url");
        String language = getStr(repo, "language");
        int stars = repo.has("stargazers_count") ? repo.get("stargazers_count").getAsInt() : 0;

        // Convert to registry-like name: io.github.{org}/{repo-name}
        String repoName = getStr(repo, "name");
        String registryName = "io.github." + orgName + "/" + repoName;

        // Build description with metadata
        StringBuilder desc = new StringBuilder();
        if (description != null && !description.isEmpty()) desc.append(description);
        if (language != null) {
            if (desc.length() > 0) desc.append(" | ");
            desc.append(language);
        }
        if (stars > 0) {
            if (desc.length() > 0) desc.append(" | ");
            desc.append("\u2605 ").append(formatStars(stars));
        }

        String status = archived ? "deprecated" : "active";

        // Determine packages: if it has a package.json, likely npm
        List<McpRegistryServerInfo.PackageInfo> packages = new ArrayList<>();
        // We don't know packages from the repo listing API, leave empty

        return McpRegistryServerInfo.fromGitHubRepo(
                registryName, repoName, desc.toString(), status,
                null, repoUrl, true, packages);
    }

    private static String formatStars(int stars) {
        if (stars >= 1000) return String.format("%.1fk", stars / 1000.0);
        return String.valueOf(stars);
    }

    private static String getStr(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) return obj.get(key).getAsString();
        return null;
    }

    // ── Caching ─────────────────────────────────────────────────────

    private String fetchWithCache(String urlStr, String cacheKey) throws IOException {
        CACHE_DIR.mkdirs();
        File cacheFile = new File(CACHE_DIR, cacheKey + ".json");

        if (cacheFile.exists()) {
            long age = System.currentTimeMillis() - cacheFile.lastModified();
            if (age < CACHE_TTL) {
                return readFile(cacheFile);
            }
        }

        try {
            String json = httpGet(urlStr);
            try (Writer w = new OutputStreamWriter(new FileOutputStream(cacheFile), StandardCharsets.UTF_8)) {
                w.write(json);
            }
            return json;
        } catch (IOException e) {
            if (cacheFile.exists()) {
                System.err.println("[GitHubOrgAdapter] Network error, using stale cache: " + e.getMessage());
                return readFile(cacheFile);
            }
            throw e;
        }
    }

    private static String httpGet(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        // Optional: use GITHUB_TOKEN env for higher rate limits
        String token = System.getenv("GITHUB_TOKEN");
        if (token != null && !token.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
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

    public void invalidateCache() {
        if (CACHE_DIR.exists()) {
            File[] files = CACHE_DIR.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().startsWith("gh_" + orgName)) {
                        f.delete();
                    }
                }
            }
        }
    }
}

