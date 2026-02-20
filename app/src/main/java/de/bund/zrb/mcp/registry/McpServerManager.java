package de.bund.zrb.mcp.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.bund.zrb.runtime.ToolRegistryImpl;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of external MCP servers:
 * - Starts/stops server processes
 * - Queries their tool lists
 * - Registers discovered tools as McpTool in the ToolRegistry
 */
public class McpServerManager {

    private static McpServerManager instance;

    private final McpServerConfigRepository configRepository = new McpServerConfigRepository();
    private final Map<String, McpServerClient> runningServers = new ConcurrentHashMap<>();
    /** Tool names contributed by each server, so we can unregister them */
    private final Map<String, List<String>> serverToolNames = new ConcurrentHashMap<>();

    private final List<Listener> listeners = new ArrayList<>();

    public interface Listener {
        void onServerStateChanged();
    }

    private McpServerManager() {
        // Built-in servers are registered via registerPluginMcpServer()
        // called from PluginManager.registerMcpServersFromPlugins()
    }

    public static synchronized McpServerManager getInstance() {
        if (instance == null) {
            instance = new McpServerManager();
        }
        return instance;
    }

    // ── Plugin-driven MCP server registration ───────────────────────

    /**
     * Register an MCP server declared by a plugin.
     * The JAR is expected in the plugin directory ({@code ~/.mainframemate/plugins/}).
     *
     * @param displayName      human-readable name (e.g. "Websearch")
     * @param jarFileName      filename of the fat-JAR (e.g. "wd4j-mcp-server.jar")
     * @param enabledByDefault whether the server should be enabled on first registration
     */
    public void registerPluginMcpServer(String displayName, String jarFileName, boolean enabledByDefault) {
        McpServerConfig config = new McpServerConfig(
                displayName,
                "java",
                java.util.Arrays.asList("-jar", "<auto:" + jarFileName.replace(".jar", "") + ">"),
                enabledByDefault
        );
        configRepository.registerBuiltIn(config);
        System.err.println("[McpServerManager] Built-in '" + displayName + "' registered (jar: " + jarFileName + ")");
    }

    /**
     * For built-in servers with an {@code <auto:...>} JAR placeholder,
     * resolve the actual path before starting.
     *
     * @return a config copy with resolved args, or null if the JAR cannot be found
     */
    private McpServerConfig resolveBuiltInArgs(McpServerConfig config) {
        List<String> args = config.getArgs();
        if (args == null) return config;

        List<String> resolved = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("<auto:") && arg.endsWith(">")) {
                String baseName = arg.substring(6, arg.length() - 1);
                String jarPath = resolveBuiltInJarPath(baseName);
                if (jarPath == null) {
                    return null; // cannot resolve
                }
                resolved.add(jarPath);
            } else {
                resolved.add(arg);
            }
        }

        McpServerConfig copy = new McpServerConfig(
                config.getName(), config.getCommand(), resolved, config.isEnabled());
        copy.setBuiltIn(config.isBuiltIn());
        return copy;
    }

    /**
     * Tries to locate the fat-JAR for a built-in MCP server.
     * Primary location: {@code ~/.mainframemate/plugins/<baseName>.jar}
     * (same directory used by the plugin system).
     * Falls back to working-dir-relative paths for development.
     */
    private static String resolveBuiltInJarPath(String baseName) {
        // 1. Plugin directory (production location)
        java.io.File pluginDir = new java.io.File(
                System.getProperty("user.home"), ".mainframemate/plugins");
        java.io.File pluginJar = new java.io.File(pluginDir, baseName + ".jar");
        if (pluginJar.isFile()) {
            try { return pluginJar.getCanonicalPath(); }
            catch (Exception e) { return pluginJar.getAbsolutePath(); }
        }

        // 2. Development / build output locations (relative to working dir)
        String[] candidates = {
                baseName + "/build/libs/" + baseName + ".jar",
                "../" + baseName + "/build/libs/" + baseName + ".jar",
                "lib/" + baseName + ".jar",
                baseName + ".jar",
        };

        for (String candidate : candidates) {
            java.io.File f = new java.io.File(candidate);
            if (f.isFile()) {
                try {
                    return f.getCanonicalPath();
                } catch (Exception e) {
                    return f.getAbsolutePath();
                }
            }
        }

        // 3. Next to the application JAR itself
        try {
            java.security.CodeSource cs = McpServerManager.class.getProtectionDomain().getCodeSource();
            if (cs != null) {
                java.io.File appDir = new java.io.File(cs.getLocation().toURI()).getParentFile();
                java.io.File nearby = new java.io.File(appDir, baseName + ".jar");
                if (nearby.isFile()) {
                    return nearby.getCanonicalPath();
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    // ── Listeners ───────────────────────────────────────────────────

    public void addListener(Listener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void fireStateChanged() {
        for (Listener l : listeners) {
            try { l.onServerStateChanged(); } catch (Exception ignored) {}
        }
    }

    // ── Server lifecycle ────────────────────────────────────────────

    /**
     * Start all enabled servers from config.
     */
    public void startEnabledServers() {
        List<McpServerConfig> configs = configRepository.loadAll();
        for (McpServerConfig config : configs) {
            if (config.isEnabled() && !runningServers.containsKey(config.getName())) {
                startServer(config);
            }
        }
    }

    /**
     * Start a specific server, discover its tools, and register them.
     */
    public void startServer(McpServerConfig config) {
        if (runningServers.containsKey(config.getName())) {
            System.err.println("[McpServerManager] Server already running: " + config.getName());
            return;
        }

        // Resolve <auto:...> placeholders in built-in server args
        McpServerConfig resolved = resolveBuiltInArgs(config);
        if (resolved == null) {
            System.err.println("[McpServerManager] Cannot start '" + config.getName()
                    + "': JAR not found in ~/.mainframemate/plugins/."
                    + " Build & install with: gradlew :wd4j-mcp-server:build");
            fireStateChanged();
            return;
        }

        McpServerClient client = new McpServerClient(resolved);
        try {
            client.start();
            runningServers.put(config.getName(), client);
            discoverAndRegisterTools(config.getName(), client);
            System.err.println("[McpServerManager] Started: " + config.getName());
        } catch (Exception e) {
            System.err.println("[McpServerManager] Failed to start " + config.getName() + ": " + e.getMessage());
            client.stop();
        }
        fireStateChanged();
    }

    /**
     * Stop a specific server and unregister its tools.
     */
    public void stopServer(String serverName) {
        McpServerClient client = runningServers.remove(serverName);
        if (client != null) {
            unregisterTools(serverName);
            client.stop();
            System.err.println("[McpServerManager] Stopped: " + serverName);
        }
        fireStateChanged();
    }

    /**
     * Stop all running servers.
     */
    public void stopAll() {
        for (String name : new ArrayList<>(runningServers.keySet())) {
            stopServer(name);
        }
    }

    /**
     * Restart a server (stop + start).
     */
    public void restartServer(McpServerConfig config) {
        stopServer(config.getName());
        if (config.isEnabled()) {
            startServer(config);
        }
    }

    public boolean isRunning(String serverName) {
        McpServerClient client = runningServers.get(serverName);
        return client != null && client.isRunning();
    }

    public Set<String> getRunningServerNames() {
        return Collections.unmodifiableSet(runningServers.keySet());
    }

    /**
     * Get tool names contributed by a server.
     */
    public List<String> getToolNames(String serverName) {
        List<String> names = serverToolNames.get(serverName);
        return names != null ? Collections.unmodifiableList(names) : Collections.<String>emptyList();
    }

    // ── Tool discovery & registration ───────────────────────────────

    private void discoverAndRegisterTools(String serverName, McpServerClient client) {
        try {
            JsonArray tools = client.listTools();
            List<String> toolNames = new ArrayList<>();
            ToolRegistryImpl registry = ToolRegistryImpl.getInstance();

            for (JsonElement el : tools) {
                JsonObject toolObj = el.getAsJsonObject();
                String name = toolObj.get("name").getAsString();
                String description = toolObj.has("description") ? toolObj.get("description").getAsString() : "";
                JsonObject inputSchema = toolObj.has("inputSchema") ? toolObj.getAsJsonObject("inputSchema") : new JsonObject();

                // Prefix tool name with server name to avoid conflicts
                String qualifiedName = serverName + "/" + name;
                McpTool proxyTool = new ExternalMcpTool(qualifiedName, description, inputSchema, client, name);
                registry.registerTool(proxyTool);
                toolNames.add(qualifiedName);
                System.err.println("[McpServerManager] Registered tool: " + qualifiedName);
            }

            serverToolNames.put(serverName, toolNames);
        } catch (Exception e) {
            System.err.println("[McpServerManager] Failed to discover tools for " + serverName + ": " + e.getMessage());
        }
    }

    private void unregisterTools(String serverName) {
        List<String> names = serverToolNames.remove(serverName);
        if (names != null) {
            ToolRegistryImpl registry = ToolRegistryImpl.getInstance();
            for (String name : names) {
                registry.unregisterTool(name);
                System.err.println("[McpServerManager] Unregistered tool: " + name);
            }
        }
    }

    // ── Config access ───────────────────────────────────────────────

    public List<McpServerConfig> loadConfigs() {
        return configRepository.loadAll();
    }

    public void saveConfigs(List<McpServerConfig> configs) {
        configRepository.saveAll(configs);
    }

    // ── Inner class: proxy tool ─────────────────────────────────────

    /**
     * A McpTool that delegates execution to an external MCP server via JSON-RPC.
     */
    private static class ExternalMcpTool implements McpTool {

        private final String qualifiedName;
        private final String description;
        private final JsonObject inputSchemaJson;
        private final McpServerClient client;
        private final String remoteName;

        ExternalMcpTool(String qualifiedName, String description, JsonObject inputSchemaJson,
                        McpServerClient client, String remoteName) {
            this.qualifiedName = qualifiedName;
            this.description = description;
            this.inputSchemaJson = inputSchemaJson;
            this.client = client;
            this.remoteName = remoteName;
        }

        @Override
        public ToolSpec getSpec() {
            // Build a ToolSpec from the JSON schema
            Map<String, ToolSpec.Property> props = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();

            if (inputSchemaJson.has("properties")) {
                JsonObject propsJson = inputSchemaJson.getAsJsonObject("properties");
                for (Map.Entry<String, JsonElement> entry : propsJson.entrySet()) {
                    JsonObject propObj = entry.getValue().getAsJsonObject();
                    String type = propObj.has("type") ? propObj.get("type").getAsString() : "string";
                    String desc = propObj.has("description") ? propObj.get("description").getAsString() : "";
                    props.put(entry.getKey(), new ToolSpec.Property(type, desc));
                }
            }
            if (inputSchemaJson.has("required") && inputSchemaJson.get("required").isJsonArray()) {
                for (JsonElement el : inputSchemaJson.getAsJsonArray("required")) {
                    required.add(el.getAsString());
                }
            }

            ToolSpec.InputSchema schema = new ToolSpec.InputSchema(props, required);
            return new ToolSpec(qualifiedName, description, schema, null);
        }

        @Override
        public McpToolResponse execute(JsonObject input, String resultVar) {
            try {
                JsonObject result = client.callTool(remoteName, input);
                JsonObject response = new JsonObject();
                response.addProperty("status", "ok");

                // Extract text content from MCP result
                if (result != null && result.has("content")) {
                    JsonArray content = result.getAsJsonArray("content");
                    StringBuilder text = new StringBuilder();
                    for (JsonElement el : content) {
                        JsonObject c = el.getAsJsonObject();
                        if ("text".equals(c.get("type").getAsString())) {
                            if (text.length() > 0) text.append("\n");
                            text.append(c.get("text").getAsString());
                        }
                    }
                    response.addProperty("result", text.toString());
                    response.add("raw", result);
                } else {
                    response.add("result", result);
                }

                if (result != null && result.has("isError") && result.get("isError").getAsBoolean()) {
                    response.addProperty("status", "error");
                }

                return new McpToolResponse(response, resultVar, null);
            } catch (Exception e) {
                JsonObject error = new JsonObject();
                error.addProperty("status", "error");
                error.addProperty("message", e.getMessage());
                return new McpToolResponse(error, resultVar, null);
            }
        }
    }
}

