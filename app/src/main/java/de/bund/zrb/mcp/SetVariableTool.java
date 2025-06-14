package de.bund.zrb.mcp;

import com.google.gson.JsonObject;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class SetVariableTool implements McpTool {

    private final MainframeContext context;

    public SetVariableTool(MainframeContext context) {
        this.context = context;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> props = new LinkedHashMap<>();
        props.put("key", new ToolSpec.Property("string", "Name der Variable"));
        props.put("value", new ToolSpec.Property("string", "Wert der Variable"));

        Map<String, Object> defaultValues = new HashMap<>();
        defaultValues.put("key", "output_path");
        defaultValues.put("value", "ABC.FILE.DAT");

        return new ToolSpec(
                "set_variable",
                "Setzt eine Variable im Workflow-Kontext",
                new ToolSpec.InputSchema(props, Arrays.asList("key", "value")),
                defaultValues
        );
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        String key = input.get("key").getAsString();
        String value = input.get("value").getAsString();
        context.getVariableRegistry().set(key, value);

        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");

        String result = "";
        return new McpToolResponse(response, resultVar, result);    }
}
