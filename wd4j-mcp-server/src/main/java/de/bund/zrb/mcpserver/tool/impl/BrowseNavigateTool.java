package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.command.response.WDBrowsingContextResult;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

/**
 * Navigate to a URL and return a compact page snapshot.
 * Invalidates all existing NodeRefs.
 */
public class BrowseNavigateTool implements McpServerTool {

    @Override
    public String name() {
        return "web_navigate";
    }

    @Override
    public String description() {
        return "Navigate to a URL in the browser. Returns the page title, final URL, and a compact snapshot of interactive elements. "
             + "All previous NodeRefs are invalidated after navigation.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject url = new JsonObject();
        url.addProperty("type", "string");
        url.addProperty("description", "URL to navigate to");
        props.add("url", url);

        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("url");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String url = params.get("url").getAsString();
        try {
            // Invalidate old refs
            session.getNodeRefRegistry().invalidateAll();

            WDBrowsingContextResult.NavigateResult nav = session.navigate(url);
            String finalUrl = nav.getUrl();

            // Wait briefly for page to stabilize
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}

            // Use snapshot tool to get interactive elements with NodeRefs
            JsonObject snapshotParams = new JsonObject();
            BrowseSnapshotTool snapshotTool = new BrowseSnapshotTool();
            ToolResult snapshotResult = snapshotTool.execute(snapshotParams, session);

            StringBuilder sb = new StringBuilder();
            sb.append("Navigated to: ").append(finalUrl != null ? finalUrl : url).append("\n\n");
            if (snapshotResult != null && !snapshotResult.isError()) {
                // Append the snapshot content (which includes NodeRefs)
                sb.append(snapshotResult.getText());
            }

            return ToolResult.text(sb.toString());
        } catch (Exception e) {
            return ToolResult.error("Navigation failed: " + e.getMessage());
        }
    }

}

