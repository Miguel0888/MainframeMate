package de.zrb.bund.newApi.mcp;

import com.google.gson.JsonObject;

/**
 * Base interface for all callable tools used via the MCP protocol.
 */
public interface McpTool {

    /**
     * Returns the full specification of this tool, including its name,
     * description, input parameters and how it should be invoked by the model.
     */
    ToolSpec getSpec();

    /**
     * Executes the tool logic with the provided JSON input.
     * The structure of the input must match the parameter specification in {@link ToolSpec#getParameters()}.
     *
     * HINWEIS:
     * Hauptsächlich dafür zuständig, den JSON-Input auf die toolspezifischen POJOs zur Konfiguration (UI- Eingabemasken) zu mappen
     */
    McpToolResponse execute(JsonObject input, String resultVar);

    /**
     * Returns the default configuration for this tool as a Gson-serializable ToolConfig object.
     * The default implementation returns an empty ToolConfig (no custom settings).
     * Plugins can override this to return a subclass of ToolConfig with tool-specific fields.
     */
    default ToolConfig getDefaultConfig() {
        return new ToolConfig();
    }

    /**
     * Returns the concrete ToolConfig class used by this tool.
     * This is needed for proper Gson deserialization from persisted JSON.
     * Override this if you use a custom ToolConfig subclass.
     */
    default Class<? extends ToolConfig> getConfigClass() {
        return ToolConfig.class;
    }
}
