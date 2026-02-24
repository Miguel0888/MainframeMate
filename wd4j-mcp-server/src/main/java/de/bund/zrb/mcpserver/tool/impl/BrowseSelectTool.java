package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

/**
 * Select an option in a <select> element by NodeRef.
 */
public class BrowseSelectTool implements McpServerTool {

    @Override
    public String name() {
        return "web_select";
    }

    @Override
    public String description() {
        return "Select an option in a dropdown (<select>) by its NodeRef ID. "
             + "Specify the option by value, label, or index.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject ref = new JsonObject();
        ref.addProperty("type", "string");
        ref.addProperty("description", "NodeRef ID of the <select> element");
        props.add("ref", ref);

        JsonObject value = new JsonObject();
        value.addProperty("type", "string");
        value.addProperty("description", "Option value attribute to select");
        props.add("value", value);

        JsonObject label = new JsonObject();
        label.addProperty("type", "string");
        label.addProperty("description", "Option visible text to select");
        props.add("label", label);

        JsonObject index = new JsonObject();
        index.addProperty("type", "integer");
        index.addProperty("description", "Option index (0-based) to select");
        props.add("index", index);

        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("ref");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String ref = params.get("ref").getAsString();
        String value = params.has("value") ? params.get("value").getAsString() : null;
        String label = params.has("label") ? params.get("label").getAsString() : null;
        Integer index = params.has("index") ? params.get("index").getAsInt() : null;

        try {
            session.selectOptionNodeRef(ref, value, label, index);
            return ToolResult.text("Selected option in " + ref + ".");
        } catch (Exception e) {
            return ToolResult.error("Select failed: " + e.getMessage());
        }
    }
}

