package de.bund.zrb;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import de.bund.zrb.api.WebSocketFrame;
import de.bund.zrb.api.WDWebSocket;
import de.bund.zrb.service.WDEventDispatcher;
import de.bund.zrb.support.mapping.GsonMapperFactory;
import de.bund.zrb.api.WDCommand;
import de.bund.zrb.api.WDCommandResponse;
import de.bund.zrb.websocket.WDErrorResponse;
import de.bund.zrb.api.WDWebSocketManager;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class WDWebSocketManagerImpl implements WDWebSocketManager {
    private final Gson gson = GsonMapperFactory.getGson(); // ✅ Nutzt zentrale Fabrik

    private final WDWebSocket webSocket;

    /**
     * Dedicated single-thread executor for dispatching <b>events only</b>.
     * This decouples event listener processing from the WebSocket receive thread, preventing
     * browser freezes caused by slow or blocking event listeners (e.g. heavy JSON parsing,
     * network ingestion). A single thread preserves event order.
     *
     * <p><b>Important:</b> Command-response callbacks are NOT dispatched through this executor.
     * They run directly on the WebSocket receive thread because they only perform a fast,
     * non-blocking {@code future.complete()} call. Routing them through this executor would
     * cause deadlocks when the event queue is saturated (e.g. 50+ network intercept events
     * from a heavy page load blocking getData responses indefinitely).</p>
     */
    private final ExecutorService dispatchExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "WDWebSocket-dispatch");
        t.setDaemon(true);
        return t;
    });

    // ---- Congestion Detection Watchdog ----

    /**
     * Periodic watchdog that monitors WebSocket traffic and warns when the connection
     * appears congested (no messages received for a configurable period while commands
     * are still pending). Interval and threshold are configurable via system properties:
     * <ul>
     *   <li>{@code wd4j.congestion.checkIntervalMs} – check interval in ms (default 5000)</li>
     *   <li>{@code wd4j.congestion.thresholdMs} – silence threshold in ms (default 10000)</li>
     * </ul>
     */
    private final ScheduledExecutorService congestionWatchdog = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "WD4J-congestion-watchdog");
        t.setDaemon(true);
        return t;
    });

    private volatile long lastWatchdogMessageCount = 0;
    private volatile long lastWatchdogCheckTimestamp = System.currentTimeMillis();

    // Retry-Regeln nun dynamisch über System Properties:
    //  - wd4j.command.retry.maxCount  (int)   Anzahl Versuche
    //  - wd4j.command.retry.windowMs  (long)  Zeitfenster in Millisekunden
    //  Fehlen oder <= 0 => Retry deaktiviert (sofortiger Fehler)
    private static final Integer MAX_RETRY_COUNT = resolveIntProp("wd4j.command.retry.maxCount");
    private static final Long MAX_RETRY_WINDOW_MILLIS = resolveLongProp("wd4j.command.retry.windowMs");

    private static Integer resolveIntProp(String key) {
        String v = System.getProperty(key);
        if (v == null || v.trim().isEmpty()) return null;
        try { return Integer.valueOf(v.trim()); } catch (NumberFormatException ignore) { return null; }
    }
    private static Long resolveLongProp(String key) {
        String v = System.getProperty(key);
        if (v == null || v.trim().isEmpty()) return null;
        try { return Long.valueOf(v.trim()); } catch (NumberFormatException ignore) { return null; }
    }
    private static boolean retryEnabled() {
        return MAX_RETRY_COUNT != null && MAX_RETRY_COUNT > 0 &&
               MAX_RETRY_WINDOW_MILLIS != null && MAX_RETRY_WINDOW_MILLIS > 0L;
    }

    // Dispatcher: key = commandId, value = callback for this command
    private final Map<Integer, Consumer<WDCommandResponse<?>>> responseDispatcher =
            new ConcurrentHashMap<Integer, Consumer<WDCommandResponse<?>>>();

    private volatile boolean dispatcherListenerRegistered = false;

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Deprecated // since WebSocketConnection should not be a singleton anymore?
    private static volatile WDWebSocketManagerImpl instance; // ToDo: Remove singleton pattern

    @Deprecated // since WebSocketConnection should not be a singleton anymore?
    public WDWebSocketManagerImpl(WDWebSocket webSocket) {
        this.webSocket = webSocket;
        startCongestionWatchdog();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Sendet einen Befehl über den WebSocket.
     *
     * @param command Das Command-Objekt, das gesendet werden soll.
     */
    public void send(WDCommand command) {
        if (webSocket.isClosed()) { // ToDo: Find a better solution, the problem is a new connection removes all listeners
            throw new RuntimeException("WebSocket connection is closed. Please reestablish the connection.");
//            this.webSocket = WebSocketImpl.getInstance();
//            registerEventListener(eventDispatcher);
        }
        String jsonCommand = gson.toJson(command);
        webSocket.send(jsonCommand); // Nachricht senden
    }

    /**
     * Sendet einen Befehl ohne auf die Antwort zu warten (fire-and-forget).
     * Wird für zeitkritische Operationen wie continueResponse in Intercept-Handlern verwendet,
     * wo blockierendes Warten zu Browser-Freezes führen würde.
     *
     * @param command Das Command-Objekt, das gesendet werden soll.
     */
    @Override
    public void sendFireAndForget(WDCommand command) {
        send(command);
    }

    /**
     * Sendet einen Befehl und wartet auf die Antwort.
     * receive nutzt den Dispatcher (Map<id, callback>), um die Antwort zu liefern.
     *
     * @param command      Der Befehl, der gesendet wird.
     * @param responseType Die Klasse des erwarteten DTOs.
     * @param <T>          Der Typ der Antwort.
     * @return Ein deserialisiertes DTO der Klasse `T`.
     */
    public <T> T sendAndWaitForResponse(final WDCommand command, final Type responseType) {
        final CompletableFuture<WDCommandResponse<?>> future = new CompletableFuture<WDCommandResponse<?>>();

        // Lambda, die auf die finale Antwort reagiert (wird von receive/Dispatcher aufgerufen)
        receive(command, new Consumer<WDCommandResponse<?>>() {
            @Override
            public void accept(WDCommandResponse<?> response) {
                if (!future.isDone()) {
                    future.complete(response);
                }
            }
        });

        // Befehl senden
        send(command);

        try {
            long timeoutSeconds = Long.getLong("wd4j.command.timeout.seconds", 45);
            WDCommandResponse<?> response = future.get(timeoutSeconds, TimeUnit.SECONDS);

            // Fehler: hier prüfen und ggf. Exception werfen
            if (response instanceof WDErrorResponse) {
                throw (WDErrorResponse) response;
            }

            // String-Spezialfall: gesamte Response als JSON zurückgeben
            if (responseType == String.class) {
                @SuppressWarnings("unchecked")
                T asString = (T) gson.toJson(response);
                return asString;
            }

            Object result = response.getResult();
            if (result == null) {
                throw new RuntimeException("Response does not contain a 'result' field.");
            }

            if (result instanceof JsonElement) {
                return gson.fromJson((JsonElement) result, responseType);
            }

            // Fallback: map result via JSON
            return gson.fromJson(gson.toJson(result), responseType);

        } catch (TimeoutException e) {
            responseDispatcher.remove(command.getId());
            throw new RuntimeException("Timeout while waiting for response.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseDispatcher.remove(command.getId());
            throw new RuntimeException("Interrupted while waiting for response.", e);
        } catch (ExecutionException e) {
            responseDispatcher.remove(command.getId());
            if (e.getCause() instanceof WDErrorResponse) {
                throw (WDErrorResponse) e.getCause();
            }
            throw new RuntimeException("Error while waiting for response.", e);
        }
    }

    /**
     * Registriert einen Callback in der Dispatcher-Map.
     * Der zentrale WebSocket-Listener ruft diesen Callback anhand der id auf.
     *
     * Retry-Verhalten:
     * - Wenn `type == "error"`:
     *   - Solange NICHT (RetryCount >= MAX_RETRY_COUNT UND Age >= MAX_RETRY_WINDOW_MILLIS):
     *     - incrementRetryCount
     *     - Command erneut senden
     *     - Callback NICHT aufrufen
     *   - Erst wenn beide Limits erreicht/überschritten sind:
     *     - WDErrorResponse an Callback liefern (final)
     *
     * @param command         Der zugehörige Command.
     * @param responseHandler Callback für finale Antwort (oder finalen Fehler).
     * @return Future, das mit der finalen WDCommandResponse abgeschlossen wird.
     */
    public CompletableFuture<WDCommandResponse<?>> receive(final WDCommand command,
                                                           final Consumer<WDCommandResponse<?>> responseHandler) {

        final int commandId = command.getId();
        final CompletableFuture<WDCommandResponse<?>> future = new CompletableFuture<WDCommandResponse<?>>();

        // Wrap callback, damit Retry-Logik zentral pro Command ausgeführt wird
        Consumer<WDCommandResponse<?>> dispatcherCallback = new Consumer<WDCommandResponse<?>>() {
            @Override
            public void accept(WDCommandResponse<?> response) {
                // Retry-Handling nur für Errors und nur wenn aktiviert
                if (response instanceof WDErrorResponse && retryEnabled()) {
                    long now = System.currentTimeMillis();
                    long age = now - command.getFirstTimestamp();
                    int retries = command.getRetryCount();

                    boolean maxCountReached = retries >= MAX_RETRY_COUNT;
                    boolean maxAgeReached = age >= MAX_RETRY_WINDOW_MILLIS;

                    // Solange nicht beide Limits erreicht → retry und NICHT benachrichtigen
                    if (!(maxCountReached && maxAgeReached)) {
                        command.incrementRetryCount();
                        String retryJson = gson.toJson(command);
                        webSocket.send(retryJson);
                        return; // Fehler unterdrücken
                    }
                    // An dieser Stelle: finaler Fehler, weiter unten normal behandeln
                }
                // Wenn Retry deaktiviert => sofortiger Fehler-Durchlass

                // Finale Antwort oder finaler Fehler: Dispatcher-Aufräumen
                responseDispatcher.remove(commandId);

                if (responseHandler != null) {
                    responseHandler.accept(response);
                }

                if (!future.isDone()) {
                    future.complete(response);
                }
            }
        };

        // In Sprungtabelle eintragen
        responseDispatcher.put(commandId, dispatcherCallback);

        // Sicherstellen, dass der zentrale Listener registriert ist
        ensureDispatcherListenerRegistered();

        return future;
    }

    /**
     * Registriert einmalig einen zentralen Listener, der alle Frames entgegennimmt
     * und anhand der id an responseDispatcher verteilt.
     */
    private void ensureDispatcherListenerRegistered() {
        if (dispatcherListenerRegistered) {
            return;
        }
        synchronized (this) {
            if (dispatcherListenerRegistered) {
                return;
            }

            webSocket.onFrameReceived(new Consumer<WebSocketFrame>() {
                @Override
                public void accept(WebSocketFrame frame) {
                    try {
                        String text = frame.text();
                        JsonObject json = gson.fromJson(text, JsonObject.class);
                        if (json == null || !json.has("id")) {
                            // Events ohne id werden woanders behandelt (registerEventListener)
                            return;
                        }

                        int id = json.get("id").getAsInt();
                        Consumer<WDCommandResponse<?>> callback = responseDispatcher.get(id);
                        if (callback == null) {
                            // Kein wartender Empfänger für diese id
                            return;
                        }

                        WDCommandResponse<?> response;

                        if (json.has("type") && "error".equals(json.get("type").getAsString())) {
                            // Fehlerantwort
                            response = gson.fromJson(text, WDErrorResponse.class);
                        } else {
                            // Generische Response mit type/result
                            final String type = json.has("type") ? json.get("type").getAsString() : null;
                            final int responseId = id;
                            final JsonElement resultElement = json.get("result");

                            response = new WDCommandResponse<Object>() {
                                @Override
                                public String getType() {
                                    return type;
                                }

                                @Override
                                public int getId() {
                                    return responseId;
                                }

                                @Override
                                public Object getResult() {
                                    return resultElement;
                                }
                            };
                        }

                        // Execute command-response callback DIRECTLY on the WebSocket thread.
                        // These callbacks only do fast, non-blocking future.complete() calls.
                        // Routing through dispatchExecutor would cause deadlocks: when 50+
                        // network events saturate the single-thread event queue, command
                        // responses (e.g. getData replies) get stuck behind pending events,
                        // causing timeouts and browser freezes.
                        try {
                            callback.accept(response);
                        } catch (Exception e) {
                            System.err.println("[ERROR] Command callback error for id " + id + ": " + e.getMessage());
                        }

                    } catch (JsonSyntaxException e) {
                        System.out.println("[ERROR] JSON Parsing-Fehler: " + e.getMessage());
                    }
                }
            });

            dispatcherListenerRegistered = true;
        }
    }

    /**
     * Registriert einen Event-Listener, der auf eingehende Events reagiert.
     *
     * @param eventDispatcher Der EventDispatcher, der die Events verarbeitet.
     */
    @Override
    public void registerEventListener(WDEventDispatcher eventDispatcher) {
        webSocket.onFrameReceived(frame -> {
            try {
                JsonObject json = gson.fromJson(frame.text(), JsonObject.class);

                // Prüfen, ob es sich um ein Event handelt (kein "id"-Feld)
                if (json.has("method")) {
                    if (Boolean.getBoolean("wd4j.debug")) {
                        System.out.println("[DEBUG] WebSocketManager detected event: " + json.get("method").getAsString());
                    }
                    // Dispatch asynchronously to free the WebSocket thread immediately.
                    // This prevents browser freezes when event handlers (e.g. intercept
                    // continueResponse) take time or trigger further WebSocket sends.
                    dispatchExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                eventDispatcher.processEvent(json);
                            } catch (Exception e) {
                                System.err.println("[ERROR] Event dispatch error: " + e.getMessage());
                            }
                        }
                    });
                }
            } catch (JsonSyntaxException e) {
                System.err.println("[ERROR] Failed to parse WebSocket event: " + e.getMessage());
            }
        });
    }

    public boolean isConnected() {
        return webSocket.isConnected();
        // ToDo: Check the session, too? (e.g. if the session is still alive, otherwise the user is not able to send commands)
        //  You may use a WebDriver BiDi command to check the session status?
        //  -> newSession() Command has to be send otherwise
    }

    // ---- Congestion Detection Watchdog ----

    /**
     * Starts a periodic congestion watchdog that checks whether the WebSocket connection
     * appears stuck. It compares the current message-received count with the previous check:
     * if no new messages arrived while commands are still pending, it warns.
     *
     * Configurable via system properties:
     * - wd4j.congestion.checkIntervalMs (default 5000)
     * - wd4j.congestion.thresholdMs     (default 10000)
     */
    private void startCongestionWatchdog() {
        long checkInterval = Long.getLong("wd4j.congestion.checkIntervalMs", 5000);
        final long threshold = Long.getLong("wd4j.congestion.thresholdMs", 10000);

        congestionWatchdog.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!(webSocket instanceof WDWebSocketImpl)) return;
                    WDWebSocketImpl ws = (WDWebSocketImpl) webSocket;

                    long currentCount = ws.getMessagesReceivedCount();
                    long lastReceived = ws.getLastMessageReceivedTimestamp();
                    long now = System.currentTimeMillis();
                    int pendingCount = responseDispatcher.size();

                    // Check: no new messages since last watchdog run AND we have pending commands
                    if (currentCount == lastWatchdogMessageCount && pendingCount > 0) {
                        long silenceDuration = now - Math.max(lastReceived, lastWatchdogCheckTimestamp);
                        if (silenceDuration >= threshold) {
                            System.err.println("[CONGESTION WARNING] WebSocket connection may be congested! "
                                    + "No messages received for " + silenceDuration + " ms. "
                                    + "Pending commands: " + pendingCount + ", "
                                    + "Total received: " + currentCount + ", "
                                    + "Total sent: " + ws.getMessagesSentCount() + ", "
                                    + "Connected: " + ws.isConnected());
                        }
                    } else if (Boolean.getBoolean("wd4j.log.congestion")) {
                        // Heartbeat log: show throughput stats when congestion logging is enabled
                        long elapsed = now - lastWatchdogCheckTimestamp;
                        long newMessages = currentCount - lastWatchdogMessageCount;
                        System.out.println("[CONGESTION OK] +" + newMessages + " msgs in " + elapsed + " ms"
                                + " (total rx=" + currentCount + ", tx=" + ws.getMessagesSentCount()
                                + ", pending=" + pendingCount + ")");
                    }

                    lastWatchdogMessageCount = currentCount;
                    lastWatchdogCheckTimestamp = now;
                } catch (Exception e) {
                    // Watchdog must never crash
                    System.err.println("[CONGESTION WATCHDOG] Error: " + e.getMessage());
                }
            }
        }, checkInterval, checkInterval, TimeUnit.MILLISECONDS);
    }

    /**
     * Shuts down the event dispatch executor, congestion watchdog, and cancels all pending command futures.
     * Call this when closing the WebSocket connection to prevent thread leaks
     * and to unblock any threads waiting on {@code sendAndWaitForResponse}.
     */
    @Override
    public void shutdown() {
        congestionWatchdog.shutdownNow();
        dispatchExecutor.shutdownNow();

        // Complete all pending futures exceptionally so that no thread remains blocked
        for (Map.Entry<Integer, Consumer<WDCommandResponse<?>>> entry : responseDispatcher.entrySet()) {
            responseDispatcher.remove(entry.getKey());
        }
    }
}
