package de.zrb.bund.newApi;

import com.google.gson.JsonElement;

import java.util.UUID;

public interface McpService {
    void accept(JsonElement toolCall, UUID sessionId);
}
