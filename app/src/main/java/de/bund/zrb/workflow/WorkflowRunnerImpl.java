package de.bund.zrb.workflow;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.bund.zrb.workflow.engine.ExpressionTreeParser;
import de.bund.zrb.workflow.engine.LiteralExpression;
import de.bund.zrb.workflow.engine.PollingResolutionContext;
import de.zrb.bund.api.ExpressionRegistry;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.McpService;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.workflow.*;

import java.util.*;

public class WorkflowRunnerImpl implements WorkflowRunner {

    private final MainframeContext context;
    private final McpService mcpService;
    private final ExpressionRegistry expressionRegistry;
    private final ExpressionTreeParser parser;
    private final Gson gson = new Gson();

    public WorkflowRunnerImpl(MainframeContext context, McpService mcpService, ExpressionRegistry expressionRegistry) {
        this.context = context;
        this.mcpService = mcpService;
        this.expressionRegistry = expressionRegistry;
        this.parser = new ExpressionTreeParser(expressionRegistry.getKeys());
    }

    @Override
    public UUID execute(WorkflowTemplate template, Map<String, String> overrides) {
        if (template == null || template.getData() == null) return null;

        UUID runId = UUID.randomUUID();

        // Symboltabelle & Context vorbereiten
        Map<String, Object> symbolTable = new LinkedHashMap<>();
        PollingResolutionContext resolutionContext = new PollingResolutionContext(symbolTable, expressionRegistry);

        // Initiale Variablen setzen
        getWorkflowVars(template, overrides).forEach(resolutionContext::provide);

        // Schritte vorbereiten
        List<WorkflowStepContainer> remainingSteps = new ArrayList<>(template.getData());

        boolean progressMade;

        do {
            progressMade = false;
            Iterator<WorkflowStepContainer> iterator = remainingSteps.iterator();

            while (iterator.hasNext()) {
                WorkflowStepContainer container = iterator.next();
                WorkflowMcpData step = container.getMcp();
                if (step == null || step.getToolName() == null) continue;

                McpTool tool = context.getToolRegistry().getToolByName(step.getToolName());
                if (tool == null) {
                    System.err.println("⚠ Tool \"" + step.getToolName() + "\" nicht gefunden.");
                    iterator.remove(); // aussortieren
                    continue;
                }

                // Parameter vorbereiten
                Map<String, Object> rawParams = step.getParameters();
                Map<String, ResolvableExpression> parsedParams = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : rawParams.entrySet()) {
                    Object val = entry.getValue();
                    if (val instanceof String) {
                        parsedParams.put(entry.getKey(), parser.parse((String) val));
                    } else {
                        parsedParams.put(entry.getKey(), new LiteralExpression(val));
                    }
                }

                try {
                    Map<String, Object> resolvedParams = new LinkedHashMap<>();
                    for (Map.Entry<String, ResolvableExpression> entry : parsedParams.entrySet()) {
                        Object resolved = entry.getValue().resolve(resolutionContext);
                        resolvedParams.put(entry.getKey(), resolved);
                    }

                    JsonObject jsonCall = new JsonObject();
                    for (Map.Entry<String, Object> resolved : resolvedParams.entrySet()) {
                        jsonCall.add(resolved.getKey(), gson.toJsonTree(resolved.getValue()));
                    }

                    // Tool ausführen
                    McpToolResponse response = tool.execute(jsonCall, step.getResultVar());

                    // Output als Variable registrieren
                    if (response.hasVariable()) {
                        resolutionContext.provide(response.asVariableName(), response.asVariableValue());
                    }

                    // Step erfolgreich → entfernen
                    iterator.remove();
                    progressMade = true;

                } catch (UnresolvedSymbolException ex) {
                    // Nicht fertig – überspringen für diesen Durchlauf
                    System.out.println("⏳ Step '" + step.getToolName() + "' wartet noch auf '" + ex.getMessage() + "'");
                } catch (Exception ex) {
                    System.err.println("❌ Fehler beim Ausführen von Step: " + step.getToolName());
                    ex.printStackTrace();
                    iterator.remove(); // optional: bei fatalem Fehler aus Liste entfernen
                }
            }

        } while (!remainingSteps.isEmpty() && progressMade);

        if (!remainingSteps.isEmpty()) {
            System.err.println("⚠ Einige Schritte konnten nicht ausgeführt werden, da benötigte Variablen fehlen:");
            for (WorkflowStepContainer s : remainingSteps) {
                System.err.println(" - " + s.getMcp().getToolName());
            }
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
