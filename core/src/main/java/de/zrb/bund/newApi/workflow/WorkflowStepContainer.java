package de.zrb.bund.newApi.workflow;

import java.util.Collections;
import java.util.List;

public class WorkflowStepContainer {
    private String id; // optional
    private WorkflowMcpData mcp; // Pflichtfeld
    private List<String> dependsOn; // optional

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public WorkflowMcpData getMcp() { return mcp; }
    public void setMcp(WorkflowMcpData mcp) { this.mcp = mcp; }

    public List<String> getDependsOn() {
        return dependsOn != null ? dependsOn : Collections.emptyList();
    }

    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn;
    }
}
