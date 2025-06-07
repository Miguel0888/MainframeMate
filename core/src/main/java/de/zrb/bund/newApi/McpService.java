package de.zrb.bund.newApi;

import com.google.gson.JsonElement;

import java.util.UUID;

public interface McpService {
    void handleToolCall(UUID sessionId, JsonElement toolCall);
}
