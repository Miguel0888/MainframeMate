package de.zrb.bund.api;

public interface ChatStreamListener {
    void onStreamStart();
    void onStreamChunk(String chunk);
    void onStreamEnd();
    void onError(Exception e);
}
