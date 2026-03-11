package de.bund.zrb.wiki.domain;

/**
 * Descriptor for a wiki site shown in the UI dropdown.
 */
public final class WikiSiteDescriptor {
    private final WikiSiteId id;
    private final String displayName;
    private final String apiUrl;
    private final boolean requiresLogin;

    public WikiSiteDescriptor(WikiSiteId id, String displayName, String apiUrl, boolean requiresLogin) {
        this.id = id;
        this.displayName = displayName;
        this.apiUrl = apiUrl;
        this.requiresLogin = requiresLogin;
    }

    public WikiSiteId id() { return id; }
    public String displayName() { return displayName; }
    public String apiUrl() { return apiUrl; }
    public boolean requiresLogin() { return requiresLogin; }

    @Override
    public String toString() { return displayName; }
}

