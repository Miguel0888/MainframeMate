package de.bund.zrb.betaview;

import java.io.IOException;
import java.net.URL;

/**
 * Public factory for creating BetaView connections.
 * <p>
 * This class provides public access to the package-private BetaView connection classes,
 * keeping the original class visibility intact while allowing the app module to create connections.
 */
public final class BetaViewFactory {

    private BetaViewFactory() {}

    /**
     * Result of a successful BetaView connection.
     */
    public static final class ConnectionResult {
        private final BetaViewClient client;
        private final BetaViewSession session;
        private final LoadResultsHtmlUseCase loadResultsUseCase;

        ConnectionResult(BetaViewClient client, BetaViewSession session, LoadResultsHtmlUseCase loadResultsUseCase) {
            this.client = client;
            this.session = session;
            this.loadResultsUseCase = loadResultsUseCase;
        }

        public BetaViewClient getClient() { return client; }
        public BetaViewSession getSession() { return session; }
        public LoadResultsHtmlUseCase getLoadResultsUseCase() { return loadResultsUseCase; }
    }

    /**
     * Connect to a BetaView server and return the connection result.
     *
     * @param baseUrl  the base URL of the BetaView server (e.g. https://host/betaview/)
     * @param username the username for login
     * @param password the password for login
     * @return connection result with client, session and use case
     * @throws IOException if the connection fails
     */
    public static ConnectionResult connect(URL baseUrl, String username, String password) throws IOException {
        BetaViewBaseUrl base = new BetaViewBaseUrl(baseUrl);
        BetaViewClient client = new BetaViewHttpClient(base);
        BetaViewCredentials credentials = new BetaViewCredentials(username, password);
        BetaViewSession session = client.login(credentials);
        LoadResultsHtmlUseCase useCase = new LoadResultsHtmlUseCase(client);
        return new ConnectionResult(client, session, useCase);
    }

    /**
     * Create a BetaViewConnectionTab with the given connection result and properties.
     */
    public static BetaViewConnectionTab createConnectionTab(URL baseUrl,
                                                             ConnectionResult connResult,
                                                             String displayName,
                                                             BetaViewAppProperties defaults) {
        return new BetaViewConnectionTab(baseUrl, connResult.client, connResult.session,
                connResult.loadResultsUseCase, displayName, defaults);
    }
}
