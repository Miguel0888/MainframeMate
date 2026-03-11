package com.acme.betaview;

import java.net.URL;
import java.util.Objects;

public final class BetaViewApplicationService {

    private final BetaViewClient client;
    private final LoadResultsHtmlUseCase loadResultsHtmlUseCase;

    private BetaViewSession session;

    public BetaViewApplicationService(BetaViewClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.loadResultsHtmlUseCase = new LoadResultsHtmlUseCase(client);
    }

    public void connect(URL baseUrl, String user, String password) throws Exception {
        BetaViewBaseUrl url = new BetaViewBaseUrl(baseUrl);
        BetaViewCredentials credentials = new BetaViewCredentials(user, password);

        // Create a new client instance if yours is bound to baseUrl; otherwise keep as is
        // If your BetaViewHttpClient needs the baseUrl in constructor, create it outside and inject it here instead.
        if (client instanceof BetaViewHttpClient) {
            // Do nothing here, because you already constructed it with the base url elsewhere
        }

        this.session = client.login(credentials);
    }

    public void connectWithClientBoundToUrl(BetaViewClient clientBoundToUrl, String user, String password) throws Exception {
        Objects.requireNonNull(clientBoundToUrl, "clientBoundToUrl must not be null");
        BetaViewCredentials credentials = new BetaViewCredentials(user, password);
        this.session = clientBoundToUrl.login(credentials);
    }

    public String loadResults(ResultFilter filter) throws Exception {
        if (session == null) {
            throw new IllegalStateException("Not connected");
        }
        return loadResultsHtmlUseCase.execute(session, filter);
    }

    public boolean isConnected() {
        return session != null;
    }
}