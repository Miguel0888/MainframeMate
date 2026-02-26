package de.zrb.bund.api;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChatHistory {

    private final UUID sessionId;
    private final List<Message> messages = new ArrayList<>();
    private String systemPrompt;

    public ChatHistory(UUID sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Setzt den System-Prompt, der bei jedem toPrompt()-Aufruf vorangestellt wird.
     * So bleibt der Mode (Agent/Ask/Edit/Plan) über die gesamte Session persistent.
     */
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public Timestamp addUserMessage(String content) {
        Message msg = new Message(sessionId, "user", content);
        messages.add(msg);
        return msg.timestamp;
    }

    public Timestamp addBotMessage(String content) {
        Message msg = new Message(sessionId,"assistant", content);
        messages.add(msg);
        return msg.timestamp;
    }

    public Timestamp addToolMessage(String content) {
        // Use "user" role instead of "tool" because the Ollama/OpenAI Chat API
        // ignores "tool" messages that don't follow an "assistant" message with
        // a tool_calls array. Since we handle tool calls manually (JSON parsing),
        // there is no native tool_calls entry, so "tool" messages get silently dropped.
        Message msg = new Message(sessionId, "user", content);
        messages.add(msg);
        return msg.timestamp;
    }

    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public List<String> getFormattedHistory() {
        List<String> result = new ArrayList<>();
        for (Message msg : messages) {
            String rolePrefix;
            if ("user".equals(msg.role)) {
                rolePrefix = "Du: ";
            } else if ("tool".equals(msg.role)) {
                rolePrefix = "Tool: ";
            } else {
                rolePrefix = "Bot: ";
            }
            result.add(rolePrefix + msg.content);
        }
        return result;
    }

    /**
     * Message-Builder für Ollama /api/chat Format.
     * Gibt eine Liste von Maps zurück, die direkt als JSON serialisiert werden können.
     * Jede Map enthält "role" und "content".
     *
     * WICHTIG: Erzwingt strikt abwechselnde user/assistant-Rollen.
     * - System-Prompt wird in die erste User-Nachricht integriert.
     * - Aufeinanderfolgende Nachrichten derselben Rolle werden zusammengefügt.
     * - "tool"-Messages werden als "user" behandelt.
     */
    public List<Map<String, String>> toMessages(String userInput) {
        List<Map<String, String>> result = new ArrayList<>();

        // 1. System-Prompt als eigene "system"-Nachricht (von Ollama/OpenAI unterstützt)
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            Map<String, String> systemMsg = new LinkedHashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt.trim());
            result.add(systemMsg);
        }

        // 2. Collect history + current input as flat list with normalized roles
        List<String[]> flat = new ArrayList<>();
        for (Message msg : messages) {
            // Normalize: anything not "assistant" becomes "user"
            String role = "assistant".equals(msg.role) ? "assistant" : "user";
            flat.add(new String[]{role, msg.content});
        }
        if (userInput != null && !userInput.trim().isEmpty()) {
            flat.add(new String[]{"user", userInput});
        }

        // 3. Merge consecutive same-role messages to ensure strict alternation
        for (String[] entry : flat) {
            // Skip system messages already added
            if (!result.isEmpty()) {
                Map<String, String> last = result.get(result.size() - 1);
                if (!"system".equals(last.get("role")) && last.get("role").equals(entry[0])) {
                    // Same role as previous non-system → merge
                    last.put("content", last.get("content") + "\n\n" + entry[1]);
                    continue;
                }
            }
            Map<String, String> m = new LinkedHashMap<>();
            m.put("role", entry[0]);
            m.put("content", entry[1]);
            result.add(m);
        }

        return result;
    }

    /**
     * Prompt-Builder für LLMs: nutzt die Rollen explizit.
     * Tool-Nachrichten werden als eigener Participant aufgenommen.
     */
    public String toPrompt(String userInput) {
        StringBuilder prompt = new StringBuilder();

        // System-Prompt immer voranstellen, damit der Mode persistent ist
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            prompt.append("SYSTEM: ").append(systemPrompt.trim()).append("\n\n");
        }

        for (Message msg : messages) {
            if ("user".equals(msg.role)) {
                prompt.append("Du: ");
            } else if ("assistant".equals(msg.role)) {
                prompt.append("Bot: ");
            } else if ("tool".equals(msg.role)) {
                prompt.append("Tool: ");
            } else {
                prompt.append(msg.role).append(": ");
            }
            prompt.append(msg.content).append("\n");
        }
        if (userInput != null && !userInput.trim().isEmpty()) {
            prompt.append("Du: ").append(userInput);
        }
        return prompt.toString();
    }

    public void clear() {
        messages.clear();
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    public void remove(Timestamp timestamp) {
        messages.removeIf(msg -> msg.timestamp.equals(timestamp));
    }

    public static class Message {
        public Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        public final UUID sessionId;
        public final String role;   // "user" oder "assistant"
        public final String content;

        public Message(UUID sessionId, String role, String content) {
            this.sessionId = sessionId;
            this.role = role;
            this.content = content;
        }
    }
}
