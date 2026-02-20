package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonObject;
import de.bund.zrb.command.response.WDBrowsingContextResult;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

/**
 * Navigates the browser to a URL.
 */
public class BrowserNavigateTool implements McpServerTool {

    @Override
    public String name() {
        return "browser_navigate";
    }

    @Override
    public String description() {
        return "Navigate the browser to a given URL.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject url = new JsonObject();
        url.addProperty("type", "string");
        url.addProperty("description", "The URL to navigate to");
        props.add("url", url);

        JsonObject ctxId = new JsonObject();
        ctxId.addProperty("type", "string");
        ctxId.addProperty("description", "Browsing context ID (optional, uses default)");
        props.add("contextId", ctxId);

        schema.add("properties", props);

        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("url");
        schema.add("required", required);

        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String url = params.get("url").getAsString();
        String ctxId = params.has("contextId") ? params.get("contextId").getAsString() : null;

        try {
            WDBrowsingContextResult.NavigateResult result = session.navigate(url, ctxId);
            String finalUrl = result.getUrl();
            return ToolResult.text("Navigated to: " + (finalUrl != null ? finalUrl : url));
        } catch (Exception e) {
            return ToolResult.error("Navigation failed: " + e.getMessage());
        }
    }
}

