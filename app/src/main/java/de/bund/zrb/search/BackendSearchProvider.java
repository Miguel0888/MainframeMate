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
 */
public interface BackendSearchProvider {

    /** The source type this provider handles. */
    SearchResult.SourceType getSourceType();

    /**
     * Perform a live search against the backend.
     * Must be <b>thread-safe</b> — may be called from a worker thread.
     *
     * @param query      the user's search query
     * @param maxResults maximum number of results to return
     * @return list of results, never {@code null}
     * @throws Exception on any backend communication error
     */
    List<SearchResult> search(String query, int maxResults) throws Exception;

    /**
     * Whether the backend is currently connected and available for live search.
     * If {@code false}, the SearchService will skip this provider and rely on
     * the Lucene index for results of this source type.
     */
    boolean isAvailable();
}

