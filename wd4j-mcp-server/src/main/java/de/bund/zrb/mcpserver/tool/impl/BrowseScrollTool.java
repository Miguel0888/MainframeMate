package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

/**
 * Scroll the page or to a specific element.
 */
public class BrowseScrollTool implements McpServerTool {

    @Override
    public String name() {
        return "browse_scroll";
    }

    @Override
    public String description() {
        return "Scroll the page. Use direction='down'/'up' for page scrolling, "
             + "or provide a NodeRef ID via 'ref' to scroll that element into view.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject direction = new JsonObject();
        direction.addProperty("type", "string");
        direction.addProperty("description", "Scroll direction: 'down', 'up', 'bottom', 'top'");
        props.add("direction", direction);

        JsonObject ref = new JsonObject();
        ref.addProperty("type", "string");
        ref.addProperty("description", "NodeRef ID to scroll into view (alternative to direction)");
        props.add("ref", ref);

        schema.add("properties", props);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String ref = params.has("ref") ? params.get("ref").getAsString() : null;
        String direction = params.has("direction") ? params.get("direction").getAsString() : "down";

        try {
            if (ref != null) {
                // Scroll element into view
                de.bund.zrb.mcpserver.browser.NodeRefRegistry.Entry entry =
                        session.getNodeRefRegistry().resolve(ref);
                de.bund.zrb.type.script.WDTarget target =
                        new de.bund.zrb.type.script.WDTarget.ContextTarget(
                                new de.bund.zrb.type.browsingContext.WDBrowsingContext(session.getContextId()));
                java.util.List<de.bund.zrb.type.script.WDLocalValue> args =
                        java.util.Collections.<de.bund.zrb.type.script.WDLocalValue>singletonList(entry.sharedRef);
                session.getDriver().script().callFunction(
                        "function(el){el.scrollIntoView({behavior:'smooth',block:'center'});}",
                        true, target, args);
                return ToolResult.text("Scrolled " + ref + " into view.");
            } else {
                String script;
                switch (direction.toLowerCase()) {
                    case "up":
                        script = "window.scrollBy(0, -window.innerHeight * 0.8)";
                        break;
                    case "top":
                        script = "window.scrollTo(0, 0)";
                        break;
                    case "bottom":
                        script = "window.scrollTo(0, document.body.scrollHeight)";
                        break;
                    case "down":
                    default:
                        script = "window.scrollBy(0, window.innerHeight * 0.8)";
                        break;
                }
                session.evaluate(script, true);
                return ToolResult.text("Scrolled " + direction + ".");
            }
        } catch (Exception e) {
            return ToolResult.error("Scroll failed: " + e.getMessage());
        }
    }
}

