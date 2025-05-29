package de.bund.zrb.service;

import de.zrb.bund.api.ChatService;

import java.io.IOException;
import java.util.function.Consumer;

public class LocalAiChatService implements ChatService {

    @Override
    public void streamAnswer(String prompt, Consumer<String> onChunk) throws IOException {

    }
}
