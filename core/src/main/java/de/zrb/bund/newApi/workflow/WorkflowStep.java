package de.zrb.bund.newApi.workflow;

import java.util.Map;

@Deprecated
public class WorkflowStep {
    private final String toolName;
    private Map<String, Object> parameters;

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

    public void setParameters(Map<String, Object> edited) {
        this.parameters = edited;
    }
}
