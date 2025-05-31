package de.zrb.bund.newApi;

import de.zrb.bund.newApi.listener.ChatListener;

public interface ChatService {
    void postUserMessage(String sessionId, String message);
    void postAssistantMessage(String sessionId, String message);
    void addChatListener(ChatListener listener);
}
