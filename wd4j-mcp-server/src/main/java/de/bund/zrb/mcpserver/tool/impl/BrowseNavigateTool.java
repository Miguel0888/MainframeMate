package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.command.response.WDBrowsingContextResult;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.bund.zrb.type.script.WDEvaluateResult;

import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Navigate to a URL and return a compact page snapshot with page text excerpt.
 * Invalidates all existing NodeRefs.
 */
public class BrowseNavigateTool implements McpServerTool {

    private static final Logger LOG = Logger.getLogger(BrowseNavigateTool.class.getName());

    @Override
    public String name() {
        return "web_navigate";
    }

    @Override
    public String description() {
        return "Navigate to a URL in the browser. Returns the page title, URL, interactive elements (NodeRefs for click/type), "
             + "and a text excerpt of the page content. Use web_read_page to get the full page text. "
             + "All previous NodeRefs are invalidated after navigation.";
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

        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("url");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        // Tolerant URL extraction: accept "url", or nested "arguments.url", or "action.url"
        String url = extractUrl(params);
        if (url == null || url.isEmpty()) {
            LOG.warning("[web_navigate] Missing 'url' parameter. Params: " + params);
            return ToolResult.error("Missing required parameter 'url'. Example: {\"name\":\"web_navigate\",\"input\":{\"url\":\"https://example.com\"}}");
        }

        LOG.info("[web_navigate] Navigating to: " + url);

        // Configurable timeout via system property (default 30s)
        long timeoutSeconds = Long.getLong("websearch.navigate.timeout.seconds", 30);
        LOG.fine("[web_navigate] Timeout: " + timeoutSeconds + "s");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<ToolResult> future = executor.submit(new Callable<ToolResult>() {
            @Override
            public ToolResult call() throws Exception {
                return doNavigate(url, session);
            }
        });

        try {
            ToolResult result = future.get(timeoutSeconds, TimeUnit.SECONDS);
            LOG.info("[web_navigate] Navigation completed successfully for: " + url
                    + " (error=" + result.isError() + ")");
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            // Kill the browser process – it's hanging
            LOG.severe("[web_navigate] Timeout after " + timeoutSeconds + "s navigating to: " + url + " – killing browser process");
            System.err.println("[web_navigate] Timeout after " + timeoutSeconds + "s navigating to: " + url + " – killing browser process");
            session.killBrowserProcess();
            return ToolResult.error(
                    "Navigation failed: Timeout after " + timeoutSeconds + " seconds. "
                  + "The browser was unresponsive and has been terminated. "
                  + "It will be automatically restarted on the next request. "
                  + "Please try again by calling web_navigate with the same URL. "
                  + "URL: " + url);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String msg = cause != null && cause.getMessage() != null ? cause.getMessage() : e.getMessage();
            if (isTimeoutError(msg, e)) {
                LOG.severe("[web_navigate] Inner timeout navigating to: " + url + " – killing browser process");
                System.err.println("[web_navigate] Inner timeout navigating to: " + url + " – killing browser process");
                session.killBrowserProcess();
                return ToolResult.error(
                        "Navigation failed: Timeout while waiting for response. "
                      + "The browser was unresponsive and has been terminated. "
                      + "It will be automatically restarted on the next request. "
                      + "Please try again by calling web_navigate with the same URL. "
                      + "URL: " + url);
            }
            if (msg != null && (msg.contains("WebSocket connection is closed") || msg.contains("not connected"))) {
                LOG.warning("[web_navigate] Browser session lost. URL: " + url + " Error: " + msg);
                return ToolResult.error(
                        "Navigation failed: The browser session has been lost. "
                      + "Please try again – the browser will be reconnected automatically. "
                      + "URL: " + url);
            }
            LOG.warning("[web_navigate] Navigation failed. URL: " + url + " Error: " + msg);
            return ToolResult.error("Navigation failed: " + msg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warning("[web_navigate] Navigation interrupted. URL: " + url);
            return ToolResult.error("Navigation was interrupted. URL: " + url);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Performs the actual navigation + snapshot + page excerpt extraction.
     */
    private ToolResult doNavigate(String url, BrowserSession session) {
        try {
            // Invalidate old refs
            session.getNodeRefRegistry().invalidateAll();
            LOG.fine("[web_navigate] Old refs invalidated. Starting navigation to: " + url);

            WDBrowsingContextResult.NavigateResult nav = session.navigate(url);
            String finalUrl = nav.getUrl();
            LOG.info("[web_navigate] Navigation response received. Final URL: " + finalUrl);

            // Wait for page to stabilize, then take snapshot
            BrowseSnapshotTool snapshotTool = new BrowseSnapshotTool();
            JsonObject snapshotParams = new JsonObject();
            ToolResult snapshotResult = null;

            for (int attempt = 0; attempt < 3; attempt++) {
                try { Thread.sleep(attempt == 0 ? 1000 : 1500); } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return ToolResult.error("Navigation interrupted during page load. URL: " + url);
                }

                LOG.fine("[web_navigate] Snapshot attempt " + (attempt + 1) + " for: " + finalUrl);
                snapshotResult = snapshotTool.execute(snapshotParams, session);
                if (snapshotResult != null && !snapshotResult.isError()) {
                    String text = snapshotResult.getText();
                    if (text.contains("[n")) {
                        LOG.fine("[web_navigate] Snapshot successful on attempt " + (attempt + 1)
                                + " with interactive elements found");
                        break;
                    } else {
                        LOG.fine("[web_navigate] Snapshot attempt " + (attempt + 1)
                                + " found no interactive elements yet");
                    }
                } else {
                    LOG.warning("[web_navigate] Snapshot attempt " + (attempt + 1) + " failed: "
                            + (snapshotResult != null ? snapshotResult.getText() : "null"));
                }
            }

            // Also get a page text excerpt (so the bot can read article content immediately)
            String pageExcerpt = getPageTextExcerpt(session, 3000);

            StringBuilder sb = new StringBuilder();
            sb.append("Navigated to: ").append(finalUrl != null ? finalUrl : url).append("\n\n");
            if (snapshotResult != null && !snapshotResult.isError()) {
                sb.append(snapshotResult.getText());
            }

            // Append page text excerpt
            if (!pageExcerpt.isEmpty()) {
                sb.append("\n── Page text (excerpt) ──────────────────\n");
                sb.append(pageExcerpt);
                if (pageExcerpt.length() >= 2900) {
                    sb.append("\n[… truncated. Use web_read_page for full text.]");
                }
            }

            return ToolResult.text(sb.toString());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            LOG.log(Level.WARNING, "[web_navigate] doNavigate failed for URL: " + url, e);

            // Check for timeout – encourage retry
            if (isTimeoutError(msg, e)) {
                return ToolResult.error(
                        "Navigation failed: Timeout while waiting for response. "
                      + "The page may be loading slowly or the browser may be temporarily unresponsive. "
                      + "Please try again by calling web_navigate with the same URL. "
                      + "URL: " + url);
            }

            // Check for connection/session errors that might be fixable by retrying
            if (msg.contains("WebSocket connection is closed") || msg.contains("not connected")) {
                return ToolResult.error(
                        "Navigation failed: The browser session has been lost. "
                      + "Please try again – the browser will be reconnected automatically. "
                      + "URL: " + url);
            }

            return ToolResult.error("Navigation failed: " + msg);
        }
    }

    /**
     * Checks if the exception represents a timeout error by examining
     * the message and cause chain.
     */
    private boolean isTimeoutError(String msg, Exception e) {
        if (msg.toLowerCase().contains("timeout")) return true;
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof java.util.concurrent.TimeoutException) return true;
            String causeMsg = cause.getMessage();
            if (causeMsg != null && causeMsg.toLowerCase().contains("timeout")) return true;
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Tolerantly extract the URL from params, handling common LLM mistakes like nested objects.
     */
    private String extractUrl(JsonObject params) {
        // Direct: {"url": "..."}
        if (params.has("url") && params.get("url").isJsonPrimitive()) {
            return params.get("url").getAsString();
        }
        // Nested in "arguments": {"arguments": {"url": "..."}}
        if (params.has("arguments") && params.get("arguments").isJsonObject()) {
            JsonObject args = params.getAsJsonObject("arguments");
            if (args.has("url") && args.get("url").isJsonPrimitive()) {
                return args.get("url").getAsString();
            }
        }
        // Nested in "action": {"action": {"url": "..."}}
        if (params.has("action") && params.get("action").isJsonObject()) {
            JsonObject action = params.getAsJsonObject("action");
            if (action.has("url") && action.get("url").isJsonPrimitive()) {
                return action.get("url").getAsString();
            }
        }
        // Try "request" field
        if (params.has("request") && params.get("request").isJsonPrimitive()) {
            return params.get("request").getAsString();
        }
        return null;
    }

    /**
     * Get a text excerpt from the current page, preferring article/main content.
     */
    private String getPageTextExcerpt(BrowserSession session, int maxLen) {
        String script = "(function(){"
                + "var el=document.querySelector('article')||document.querySelector('[role=main]')"
                + "||document.querySelector('main')||document.querySelector('#article-container')"
                + "||document.querySelector('.caas-body')||document.querySelector('.caas-content-wrapper')"
                + "||document.querySelector('#Main')||document.querySelector('[data-content-area]')"
                + "||document.querySelector('.ntk-lead')||document.querySelector('#content')"
                + "||document.querySelector('.content')||document.body;"
                + "if(!el)return '';"
                // Clone and remove noise elements
                + "var clone=el.cloneNode(true);"
                + "var remove=clone.querySelectorAll('script,style,nav,footer,header,noscript,[aria-hidden=true],.ad,.ads');"
                + "for(var i=0;i<remove.length;i++){try{remove[i].parentNode.removeChild(remove[i]);}catch(e){}}"
                + "var t=clone.innerText||clone.textContent||'';"
                + "return t.replace(/\\t/g,' ').replace(/ {3,}/g,' ').replace(/\\n{3,}/g,'\\n\\n').trim().substring(0," + maxLen + ");"
                + "})()";
        try {
            Object result = session.evaluate(script, true);
            if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                String s = ((WDEvaluateResult.WDEvaluateResultSuccess) result).getResult().asString();
                if (s != null && !s.startsWith("[Object:")) return s;
            }
        } catch (Exception ignored) {}
        return "";
    }
}
