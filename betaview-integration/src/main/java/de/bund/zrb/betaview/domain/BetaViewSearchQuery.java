package de.bund.zrb.betaview.domain;

/**
 * Search filter parameters for a BetaView query.
 * Credentials are NOT part of this query – they come from the shared LoginManager.
 */
public final class BetaViewSearchQuery {

    private final String baseUrl;
    private final String user;
    private final String password;
    private final String favoriteId;
    private final String locale;
    private final String extensionPattern;
    private final String form;
    private final int daysBack;

    public BetaViewSearchQuery(String baseUrl, String user, String password,
                               String favoriteId, String locale,
                               String extensionPattern, String form, int daysBack) {
        this.baseUrl = baseUrl != null ? baseUrl : "";
        this.user = user != null ? user : "";
        this.password = password != null ? password : "";
        this.favoriteId = favoriteId != null ? favoriteId : "";
        this.locale = locale != null ? locale : "de";
        this.extensionPattern = extensionPattern != null ? extensionPattern : "*";
        this.form = form != null ? form : "APZF";
        this.daysBack = daysBack > 0 ? daysBack : 60;
    }

    public String baseUrl() { return baseUrl; }
    public String user() { return user; }
    public String password() { return password; }
    public String favoriteId() { return favoriteId; }
    public String locale() { return locale; }
    public String extensionPattern() { return extensionPattern; }
    public String form() { return form; }
    public int daysBack() { return daysBack; }
}
