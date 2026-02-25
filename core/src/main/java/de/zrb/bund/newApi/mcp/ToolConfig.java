package de.zrb.bund.newApi.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Base class for per-tool configuration.
 * Every tool returns a ToolConfig (or a subclass) from {@link McpTool#getDefaultConfig()}.
 * Subclasses can add their own fields which are automatically serialized/deserialized via Gson.
 *
 * <p>The config is persisted to {@code tool-configs.json} using Gson serialization.
 * A tool with no custom configuration simply returns {@code new ToolConfig()} (empty).</p>
 */
public class ToolConfig {

    /**
     * Returns true if this config has no meaningful fields (i.e. is the empty base config).
     * Subclasses with custom fields should override this.
     */
    public boolean isEmpty() {
        // Serialize to JSON and check if it's an empty object
        JsonObject json = toJson();
        return json.size() == 0;
    }

    /**
     * Serialize this config to a JsonObject via Gson.
     */
    public JsonObject toJson() {
        Gson gson = new GsonBuilder().create();
        String jsonStr = gson.toJson(this);
        return JsonParser.parseString(jsonStr).getAsJsonObject();
    }

    /**
     * Deserialize a JsonObject back into a ToolConfig (or subclass).
     *
     * @param json  the JSON to deserialize
     * @param clazz the target config class
     * @param <T>   the config type
     * @return the deserialized config, or a new empty instance on error
     */
    @SuppressWarnings("unchecked")
    public static <T extends ToolConfig> T fromJson(JsonObject json, Class<T> clazz) {
        try {
            // Check if the class has a static fromFlatJson(JsonObject) method (e.g. WebSearchToolConfig)
            try {
                java.lang.reflect.Method fromFlat = clazz.getMethod("fromFlatJson", JsonObject.class);
                if (java.lang.reflect.Modifier.isStatic(fromFlat.getModifiers())) {
                    return (T) fromFlat.invoke(null, json);
                }
            } catch (NoSuchMethodException ignored) {
                // Fall through to standard Gson deserialization
            }
            Gson gson = new GsonBuilder().create();
            return gson.fromJson(json, clazz);
        } catch (Exception e) {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception ex) {
                throw new RuntimeException("Cannot instantiate " + clazz.getName(), ex);
            }
        }
    }

    /**
     * Pretty-print this config as JSON string.
     */
    public String toPrettyJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
}

