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

    private McpServerManager() {}

    public static synchronized McpServerManager getInstance() {
        if (instance == null) {
            instance = new McpServerManager();
        }
        return instance;
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

        McpServerClient client = new McpServerClient(config);
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

