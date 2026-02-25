package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.research.ResearchSession;
import de.bund.zrb.mcpserver.research.ResearchSessionManager;
import de.bund.zrb.mcpserver.research.SettlePolicy;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

import java.util.logging.Logger;

/**
 * Allows the bot to update session configuration during a research session:
 * domain policy, limits, capture filters, privacy rules, settle defaults.
 */
public class ResearchConfigUpdateTool implements McpServerTool {

    private static final Logger LOG = Logger.getLogger(ResearchConfigUpdateTool.class.getName());

    @Override
    public String name() {
        return "research_config_update";
    }

    @Override
    public String description() {
        return "Update research session configuration: domain policy (include/exclude), "
             + "crawl limits, settle defaults, excerpt length. "
             + "Changes take effect immediately for subsequent tool calls.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject dp = new JsonObject();
        dp.addProperty("type", "object");
        dp.addProperty("description", "Domain filter: {\"include\":[\"example.com\"], \"exclude\":[\"ads.example.com\"]}");
        props.add("domainPolicy", dp);

        JsonObject limits = new JsonObject();
        limits.addProperty("type", "object");
        limits.addProperty("description", "Limits: {\"maxUrls\":500, \"maxDepth\":5, \"maxBytesPerDoc\":2000000}");
        props.add("limits", limits);

        JsonObject settle = new JsonObject();
        settle.addProperty("type", "string");
        settle.addProperty("description", "Default settle policy: NAVIGATION, DOM_QUIET, NETWORK_QUIET");
        props.add("defaultSettlePolicy", settle);

        JsonObject maxMenu = new JsonObject();
        maxMenu.addProperty("type", "integer");
        maxMenu.addProperty("description", "Max menu items per view (default: 50)");
        props.add("maxMenuItems", maxMenu);

        JsonObject excLen = new JsonObject();
        excLen.addProperty("type", "integer");
        excLen.addProperty("description", "Max excerpt length in characters (default: 3000)");
        props.add("excerptMaxLength", excLen);

        schema.add("properties", props);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        try {
            ResearchSession rs = ResearchSessionManager.getInstance().getOrCreate(session);
            StringBuilder changes = new StringBuilder("Configuration updated:\n");

            // Domain policy
            if (params.has("domainPolicy") && params.get("domainPolicy").isJsonObject()) {
                JsonObject dp = params.getAsJsonObject("domainPolicy");
                if (dp.has("include") && dp.get("include").isJsonArray()) {
                    rs.getDomainInclude().clear();
                    for (JsonElement el : dp.getAsJsonArray("include")) {
                        rs.getDomainInclude().add(el.getAsString());
                    }
                    changes.append("  domainInclude: ").append(rs.getDomainInclude()).append("\n");
                }
                if (dp.has("exclude") && dp.get("exclude").isJsonArray()) {
                    rs.getDomainExclude().clear();
                    for (JsonElement el : dp.getAsJsonArray("exclude")) {
                        rs.getDomainExclude().add(el.getAsString());
                    }
                    changes.append("  domainExclude: ").append(rs.getDomainExclude()).append("\n");
                }
            }

            // Limits
            if (params.has("limits") && params.get("limits").isJsonObject()) {
                JsonObject lim = params.getAsJsonObject("limits");
                if (lim.has("maxUrls")) {
                    rs.setMaxUrls(lim.get("maxUrls").getAsInt());
                    changes.append("  maxUrls: ").append(rs.getMaxUrls()).append("\n");
                }
                if (lim.has("maxDepth")) {
                    rs.setMaxDepth(lim.get("maxDepth").getAsInt());
                    changes.append("  maxDepth: ").append(rs.getMaxDepth()).append("\n");
                }
                if (lim.has("maxBytesPerDoc")) {
                    rs.setMaxBytesPerDoc(lim.get("maxBytesPerDoc").getAsInt());
                    changes.append("  maxBytesPerDoc: ").append(rs.getMaxBytesPerDoc()).append("\n");
                }
            }

            // Settle policy default
            if (params.has("defaultSettlePolicy")) {
                SettlePolicy sp = SettlePolicy.fromString(params.get("defaultSettlePolicy").getAsString());
                rs.setDefaultSettlePolicy(sp);
                changes.append("  defaultSettlePolicy: ").append(sp).append("\n");
            }

            // Menu items
            if (params.has("maxMenuItems")) {
                rs.setMaxMenuItems(params.get("maxMenuItems").getAsInt());
                changes.append("  maxMenuItems: ").append(rs.getMaxMenuItems()).append("\n");
            }

            // Excerpt length
            if (params.has("excerptMaxLength")) {
                rs.setExcerptMaxLength(params.get("excerptMaxLength").getAsInt());
                changes.append("  excerptMaxLength: ").append(rs.getExcerptMaxLength()).append("\n");
            }

            LOG.info("[research_config_update] " + changes);
            return ToolResult.text(changes.toString());
        } catch (Exception e) {
            LOG.warning("[research_config_update] Failed: " + e.getMessage());
            return ToolResult.error("Config update failed: " + e.getMessage());
        }
    }
}
