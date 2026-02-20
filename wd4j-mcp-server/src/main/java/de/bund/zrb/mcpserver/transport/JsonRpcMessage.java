package de.bund.zrb.mcpserver.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Represents a JSON-RPC 2.0 message (request, response, or notification).
 */
public class JsonRpcMessage {

    private String jsonrpc = "2.0";
    private JsonElement id;       // null for notifications
    private String method;        // null for responses
    private JsonObject params;    // null for responses
    private JsonElement result;   // null for requests/notifications
    private JsonObject error;     // null unless error response

    public JsonRpcMessage() {}

    // ── Getters ─────────────────────────────────────────────────────

    public String getJsonrpc() { return jsonrpc; }
    public JsonElement getId() { return id; }
    public String getMethod() { return method; }
    public JsonObject getParams() { return params; }
    public JsonElement getResult() { return result; }
    public JsonObject getError() { return error; }

    // ── Setters ─────────────────────────────────────────────────────

    public void setId(JsonElement id) { this.id = id; }
    public void setMethod(String method) { this.method = method; }
    public void setParams(JsonObject params) { this.params = params; }
    public void setResult(JsonElement result) { this.result = result; }
    public void setError(JsonObject error) { this.error = error; }

    // ── Classification helpers ──────────────────────────────────────

    public boolean isRequest() { return method != null && id != null; }
    public boolean isNotification() { return method != null && id == null; }
    public boolean isResponse() { return method == null && (result != null || error != null); }

    // ── Factory methods ─────────────────────────────────────────────

    public static JsonRpcMessage successResponse(JsonElement id, JsonElement result) {
        JsonRpcMessage msg = new JsonRpcMessage();
        msg.id = id;
        msg.result = result;
        return msg;
    }

    public static JsonRpcMessage errorResponse(JsonElement id, int code, String message) {
        JsonRpcMessage msg = new JsonRpcMessage();
        msg.id = id;
        JsonObject err = new JsonObject();
        err.addProperty("code", code);
        err.addProperty("message", message);
        msg.error = err;
        return msg;
    }

    public static JsonRpcMessage errorResponse(JsonElement id, int code, String message, JsonElement data) {
        JsonRpcMessage msg = errorResponse(id, code, message);
        if (data != null) {
            msg.error.add("data", data);
        }
        return msg;
    }
}

