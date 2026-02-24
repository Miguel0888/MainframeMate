package de.bund.zrb.runtime;

import com.google.gson.JsonObject;
import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;
import de.bund.zrb.helper.ToolSettingsHelper;

import java.util.*;

public class ToolRegistryImpl implements ToolRegistry {

    private static ToolRegistryImpl instance;

    private final Map<String, McpTool> toolsByName = new LinkedHashMap<>();

    private ToolRegistryImpl() {
        // private, damit keine externe Instanzierung möglich ist
    }

    /**
     * Liefert die Singleton-Instanz der ToolRegistry.
     */
    public static synchronized ToolRegistryImpl getInstance() {
        if (instance == null) {
            instance = new ToolRegistryImpl();
        }
        return instance;
    }

    @Override
    public void registerTool(McpTool tool) {
        String name = tool.getSpec().getName();
        ToolSpec userSpec = ToolSettingsHelper.findToolByName(name);
        McpTool wrapped = wrapWithUserSpec(tool, userSpec);
        toolsByName.put(name, wrapped);
    }

    /**
     * Entfernt ein Tool aus der Registry (z.B. wenn ein externer MCP-Server gestoppt wird).
     */
    public void unregisterTool(String toolName) {
        toolsByName.remove(toolName);
    }

    private McpTool wrapWithUserSpec(McpTool original, ToolSpec override) {
        if (override == null) return original;
        return new McpTool() {
            @Override
            public ToolSpec getSpec() {
                return override;
            }

            @Override
            public McpToolResponse execute(JsonObject input, String resultVar) {
                return original.execute(input, resultVar);
            }
        };
    }

    @Override
    public List<McpTool> getAllTools() {
        return new ArrayList<>(toolsByName.values());
    }

    @Override
    public McpTool getToolByName(String toolName) {
        return toolsByName.values().stream().filter((t) ->
                toolName.equals(t.getSpec().getName())).findFirst().get();
    }

    /**
     * Liefert alle Tool-Spezifikationen (nur tatsächlich registrierte Tools).
     * Gespeicherte User-Anpassungen werden über registerTool() angewendet,
     * aber nicht mehr registrierte Tools werden nicht reaktiviert.
     */
    public List<ToolSpec> getRegisteredToolSpecs() {
        Map<String, ToolSpec> merged = new LinkedHashMap<>();

        for (McpTool tool : toolsByName.values()) {
            ToolSpec spec = tool.getSpec();
            merged.put(spec.getName(), spec);
        }

        return new ArrayList<>(merged.values());
    }

    /**
     * Entfernt gespeicherte Tool-Definitionen (tools.json), die nicht mehr
     * als registriertes Tool existieren. Sollte nach dem Plugin-Init aufgerufen werden.
     */
    public void cleanupStoredTools() {
        List<ToolSpec> stored = ToolSettingsHelper.loadTools();
        int before = stored.size();
        stored.removeIf(spec -> !toolsByName.containsKey(spec.getName()));
        if (stored.size() < before) {
            ToolSettingsHelper.saveTools(stored);
        }
    }
}
