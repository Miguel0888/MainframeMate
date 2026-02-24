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
        Message msg = new Message(sessionId, "tool", content);
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
     */
    public List<Map<String, String>> toMessages(String userInput) {
        List<Map<String, String>> result = new ArrayList<>();

        // System-Prompt als erste Nachricht
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            Map<String, String> systemMsg = new LinkedHashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt.trim());
            result.add(systemMsg);
        }

        // Historische Nachrichten
        for (Message msg : messages) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("role", msg.role);
            m.put("content", msg.content);
            result.add(m);
        }

        // Aktuelle User-Eingabe
        if (userInput != null && !userInput.trim().isEmpty()) {
            Map<String, String> userMsg = new LinkedHashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userInput);
            result.add(userMsg);
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
