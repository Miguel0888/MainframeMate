package de.bund.zrb.mcpserver.research;

/**
 * Classifies extracted links as internal or external relative to a domain boundary.
 * <p>
 * The domain boundary is typically the base domain of the seed URL (e.g. "yahoo.com")
 * so that subdomains like "news.yahoo.com" and "de.finance.yahoo.com" are all internal.
 */
public class LinkClassifier {

    private final String baseDomain;

    /**
     * Creates a classifier with the given base domain boundary.
     *
     * @param seedUrl the seed URL from which the base domain is derived
     */
    public LinkClassifier(String seedUrl) {
        this.baseDomain = UrlCanonicalizer.extractBaseDomain(seedUrl);
    }

    /**
     * Creates a classifier with an explicit domain boundary string.
     *
     * @param baseDomain e.g. "yahoo.com" or "bundestag.de"
     * @param ignored    disambiguator for overload
     */
    public LinkClassifier(String baseDomain, boolean ignored) {
        this.baseDomain = baseDomain != null ? baseDomain.toLowerCase() : null;
    }

    /**
     * Returns true if the given link URL belongs to the same domain boundary.
     */
    public boolean isInternal(String linkUrl) {
        if (baseDomain == null || linkUrl == null) return false;
        String linkBaseDomain = UrlCanonicalizer.extractBaseDomain(linkUrl);
        return baseDomain.equalsIgnoreCase(linkBaseDomain);
    }

    /**
     * Returns true if the given link URL is external (not same domain boundary).
     */
    public boolean isExternal(String linkUrl) {
        return !isInternal(linkUrl);
    }

    /**
     * Returns the base domain used for classification.
     */
    public String getBaseDomain() {
        return baseDomain;
    }
}
