package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.research.*;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Navigate one page back in browser history.
 * Uses DOM snapshot (not NetworkIngestionPipeline) for HTML extraction.
 */
public class ResearchBackTool implements McpServerTool {

    private static final Logger LOG = Logger.getLogger(ResearchBackTool.class.getName());

    @Override
    public String name() {
        return "research_back";
    }

    @Override
    public String description() {
        return "Go back one page in browser history. Returns page content and links of the previous page.";
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
        LOG.info("[research_back] Navigating back");
        try {
            session.getDriver().browsingContext().traverseHistory(session.getContextId(), -1);
            session.getNodeRefRegistry().invalidateAll();

            ResearchSession rs = ResearchSessionManager.getInstance().getOrCreate(session);
            rs.invalidateView();

            // Dismiss cookie banners + fetch DOM snapshot
            CookieBannerDismisser.tryDismiss(session);
            String html = DomSnapshotFetcher.fetchHtml(session);
            String currentUrl = DomSnapshotFetcher.fetchCurrentUrl(session);

            if (currentUrl != null) {
                rs.setLastNavigationUrl(currentUrl);
            }

            MenuViewBuilder builder = new MenuViewBuilder(rs);
            builder.setHtmlOverride(html, currentUrl != null ? currentUrl : "");
            MenuView view = builder.build(rs.getMaxMenuItems(), rs.getExcerptMaxLength());

            return ToolResult.text(view.toCompactText());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[research_back] Failed", e);
            return ToolResult.error("Back navigation failed: " + e.getMessage());
        }
    }
}

