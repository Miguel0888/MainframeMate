package de.bund.zrb.websearch.fetch;

import java.util.logging.Logger;

/**
 * Smart content fetching strategy that tries Jsoup first (fast, lightweight)
 * and falls back to the browser only when Jsoup cannot extract meaningful content
 * (e.g., JavaScript-heavy pages, SPAs).
 *
 * <p>Supports three modes:</p>
 * <ul>
 *     <li><b>SMART</b> (default): Try Jsoup first, fall back to browser if content is insufficient</li>
 *     <li><b>JSOUP_ONLY</b>: Only use Jsoup (fastest, no browser needed)</li>
 *     <li><b>BROWSER_ONLY</b>: Only use browser (for interactive/JS-heavy pages)</li>
 * </ul>
 */
public class SmartContentFetcherStrategy implements ContentFetcher {

    private static final Logger LOG = Logger.getLogger(SmartContentFetcherStrategy.class.getName());

    public enum Mode {
        SMART,
        JSOUP_ONLY,
        BROWSER_ONLY
    }

    private final JsoupContentFetcher jsoupFetcher;
    private final BrowserContentFetcher browserFetcher;
    private final Mode mode;

    /**
     * Create with both fetchers and smart mode.
     */
    public SmartContentFetcherStrategy(JsoupContentFetcher jsoupFetcher, BrowserContentFetcher browserFetcher) {
        this(jsoupFetcher, browserFetcher, Mode.SMART);
    }

    /**
     * Create with both fetchers and specified mode.
     */
    public SmartContentFetcherStrategy(JsoupContentFetcher jsoupFetcher, BrowserContentFetcher browserFetcher, Mode mode) {
        this.jsoupFetcher = jsoupFetcher;
        this.browserFetcher = browserFetcher;
        this.mode = mode;
    }

    /**
     * Create with Jsoup only (no browser fallback available).
     */
    public SmartContentFetcherStrategy(JsoupContentFetcher jsoupFetcher) {
        this.jsoupFetcher = jsoupFetcher;
        this.browserFetcher = null;
        this.mode = Mode.JSOUP_ONLY;
    }

    @Override
    public String getName() {
        return "Smart (" + mode + ")";
    }

    @Override
    public FetchResult fetch(String url) {
        return fetch(url, FetchOptions.DEFAULT);
    }

    @Override
    public FetchResult fetch(String url, FetchOptions options) {
        switch (mode) {
            case JSOUP_ONLY:
                return fetchWithJsoup(url, options);
            case BROWSER_ONLY:
                return fetchWithBrowser(url, options);
            case SMART:
            default:
                return fetchSmart(url, options);
        }
    }

    /**
     * Smart strategy: Jsoup first, browser fallback if content is insufficient.
     */
    private FetchResult fetchSmart(String url, FetchOptions options) {
        LOG.info("[SmartFetcher] Trying Jsoup first for: " + url);

        // Step 1: Try Jsoup
        FetchResult jsoupResult = jsoupFetcher.fetch(url, options);

        if (jsoupResult.isSuccess() && jsoupResult.hasSubstantialContent()) {
            LOG.info("[SmartFetcher] Jsoup succeeded with substantial content for: " + url
                    + " (textLen=" + jsoupResult.getText().length() + ")");
            return jsoupResult;
        }

        // Step 2: Jsoup didn't get enough content - try browser
        if (browserFetcher != null) {
            String reason = !jsoupResult.isSuccess()
                    ? "Jsoup failed: " + jsoupResult.getErrorMessage()
                    : "Jsoup content insufficient (textLen="
                      + (jsoupResult.getText() != null ? jsoupResult.getText().length() : 0) + ")";
            LOG.info("[SmartFetcher] " + reason + " – falling back to browser for: " + url);

            FetchResult browserResult = browserFetcher.fetch(url, options);

            if (browserResult.isSuccess()) {
                LOG.info("[SmartFetcher] Browser fallback succeeded for: " + url);
                return browserResult;
            }

            // Both failed – return whichever had more content
            LOG.warning("[SmartFetcher] Both Jsoup and browser failed for: " + url);
            if (jsoupResult.isSuccess() && jsoupResult.getText() != null && !jsoupResult.getText().isEmpty()) {
                // Jsoup at least got something
                return jsoupResult;
            }
            return browserResult;
        }

        // No browser available – return Jsoup result (even if partial)
        if (jsoupResult.isSuccess()) {
            LOG.info("[SmartFetcher] Jsoup partial result (no browser fallback) for: " + url);
            return jsoupResult;
        }
        return jsoupResult;
    }

    private FetchResult fetchWithJsoup(String url, FetchOptions options) {
        return jsoupFetcher.fetch(url, options);
    }

    private FetchResult fetchWithBrowser(String url, FetchOptions options) {
        if (browserFetcher == null) {
            return FetchResult.error(url, "Browser fetcher not available");
        }
        return browserFetcher.fetch(url, options);
    }

    public Mode getMode() {
        return mode;
    }
}
