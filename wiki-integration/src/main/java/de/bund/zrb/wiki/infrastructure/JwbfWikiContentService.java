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
            return;
        }
        if (credentials == null || credentials.isAnonymous()) {
            LOG.warning("[Wiki] Site " + site.displayName() + " requires login but no credentials – proceeding without login!");
            return;
        }

        String key = site.id().value() + "|" + credentials.username();
        if (loggedIn.contains(key)) {
            return;
        }

        LOG.info("[Wiki] === LOGIN START === " + site.displayName() + " as '" + credentials.username() + "'");

        // Always start with a clean cookie jar
        cookieManagers.remove(site.id().value());

        String apiBase = buildApiUrl(site, "");

        // ── Step 1: Get login token ─────────────────────────────
        // POST, so that the session cookie established here is the same one used for login
        String tokenBody = "action=query&meta=tokens&type=login&format=json";
        LOG.info("[Wiki] Step 1 – POST " + apiBase + " body=" + tokenBody);
        String tokenJson = httpRequest(site, apiBase, tokenBody);
        LOG.info("[Wiki] Step 1 – response: " + truncate(tokenJson, 500));
        logCookies(site);

        JsonNode tokenRoot = mapper.readTree(tokenJson);
        String loginToken = tokenRoot.path("query").path("tokens").path("logintoken").asText();
        LOG.info("[Wiki] Step 1 – token raw value: '" + loginToken + "' (length=" + loginToken.length() + ")");
        LOG.info("[Wiki] Step 1 – token URL-encoded: '" + enc(loginToken) + "'");

        if (loginToken.isEmpty()) {
            throw new IOException("Kein Login-Token von " + site.displayName() + " erhalten");
        }

        // ── Step 2: action=login ────────────────────────────────
        String postBody = "action=login&format=json"
                + "&lgname=" + enc(credentials.username())
                + "&lgpassword=" + enc(new String(credentials.password()))
                + "&lgtoken=" + enc(loginToken);

        LOG.info("[Wiki] Step 2 – POST action=login lgname=" + enc(credentials.username())
                + " lgtoken=" + enc(loginToken) + " (password omitted)");

        String loginJson = httpRequest(site, apiBase, postBody);
        LOG.info("[Wiki] Step 2 – response: " + truncate(loginJson, 500));
        logCookies(site);

        JsonNode loginRoot = mapper.readTree(loginJson);
        String result = loginRoot.path("login").path("result").asText();
        LOG.info("[Wiki] Step 2 – result='" + result + "'");

        if ("Success".equalsIgnoreCase(result)) {
            loggedIn.add(key);
            LOG.info("[Wiki] === LOGIN SUCCESS === " + site.displayName());
            return;
        }

        // Some older wikis need a two-step dance: first call → NeedToken → second call with lgtoken
        if ("NeedToken".equalsIgnoreCase(result)) {
            String lgToken = loginRoot.path("login").path("token").asText();
            LOG.info("[Wiki] Step 2b – NeedToken, lgtoken='" + lgToken + "'");

            postBody = "action=login&format=json"
                    + "&lgname=" + enc(credentials.username())
                    + "&lgpassword=" + enc(new String(credentials.password()))
                    + "&lgtoken=" + enc(lgToken);

            loginJson = httpRequest(site, apiBase, postBody);
            LOG.info("[Wiki] Step 2b – response: " + truncate(loginJson, 500));

            loginRoot = mapper.readTree(loginJson);
            result = loginRoot.path("login").path("result").asText();

            if ("Success".equalsIgnoreCase(result)) {
                loggedIn.add(key);
                LOG.info("[Wiki] === LOGIN SUCCESS (2-step) === " + site.displayName());
                return;
            }
        }

        // If action=login also fails, try clientlogin as last resort (different token type)
        LOG.info("[Wiki] action=login failed (result='" + result + "'), trying clientlogin as fallback");

        // Reset session again for clientlogin attempt
        cookieManagers.remove(site.id().value());

        tokenJson = httpRequest(site, apiBase, tokenBody);
        tokenRoot = mapper.readTree(tokenJson);
        loginToken = tokenRoot.path("query").path("tokens").path("logintoken").asText();

        String returnUrl = site.apiUrl().endsWith("/") ? site.apiUrl() : site.apiUrl() + "/";
        postBody = "action=clientlogin&format=json"
                + "&loginreturnurl=" + enc(returnUrl)
                + "&username=" + enc(credentials.username())
                + "&password=" + enc(new String(credentials.password()))
                + "&logintoken=" + enc(loginToken);

        loginJson = httpRequest(site, apiBase, postBody);
        LOG.info("[Wiki] clientlogin response: " + truncate(loginJson, 500));

        loginRoot = mapper.readTree(loginJson);
        String status = loginRoot.path("clientlogin").path("status").asText();

        if ("PASS".equalsIgnoreCase(status)) {
            loggedIn.add(key);
            LOG.info("[Wiki] === LOGIN SUCCESS (clientlogin) === " + site.displayName());
            return;
        }

        // Everything failed
        String reason = loginRoot.path("login").path("reason").asText(
                loginRoot.path("clientlogin").path("message").asText(
                        loginRoot.path("error").path("info").asText("Unbekannter Fehler")));

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
    //  HTTP with Cookie management + manual redirect handling
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
        conn.setInstanceFollowRedirects(false);   // handle redirects manually to preserve cookies
        conn.setRequestProperty("User-Agent", "MainframeMate/1.0 WikiIntegration");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setUseCaches(false);

        // Attach cookies from previous requests
        try {
            Map<String, List<String>> cookieHeaders = cm.get(url.toURI(), Collections.<String, List<String>>emptyMap());
            boolean anyCookieSent = false;
            for (Map.Entry<String, List<String>> entry : cookieHeaders.entrySet()) {
                for (String value : entry.getValue()) {
                    conn.addRequestProperty(entry.getKey(), value);
                    LOG.info("[Wiki] → Sending cookie: " + entry.getKey() + ": " + value);
                    anyCookieSent = true;
                }
            }
            if (!anyCookieSent) {
                LOG.info("[Wiki] → NO cookies sent for " + url);
            }
        } catch (URISyntaxException e) {
            LOG.log(Level.WARNING, "[Wiki] Could not attach cookies for " + urlStr, e);
        }

        return conn;
    }

    private void storeCookies(WikiSiteDescriptor site, HttpURLConnection conn) {
        CookieManager cm = getCookieManager(site);
        try {
            // Log raw Set-Cookie headers
            Map<String, List<String>> headers = conn.getHeaderFields();
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Set-Cookie")) {
                    for (String v : entry.getValue()) {
                        LOG.info("[Wiki] ← Set-Cookie: " + truncate(v, 120));
                    }
                }
            }
            cm.put(conn.getURL().toURI(), headers);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Wiki] Could not store cookies", e);
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
     * Handles redirects manually so cookies are preserved across hops.
     */
    private String httpRequest(WikiSiteDescriptor site, String urlStr, String postBody) throws IOException {
        String currentUrl = urlStr;
        String currentMethod = (postBody == null) ? "GET" : "POST";
        String currentBody = postBody;

        for (int redirectCount = 0; redirectCount < 5; redirectCount++) {
            LOG.fine("[Wiki] HTTP " + currentMethod + " " + truncate(currentUrl, 200));

            HttpURLConnection conn;
            try {
                conn = openConnection(site, currentUrl, currentMethod);
            } catch (ConnectException e) {
                throw new IOException("Verbindung zu " + site.displayName() + " fehlgeschlagen: " + e.getMessage()
                        + "\nPrüfen Sie die API-URL und Ihre Proxy-Einstellungen.", e);
            } catch (UnknownHostException e) {
                throw new IOException("Host nicht gefunden: " + e.getMessage()
                        + "\nPrüfen Sie die API-URL und Ihre Netzwerk-/Proxy-Konfiguration.", e);
            }

            try {
                if ("POST".equals(currentMethod) && currentBody != null) {
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                    OutputStream os = conn.getOutputStream();
                    os.write(currentBody.getBytes("UTF-8"));
                    os.flush();
                    os.close();
                }

                int status = conn.getResponseCode();
                String location = conn.getHeaderField("Location");

                // Store cookies BEFORE reading body or following redirect
                storeCookies(site, conn);

                String responseBody = readResponseBody(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
                conn.disconnect();

                LOG.fine("[Wiki] HTTP " + status
                        + (location != null ? " Location=" + location : "")
                        + " body=" + truncate(responseBody, 120));

                // Handle redirects manually
                if (isRedirect(status) && location != null && !location.isEmpty()) {
                    currentUrl = resolveRedirectUrl(currentUrl, location);
                    // 303 See Other → switch to GET
                    if (status == HttpURLConnection.HTTP_SEE_OTHER) {
                        currentMethod = "GET";
                        currentBody = null;
                    }
                    continue;
                }

                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP " + status + ": " + responseBody);
                }
                return responseBody;

            } catch (SocketTimeoutException e) {
                conn.disconnect();
                throw new IOException("Zeitüberschreitung bei " + site.displayName()
                        + " (" + e.getMessage() + ")", e);
            } catch (ConnectException e) {
                conn.disconnect();
                throw new IOException("Verbindung abgelehnt: " + site.displayName()
                        + " (" + e.getMessage() + ")", e);
            }
        }
        throw new IOException("Zu viele Redirects bei Verbindung zu " + site.displayName());
    }

    private String readResponseBody(InputStream is) throws IOException {
        if (is == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder(4096);
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    private boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307 || status == 308;
    }

    private String resolveRedirectUrl(String currentUrl, String location) throws IOException {
        try {
            return new URL(new URL(currentUrl), location).toExternalForm();
        } catch (MalformedURLException e) {
            throw new IOException("Ungültige Redirect-URL: " + location, e);
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
