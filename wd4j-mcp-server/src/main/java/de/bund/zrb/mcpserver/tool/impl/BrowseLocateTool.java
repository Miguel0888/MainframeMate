package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.browser.NodeRef;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.bund.zrb.type.browsingContext.WDLocator;

import java.util.List;

/**
 * Locate elements on the page using various strategies and return NodeRefs.
 */
public class BrowseLocateTool implements McpServerTool {

    @Override
    public String name() {
        return "browse_locate";
    }

    @Override
    public String description() {
        return "Find elements on the page. Returns NodeRefs that can be used with browse_click, browse_type, etc. "
             + "Strategies: 'css' (CSS selector), 'text' (visible text contains), 'aria' (accessible role/name). "
             + "Example: {\"strategy\":\"text\", \"query\":\"Anmelden\"} or {\"strategy\":\"css\", \"query\":\"input[name=q]\"}";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject strategy = new JsonObject();
        strategy.addProperty("type", "string");
        strategy.addProperty("description", "Locate strategy: 'css', 'text', 'aria'");
        props.add("strategy", strategy);

        JsonObject query = new JsonObject();
        query.addProperty("type", "string");
        query.addProperty("description", "The search query (CSS selector, text content, or accessible name)");
        props.add("query", query);

        JsonObject maxResults = new JsonObject();
        maxResults.addProperty("type", "integer");
        maxResults.addProperty("description", "Max number of results (default: 10)");
        props.add("maxResults", maxResults);

        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("query");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String query = params.get("query").getAsString();
        String strategy = params.has("strategy") ? params.get("strategy").getAsString() : "css";
        int maxResults = params.has("maxResults") ? params.get("maxResults").getAsInt() : 10;

        try {
            List<NodeRef> refs;
            switch (strategy.toLowerCase()) {
                case "text":
                    // Firefox doesn't support innerText locator in BiDi yet,
                    // so we use JS-based text search + CSS locateNodes for SharedRefs
                    refs = session.locateByTextAndRegister(query, maxResults);
                    break;
                case "aria":
                    // Try native accessibility locator, fallback to JS
                    refs = session.locateByAriaAndRegister(query, null, maxResults);
                    break;
                case "css":
                default:
                    refs = session.locateAndRegister(new WDLocator.CssLocator(query), maxResults);
                    break;
            }


            if (refs.isEmpty()) {
                return ToolResult.text("No elements found for " + strategy + ":'" + query + "'.\n"
                        + "Try a different strategy or query. Use browse_snapshot to see all interactive elements.");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(refs.size()).append(" element(s):\n");
            for (NodeRef ref : refs) {
                sb.append("  ").append(ref.toCompactString()).append("\n");
            }
            sb.append("\nUse the NodeRef ID (e.g. ").append(refs.get(0).getId())
              .append(") with browse_click or browse_type.");

            return ToolResult.text(sb.toString());
        } catch (Exception e) {
            return ToolResult.error("Locate failed: " + e.getMessage());
        }
    }
}

