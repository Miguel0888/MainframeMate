package de.bund.zrb.service;

import de.zrb.bund.api.ChatService;
import de.zrb.bund.api.ChatStreamListener;

import java.io.IOException;

public class LocalAiChatService implements ChatService {

    @Override
    public void streamAnswer(String prompt, ChatStreamListener listener, boolean keepAlive) throws IOException {

    }
}
