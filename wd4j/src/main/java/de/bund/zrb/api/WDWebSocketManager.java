package de.bund.zrb.api;

import de.bund.zrb.service.WDEventDispatcher;

import java.lang.reflect.Type;

public interface WDWebSocketManager {

    /**
     * Send the given command and wait for a typed response.
     *
     * @param command the command object to send
     * @param responseType the expected response type (can be Class<T> or any Type)
     * @param <T> the actual result object inside the WebDriver response wrapper
     * @return deserialized result object
     */
    <T> T sendAndWaitForResponse(WDCommand command, Type responseType);

    void registerEventListener(WDEventDispatcher eventDispatcher);

    /**
     * Send the given command without waiting for a response (fire-and-forget).
     * Used for time-critical operations like continueResponse in intercept handlers
     * where blocking would cause browser freezes.
     *
     * @param command the command object to send
     */
    void sendFireAndForget(WDCommand command);

    /**
     * Returns true if the underlying WebSocket is connected.
     *
     * @return true if connected
     */
    boolean isConnected();

    /**
     * Shuts down the event dispatch executor and cleans up pending resources.
     * Must be called when the WebSocket connection is closed.
     */
    void shutdown();
}
