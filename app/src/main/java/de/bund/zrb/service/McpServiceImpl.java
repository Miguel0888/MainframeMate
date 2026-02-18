package de.bund.zrb.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.zrb.bund.newApi.ChatEvent;
import de.zrb.bund.newApi.ChatEventSink;
import de.zrb.bund.newApi.ChatMessage;
import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.McpService;

import java.util.UUID;

public class McpServiceImpl implements McpService {

    private final ToolRegistry toolRegistry;
    private final ChatEventSink eventSink;

    public McpServiceImpl(ToolRegistry registry) {
        this(registry, null);
    }

    public McpServiceImpl(ToolRegistry registry, ChatEventSink eventSink) {
        this.toolRegistry = registry;
        this.eventSink = eventSink;
    }

    @Override
    public void accept(JsonElement toolCall, UUID sessionId, String resultVar) {
        JsonObject obj = toolCall.getAsJsonObject();

        // Toolnamen ermitteln
        String toolName = getAsRequiredString(obj, "name");
        McpTool tool = toolRegistry.getToolByName(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Tool nicht registriert: " + toolName);
        }

        // Toolaufruf-ID (optional für Logging / Response Zuordnung)
        String callId = obj.has("tool_call_id") ? obj.get("tool_call_id").getAsString() : null;

        // Eingabe-JSON extrahieren (tool_input / input / arguments)
        JsonObject input = extractToolArguments(obj);

        // Validate against ToolSpec.required before executing
        validateRequired(tool.getSpec(), input);

        if (eventSink != null) {
            eventSink.publish(sessionId, new ChatEvent(ChatEvent.Type.TOOL_USE, toolName, input));
        }

        // Tool ausführen
        de.zrb.bund.newApi.mcp.McpToolResponse response = tool.execute(input, resultVar);
        JsonObject result = response.asJson();

        if (eventSink != null) {
            eventSink.publish(sessionId, new ChatEvent(ChatEvent.Type.TOOL_RESULT, toolName, result));
        }

        // Ergebnis verarbeiten (z. B. an Modell zurücksenden) - aktuell nur UI
        ChatMessage toolResultMessage = new ChatMessage();
        toolResultMessage.setRole(ChatMessage.Role.tool);
        toolResultMessage.setToolResult(result);

        // TODO: In einem nächsten Schritt: toolResultMessage als Kontext an die KI zurückführen
    }

    /**
     * Extrahiert das JSON-Objekt mit den Tool-Argumenten aus einem Tool-Call.
     * Unterstützt:
     * - 'tool_input' (JSON-Objekt)
     * - 'input' (JSON-Objekt)
     * - 'arguments' (JSON-String ODER JSON-Objekt)
     */
    private JsonObject extractToolArguments(JsonObject call) {
        if (call == null) {
            throw new IllegalArgumentException("Tool-Aufruf ist null");
        }

        if (call.has("tool_input") && call.get("tool_input").isJsonObject()) {
            return call.getAsJsonObject("tool_input");
        }

        if (call.has("input") && call.get("input").isJsonObject()) {
            return call.getAsJsonObject("input");
        }

        if (call.has("arguments")) {
            // Some models emit arguments as an object (not as string)
            if (call.get("arguments").isJsonObject()) {
                return call.getAsJsonObject("arguments");
            }
            if (call.get("arguments").isJsonPrimitive()) {
                String jsonString = call.get("arguments").getAsString();
                try {
                    JsonElement parsed = com.google.gson.JsonParser.parseString(jsonString);
                    if (!parsed.isJsonObject()) {
                        throw new IllegalArgumentException("'arguments' ist kein JSON-Objekt.");
                    }
                    return parsed.getAsJsonObject();
                } catch (Exception e) {
                    throw new IllegalArgumentException("Fehler beim Parsen von 'arguments': " + e.getMessage(), e);
                }
            }
        }

        throw new IllegalArgumentException("Tool-Aufruf enthält weder 'input' noch 'tool_input' noch 'arguments'.");
    }

    private void validateRequired(de.zrb.bund.newApi.mcp.ToolSpec spec, JsonObject input) {
        if (spec == null || spec.getInputSchema() == null) {
            return;
        }
        java.util.List<String> required = spec.getInputSchema().getRequired();
        if (required == null || required.isEmpty()) {
            return;
        }

        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String key : required) {
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            if (input == null || !input.has(key) || input.get(key).isJsonNull()) {
                missing.add(key);
            }
        }

        if (!missing.isEmpty()) {
            String example = spec.getExampleInput() == null ? null : new com.google.gson.Gson().toJson(spec.getExampleInput());
            String msg = "Pflichtfelder fehlen: " + missing
                    + (example == null ? "" : " | example_input=" + example);
            throw new IllegalArgumentException(msg);
        }
    }

    private String getAsRequiredString(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            throw new IllegalArgumentException("Pflichtfeld fehlt: " + key);
        }
        return obj.get(key).getAsString();
    }



}
