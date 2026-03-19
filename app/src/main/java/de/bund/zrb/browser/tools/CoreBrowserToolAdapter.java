package de.bund.zrb.browser.tools;

import com.google.gson.JsonObject;
import de.bund.zrb.browser.Wd4jBrowserService;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolConfig;
import de.zrb.bund.newApi.mcp.ToolSpec;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generic adapter that wraps a wd4j {@link McpServerTool} as a core {@link McpTool}.
 *
 * <p>Unlike the plugin's BrowserToolAdapter, this adapter lives in the app-core
 * and uses the centralized {@link Wd4jBrowserService} instead of a plugin-specific
 * browser manager. It does NOT include any research-specific logic (URL boundaries,
 * research sessions, etc.).</p>
 */
public class CoreBrowserToolAdapter implements McpTool {

    private static final Logger LOG = Logger.getLogger(CoreBrowserToolAdapter.class.getName());

    private final McpServerTool delegate;
    private final Wd4jBrowserService browserService;

    public CoreBrowserToolAdapter(McpServerTool delegate, Wd4jBrowserService browserService) {
        this.delegate = delegate;
        this.browserService = browserService;
    }

    @Override
    public ToolSpec getSpec() {
        JsonObject schema = delegate.inputSchema();
        Map<String, ToolSpec.Property> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        if (schema.has("properties")) {
            for (Map.Entry<String, JsonElement> entry : schema.getAsJsonObject("properties").entrySet()) {
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
    public McpToolResponse execute(JsonObject input, String resultVar) {
        String toolName = delegate.name();
        LOG.info("[CoreBrowserTool] Executing: " + toolName);

        long startTime = System.currentTimeMillis();
        try {
            de.bund.zrb.mcpserver.browser.BrowserSession session = browserService.getWd4jSession();
            ToolResult result = delegate.execute(input, session);

            long elapsed = System.currentTimeMillis() - startTime;
            JsonObject response = new JsonObject();

            if (result.isError()) {
                response.addProperty("status", "error");
                LOG.warning("[CoreBrowserTool] " + toolName + " error after " + elapsed + "ms: " + result.getText());
            } else {
                response.addProperty("status", "ok");
                LOG.info("[CoreBrowserTool] " + toolName + " completed in " + elapsed + "ms");
            }

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
                        String t = c.get("text").getAsString();
                        String trimmed = t.trim();
                        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                            try {
                                JsonObject parsed = com.google.gson.JsonParser.parseString(trimmed).getAsJsonObject();
                                for (Map.Entry<String, JsonElement> entry : parsed.entrySet()) {
                                    if (!response.has(entry.getKey())) {
                                        response.add(entry.getKey(), entry.getValue());
                                    }
                                }
                                continue;
                            } catch (Exception ignored) {}
                        }
                        if (text.length() > 0) text.append("\n");
                        text.append(t);
                    }
                }
                response.addProperty("result", text.toString());
            }
            return new McpToolResponse(response, resultVar, null);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            LOG.log(Level.SEVERE, "[CoreBrowserTool] " + toolName + " exception after " + elapsed + "ms", e);
            JsonObject error = new JsonObject();
            error.addProperty("status", "error");
            error.addProperty("message", e.getMessage());
            return new McpToolResponse(error, resultVar, null);
        }
    }
}

