package de.bund.zrb.sharepoint;

import java.net.URL;
import java.util.logging.Logger;

/**
 * Utility for converting SharePoint URLs to WebDAV UNC paths and vice versa.
 * <p>
 * SharePoint Online/On-Premises exposes every document library via WebDAV.
 * On Windows the built-in <em>WebClient</em> service makes UNC paths like
 * {@code \\host@SSL\DavWWWRoot\sites\name} directly navigable by
 * {@link java.io.File} and Explorer.
 *
 * <h3>Mapping rules</h3>
 * <pre>
 *   https://host/path  →  \\host@SSL\DavWWWRoot\path
 *   http://host/path   →  \\host\DavWWWRoot\path
 * </pre>
 */
public final class SharePointPathUtil {

    private static final Logger LOG = Logger.getLogger(SharePointPathUtil.class.getName());

    private SharePointPathUtil() {}

    /**
     * Convert a SharePoint web URL to a Windows WebDAV UNC path.
     *
     * @param webUrl e.g. {@code https://myorg.sharepoint.com/sites/TeamSite/Shared Documents}
     * @return UNC path e.g. {@code \\myorg.sharepoint.com@SSL\DavWWWRoot\sites\TeamSite\Shared Documents}
     */
    public static String toUncPath(String webUrl) {
        if (webUrl == null || webUrl.trim().isEmpty()) return "";
        try {
            URL parsed = new URL(webUrl.trim());
            String host = parsed.getHost();
            String path = parsed.getPath();
            boolean ssl = "https".equalsIgnoreCase(parsed.getProtocol());
            int port = parsed.getPort();

            StringBuilder sb = new StringBuilder("\\\\");
            sb.append(host);
            if (ssl) sb.append("@SSL");
            if (port > 0 && port != 80 && port != 443) {
                sb.append("@").append(port);
            }
            sb.append("\\DavWWWRoot");
            if (path != null && !path.isEmpty()) {
                sb.append(path.replace('/', '\\'));
            }
            return sb.toString();
        } catch (Exception e) {
            LOG.warning("Cannot convert to UNC: " + webUrl + " – " + e.getMessage());
            return webUrl;
        }
    }

    /**
     * Convert a WebDAV UNC path back to a web URL (best-effort).
     */
    public static String toWebUrl(String uncPath) {
        if (uncPath == null || !uncPath.startsWith("\\\\")) return uncPath;
        try {
            String rest = uncPath.substring(2); // strip leading \\
            int firstSlash = rest.indexOf('\\');
            if (firstSlash < 0) return uncPath;

            String hostPart = rest.substring(0, firstSlash);
            String pathPart = rest.substring(firstSlash);

            boolean ssl = hostPart.contains("@SSL");
            String host = hostPart.replace("@SSL", "");
            // remove @port if present
            int atPort = host.indexOf('@');
            if (atPort > 0) host = host.substring(0, atPort);

            // strip \DavWWWRoot prefix
            if (pathPart.startsWith("\\DavWWWRoot")) {
                pathPart = pathPart.substring("\\DavWWWRoot".length());
            }
            pathPart = pathPart.replace('\\', '/');

            return (ssl ? "https://" : "http://") + host + pathPart;
        } catch (Exception e) {
            return uncPath;
        }
    }

    /**
     * Extract a display name from a SharePoint URL or UNC path.
     */
    public static String extractDisplayName(String urlOrPath) {
        if (urlOrPath == null) return "?";

        // Try /sites/SiteName pattern
        String lower = urlOrPath.toLowerCase();
        int sitesIdx = lower.indexOf("/sites/");
        if (sitesIdx < 0) sitesIdx = lower.indexOf("\\sites\\");
        if (sitesIdx >= 0) {
            String rest = urlOrPath.substring(sitesIdx + 7);
            int end = rest.indexOf('/');
            if (end < 0) end = rest.indexOf('\\');
            if (end < 0) end = rest.length();
            return rest.substring(0, end).replace("%20", " ").replace("-", " ");
        }

        // Try hostname
        try {
            return new URL(urlOrPath).getHost();
        } catch (Exception ignore) {}

        // Fallback
        return urlOrPath.length() > 30 ? urlOrPath.substring(0, 30) + "…" : urlOrPath;
    }
}

