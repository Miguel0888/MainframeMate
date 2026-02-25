package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

/**
 * Type text into an element identified by a NodeRef ID.
 */
public class BrowseTypeTool implements McpServerTool {

    @Override
    public String name() {
        return "web_type";
    }

    @Override
    public String description() {
        return "Type text into an input element by its NodeRef ID (e.g. 'n3'). "
             + "Set submit=true to press Enter after typing.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject ref = new JsonObject();
        ref.addProperty("type", "string");
        ref.addProperty("description", "NodeRef ID of the input element");
        props.add("ref", ref);

        JsonObject text = new JsonObject();
        text.addProperty("type", "string");
        text.addProperty("description", "Text to type");
        props.add("text", text);

        JsonObject clearFirst = new JsonObject();
        clearFirst.addProperty("type", "boolean");
        clearFirst.addProperty("description", "Clear existing value before typing (default: true)");
        props.add("clearFirst", clearFirst);

        JsonObject submit = new JsonObject();
        submit.addProperty("type", "boolean");
        submit.addProperty("description", "Press Enter after typing to submit (default: false)");
        props.add("submit", submit);

        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("ref");
        required.add("text");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        // Accept both 'ref' and common aliases
        String ref = null;
        for (String key : new String[]{"ref", "nodeId", "nodeRef", "id", "element"}) {
            if (params.has(key) && !params.get(key).isJsonNull()) {
                ref = params.get(key).getAsString();
                break;
            }
        }
        if (ref == null || ref.isEmpty()) {
            return ToolResult.error("Missing required parameter 'ref' (NodeRef ID, e.g. 'n1').");
        }
        String text = params.get("text").getAsString();
        boolean clearFirst = !params.has("clearFirst") || params.get("clearFirst").getAsBoolean();
        boolean submit = params.has("submit") && params.get("submit").getAsBoolean();

        try {
            session.typeNodeRef(ref, text, clearFirst);

            if (submit) {
                // Submit by dispatching Enter keypress + form submit
                de.bund.zrb.mcpserver.browser.NodeRefRegistry.Entry entry =
                        session.getNodeRefRegistry().resolve(ref);
                de.bund.zrb.type.script.WDTarget target =
                        new de.bund.zrb.type.script.WDTarget.ContextTarget(
                                new de.bund.zrb.type.browsingContext.WDBrowsingContext(session.getContextId()));
                java.util.List<de.bund.zrb.type.script.WDLocalValue> args =
                        java.util.Collections.<de.bund.zrb.type.script.WDLocalValue>singletonList(entry.sharedRef);
                session.getDriver().script().callFunction(
                        "function(el){"
                      + "el.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',bubbles:true}));"
                      + "el.dispatchEvent(new KeyboardEvent('keypress',{key:'Enter',code:'Enter',bubbles:true}));"
                      + "el.dispatchEvent(new KeyboardEvent('keyup',{key:'Enter',code:'Enter',bubbles:true}));"
                      + "var f=el.closest('form');if(f)f.submit();"
                      + "}",
                        true, target, args);

                // Wait for navigation/response
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }

            return ToolResult.text("Typed into " + ref + (submit ? " and submitted" : "")
                    + ".\nUse research_menu to see the result and interactive elements.");
        } catch (Exception e) {
            return ToolResult.error("Type failed: " + e.getMessage());
        }
    }
}

