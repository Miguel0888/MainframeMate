package de.bund.zrb.betaview.usecase;

import de.bund.zrb.betaview.domain.BetaViewDocument;
import de.bund.zrb.betaview.domain.BetaViewDocumentRef;
import de.bund.zrb.betaview.port.BetaViewGateway;

/**
 * Use case: Load a full BetaView document by its reference.
 */
public class LoadBetaViewDocumentUseCase {

    private final BetaViewGateway gateway;

    public LoadBetaViewDocumentUseCase(BetaViewGateway gateway) {
        this.gateway = gateway;
    }

    public BetaViewDocument execute(BetaViewDocumentRef ref) throws Exception {
        return gateway.loadDocument(ref);
    }
}

