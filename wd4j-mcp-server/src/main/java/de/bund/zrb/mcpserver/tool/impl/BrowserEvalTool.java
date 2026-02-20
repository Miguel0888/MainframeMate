package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;
import de.bund.zrb.type.script.WDEvaluateResult;

/**
 * Evaluates arbitrary JavaScript in the browser.
 */
public class BrowserEvalTool implements McpServerTool {

    private static final Gson GSON = new Gson();

    @Override
    public String name() {
        return "browser_eval";
    }

    @Override
    public String description() {
        return "Evaluate a JavaScript expression in the browser and return the result.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject script = new JsonObject();
        script.addProperty("type", "string");
        script.addProperty("description", "JavaScript expression to evaluate");
        props.add("script", script);

        JsonObject awaitPromise = new JsonObject();
        awaitPromise.addProperty("type", "boolean");
        awaitPromise.addProperty("description", "Whether to await a returned Promise (default: true)");
        props.add("awaitPromise", awaitPromise);

        JsonObject ctxId = new JsonObject();
        ctxId.addProperty("type", "string");
        ctxId.addProperty("description", "Browsing context ID (optional, uses default)");
        props.add("contextId", ctxId);

        schema.add("properties", props);

        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("script");
        schema.add("required", required);

        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        String script = params.get("script").getAsString();
        boolean awaitPromise = !params.has("awaitPromise") || params.get("awaitPromise").getAsBoolean();
        String ctxId = params.has("contextId") ? params.get("contextId").getAsString() : null;

        try {
            WDEvaluateResult result = session.evaluate(script, awaitPromise, ctxId);

            if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                WDEvaluateResult.WDEvaluateResultSuccess success = (WDEvaluateResult.WDEvaluateResultSuccess) result;
                String serialized = GSON.toJson(success.getResult());
                return ToolResult.text("Result: " + serialized);
            } else if (result instanceof WDEvaluateResult.WDEvaluateResultError) {
                WDEvaluateResult.WDEvaluateResultError error = (WDEvaluateResult.WDEvaluateResultError) result;
                String details = GSON.toJson(error.getExceptionDetails());
                return ToolResult.error("Script exception: " + details);
            } else {
                return ToolResult.text("Evaluation completed (unknown result type).");
            }
        } catch (Exception e) {
            return ToolResult.error("Eval failed: " + e.getMessage());
        }
    }
}

