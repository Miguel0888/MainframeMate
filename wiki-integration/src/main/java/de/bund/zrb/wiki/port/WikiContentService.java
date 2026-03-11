package de.bund.zrb.wiki.port;

import de.bund.zrb.wiki.domain.*;

import java.io.IOException;
import java.util.List;

/**
 * Service interface for accessing MediaWiki content.
 * Implementations may use JWBF, REST API, or any other backend.
 */
public interface WikiContentService {

    /**
     * List all configured wiki sites (for the dropdown).
     */
    List<WikiSiteDescriptor> listSites();

    /**
     * Load a wiki page and return cleaned HTML + outline.
     */
    WikiPageView loadPage(WikiSiteId siteId, String pageTitle, WikiCredentials credentials) throws IOException;

    /**
     * Load outgoing links from a page (with continuation/paging).
     */
    List<String> loadOutgoingLinks(WikiSiteId siteId, String pageTitle, WikiCredentials credentials) throws IOException;

    /**
     * Search for pages matching a query string.
     */
    List<String> searchPages(WikiSiteId siteId, String query, WikiCredentials credentials, int limit) throws IOException;
}

