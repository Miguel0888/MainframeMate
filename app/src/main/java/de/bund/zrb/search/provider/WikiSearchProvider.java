package de.bund.zrb.search.provider;

import de.bund.zrb.search.BackendSearchProvider;
import de.bund.zrb.search.SearchResult;
import de.bund.zrb.wiki.domain.WikiCredentials;
import de.bund.zrb.wiki.domain.WikiSiteDescriptor;
import de.bund.zrb.wiki.domain.WikiSiteId;
import de.bund.zrb.wiki.port.WikiContentService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Live backend search provider for MediaWiki sites.
 * <p>
 * Uses the MediaWiki Action API ({@code action=query&list=search}) to find
 * pages across all configured wiki sites.  Results are returned as
 * {@link SearchResult}s with source type {@link SearchResult.SourceType#WIKI}.
 * <p>
 * The search runs <b>sequentially per wiki site</b> but the overall provider
 * call itself is executed in parallel by {@link de.bund.zrb.search.SearchService}.
 */
public class WikiSearchProvider implements BackendSearchProvider {

    private static final Logger LOG = Logger.getLogger(WikiSearchProvider.class.getName());

    private final WikiContentService wikiService;
    private final Function<WikiSiteId, WikiCredentials> credentialsResolver;

    /**
     * @param wikiService         the wiki content service (must be pre-configured with sites)
     * @param credentialsResolver resolves login credentials per site; may return {@code null}
     *                            if no credentials are available (anonymous search)
     */
    public WikiSearchProvider(WikiContentService wikiService,
                              Function<WikiSiteId, WikiCredentials> credentialsResolver) {
        this.wikiService = wikiService;
        this.credentialsResolver = credentialsResolver;
    }

    @Override
    public SearchResult.SourceType getSourceType() {
        return SearchResult.SourceType.WIKI;
    }

    @Override
    public boolean isAvailable() {
        return wikiService != null && !wikiService.listSites().isEmpty();
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) throws Exception {
        List<SearchResult> results = new ArrayList<SearchResult>();
        List<WikiSiteDescriptor> sites = wikiService.listSites();

        for (WikiSiteDescriptor site : sites) {
            if (results.size() >= maxResults) break;
            try {
                WikiCredentials creds = credentialsResolver != null
                        ? credentialsResolver.apply(site.id()) : null;
                int limit = Math.min(maxResults - results.size(), 30);
                List<String> titles = wikiService.searchPages(site.id(), query, creds, limit);

                for (String title : titles) {
                    if (results.size() >= maxResults) break;
                    String docId = "wiki://" + site.id().value() + "/" + title;
                    results.add(new SearchResult(
                            SearchResult.SourceType.WIKI,
                            docId,
                            title,
                            site.displayName(),
                            "",   // snippet — MediaWiki search API returns titles only
                            0.8f, // slightly lower than Lucene-indexed results
                            docId,
                            site.displayName()
                    ));
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[WikiSearch] Search failed for site " + site.displayName(), e);
                // Continue with other sites
            }
        }
        return results;
    }
}

