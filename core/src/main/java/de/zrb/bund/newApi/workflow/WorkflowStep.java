package de.zrb.bund.newApi.workflow;

import java.util.Map;

public class WorkflowStep {
    private final String toolName;
    private final Map<String, Object> parameters;

    public WorkflowStep(String toolName, Map<String, Object> parameters) {
        this.toolName = toolName;
        this.parameters = parameters;
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }
}
