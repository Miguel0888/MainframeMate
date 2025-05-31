package de.zrb.bund.newApi.listener;

import de.zrb.bund.newApi.ChatMessage;

public interface ChatListener {
    void onMessage(String sessionId, ChatMessage message);
}
