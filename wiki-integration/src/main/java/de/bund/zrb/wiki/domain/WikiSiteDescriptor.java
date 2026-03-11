package de.bund.zrb.wiki.domain;

/**
 * Descriptor for a wiki site shown in the UI dropdown.
 */
public final class WikiSiteDescriptor {
    private final WikiSiteId id;
    private final String displayName;
    private final String apiUrl;
    private final boolean requiresLogin;
    private final boolean useProxy;
    private final boolean autoIndex;

    public WikiSiteDescriptor(WikiSiteId id, String displayName, String apiUrl, boolean requiresLogin) {
        this(id, displayName, apiUrl, requiresLogin, false, false);
    }

    public WikiSiteDescriptor(WikiSiteId id, String displayName, String apiUrl, boolean requiresLogin, boolean useProxy) {
        this(id, displayName, apiUrl, requiresLogin, useProxy, false);
    }

    public WikiSiteDescriptor(WikiSiteId id, String displayName, String apiUrl, boolean requiresLogin, boolean useProxy, boolean autoIndex) {
        this.id = id;
        this.displayName = displayName;
        this.apiUrl = apiUrl;
        this.requiresLogin = requiresLogin;
        this.useProxy = useProxy;
        this.autoIndex = autoIndex;
    }

    public WikiSiteId id() { return id; }
    public String displayName() { return displayName; }
    public String apiUrl() { return apiUrl; }
    public boolean requiresLogin() { return requiresLogin; }
    public boolean useProxy() { return useProxy; }
    public boolean autoIndex() { return autoIndex; }

    @Override
    public String toString() { return displayName; }
}

