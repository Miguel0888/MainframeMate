package de.bund.zrb;

import de.bund.zrb.api.WDWebSocketManager;
import de.bund.zrb.command.response.WDSessionResult;
import de.bund.zrb.manager.*;
import de.bund.zrb.service.WDEventDispatcher;
import de.bund.zrb.type.session.WDSubscription;
import de.bund.zrb.type.session.WDSubscriptionRequest;

import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * This class is the entry point for the low-level WebDriver API. Aggregates all WebDriver Modules at one place.
 * @see WDBrowserManager
 * @see WDSessionManager
 * @see WDBrowsingContextManager
 * @see WDScriptManager
 * @see WDInputManager
 * @see WDStorageManager
 * @see WDNetworkManager
 * @see WDLogManager
 * @see WDWebExtensionManager
 *
 * @link https://www.w3.org/TR/webdriver-bidi/#modules
 * @link https://de.wikipedia.org/wiki/Fassade_(Entwurfsmuster)
 */
public class WebDriver {

    private final WDWebSocketManager webSocketManager;

    private WDBrowserManager browser;
    private WDSessionManager session;
    private WDBrowsingContextManager browsingContext;
    private WDScriptManager script;
    private WDInputManager input;
    private WDStorageManager storage;
    private WDNetworkManager network;
    private WDLogManager log;
    private WDWebExtensionManager webExtension;

    private String sessionId;

    private final WDEventDispatcher dispatcher;

    // ToDo: Use WebSocket Interface instead of WebSocketImpl, here !!!
    public WebDriver(WDWebSocketImpl webSocketImpl) throws ExecutionException, InterruptedException {
        this(webSocketImpl, new WDEventDispatcher());
    }

    // ToDo: Use WebSocket Interface instead of WebSocketImpl, here !!!
    public WebDriver(WDWebSocketImpl webSocketImpl, WDEventDispatcher dispatcher) throws ExecutionException, InterruptedException {
        this.webSocketManager = new WDWebSocketManagerImpl(webSocketImpl);

        this.browser = new WDBrowserManager(webSocketManager);
        this.session = new WDSessionManager(webSocketManager);
        this.browsingContext = new WDBrowsingContextManager(webSocketManager);
        this.script = new WDScriptManager(webSocketManager);
        this.input = new WDInputManager(webSocketManager);
        this.storage = new WDStorageManager(webSocketManager);
        this.network = new WDNetworkManager(webSocketManager);
        this.log = new WDLogManager(webSocketManager);
        this.webExtension = new WDWebExtensionManager(webSocketManager);

        this.dispatcher = dispatcher;
        webSocketManager.registerEventListener(dispatcher); // 🔥 Events aktivieren!
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // WebDriver BiDi Modules
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public WDBrowserManager browser() {
        return browser;
    }

    public WDSessionManager session() {
        return session;
    }

    public WDBrowsingContextManager browsingContext() {
        return browsingContext;
    }

    public WDScriptManager script() {
        return script;
    }

    public WDInputManager input() {
        return input;
    }

    public WDStorageManager storage() {
        return storage;
    }

    public WDNetworkManager network() {
        return network;
    }

    public WDLogManager log() {
        return log;
    }

    public WDWebExtensionManager webExtension() {
        return webExtension;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Event Handling
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

//    public <T> void on(WDEventType<T> event, Consumer<T> handler) {
//        connection.on(event.getName(), handler);
//    }
//
//    public <T> void off(WDEventType<T> event, Consumer<T> handler) {
//        connection.off(event.getName(), handler);
//    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // private (required for WebDriver Class)
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Creates a new session aka. browsing context.
     *
     * Starts a new session with the default browser. For one Browser only one session can be active at a time.
     * Therefore, this is recognized as a WebDriver core functionality.
     *
     * The default contextId is not provided by every browser.
     * E.g. Firefox ESR does not provide a default contextId, whereas the normal Firefox does.
     *
     * To avoid this issue, you can also create a new context every time you launch a browser. Thus, this method is optional.
     */
    public WebDriver connect(String browserName) throws InterruptedException, ExecutionException {
        // Try session.status first to check if browser is ready
        try {
            WDSessionResult.StatusResult status = session().status();
            if (status != null) {
                System.out.println("[WebDriver] session.status OK: ready=" + status.isReady()
                        + ", message=" + status.getMessage());
            }
        } catch (Exception e) {
            System.out.println("[WebDriver] session.status failed (non-fatal): " + e.getMessage());
        }

        // Try to create a new session
        try {
            WDSessionResult.NewResult sessionResponse = session().newSession(browserName);
            if (sessionResponse != null && sessionResponse.getSessionId() != null) {
                sessionId = sessionResponse.getSessionId();
                System.out.println("[WebDriver] session.new succeeded: sessionId=" + sessionId);
                return this;
            }
        } catch (Exception e) {
            System.out.println("[WebDriver] session.new failed for '" + browserName + "': " + e.getMessage());
            // Chrome/Edge without Chromedriver may not support session.new directly.
            // Fall through to fallback.
        }

        // Fallback: generate a session ID (Chrome BiDi works without explicit session.new
        // when connecting directly to the browser's BiDi endpoint)
        sessionId = "bidi-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        System.out.println("[WebDriver] Using fallback sessionId: " + sessionId
                + " (browser=" + browserName + ")");
        return this; // fluent API
    }


    /**
     * Reuses a session with the default browser identified by the given session ID.
     * For one Browser only one session can be active at a time.
     * Therefore, this is recognized as a WebDriver core functionality.
     *
     * @param sessionId
     * @return
     */
    // ToDo: Check if a session ID can be reused, or a default session ID is provided and can be reused
    public WebDriver reconnect(String sessionId) {
        // Reuse the session
        // ToDo: check status ?
        this.sessionId = sessionId;
        return this; // fluent API
    }


    public boolean isConnected() {
        return webSocketManager.isConnected();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public <T> WDSubscription addEventListener(WDSubscriptionRequest subscriptionRequest, Consumer<T> handler) {
        return dispatcher.addEventListener(subscriptionRequest, handler, session());
    }

    public <T> void removeEventListener(String eventType, String browsingContextId, Consumer<T> listener) {
        dispatcher.removeEventListener(eventType, browsingContextId, listener, session());
    }

    // ToDo: Not supported yet
    public <T> void removeEventListener(WDSubscription subscription, Consumer<T> listener) {
        dispatcher.removeEventListener(subscription, listener, session());
    }

    @Deprecated // Since it does neither use the subscription id nor the browsing context id, thus terminating all listeners for the event type
    public <T> void removeEventListener(String eventType, Consumer<T> listener) {
        dispatcher.removeEventListener(eventType, listener, session());
    }


    public WDWebSocketManager getWebSocketManager() {
        return webSocketManager;
    }
}
