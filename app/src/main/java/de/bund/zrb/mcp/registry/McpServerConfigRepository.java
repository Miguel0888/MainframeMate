package de.bund.zrb.mcp.registry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.bund.zrb.helper.SettingsHelper;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists the list of configured MCP servers to a JSON file
 * in the application settings folder.
 */
public class McpServerConfigRepository {

    private static final File FILE = new File(SettingsHelper.getSettingsFolder(), "mcp-servers.json");
    private static final Type LIST_TYPE = new TypeToken<List<McpServerConfig>>() {}.getType();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public List<McpServerConfig> loadAll() {
        if (!FILE.exists()) {
            return new ArrayList<>();
        }
        try (Reader r = new InputStreamReader(new FileInputStream(FILE), StandardCharsets.UTF_8)) {
            List<McpServerConfig> list = GSON.fromJson(r, LIST_TYPE);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            System.err.println("[McpServerConfigRepository] Error loading: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public void saveAll(List<McpServerConfig> configs) {
        try {
            FILE.getParentFile().mkdirs();
            try (Writer w = new OutputStreamWriter(new FileOutputStream(FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(configs, LIST_TYPE, w);
            }
        } catch (Exception e) {
            System.err.println("[McpServerConfigRepository] Error saving: " + e.getMessage());
        }
    }
}

