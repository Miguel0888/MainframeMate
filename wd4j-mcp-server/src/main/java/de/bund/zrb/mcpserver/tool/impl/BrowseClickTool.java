package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

/**
 * Click an element identified by a NodeRef ID (e.g. "n3").
 * <p>
 * The bot gets NodeRef IDs from research_navigate responses
 * (interactive elements section). This tool scrolls the element
 * into view and clicks it.
 */
public class BrowseClickTool implements McpServerTool {

    @Override
    public String name() {
        return "web_click";
    }

    @Override
    public String description() {
        return "Click a page element by its NodeRef ID (e.g. 'n3'). "
             + "Use NodeRef IDs from the interactive elements in the research_navigate response.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject ref = new JsonObject();
        ref.addProperty("type", "string");
        ref.addProperty("description", "NodeRef ID of the element to click (e.g. 'n5')");
        props.add("ref", ref);

        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("ref");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        // Accept both 'ref' and common aliases
        String ref = null;
        for (String key : new String[]{"ref", "nodeId", "nodeRef", "id", "element"}) {
            if (params.has(key) && !params.get(key).isJsonNull()) {
                ref = params.get(key).getAsString();
                break;
            }
        }
        if (ref == null || ref.isEmpty()) {
            return ToolResult.error("Missing required parameter 'ref' (NodeRef ID, e.g. 'n1').");
        }

        try {
            session.clickNodeRef(ref);

            // Brief wait for any page update
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}

            return ToolResult.text("Clicked element " + ref
                    + ".\nUse research_navigate with the current URL to see the updated page.");
        } catch (IllegalArgumentException e) {
            return ToolResult.error("NodeRef '" + ref + "' not found. "
                    + "The page may have changed. Use research_navigate to get current NodeRef IDs.");
        } catch (Exception e) {
            return ToolResult.error("Click failed: " + e.getMessage());
        }
    }
}
