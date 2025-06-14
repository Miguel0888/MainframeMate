package de.zrb.bund.newApi.mcp;

import com.google.gson.JsonObject;

public class McpToolResponse {

    private final JsonObject jsonObject;
    private final String variableName;
    private final String variableValue;

    public McpToolResponse(JsonObject jsonObject, String variableName, String variableValue) {
        this.jsonObject = jsonObject;
        this.variableName = variableName;
        this.variableValue = variableValue;
    }

    public JsonObject asJson() {
        return jsonObject;
    }

    public String asVariableName() {
        return variableName;
    }

    public String asVariableValue() {
        return variableValue;
    }

    public boolean hasVariable() {
        return variableName != null && variableValue != null;
    }
}
