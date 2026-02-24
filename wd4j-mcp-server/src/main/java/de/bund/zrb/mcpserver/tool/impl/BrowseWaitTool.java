package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.bund.zrb.type.script.WDEvaluateResult;

/**
 * Wait for a condition on the page (element visible, text present, etc.).
 */
public class BrowseWaitTool implements McpServerTool {

    @Override
    public String name() {
        return "web_wait";
    }

    @Override
    public String description() {
        return "Wait for a condition on the page. "
             + "Conditions: 'selector' (CSS selector visible), 'text' (text appears on page), "
             + "'url' (URL contains string), 'idle' (network idle / DOM stable).";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject condition = new JsonObject();
        condition.addProperty("type", "string");
        condition.addProperty("description", "Condition type: 'selector', 'text', 'url', 'idle'");
        props.add("condition", condition);

        JsonObject value = new JsonObject();
        value.addProperty("type", "string");
        value.addProperty("description", "The value for the condition (selector, text, or URL substring)");
        props.add("value", value);

        JsonObject timeout = new JsonObject();
        timeout.addProperty("type", "integer");
        timeout.addProperty("description", "Timeout in milliseconds (default: 10000)");
        props.add("timeout", timeout);

        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("condition");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String condition = params.get("condition").getAsString();
        String value = params.has("value") ? params.get("value").getAsString() : "";
        long timeout = params.has("timeout") ? params.get("timeout").getAsLong() : 10000L;

        try {
            long deadline = System.currentTimeMillis() + timeout;
            boolean met = false;

            while (System.currentTimeMillis() < deadline) {
                switch (condition.toLowerCase()) {
                    case "selector":
                        met = evalBoolean(session, "!!document.querySelector('" + escapeJs(value) + "')");
                        break;
                    case "text":
                        met = evalBoolean(session,
                                "(document.body.innerText||'').indexOf('" + escapeJs(value) + "')>=0");
                        break;
                    case "url":
                        met = evalBoolean(session,
                                "window.location.href.indexOf('" + escapeJs(value) + "')>=0");
                        break;
                    case "idle":
                        // Simple heuristic: wait 2 seconds of DOM stability
                        String hash1 = evalString(session, "document.body.innerHTML.length.toString()");
                        Thread.sleep(1000);
                        String hash2 = evalString(session, "document.body.innerHTML.length.toString()");
                        met = hash1.equals(hash2);
                        break;
                    default:
                        return ToolResult.error("Unknown condition: " + condition);
                }

                if (met) break;
                Thread.sleep(300);
            }

            if (met) {
                return ToolResult.text("Condition '" + condition + "' met.");
            } else {
                return ToolResult.error("Timeout: condition '" + condition + "' not met within " + timeout + "ms.");
            }
        } catch (Exception e) {
            return ToolResult.error("Wait failed: " + e.getMessage());
        }
    }

    private boolean evalBoolean(BrowserSession session, String script) {
        try {
            WDEvaluateResult result = session.evaluate(script, true);
            if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                String val = ((WDEvaluateResult.WDEvaluateResultSuccess) result).getResult().asString();
                return "true".equals(val);
            }
        } catch (Exception ignored) {}
        return false;
    }

    private String evalString(BrowserSession session, String script) {
        try {
            WDEvaluateResult result = session.evaluate(script, true);
            if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                return ((WDEvaluateResult.WDEvaluateResultSuccess) result).getResult().asString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String escapeJs(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}

