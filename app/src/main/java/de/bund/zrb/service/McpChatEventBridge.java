package de.bund.zrb.service;

import de.zrb.bund.newApi.ChatEvent;
import de.zrb.bund.newApi.ChatEventSink;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simple in-memory event hub to bridge MCP tool execution results into the Swing chat UI.
 */
public class McpChatEventBridge implements ChatEventSink {

    public interface Listener {
        void onChatEvent(UUID sessionId, ChatEvent event);
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    @Override
    public void publish(UUID sessionId, ChatEvent event) {
        for (Listener l : listeners) {
            try {
                l.onChatEvent(sessionId, event);
            } catch (Exception ignore) {
                // don't let UI listeners break tool execution
            }
        }
    }
}

