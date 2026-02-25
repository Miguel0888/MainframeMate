package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.research.*;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Returns the current menu view for the active page without navigating.
 * The bot calls this to get an updated list of interactive elements
 * after the viewToken has become stale.
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
             + "Returns: viewToken, page excerpt, and menu items (links/buttons). "
             + "Call this after research_choose returns a stale viewToken error, "
             + "or anytime you need a fresh view of the page.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject selector = new JsonObject();
        selector.addProperty("type", "string");
        selector.addProperty("description", "Optional CSS selector to scope the menu to a page section");
        props.add("selector", selector);

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

            return ToolResult.text(view.toCompactText());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[research_menu] Failed to build menu view", e);
            return ToolResult.error("Failed to build menu view: " + e.getMessage());
        }
    }
}

