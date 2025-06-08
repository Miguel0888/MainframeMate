package de.zrb.bund.newApi.workflow;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface WorkflowRunner {
    UUID execute(WorkflowTemplate template, Map<String, String> overrides);
}
