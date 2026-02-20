package de.bund.zrb.mcp.registry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.bund.zrb.helper.SettingsHelper;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Persisted settings for the MCP Registry browser.
 */
public class McpRegistrySettings {

    private static final File FILE = new File(SettingsHelper.getSettingsFolder(), "mcp-registry-settings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String DEFAULT_REGISTRY_URL = "https://registry.modelcontextprotocol.io";

    private String registryBaseUrl = DEFAULT_REGISTRY_URL;
    private long cacheTtlMs = 3600_000L; // 1 hour

    public McpRegistrySettings() {}

    public String getRegistryBaseUrl() { return registryBaseUrl; }
    public void setRegistryBaseUrl(String url) { this.registryBaseUrl = url; }

    public long getCacheTtlMs() { return cacheTtlMs; }
    public void setCacheTtlMs(long ms) { this.cacheTtlMs = ms; }

    // ── Persistence ─────────────────────────────────────────────────

    public static McpRegistrySettings load() {
        if (!FILE.exists()) return new McpRegistrySettings();
        try (Reader r = new InputStreamReader(new FileInputStream(FILE), StandardCharsets.UTF_8)) {
            McpRegistrySettings s = GSON.fromJson(r, McpRegistrySettings.class);
            return s != null ? s : new McpRegistrySettings();
        } catch (Exception e) {
            System.err.println("[McpRegistrySettings] Error loading: " + e.getMessage());
            return new McpRegistrySettings();
        }
    }

    public void save() {
        try {
            FILE.getParentFile().mkdirs();
            try (Writer w = new OutputStreamWriter(new FileOutputStream(FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (Exception e) {
            System.err.println("[McpRegistrySettings] Error saving: " + e.getMessage());
        }
    }
}

