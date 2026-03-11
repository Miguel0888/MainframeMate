package de.bund.zrb.wiki.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.bund.zrb.wiki.domain.*;
import de.bund.zrb.wiki.port.WikiContentService;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WikiContentService implementation using plain HTTP + MediaWiki Action API.
 * Uses JWBF only for login/session management (cookies).
 * Falls back to anonymous access if no login needed.
 */
public class JwbfWikiContentService implements WikiContentService {

    private static final Logger LOG = Logger.getLogger(JwbfWikiContentService.class.getName());

    private final List<WikiSiteDescriptor> sites;
    private final JwbfBotProvider botProvider;
    private final ObjectMapper mapper;
    private final HtmlPostProcessor htmlProcessor;

    public JwbfWikiContentService(List<WikiSiteDescriptor> sites) {
        this.sites = new ArrayList<WikiSiteDescriptor>(Objects.requireNonNull(sites));
        this.botProvider = new JwbfBotProvider();
        this.mapper = new ObjectMapper();
        this.htmlProcessor = new HtmlPostProcessor();
    }

    @Override
    public List<WikiSiteDescriptor> listSites() {
        return Collections.unmodifiableList(sites);
    }

    @Override
    public WikiPageView loadPage(WikiSiteId siteId, String pageTitle, WikiCredentials credentials) throws IOException {
        WikiSiteDescriptor site = findSite(siteId);

        // Ensure login if needed (establishes cookies in bot)
        if (site.requiresLogin() && credentials != null && !credentials.isAnonymous()) {
            botProvider.getBot(site, credentials);
        }

        String apiUrl = buildApiUrl(site, "action=parse&format=json&formatversion=2&disabletoc=1&prop=text&page="
                + enc(pageTitle));

        String json = httpGet(apiUrl);
        String rawHtml = extractHtml(json);
        HtmlPostProcessor.Result result = htmlProcessor.cleanAndBuildOutline(rawHtml);

        return new WikiPageView(pageTitle, result.cleanedHtml, result.outlineRoot, result.images);
    }

    @Override
    public List<String> loadOutgoingLinks(WikiSiteId siteId, String pageTitle, WikiCredentials credentials) throws IOException {
        WikiSiteDescriptor site = findSite(siteId);
        List<String> all = new ArrayList<String>();
        String plcontinue = null;

        for (int i = 0; i < 50; i++) {
            StringBuilder params = new StringBuilder();
            params.append("action=query&format=json&formatversion=2&prop=links&pllimit=max&titles=");
            params.append(enc(pageTitle));
            if (plcontinue != null) {
                params.append("&plcontinue=").append(enc(plcontinue));
            }

            String json = httpGet(buildApiUrl(site, params.toString()));
            JsonNode root = mapper.readTree(json);
            JsonNode pages = root.path("query").path("pages");

            if (pages.isArray()) {
                for (JsonNode page : pages) {
                    JsonNode links = page.path("links");
                    if (links.isArray()) {
                        for (JsonNode link : links) {
                            all.add(link.path("title").asText());
                        }
                    }
                }
            }

            JsonNode cont = root.path("continue");
            if (cont.isMissingNode() || !cont.has("plcontinue")) break;
            plcontinue = cont.path("plcontinue").asText();
        }

        return all;
    }

    @Override
    public List<String> searchPages(WikiSiteId siteId, String query, WikiCredentials credentials, int limit) throws IOException {
        WikiSiteDescriptor site = findSite(siteId);

        String apiUrl = buildApiUrl(site,
                "action=query&format=json&formatversion=2&list=search&srlimit="
                        + Math.min(limit, 50)
                        + "&srsearch=" + enc(query));

        String json = httpGet(apiUrl);
        List<String> results = new ArrayList<String>();
        JsonNode root = mapper.readTree(json);
        JsonNode searchResults = root.path("query").path("search");
        if (searchResults.isArray()) {
            for (JsonNode item : searchResults) {
                results.add(item.path("title").asText());
            }
        }
        return results;
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    private String extractHtml(String json) throws IOException {
        JsonNode root = mapper.readTree(json);

        // Check for error
        if (root.has("error")) {
            String errorMsg = root.path("error").path("info").asText("Unknown error");
            throw new IOException("MediaWiki API error: " + errorMsg);
        }

        JsonNode text = root.path("parse").path("text");
        if (text.isTextual()) return text.asText();
        return text.path("*").asText("");
    }

    private String buildApiUrl(WikiSiteDescriptor site, String params) {
        String base = site.apiUrl();
        // Ensure base ends with api.php
        if (base.endsWith("/")) {
            base += "api.php";
        } else if (!base.endsWith("api.php")) {
            base += "/api.php";
        }
        return base + "?" + params;
    }

    private String httpGet(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "MainframeMate/1.0 WikiIntegration");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new IOException("HTTP " + status + " from " + urlStr);
        }

        InputStream is = conn.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder(4096);
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();
        return sb.toString();
    }

    private WikiSiteDescriptor findSite(WikiSiteId id) {
        for (WikiSiteDescriptor s : sites) {
            if (s.id().equals(id)) return s;
        }
        throw new IllegalArgumentException("Unknown wiki site: " + id);
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }
}
