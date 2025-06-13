package de.bund.zrb.workflow;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.bund.zrb.util.AsyncPlaceholderResolver;
import de.bund.zrb.util.PlaceholderResolver;
import de.zrb.bund.api.ExpressionRegistry;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.VariableRegistry;
import de.zrb.bund.newApi.workflow.*;
import de.zrb.bund.newApi.McpService;
import de.zrb.bund.newApi.mcp.McpTool;

import java.util.*;
import java.util.stream.Collectors;

public class WorkflowRunnerImpl implements WorkflowRunner {

    private final MainframeContext context;
    private final McpService mcpService;
    private final ExpressionRegistry expressionRegistry;
    private final Gson gson = new Gson();

    public WorkflowRunnerImpl(MainframeContext context, McpService mcpService, ExpressionRegistry expressionRegistry) {
        this.context = context;
        this.mcpService = mcpService;
        this.expressionRegistry = expressionRegistry;
    }

    @Override
    public UUID execute(WorkflowTemplate template, Map<String, String> overrides) {
        if (template == null || template.getData() == null) return null;

        processVariables(template, overrides);

        // 2. Schritte durchlaufen und vorbereiten
        AsyncPlaceholderResolver resolver = new AsyncPlaceholderResolver(context.getVariableRegistry(), 10_000); // 10 Sekunden Timeout
        UUID runId = UUID.randomUUID(); // create workflow id
        for (WorkflowStepContainer container : template.getData()) {
            WorkflowMcpData step = container.getMcp();
            if (step == null || step.getToolName() == null) continue;

            McpTool tool = context.getToolRegistry().getToolByName(step.getToolName());
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

    private void processVariables(WorkflowTemplate template, Map<String, String> overrides) {
        // 1. Alle Variablen aus dem Template + Overrides mergen
        Map<String, String> vars = new LinkedHashMap<>();
        if (template.getMeta() != null && template.getMeta().getVariables() != null) {
            vars.putAll(template.getMeta().getVariables());
        }
        if (overrides != null) {
            vars.putAll(overrides);
        }

        // 2. Expressions aus Parametern extrahieren und auswerten
        extractAndEvaluateExpressions(template.getData(), vars);

        // 3. Alle Vars in registry eintragen
        VariableRegistry registry = context.getVariableRegistry();
        registry.clear(); // Daten vom letzten Lauf löschen, ToDo: Eine eigene Registry pro Template verwenden..
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            registry.set(entry.getKey(), entry.getValue());
        }
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

    private void extractAndEvaluateExpressions(List<WorkflowStepContainer> steps, Map<String, String> vars) {
        Set<String> foundExpressions = new HashSet<>();

        for (WorkflowStepContainer container : steps) {
            WorkflowMcpData step = container.getMcp();
            if (step == null) continue;

            for (Object val : step.getParameters().values()) {
                if (val instanceof String) {
                    String str = (String) val;
                    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{\\{([^}]+)}}").matcher(str);
                    while (matcher.find()) {
                        foundExpressions.add(matcher.group(1));
                    }
                }
            }
        }

        for (String expr : foundExpressions) {
            // Ignoriere, falls manuell in vars gesetzt
            if (vars.containsKey(expr)) continue;

            // Versuche Expression zu parsen und auszuwerten
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(\\w+)\\((.*)\\)$").matcher(expr);
            if (matcher.matches()) {
                String functionName = matcher.group(1);
                String argsString = matcher.group(2);
                List<String> args = argsString.isEmpty() ? Collections.emptyList()
                        : Arrays.stream(argsString.split("\\s*,\\s*"))
                        .map(s -> s.replaceAll("^['\"]|['\"]$", "")) // entferne " oder '
                        .collect(Collectors.toList());

                try {
                    String result = expressionRegistry.evaluate(functionName, args);
                    vars.put(expr, result);
                } catch (Exception e) {
                    System.err.println("Fehler bei Auswertung von Expression „" + expr + "“:");
                    e.printStackTrace();
                }
            }
        }
    }


}
