package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.bund.zrb.type.script.WDEvaluateResult;

/**
 * Reads the visible text content of the current page.
 * This is the primary tool for extracting article text, news content, etc.
 * Unlike web_snapshot (which focuses on interactive elements), this tool
 * returns the full readable text of the page.
 */
public class BrowseReadPageTool implements McpServerTool {

    @Override
    public String name() {
        return "web_read_page";
    }

    @Override
    public String description() {
        return "Read the visible text content of the current page. "
             + "Use this to extract article text, news content, search results text, etc. "
             + "Returns up to 8000 characters of page text. "
             + "For interacting with elements (click, type), use web_snapshot instead.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject selector = new JsonObject();
        selector.addProperty("type", "string");
        selector.addProperty("description", "Optional CSS selector to read only a specific section (e.g. 'article', 'main', '#content')");
        props.add("selector", selector);

        JsonObject maxLength = new JsonObject();
        maxLength.addProperty("type", "integer");
        maxLength.addProperty("description", "Maximum characters to return (default: 8000, max: 15000)");
        props.add("maxLength", maxLength);

        schema.add("properties", props);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String selector = params != null && params.has("selector") ? params.get("selector").getAsString() : null;
        int maxLen = params != null && params.has("maxLength") ? params.get("maxLength").getAsInt() : 8000;
        if (maxLen > 15000) maxLen = 15000;
        if (maxLen < 100) maxLen = 100;

        try {
            // Get page title and URL
            String titleScript = "document.title";
            String urlScript = "window.location.href";
            String title = evalString(session, titleScript);
            String url = evalString(session, urlScript);

            // Build text extraction script
            String targetExpr;
            if (selector != null && !selector.isEmpty()) {
                targetExpr = "document.querySelector('" + escapeJs(selector) + "')";
            } else {
                // Try common article containers first, fall back to body
                targetExpr = "document.querySelector('article') || document.querySelector('[role=main]') "
                           + "|| document.querySelector('main') || document.querySelector('#article-container') "
                           + "|| document.querySelector('.article-body') || document.querySelector('.caas-body') "
                           + "|| document.body";
            }

            String readScript = "(function(){"
                    + "var el=" + targetExpr + ";"
                    + "if(!el) el=document.body;"
                    + "var text=el.innerText||el.textContent||'';"
                    // Clean up excessive whitespace
                    + "text=text.replace(/\\t/g,' ').replace(/ {3,}/g,' ').replace(/\\n{3,}/g,'\\n\\n').trim();"
                    + "return text.substring(0," + maxLen + ");"
                    + "})()";

            String pageText = evalString(session, readScript);

            if (pageText.isEmpty()) {
                return ToolResult.text("Page: " + title + "\nURL: " + url + "\n\n(No readable text found on this page)");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Page: ").append(title).append("\n");
            sb.append("URL: ").append(url).append("\n");
            sb.append("────────────────────────────────────────\n");
            sb.append(pageText);

            if (pageText.length() >= maxLen - 10) {
                sb.append("\n\n[… text truncated at ").append(maxLen).append(" chars. Use maxLength parameter for more, or selector to focus on a section.]");
            }

            return ToolResult.text(sb.toString());
        } catch (Exception e) {
            return ToolResult.error("Read page failed: " + e.getMessage());
        }
    }

    private String evalString(BrowserSession session, String script) {
        try {
            Object result = session.evaluate(script, true);
            if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                String s = ((WDEvaluateResult.WDEvaluateResultSuccess) result).getResult().asString();
                if (s != null && !s.startsWith("[Object:")) return s;
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String escapeJs(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}

