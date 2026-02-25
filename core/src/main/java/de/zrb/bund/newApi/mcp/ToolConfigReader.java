package de.zrb.bund.newApi.mcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Lightweight reader for tool-configs.json, usable from plugins (core module).
 * Only reads the config file; writing is handled by the app-level ToolConfigHelper.
 */
public class ToolConfigReader {

    private static final File CONFIG_FILE = new File(
            System.getProperty("user.home"), ".mainframemate/tool-configs.json");

    /**
     * Read the persisted config for a specific tool as a JsonObject.
     *
     * @param toolName the tool name (e.g. "import_excel")
     * @return the config JSON, or null if not found
     */
    public static JsonObject getConfigJson(String toolName) {
        if (!CONFIG_FILE.exists()) return null;
        try (Reader reader = new InputStreamReader(new FileInputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) sb.append(buf, 0, n);
            JsonObject root = JsonParser.parseString(sb.toString()).getAsJsonObject();
            if (root.has(toolName) && root.get(toolName).isJsonObject()) {
                return root.getAsJsonObject(toolName);
            }
        } catch (Exception e) {
            // Silently ignore – caller uses fallback
        }
        return null;
    }

    /**
     * Load a typed ToolConfig for a specific tool from the persisted configs.
     *
     * @param toolName the tool name
     * @param clazz    the ToolConfig subclass
     * @return the config, or null if not found
     */
    public static <T extends ToolConfig> T getConfig(String toolName, Class<T> clazz) {
        JsonObject json = getConfigJson(toolName);
        if (json == null) return null;
        return ToolConfig.fromJson(json, clazz);
    }
}

