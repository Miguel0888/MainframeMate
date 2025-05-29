package de.bund.zrb.model;

import java.util.ArrayList;
import java.util.List;

public class ChatSession {

    private final List<ChatMessage> history = new ArrayList<>();

    public void addUserMessage(String content) {
        history.add(new ChatMessage("user", content));
    }

    public void addBotMessage(String content) {
        history.add(new ChatMessage("bot", content));
    }

    public String buildPrompt() {
        StringBuilder prompt = new StringBuilder();
        for (ChatMessage msg : history) {
            if ("user".equals(msg.role)) {
                prompt.append("Du: ").append(msg.content).append("\n");
            } else {
                prompt.append("Bot: ").append(msg.content).append("\n");
            }
        }
        return prompt.toString();
    }

    public void clear() {
        history.clear();
    }

    private static class ChatMessage {
        String role;
        String content;

        ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
