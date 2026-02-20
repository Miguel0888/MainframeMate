package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.bund.zrb.type.script.WDEvaluateResult;

import java.lang.reflect.Method;

/**
 * Extracts structured data from the current page using jsoup (if available).
 * Falls back to a basic JS-based extraction if jsoup is not on the classpath.
 */
public class PageExtractTool implements McpServerTool {

    private static final Gson GSON = new Gson();
    private static final boolean JSOUP_AVAILABLE;

    static {
        boolean available;
        try {
            Class.forName("org.jsoup.Jsoup");
            available = true;
        } catch (ClassNotFoundException e) {
            available = false;
        }
        JSOUP_AVAILABLE = available;
    }

    @Override
    public String name() {
        return "page_extract";
    }

    @Override
    public String description() {
        return "Extract structured data from the current page (title, headings, links, text). "
                + "Uses jsoup for HTML parsing if available, otherwise falls back to JS-based extraction.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject mode = new JsonObject();
        mode.addProperty("type", "string");
        mode.addProperty("description", "Extraction mode: 'text', 'links', or 'structured'. Default: text");
        com.google.gson.JsonArray enumArr = new com.google.gson.JsonArray();
        enumArr.add("text");
        enumArr.add("links");
        enumArr.add("structured");
        mode.add("enum", enumArr);
        props.add("mode", mode);

        JsonObject selector = new JsonObject();
        selector.addProperty("type", "string");
        selector.addProperty("description", "CSS selector to scope extraction (optional)");
        props.add("selector", selector);

        JsonObject maxItems = new JsonObject();
        maxItems.addProperty("type", "integer");
        maxItems.addProperty("description", "Maximum number of items (links, headings) to return. Default: 50");
        props.add("maxItems", maxItems);

        JsonObject ctxId = new JsonObject();
        ctxId.addProperty("type", "string");
        ctxId.addProperty("description", "Browsing context ID (optional, uses default)");
        props.add("contextId", ctxId);

        schema.add("properties", props);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String mode = params.has("mode") ? params.get("mode").getAsString() : "text";
        String selector = params.has("selector") ? params.get("selector").getAsString() : null;
        int maxItems = params.has("maxItems") ? params.get("maxItems").getAsInt() : 50;
        String ctxId = params.has("contextId") ? params.get("contextId").getAsString() : null;

        try {
            // Get HTML from the page
            String htmlScript = selector != null
                    ? "(function() { var el = document.querySelector('" + escapeJs(selector) + "'); return el ? el.outerHTML : ''; })()"
                    : "document.documentElement.outerHTML";

            WDEvaluateResult result = session.evaluate(htmlScript, true, ctxId);
            if (!(result instanceof WDEvaluateResult.WDEvaluateResultSuccess)) {
                return ToolResult.error("Failed to get page HTML.");
            }
            String html = ((WDEvaluateResult.WDEvaluateResultSuccess) result).getResult().asString();

            if (JSOUP_AVAILABLE) {
                return extractWithJsoup(html, mode, maxItems);
            } else {
                return extractWithoutJsoup(session, mode, maxItems, ctxId);
            }
        } catch (Exception e) {
            return ToolResult.error("Extraction failed: " + e.getMessage());
        }
    }

    /**
     * jsoup-based extraction via reflection (since jsoup is compileOnly).
     */
    private ToolResult extractWithJsoup(String html, String mode, int maxItems) {
        try {
            // org.jsoup.Jsoup.parse(html)
            Class<?> jsoupClass = Class.forName("org.jsoup.Jsoup");
            Method parseMethod = jsoupClass.getMethod("parse", String.class);
            Object doc = parseMethod.invoke(null, html);

            Class<?> docClass = doc.getClass();
            Class<?> elementClass = Class.forName("org.jsoup.nodes.Element");

            StringBuilder sb = new StringBuilder();

            switch (mode) {
                case "text": {
                    // doc.body().text()
                    Method bodyMethod = docClass.getMethod("body");
                    Object body = bodyMethod.invoke(doc);
                    if (body != null) {
                        Method textMethod = elementClass.getMethod("text");
                        sb.append(textMethod.invoke(body));
                    }
                    break;
                }
                case "links": {
                    // doc.select("a[href]")
                    Method selectMethod = docClass.getMethod("select", String.class);
                    Object elements = selectMethod.invoke(doc, "a[href]");

                    Class<?> elementsClass = Class.forName("org.jsoup.select.Elements");
                    Method sizeMethod = elementsClass.getMethod("size");
                    Method getMethod = elementsClass.getMethod("get", int.class);
                    Method attrMethod = elementClass.getMethod("attr", String.class);
                    Method textMethod = elementClass.getMethod("text");

                    int count = (Integer) sizeMethod.invoke(elements);
                    int limit = Math.min(count, maxItems);
                    for (int i = 0; i < limit; i++) {
                        Object el = getMethod.invoke(elements, i);
                        String href = (String) attrMethod.invoke(el, "href");
                        String text = (String) textMethod.invoke(el);
                        sb.append(text).append(" -> ").append(href).append("\n");
                    }
                    if (count > maxItems) {
                        sb.append("... (").append(count - maxItems).append(" more links)");
                    }
                    break;
                }
                case "structured": {
                    // Title
                    Method titleMethod = docClass.getMethod("title");
                    sb.append("Title: ").append(titleMethod.invoke(doc)).append("\n\n");

                    // Meta description
                    Method selectMethod = docClass.getMethod("select", String.class);
                    Object metaEls = selectMethod.invoke(doc, "meta[name=description]");
                    Class<?> elementsClass = Class.forName("org.jsoup.select.Elements");
                    Method firstMethod = elementsClass.getMethod("first");
                    Object metaEl = firstMethod.invoke(metaEls);
                    if (metaEl != null) {
                        Method attrMethod = elementClass.getMethod("attr", String.class);
                        sb.append("Description: ").append(attrMethod.invoke(metaEl, "content")).append("\n\n");
                    }

                    // Headings h1..h3
                    sb.append("Headings:\n");
                    Object headings = selectMethod.invoke(doc, "h1, h2, h3");
                    Method sizeMethod = elementsClass.getMethod("size");
                    Method getMethod = elementsClass.getMethod("get", int.class);
                    Method textMethod = elementClass.getMethod("text");
                    Method tagNameMethod = elementClass.getMethod("tagName");

                    int headingCount = (Integer) sizeMethod.invoke(headings);
                    int headingLimit = Math.min(headingCount, maxItems);
                    for (int i = 0; i < headingLimit; i++) {
                        Object el = getMethod.invoke(headings, i);
                        sb.append("  [").append(tagNameMethod.invoke(el)).append("] ").append(textMethod.invoke(el)).append("\n");
                    }

                    // Main text (body text, limited)
                    sb.append("\nText:\n");
                    Method bodyMethod = docClass.getMethod("body");
                    Object body = bodyMethod.invoke(doc);
                    if (body != null) {
                        String bodyText = (String) textMethod.invoke(body);
                        if (bodyText.length() > 5000) {
                            bodyText = bodyText.substring(0, 5000) + "\n... [truncated]";
                        }
                        sb.append(bodyText);
                    }
                    break;
                }
                default:
                    return ToolResult.error("Unknown extraction mode: " + mode);
            }

            return ToolResult.text(sb.toString());
        } catch (Exception e) {
            return ToolResult.error("jsoup extraction failed: " + e.getMessage());
        }
    }

    /**
     * Fallback: JS-based extraction when jsoup is not available.
     */
    private ToolResult extractWithoutJsoup(BrowserSession session, String mode, int maxItems, String ctxId) {
        try {
            String script;
            switch (mode) {
                case "text":
                    script = "document.body.innerText";
                    break;
                case "links":
                    script = "(function() { " +
                            "var links = document.querySelectorAll('a[href]'); " +
                            "var result = []; " +
                            "for (var i = 0; i < Math.min(links.length, " + maxItems + "); i++) { " +
                            "  result.push(links[i].textContent.trim() + ' -> ' + links[i].href); " +
                            "} " +
                            "return result.join('\\n'); " +
                            "})()";
                    break;
                case "structured":
                    script = "(function() { " +
                            "var r = 'Title: ' + document.title + '\\n\\n'; " +
                            "var meta = document.querySelector('meta[name=description]'); " +
                            "if (meta) r += 'Description: ' + meta.content + '\\n\\n'; " +
                            "r += 'Headings:\\n'; " +
                            "document.querySelectorAll('h1,h2,h3').forEach(function(h) { " +
                            "  r += '  [' + h.tagName + '] ' + h.textContent.trim() + '\\n'; " +
                            "}); " +
                            "r += '\\nText:\\n' + document.body.innerText.substring(0, 5000); " +
                            "return r; " +
                            "})()";
                    break;
                default:
                    return ToolResult.error("Unknown extraction mode: " + mode);
            }

            WDEvaluateResult result = session.evaluate(script, true, ctxId);
            if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                return ToolResult.text(((WDEvaluateResult.WDEvaluateResultSuccess) result).getResult().asString());
            }
            return ToolResult.error("Extraction script failed.");
        } catch (Exception e) {
            return ToolResult.error("JS extraction failed: " + e.getMessage());
        }
    }

    private String escapeJs(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}

