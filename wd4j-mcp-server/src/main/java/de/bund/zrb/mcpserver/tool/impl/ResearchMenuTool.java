package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.research.*;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Returns the current menu view for the active page without navigating.
 * <p>
 * Ausgabe (MUSS): viewToken, url, title, excerpt, menuItems[], newArchivedDocs[].
 * <p>
 * The bot calls this after a stale viewToken error, or anytime a fresh view is needed.
 */
public class ResearchMenuTool implements McpServerTool {

    private static final Logger LOG = Logger.getLogger(ResearchMenuTool.class.getName());

    @Override
    public String name() {
        return "research_menu";
    }

    @Override
    public String description() {
        return "Get the current menu view for the active page. "
             + "Returns: viewToken, page excerpt, menu items (links/buttons), "
             + "and IDs of documents archived since last call. "
             + "Call this after research_choose returns a stale viewToken error.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject selector = new JsonObject();
        selector.addProperty("type", "string");
        selector.addProperty("description", "Optional CSS selector to scope the menu to a section");
        props.add("selector", selector);

        JsonObject sessionId = new JsonObject();
        sessionId.addProperty("type", "string");
        sessionId.addProperty("description", "Session ID (optional)");
        props.add("sessionId", sessionId);

        JsonObject contextId = new JsonObject();
        contextId.addProperty("type", "string");
        contextId.addProperty("description", "BrowsingContext ID (optional, uses active tab)");
        props.add("contextId", contextId);

        schema.add("properties", props);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        LOG.fine("[research_menu] Building menu view");

        try {
            ResearchSession rs = ResearchSessionManager.getInstance().getOrCreate(session);
            MenuViewBuilder builder = new MenuViewBuilder(session, rs);
            MenuView view = builder.build(rs.getMaxMenuItems(), rs.getExcerptMaxLength());

            // Append newly archived doc IDs
            List<String> newDocs = rs.drainNewArchivedDocIds();
            StringBuilder sb = new StringBuilder(view.toCompactText());
            if (!newDocs.isEmpty()) {
                sb.append("\n── Newly archived (").append(newDocs.size()).append(") ──\n");
                for (String docId : newDocs) {
                    sb.append("  ").append(docId).append("\n");
                }
            }

            return ToolResult.text(sb.toString());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[research_menu] Failed to build menu view", e);
            return ToolResult.error("Failed to build menu view: " + e.getMessage());
        }
    }
}
