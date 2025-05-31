package de.bund.zrb.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.zrb.bund.newApi.ChatMessage;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.MpcService;
import de.zrb.bund.newApi.PluginRegistry;

public class DefaultMpcService implements MpcService {

    private final PluginRegistry pluginRegistry;

    public DefaultMpcService(PluginRegistry registry) {
        this.pluginRegistry = registry;
    }

    @Override
    public void handleToolCall(String sessionId, JsonElement toolCall) {
        JsonObject obj = toolCall.getAsJsonObject();
        String toolName = obj.get("tool_name").getAsString();
        JsonObject input = obj.getAsJsonObject("tool_input");
        String callId = obj.get("tool_call_id").getAsString();

        McpTool tool = pluginRegistry.get(toolName);
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
