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
     * Prefetch the given page titles in the background.
     * Implementations should use a thread pool and mark entries as volatile/ephemeral.
     */
    void prefetchSearchResults(WikiSiteId siteId, List<String> pageTitles);

    /**
     * Get a previously prefetched page from the in-memory cache.
     * @return the cached WikiPageView, or null if not yet prefetched.
     */
    WikiPageView getCached(WikiSiteId siteId, String pageTitle);

    /**
     * Store a page that was loaded on-demand into the in-memory cache,
     * so re-selecting the same result is instant next time.
     */
    void putInCache(WikiSiteId siteId, String pageTitle, WikiPageView view);

    /**
     * Shut down background threads. Called when the connection tab closes.
     */
    void shutdown();
}
