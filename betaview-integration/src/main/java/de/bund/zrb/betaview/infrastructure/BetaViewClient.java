package de.bund.zrb.betaview.infrastructure;

import java.io.IOException;
import java.util.Map;

/**
 * Low-level client interface for BetaView HTTP communication.
 * Modelled after the betaview-example architecture.
 */
public interface BetaViewClient {

    /**
     * Perform login and return a session.
     */
    BetaViewSession login(BetaViewCredentials credentials) throws IOException;

    /**
     * GET a text resource relative to the base URL.
     */
    String getText(BetaViewSession session, String relativePath) throws IOException;

    /**
     * GET a binary resource relative to the base URL.
     */
    byte[] getBinary(BetaViewSession session, String relativePath) throws IOException;

    /**
     * POST a form and return the text response body.
     */
    String postFormText(BetaViewSession session, String relativePath, Map<String, String> fields) throws IOException;
}

