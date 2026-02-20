package de.bund.zrb.mcp.registry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.bund.zrb.helper.SettingsHelper;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Persists the list of configured MCP servers to a JSON file
 * in the application settings folder.
 *
 * Built-in servers are always present in the returned list but
 * not stored in the JSON themselves – only their enabled-state is persisted.
 */
public class McpServerConfigRepository {

    private static final File FILE = new File(SettingsHelper.getSettingsFolder(), "mcp-servers.json");
    private static final File BUILTIN_STATE_FILE = new File(SettingsHelper.getSettingsFolder(), "mcp-builtin-state.json");
    private static final Type LIST_TYPE = new TypeToken<List<McpServerConfig>>() {}.getType();
    private static final Type STATE_TYPE = new TypeToken<Map<String, Boolean>>() {}.getType();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Built-in server definitions registered at startup. */
    private final List<McpServerConfig> builtIns = new ArrayList<>();

    /**
     * Register a built-in server. Call this before loadAll().
     */
    public void registerBuiltIn(McpServerConfig config) {
        config.setBuiltIn(true);
        builtIns.add(config);
    }

    /**
     * Load all servers: built-ins (with persisted enabled-state) + user-defined.
     */
    public List<McpServerConfig> loadAll() {
        // 1. Load persisted user servers
        List<McpServerConfig> userServers = readUserServers();

        // 2. Load persisted built-in enabled-states
        Map<String, Boolean> builtInStates = readBuiltInStates();

        // 3. Merge: built-ins first, then user servers
        List<McpServerConfig> merged = new ArrayList<>();

        for (McpServerConfig bi : builtIns) {
            McpServerConfig copy = new McpServerConfig(bi.getName(), bi.getCommand(), bi.getArgs(),
                    builtInStates.containsKey(bi.getName()) ? builtInStates.get(bi.getName()) : bi.isEnabled());
            copy.setBuiltIn(true);
            merged.add(copy);
        }

        // Add user servers (skip any that collide with a built-in name)
        Set<String> builtInNames = new HashSet<>();
        for (McpServerConfig bi : builtIns) {
            builtInNames.add(bi.getName());
        }
        for (McpServerConfig us : userServers) {
            if (!builtInNames.contains(us.getName())) {
                merged.add(us);
            }
        }

        return merged;
    }

    /**
     * Save all servers. Built-in servers are not written to the user file,
     * but their enabled-state is persisted separately.
     */
    public void saveAll(List<McpServerConfig> configs) {
        List<McpServerConfig> userServers = new ArrayList<>();
        Map<String, Boolean> builtInStates = new LinkedHashMap<>();

        for (McpServerConfig cfg : configs) {
            if (cfg.isBuiltIn()) {
                builtInStates.put(cfg.getName(), cfg.isEnabled());
            } else {
                userServers.add(cfg);
            }
        }

        // Save user servers
        writeJson(FILE, userServers, LIST_TYPE);

        // Save built-in enabled states
        writeJson(BUILTIN_STATE_FILE, builtInStates, STATE_TYPE);
    }

    // ── Internal ────────────────────────────────────────────────────

    private List<McpServerConfig> readUserServers() {
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

    private Map<String, Boolean> readBuiltInStates() {
        if (!BUILTIN_STATE_FILE.exists()) {
            return new LinkedHashMap<>();
        }
        try (Reader r = new InputStreamReader(new FileInputStream(BUILTIN_STATE_FILE), StandardCharsets.UTF_8)) {
            Map<String, Boolean> map = GSON.fromJson(r, STATE_TYPE);
            return map != null ? map : new LinkedHashMap<>();
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private void writeJson(File file, Object data, Type type) {
        try {
            file.getParentFile().mkdirs();
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                GSON.toJson(data, type, w);
            }
        } catch (Exception e) {
            System.err.println("[McpServerConfigRepository] Error saving " + file.getName() + ": " + e.getMessage());
        }
    }
}

