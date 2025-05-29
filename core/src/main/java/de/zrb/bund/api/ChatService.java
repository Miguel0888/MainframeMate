package de.zrb.bund.api;

import java.io.IOException;
import java.util.function.Consumer;

public interface ChatService {
    void streamAnswer(String prompt, Consumer<String> onChunk) throws IOException;
}
