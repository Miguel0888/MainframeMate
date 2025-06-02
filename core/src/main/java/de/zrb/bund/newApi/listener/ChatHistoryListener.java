package de.zrb.bund.newApi.listener;

import de.zrb.bund.api.ChatHistory;

public class ChatHistoryListener {
    private final ChatHistory chatHistory;

    public ChatHistoryListener(ChatHistory chatHistory) {
        this.chatHistory = chatHistory;
    }

    public void onUserMessage(String content) {
        chatHistory.addUserMessage(content);
    }

    public void onBotMessage(String content) {
        chatHistory.addBotMessage(content);
    }

    public void clearHistory() {
        chatHistory.clear();
    }

    public boolean isEmpty() {
        return chatHistory.isEmpty();
    }

    public ChatHistory getChatHistory() {
        return chatHistory;
    }
}
