package de.bund.zrb.betaview.usecase;

import de.bund.zrb.betaview.domain.BetaViewSearchQuery;
import de.bund.zrb.betaview.domain.BetaViewSearchResult;
import de.bund.zrb.betaview.port.BetaViewGateway;

/**
 * Use case: Execute a BetaView search and return structured results.
 */
public class SearchBetaViewUseCase {

    private final BetaViewGateway gateway;

    public SearchBetaViewUseCase(BetaViewGateway gateway) {
        this.gateway = gateway;
    }

    public BetaViewSearchResult execute(BetaViewSearchQuery query) throws Exception {
        return gateway.search(query);
    }
}

