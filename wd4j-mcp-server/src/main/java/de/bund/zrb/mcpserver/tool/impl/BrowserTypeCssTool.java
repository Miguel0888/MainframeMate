package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

/**
 * Types text into an element identified by a CSS selector.
 */
public class BrowserTypeCssTool implements McpServerTool {

    @Override
    public String name() {
        return "browser_type_css";
    }

    @Override
    public String description() {
        return "Type text into an input element identified by a CSS selector.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject selector = new JsonObject();
        selector.addProperty("type", "string");
        selector.addProperty("description", "CSS selector of the input element");
        props.add("selector", selector);

        JsonObject text = new JsonObject();
        text.addProperty("type", "string");
        text.addProperty("description", "The text to type into the element");
        props.add("text", text);

        JsonObject clearFirst = new JsonObject();
        clearFirst.addProperty("type", "boolean");
        clearFirst.addProperty("description", "Clear existing value before typing (default: false)");
        props.add("clearFirst", clearFirst);

        JsonObject ctxId = new JsonObject();
        ctxId.addProperty("type", "string");
        ctxId.addProperty("description", "Browsing context ID (optional, uses default)");
        props.add("contextId", ctxId);

        schema.add("properties", props);

        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("selector");
        required.add("text");
        schema.add("required", required);

        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String selector = params.get("selector").getAsString();
        String text = params.get("text").getAsString();
        boolean clearFirst = params.has("clearFirst") && params.get("clearFirst").getAsBoolean();
        String ctxId = params.has("contextId") ? params.get("contextId").getAsString() : null;

        try {
            session.typeIntoElement(selector, text, clearFirst, ctxId);
            return ToolResult.text("Typed text into: " + selector);
        } catch (Exception e) {
            return ToolResult.error("Type failed: " + e.getMessage());
        }
    }
}

