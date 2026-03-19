package de.bund.zrb.sharepoint;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches a web page and extracts all hyperlinks.
 * Used by the SharePoint settings panel to discover available links
 * from the configured parent page URL.
 */
public final class SharePointLinkFetcher {

    private static final Logger LOG = Logger.getLogger(SharePointLinkFetcher.class.getName());

    private SharePointLinkFetcher() {}

    /**
     * Download the HTML at {@code parentUrl} and extract all {@code <a href>} links.
     *
     * @return list of discovered links (name + URL), never null
     */
    public static List<SharePointSite> fetchLinks(String parentUrl) throws Exception {
        if (parentUrl == null || parentUrl.trim().isEmpty()) {
            return Collections.emptyList();
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        Request request = new Request.Builder()
                .url(parentUrl.trim())
                .header("User-Agent", "MainframeMate/5.x SharePointLinkFetcher")
                .build();

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new RuntimeException("HTTP " + response.code() + " " + response.message());
        }
        String html = response.body() != null ? response.body().string() : "";
        return extractLinks(html, parentUrl);
    }

    /**
     * Parse all {@code <a href="...">label</a>} from HTML and return them as
     * {@link SharePointSite} instances.  The {@code selected} flag is false by default;
     * the user picks which ones are actual SharePoint sites in the Settings panel.
     */
    static List<SharePointSite> extractLinks(String html, String baseUrl) {
        List<SharePointSite> result = new ArrayList<SharePointSite>();
        if (html == null || html.isEmpty()) return result;

        Pattern p = Pattern.compile(
                "<a\\s[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(html);
        Set<String> seen = new HashSet<String>();

        while (m.find()) {
            String href = m.group(1).trim();
            String label = m.group(2).replaceAll("<[^>]+>", "").trim();

            // Skip non-navigable
            if (href.isEmpty() || href.startsWith("javascript:") || href.startsWith("mailto:")
                    || href.startsWith("tel:") || href.equals("#")) {
                continue;
            }

            // Resolve relative
            if (!href.startsWith("http://") && !href.startsWith("https://")) {
                href = resolveRelative(baseUrl, href);
            }

            if (seen.contains(href)) continue;
            seen.add(href);

            if (label.isEmpty()) {
                label = href.length() > 60 ? href.substring(0, 60) + "…" : href;
            }
            if (label.length() > 120) {
                label = label.substring(0, 120) + "…";
            }

            result.add(new SharePointSite(label, href, false));
        }
        return result;
    }

    private static String resolveRelative(String base, String relative) {
        if (relative.startsWith("//")) return "https:" + relative;
        if (relative.startsWith("/")) {
            try {
                java.net.URL u = new java.net.URL(base);
                return u.getProtocol() + "://" + u.getHost()
                        + (u.getPort() > 0 ? ":" + u.getPort() : "") + relative;
            } catch (Exception e) {
                return relative;
            }
        }
        int lastSlash = base.lastIndexOf('/');
        return lastSlash >= 0 ? base.substring(0, lastSlash + 1) + relative : base + "/" + relative;
    }
}

