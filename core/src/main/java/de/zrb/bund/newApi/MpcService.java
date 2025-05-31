package de.zrb.bund.newApi;

import com.google.gson.JsonElement;

public interface MpcService {
    void handleToolCall(String sessionId, JsonElement toolCall);
}
