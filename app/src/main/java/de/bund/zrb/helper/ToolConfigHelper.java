package de.bund.zrb.helper;

import com.google.gson.*;
import de.zrb.bund.newApi.mcp.ToolConfig;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists per-tool ToolConfig objects to tool-configs.json in the settings folder.
 * Each tool is stored by its name as key, with a Gson-serialized ToolConfig as value.
 *
 * <p>On load, configs are deserialized as raw JsonObjects stored in ToolConfig wrappers.
 * To get the properly typed config, use {@link ToolConfig#fromJson(JsonObject, Class)}
 * with the tool's {@code getConfigClass()} result.</p>
 */
public class ToolConfigHelper {

    private static final File CONFIG_FILE = new File(SettingsHelper.getSettingsFolder(), "tool-configs.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Load all tool configs from disk.
     * Returns a map of toolName → ToolConfig (deserialized as base ToolConfig).
     * Callers can re-deserialize with the correct subclass using ToolConfig.fromJson().
     */
    public static Map<String, ToolConfig> loadAll() {
        Map<String, ToolConfig> result = new LinkedHashMap<>();
        if (!CONFIG_FILE.exists()) return result;
        try (Reader reader = new InputStreamReader(new FileInputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseString(readAll(reader)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    // Store as base ToolConfig; actual deserialization to subclass
                    // happens when the tool's getConfigClass() is known
                    ToolConfig config = GSON.fromJson(entry.getValue(), ToolConfig.class);
                    result.put(entry.getKey(), config);
                }
            }
        } catch (Exception e) {
            System.err.println("[ToolConfigHelper] Failed to load tool configs: " + e.getMessage());
        }
        return result;
    }

    /**
     * Load config for a specific tool as a JsonObject (for re-deserialization with correct type).
     */
    public static JsonObject getConfigJson(String toolName) {
        if (!CONFIG_FILE.exists()) return null;
        try (Reader reader = new InputStreamReader(new FileInputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseString(readAll(reader)).getAsJsonObject();
            if (root.has(toolName) && root.get(toolName).isJsonObject()) {
                return root.getAsJsonObject(toolName);
            }
        } catch (Exception e) {
            System.err.println("[ToolConfigHelper] Failed to load config for " + toolName + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Save all tool configs to disk.
     */
    public static void saveAll(Map<String, ToolConfig> configs) {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            JsonObject root = new JsonObject();
            for (Map.Entry<String, ToolConfig> entry : configs.entrySet()) {
                root.add(entry.getKey(), entry.getValue().toJson());
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
    public static ToolConfig getConfig(String toolName) {
        return loadAll().get(toolName);
    }

    /**
     * Set config for a specific tool and save to disk.
     */
    public static void setConfig(String toolName, ToolConfig config) {
        Map<String, ToolConfig> all = loadAll();
        all.put(toolName, config);
        saveAll(all);
    }

    /**
     * Remove config for a specific tool.
     */
    public static void removeConfig(String toolName) {
        Map<String, ToolConfig> all = loadAll();
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

