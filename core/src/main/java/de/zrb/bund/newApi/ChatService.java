package de.zrb.bund.newApi;

public interface ChatService {
    void postUserMessage(String sessionId, String message);
    void postAssistantMessage(String sessionId, String message);
}
