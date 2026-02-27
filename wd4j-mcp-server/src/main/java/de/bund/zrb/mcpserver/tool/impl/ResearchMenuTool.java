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
        return "Refresh the current page's link list without navigating. "
             + "Returns: page title, excerpt, links, and archived document IDs. "
             + "Use research_navigate with a URL from the list to continue browsing.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        schema.add("properties", props);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        LOG.fine("[research_menu] Building menu view");

        try {
            ResearchSession rs = ResearchSessionManager.getInstance().getOrCreate(session);

            // Fetch fresh DOM snapshot for the current page
            String html = DomSnapshotFetcher.fetchHtml(session, 0);
            String currentUrl = DomSnapshotFetcher.fetchCurrentUrl(session);

            MenuViewBuilder builder = new MenuViewBuilder(rs);
            builder.setHtmlOverride(html, currentUrl != null ? currentUrl : "");
            MenuView view = builder.build(rs.getMaxMenuItems(), rs.getExcerptMaxLength());

            // Append newly archived doc IDs
            List<String> newDocs = rs.drainNewArchivedDocIds();
            StringBuilder sb = new StringBuilder(view.toCompactText());
            if (!newDocs.isEmpty()) {
                sb.append("\n── Archiviert (").append(newDocs.size()).append(") ──\n");
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
