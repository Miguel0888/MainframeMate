package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.bund.zrb.type.script.WDEvaluateResult;

/**
 * Returns the page DOM (HTML or visible text) via JavaScript evaluation.
 */
public class PageDomSnapshotTool implements McpServerTool {

    @Override
    public String name() {
        return "page_dom_snapshot";
    }

    @Override
    public String description() {
        return "Get the page DOM content as HTML or visible text. Supports limiting by CSS selector and max character count.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject mode = new JsonObject();
        mode.addProperty("type", "string");
        mode.addProperty("description", "Output mode: 'html' (outerHTML) or 'text' (innerText). Default: html");
        com.google.gson.JsonArray enumArr = new com.google.gson.JsonArray();
        enumArr.add("html");
        enumArr.add("text");
        mode.add("enum", enumArr);
        props.add("mode", mode);

        JsonObject selector = new JsonObject();
        selector.addProperty("type", "string");
        selector.addProperty("description", "CSS selector to scope the snapshot (optional, defaults to entire document)");
        props.add("selector", selector);

        JsonObject maxChars = new JsonObject();
        maxChars.addProperty("type", "integer");
        maxChars.addProperty("description", "Maximum number of characters to return (optional, default: 100000)");
        props.add("maxChars", maxChars);

        JsonObject ctxId = new JsonObject();
        ctxId.addProperty("type", "string");
        ctxId.addProperty("description", "Browsing context ID (optional, uses default)");
        props.add("contextId", ctxId);

        schema.add("properties", props);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String mode = params.has("mode") ? params.get("mode").getAsString() : "html";
        String selector = params.has("selector") ? params.get("selector").getAsString() : null;
        int maxChars = params.has("maxChars") ? params.get("maxChars").getAsInt() : 100000;
        String ctxId = params.has("contextId") ? params.get("contextId").getAsString() : null;

        try {
            String script = buildScript(mode, selector);
            WDEvaluateResult result = session.evaluate(script, true, ctxId);

            if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                WDEvaluateResult.WDEvaluateResultSuccess success = (WDEvaluateResult.WDEvaluateResultSuccess) result;
                String content = success.getResult().asString();

                // Truncate if needed
                boolean truncated = false;
                if (content != null && content.length() > maxChars) {
                    content = content.substring(0, maxChars);
                    truncated = true;
                }

                String msg = "DOM snapshot (" + mode + ")";
                if (truncated) {
                    msg += " [truncated to " + maxChars + " chars]";
                }
                msg += ":\n" + content;
                return ToolResult.text(msg);
            } else if (result instanceof WDEvaluateResult.WDEvaluateResultError) {
                WDEvaluateResult.WDEvaluateResultError error = (WDEvaluateResult.WDEvaluateResultError) result;
                return ToolResult.error("Script error: " + new Gson().toJson(error.getExceptionDetails()));
            } else {
                return ToolResult.error("Unknown evaluation result type.");
            }
        } catch (Exception e) {
            return ToolResult.error("DOM snapshot failed: " + e.getMessage());
        }
    }

    private String buildScript(String mode, String selector) {
        if ("text".equals(mode)) {
            if (selector != null) {
                return "(function() { var el = document.querySelector('" + escapeJs(selector) + "'); return el ? el.innerText : ''; })()";
            }
            return "document.body.innerText";
        } else {
            // HTML mode
            if (selector != null) {
                return "(function() { var el = document.querySelector('" + escapeJs(selector) + "'); return el ? el.outerHTML : ''; })()";
            }
            return "document.documentElement.outerHTML";
        }
    }

    private String escapeJs(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}

