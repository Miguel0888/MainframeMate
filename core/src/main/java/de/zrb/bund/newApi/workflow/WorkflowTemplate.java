package de.zrb.bund.newApi.workflow;

import java.util.ArrayList;
import java.util.List;

public class WorkflowTemplate {
    private WorkflowMeta meta = new WorkflowMeta();
    private List<WorkflowStepContainer> data = new ArrayList<>();

    public WorkflowMeta getMeta() { return meta; }
    public void setMeta(WorkflowMeta meta) { this.meta = meta; }

    public List<WorkflowStepContainer> getData() { return data; }
    public void setData(List<WorkflowStepContainer> data) { this.data = data; }
}
