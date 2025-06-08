package de.bund.zrb.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.zrb.bund.newApi.ChatMessage;
import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.McpService;

import java.util.UUID;

public class McpServiceImpl implements McpService {

    private final ToolRegistry toolRegistry;

    public McpServiceImpl(ToolRegistry registry) {
        this.toolRegistry = registry;
    }

    @Override
    public void accept(JsonElement toolCall, UUID sessionId) {
        JsonObject obj = toolCall.getAsJsonObject();
        String toolName = obj.get("tool_name").getAsString();
        JsonObject input = obj.getAsJsonObject("tool_input");
        String callId = obj.get("tool_call_id").getAsString();

        McpTool tool = toolRegistry.getToolByName(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }

        JsonObject result = tool.execute(input);

        ChatMessage toolResultMessage = new ChatMessage();
        toolResultMessage.setRole(ChatMessage.Role.tool);
        toolResultMessage.setToolResult(result);

        // Speichere oder sende die tool_result-Nachricht an das Modell zurück
        // z. B.: chatSession.appendMessage(toolResultMessage);
    }
}
