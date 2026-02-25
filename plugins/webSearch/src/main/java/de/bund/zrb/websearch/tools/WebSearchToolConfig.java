package de.bund.zrb.websearch.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.zrb.bund.newApi.mcp.ToolConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dynamic ToolConfig for web search browser tools.
 * Stores config values as a flat key-value map, built from the tool's input schema.
 */
public class WebSearchToolConfig extends ToolConfig {

    private final Map<String, Object> values = new LinkedHashMap<>();

    public WebSearchToolConfig() {
        // Default constructor for Gson
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public void put(String key, Object value) {
        values.put(key, value);
    }

    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * Build a WebSearchToolConfig from a tool's input schema.
     */
    public static WebSearchToolConfig fromSchema(JsonObject schema) {
        WebSearchToolConfig config = new WebSearchToolConfig();
        if (schema.has("properties")) {
            for (Map.Entry<String, JsonElement> entry : schema.getAsJsonObject("properties").entrySet()) {
                String key = entry.getKey();
                if ("contextId".equals(key)) continue;
                JsonObject propObj = entry.getValue().getAsJsonObject();
                String type = propObj.has("type") ? propObj.get("type").getAsString() : "string";
                switch (type) {
                    case "boolean": config.put(key, false); break;
                    case "integer": case "number": config.put(key, 0); break;
                    default: config.put(key, ""); break;
                }
            }
        }
        return config;
    }
}

