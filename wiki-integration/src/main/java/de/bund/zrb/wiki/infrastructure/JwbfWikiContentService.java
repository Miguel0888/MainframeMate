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

        String json = httpRequest(site, apiUrl);
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

            String json = httpRequest(site, buildApiUrl(site, params.toString()));
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

        String json = httpRequest(site, apiUrl);
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
        if (!site.requiresLogin()) {
            LOG.info("[Wiki] Site " + site.displayName() + " does not require login, skipping.");
            return;
        }
        if (credentials == null || credentials.isAnonymous()) {
            LOG.warning("[Wiki] Site " + site.displayName() + " requires login but credentials are "
                    + (credentials == null ? "null" : "anonymous") + " – proceeding without login!");
            return;
        }

        String key = site.id().value() + "|" + credentials.username();
        if (loggedIn.contains(key)) {
            LOG.fine("[Wiki] Already logged in as " + credentials.username() + " on " + site.displayName());
            return;
        }

        LOG.info("[Wiki] === LOGIN START === " + site.displayName() + " as '" + credentials.username() + "'");
        LOG.info("[Wiki] API base URL: " + site.apiUrl());

        // Log proxy info for diagnostics
        try {
            java.net.URI uri = new java.net.URI(site.apiUrl());
            java.util.List<java.net.Proxy> proxies = java.net.ProxySelector.getDefault().select(uri);
            LOG.info("[Wiki] Proxy for " + uri.getHost() + ": " + proxies);
        } catch (Exception pe) {
            LOG.fine("[Wiki] Could not determine proxy: " + pe.getMessage());
        }

        String apiBase = buildApiUrl(site, "");

        // Step 1: get a login token
        String tokenUrl = apiBase + "action=query&meta=tokens&type=login&format=json";
        LOG.info("[Wiki] Step 1 – Requesting login token: " + tokenUrl);
        String tokenJson = httpRequest(site, tokenUrl, null);
        LOG.info("[Wiki] Step 1 – Token response: " + truncate(tokenJson, 500));
        JsonNode tokenRoot = mapper.readTree(tokenJson);
        String loginToken = tokenRoot.path("query").path("tokens").path("logintoken").asText();
        LOG.info("[Wiki] Step 1 – Login token: " + (loginToken.isEmpty() ? "(EMPTY!)" : loginToken.substring(0, Math.min(8, loginToken.length())) + "…"));

        if (loginToken.isEmpty()) {
            throw new IOException("Could not obtain login token from " + site.displayName()
                    + " – response was: " + truncate(tokenJson, 200));
        }

        // Step 2: try action=clientlogin (modern MediaWiki ≥ 1.27)
        String postBody = "action=clientlogin&format=json"
                + "&loginreturnurl=" + enc(site.apiUrl())
                + "&username=" + enc(credentials.username())
                + "&password=" + enc(new String(credentials.password()))
                + "&logintoken=" + enc(loginToken);

        LOG.info("[Wiki] Step 2 – Trying clientlogin for user '" + credentials.username() + "'");
        logCookies(site);
        String loginJson = httpRequest(site, apiBase, postBody);
        LOG.info("[Wiki] Step 2 – clientlogin response: " + truncate(loginJson, 500));
        JsonNode loginRoot = mapper.readTree(loginJson);

        String status = loginRoot.path("clientlogin").path("status").asText();
        String message = loginRoot.path("clientlogin").path("message").asText("");
        LOG.info("[Wiki] Step 2 – clientlogin status='" + status + "' message='" + message + "'");

        if ("PASS".equalsIgnoreCase(status)) {
            loggedIn.add(key);
            LOG.info("[Wiki] === LOGIN SUCCESS (clientlogin) === " + site.displayName());
            logCookies(site);
            return;
        }

        // Fallback: try legacy action=login (older MediaWiki / bot passwords)
        // IMPORTANT: Reuse the SAME token from Step 1 – do NOT fetch a new token,
        // because that would start a new session and invalidate the existing cookies.
        LOG.info("[Wiki] Step 3 – clientlogin failed (status='" + status + "'), trying legacy action=login with same token");

        postBody = "action=login&format=json"
                + "&lgname=" + enc(credentials.username())
                + "&lgpassword=" + enc(new String(credentials.password()))
                + "&lgtoken=" + enc(loginToken);

        LOG.info("[Wiki] Step 3 – Sending legacy login (reusing token from Step 1)");
        logCookies(site);
        loginJson = httpRequest(site, apiBase, postBody);
        LOG.info("[Wiki] Step 3 – legacy login response: " + truncate(loginJson, 500));
        loginRoot = mapper.readTree(loginJson);
        String result = loginRoot.path("login").path("result").asText();

        if ("Success".equalsIgnoreCase(result)) {
            loggedIn.add(key);
            LOG.info("[Wiki] === LOGIN SUCCESS (legacy) === " + site.displayName());
            logCookies(site);
            return;
        }

        // Legacy login sometimes requires a two-step dance: first call returns "NeedToken"
        // with a dedicated lgtoken, then a second call uses that token.
        if ("NeedToken".equalsIgnoreCase(result)) {
            String lgToken = loginRoot.path("login").path("token").asText();
            LOG.info("[Wiki] Step 3b – NeedToken received, retrying with lgtoken=" + truncate(lgToken, 20));

            postBody = "action=login&format=json"
                    + "&lgname=" + enc(credentials.username())
                    + "&lgpassword=" + enc(new String(credentials.password()))
                    + "&lgtoken=" + enc(lgToken);

            loginJson = httpRequest(site, apiBase, postBody);
            LOG.info("[Wiki] Step 3b – second legacy login response: " + truncate(loginJson, 500));
            loginRoot = mapper.readTree(loginJson);
            result = loginRoot.path("login").path("result").asText();

            if ("Success".equalsIgnoreCase(result)) {
                loggedIn.add(key);
                LOG.info("[Wiki] === LOGIN SUCCESS (legacy 2-step) === " + site.displayName());
                logCookies(site);
                return;
            }
        }

        String reason = loginRoot.path("login").path("reason").asText(result);
        LOG.warning("[Wiki] === LOGIN FAILED === " + site.displayName() + ": " + reason);
        logCookies(site);
        throw new IOException("Wiki-Login fehlgeschlagen für " + site.displayName() + ": " + reason);
    }

    private void logCookies(WikiSiteDescriptor site) {
        CookieManager cm = getCookieManager(site);
        CookieStore store = cm.getCookieStore();
        List<HttpCookie> cookies = store.getCookies();
        LOG.info("[Wiki] Cookie count for " + site.displayName() + ": " + cookies.size());
        for (HttpCookie c : cookies) {
            LOG.info("[Wiki]   Cookie: " + c.getName() + "=" + truncate(c.getValue(), 30)
                    + " (domain=" + c.getDomain() + ", path=" + c.getPath() + ")");
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "(null)";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "…[" + s.length() + " chars]";
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
                    LOG.fine("[Wiki] → Cookie header: " + entry.getKey() + ": " + value);
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


    /**
     * Convenience GET request.
     */
    private String httpRequest(WikiSiteDescriptor site, String urlStr) throws IOException {
        return httpRequest(site, urlStr, null);
    }

    /**
     * Perform an HTTP request (GET if postBody is null, POST otherwise).
     * Cookies are stored AFTER the response is fully read.
     */
    private String httpRequest(WikiSiteDescriptor site, String urlStr, String postBody) throws IOException {
        boolean isPost = (postBody != null);
        LOG.fine("[Wiki] HTTP " + (isPost ? "POST" : "GET") + " " + truncate(urlStr, 200));

        HttpURLConnection conn;
        try {
            conn = openConnection(site, urlStr, isPost ? "POST" : "GET");
        } catch (java.net.ConnectException e) {
            throw new IOException("Verbindung zu " + site.displayName() + " fehlgeschlagen: " + e.getMessage()
                    + "\nPrüfen Sie die API-URL und Ihre Proxy-Einstellungen.", e);
        } catch (java.net.UnknownHostException e) {
            throw new IOException("Host nicht gefunden: " + e.getMessage()
                    + "\nPrüfen Sie die API-URL und Ihre Netzwerk-/Proxy-Konfiguration.", e);
        }

        try {
            if (isPost) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                OutputStream os = conn.getOutputStream();
                os.write(postBody.getBytes("UTF-8"));
                os.flush();
                os.close();
            }

            // Read response body
            int status = conn.getResponseCode();
            LOG.fine("[Wiki] HTTP response status: " + status);
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

            // Store cookies BEFORE disconnect (headers may be lost after disconnect)
            storeCookies(site, conn);
            conn.disconnect();

            if (status != 200) {
                throw new IOException("HTTP " + status + ": " + sb.toString());
            }
            return sb.toString();

        } catch (java.net.SocketTimeoutException e) {
            conn.disconnect();
            throw new IOException("Zeitüberschreitung bei Verbindung zu " + site.displayName()
                    + " (" + e.getMessage() + ")"
                    + "\nPrüfen Sie Ihre Proxy-Einstellungen und die API-URL.", e);
        } catch (java.net.ConnectException e) {
            conn.disconnect();
            throw new IOException("Verbindung abgelehnt: " + site.displayName()
                    + " (" + e.getMessage() + ")"
                    + "\nPrüfen Sie Ihre Proxy-/Firewall-Einstellungen.", e);
        }
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
