package de.bund.zrb.sharepoint;

/**
 * Represents a single SharePoint site discovered from the parent page.
 * <p>
 * The user selects (checks) which links are actual SharePoint sites;
 * selected sites are mounted as WebDAV network paths and browsable like
 * local directories.
 */
public class SharePointSite {

    private String name;
    private String url;
    private boolean selected;

    /** Empty constructor for JSON deserialization. */
    public SharePointSite() {}

    public SharePointSite(String name, String url) {
        this(name, url, false);
    }

    public SharePointSite(String name, String url, boolean selected) {
        this.name = name;
        this.url = url;
        this.selected = selected;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }

    /**
     * Derives the WebDAV UNC path from the SharePoint URL.
     * Example: {@code https://tenant.sharepoint.com/sites/MySite}
     *       → {@code \\tenant.sharepoint.com@SSL\DavWWWRoot\sites\MySite}
     * <p>
     * On Windows the WebClient service resolves these paths transparently.
     */
    public String toUncPath() {
        return SharePointPathUtil.toUncPath(url);
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
        return url != null ? url.equals(that.url) : that.url == null;
    }

    @Override
    public int hashCode() {
        return url != null ? url.hashCode() : 0;
    }
}
