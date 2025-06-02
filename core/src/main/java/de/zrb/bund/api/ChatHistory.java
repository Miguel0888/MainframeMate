package de.zrb.bund.api;

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
