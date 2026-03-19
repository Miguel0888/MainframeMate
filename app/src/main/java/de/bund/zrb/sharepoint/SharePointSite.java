package de.bund.zrb.sharepoint;

/**
 * Represents a single SharePoint site extracted from the parent page.
 */
public class SharePointSite {

    private final String name;
    private final String url;

    public SharePointSite(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SharePointSite that = (SharePointSite) o;
        return url.equals(that.url);
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }
}

