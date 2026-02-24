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
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.*;

/**
 * Wraps a {@link McpServerTool} (from wd4j-mcp-server) as a regular {@link McpTool}
 * that can be registered in the application's ToolRegistry.
 */
public class BrowserToolAdapter implements McpTool {

    private final McpServerTool delegate;
    private final WebSearchBrowserManager browserManager;

    public BrowserToolAdapter(McpServerTool delegate, WebSearchBrowserManager browserManager) {
        this.delegate = delegate;
        this.browserManager = browserManager;
    }

    @Override
    public ToolSpec getSpec() {
        JsonObject schema = delegate.inputSchema();
        Map<String, ToolSpec.Property> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        if (schema.has("properties")) {
            for (Map.Entry<String, JsonElement> entry : schema.getAsJsonObject("properties").entrySet()) {
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
    public McpToolResponse execute(JsonObject input, String resultVar) {
        try {
            // Auto-inject plugin settings for browser_launch
            if ("browser_launch".equals(delegate.name())) {
                if (!input.has("browserExecutablePath") || input.get("browserExecutablePath").getAsString().isEmpty()) {
                    String path = browserManager.getBrowserPath();
                    if (path != null && !path.isEmpty()) {
                        input.addProperty("browserExecutablePath", path);
                    }
                }
                // Force headless from plugin settings (overrides bot param)
                input.addProperty("headless", browserManager.isHeadless());
            }

            BrowserSession session = browserManager.getSession();
            ToolResult result = delegate.execute(input, session);
            JsonObject response = new JsonObject();

            if (result.isError()) {
                response.addProperty("status", "error");
            } else {
                response.addProperty("status", "ok");
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
            JsonObject error = new JsonObject();
            error.addProperty("status", "error");
            error.addProperty("message", e.getMessage());
            return new McpToolResponse(error, resultVar, null);
        }
    }
}

