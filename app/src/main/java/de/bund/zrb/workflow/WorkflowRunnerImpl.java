package de.bund.zrb.workflow;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.bund.zrb.workflow.engine.BlockingResolutionContext;
import de.bund.zrb.workflow.engine.ExpressionTreeParser;
import de.bund.zrb.workflow.engine.LiteralExpression;
import de.zrb.bund.api.ExpressionRegistry;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.McpService;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.workflow.*;

import java.util.*;
import java.util.concurrent.*;

public class WorkflowRunnerImpl implements WorkflowRunner {

    private final MainframeContext context;
    private final McpService mcpService;
    private final ExpressionRegistry expressionRegistry;
    private final ExpressionTreeParser parser;
    private final ExecutorService executor;
    private final Gson gson = new Gson();

    public WorkflowRunnerImpl(MainframeContext context, McpService mcpService, ExpressionRegistry expressionRegistry) {
        this.context = context;
        this.mcpService = mcpService;
        this.expressionRegistry = expressionRegistry;
        this.parser = new ExpressionTreeParser(expressionRegistry.getKeys());
        this.executor = Executors.newCachedThreadPool();
    }

    @Override
    public UUID execute(WorkflowTemplate template, Map<String, String> overrides) {
        if (template == null || template.getData() == null) return null;

        UUID runId = UUID.randomUUID();
        BlockingResolutionContext resolutionContext = new BlockingResolutionContext(); // sorgt für das Warten, wenn Variable noch nicht existiert

        // 1. Starte mit initialen Variablen
        Map<String, String> initialVars = getWorkflowVars(template, overrides);
        for (Map.Entry<String, String> entry : initialVars.entrySet()) {
            resolutionContext.provide(entry.getKey(), entry.getValue());
        }

        // 2. Schritte parallel vorbereiten
        for (WorkflowStepContainer container : template.getData()) {
            WorkflowMcpData step = container.getMcp();
            if (step == null || step.getToolName() == null) continue;

            McpTool tool = context.getToolRegistry().getToolByName(step.getToolName());
            if (tool == null) {
                System.err.println("Tool \"" + step.getToolName() + "\" nicht gefunden.");
                continue;
            }

            Map<String, Object> rawParams = step.getParameters();
            Map<String, ResolvableExpression> parsedParams = new LinkedHashMap<>();

            for (Map.Entry<String, Object> entry : rawParams.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof String) {
                    parsedParams.put(entry.getKey(), parser.parse((String) val)); // val ist immer ein String
                } else {
                    parsedParams.put(entry.getKey(), new LiteralExpression(val)); // übernimmt den Typ aus val
                }
            }

            // Schritt ausführen, sobald alle Parameter auflösbar sind
            executor.submit(() -> {
                try {
                    Map<String, Object> resolvedParams = new LinkedHashMap<>();
                    for (Map.Entry<String, ResolvableExpression> e : parsedParams.entrySet()) {
                            Object resolved = e.getValue().resolve(resolutionContext); // blockiert, falls nötig
                        resolvedParams.put(e.getKey(), resolved);
                    }

                    JsonObject jsonCall = new JsonObject();
                    for (Map.Entry<String, Object> resolved : resolvedParams.entrySet()) {
                        Object value = resolved.getValue();
                        jsonCall.add(resolved.getKey(), gson.toJsonTree(value)); // korrekt getypt und gequoted
                    }

                    McpToolResponse response = tool.execute(jsonCall, step.getResultVar());

                    if (response.hasVariable()) {
                        resolutionContext.provide(response.asVariableName(), response.asVariableValue());
                    }

                    mcpService.accept(response.asJson(), runId, step.getResultVar());
                } catch (UnresolvedSymbolException ex) {
                    System.err.println("Fehler beim Ausführen des Tools \"" + step.getToolName() + "\": " + ex.getMessage());
                    ex.printStackTrace();
                }
            });
        }

        return runId;
    }

    private Map<String, String> getWorkflowVars(WorkflowTemplate template, Map<String, String> overrides) {
        Map<String, String> vars = new LinkedHashMap<>();
        if (template.getMeta() != null && template.getMeta().getVariables() != null) {
            vars.putAll(template.getMeta().getVariables());
        }
        if (overrides != null) {
            vars.putAll(overrides);
        }
        return vars;
    }
}
