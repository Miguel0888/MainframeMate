package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.browser.NodeRef;
import de.bund.zrb.mcpserver.browser.NodeRefRegistry;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.bund.zrb.type.browsingContext.WDLocator;

import java.util.List;

/**
 * Takes a snapshot of the page and registers interactive elements as NodeRefs.
 * The bot can then use NodeRef IDs (n1, n2, …) in subsequent actions.
 */
public class BrowseSnapshotTool implements McpServerTool {

    @Override
    public String name() {
        return "web_snapshot";
    }

    @Override
    public String description() {
        return "Get a compact text snapshot of the current page with interactive elements registered as NodeRefs (n1, n2, …). "
             + "Use the returned NodeRef IDs in web_click, web_type, etc. "
             + "Modes: 'interactive' (default, only clickable/input elements), 'full' (all visible text + elements).";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject mode = new JsonObject();
        mode.addProperty("type", "string");
        mode.addProperty("description", "Snapshot mode: 'interactive' (default) or 'full'");
        props.add("mode", mode);

        JsonObject selector = new JsonObject();
        selector.addProperty("type", "string");
        selector.addProperty("description", "Optional CSS selector to scope the snapshot to a subtree");
        props.add("selector", selector);

        schema.add("properties", props);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String mode = params.has("mode") ? params.get("mode").getAsString() : "interactive";
        String selector = params.has("selector") ? params.get("selector").getAsString() : null;

        try {
            // Clear previous refs and get new snapshot version
            session.getNodeRefRegistry().invalidateAll();
            int version = session.getNodeRefRegistry().getSnapshotVersion();

            // Locate all interactive elements and register them
            String cssSelector = selector != null ? selector + " " : "";
            String interactiveSelector = cssSelector
                    + "a, button, input, select, textarea, "
                    + "[role=button], [role=link], [role=tab], [role=menuitem], [role=checkbox], [role=radio], "
                    + "[onclick], [contenteditable=true]";

            List<NodeRef> refs = session.locateAndRegister(
                    new WDLocator.CssLocator(interactiveSelector), 200);

            // Enrich NodeRefs with tag, text, aria-label via JS
            session.enrichNodeRefsViaJs(refs);

            // Build text output
            StringBuilder sb = new StringBuilder();

            // Page title and URL
            String titleScript = "(function(){return document.title + '|' + window.location.href;})()";
            String titleResult = evalString(session, titleScript);
            String[] parts = titleResult.split("\\|", 2);
            sb.append("Page: ").append(parts[0]).append("\n");
            if (parts.length > 1) sb.append("URL: ").append(parts[1]).append("\n");
            sb.append("Snapshot: v").append(version).append(" (").append(refs.size()).append(" interactive elements)\n\n");

            // List interactive elements with NodeRefs
            sb.append("Interactive elements:\n");
            for (NodeRef ref : refs) {
                sb.append("  ").append(ref.toCompactString()).append("\n");
            }

            // If full mode, add page text
            if ("full".equals(mode)) {
                String textScript = selector != null
                        ? "(function(){var el=document.querySelector('" + escapeJs(selector) + "');return el?el.innerText.substring(0,3000):'(not found)';})()"
                        : "(document.body?document.body.innerText:'').substring(0,3000)";
                String text = evalString(session, textScript);
                sb.append("\nPage text:\n").append(text);
            }

            return ToolResult.text(sb.toString());
        } catch (Exception e) {
            return ToolResult.error("Snapshot failed: " + e.getMessage());
        }
    }


    private String evalString(BrowserSession session, String script) {
        try {
            Object result = session.evaluate(script, true);
            if (result instanceof de.bund.zrb.type.script.WDEvaluateResult.WDEvaluateResultSuccess) {
                return ((de.bund.zrb.type.script.WDEvaluateResult.WDEvaluateResultSuccess) result)
                        .getResult().asString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String escapeJs(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}

