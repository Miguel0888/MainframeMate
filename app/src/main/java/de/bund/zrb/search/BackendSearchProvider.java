package de.bund.zrb.search;

import java.util.List;

/**
 * Provider for live backend search delegation.
 * <p>
 * Backends like Wiki, Confluence, or Local register themselves with
 * {@link SearchService#registerBackendSearchProvider(BackendSearchProvider)}
 * so the central search can dispatch queries to them in parallel.
 * <p>
 * The cache is <b>transparent</b>: each cached entry is found under its
 * real source type (WIKI, CONFLUENCE, FTP, …), not as "Archive".
 * When a backend is connected, the live search supplements the Lucene index
 * to find content that has not yet been indexed.
 * <p>
 * Implementations should be <b>singletons</b> and <b>stateless</b>:
 * they read their configuration (sites, credentials) from
 * {@link de.bund.zrb.model.Settings} on each invocation.
 */
public interface BackendSearchProvider {

    /** The source type this provider handles. */
    SearchResult.SourceType getSourceType();

    /**
     * Perform a live search against the backend.
     * Must be <b>thread-safe</b> — may be called from a worker thread.
     *
     * @param query       the user's search query
     * @param maxResults  maximum number of results to return
     * @param networkZone network zone filter: {@code "INTERN"}, {@code "EXTERN"},
     *                    or {@code null} to search all zones
     * @return list of results, never {@code null}
     * @throws Exception on any backend communication error
     */
    List<SearchResult> search(String query, int maxResults, String networkZone) throws Exception;

    /**
     * Whether the backend has any configured entries and is potentially available.
     * If {@code false}, the SearchService will skip this provider and rely on
     * the Lucene index for results of this source type.
     */
    boolean isAvailable();

    // ═══════════════════════════════════════════════════════════
    //  Prefetch / Content preloading (default no-op)
    // ═══════════════════════════════════════════════════════════

    /**
     * Preload content for the given search results in the background.
     * Called by {@link SearchService} after live search completes.
     * <p>
     * Implementations should start background threads to fetch the full
     * content (HTML) for each result so that preview is instant.
     * <p>
     * Default implementation does nothing (backward compat).
     *
     * @param results the search results to preload
     */
    default void preloadResults(List<SearchResult> results) {
        // no-op by default
    }

    /**
     * Load the full HTML content for a single document (priority load).
     * Used for preview when the user selects a search result.
     * <p>
     * Returns {@code null} if the provider cannot load the content
     * (e.g. the document ID doesn't belong to this provider).
     * <p>
     * Default implementation returns {@code null} (backward compat).
     *
     * @param docId the document ID (e.g. "wiki://siteId/pageTitle" or "confluence://pageId")
     * @return HTML content string, or {@code null} if not available
     */
    default String loadContentHtml(String docId) {
        return null;
    }

    /**
     * Shut down any background prefetch threads.
     * Called on application exit.
     */
    default void shutdown() {
        // no-op by default
    }
}
