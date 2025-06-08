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

        // Toolnamen ermitteln
        String toolName = getAsRequiredString(obj, "name");
        McpTool tool = toolRegistry.getToolByName(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Tool nicht registriert: " + toolName);
        }

        // Toolaufruf-ID (optional für Logging / Response Zuordnung)
        String callId = obj.has("tool_call_id") ? obj.get("tool_call_id").getAsString() : null;

        // Eingabe-JSON extrahieren (tool_input oder arguments)
        JsonObject input = extractToolArguments(obj);

        // Tool ausführen
        JsonObject result = tool.execute(input);

        // Ergebnis verarbeiten (z. B. an Modell zurücksenden)
        ChatMessage toolResultMessage = new ChatMessage();
        toolResultMessage.setRole(ChatMessage.Role.tool);
        toolResultMessage.setToolResult(result);

        // TODO: Speichere oder sende toolResultMessage zurück an Session
    }

    /**
     * Extrahiert das JSON-Objekt mit den Tool-Argumenten aus einem Tool-Call.
     * Unterstützt sowohl 'tool_input' als auch 'arguments' (als JSON-String).
     */
    private JsonObject extractToolArguments(JsonObject call) {
        if (call.has("tool_input") && call.get("tool_input").isJsonObject()) {
            return call.getAsJsonObject("tool_input");
        }

        if (call.has("arguments") && call.get("arguments").isJsonPrimitive()) {
            String jsonString = call.get("arguments").getAsString();
            try {
                JsonElement parsed = new com.google.gson.JsonParser().parse(jsonString);
                if (!parsed.isJsonObject()) {
                    throw new IllegalArgumentException("'arguments' ist kein JSON-Objekt.");
                }
                return parsed.getAsJsonObject();
            } catch (Exception e) {
                throw new IllegalArgumentException("Fehler beim Parsen von 'arguments': " + e.getMessage(), e);
            }
        }

        throw new IllegalArgumentException("Tool-Aufruf enthält weder 'tool_input' noch 'arguments'.");
    }

    private String getAsRequiredString(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            throw new IllegalArgumentException("Pflichtfeld fehlt: " + key);
        }
        return obj.get(key).getAsString();
    }



}

