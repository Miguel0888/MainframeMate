package de.bund.zrb.mcpserver.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of an MCP tool call.
 * Supports text and image content types as defined by the MCP specification.
 */
public class ToolResult {

    private final List<JsonObject> content = new ArrayList<JsonObject>();
    private final boolean isError;

    private ToolResult(boolean isError) {
        this.isError = isError;
    }

    // ── Factory methods ─────────────────────────────────────────────

    public static ToolResult text(String text) {
        ToolResult r = new ToolResult(false);
        r.addText(text);
        return r;
    }

    public static ToolResult error(String message) {
        ToolResult r = new ToolResult(true);
        r.addText(message);
        return r;
    }

    public static ToolResult image(String base64Png, String mimeType) {
        ToolResult r = new ToolResult(false);
        r.addImage(base64Png, mimeType);
        return r;
    }

    public static ToolResult imageWithText(String base64Png, String mimeType, String text) {
        ToolResult r = new ToolResult(false);
        r.addText(text);
        r.addImage(base64Png, mimeType);
        return r;
    }

    // ── Content builders ────────────────────────────────────────────

    public void addText(String text) {
        JsonObject c = new JsonObject();
        c.addProperty("type", "text");
        c.addProperty("text", text);
        content.add(c);
    }

    public void addImage(String base64Data, String mimeType) {
        JsonObject c = new JsonObject();
        c.addProperty("type", "image");
        c.addProperty("data", base64Data);
        c.addProperty("mimeType", mimeType);
        content.add(c);
    }

    // ── Serialisation ───────────────────────────────────────────────

    /**
     * Convert to the MCP tools/call result JSON structure.
     */
    public JsonElement toJson() {
        JsonObject obj = new JsonObject();
        JsonArray arr = new JsonArray();
        for (JsonObject c : content) {
            arr.add(c);
        }
        obj.add("content", arr);
        if (isError) {
            obj.addProperty("isError", true);
        }
        return obj;
    }
}

