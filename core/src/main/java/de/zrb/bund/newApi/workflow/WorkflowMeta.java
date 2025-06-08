package de.zrb.bund.newApi.workflow;

import java.util.LinkedHashMap;
import java.util.Map;

public class WorkflowMeta {
    private Map<String, String> variables = new LinkedHashMap<>();
    private String description;
    private String createdBy;
    private String createdAt;

    public Map<String, String> getVariables() { return variables; }
    public void setVariables(Map<String, String> variables) { this.variables = variables; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
