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
 *   <li>An absolute URL: {@code https://example.com/path/...}</li>
 *   <li>A relative path: {@code /path/subpath/} (resolved against current page)</li>
 *   <li>A history action: {@code back}, {@code forward}</li>
 * </ul>
 * <p>
 * Returns: page title, text excerpt, and a list of URLs with descriptions.
 * Each link uses the format "Für {description}: {url}" so the bot knows
 * what to pass as {@code target} for the next call.
 * <p>
 * Design principle (Callcenter-Metapher): When you call a URL, you get a
 * friendly response saying "You have these options: for A go here, for B go there."
 * No abstract IDs, no separate session-start, no separate choose-tool.
 */
public class ResearchNavigateTool implements McpServerTool {

    private static final Logger LOG = Logger.getLogger(ResearchNavigateTool.class.getName());

    @Override
    public String name() {
        return "research_navigate";
    }

    @Override
    public String description() {
        return "Navigate to a web page. "
             + "Pass a URL as 'target': use a URL from the previous tool response. "
             + "Or pass 'back'/'forward' for browser history. "
             + "The response shows page content and a list of URLs you can visit next. "
             + "ALWAYS pick a URL from the response list. NEVER repeat the same URL.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject target = new JsonObject();
        target.addProperty("type", "string");
        target.addProperty("description",
                "A URL from the previous response, a relative path, or 'back'/'forward'. "
                + "MUST be different from the current page URL.");
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
        // URL (absolute or relative)
        return handleUrlNavigation(target, session);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Target classification
    // ═══════════════════════════════════════════════════════════════

    private boolean isHistoryAction(String target) {
        String lower = target.toLowerCase();
        return "back".equals(lower) || "forward".equals(lower);
    }

    // ═══════════════════════════════════════════════════════════════
    //  History actions (back/forward)
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
                sb.append("FEHLER: Du bist BEREITS auf dieser Seite (").append(url).append(").\n");
                sb.append("Du DARFST diese URL NICHT erneut aufrufen.\n\n");

                // Show available links so the bot can pick one
                int count = 0;
                for (MenuItem item : existingView.getMenuItems()) {
                    if (count >= 5) break;
                    if (item.getHref() != null && !item.getHref().isEmpty()
                            && !isSameUrl(item.getHref(), url)) {
                        String relUrl = item.getRelativeHref(url);
                        sb.append("  Für ").append(item.getLabel()).append(":  ").append(relUrl).append("\n");
                        count++;
                    }
                }
                sb.append("\nDu MUSST eine dieser URLs wählen. Rufe research_navigate mit einer davon auf.");
                return ToolResult.error(sb.toString());
            }
            return ToolResult.error("FEHLER: Du bist BEREITS auf " + currentUrl
                    + ". Navigiere zu einer ANDEREN URL.");
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

            // Update the pipeline's lastNavigationUrl to the ACTUAL landing URL,
            // not the requested URL. This is critical for same-URL detection after
            // 404-redirects (e.g. /politik/ → /?err=404&err_url=...).
            NetworkIngestionPipeline pipeline = rs.getNetworkPipeline();
            if (pipeline != null && finalUrl != null && !finalUrl.isEmpty()) {
                pipeline.setLastNavigationUrl(finalUrl);
            }

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
        ToolResult result = ToolResult.text(buildResponseText(view, rs, pipeline));

        // Add links as structured JSON so the bot can reliably pick a URL
        if (view.getMenuItems() != null && !view.getMenuItems().isEmpty()) {
            JsonObject linksJson = new JsonObject();
            JsonArray linksArray = new JsonArray();
            for (MenuItem item : view.getMenuItems()) {
                JsonObject link = new JsonObject();
                link.addProperty("label", item.getLabel() != null ? item.getLabel() : "");
                String url = item.getRelativeHref(view.getUrl());
                link.addProperty("url", url != null ? url : "");
                linksArray.add(link);
            }
            linksJson.add("links", linksArray);
            linksJson.addProperty("hint", "Wähle eine URL aus 'links' und rufe research_navigate damit auf.");
            result.addText(linksJson.toString());
        }

        return result;
    }

    private String buildResponseText(MenuView view, ResearchSession rs, NetworkIngestionPipeline pipeline) {
        StringBuilder sb = new StringBuilder(view.toCompactText());

        // Archived docs
        List<String> newDocs = rs.drainNewArchivedDocIds();
        if (!newDocs.isEmpty()) {
            sb.append("\n── Archiviert (").append(newDocs.size()).append(") ──\n");
            for (String docId : newDocs) {
                sb.append("  ").append(docId).append("\n");
            }
        }

        // Network traffic (compact)
        if (pipeline != null) {
            Map<String, Integer> cats = pipeline.getCategoryCounts();
            if (!cats.isEmpty()) {
                sb.append("\n── Netzwerk ──\n  ");
                boolean first = true;
                for (Map.Entry<String, Integer> e : cats.entrySet()) {
                    if (!first) sb.append(", ");
                    sb.append(e.getKey()).append(":").append(e.getValue());
                    first = false;
                }
                sb.append("\n");
            }
        }

        sb.append("\n── Nächster Schritt ──\n");
        sb.append("Wähle eine URL aus der Liste oben und rufe research_navigate damit auf.");
        sb.append(" Bevorzuge Navigations-/Sektionslinks vor einzelnen Artikeln.\n");

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
            URI c = new URI(stripErrorParams(current));
            URI t = new URI(stripErrorParams(target));
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

    /**
     * Strip error/redirect query params that Yahoo and other sites add on 404 redirects.
     * E.g. "https://de.yahoo.com/?err=404&err_url=..." → "https://de.yahoo.com/"
     */
    private String stripErrorParams(String url) {
        if (url == null) return null;
        try {
            URI uri = new URI(url);
            String query = uri.getQuery();
            if (query == null || query.isEmpty()) return url;
            // If the query contains err= or err_url=, it's a redirect-error page → strip all query
            if (query.contains("err=") || query.contains("err_url=")) {
                return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null).toString();
            }
            return url;
        } catch (Exception e) {
            return url;
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
        // Fallback: "action" (backwards compat)
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

