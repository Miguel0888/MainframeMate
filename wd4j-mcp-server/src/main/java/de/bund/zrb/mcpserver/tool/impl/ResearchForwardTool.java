package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.research.*;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Navigate one step forward in browser history.
 * Returns a fresh menu view after the navigation action.
 */
public class ResearchForwardTool implements McpServerTool {

    private static final Logger LOG = Logger.getLogger(ResearchForwardTool.class.getName());

    @Override
    public String name() {
        return "research_forward";
    }

    @Override
    public String description() {
        return "Go forward one page in browser history. "
             + "Returns updated page content and link list.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        LOG.info("[research_forward] Going forward in history");
        try {
            session.getDriver().browsingContext().traverseHistory(session.getContextId(), 1);

            session.getNodeRefRegistry().invalidateAll();
            ResearchSession rs = ResearchSessionManager.getInstance().getOrCreate(session);
            rs.invalidateView();

            // Wait for history navigation to settle
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            // Get HTML from the NetworkIngestionPipeline cache (no evaluate!)
            NetworkIngestionPipeline pipeline = rs.getNetworkPipeline();
            String html = pipeline != null ? pipeline.getLastNavigationHtml() : null;
            String currentUrl = pipeline != null ? pipeline.getLastNavigationUrl() : null;

            if (currentUrl != null) {
                rs.setLastNavigationUrl(currentUrl);
            }

            MenuViewBuilder builder = new MenuViewBuilder(rs, null);
            builder.setHtmlOverride(html, currentUrl != null ? currentUrl : "");
            MenuView view = builder.build(rs.getMaxMenuItems(), rs.getExcerptMaxLength());

            return ToolResult.text(view.toCompactText());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[research_forward] Failed", e);
            return ToolResult.error("Forward navigation failed: " + e.getMessage());
        }
    }
}

