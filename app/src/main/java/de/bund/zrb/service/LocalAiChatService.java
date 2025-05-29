package de.bund.zrb.service;

import de.zrb.bund.api.ChatService;
import de.zrb.bund.api.ChatStreamListener;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class LocalAiChatService implements ChatService {

    @Override
    public UUID newSession() {
        return null;
    }

    @Override
    public void streamAnswer(UUID sessionId, String prompt, ChatStreamListener listener, boolean keepAlive) throws IOException {

    }

    @Override
    public List<String> getHistory(UUID sessionId) {
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
}
