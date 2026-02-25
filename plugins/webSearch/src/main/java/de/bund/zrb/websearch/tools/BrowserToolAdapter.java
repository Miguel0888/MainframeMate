package de.bund.zrb.websearch.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.bund.zrb.websearch.plugin.WebSearchBrowserManager;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolConfig;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wraps a {@link McpServerTool} (from wd4j-mcp-server) as a regular {@link McpTool}
 * that can be registered in the application's ToolRegistry.
 */
public class BrowserToolAdapter implements McpTool {

    private static final Logger LOG = Logger.getLogger(BrowserToolAdapter.class.getName());

    /** Tools whose URL parameter must be checked against whitelist/blacklist boundaries. */
    private static final Set<String> URL_CHECKED_TOOLS = new HashSet<>(Arrays.asList(
            "research_open"
    ));

    private final McpServerTool delegate;
    private final WebSearchBrowserManager browserManager;

    /** Shared boundary checker – loaded from plugin settings on first use, refreshed on settings change. */
    private static volatile UrlBoundaryChecker boundaryChecker;

    public BrowserToolAdapter(McpServerTool delegate, WebSearchBrowserManager browserManager) {
        this.delegate = delegate;
        this.browserManager = browserManager;
    }

    /** Default blacklist: block URLs whose host is a bare IP address (IPv4 or IPv6). */
    private static final String DEFAULT_BLACKLIST =
            "# IPv4-Adressen blockieren (http(s)://123.45.67.89)\n"
          + "https?://\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}([:/]|$)\n"
          + "# IPv6-Adressen blockieren (http(s)://[::1])\n"
          + "https?://\\[[:0-9a-fA-F]+\\]";

    /**
     * Reload the URL boundary checker from the current plugin settings.
     * Called when the WebSearch settings are saved.
     */
    public static void reloadBoundaries(Map<String, String> settings) {
        List<String> whitelist = UrlBoundaryChecker.parsePatternList(
                settings.getOrDefault("urlWhitelist", ""));
        String blacklistValue = settings.containsKey("urlBlacklist")
                ? settings.get("urlBlacklist")
                : DEFAULT_BLACKLIST;
        List<String> blacklist = UrlBoundaryChecker.parsePatternList(blacklistValue);
        boundaryChecker = new UrlBoundaryChecker(whitelist, blacklist);
        LOG.info("[BrowserToolAdapter] URL boundaries reloaded: whitelist=" + whitelist.size()
                + " patterns, blacklist=" + blacklist.size() + " patterns");
    }

    /**
     * Get the current boundary checker, loading from settings if necessary.
     */
    private UrlBoundaryChecker getBoundaryChecker() {
        if (boundaryChecker == null) {
            reloadBoundaries(browserManager.loadSettings());
        }
        return boundaryChecker;
    }

    @Override
    public ToolSpec getSpec() {
        JsonObject schema = delegate.inputSchema();
        Map<String, ToolSpec.Property> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        if (schema.has("properties")) {
            for (Map.Entry<String, JsonElement> entry : schema.getAsJsonObject("properties").entrySet()) {
                // Hide contextId – the active tab is managed automatically
                if ("contextId".equals(entry.getKey())) continue;
                JsonObject propObj = entry.getValue().getAsJsonObject();
                String type = propObj.has("type") ? propObj.get("type").getAsString() : "string";
                String desc = propObj.has("description") ? propObj.get("description").getAsString() : "";
                props.put(entry.getKey(), new ToolSpec.Property(type, desc));
            }
        }
        if (schema.has("required") && schema.get("required").isJsonArray()) {
            for (JsonElement el : schema.getAsJsonArray("required")) {
                required.add(el.getAsString());
            }
        }

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(props, required);
        return new ToolSpec(delegate.name(), delegate.description(), inputSchema, null);
    }

    @Override
    public ToolConfig getDefaultConfig() {
        return WebSearchToolConfig.fromSchema(delegate.inputSchema());
    }

    @Override
    public Class<? extends ToolConfig> getConfigClass() {
        return WebSearchToolConfig.class;
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        String toolName = delegate.name();
        LOG.info("[BrowserToolAdapter] Executing tool: " + toolName + " | input: "
                + (input.toString().length() > 500 ? input.toString().substring(0, 500) + "..." : input.toString()));

        // ── URL boundary check ──────────────────────────────────────
        if (URL_CHECKED_TOOLS.contains(toolName)) {
            String url = extractUrlFromInput(input);
            if (url != null && !url.isEmpty()) {
                String error = getBoundaryChecker().check(url);
                if (error != null) {
                    LOG.warning("[BrowserToolAdapter] URL blocked: " + url);
                    JsonObject errorResult = new JsonObject();
                    errorResult.addProperty("status", "error");
                    errorResult.addProperty("message", error);
                    return new McpToolResponse(errorResult, resultVar, null);
                }
            }
        }

        long startTime = System.currentTimeMillis();

        try {
            // getSession() auto-launches and connects the browser if needed
            BrowserSession session = browserManager.getSession();
            ToolResult result = delegate.execute(input, session);

            long elapsed = System.currentTimeMillis() - startTime;
            JsonObject response = new JsonObject();

            if (result.isError()) {
                response.addProperty("status", "error");
                LOG.warning("[BrowserToolAdapter] Tool " + toolName + " returned error after " + elapsed + "ms: "
                        + result.getText());
            } else {
                response.addProperty("status", "ok");
                LOG.info("[BrowserToolAdapter] Tool " + toolName + " completed in " + elapsed + "ms"
                        + " | result length: " + (result.getText() != null ? result.getText().length() : 0));
            }

            // Always include the active tab ID so the bot knows where it is
            String contextId = session.getContextId();
            if (contextId != null) {
                response.addProperty("tabId", contextId);
            }

            JsonElement contentJson = result.toJson();
            if (contentJson.isJsonObject() && contentJson.getAsJsonObject().has("content")) {
                JsonArray content = contentJson.getAsJsonObject().getAsJsonArray("content");
                StringBuilder text = new StringBuilder();
                for (JsonElement el : content) {
                    JsonObject c = el.getAsJsonObject();
                    if ("text".equals(c.get("type").getAsString())) {
                        if (text.length() > 0) text.append("\n");
                        text.append(c.get("text").getAsString());
                    }
                }
                response.addProperty("result", text.toString());
            }
            response.add("raw", contentJson);
            return new McpToolResponse(response, resultVar, null);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            LOG.log(Level.SEVERE, "[BrowserToolAdapter] Tool " + toolName + " threw exception after " + elapsed + "ms", e);
            JsonObject error = new JsonObject();
            error.addProperty("status", "error");
            error.addProperty("message", e.getMessage());
            return new McpToolResponse(error, resultVar, null);
        }
    }

    /**
     * Extract URL from tool input JSON. Handles both direct "url" field
     * and nested "arguments.url" (older format).
     */
    private String extractUrlFromInput(JsonObject input) {
        if (input.has("url") && input.get("url").isJsonPrimitive()) {
            return input.get("url").getAsString();
        }
        if (input.has("arguments") && input.get("arguments").isJsonObject()) {
            JsonObject args = input.getAsJsonObject("arguments");
            if (args.has("url") && args.get("url").isJsonPrimitive()) {
                return args.get("url").getAsString();
            }
        }
        return null;
    }
}

