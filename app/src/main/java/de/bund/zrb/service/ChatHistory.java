package de.bund.zrb.service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ChatHistory {

    private final UUID sessionId;
    private final List<Message> messages = new ArrayList<>();

    public ChatHistory(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public void addUserMessage(String content) {
        messages.add(new Message(sessionId, new Timestamp(System.currentTimeMillis()), "user", content));
    }

    public void addBotMessage(String content) {
        messages.add(new Message(sessionId, new Timestamp(System.currentTimeMillis()), "assistant", content));
    }

    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public List<String> getFormattedHistory() {
        List<String> result = new ArrayList<>();
        for (Message msg : messages) {
            String rolePrefix = msg.role.equals("user") ? "Du: " : "Bot: ";
            result.add(rolePrefix + msg.content);
        }
        return result;
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

    public static class Message {
        public final UUID sessionId;
        public final Timestamp timestamp;
        public final String role;   // "user" oder "assistant"
        public final String content;

        public Message(UUID sessionId, Timestamp timestamp, String role, String content) {
            this.sessionId = sessionId;
            this.timestamp = timestamp;
            this.role = role;
            this.content = content;
        }
    }
}
