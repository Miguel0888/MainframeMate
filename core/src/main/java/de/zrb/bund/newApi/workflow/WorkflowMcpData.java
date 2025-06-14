package de.zrb.bund.newApi.workflow;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public class WorkflowMcpData {
    @SerializedName("name")
    private final String toolName;
    @SerializedName("tool_input") // Claude naming, OpenAi naming "arguments" would expect a JSON AS STRING
    private Map<String, Object> parameters;
    private String resultVar;

    public WorkflowMcpData(String toolName, Map<String, Object> parameters, String resultVar) {
        this.toolName = toolName;
        this.parameters = parameters;
        this.resultVar = resultVar;
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> edited) {
        this.parameters = edited;
    }

    public String getResultVar() {
        return resultVar;
    }

    public void setResultVar(String resultVar) {
        this.resultVar = resultVar;
    }
}
