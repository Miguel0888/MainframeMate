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
             + "Call this ONCE at the beginning – do NOT call again. "
             + "If already started, it returns the existing session. "
             + "After this, use research_open to navigate to a URL. "
             + "mode: 'research' (default, full crawl) or 'agent' (quick lookup). "
             + "includeDomains: comma-separated domains to include (e.g. 'yahoo.com,reuters.com'). "
             + "excludeDomains: comma-separated domains to exclude. "
             + "maxUrls: max URLs to crawl (default 500). maxDepth: max crawl depth (default 5).";
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

        // Flat string parameters instead of nested objects to avoid LLM JSON-parse issues
        JsonObject includeDomains = new JsonObject();
        includeDomains.addProperty("type", "string");
        includeDomains.addProperty("description", "Comma-separated domains to include, e.g. 'yahoo.com,reuters.com'. Optional.");
        props.add("includeDomains", includeDomains);

        JsonObject excludeDomains = new JsonObject();
        excludeDomains.addProperty("type", "string");
        excludeDomains.addProperty("description", "Comma-separated domains to exclude, e.g. 'ads.example.com'. Optional.");
        props.add("excludeDomains", excludeDomains);

        JsonObject maxUrls = new JsonObject();
        maxUrls.addProperty("type", "integer");
        maxUrls.addProperty("description", "Max URLs to crawl (default: 500). Optional.");
        props.add("maxUrls", maxUrls);

        JsonObject maxDepth = new JsonObject();
        maxDepth.addProperty("type", "integer");
        maxDepth.addProperty("description", "Max crawl depth (default: 5). Optional.");
        props.add("maxDepth", maxDepth);

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
            // Check if a session already exists for this browser – return it immediately
            ResearchSession existing = ResearchSessionManager.getInstance().get(session);
            if (existing != null) {
                LOG.info("[research_session_start] Session already exists: " + existing.getSessionId()
                        + " – returning existing session (no new UserContext/Pipeline)");
                return buildSuccessResponse(existing, session.getContextId());
            }

            // Create new ResearchSession
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

            // Apply domain policy (flat string parameters)
            if (params.has("includeDomains") && params.get("includeDomains").isJsonPrimitive()) {
                String includeStr = params.get("includeDomains").getAsString().trim();
                if (!includeStr.isEmpty()) {
                    for (String domain : includeStr.split("[,;\\s]+")) {
                        String d = domain.trim();
                        if (!d.isEmpty()) rs.getDomainInclude().add(d);
                    }
                }
            }
            if (params.has("excludeDomains") && params.get("excludeDomains").isJsonPrimitive()) {
                String excludeStr = params.get("excludeDomains").getAsString().trim();
                if (!excludeStr.isEmpty()) {
                    for (String domain : excludeStr.split("[,;\\s]+")) {
                        String d = domain.trim();
                        if (!d.isEmpty()) rs.getDomainExclude().add(d);
                    }
                }
            }
            // Legacy: also accept nested domainPolicy for backward compatibility
            if (params.has("domainPolicy") && params.get("domainPolicy").isJsonObject()) {
                JsonObject dp = params.getAsJsonObject("domainPolicy");
                if (dp.has("include") && dp.get("include").isJsonArray()) {
                    for (JsonElement el : dp.getAsJsonArray("include")) {
                        if (el.isJsonPrimitive()) rs.getDomainInclude().add(el.getAsString());
                    }
                }
                if (dp.has("exclude") && dp.get("exclude").isJsonArray()) {
                    for (JsonElement el : dp.getAsJsonArray("exclude")) {
                        if (el.isJsonPrimitive()) rs.getDomainExclude().add(el.getAsString());
                    }
                }
            }

            // Apply limits (flat parameters)
            if (params.has("maxUrls") && params.get("maxUrls").isJsonPrimitive()) {
                rs.setMaxUrls(params.get("maxUrls").getAsInt());
            }
            if (params.has("maxDepth") && params.get("maxDepth").isJsonPrimitive()) {
                rs.setMaxDepth(params.get("maxDepth").getAsInt());
            }
            // Legacy: also accept nested limits object
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

            // ── Start Network Ingestion Pipeline (research mode only, once) ──
            if (mode == ResearchSession.Mode.RESEARCH && session.getDriver() != null
                    && rs.getNetworkPipeline() == null) {
                try {
                    NetworkIngestionPipeline pipeline = new NetworkIngestionPipeline(
                            session.getDriver(), rs);

                    // Use global callback (registered by WebSearchPlugin) or fallback
                    NetworkIngestionPipeline.IngestionCallback cb =
                            NetworkIngestionPipeline.getGlobalDefaultCallback();
                    if (cb == null) {
                        // Fallback: logging only (no persistence)
                        cb = new NetworkIngestionPipeline.IngestionCallback() {
                            @Override
                            public String onBodyCaptured(String url, String mimeType, long status,
                                                         String bodyText, java.util.Map<String, String> headers,
                                                         long capturedAt) {
                                LOG.fine("[NetworkIngestion] Captured (no persister): " + url
                                        + " (" + mimeType + ", " + bodyText.length() + " chars)");
                                return "net-" + Long.toHexString(capturedAt);
                            }
                        };
                    }

                    pipeline.start(cb);
                    rs.setNetworkPipeline(pipeline);
                    LOG.info("[research_session_start] Network ingestion pipeline started");
                } catch (Exception e) {
                    LOG.warning("[research_session_start] Network pipeline failed to start: "
                            + e.getMessage());
                }
            }

            return buildSuccessResponse(rs, session.getContextId());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[research_session_start] Failed", e);
            return ToolResult.error("Session start failed: " + e.getMessage());
        }
    }

    /**
     * Build a consistent success response for an existing or new session.
     */
    private ToolResult buildSuccessResponse(ResearchSession rs, String contextId) {
        boolean hasPipeline = rs.getNetworkPipeline() != null && rs.getNetworkPipeline().isActive();

        StringBuilder text = new StringBuilder();
        text.append("Research session started.\n");
        text.append("  sessionId: ").append(rs.getSessionId()).append("\n");
        text.append("  mode: ").append(rs.getMode().name().toLowerCase()).append("\n");
        if (rs.getUserContextId() != null) {
            text.append("  userContextId: ").append(rs.getUserContextId()).append("\n");
        }
        text.append("  contextId: ").append(contextId).append("\n");
        if (hasPipeline) {
            text.append("  networkIngestion: active (auto-archiving HTTP responses)\n");
        }
        text.append("\nSession is ready. NEXT STEP: Call research_open with a URL.\n");
        text.append("Example: {\"name\":\"research_open\",\"input\":{\"url\":\"https://search.yahoo.com/search?p=wirtschaft\"}}\n");
        text.append("STOP: Do NOT call research_session_start again.");

        return ToolResult.text(text.toString());
    }
}
