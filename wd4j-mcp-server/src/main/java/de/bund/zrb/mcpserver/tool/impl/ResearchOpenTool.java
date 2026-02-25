package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.command.response.WDBrowsingContextResult;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.research.*;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.bund.zrb.type.browsingContext.WDReadinessState;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Navigate to a URL and return a menu view.
 * <p>
 * Eingaben (MUSS): sessionId (opt.), contextId (opt.), url, wait (none|interactive|complete),
 * settlePolicy (NAVIGATION|DOM_QUIET|NETWORK_QUIET).
 * <p>
 * Ausgabe: wie research_menu (viewToken, url, title, excerpt, menuItems[], newArchivedDocs[]).
 * <p>
 * Intern MUSS browsingContext.navigate mit wait verwendet werden (Default: interactive).
 */
public class ResearchOpenTool implements McpServerTool {

    private static final Logger LOG = Logger.getLogger(ResearchOpenTool.class.getName());

    /**
     * Ensure the NetworkIngestionPipeline is running.
     * After a browser kill+restart, the pipeline is gone because a new session was created.
     * This method auto-starts it so research_open works without requiring research_session_start again.
     */
    private void ensurePipeline(ResearchSession rs, BrowserSession session) {
        NetworkIngestionPipeline pipeline = rs.getNetworkPipeline();
        if (pipeline != null && pipeline.isActive()) return;

        if (session.getDriver() == null) return;

        try {
            NetworkIngestionPipeline newPipeline = new NetworkIngestionPipeline(session.getDriver(), rs);

            NetworkIngestionPipeline.IngestionCallback cb =
                    NetworkIngestionPipeline.getGlobalDefaultCallback();
            if (cb == null) {
                cb = (runId, url, mimeType, status, bodyText, headers, capturedAt) -> {
                    LOG.fine("[NetworkIngestion] Captured (no persister): " + url);
                    return "net-" + Long.toHexString(capturedAt);
                };
            }

            // Ensure runId exists
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
            LOG.info("[research_open] Auto-started network ingestion pipeline after browser restart");
        } catch (Exception e) {
            LOG.warning("[research_open] Failed to auto-start pipeline: " + e.getMessage());
        }
    }

    @Override
    public String name() {
        return "research_open";
    }

    @Override
    public String description() {
        return "Navigate to a URL and get a menu view with clickable items. "
             + "Returns: viewToken, page excerpt, menu items, and newly archived doc IDs. "
             + "Use menuItemId with research_choose to interact. "
             + "wait: none|interactive (default)|complete. "
             + "settlePolicy: NAVIGATION (default), DOM_QUIET (SPA), NETWORK_QUIET (AJAX).";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject url = new JsonObject();
        url.addProperty("type", "string");
        url.addProperty("description", "URL to navigate to");
        props.add("url", url);

        JsonObject wait = new JsonObject();
        wait.addProperty("type", "string");
        wait.addProperty("description", "ReadinessState to wait for: 'none', 'interactive' (default), 'complete'");
        props.add("wait", wait);

        JsonObject settle = new JsonObject();
        settle.addProperty("type", "string");
        settle.addProperty("description", "Settle strategy after navigation: NAVIGATION (default), DOM_QUIET, NETWORK_QUIET");
        props.add("settlePolicy", settle);

        JsonObject sessionId = new JsonObject();
        sessionId.addProperty("type", "string");
        sessionId.addProperty("description", "Session ID (from research_session_start). Optional if only one session.");
        props.add("sessionId", sessionId);

        JsonObject contextId = new JsonObject();
        contextId.addProperty("type", "string");
        contextId.addProperty("description", "BrowsingContext ID to navigate in. Optional (uses active tab).");
        props.add("contextId", contextId);

        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("url");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String url = extractUrl(params);
        if (url == null || url.isEmpty()) {
            return ToolResult.error("Missing required parameter 'url'.");
        }

        // Parse wait (ReadinessState)
        WDReadinessState readiness = parseReadinessState(
                params.has("wait") ? params.get("wait").getAsString() : null);

        SettlePolicy policy = SettlePolicy.fromString(
                params.has("settlePolicy") ? params.get("settlePolicy").getAsString() : null);

        LOG.info("[research_open] Opening: " + url + " (wait=" + readiness + ", settle=" + policy + ")");

        long timeoutSeconds = Long.getLong("websearch.navigate.timeout.seconds", 30);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<ToolResult> future = executor.submit(new Callable<ToolResult>() {
            @Override
            public ToolResult call() {
                return doOpen(url, readiness, policy, session);
            }
        });

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            LOG.severe("[research_open] Timeout after " + timeoutSeconds + "s for: " + url);
            // Don't kill browser – try to build menu from current page state instead
            try {
                ResearchSession rs = ResearchSessionManager.getInstance().getOrCreate(session);
                NetworkIngestionPipeline pipeline = rs.getNetworkPipeline();
                MenuViewBuilder builder = new MenuViewBuilder(rs, pipeline);
                MenuView view = builder.build(rs.getMaxMenuItems(), rs.getExcerptMaxLength());
                if (view != null && view.getMenuItems() != null && !view.getMenuItems().isEmpty()) {
                    LOG.info("[research_open] Timeout recovery: built menu from current page state");
                    StringBuilder sb = new StringBuilder();
                    sb.append("⚠️ Navigation timed out after ").append(timeoutSeconds)
                      .append("s, showing current page state.\n\n");
                    sb.append(view.toCompactText());
                    // Append newly archived doc IDs
                    List<String> newDocs = rs.drainNewArchivedDocIds();
                    if (!newDocs.isEmpty()) {
                        sb.append("\n── Newly archived (").append(newDocs.size()).append(") ──\n");
                        for (String docId : newDocs) {
                            sb.append("  ").append(docId).append("\n");
                        }
                    }
                    return ToolResult.text(sb.toString());
                }
            } catch (Exception recovery) {
                LOG.warning("[research_open] Timeout recovery failed: " + recovery.getMessage());
            }
            // Only kill as last resort if recovery failed
            session.killBrowserProcess();
            return ToolResult.error(
                    "Navigation timeout after " + timeoutSeconds + "s. "
                  + "Browser terminated, will restart on next request. "
                  + "Try again with research_open.");
        } catch (ExecutionException e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            LOG.warning("[research_open] Failed: " + msg);
            if (isConnectionError(msg)) {
                return ToolResult.error("Browser session lost. Try again – auto-reconnect.");
            }
            return ToolResult.error("Navigation failed: " + msg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error("Navigation interrupted.");
        } finally {
            executor.shutdownNow();
        }
    }

    private ToolResult doOpen(String url, WDReadinessState readiness,
                              SettlePolicy policy, BrowserSession session) {
        try {
            String ctx = session.getContextId();
            ResearchSession rs = ResearchSessionManager.getInstance().getOrCreate(session);

            // Ensure pipeline is running (auto-start after browser restart)
            ensurePipeline(rs, session);
            NetworkIngestionPipeline pipeline = rs.getNetworkPipeline();

            // ── Same-URL detection (no JS) ──
            // If we already have cached HTML for this URL, return existing view
            // instead of re-navigating (Firefox hangs on navigate to same URL)
            boolean alreadyThere = false;
            if (pipeline != null) {
                String cachedUrl = pipeline.getLastNavigationUrl();
                if (cachedUrl != null && isSameUrl(cachedUrl, url)) {
                    // Check if we already have a valid view with a valid token
                    MenuView existingView = rs.getCurrentMenuView();
                    if (existingView != null && existingView.getViewToken() != null
                            && existingView.getMenuItems() != null
                            && !existingView.getMenuItems().isEmpty()) {
                        LOG.info("[research_open] Already on " + cachedUrl
                                + " with valid view (token=" + existingView.getViewToken()
                                + ") – returning cached view.");

                        List<String> newDocs = rs.drainNewArchivedDocIds();
                        StringBuilder sb = new StringBuilder(existingView.toCompactText());
                        if (!newDocs.isEmpty()) {
                            sb.append("\n── Newly archived (").append(newDocs.size()).append(") ──\n");
                            for (String docId : newDocs) {
                                sb.append("  ").append(docId).append("\n");
                            }
                        }
                        sb.append("\n── ⚠ IMPORTANT ──\n");
                        sb.append("You are ALREADY on this page. Do NOT call research_open with the same URL again.\n");
                        sb.append("Instead, use research_choose with viewToken='")
                          .append(existingView.getViewToken())
                          .append("' and a menuItemId from the list above (e.g. 'm0').\n");
                        sb.append("Or call research_open with a DIFFERENT URL from the menu.");
                        return ToolResult.text(sb.toString());
                    }
                    // Have cached HTML but no valid view – rebuild from cache, don't navigate
                    alreadyThere = true;
                    LOG.info("[research_open] Already on " + cachedUrl + " – rebuilding view from cached HTML.");
                }
            }

            if (!alreadyThere) {
                // Clear HTML cache before new navigation
                if (pipeline != null) {
                    pipeline.clearNavigationCache();
                }

                // Navigate via address bar (URL-based, no JS)
                WDBrowsingContextResult.NavigateResult nav =
                        session.getDriver().browsingContext().navigate(url, ctx, readiness);
                String finalUrl = nav.getUrl();
                LOG.info("[research_open] Navigation response. Final URL: " + finalUrl);
            }

            // Build menu view from captured HTML via Jsoup (no JS injection)
            MenuViewBuilder builder = new MenuViewBuilder(rs, pipeline);
            MenuView view = builder.buildWithSettle(policy,
                    rs.getMaxMenuItems(), rs.getExcerptMaxLength());

            // Append newly archived doc IDs
            List<String> newDocs = rs.drainNewArchivedDocIds();
            StringBuilder sb = new StringBuilder(view.toCompactText());
            if (!newDocs.isEmpty()) {
                sb.append("\n── Newly archived (").append(newDocs.size()).append(") ──\n");
                for (String docId : newDocs) {
                    sb.append("  ").append(docId).append("\n");
                }
            }

            // Network traffic summary
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
            sb.append("Read the excerpt above. To follow a link, use research_choose with ");
            sb.append("viewToken='").append(view.getViewToken()).append("' and the menuItemId, ");
            sb.append("or call research_open with a URL directly.");

            return ToolResult.text(sb.toString());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[research_open] doOpen failed", e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (isTimeoutError(msg, e)) {
                return ToolResult.error(
                        "Navigation timeout. The page may be loading slowly. "
                      + "Try again or use wait='none' for immediate response.");
            }
            return ToolResult.error("Navigation failed: " + msg);
        }
    }

    private WDReadinessState parseReadinessState(String s) {
        if (s == null || s.isEmpty()) return WDReadinessState.INTERACTIVE;
        switch (s.toLowerCase()) {
            case "none": return WDReadinessState.NONE;
            case "complete": return WDReadinessState.COMPLETE;
            case "interactive":
            default: return WDReadinessState.INTERACTIVE;
        }
    }

    private boolean isTimeoutError(String msg, Exception e) {
        if (msg != null && msg.toLowerCase().contains("timeout")) return true;
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof TimeoutException) return true;
            String cm = cause.getMessage();
            if (cm != null && cm.toLowerCase().contains("timeout")) return true;
            cause = cause.getCause();
        }
        return false;
    }

    private boolean isConnectionError(String msg) {
        return msg != null && (msg.contains("WebSocket connection is closed")
                || msg.contains("not connected"));
    }

    /**
     * Check if two URLs refer to the same page (ignoring fragments and trailing slashes).
     * Strict comparison: scheme + host + path + query must match exactly.
     * Uses java.net.URI for reliable parsing.
     */
    private boolean isSameUrl(String current, String target) {
        if (current == null || target == null) return false;
        try {
            java.net.URI c = new java.net.URI(current);
            java.net.URI t = new java.net.URI(target);

            // Compare scheme (case-insensitive)
            if (!nullSafeEqualsIgnoreCase(c.getScheme(), t.getScheme())) return false;
            // Compare host (case-insensitive)
            if (!nullSafeEqualsIgnoreCase(c.getHost(), t.getHost())) return false;
            // Compare port (default ports: 80 for http, 443 for https)
            int cPort = effectivePort(c);
            int tPort = effectivePort(t);
            if (cPort != tPort) return false;
            // Compare path (case-sensitive, normalize trailing slash)
            String cPath = normalizePath(c.getPath());
            String tPath = normalizePath(t.getPath());
            if (!cPath.equals(tPath)) return false;
            // Compare query (case-sensitive – ?p=hi ≠ ?p=news)
            String cQuery = c.getQuery() != null ? c.getQuery() : "";
            String tQuery = t.getQuery() != null ? t.getQuery() : "";
            return cQuery.equals(tQuery);
        } catch (Exception e) {
            // Fallback: simple string comparison without fragment
            return normalizeForComparison(current).equals(normalizeForComparison(target));
        }
    }

    private boolean nullSafeEqualsIgnoreCase(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    private int effectivePort(java.net.URI uri) {
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

    private String normalizeForComparison(String url) {
        // Remove fragment only (NOT query params – they distinguish pages like ?p=hi vs ?p=news)
        int hash = url.indexOf('#');
        if (hash >= 0) url = url.substring(0, hash);
        // Remove trailing slash
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        // Lowercase scheme+host
        return url.toLowerCase();
    }


    private String extractUrl(JsonObject params) {
        if (params.has("url") && params.get("url").isJsonPrimitive()) {
            return params.get("url").getAsString();
        }
        if (params.has("arguments") && params.get("arguments").isJsonObject()) {
            JsonObject args = params.getAsJsonObject("arguments");
            if (args.has("url")) return args.get("url").getAsString();
        }
        return null;
    }
}
