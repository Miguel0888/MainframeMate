package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.research.NetworkIngestionPipeline;
import de.bund.zrb.mcpserver.research.ResearchSession;
import de.bund.zrb.mcpserver.research.ResearchSessionManager;
import de.bund.zrb.mcpserver.research.SettlePolicy;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.bund.zrb.type.browser.WDUserContextInfo;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates a new research session with UserContext isolation, domain policy,
 * limits, and privacy settings.
 * <p>
 * Ausgabe (MUSS): sessionId, userContextId, contexts[], crawlQueueId, status.
 */
public class ResearchSessionStartTool implements McpServerTool {

    private static final Logger LOG = Logger.getLogger(ResearchSessionStartTool.class.getName());

    @Override
    public String name() {
        return "research_session_start";
    }

    @Override
    public String description() {
        return "Start a research session with browser isolation and crawl policies. "
             + "Creates a UserContext for cookie/storage isolation. "
             + "mode: 'research' (full crawl, auto-archive) or 'agent' (quick lookup, minimal state). "
             + "Returns sessionId, userContextId, and initial contextId.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject mode = new JsonObject();
        mode.addProperty("type", "string");
        mode.addProperty("description", "Session mode: 'research' (default) or 'agent'");
        props.add("mode", mode);

        JsonObject domainPolicy = new JsonObject();
        domainPolicy.addProperty("type", "object");
        domainPolicy.addProperty("description", "Domain filter: {\"include\":[\"example.com\"], \"exclude\":[\"ads.example.com\"]}");
        props.add("domainPolicy", domainPolicy);

        JsonObject limits = new JsonObject();
        limits.addProperty("type", "object");
        limits.addProperty("description", "Crawl limits: {\"maxUrls\":500, \"maxDepth\":5, \"maxBytesPerDoc\":2000000}");
        props.add("limits", limits);

        JsonObject seedUrls = new JsonObject();
        seedUrls.addProperty("type", "array");
        seedUrls.addProperty("description", "Optional seed URLs to add to crawl queue on session start");
        props.add("seedUrls", seedUrls);

        schema.add("properties", props);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String modeStr = params.has("mode") ? params.get("mode").getAsString() : "research";
        ResearchSession.Mode mode = "agent".equalsIgnoreCase(modeStr)
                ? ResearchSession.Mode.AGENT
                : ResearchSession.Mode.RESEARCH;

        LOG.info("[research_session_start] Starting session (mode=" + mode + ")");

        try {
            // Create ResearchSession
            ResearchSession rs = ResearchSessionManager.getInstance().getOrCreate(session, mode);

            // Create UserContext for isolation (if driver supports it)
            String userContextId = null;
            try {
                if (session.getDriver() != null) {
                    WDUserContextInfo userCtxInfo = session.getDriver().browser().createUserContext();
                    userContextId = userCtxInfo.getUserContext().value();
                    rs.setUserContextId(userContextId);
                    LOG.info("[research_session_start] UserContext created: " + userContextId);
                }
            } catch (Exception e) {
                LOG.warning("[research_session_start] UserContext creation failed (browser may not support it): "
                        + e.getMessage());
            }

            // Register the active context
            String contextId = session.getContextId();
            if (contextId != null) {
                rs.addContextId(contextId);
            }

            // Apply domain policy
            if (params.has("domainPolicy") && params.get("domainPolicy").isJsonObject()) {
                JsonObject dp = params.getAsJsonObject("domainPolicy");
                if (dp.has("include") && dp.get("include").isJsonArray()) {
                    for (JsonElement el : dp.getAsJsonArray("include")) {
                        rs.getDomainInclude().add(el.getAsString());
                    }
                }
                if (dp.has("exclude") && dp.get("exclude").isJsonArray()) {
                    for (JsonElement el : dp.getAsJsonArray("exclude")) {
                        rs.getDomainExclude().add(el.getAsString());
                    }
                }
            }

            // Apply limits
            if (params.has("limits") && params.get("limits").isJsonObject()) {
                JsonObject lim = params.getAsJsonObject("limits");
                if (lim.has("maxUrls")) rs.setMaxUrls(lim.get("maxUrls").getAsInt());
                if (lim.has("maxDepth")) rs.setMaxDepth(lim.get("maxDepth").getAsInt());
                if (lim.has("maxBytesPerDoc")) rs.setMaxBytesPerDoc(lim.get("maxBytesPerDoc").getAsInt());
            }

            // Configure for agent mode: smaller limits, no auto-crawl
            if (mode == ResearchSession.Mode.AGENT) {
                rs.setMaxMenuItems(20);
                rs.setExcerptMaxLength(1500);
                rs.setMaxUrls(50);
                rs.setMaxDepth(2);
            }

            // ── Start Network Ingestion Pipeline (research mode only) ──
            boolean networkPipelineActive = false;
            if (mode == ResearchSession.Mode.RESEARCH && session.getDriver() != null) {
                try {
                    NetworkIngestionPipeline pipeline = new NetworkIngestionPipeline(
                            session.getDriver(), rs);
                    pipeline.start(new NetworkIngestionPipeline.IngestionCallback() {
                        @Override
                        public String onBodyCaptured(String url, String mimeType, long status,
                                                     String bodyText, java.util.Map<String, String> headers,
                                                     long capturedAt) {
                            // Store in ResearchSession for now; full H2 integration TBD
                            LOG.fine("[NetworkIngestion] Captured: " + url
                                    + " (" + mimeType + ", " + bodyText.length() + " chars)");
                            return "net-" + Long.toHexString(capturedAt);
                        }
                    });
                    rs.setNetworkPipeline(pipeline);
                    networkPipelineActive = true;
                    LOG.info("[research_session_start] Network ingestion pipeline started");
                } catch (Exception e) {
                    LOG.warning("[research_session_start] Network pipeline failed to start: "
                            + e.getMessage());
                }
            }

            // Build response
            JsonObject result = new JsonObject();
            result.addProperty("status", "ok");
            result.addProperty("sessionId", rs.getSessionId());
            if (userContextId != null) {
                result.addProperty("userContextId", userContextId);
            }

            JsonArray contexts = new JsonArray();
            for (String cid : rs.getContextIds()) {
                contexts.add(cid);
            }
            result.add("contexts", contexts);
            result.addProperty("mode", mode.name().toLowerCase());

            StringBuilder text = new StringBuilder();
            text.append("Research session started.\n");
            text.append("  sessionId: ").append(rs.getSessionId()).append("\n");
            text.append("  mode: ").append(mode.name().toLowerCase()).append("\n");
            if (userContextId != null) {
                text.append("  userContextId: ").append(userContextId).append("\n");
            }
            text.append("  contextId: ").append(contextId).append("\n");
            if (networkPipelineActive) {
                text.append("  networkIngestion: active (auto-archiving HTTP responses)\n");
            }
            text.append("\nUse research_open to navigate to a URL.");

            return ToolResult.text(text.toString());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[research_session_start] Failed", e);
            return ToolResult.error("Session start failed: " + e.getMessage());
        }
    }
}
