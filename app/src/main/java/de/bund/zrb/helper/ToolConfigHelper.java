package de.bund.zrb.helper;

import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists per-tool JSON configuration to tool-configs.json in the settings folder.
 * Each tool is stored by its name as key, with an arbitrary JsonObject as config value.
 */
public class ToolConfigHelper {

    private static final File CONFIG_FILE = new File(SettingsHelper.getSettingsFolder(), "tool-configs.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Load all tool configs from disk.
     * @return map of toolName → config JsonObject (never null)
     */
    public static Map<String, JsonObject> loadAll() {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        if (!CONFIG_FILE.exists()) return result;
        try (Reader reader = new InputStreamReader(new FileInputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseString(readAll(reader)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    result.put(entry.getKey(), entry.getValue().getAsJsonObject());
                }
            }
        } catch (Exception e) {
            System.err.println("[ToolConfigHelper] Failed to load tool configs: " + e.getMessage());
        }
        return result;
    }

    /**
     * Save all tool configs to disk.
     */
    public static void saveAll(Map<String, JsonObject> configs) {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            JsonObject root = new JsonObject();
            for (Map.Entry<String, JsonObject> entry : configs.entrySet()) {
                root.add(entry.getKey(), entry.getValue());
            }
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            System.err.println("[ToolConfigHelper] Failed to save tool configs: " + e.getMessage());
        }
    }

    /**
     * Get config for a specific tool. Returns null if not found.
     */
    public static JsonObject getConfig(String toolName) {
        return loadAll().get(toolName);
    }

    /**
     * Set config for a specific tool and save to disk.
     */
    public static void setConfig(String toolName, JsonObject config) {
        Map<String, JsonObject> all = loadAll();
        all.put(toolName, config);
        saveAll(all);
    }

    /**
     * Remove config for a specific tool.
     */
    public static void removeConfig(String toolName) {
        Map<String, JsonObject> all = loadAll();
        all.remove(toolName);
        saveAll(all);
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }
}

