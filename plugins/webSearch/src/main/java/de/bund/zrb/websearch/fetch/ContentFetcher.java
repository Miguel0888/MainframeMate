package de.bund.zrb.websearch.fetch;

/**
 * Strategy interface for fetching web page content.
 * Implementations may use Jsoup (direct HTTP), a browser via WebDriver BiDi,
 * or other mechanisms.
 */
public interface ContentFetcher {

    /**
     * Fetch the content of a URL.
     *
     * @param url the URL to fetch
     * @return the fetch result containing extracted text, title, links, etc.
     */
    FetchResult fetch(String url);

    /**
     * Fetch the content of a URL with options.
     *
     * @param url     the URL to fetch
     * @param options fetch options (timeout, max length, etc.)
     * @return the fetch result
     */
    FetchResult fetch(String url, FetchOptions options);

    /**
     * Returns the name of this fetcher implementation for logging/debugging.
     */
    String getName();
}
