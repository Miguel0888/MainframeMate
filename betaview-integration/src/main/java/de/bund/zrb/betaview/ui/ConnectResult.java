package de.bund.zrb.betaview.ui;

import de.bund.zrb.betaview.infrastructure.BetaViewClient;
import de.bund.zrb.betaview.infrastructure.BetaViewSession;
import de.bund.zrb.betaview.infrastructure.LoadResultsHtmlUseCase;

import java.net.URL;

/**
 * Result of a successful BetaView connection.
 * In the standalone BetaView app this comes from ConnectDialog.
 * In MainframeMate it is assembled by BetaViewConnectionTab using LoginManager credentials.
 */
public final class ConnectResult {

    private final URL baseUrl;
    private final BetaViewClient client;
    private final BetaViewSession session;
    private final LoadResultsHtmlUseCase loadResultsUseCase;
    private final String displayName;
    private final boolean openLastTabs;

    public ConnectResult(URL baseUrl, BetaViewClient client, BetaViewSession session,
                         LoadResultsHtmlUseCase loadResultsUseCase, String displayName,
                         boolean openLastTabs) {
        this.baseUrl = baseUrl;
        this.client = client;
        this.session = session;
        this.loadResultsUseCase = loadResultsUseCase;
        this.displayName = displayName;
        this.openLastTabs = openLastTabs;
    }

    public URL baseUrl()                               { return baseUrl; }
    public BetaViewClient client()                     { return client; }
    public BetaViewSession session()                   { return session; }
    public LoadResultsHtmlUseCase loadResultsUseCase() { return loadResultsUseCase; }
    public String displayName()                        { return displayName; }
    public boolean openLastTabs()                      { return openLastTabs; }
}

