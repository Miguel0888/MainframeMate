package de.zrb.bund.newApi;

import com.google.gson.JsonObject;

/**
 * Represents a non-streamed, structured chat UX event (e.g. tool usage).
 */
public class ChatEvent {

    public enum Type {
        TOOL_USE,
        TOOL_RESULT
    }

    private final Type type;
    private final String toolName;
    private final JsonObject payload;

    public ChatEvent(Type type, String toolName, JsonObject payload) {
        this.type = type;
        this.toolName = toolName;
        this.payload = payload;
    }

    public Type getType() {
        return type;
    }

    public String getToolName() {
        return toolName;
    }

    public JsonObject getPayload() {
        return payload;
    }
}

