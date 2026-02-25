package de.bund.zrb.websearch.tools;

import com.google.gson.*;
import de.zrb.bund.newApi.mcp.ToolConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dynamic ToolConfig for web search browser tools.
 * Stores config values as a flat key-value map, built from the tool's input schema.
 *
 * <p>Serializes directly as flat JSON (no "values" wrapper), e.g.:
 * {@code {"mode": "research", "headless": false}}</p>
 */
public class WebSearchToolConfig extends ToolConfig {

    private transient final Map<String, Object> entries = new LinkedHashMap<>();

    public WebSearchToolConfig() {
        // Default constructor for Gson
    }

    public Map<String, Object> getValues() {
        return entries;
    }

    public Object get(String key) {
        return entries.get(key);
    }

    public void put(String key, Object value) {
        entries.put(key, value);
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    // ── Custom flat serialization (no "values" wrapper) ──────────

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, Object> e : entries.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Boolean)       json.addProperty(e.getKey(), (Boolean) v);
            else if (v instanceof Number)   json.addProperty(e.getKey(), (Number) v);
            else                            json.addProperty(e.getKey(), String.valueOf(v));
        }
        return json;
    }

    /**
     * Deserialize from a flat JSON object back into a WebSearchToolConfig.
     */
    public static WebSearchToolConfig fromFlatJson(JsonObject json) {
        WebSearchToolConfig config = new WebSearchToolConfig();
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            JsonElement val = e.getValue();
            if (val.isJsonPrimitive()) {
                JsonPrimitive prim = val.getAsJsonPrimitive();
                if (prim.isBoolean())       config.put(e.getKey(), prim.getAsBoolean());
                else if (prim.isNumber())   config.put(e.getKey(), prim.getAsNumber());
                else                        config.put(e.getKey(), prim.getAsString());
            }
        }
        return config;
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

