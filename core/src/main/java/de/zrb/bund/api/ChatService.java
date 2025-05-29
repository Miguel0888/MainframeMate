package de.zrb.bund.api;

import java.io.IOException;

public interface ChatService {
    void streamAnswer(String prompt, ChatStreamListener listener, boolean keepAlive) throws IOException;
}
