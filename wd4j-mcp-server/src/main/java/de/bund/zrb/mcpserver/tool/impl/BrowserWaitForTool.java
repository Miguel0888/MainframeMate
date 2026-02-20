package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonObject;
import de.bund.zrb.command.response.WDBrowsingContextResult;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.bund.zrb.type.script.WDEvaluateResult;

/**
 * Waits for a page load event or for a CSS selector to become present.
 */
public class BrowserWaitForTool implements McpServerTool {

    @Override
    public String name() {
        return "browser_wait_for";
    }

    @Override
    public String description() {
        return "Wait for a page load event (load/domcontentloaded) or for a CSS selector to appear in the DOM.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject type = new JsonObject();
        type.addProperty("type", "string");
        type.addProperty("description", "What to wait for: 'load', 'domcontentloaded', or 'selector'");
        com.google.gson.JsonArray enumArr = new com.google.gson.JsonArray();
        enumArr.add("load");
        enumArr.add("domcontentloaded");
        enumArr.add("selector");
        type.add("enum", enumArr);
        props.add("type", type);

        JsonObject selector = new JsonObject();
        selector.addProperty("type", "string");
        selector.addProperty("description", "CSS selector to wait for (required when type=selector)");
        props.add("selector", selector);

        JsonObject timeout = new JsonObject();
        timeout.addProperty("type", "integer");
        timeout.addProperty("description", "Timeout in milliseconds (default: 10000)");
        props.add("timeoutMs", timeout);

        JsonObject ctxId = new JsonObject();
        ctxId.addProperty("type", "string");
        ctxId.addProperty("description", "Browsing context ID (optional, uses default)");
        props.add("contextId", ctxId);

        schema.add("properties", props);

        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("type");
        schema.add("required", required);

        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String waitType = params.get("type").getAsString();
        long timeoutMs = params.has("timeoutMs") ? params.get("timeoutMs").getAsLong() : 10000L;
        String ctxId = params.has("contextId") ? params.get("contextId").getAsString() : null;

        try {
            switch (waitType) {
                case "load":
                case "domcontentloaded":
                    return waitForReadyState(session, waitType, timeoutMs, ctxId);
                case "selector":
                    if (!params.has("selector")) {
                        return ToolResult.error("'selector' parameter required when type='selector'");
                    }
                    return waitForSelector(session, params.get("selector").getAsString(), timeoutMs, ctxId);
                default:
                    return ToolResult.error("Unknown wait type: " + waitType);
            }
        } catch (Exception e) {
            return ToolResult.error("Wait failed: " + e.getMessage());
        }
    }

    private ToolResult waitForReadyState(BrowserSession session, String waitType,
                                          long timeoutMs, String ctxId) throws InterruptedException {
        // Poll document.readyState
        String targetState = "load".equals(waitType) ? "complete" : "interactive";
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            try {
                WDEvaluateResult result = session.evaluate("document.readyState", true, ctxId);
                if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                    WDEvaluateResult.WDEvaluateResultSuccess success = (WDEvaluateResult.WDEvaluateResultSuccess) result;
                    String state = success.getResult().asString();
                    if ("complete".equals(state)) {
                        return ToolResult.text("Page ready (readyState=" + state + ")");
                    }
                    if ("interactive".equals(targetState) && ("interactive".equals(state) || "complete".equals(state))) {
                        return ToolResult.text("Page ready (readyState=" + state + ")");
                    }
                }
            } catch (Exception ignored) {
                // Page might be navigating, retry
            }
            Thread.sleep(200);
        }

        return ToolResult.error("Timeout waiting for " + waitType + " after " + timeoutMs + "ms");
    }

    private ToolResult waitForSelector(BrowserSession session, String selector,
                                        long timeoutMs, String ctxId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            try {
                WDBrowsingContextResult.LocateNodesResult nodes = session.locateNodes(selector, 1, ctxId);
                if (nodes.getNodes() != null && !nodes.getNodes().isEmpty()) {
                    return ToolResult.text("Element found: " + selector);
                }
            } catch (Exception ignored) {
                // Page might be loading, retry
            }
            Thread.sleep(200);
        }

        return ToolResult.error("Timeout waiting for selector '" + selector + "' after " + timeoutMs + "ms");
    }
}

