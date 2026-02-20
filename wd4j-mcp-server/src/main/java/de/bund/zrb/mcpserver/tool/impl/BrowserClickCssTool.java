package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

/**
 * Clicks an element identified by a CSS selector.
 */
public class BrowserClickCssTool implements McpServerTool {

    @Override
    public String name() {
        return "browser_click_css";
    }

    @Override
    public String description() {
        return "Click an element on the page identified by a CSS selector.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject selector = new JsonObject();
        selector.addProperty("type", "string");
        selector.addProperty("description", "CSS selector of the element to click");
        props.add("selector", selector);

        JsonObject ctxId = new JsonObject();
        ctxId.addProperty("type", "string");
        ctxId.addProperty("description", "Browsing context ID (optional, uses default)");
        props.add("contextId", ctxId);

        schema.add("properties", props);

        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("selector");
        schema.add("required", required);

        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String selector = params.get("selector").getAsString();
        String ctxId = params.has("contextId") ? params.get("contextId").getAsString() : null;

        try {
            session.clickElement(selector, ctxId);
            return ToolResult.text("Clicked element: " + selector);
        } catch (Exception e) {
            return ToolResult.error("Click failed: " + e.getMessage());
        }
    }
}

