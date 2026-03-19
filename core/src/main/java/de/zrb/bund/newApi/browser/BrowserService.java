package de.zrb.bund.newApi.browser;

import java.util.List;

/**
 * Generic Browser API – provides browser automation capabilities
 * independent of any specific agent or research logic.
 *
 * <p>This interface abstracts away the underlying browser driver (e.g. wd4j)
 * so that plugins can use browser functionality without depending on
 * driver-specific classes.</p>
 *
 * <p>Implementations are provided by the app core (browser-core layer).
 * Plugins access this via {@link de.zrb.bund.api.MainframeContext#getBrowserService()}.</p>
 */
public interface BrowserService {

    // ── Session Lifecycle ───────────────────────────────────────────

    /**
     * Opens a new browser session using the configured browser settings.
     * If a session already exists and is connected, returns the existing session.
     *
     * @return a BrowserSession handle for browser operations
     * @throws BrowserException if the browser could not be started
     */
    BrowserSession openSession() throws BrowserException;

    /**
     * Returns the current active session, or null if none exists.
     * Does NOT create a new session.
     */
    BrowserSession getActiveSession();

    /**
     * Closes the active browser session and terminates the browser process.
     */
    void closeSession();

    /**
     * Returns true if a browser session is active and connected.
     */
    boolean isSessionActive();

    // ── Configuration ───────────────────────────────────────────────

    /**
     * Returns the configured browser type (e.g. "Firefox", "Chrome", "Edge").
     */
    String getBrowserType();

    /**
     * Returns the configured browser executable path.
     */
    String getBrowserPath();
}

