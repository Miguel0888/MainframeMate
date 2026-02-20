package de.bund.zrb.mcp;

import com.google.gson.JsonObject;
import de.bund.zrb.mcp.registry.McpServerManager;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.*;

/**
 * Built-in MCP Tool for web searching / browsing via the wd4j-mcp-server.
 *
 * <p>This tool delegates to the built-in "Websearch" MCP server.
 * When called, it ensures the server is running, then forwards the request.
 * The tool appears in the ToolPolicy dialog and can be enabled/disabled there.</p>
 *
 * <p>Supported actions (via the {@code action} parameter):</p>
 * <ul>
 *     <li>{@code navigate} – open a URL and return page text</li>
 *     <li>{@code search} – perform a web search (navigate to search engine)</li>
 *     <li>{@code screenshot} – take a screenshot of the current page</li>
 *     <li>{@code extract} – extract structured data from the current page</li>
 *     <li>{@code eval} – evaluate JavaScript on the current page</li>
 *     <li>{@code click} – click an element by CSS selector</li>
 *     <li>{@code type} – type text into an input element</li>
 *     <li>{@code close} – close the browser session</li>
 * </ul>
 *
 * accessType: WRITE (browser interaction is a side effect)
 */
public class WebsearchTool implements McpTool {

    private static final String SERVER_NAME = "Websearch";

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<>();
        properties.put("action", new ToolSpec.Property("string",
                "Aktion: navigate, search, screenshot, extract, eval, click, type, close"));
        properties.put("url", new ToolSpec.Property("string",
                "URL zum Navigieren (für action=navigate)"));
        properties.put("query", new ToolSpec.Property("string",
                "Suchbegriff (für action=search, navigiert zu einer Suchmaschine)"));
        properties.put("selector", new ToolSpec.Property("string",
                "CSS-Selector (für action=click, action=type)"));
        properties.put("text", new ToolSpec.Property("string",
                "Text zum Tippen (für action=type)"));
        properties.put("script", new ToolSpec.Property("string",
                "JavaScript (für action=eval)"));
        properties.put("mode", new ToolSpec.Property("string",
                "Extraktionsmodus: text, links, structured (für action=extract, default: text)"));
        properties.put("maxChars", new ToolSpec.Property("integer",
                "Max. Zeichenanzahl für Seiteninhalt (default: 50000)"));

        ToolSpec.InputSchema schema = new ToolSpec.InputSchema(
                properties, Collections.singletonList("action"));

        Map<String, Object> example = new LinkedHashMap<>();
        example.put("action", "search");
        example.put("query", "Java WebDriver BiDi specification");

        return new ToolSpec("websearch",
                "Web-Browsing: Webseiten navigieren, Inhalte lesen, suchen, Screenshots erstellen. "
                + "Nutzt einen headless Browser über WebDriver BiDi.",
                schema, example);
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        String action = input.has("action") ? input.get("action").getAsString() : null;
        if (action == null || action.isEmpty()) {
            return errorResponse("Parameter 'action' ist erforderlich.", resultVar);
        }

        McpServerManager manager = McpServerManager.getInstance();

        // Ensure Websearch server is running
        if (!manager.isRunning(SERVER_NAME)) {
            return errorResponse(
                    "Der Websearch-Server ist nicht aktiv. Bitte in der MCP-Server-Verwaltung (🔌 MCP) aktivieren.",
                    resultVar);
        }

        try {
            switch (action) {
                case "navigate":
                    return handleNavigate(manager, input, resultVar);
                case "search":
                    return handleSearch(manager, input, resultVar);
                case "screenshot":
                    return handleScreenshot(manager, input, resultVar);
                case "extract":
                    return handleExtract(manager, input, resultVar);
                case "eval":
                    return handleEval(manager, input, resultVar);
                case "click":
                    return handleClick(manager, input, resultVar);
                case "type":
                    return handleType(manager, input, resultVar);
                case "close":
                    return handleClose(manager, input, resultVar);
                default:
                    return errorResponse("Unbekannte Aktion: " + action
                            + ". Erlaubt: navigate, search, screenshot, extract, eval, click, type, close",
                            resultVar);
            }
        } catch (Exception e) {
            return errorResponse("Websearch-Fehler: " + e.getMessage(), resultVar);
        }
    }

    // ── Action handlers ─────────────────────────────────────────────

    private McpToolResponse handleNavigate(McpServerManager manager, JsonObject input, String resultVar) throws Exception {
        String url = requireString(input, "url", "Parameter 'url' ist für action=navigate erforderlich.");

        // 1. Navigate
        JsonObject navArgs = new JsonObject();
        navArgs.addProperty("url", url);
        callMcpTool(manager, "browser_navigate", navArgs);

        // 2. Wait for load
        JsonObject waitArgs = new JsonObject();
        waitArgs.addProperty("type", "load");
        waitArgs.addProperty("timeoutMs", 15000);
        callMcpTool(manager, "browser_wait_for", waitArgs);

        // 3. Extract text
        int maxChars = input.has("maxChars") ? input.get("maxChars").getAsInt() : 50000;
        JsonObject snapArgs = new JsonObject();
        snapArgs.addProperty("mode", "text");
        snapArgs.addProperty("maxChars", maxChars);
        JsonObject snapResult = callMcpTool(manager, "page_dom_snapshot", snapArgs);

        String text = extractTextFromResult(snapResult);
        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.addProperty("url", url);
        response.addProperty("content", text);
        return new McpToolResponse(response, resultVar, null);
    }

    private McpToolResponse handleSearch(McpServerManager manager, JsonObject input, String resultVar) throws Exception {
        String query = requireString(input, "query", "Parameter 'query' ist für action=search erforderlich.");

        // Navigate to search engine
        String searchUrl = "https://html.duckduckgo.com/html/?q=" + urlEncode(query);
        JsonObject navArgs = new JsonObject();
        navArgs.addProperty("url", searchUrl);
        callMcpTool(manager, "browser_navigate", navArgs);

        // Wait
        JsonObject waitArgs = new JsonObject();
        waitArgs.addProperty("type", "load");
        waitArgs.addProperty("timeoutMs", 15000);
        callMcpTool(manager, "browser_wait_for", waitArgs);

        // Extract results
        int maxChars = input.has("maxChars") ? input.get("maxChars").getAsInt() : 50000;
        JsonObject snapArgs = new JsonObject();
        snapArgs.addProperty("mode", "text");
        snapArgs.addProperty("maxChars", maxChars);
        JsonObject snapResult = callMcpTool(manager, "page_dom_snapshot", snapArgs);

        String text = extractTextFromResult(snapResult);
        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.addProperty("query", query);
        response.addProperty("searchUrl", searchUrl);
        response.addProperty("content", text);
        return new McpToolResponse(response, resultVar, null);
    }

    private McpToolResponse handleScreenshot(McpServerManager manager, JsonObject input, String resultVar) throws Exception {
        JsonObject result = callMcpTool(manager, "browser_screenshot", new JsonObject());
        String text = extractTextFromResult(result);
        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.addProperty("result", text);
        if (result != null) response.add("raw", result);
        return new McpToolResponse(response, resultVar, null);
    }

    private McpToolResponse handleExtract(McpServerManager manager, JsonObject input, String resultVar) throws Exception {
        String mode = input.has("mode") ? input.get("mode").getAsString() : "text";
        JsonObject args = new JsonObject();
        args.addProperty("mode", mode);
        if (input.has("selector")) args.addProperty("selector", input.get("selector").getAsString());
        if (input.has("maxItems")) args.addProperty("maxItems", input.get("maxItems").getAsInt());
        JsonObject result = callMcpTool(manager, "page_extract", args);

        String text = extractTextFromResult(result);
        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.addProperty("content", text);
        return new McpToolResponse(response, resultVar, null);
    }

    private McpToolResponse handleEval(McpServerManager manager, JsonObject input, String resultVar) throws Exception {
        String script = requireString(input, "script", "Parameter 'script' ist für action=eval erforderlich.");
        JsonObject args = new JsonObject();
        args.addProperty("script", script);
        JsonObject result = callMcpTool(manager, "browser_eval", args);

        String text = extractTextFromResult(result);
        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.addProperty("result", text);
        return new McpToolResponse(response, resultVar, null);
    }

    private McpToolResponse handleClick(McpServerManager manager, JsonObject input, String resultVar) throws Exception {
        String selector = requireString(input, "selector", "Parameter 'selector' ist für action=click erforderlich.");
        JsonObject args = new JsonObject();
        args.addProperty("selector", selector);
        callMcpTool(manager, "browser_click_css", args);

        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.addProperty("message", "Klick auf: " + selector);
        return new McpToolResponse(response, resultVar, null);
    }

    private McpToolResponse handleType(McpServerManager manager, JsonObject input, String resultVar) throws Exception {
        String selector = requireString(input, "selector", "Parameter 'selector' ist für action=type erforderlich.");
        String text = requireString(input, "text", "Parameter 'text' ist für action=type erforderlich.");
        JsonObject args = new JsonObject();
        args.addProperty("selector", selector);
        args.addProperty("text", text);
        args.addProperty("clearFirst", input.has("clearFirst") && input.get("clearFirst").getAsBoolean());
        callMcpTool(manager, "browser_type_css", args);

        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.addProperty("message", "Text eingegeben in: " + selector);
        return new McpToolResponse(response, resultVar, null);
    }

    private McpToolResponse handleClose(McpServerManager manager, JsonObject input, String resultVar) throws Exception {
        callMcpTool(manager, "browser_close", new JsonObject());
        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.addProperty("message", "Browser-Session geschlossen.");
        return new McpToolResponse(response, resultVar, null);
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /**
     * Calls a tool on the Websearch MCP server by finding the registered proxy tool
     * in the ToolRegistry and executing it.
     */
    private JsonObject callMcpTool(McpServerManager manager, String toolName, JsonObject args) throws Exception {
        // The tool is registered as "Websearch/<toolName>" in the ToolRegistry
        String qualifiedName = SERVER_NAME + "/" + toolName;
        de.zrb.bund.newApi.mcp.McpTool tool = de.bund.zrb.runtime.ToolRegistryImpl.getInstance()
                .getToolByName(qualifiedName);
        if (tool == null) {
            throw new IllegalStateException("MCP Tool nicht gefunden: " + qualifiedName
                    + ". Ist der Websearch-Server gestartet?");
        }
        McpToolResponse resp = tool.execute(args, null);
        return resp.asJson();
    }

    private String extractTextFromResult(JsonObject result) {
        if (result == null) return "";
        if (result.has("result") && result.get("result").isJsonPrimitive()) {
            return result.get("result").getAsString();
        }
        if (result.has("content") && result.get("content").isJsonPrimitive()) {
            return result.get("content").getAsString();
        }
        return result.toString();
    }

    private String requireString(JsonObject input, String key, String errorMsg) {
        if (!input.has(key) || input.get(key).getAsString().isEmpty()) {
            throw new IllegalArgumentException(errorMsg);
        }
        return input.get(key).getAsString();
    }

    private McpToolResponse errorResponse(String message, String resultVar) {
        JsonObject response = new JsonObject();
        response.addProperty("status", "error");
        response.addProperty("message", message);
        return new McpToolResponse(response, resultVar, null);
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}

