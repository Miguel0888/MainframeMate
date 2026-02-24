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

            // Wait for page to stabilize, then take snapshot
            // Retry up to 3 times if no elements found (page may still be loading)
            BrowseSnapshotTool snapshotTool = new BrowseSnapshotTool();
            JsonObject snapshotParams = new JsonObject();
            ToolResult snapshotResult = null;

            for (int attempt = 0; attempt < 3; attempt++) {
                try { Thread.sleep(attempt == 0 ? 500 : 1000); } catch (InterruptedException ignored) {}

                snapshotResult = snapshotTool.execute(snapshotParams, session);
                if (snapshotResult != null && !snapshotResult.isError()) {
                    String text = snapshotResult.getText();
                    // Check if we found at least some elements
                    if (text.contains("[n")) {
                        break;
                    }
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Navigated to: ").append(finalUrl != null ? finalUrl : url).append("\n\n");
            if (snapshotResult != null && !snapshotResult.isError()) {
                sb.append(snapshotResult.getText());
            }

            return ToolResult.text(sb.toString());
        } catch (Exception e) {
            return ToolResult.error("Navigation failed: " + e.getMessage());
        }
    }

}

