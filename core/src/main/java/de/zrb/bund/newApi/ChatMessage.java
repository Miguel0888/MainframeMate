package de.zrb.bund.newApi;

import com.google.gson.JsonObject;

public class ChatMessage {

    public void setRole(Role role) {

    }

    public void setToolResult(JsonObject result) {

    }

    public enum Role {
        user, assistant, tool
    }

    private Role role;
    private String content;
    private JsonObject toolUse;     // nur bei role == assistant + tool_use
    private JsonObject toolResult;  // nur bei role == tool

    // Standard-Getter und Setter
}
