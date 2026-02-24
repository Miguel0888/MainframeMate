package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonObject;
import de.bund.zrb.command.response.WDBrowsingContextResult;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.bund.zrb.type.script.WDEvaluateResult;

/**
 * Navigates the browser to a URL.
 */
public class BrowserNavigateTool implements McpServerTool {

    @Override
    public String name() {
        return "browser_navigate";
    }

    @Override
    public String description() {
        return "Navigate the browser to a given URL.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject url = new JsonObject();
        url.addProperty("type", "string");
        url.addProperty("description", "The URL to navigate to");
        props.add("url", url);

        JsonObject ctxId = new JsonObject();
        ctxId.addProperty("type", "string");
        ctxId.addProperty("description", "Browsing context ID (optional, uses default)");
        props.add("contextId", ctxId);

        schema.add("properties", props);

        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("url");
        schema.add("required", required);

        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String url = params.get("url").getAsString();
        String ctxId = params.has("contextId") ? params.get("contextId").getAsString() : null;

        try {
            WDBrowsingContextResult.NavigateResult result = session.navigate(url, ctxId);
            String finalUrl = result.getUrl();

            // After successful navigation, wait briefly for page readyState to be at least "interactive"
            try {
                waitForPageLoad(session, 10_000);
            } catch (Exception loadWaitEx) {
                // Non-fatal: page may still be usable, just log
            }

            return ToolResult.text("Navigated to: " + (finalUrl != null ? finalUrl : url));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();

            // Check for timeout – encourage retry
            if (isTimeoutError(msg, e)) {
                return ToolResult.error(
                        "Navigation failed: The browser did not respond within the timeout period. "
                      + "The page may be loading slowly or the browser may be unresponsive. "
                      + "Please try again by calling browser_navigate with the same URL. "
                      + "URL: " + url);
            }

            // Check for DNS/connection errors
            if (msg.contains("NS_ERROR_UNKNOWN_HOST") || msg.contains("UnknownHost")) {
                return ToolResult.error(
                        "Navigation failed: The host could not be found (DNS error). "
                      + "Please check the URL for typos. URL: " + url);
            }

            return ToolResult.error("Navigation failed: " + msg);
        }
    }

    /**
     * Checks if the exception represents a timeout error.
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
     * Waits for the page to reach at least document.readyState "interactive" or "complete".
     * Polls via script evaluation with a short interval.
     */
    private void waitForPageLoad(BrowserSession session, long maxWaitMs) {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                WDEvaluateResult result = session.evaluate("document.readyState", true);
                if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                    String state = ((WDEvaluateResult.WDEvaluateResultSuccess) result)
                            .getResult().asString();
                    if ("interactive".equals(state) || "complete".equals(state)) {
                        return;
                    }
                }
            } catch (Exception ignored) {
                // Page might not be ready yet
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}

