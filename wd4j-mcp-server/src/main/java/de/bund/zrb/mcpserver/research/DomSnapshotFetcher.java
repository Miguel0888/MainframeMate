package de.bund.zrb.mcpserver.research;

import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.type.script.WDEvaluateResult;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fetches the current DOM as an HTML string via {@code script.evaluate}.
 * <p>
 * This replaces the old {@link NetworkIngestionPipeline}-based HTML capture.
 * Instead of intercepting network responses (which caused browser freezes),
 * we simply ask the browser for the rendered DOM after the page has settled.
 * <p>
 * The returned HTML includes all JS-rendered content (SPAs, lazy-loaded elements)
 * which the network response body would NOT contain.
 */
public class DomSnapshotFetcher {

    private static final Logger LOG = Logger.getLogger(DomSnapshotFetcher.class.getName());

    /** Default settle delay in ms before fetching the DOM snapshot. */
    private static final long DEFAULT_SETTLE_MS = 2000;

    /**
     * Fetch the full DOM HTML of the current page.
     *
     * @param session     the browser session
     * @param settleDelayMs  how long to wait for JS rendering before snapshot (0 = no wait)
     * @return the outerHTML string, or null if fetching failed
     */
    public static String fetchHtml(BrowserSession session, long settleDelayMs) {
        if (session == null || session.getDriver() == null) {
            LOG.warning("[DomSnapshot] No driver available");
            return null;
        }

        // Wait for the page to settle (JS rendering, lazy loads, etc.)
        if (settleDelayMs > 0) {
            try {
                Thread.sleep(settleDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        try {
            WDEvaluateResult result = session.evaluate(
                    "document.documentElement.outerHTML", false);

            if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                String html = ((WDEvaluateResult.WDEvaluateResultSuccess) result)
                        .getResult().asString();
                if (html != null && !html.isEmpty()) {
                    LOG.fine("[DomSnapshot] Captured " + html.length() + " chars");
                    return html;
                }
            } else if (result instanceof WDEvaluateResult.WDEvaluateResultError) {
                WDEvaluateResult.WDEvaluateResultError error =
                        (WDEvaluateResult.WDEvaluateResultError) result;
                LOG.warning("[DomSnapshot] Script error: " + error.getExceptionDetails());
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[DomSnapshot] Failed to fetch DOM", e);
        }

        return null;
    }

    /**
     * Fetch with default settle delay (2 seconds).
     */
    public static String fetchHtml(BrowserSession session) {
        return fetchHtml(session, DEFAULT_SETTLE_MS);
    }

    /**
     * Fetch the current page URL via script.evaluate.
     * Useful after history navigation (back/forward) where the URL isn't returned by the API.
     */
    public static String fetchCurrentUrl(BrowserSession session) {
        if (session == null || session.getDriver() == null) return null;
        try {
            WDEvaluateResult result = session.evaluate("window.location.href", false);
            if (result instanceof WDEvaluateResult.WDEvaluateResultSuccess) {
                return ((WDEvaluateResult.WDEvaluateResultSuccess) result).getResult().asString();
            }
        } catch (Exception e) {
            LOG.fine("[DomSnapshot] Failed to fetch URL: " + e.getMessage());
        }
        return null;
    }
}

