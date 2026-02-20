package de.bund.zrb.mcp.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.bund.zrb.runtime.ToolRegistryImpl;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of MCP servers:
 * <ul>
 *   <li><b>In-process (built-in)</b>: Browser tools run directly via wd4j-mcp-server classes (no separate process).</li>
 *   <li><b>External</b>: User-defined servers started as separate processes via JSON-RPC.</li>
 * </ul>
 */
public class McpServerManager {

    private static McpServerManager instance;

    private final McpServerConfigRepository configRepository = new McpServerConfigRepository();

    /** External servers running as separate processes. */
    private final Map<String, McpServerClient> runningServers = new ConcurrentHashMap<>();

    /** In-process browser sessions (built-in MCP servers). */
    private final Map<String, BrowserSession> inProcessSessions = new ConcurrentHashMap<>();

    /** In-process tool registries per server. */
    private final Map<String, de.bund.zrb.mcpserver.tool.ToolRegistry> inProcessToolRegistries = new ConcurrentHashMap<>();

    /** Tool names contributed by each server, so we can unregister them. */
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

    // ── Plugin-driven MCP server registration ───────────────────────

    /**
     * Register an in-process MCP server declared by a plugin.
     * No JAR or external process needed — the server runs within the application.
     *
     * @param displayName      human-readable name (e.g. "Websearch")
     * @param enabledByDefault whether the server should be enabled on first registration
     */
    public void registerPluginMcpServer(String displayName, boolean enabledByDefault) {
        McpServerConfig config = new McpServerConfig(
                displayName,
                "<in-process>",
                Collections.<String>emptyList(),
                enabledByDefault
        );
        configRepository.registerBuiltIn(config);
        System.err.println("[McpServerManager] Built-in '" + displayName + "' registered (in-process)");
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
            if (config.isEnabled() && !isRunning(config.getName())) {
                startServer(config);
            }
        }
    }

    /**
     * Start a specific server, discover its tools, and register them.
     */
    public void startServer(McpServerConfig config) {
        String name = config.getName();
        if (isRunning(name)) {
            System.err.println("[McpServerManager] Server already running: " + name);
            return;
        }

        if ("<in-process>".equals(config.getCommand())) {
            startInProcess(config);
        } else {
            startExternal(config);
        }
        fireStateChanged();
    }

    private void startInProcess(McpServerConfig config) {
        String name = config.getName();
        try {
            BrowserSession session = new BrowserSession();
            de.bund.zrb.mcpserver.tool.ToolRegistry mcpRegistry = new de.bund.zrb.mcpserver.tool.ToolRegistry();

            // Register all known MCP server tools
            mcpRegistry.register(new de.bund.zrb.mcpserver.tool.impl.BrowserOpenTool());
            mcpRegistry.register(new de.bund.zrb.mcpserver.tool.impl.BrowserLaunchTool());
            mcpRegistry.register(new de.bund.zrb.mcpserver.tool.impl.BrowserNavigateTool());
            mcpRegistry.register(new de.bund.zrb.mcpserver.tool.impl.BrowserClickCssTool());
            mcpRegistry.register(new de.bund.zrb.mcpserver.tool.impl.BrowserTypeCssTool());
            mcpRegistry.register(new de.bund.zrb.mcpserver.tool.impl.BrowserEvalTool());
            mcpRegistry.register(new de.bund.zrb.mcpserver.tool.impl.BrowserScreenshotTool());
            mcpRegistry.register(new de.bund.zrb.mcpserver.tool.impl.BrowserCloseTool());
            mcpRegistry.register(new de.bund.zrb.mcpserver.tool.impl.BrowserWaitForTool());
            mcpRegistry.register(new de.bund.zrb.mcpserver.tool.impl.PageDomSnapshotTool());
            mcpRegistry.register(new de.bund.zrb.mcpserver.tool.impl.PageExtractTool());

            inProcessSessions.put(name, session);
            inProcessToolRegistries.put(name, mcpRegistry);

            // Register tools as McpTool in the app's ToolRegistry
            registerInProcessTools(name, mcpRegistry, session);

            System.err.println("[McpServerManager] Started (in-process): " + name);
        } catch (Exception e) {
            System.err.println("[McpServerManager] Failed to start in-process " + name + ": " + e.getMessage());
        }
    }

    private void startExternal(McpServerConfig config) {
        String name = config.getName();
        McpServerClient client = new McpServerClient(config);
        try {
            client.start();
            runningServers.put(name, client);
            discoverAndRegisterTools(name, client);
            System.err.println("[McpServerManager] Started (external): " + name);
        } catch (Exception e) {
            System.err.println("[McpServerManager] Failed to start " + name + ": " + e.getMessage());
            client.stop();
        }
    }

    /**
     * Stop a specific server and unregister its tools.
     */
    public void stopServer(String serverName) {
        // In-process
        BrowserSession session = inProcessSessions.remove(serverName);
        inProcessToolRegistries.remove(serverName);
        if (session != null) {
            try { session.close(); } catch (Exception ignored) {}
            unregisterTools(serverName);
            System.err.println("[McpServerManager] Stopped (in-process): " + serverName);
        }

        // External
        McpServerClient client = runningServers.remove(serverName);
        if (client != null) {
            unregisterTools(serverName);
            client.stop();
            System.err.println("[McpServerManager] Stopped (external): " + serverName);
        }
        fireStateChanged();
    }

    /**
     * Stop all running servers.
     */
    public void stopAll() {
        Set<String> allNames = new HashSet<>();
        allNames.addAll(inProcessSessions.keySet());
        allNames.addAll(runningServers.keySet());
        for (String name : allNames) {
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
        if (inProcessSessions.containsKey(serverName)) return true;
        McpServerClient client = runningServers.get(serverName);
        return client != null && client.isRunning();
    }

    public Set<String> getRunningServerNames() {
        Set<String> names = new HashSet<>();
        names.addAll(inProcessSessions.keySet());
        names.addAll(runningServers.keySet());
        return Collections.unmodifiableSet(names);
    }

    /**
     * Get tool names contributed by a server.
     */
    public List<String> getToolNames(String serverName) {
        List<String> names = serverToolNames.get(serverName);
        return names != null ? Collections.unmodifiableList(names) : Collections.<String>emptyList();
    }

    /**
     * Get the in-process browser session for a server (e.g. for settings).
     */
    public BrowserSession getInProcessSession(String serverName) {
        return inProcessSessions.get(serverName);
    }

    // ── In-process tool registration ────────────────────────────────

    private void registerInProcessTools(String serverName, de.bund.zrb.mcpserver.tool.ToolRegistry mcpRegistry,
                                        BrowserSession session) {
        List<String> toolNames = new ArrayList<>();
        ToolRegistryImpl registry = ToolRegistryImpl.getInstance();

        for (McpServerTool mcpTool : mcpRegistry.allTools()) {
            String qualifiedName = serverName + "/" + mcpTool.name();
            McpTool proxyTool = new InProcessMcpTool(qualifiedName, mcpTool, session);
            registry.registerTool(proxyTool);
            toolNames.add(qualifiedName);
            System.err.println("[McpServerManager] Registered tool: " + qualifiedName);
        }

        serverToolNames.put(serverName, toolNames);
    }

    // ── External tool discovery & registration ──────────────────────

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

    // ── Inner class: in-process tool proxy ──────────────────────────

    /**
     * McpTool that delegates directly to an in-process McpServerTool + BrowserSession.
     */
    private static class InProcessMcpTool implements McpTool {

        private final String qualifiedName;
        private final McpServerTool serverTool;
        private final BrowserSession session;

        InProcessMcpTool(String qualifiedName, McpServerTool serverTool, BrowserSession session) {
            this.qualifiedName = qualifiedName;
            this.serverTool = serverTool;
            this.session = session;
        }

        @Override
        public ToolSpec getSpec() {
            JsonObject schema = serverTool.inputSchema();
            Map<String, ToolSpec.Property> props = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();

            if (schema.has("properties")) {
                for (Map.Entry<String, JsonElement> entry : schema.getAsJsonObject("properties").entrySet()) {
                    JsonObject propObj = entry.getValue().getAsJsonObject();
                    String type = propObj.has("type") ? propObj.get("type").getAsString() : "string";
                    String desc = propObj.has("description") ? propObj.get("description").getAsString() : "";
                    props.put(entry.getKey(), new ToolSpec.Property(type, desc));
                }
            }
            if (schema.has("required") && schema.get("required").isJsonArray()) {
                for (JsonElement el : schema.getAsJsonArray("required")) {
                    required.add(el.getAsString());
                }
            }

            ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(props, required);
            return new ToolSpec(qualifiedName, serverTool.description(), inputSchema, null);
        }

        @Override
        public McpToolResponse execute(JsonObject input, String resultVar) {
            try {
                ToolResult result = serverTool.execute(input, session);
                JsonObject response = new JsonObject();

                if (result.isError()) {
                    response.addProperty("status", "error");
                } else {
                    response.addProperty("status", "ok");
                }

                // Extract text from ToolResult content
                JsonElement contentJson = result.toJson();
                if (contentJson.isJsonObject() && contentJson.getAsJsonObject().has("content")) {
                    JsonArray content = contentJson.getAsJsonObject().getAsJsonArray("content");
                    StringBuilder text = new StringBuilder();
                    for (JsonElement el : content) {
                        JsonObject c = el.getAsJsonObject();
                        if ("text".equals(c.get("type").getAsString())) {
                            if (text.length() > 0) text.append("\n");
                            text.append(c.get("text").getAsString());
                        }
                    }
                    response.addProperty("result", text.toString());
                }
                response.add("raw", contentJson);
                return new McpToolResponse(response, resultVar, null);
            } catch (Exception e) {
                JsonObject error = new JsonObject();
                error.addProperty("status", "error");
                error.addProperty("message", e.getMessage());
                return new McpToolResponse(error, resultVar, null);
            }
        }
    }

    // ── Inner class: external tool proxy ────────────────────────────

    /**
     * McpTool that delegates to an external MCP server via JSON-RPC.
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
            Map<String, ToolSpec.Property> props = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();

            if (inputSchemaJson.has("properties")) {
                for (Map.Entry<String, JsonElement> entry : inputSchemaJson.getAsJsonObject("properties").entrySet()) {
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

