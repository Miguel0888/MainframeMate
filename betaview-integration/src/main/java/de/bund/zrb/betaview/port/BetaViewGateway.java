package de.bund.zrb.betaview.port;

import de.bund.zrb.betaview.domain.BetaViewDocument;
import de.bund.zrb.betaview.domain.BetaViewDocumentRef;
import de.bund.zrb.betaview.domain.BetaViewSearchQuery;
import de.bund.zrb.betaview.domain.BetaViewSearchResult;

/**
 * Port that defines what MainframeMate needs from BetaView.
 * The infrastructure layer provides the concrete adapter.
 */
public interface BetaViewGateway {

    /**
     * Connect to BetaView and perform a search.
     *
     * @param query search parameters including credentials
     * @return search result with raw HTML and parsed document references
     * @throws Exception on connection/search failure
     */
    BetaViewSearchResult search(BetaViewSearchQuery query) throws Exception;

    /**
     * Load the full content of a specific document.
     *
     * @param ref document reference from a previous search
     * @return the loaded document with content
     * @throws Exception on load failure
     */
    BetaViewDocument loadDocument(BetaViewDocumentRef ref) throws Exception;

    /**
     * Navigate a hyperlink within the BetaView result HTML.
     *
     * @param relativePath the relative action path from the link
     * @return the resulting HTML content
     * @throws Exception on navigation failure
     */
    String navigateLink(String relativePath) throws Exception;

    /**
     * Whether a session is currently active.
     */
    boolean isConnected();
}

