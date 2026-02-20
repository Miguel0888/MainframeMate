package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Launches a browser in headless mode, detects its BiDi endpoint, and connects.
 */
public class BrowserLaunchTool implements McpServerTool {

    @Override
    public String name() {
        return "browser_launch";
    }

    @Override
    public String description() {
        return "Launch a browser in headless mode, detect its BiDi WebSocket endpoint, and connect.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject exePath = new JsonObject();
        exePath.addProperty("type", "string");
        exePath.addProperty("description", "Path to the browser executable");
        props.add("browserExecutablePath", exePath);

        JsonObject args = new JsonObject();
        args.addProperty("type", "array");
        JsonObject itemsObj = new JsonObject();
        itemsObj.addProperty("type", "string");
        args.add("items", itemsObj);
        args.addProperty("description", "Additional command-line arguments for the browser");
        props.add("args", args);

        JsonObject headless = new JsonObject();
        headless.addProperty("type", "boolean");
        headless.addProperty("description", "Launch in headless mode (default: true)");
        props.add("headless", headless);

        JsonObject timeout = new JsonObject();
        timeout.addProperty("type", "integer");
        timeout.addProperty("description", "Timeout in ms to wait for BiDi endpoint (default: 30000)");
        props.add("timeoutMs", timeout);

        schema.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("browserExecutablePath");
        schema.add("required", required);

        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String browserPath = params.get("browserExecutablePath").getAsString();
        boolean headless = !params.has("headless") || params.get("headless").getAsBoolean();
        long timeoutMs = params.has("timeoutMs") ? params.get("timeoutMs").getAsLong() : 30000L;

        List<String> args = new ArrayList<String>();
        if (params.has("args") && params.get("args").isJsonArray()) {
            for (JsonElement el : params.getAsJsonArray("args")) {
                args.add(el.getAsString());
            }
        }

        try {
            session.launchAndConnect(browserPath, args, headless, timeoutMs);
            String contextId = session.getContextId();

            StringBuilder sb = new StringBuilder();
            sb.append("Browser launched and connected.");
            if (contextId != null) {
                sb.append("\ncontextId: ").append(contextId);
            }
            return ToolResult.text(sb.toString());
        } catch (Exception e) {
            return ToolResult.error("Failed to launch browser: " + e.getMessage());
        }
    }
}

