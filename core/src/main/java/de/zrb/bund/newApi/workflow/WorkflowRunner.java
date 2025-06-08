package de.zrb.bund.newApi.workflow;

import java.util.List;

public interface WorkflowRunner {
    void execute(List<WorkflowMcpData> steps);
}
