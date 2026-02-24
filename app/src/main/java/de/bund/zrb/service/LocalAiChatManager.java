package de.bund.zrb.service;

import de.zrb.bund.api.ChatHistory;
import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.ChatStreamListener;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LocalAiChatManager implements ChatManager {

    private final Map<UUID, ChatHistory> sessionHistories = new ConcurrentHashMap<>();

    @Override
    public UUID newSession() {
        return null;
    }

    @Override
    public boolean streamAnswer(UUID sessionId, boolean useContext, String prompt, ChatStreamListener listener, boolean keepAlive) throws IOException {

        return keepAlive;
    }

    @Override
    public ChatHistory getHistory(UUID sessionId) {
        return sessionHistories.computeIfAbsent(sessionId, ChatHistory::new);
    }

    @Override
    public List<String> getFormattedHistory(UUID sessionId) {
        return Collections.emptyList();
    }

    @Override
    public void clearHistory(UUID sessionId) {

    }



    @Override
    public void addUserMessage(UUID sessionId, String message) {

    }

    @Override
    public void addBotMessage(UUID sessionId, String message) {

    }

    @Override
    public void cancel(UUID sessionId) {

    }

    @Override
    public void onDispose() {

    }

    @Override
    public void closeSession(UUID sessionId) {

    }
}
