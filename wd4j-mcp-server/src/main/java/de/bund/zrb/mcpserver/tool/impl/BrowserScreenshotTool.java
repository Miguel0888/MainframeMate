package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

/**
 * Captures a screenshot of the current page as Base64 PNG.
 */
public class BrowserScreenshotTool implements McpServerTool {

    @Override
    public String name() {
        return "browser_screenshot";
    }

    @Override
    public String description() {
        return "Capture a screenshot of the current browser page. Returns a Base64-encoded PNG image.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject ctxId = new JsonObject();
        ctxId.addProperty("type", "string");
        ctxId.addProperty("description", "Browsing context ID (optional, uses default)");
        props.add("contextId", ctxId);

        schema.add("properties", props);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String ctxId = params.has("contextId") ? params.get("contextId").getAsString() : null;

        try {
            String base64Png = session.captureScreenshot(ctxId);
            return ToolResult.imageWithText(base64Png, "image/png", "Screenshot captured.");
        } catch (Exception e) {
            return ToolResult.error("Screenshot failed: " + e.getMessage());
        }
    }
}

