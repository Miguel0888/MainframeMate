package de.bund.zrb.wiki.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.bund.zrb.wiki.domain.*;
import de.bund.zrb.wiki.port.WikiContentService;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WikiContentService implementation using plain HTTP + MediaWiki Action API.
 * Performs login directly via the MediaWiki API and manages cookies with
 * {@link CookieManager} so that every subsequent request carries the session.
 */
public class JwbfWikiContentService implements WikiContentService {

    private static final Logger LOG = Logger.getLogger(JwbfWikiContentService.class.getName());

    private final List<WikiSiteDescriptor> sites;
    private final ObjectMapper mapper;
    private final HtmlPostProcessor htmlProcessor;

    /** One CookieManager per site-id, keeps login sessions alive. */
    private final Map<String, CookieManager> cookieManagers = new HashMap<String, CookieManager>();
    /** Track which site+user combos are already logged in. */
    private final Set<String> loggedIn = new HashSet<String>();

    public JwbfWikiContentService(List<WikiSiteDescriptor> sites) {
        this.sites = new ArrayList<WikiSiteDescriptor>(Objects.requireNonNull(sites));
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
        ensureLoggedIn(site, credentials);

        String apiUrl = buildApiUrl(site, "action=parse&format=json&formatversion=2&disabletoc=1&prop=text&page="
                + enc(pageTitle));

        String json = httpGet(site, apiUrl);
        String rawHtml = extractHtml(json);
        HtmlPostProcessor.Result result = htmlProcessor.cleanAndBuildOutline(rawHtml);

        return new WikiPageView(pageTitle, result.cleanedHtml, result.outlineRoot, result.images);
    }

    @Override
    public List<String> loadOutgoingLinks(WikiSiteId siteId, String pageTitle, WikiCredentials credentials) throws IOException {
        WikiSiteDescriptor site = findSite(siteId);
        ensureLoggedIn(site, credentials);

        List<String> all = new ArrayList<String>();
        String plcontinue = null;

        for (int i = 0; i < 50; i++) {
            StringBuilder params = new StringBuilder();
            params.append("action=query&format=json&formatversion=2&prop=links&pllimit=max&titles=");
            params.append(enc(pageTitle));
            if (plcontinue != null) {
                params.append("&plcontinue=").append(enc(plcontinue));
            }

            String json = httpGet(site, buildApiUrl(site, params.toString()));
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
        ensureLoggedIn(site, credentials);

        String apiUrl = buildApiUrl(site,
                "action=query&format=json&formatversion=2&list=search&srlimit="
                        + Math.min(limit, 50)
                        + "&srsearch=" + enc(query));

        String json = httpGet(site, apiUrl);
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
    //  Login via MediaWiki Action API
    // ═══════════════════════════════════════════════════════════

    private void ensureLoggedIn(WikiSiteDescriptor site, WikiCredentials credentials) throws IOException {
        if (!site.requiresLogin()) return;
        if (credentials == null || credentials.isAnonymous()) return;

        String key = site.id().value() + "|" + credentials.username();
        if (loggedIn.contains(key)) return;

        String apiBase = buildApiUrl(site, "");

        // Step 1: get login token
        String tokenUrl = apiBase + "action=query&meta=tokens&type=login&format=json";
        String tokenJson = httpGet(site, tokenUrl);
        JsonNode tokenRoot = mapper.readTree(tokenJson);
        String loginToken = tokenRoot.path("query").path("tokens").path("logintoken").asText();

        if (loginToken.isEmpty()) {
            throw new IOException("Could not obtain login token from " + site.displayName());
        }

        // Step 2: POST login with token + credentials
        String postBody = "action=login&format=json"
                + "&lgname=" + enc(credentials.username())
                + "&lgpassword=" + enc(new String(credentials.password()))
                + "&lgtoken=" + enc(loginToken);

        String loginJson = httpPost(site, apiBase, postBody);
        JsonNode loginRoot = mapper.readTree(loginJson);
        String result = loginRoot.path("login").path("result").asText();

        if ("Success".equalsIgnoreCase(result)) {
            loggedIn.add(key);
            LOG.info("[Wiki] Logged in to " + site.displayName() + " as " + credentials.username());
        } else {
            String reason = loginRoot.path("login").path("reason").asText(result);
            throw new IOException("Wiki login failed for " + site.displayName() + ": " + reason);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  HTTP with Cookie management
    // ═══════════════════════════════════════════════════════════

    private CookieManager getCookieManager(WikiSiteDescriptor site) {
        String siteKey = site.id().value();
        CookieManager cm = cookieManagers.get(siteKey);
        if (cm == null) {
            cm = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            cookieManagers.put(siteKey, cm);
        }
        return cm;
    }

    private HttpURLConnection openConnection(WikiSiteDescriptor site, String urlStr, String method) throws IOException {
        CookieManager cm = getCookieManager(site);
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("User-Agent", "MainframeMate/1.0 WikiIntegration");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        // Attach cookies from previous requests
        try {
            Map<String, List<String>> cookieHeaders = cm.get(url.toURI(), Collections.<String, List<String>>emptyMap());
            for (Map.Entry<String, List<String>> entry : cookieHeaders.entrySet()) {
                for (String value : entry.getValue()) {
                    conn.addRequestProperty(entry.getKey(), value);
                }
            }
        } catch (URISyntaxException e) {
            LOG.log(Level.FINE, "[Wiki] Could not attach cookies", e);
        }

        return conn;
    }

    private void storeCookies(WikiSiteDescriptor site, HttpURLConnection conn) {
        CookieManager cm = getCookieManager(site);
        try {
            cm.put(conn.getURL().toURI(), conn.getHeaderFields());
        } catch (Exception e) {
            LOG.log(Level.FINE, "[Wiki] Could not store cookies", e);
        }
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        int status = conn.getResponseCode();
        InputStream is = (status >= 400) ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) {
            throw new IOException("HTTP " + status + " (no body)");
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder(4096);
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();

        if (status != 200) {
            throw new IOException("HTTP " + status + ": " + sb.toString());
        }
        return sb.toString();
    }

    private String httpGet(WikiSiteDescriptor site, String urlStr) throws IOException {
        HttpURLConnection conn = openConnection(site, urlStr, "GET");
        conn.connect();
        storeCookies(site, conn);
        return readResponse(conn);
    }

    private String httpPost(WikiSiteDescriptor site, String urlStr, String body) throws IOException {
        HttpURLConnection conn = openConnection(site, urlStr, "POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

        OutputStream os = conn.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.flush();
        os.close();

        storeCookies(site, conn);
        return readResponse(conn);
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    private String extractHtml(String json) throws IOException {
        JsonNode root = mapper.readTree(json);

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
        if (base.endsWith("/")) {
            base += "api.php";
        } else if (!base.endsWith("api.php")) {
            base += "/api.php";
        }
        if (params == null || params.isEmpty()) return base + "?";
        return base + "?" + params;
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
