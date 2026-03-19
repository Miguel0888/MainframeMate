package de.zrb.bund.newApi.browser;

/**
 * Result of a browser navigation operation.
 */
public class NavigationResult {

    private final String url;
    private final String navigationId;

    public NavigationResult(String url, String navigationId) {
        this.url = url;
        this.navigationId = navigationId;
    }

    /** The final URL after navigation (may differ from requested URL due to redirects). */
    public String getUrl() {
        return url;
    }

    /** The navigation ID assigned by the browser. */
    public String getNavigationId() {
        return navigationId;
    }

    @Override
    public String toString() {
        return "NavigationResult{url='" + url + "', navigationId='" + navigationId + "'}";
    }
}

