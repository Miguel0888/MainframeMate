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

        long timeoutSeconds = Long.getLong("websearch.navigate.timeout.seconds", 60);
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
            // Invalidate old refs
            session.getNodeRefRegistry().invalidateAll();

            // Navigate with specified readiness state
            String ctx = session.getContextId();
            WDBrowsingContextResult.NavigateResult nav =
                    session.getDriver().browsingContext().navigate(url, ctx, readiness);
            String finalUrl = nav.getUrl();
            LOG.info("[research_open] Navigation response. Final URL: " + finalUrl);

            // Build menu view with settle
            ResearchSession rs = ResearchSessionManager.getInstance().getOrCreate(session);
            MenuViewBuilder builder = new MenuViewBuilder(session, rs);
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
