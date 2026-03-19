package de.zrb.bund.newApi.browser;

import java.util.List;

/**
 * Represents an active browser session with navigation and DOM interaction capabilities.
 *
 * <p>This is a driver-agnostic interface. The underlying implementation
 * (e.g. wd4j-based) is hidden from plugins. All operations work on the
 * currently active browsing context (tab).</p>
 */
public interface BrowserSession {

    // ── Navigation ──────────────────────────────────────────────────

    /**
     * Navigate to the given URL in the active tab.
     *
     * @param url the URL to navigate to
     * @return navigation result with final URL and status
     * @throws BrowserException on navigation failure
     */
    NavigationResult navigate(String url) throws BrowserException;

    /**
     * Navigate back in browser history.
     */
    void navigateBack() throws BrowserException;

    /**
     * Navigate forward in browser history.
     */
    void navigateForward() throws BrowserException;

    /**
     * Returns the URL of the currently loaded page.
     */
    String getCurrentUrl();

    // ── Page Content ────────────────────────────────────────────────

    /**
     * Read the text content of the current page.
     *
     * @return page text content (may be truncated for large pages)
     */
    String getPageContent() throws BrowserException;

    /**
     * Capture a DOM snapshot of the current page.
     *
     * @return the DOM snapshot as a string (HTML or serialized form)
     */
    String getDomSnapshot() throws BrowserException;

    /**
     * Capture a screenshot of the current page.
     *
     * @return base64-encoded screenshot image
     */
    String captureScreenshot() throws BrowserException;

    // ── Element Interaction ─────────────────────────────────────────

    /**
     * Click an element identified by CSS selector.
     *
     * @param cssSelector CSS selector for the target element
     * @throws BrowserException if element not found or click fails
     */
    void click(String cssSelector) throws BrowserException;

    /**
     * Click an element identified by a node reference ID.
     *
     * @param nodeRefId the node reference ID (e.g. "n5")
     * @throws BrowserException if ref not found or click fails
     */
    void clickByRef(String nodeRefId) throws BrowserException;

    /**
     * Type text into an input element identified by CSS selector.
     *
     * @param cssSelector CSS selector for the input element
     * @param text        the text to type
     * @param clearFirst  whether to clear existing content before typing
     */
    void type(String cssSelector, String text, boolean clearFirst) throws BrowserException;

    /**
     * Type text into an input element identified by node reference ID.
     *
     * @param nodeRefId  the node reference ID
     * @param text       the text to type
     * @param clearFirst whether to clear existing content before typing
     */
    void typeByRef(String nodeRefId, String text, boolean clearFirst) throws BrowserException;

    /**
     * Select an option in a select element.
     *
     * @param cssSelector CSS selector for the select element
     * @param value       option value (or null)
     * @param label       option label (or null)
     * @param index       option index (or null)
     */
    void select(String cssSelector, String value, String label, Integer index) throws BrowserException;

    /**
     * Scroll the page or a specific element.
     *
     * @param cssSelector optional CSS selector to scroll into view (null for page scroll)
     * @param deltaX      horizontal scroll amount
     * @param deltaY      vertical scroll amount
     */
    void scroll(String cssSelector, int deltaX, int deltaY) throws BrowserException;

    // ── JavaScript ──────────────────────────────────────────────────

    /**
     * Execute arbitrary JavaScript in the current page context.
     *
     * @param script        the JavaScript code to execute
     * @param awaitPromise  whether to await a returned Promise
     * @return the string representation of the result
     */
    String executeScript(String script, boolean awaitPromise) throws BrowserException;

    // ── Tab Management ──────────────────────────────────────────────

    /**
     * Returns the ID of the active browsing context (tab).
     */
    String getActiveTabId();

    /**
     * List all open tabs/pages.
     *
     * @return list of tab descriptors
     */
    List<TabInfo> listTabs() throws BrowserException;

    /**
     * Switch to a different tab by its ID.
     *
     * @param tabId the tab/context ID to activate
     */
    void activateTab(String tabId) throws BrowserException;

    // ── Connection State ────────────────────────────────────────────

    /**
     * Returns true if the browser connection is still active.
     */
    boolean isConnected();

    /**
     * Close this session and release all resources.
     */
    void close();
}

