package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.command.response.WDBrowsingContextResult;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.research.*;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Navigate to a URL and return a menu view (viewToken + excerpt + menu items).
 * This is the primary entry point for the research bot to open pages.
 * <p>
 * Unlike {@link BrowseNavigateTool} (which returns raw DOM info), this tool
 * returns a bot-friendly menu with action-tokens.
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
             + "Returns: viewToken, page excerpt, and a numbered menu of links/buttons. "
             + "Use the menuItemId with research_choose to interact. "
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

        JsonObject settle = new JsonObject();
        settle.addProperty("type", "string");
        settle.addProperty("description", "How to wait for page: NAVIGATION (default), DOM_QUIET, NETWORK_QUIET");
        props.add("settlePolicy", settle);

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

        SettlePolicy policy = SettlePolicy.fromString(
                params.has("settlePolicy") ? params.get("settlePolicy").getAsString() : null);

        LOG.info("[research_open] Opening: " + url + " (settle=" + policy + ")");

        long timeoutSeconds = Long.getLong("websearch.navigate.timeout.seconds", 60);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<ToolResult> future = executor.submit(new Callable<ToolResult>() {
            @Override
            public ToolResult call() throws Exception {
                return doOpen(url, policy, session);
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
                  + "Browser has been terminated and will restart on next request. "
                  + "Try again with research_open.");
        } catch (ExecutionException e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            LOG.warning("[research_open] Failed: " + msg);
            return ToolResult.error("Navigation failed: " + msg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error("Navigation interrupted.");
        } finally {
            executor.shutdownNow();
        }
    }

    private ToolResult doOpen(String url, SettlePolicy policy, BrowserSession session) {
        try {
            // Navigate
            session.getNodeRefRegistry().invalidateAll();
            WDBrowsingContextResult.NavigateResult nav = session.navigate(url);
            String finalUrl = nav.getUrl();
            LOG.info("[research_open] Navigation response. Final URL: " + finalUrl);

            // Build menu view with settle
            ResearchSession rs = ResearchSessionManager.getInstance().getOrCreate(session);
            MenuViewBuilder builder = new MenuViewBuilder(session, rs);
            MenuView view = builder.buildWithSettle(policy, rs.getMaxMenuItems(), rs.getExcerptMaxLength());

            return ToolResult.text(view.toCompactText());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[research_open] doOpen failed", e);
            return ToolResult.error("Navigation failed: " + e.getMessage());
        }
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

