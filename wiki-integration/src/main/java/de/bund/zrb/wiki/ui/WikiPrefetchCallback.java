package de.bund.zrb.wiki.ui;

import de.bund.zrb.wiki.domain.WikiPageView;
import de.bund.zrb.wiki.domain.WikiSiteId;

import java.util.List;

/**
 * Callback interface for prefetching wiki search results into the local cache.
 * The implementation lives in the app module (has access to CacheRepository).
 * The wiki-integration module only knows this interface.
 */
public interface WikiPrefetchCallback {

    /**
     * Prefetch pages starting from the given index in the result list.
     * Called after search completes (fromIndex=0) and when the user scrolls/selects further down.
     */
    void prefetchSearchResults(WikiSiteId siteId, List<String> pageTitles, int fromIndex);

    /**
     * Convenience: prefetch from the beginning.
     */
    default void prefetchSearchResults(WikiSiteId siteId, List<String> pageTitles) {
        prefetchSearchResults(siteId, pageTitles, 0);
    }

    /**
     * Get a previously prefetched page from the in-memory cache. O(1).
     * @return the cached WikiPageView, or null if not yet prefetched.
     */
    WikiPageView getCached(WikiSiteId siteId, String pageTitle);

    /**
     * Store a page that was loaded on-demand into the in-memory cache,
     * so re-selecting the same result is instant next time.
     */
    void putInCache(WikiSiteId siteId, String pageTitle, WikiPageView view);

    /**
     * Load a single page with high priority on a dedicated thread,
     * bypassing the prefetch queue. For user-initiated preview/open.
     * Returns null on error.
     */
    WikiPageView loadPriority(WikiSiteId siteId, String pageTitle);

    /**
     * Shut down background threads. Called when the connection tab closes.
     */
    void shutdown();
}
