package de.bund.zrb.workflow;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.bund.zrb.util.PlaceholderResolver;
import de.zrb.bund.api.ExpressionRegistry;
import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.workflow.*;
import de.zrb.bund.newApi.McpService;
import de.zrb.bund.newApi.mcp.McpTool;

import java.util.*;

public class WorkflowRunnerImpl implements WorkflowRunner {

    private final ToolRegistry registry;
    private final McpService mcpService;
    private final ExpressionRegistry expressionRegistry;
    private final Gson gson = new Gson();

    public WorkflowRunnerImpl(ToolRegistry registry, McpService mcpService, ExpressionRegistry expressionRegistry) {
        this.registry = registry;
        this.mcpService = mcpService;
        this.expressionRegistry = expressionRegistry;
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

        // 1b. Expressions innerhalb der Werte auswerten
        for (Map.Entry<String, String> entry : new LinkedHashMap<>(vars).entrySet()) {
            String value = entry.getValue();
            String resolved = evaluateExpressions(value);
            vars.put(entry.getKey(), resolved);
        }

        // 2. Schritte durchlaufen und vorbereiten
        PlaceholderResolver resolver = new PlaceholderResolver(vars);
        UUID runId = UUID.randomUUID(); // create workflow id
        for (WorkflowStepContainer container : template.getData()) {
            WorkflowMcpData step = container.getMcp();
            if (step == null || step.getToolName() == null) continue;

            McpTool tool = registry.getToolByName(step.getToolName());
            if (tool == null) {
                System.err.println("Tool \"" + step.getToolName() + "\" nicht gefunden.");
                continue;
            }

            Map<String, Object> rawParams = step.getParameters();
            Map<String, Object> resolvedParams = new LinkedHashMap<>();

            for (Map.Entry<String, Object> entry : rawParams.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof String) {
                    String str = (String) val;
                    resolvedParams.put(entry.getKey(), resolver.resolve(str));
                } else {
                    resolvedParams.put(entry.getKey(), val); // andere Typen direkt übernehmen
                }
            }

            // 3. JSON-Call vorbereiten
            JsonObject jsonCall = new JsonObject();
            jsonCall.addProperty("name", step.getToolName());
            jsonCall.add("tool_input", gson.toJsonTree(resolvedParams));

            // 4. Fire-and-Forget an MCP-Service
            mcpService.accept(jsonCall, runId);
        }
        return runId;
    }

    private String evaluateExpressions(String input) {
        if (input == null) return null;

        StringBuffer result = new StringBuffer();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{\\{(\\w+)\\((.*?)\\)}}").matcher(input);

        while (matcher.find()) {
            String functionName = matcher.group(1);
            String rawArgs = matcher.group(2);
            List<String> args = rawArgs.isEmpty() ? Collections.emptyList() : Arrays.asList(rawArgs.split("\\s*,\\s*"));
            String replacement;
            try {
                replacement = expressionRegistry.evaluate(functionName, args);
            } catch (Exception e) {
                replacement = "!!FEHLER!!";
                System.err.println("Fehler bei Auswertung von Expression: " + matcher.group(0));
                e.printStackTrace();
            }
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

}
