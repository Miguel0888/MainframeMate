package de.zrb.bund.newApi.workflow;

import java.util.Collections;
import java.util.List;

public class WorkflowStepContainer {
    private String id; // optional
    private WorkflowStep mcp; // Pflichtfeld
    private List<String> dependsOn; // optional

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public WorkflowStep getMcp() { return mcp; }
    public void setMcp(WorkflowStep mcp) { this.mcp = mcp; }

    public List<String> getDependsOn() {
        return dependsOn != null ? dependsOn : Collections.emptyList();
    }

    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn;
    }
}
