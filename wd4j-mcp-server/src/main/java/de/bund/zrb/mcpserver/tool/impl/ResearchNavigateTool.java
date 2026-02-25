package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.command.response.WDBrowsingContextResult;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.research.*;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.bund.zrb.type.browsingContext.WDReadinessState;

import de.bund.zrb.type.browser.WDUserContextInfo;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Unified navigation tool – the ONLY tool the bot needs for browsing.
 * <p>
 * Accepts ONE parameter: {@code target}. The target can be:
 * <ul>
 *   <li>An absolute URL: {@code https://de.yahoo.com/nachrichten/...}</li>
 *   <li>A relative path: {@code /nachrichten/politik/} (resolved against current page)</li>
 *   <li>A menu item ID: {@code m3} (follows link from current page's link list)</li>
 *   <li>A history action: {@code back}, {@code forward}, {@code reload}</li>
 * </ul>
 * <p>
 * Returns: page title, text excerpt, numbered link list, archived doc IDs, network traffic.
 * The bot picks a link from the list and calls this tool again with that link's ID or URL.
 * <p>
 * Replaces: research_open, research_choose, research_navigate (back/forward).
 */
public class ResearchNavigateTool implements McpServerTool {

    private static final Logger LOG = Logger.getLogger(ResearchNavigateTool.class.getName());

    @Override
    public String name() {
        return "research_navigate";
    }

    @Override
    public String description() {
        return "Navigate to a target. "
             + "The target can be: an absolute URL (https://...), "
             + "a relative path (/path/page), "
             + "a link ID from the current page (m0, m3, m17), "
             + "or a history action (back, forward, reload). "
             + "Returns: page title, excerpt, numbered link list. "
             + "Pick a link ID or URL from the response to navigate further.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject target = new JsonObject();
        target.addProperty("type", "string");
        target.addProperty("description",
                "Where to go: absolute URL, relative path, link ID (m0..mN), or action (back/forward/reload)");
        props.add("target", target);

        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("target");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String target = extractTarget(params);
        if (target == null || target.trim().isEmpty()) {
            return ToolResult.error("Missing required parameter 'target'.");
        }
        target = target.trim();

        // ── Classify the target ──
        if (isHistoryAction(target)) {
            return handleHistoryAction(target, session);
        }
        if (isMenuItemId(target)) {
            return handleMenuItemNavigation(target, session);
        }
        // URL (absolute or relative)
        return handleUrlNavigation(target, session);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Target classification
    // ═══════════════════════════════════════════════════════════════

    private boolean isHistoryAction(String target) {
        String lower = target.toLowerCase();
        return "back".equals(lower) || "forward".equals(lower) || "reload".equals(lower);
    }

    private boolean isMenuItemId(String target) {
        // m0, m1, m42, etc.
        return target.matches("^m\\d+$");
    }

    // ═══════════════════════════════════════════════════════════════
    //  History actions (back/forward/reload)
    // ═══════════════════════════════════════════════════════════════

    private ToolResult handleHistoryAction(String action, BrowserSession session) {
        LOG.info("[research_navigate] History action: " + action);
        try {
            switch (action.toLowerCase()) {
                case "back":
                    session.evaluate("window.history.back()", true);
                    break;
                case "forward":
                    session.evaluate("window.history.forward()", true);
                    break;
                case "reload":
                    session.evaluate("window.location.reload()", true);
                    break;
            }

            session.getNodeRefRegistry().invalidateAll();
            ResearchSession rs = ensureSession(session);
            rs.invalidateView();

            NetworkIngestionPipeline pipeline = rs.getNetworkPipeline();
            ensurePipeline(rs, session);
            pipeline = rs.getNetworkPipeline();

            MenuViewBuilder builder = new MenuViewBuilder(rs, pipeline);
            MenuView view = builder.buildWithSettle(SettlePolicy.NAVIGATION,
                    rs.getMaxMenuItems(), rs.getExcerptMaxLength());

            return buildResponse(view, rs, pipeline);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[research_navigate] History action failed", e);
            return ToolResult.error("Action '" + action + "' failed: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Menu item navigation (m0, m3, etc.)
    // ═══════════════════════════════════════════════════════════════

    private ToolResult handleMenuItemNavigation(String menuItemId, BrowserSession session) {
        LOG.info("[research_navigate] Follow menu item: " + menuItemId);
        try {
            ResearchSession rs = ensureSession(session);
            MenuView currentView = rs.getCurrentMenuView();

            if (currentView == null || currentView.getMenuItems().isEmpty()) {
                return ToolResult.error("No link list available. Navigate to a URL first.");
            }

            // Find the menu item
            MenuItem chosen = null;
            for (MenuItem item : currentView.getMenuItems()) {
                if (menuItemId.equals(item.getMenuItemId())) {
                    chosen = item;
                    break;
                }
            }

            if (chosen == null) {
                return ToolResult.error("Unknown link '" + menuItemId + "'. "
                        + "Valid: m0 to m" + (currentView.getMenuItems().size() - 1) + ".");
            }

            String url = chosen.getHref();
            if (url == null || url.isEmpty()) {
                return ToolResult.error("Link '" + menuItemId + "' (" + chosen.getLabel() + ") has no URL.");
            }

            LOG.info("[research_navigate] → " + url + " (label: " + chosen.getLabel() + ")");
            return navigateToUrl(url, rs, session);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[research_navigate] Menu item navigation failed", e);
            return ToolResult.error("Navigation failed: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  URL navigation (absolute or relative)
    // ═══════════════════════════════════════════════════════════════

    private ToolResult handleUrlNavigation(String target, BrowserSession session) {
        try {
            ResearchSession rs = ensureSession(session);
            String url = resolveUrl(target, rs);

            LOG.info("[research_navigate] Navigate to: " + url + (url.equals(target) ? "" : " (resolved from: " + target + ")"));
            return navigateToUrl(url, rs, session);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[research_navigate] URL navigation failed", e);
            return ToolResult.error("Navigation failed: " + e.getMessage());
        }
    }

    /**
     * Resolve a target to an absolute URL.
     * <ul>
     *   <li>Absolute URL (http/https): returned as-is</li>
     *   <li>Starts with '/': resolved against current page's origin</li>
     *   <li>No scheme, no leading '/': treated as relative to current page's path</li>
     * </ul>
     */
    private String resolveUrl(String target, ResearchSession rs) {
        // Already absolute
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return target;
        }

        // Get base URL from current page
        String baseUrl = getCurrentPageUrl(rs);
        if (baseUrl == null) {
            // No current page – try to make it absolute anyway
            if (!target.startsWith("/")) {
                // Looks like a domain without scheme
                return "https://" + target;
            }
            return ToolResult.error("Cannot resolve relative path '" + target
                    + "' – no current page. Use an absolute URL (https://...).").toString();
        }

        try {
            URI base = new URI(baseUrl);
            if (target.startsWith("/")) {
                // Absolute path – resolve against origin
                return base.getScheme() + "://" + base.getAuthority() + target;
            } else {
                // Relative path – resolve against current directory
                return base.resolve(target).toString();
            }
        } catch (Exception e) {
            LOG.warning("[research_navigate] Failed to resolve '" + target + "' against '" + baseUrl + "': " + e.getMessage());
            // Best effort: prepend https://
            return "https://" + target;
        }
    }

    private String getCurrentPageUrl(ResearchSession rs) {
        MenuView view = rs.getCurrentMenuView();
        if (view != null && view.getUrl() != null) {
            return view.getUrl();
        }
        NetworkIngestionPipeline pipeline = rs.getNetworkPipeline();
        if (pipeline != null) {
            return pipeline.getLastNavigationUrl();
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Core navigation
    // ═══════════════════════════════════════════════════════════════

    private ToolResult navigateToUrl(String url, ResearchSession rs, BrowserSession session) {
        // ── Same-URL guard (multi-source check) ──
        String currentUrl = null;

        // Source 1: pipeline's last navigation URL
        NetworkIngestionPipeline pipeline = rs.getNetworkPipeline();
        if (pipeline != null) {
            currentUrl = pipeline.getLastNavigationUrl();
        }

        // Source 2: current MenuView URL (fallback if pipeline was re-created)
        if (currentUrl == null) {
            MenuView view = rs.getCurrentMenuView();
            if (view != null) {
                currentUrl = view.getUrl();
            }
        }

        if (currentUrl != null && isSameUrl(currentUrl, url)) {
            LOG.warning("[research_navigate] BLOCKED: Already on " + currentUrl);
            MenuView existingView = rs.getCurrentMenuView();
            if (existingView != null && existingView.getMenuItems() != null
                    && !existingView.getMenuItems().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("You are ALREADY on this page (").append(url).append("). ")
                  .append("Pick a link from the list below instead:\n\n");
                sb.append(existingView.toCompactText());
                sb.append("\n── Hint ──\n");
                sb.append("Call research_navigate with a link ID (e.g. 'm0') or a DIFFERENT URL.");
                return ToolResult.error(sb.toString());
            }
            return ToolResult.error("Already on this page (" + currentUrl + "). "
                    + "Use a link ID (m0..mN) or navigate to a different URL.");
        }

        // Ensure pipeline
        ensurePipeline(rs, session);
        pipeline = rs.getNetworkPipeline();

        // Clear cache for new navigation
        if (pipeline != null) {
            pipeline.clearNavigationCache();
            pipeline.setLastNavigationUrl(url);
        }

        // ── Execute navigation with timeout ──
        long timeoutSeconds = Long.getLong("websearch.navigate.timeout.seconds", 30);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ToolResult> future = executor.submit(() ->
                    doNavigate(url, rs, session));
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            LOG.severe("[research_navigate] Timeout after " + timeoutSeconds + "s for: " + url);
            // Try to build menu from current page state
            try {
                MenuViewBuilder builder = new MenuViewBuilder(rs, rs.getNetworkPipeline());
                MenuView view = builder.build(rs.getMaxMenuItems(), rs.getExcerptMaxLength());
                if (view != null && view.getMenuItems() != null && !view.getMenuItems().isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("⚠️ Page loading timed out after ").append(timeoutSeconds)
                      .append("s, showing available content.\n\n");
                    sb.append(buildResponseText(view, rs, rs.getNetworkPipeline()));
                    return ToolResult.text(sb.toString());
                }
            } catch (Exception ignored) {}
            session.killBrowserProcess();
            return ToolResult.error("Navigation timeout after " + timeoutSeconds + "s. Browser restarted. Try again.");
        } catch (ExecutionException e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            LOG.warning("[research_navigate] Failed: " + msg);
            return ToolResult.error("Navigation failed: " + msg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error("Navigation interrupted.");
        } finally {
            executor.shutdownNow();
        }
    }

    private ToolResult doNavigate(String url, ResearchSession rs, BrowserSession session) {
        try {
            WDBrowsingContextResult.NavigateResult nav =
                    session.getDriver().browsingContext().navigate(
                            url, session.getContextId(), WDReadinessState.INTERACTIVE);
            String finalUrl = nav.getUrl();
            LOG.info("[research_navigate] Landed on: " + finalUrl);

            NetworkIngestionPipeline pipeline = rs.getNetworkPipeline();
            MenuViewBuilder builder = new MenuViewBuilder(rs, pipeline);
            MenuView view = builder.buildWithSettle(SettlePolicy.NAVIGATION,
                    rs.getMaxMenuItems(), rs.getExcerptMaxLength());

            return buildResponse(view, rs, pipeline);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[research_navigate] doNavigate failed", e);
            return ToolResult.error("Navigation failed: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Response builder
    // ═══════════════════════════════════════════════════════════════

    private ToolResult buildResponse(MenuView view, ResearchSession rs, NetworkIngestionPipeline pipeline) {
        return ToolResult.text(buildResponseText(view, rs, pipeline));
    }

    private String buildResponseText(MenuView view, ResearchSession rs, NetworkIngestionPipeline pipeline) {
        StringBuilder sb = new StringBuilder(view.toCompactText());

        // Archived docs
        List<String> newDocs = rs.drainNewArchivedDocIds();
        if (!newDocs.isEmpty()) {
            sb.append("\n── Newly archived (").append(newDocs.size()).append(") ──\n");
            for (String docId : newDocs) {
                sb.append("  ").append(docId).append("\n");
            }
        }

        // Network traffic
        if (pipeline != null) {
            Map<String, Integer> cats = pipeline.getCategoryCounts();
            if (!cats.isEmpty()) {
                sb.append("\n── Network traffic ──\n");
                for (Map.Entry<String, Integer> e : cats.entrySet()) {
                    sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
                }
            }
        }

        sb.append("\n── Next step ──\n");
        sb.append("Pick a link ID (e.g. 'm0') or provide a URL to navigate further.");

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Session auto-init (replaces ResearchSessionStartTool)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Ensures a fully initialized ResearchSession exists for this BrowserSession.
     * Creates UserContext, RunId, and NetworkIngestionPipeline on first call.
     * Subsequent calls return the existing session immediately.
     */
    private ResearchSession ensureSession(BrowserSession session) {
        ResearchSession rs = ResearchSessionManager.getInstance().get(session);
        if (rs != null && rs.getRunId() != null) {
            // Session fully initialized
            ensurePipeline(rs, session);
            return rs;
        }

        // Create or get session
        rs = ResearchSessionManager.getInstance().getOrCreate(session, ResearchSession.Mode.RESEARCH);

        // Create UserContext for isolation (if driver supports it and not yet created)
        if (rs.getUserContextId() == null) {
            try {
                if (session.getDriver() != null) {
                    WDUserContextInfo userCtxInfo = session.getDriver().browser().createUserContext();
                    String userContextId = userCtxInfo.getUserContext().value();
                    rs.setUserContextId(userContextId);
                    LOG.info("[research_navigate] UserContext created: " + userContextId);
                }
            } catch (Exception e) {
                LOG.fine("[research_navigate] UserContext creation skipped: " + e.getMessage());
            }
        }

        // Register context
        String contextId = session.getContextId();
        if (contextId != null) {
            rs.addContextId(contextId);
        }

        // Create Data Lake Run (if not yet created)
        if (rs.getRunId() == null) {
            try {
                RunLifecycleCallback runCallback = ResearchSessionManager.getRunLifecycleCallback();
                if (runCallback != null) {
                    String runId = runCallback.startRun(rs.getMode().name(), null);
                    rs.setRunId(runId);
                    LOG.info("[research_navigate] Data Lake run created: " + runId);
                } else {
                    rs.setRunId(java.util.UUID.randomUUID().toString());
                    LOG.info("[research_navigate] Using ephemeral runId");
                }
            } catch (Exception e) {
                LOG.warning("[research_navigate] Could not create run: " + e.getMessage());
                rs.setRunId(java.util.UUID.randomUUID().toString());
            }
        }

        // Ensure pipeline is running
        ensurePipeline(rs, session);

        LOG.info("[research_navigate] Session ready: " + rs.getSessionId());
        return rs;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Pipeline management
    // ═══════════════════════════════════════════════════════════════

    private void ensurePipeline(ResearchSession rs, BrowserSession session) {
        NetworkIngestionPipeline pipeline = rs.getNetworkPipeline();
        if (pipeline != null && pipeline.isActive()) return;
        if (session.getDriver() == null) return;

        try {
            NetworkIngestionPipeline newPipeline = new NetworkIngestionPipeline(session.getDriver(), rs);
            NetworkIngestionPipeline.IngestionCallback cb = NetworkIngestionPipeline.getGlobalDefaultCallback();
            if (cb == null) {
                cb = (runId, url, mimeType, status, bodyText, headers, capturedAt) -> {
                    LOG.fine("[NetworkIngestion] Captured (no persister): " + url);
                    return "net-" + Long.toHexString(capturedAt);
                };
            }
            if (rs.getRunId() == null) {
                RunLifecycleCallback runCallback = ResearchSessionManager.getRunLifecycleCallback();
                if (runCallback != null) {
                    rs.setRunId(runCallback.startRun(rs.getMode().name(), null));
                } else {
                    rs.setRunId(java.util.UUID.randomUUID().toString());
                }
            }
            newPipeline.start(cb);
            rs.setNetworkPipeline(newPipeline);
            LOG.info("[research_navigate] Auto-started network ingestion pipeline");
        } catch (Exception e) {
            LOG.warning("[research_navigate] Failed to auto-start pipeline: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  URL comparison
    // ═══════════════════════════════════════════════════════════════

    private boolean isSameUrl(String current, String target) {
        if (current == null || target == null) return false;
        try {
            URI c = new URI(current);
            URI t = new URI(target);
            if (!nullSafeEqualsIgnoreCase(c.getScheme(), t.getScheme())) return false;
            if (!nullSafeEqualsIgnoreCase(c.getHost(), t.getHost())) return false;
            if (effectivePort(c) != effectivePort(t)) return false;
            String cPath = normalizePath(c.getPath());
            String tPath = normalizePath(t.getPath());
            if (!cPath.equals(tPath)) return false;
            String cQuery = c.getQuery() != null ? c.getQuery() : "";
            String tQuery = t.getQuery() != null ? t.getQuery() : "";
            return cQuery.equals(tQuery);
        } catch (Exception e) {
            return normalizeSimple(current).equals(normalizeSimple(target));
        }
    }

    private boolean nullSafeEqualsIgnoreCase(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    private int effectivePort(URI uri) {
        int port = uri.getPort();
        if (port >= 0) return port;
        if ("https".equalsIgnoreCase(uri.getScheme())) return 443;
        if ("http".equalsIgnoreCase(uri.getScheme())) return 80;
        return -1;
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private String normalizeSimple(String url) {
        int hash = url.indexOf('#');
        if (hash >= 0) url = url.substring(0, hash);
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url.toLowerCase();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Param extraction
    // ═══════════════════════════════════════════════════════════════

    private String extractTarget(JsonObject params) {
        // Primary: "target"
        if (params.has("target") && params.get("target").isJsonPrimitive()) {
            return params.get("target").getAsString();
        }
        // Fallback: "url" (backwards compat with old research_open calls)
        if (params.has("url") && params.get("url").isJsonPrimitive()) {
            return params.get("url").getAsString();
        }
        // Fallback: "menuItemId" (backwards compat with old research_choose calls)
        if (params.has("menuItemId") && params.get("menuItemId").isJsonPrimitive()) {
            return params.get("menuItemId").getAsString();
        }
        // Fallback: "action" (backwards compat with old research_navigate calls)
        if (params.has("action") && params.get("action").isJsonPrimitive()) {
            return params.get("action").getAsString();
        }
        // Nested arguments (some LLMs wrap params)
        if (params.has("arguments") && params.get("arguments").isJsonObject()) {
            return extractTarget(params.getAsJsonObject("arguments"));
        }
        if (params.has("input") && params.get("input").isJsonObject()) {
            return extractTarget(params.getAsJsonObject("input"));
        }
        return null;
    }
}

