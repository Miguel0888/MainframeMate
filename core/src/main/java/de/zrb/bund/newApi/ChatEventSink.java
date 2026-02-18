package de.zrb.bund.newApi;

import java.util.UUID;

/**
 * Receives chat events that are not part of the LLM streaming text channel,
 * e.g. tool usage and tool results.
 */
public interface ChatEventSink {

    /**
     * Publishes an informational event into the chat UI.
     */
    void publish(UUID sessionId, ChatEvent event);
}
