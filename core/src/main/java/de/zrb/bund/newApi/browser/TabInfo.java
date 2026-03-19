package de.zrb.bund.newApi.browser;

/**
 * Information about a browser tab/browsing context.
 */
public class TabInfo {

    private final String tabId;
    private final String url;
    private final String title;

    public TabInfo(String tabId, String url, String title) {
        this.tabId = tabId;
        this.url = url;
        this.title = title;
    }

    public String getTabId() {
        return tabId;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public String toString() {
        return "TabInfo{id='" + tabId + "', url='" + url + "', title='" + title + "'}";
    }
}

