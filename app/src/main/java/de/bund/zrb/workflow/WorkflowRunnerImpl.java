package de.bund.zrb.workflow;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.util.AsyncPlaceholderResolver;
import de.zrb.bund.api.ExpressionRegistry;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.VariableRegistry;
import de.zrb.bund.newApi.workflow.*;
import de.zrb.bund.newApi.McpService;
import de.zrb.bund.newApi.mcp.McpTool;
import org.jetbrains.annotations.NotNull;

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

        // 0.) Prepare the Variable Registry
        Map<String, String> currentlySetVars = getWorkflowVars(template, overrides);
        registerVars(currentlySetVars, true);

        // 1.) ToDo: Now parse each parameter for each templates on its own
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

            // 2.) ToDo: Then try to resolve the Value for each Expression in each step concurrently (!), wait until timeout if not resolvable
            Map<String, ResolvableExpression> resolvedParams = new LinkedHashMap<>(); // the key is the param name form JSON
            // ToDo:

            Map<String, Object> resolvedParamsAsString = new LinkedHashMap<>(); // the key is the param name from JSON
            for (Map.Entry<String, Object> entry : rawParams.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof String) {
                    String str = (String) val;
                    // ToDo: convert resolvedParams to resolvedParamsAsString somehow?
                    // ToDo: Simply register the outmost {{}} block as new Variable in the Registry and store the resolved value (thus Expressions are "flattend" to one level)
                    String resolvedParamValue = ""; // ToDo. Caution can be something line TEXT1 {{VAR1}} TEX2 {{VAR2}} etc.. (we have multiple vars on level one, each variable on level 1 is in the VarRegistry registerd on in its own)
                    resolvedParamsAsString.put(entry.getKey(), resolvedParamValue); // ToDo: Strings are quoted in JSON!
                } else {
                    resolvedParamsAsString.put(entry.getKey(), val); // Not quoted value / andere Typen direkt übernehmen
                }
            }

            // 3. JSON-Call vorbereiten
            JsonObject jsonCall = new JsonObject();
            jsonCall.addProperty("name", step.getToolName());
            jsonCall.add("tool_input", gson.toJsonTree(resolvedParamsAsString));

            // 4. Fire-and-Forget an MCP-Service
            mcpService.accept(jsonCall, runId, step.getResultVar());

        }

        return runId;
    }

    private void registerVars(Map<String, String> vars, boolean startsNewWorkflow) {
        VariableRegistry registry = context.getVariableRegistry();
        if(startsNewWorkflow)
        {
            registry.clear(); // Daten vom letzten Lauf löschen, ToDo: Eine eigene Registry pro Template verwenden..
        }
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            registry.set(entry.getKey(), entry.getValue());
        }
    }

    @NotNull
    private static Map<String, String> getWorkflowVars(WorkflowTemplate template, Map<String, String> overrides) {
        // 1. Alle Variablen aus dem Template + Overrides mergen
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
