package de.bund.zrb.wiki.ui;

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
     * Shut down background threads. Called when the connection tab closes.
     */
    void shutdown();
}

