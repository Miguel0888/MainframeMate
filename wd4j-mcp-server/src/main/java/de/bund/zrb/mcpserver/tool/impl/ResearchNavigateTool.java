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
            ResearchSession rs = ensureSession(session);

            // Use BiDi-native traverseHistory instead of script.evaluate
            int delta = "back".equalsIgnoreCase(action) ? -1 : 1;
            session.getDriver().browsingContext().traverseHistory(session.getContextId(), delta);

            session.getNodeRefRegistry().invalidateAll();
            rs.invalidateView();

            // Dismiss cookie banners + fetch DOM snapshot
            CookieBannerDismisser.tryDismiss(session);
            String html = DomSnapshotFetcher.fetchHtml(session);
            String currentUrl = DomSnapshotFetcher.fetchCurrentUrl(session);

            if (currentUrl != null) {
                rs.setLastNavigationUrl(currentUrl);
            }

            // Archive the snapshot
            archiveSnapshot(rs, currentUrl != null ? currentUrl : "", html);

            MenuViewBuilder builder = new MenuViewBuilder(rs);
            builder.setHtmlOverride(html, currentUrl != null ? currentUrl : "");
            MenuView view = builder.build(rs.getMaxMenuItems(), rs.getExcerptMaxLength());

            return buildResponse(view, rs);
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
        return rs.getLastNavigationUrl();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Core navigation
    // ═══════════════════════════════════════════════════════════════

    private ToolResult navigateToUrl(String url, ResearchSession rs, BrowserSession session) {
        // ── Same-URL guard ──
        String currentUrl = rs.getLastNavigationUrl();

        // Fallback: current MenuView URL
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

        // Pre-set URL for same-URL detection during navigation
        rs.setLastNavigationUrl(url);

        // ── Execute navigation with timeout ──
        long timeoutSeconds = Long.getLong("websearch.navigate.timeout.seconds", 30);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ToolResult> future = executor.submit(() ->
                    doNavigate(url, rs, session));
            ToolResult result = future.get(timeoutSeconds, TimeUnit.SECONDS);
            return result;
        } catch (TimeoutException e) {
            LOG.severe("[research_navigate] Timeout after " + timeoutSeconds + "s for: " + url);
            // On timeout, try a last-ditch DOM snapshot
            try {
                String html = DomSnapshotFetcher.fetchHtml(session, 0);
                if (html != null) {
                    MenuViewBuilder builder = new MenuViewBuilder(rs);
                    builder.setHtmlOverride(html, url);
                    MenuView view = builder.build(rs.getMaxMenuItems(), rs.getExcerptMaxLength());
                    if (view != null && view.getMenuItems() != null && !view.getMenuItems().isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("⚠️ Page loading timed out after ").append(timeoutSeconds)
                          .append("s, showing available content.\n\n");
                        sb.append(buildResponseText(view, rs));
                        return ToolResult.text(sb.toString());
                    }
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
            LOG.info("[research_navigate] doNavigate url=" + url);
            // Use NONE readiness to avoid freezing on pages with endless resource loading
            // (ads, tracking scripts, etc.). A fixed delay afterwards lets the page render.
            WDBrowsingContextResult.NavigateResult nav =
                    session.getDriver().browsingContext().navigate(
                            url, session.getContextId(), WDReadinessState.NONE);
            String finalUrl = nav.getUrl();
            LOG.info("[research_navigate] Landed on: " + finalUrl);

            // Update lastNavigationUrl for same-URL detection
            rs.setLastNavigationUrl(finalUrl != null && !finalUrl.isEmpty() ? finalUrl : url);

            // Try to dismiss cookie banners before taking the snapshot
            CookieBannerDismisser.tryDismiss(session);

            // Fetch DOM snapshot via script.evaluate (replaces old NetworkIngestionPipeline approach)
            String html = DomSnapshotFetcher.fetchHtml(session);

            if (html == null) {
                LOG.warning("[research_navigate] DOM snapshot returned null for: " + finalUrl);
            }

            // Archive the snapshot asynchronously via the callback
            archiveSnapshot(rs, finalUrl != null ? finalUrl : url, html);

            MenuViewBuilder builder = new MenuViewBuilder(rs);
            builder.setHtmlOverride(html, finalUrl != null ? finalUrl : url);
            MenuView view = builder.build(rs.getMaxMenuItems(), rs.getExcerptMaxLength());

            return buildResponse(view, rs);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[research_navigate] doNavigate failed", e);
            return ToolResult.error("Navigation failed: " + e.getMessage());
        }
    }


    // ═══════════════════════════════════════════════════════════════
    //  Response builder
    // ═══════════════════════════════════════════════════════════════

    private ToolResult buildResponse(MenuView view, ResearchSession rs) {
        ToolResult result = ToolResult.text(buildResponseText(view, rs));

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
            result.addText(linksJson.toString());
        }

        return result;
    }

    private String buildResponseText(MenuView view, ResearchSession rs) {
        StringBuilder sb = new StringBuilder();

        // Page title and URL
        sb.append("Du bist auf: ").append(view.getTitle() != null ? view.getTitle() : "(no title)");
        sb.append(" (").append(view.getUrl() != null ? view.getUrl() : "unknown").append(")\n");

        // Page excerpt
        String excerpt = view.getExcerpt();
        if (excerpt != null && !excerpt.isEmpty()) {
            sb.append("\n── Seiteninhalt ──\n");
            sb.append(excerpt);
            if (excerpt.length() >= 480) {
                sb.append("\n[… truncated]");
            }
            sb.append("\n");
        }

        // Drain archived docs (side-effect needed) but don't bloat the response
        rs.drainNewArchivedDocIds();

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Session auto-init (replaces ResearchSessionStartTool)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Ensures a fully initialized ResearchSession exists for this BrowserSession.
     * Creates UserContext and RunId on first call.
     * Subsequent calls return the existing session immediately.
     */
    private ResearchSession ensureSession(BrowserSession session) {
        ResearchSession rs = ResearchSessionManager.getInstance().get(session);
        if (rs != null && rs.getRunId() != null) {
            // Session fully initialized
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

        LOG.info("[research_navigate] Session ready: " + rs.getSessionId());
        return rs;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Snapshot archiving (replaces NetworkIngestionPipeline)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Archive the DOM snapshot via the global ingestion callback.
     * Uses the same callback interface as the old NetworkIngestionPipeline
     * so the ArchiveService integration works unchanged.
     */
    private void archiveSnapshot(ResearchSession rs, String url, String html) {
        if (html == null || html.isEmpty() || url == null || url.isEmpty()) return;

        SnapshotArchivingCallback callback = ResearchSessionManager.getSnapshotArchivingCallback();
        if (callback == null) return;

        try {
            String runId = rs.getRunId();
            String docId = callback.onSnapshotCaptured(runId, url, "text/html", 200, html, System.currentTimeMillis());
            if (docId != null) {
                rs.addArchivedDocId(docId);
                LOG.info("[research_navigate] ✅ Snapshot archived: " + url + " → id=" + docId
                        + " (" + html.length() + " chars)");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[research_navigate] Failed to archive snapshot for " + url, e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Pipeline management
    // ═══════════════════════════════════════════════════════════════


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
