package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

/**
 * Click an element identified by a NodeRef ID.
 */
public class BrowseClickTool implements McpServerTool {

    @Override
    public String name() {
        return "web_click";
    }

    @Override
    public String description() {
        return "Click an element by its NodeRef ID (e.g. 'n1'). "
             + "Get NodeRef IDs from web_snapshot or web_locate first.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject ref = new JsonObject();
        ref.addProperty("type", "string");
        ref.addProperty("description", "NodeRef ID (e.g. 'n1', 'n5')");
        props.add("ref", ref);

        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("ref");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        // Accept both 'ref' and common aliases like 'nodeId', 'nodeRef', 'id'
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

            // Brief wait for potential DOM changes
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}

            // Get current URL (may have changed)
            String urlScript = "window.location.href";
            String url = "";
            try {
                Object result = session.evaluate(urlScript, true);
                if (result instanceof de.bund.zrb.type.script.WDEvaluateResult.WDEvaluateResultSuccess) {
                    url = ((de.bund.zrb.type.script.WDEvaluateResult.WDEvaluateResultSuccess) result)
                            .getResult().asString();
                }
            } catch (Exception ignored) {}

            return ToolResult.text("Clicked " + ref + ". Current URL: " + url
                    + "\nUse web_read_page to read the page content, or web_snapshot to see interactive elements.");
        } catch (Exception e) {
            return ToolResult.error("Click failed: " + e.getMessage());
        }
    }
}

