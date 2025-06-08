package de.bund.zrb.workflow;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.McpService;
import de.zrb.bund.newApi.workflow.WorkflowRunner;
import de.zrb.bund.newApi.workflow.WorkflowStep;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WorkflowRunnerImpl implements WorkflowRunner {

    private final ToolRegistry registry;
    private final McpService mcpService;

    public WorkflowRunnerImpl(ToolRegistry registry, McpService mcpService) {
        this.registry = registry;
        this.mcpService = mcpService;
    }

    @Override
    public void execute(List<WorkflowStep> steps) {
        List<JsonObject> jsonCalls = new ArrayList<>();

        for (WorkflowStep step : steps) {
            McpTool tool = registry.getToolByName(step.getToolName());
            if (tool == null) continue;

            JsonObject input = new Gson().toJsonTree(step.getParameters()).getAsJsonObject();
            // TODO: Ab hier haben wir denselben Inhalt wie die EXTRAHIERTEN PARAMETER aus einer Bot-Nachricht, die ein Tool steuern soll..
            //  Wir können jetzt einerseits die volle Tool-Usage JSON (mit tool = ExcelImport etc.) zusammenbauen und
            //  an den MCB-Service übergeben
            JsonObject result = tool.execute(input); // TODO: Die Ausführung muss im MCP-Service erfolgen, nicht hier!

            jsonCalls.add(result);
        }

//        JsonObject message = new JsonObject();
//        message.add("workflow", new Gson().toJsonTree(jsonCalls));

//        UUID uuid = null; // ToDo
//        mcpService.handleToolCall(uuid, message);
    }
}
