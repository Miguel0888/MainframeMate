package de.bund.zrb.workflow;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.workflow.*;
import de.zrb.bund.newApi.McpService;
import de.zrb.bund.newApi.mcp.McpTool;

import java.util.*;

public class WorkflowRunnerImpl implements WorkflowRunner {

    private final ToolRegistry registry;
    private final McpService mcpService;
    private final Gson gson = new Gson();

    public WorkflowRunnerImpl(ToolRegistry registry, McpService mcpService) {
        this.registry = registry;
        this.mcpService = mcpService;
    }

    @Override
    public UUID execute(WorkflowTemplate template, Map<String, String> overrides) {
        if (template == null || template.getData() == null) return null;

        // 1. Alle Variablen aus dem Template + Overrides mergen
        Map<String, String> vars = new LinkedHashMap<>();
        if (template.getMeta() != null && template.getMeta().getVariables() != null) {
            vars.putAll(template.getMeta().getVariables());
        }
        if (overrides != null) {
            vars.putAll(overrides);
        }

        // 2. Schritte durchlaufen und vorbereiten
        UUID runId = UUID.randomUUID(); // create workflow id
        for (WorkflowStepContainer container : template.getData()) {
            WorkflowMcpData step = container.getMcp();
            if (step == null || step.getToolName() == null) continue;

            McpTool tool = registry.getToolByName(step.getToolName());
            if (tool == null) continue;

            Map<String, Object> rawParams = step.getParameters();
            Map<String, Object> resolvedParams = new LinkedHashMap<>();

            for (Map.Entry<String, Object> entry : rawParams.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof String) {
                    String str = (String) val;
                    resolvedParams.put(entry.getKey(), resolvePlaceholders(str, vars));
                } else {
                    resolvedParams.put(entry.getKey(), val); // andere Typen direkt übernehmen
                }
            }

            // 3. JSON-Call vorbereiten
            JsonObject jsonCall = new JsonObject();
            jsonCall.addProperty("toolName", step.getToolName());
            jsonCall.add("parameters", gson.toJsonTree(resolvedParams));

            // 4. Fire-and-Forget an MCP-Service
            mcpService.accept(jsonCall, runId);
        }
        return runId;
    }

    private String resolvePlaceholders(String input, Map<String, String> vars) {
        StringBuilder result = new StringBuilder();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\\{([^}]+)}}");
        java.util.regex.Matcher matcher = pattern.matcher(input);

        int lastEnd = 0;
        while (matcher.find()) {
            result.append(input, lastEnd, matcher.start());

            String key = matcher.group(1);
            String value = vars.getOrDefault(key, "");
            result.append(value);

            lastEnd = matcher.end();
        }

        result.append(input.substring(lastEnd));
        return result.toString();
    }
}
