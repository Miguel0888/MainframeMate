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
}
