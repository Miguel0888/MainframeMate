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
            URI uri = new URI(site.apiUrl());
            List<Proxy> proxies = ProxySelector.getDefault().select(uri);
            LOG.info("[Wiki] Proxy for " + uri.getHost() + ": " + proxies);
        } catch (Exception pe) {
            LOG.fine("[Wiki] Could not determine proxy: " + pe.getMessage());
        }

        // ── Strategy 1: clientlogin (modern MediaWiki ≥ 1.27) ───────
        IOException clientLoginError = null;
        resetSession(site, key);
        try {
            attemptClientLogin(site, credentials);
            loggedIn.add(key);
            LOG.info("[Wiki] === LOGIN SUCCESS (clientlogin) === " + site.displayName());
            logCookies(site);
            return;
        } catch (IOException e) {
            clientLoginError = e;
            LOG.info("[Wiki] clientlogin failed: " + e.getMessage() + " – trying legacy login with fresh session");
        }

        // ── Strategy 2: legacy action=login (fresh session!) ────────
        resetSession(site, key);
        try {
            attemptLegacyLogin(site, credentials);
            loggedIn.add(key);
            LOG.info("[Wiki] === LOGIN SUCCESS (legacy) === " + site.displayName());
            logCookies(site);
            return;
        } catch (IOException legacyError) {
            LOG.warning("[Wiki] === LOGIN FAILED === " + site.displayName() + ": " + legacyError.getMessage());
            logCookies(site);
            if (clientLoginError != null) {
                legacyError.addSuppressed(clientLoginError);
            }
            throw new IOException(
                    "Wiki-Login fehlgeschlagen für " + site.displayName() + ": " + legacyError.getMessage(),
                    legacyError);
        }
    }

    /** Reset cookie jar and login state so the next login attempt starts with a clean session. */
    private void resetSession(WikiSiteDescriptor site, String loginKey) {
        cookieManagers.remove(site.id().value());
        loggedIn.remove(loginKey);
        LOG.fine("[Wiki] Session reset for " + site.displayName());
    }

    /** Request a fresh login token via POST (same session). */
    private String requestLoginToken(WikiSiteDescriptor site) throws IOException {
        String apiBase = buildApiUrl(site, "");

        // Use POST for the token request so that session cookies are established
        // consistently (some wikis behind reverse proxies behave differently for GET vs POST).
        String postBody = "action=query&meta=tokens&type=login&format=json";
        LOG.info("[Wiki] Requesting login token via POST: " + apiBase);

        String tokenJson = httpRequest(site, apiBase, postBody);
        LOG.info("[Wiki] Token response: " + truncate(tokenJson, 500));

        JsonNode tokenRoot = mapper.readTree(tokenJson);
        String loginToken = tokenRoot.path("query").path("tokens").path("logintoken").asText();

        LOG.info("[Wiki] Login token: "
                + (loginToken.isEmpty() ? "(EMPTY!)"
                : loginToken.substring(0, Math.min(8, loginToken.length())) + "…"));

        if (loginToken.isEmpty()) {
            throw new IOException("Could not obtain login token from " + site.displayName()
                    + " – response was: " + truncate(tokenJson, 200));
        }
        logCookies(site);
        return loginToken;
    }

    private void attemptClientLogin(WikiSiteDescriptor site, WikiCredentials credentials) throws IOException {
        String apiBase = buildApiUrl(site, "");
        String loginToken = requestLoginToken(site);

        String returnUrl = loginReturnUrl(site);
        String postBody = "action=clientlogin&format=json"
                + "&loginreturnurl=" + enc(returnUrl)
                + "&username=" + enc(credentials.username())
                + "&password=" + enc(new String(credentials.password()))
                + "&logintoken=" + enc(loginToken);

        LOG.info("[Wiki] Trying clientlogin for user '" + credentials.username() + "'");
        String loginJson = httpRequest(site, apiBase, postBody);
        LOG.info("[Wiki] clientlogin response: " + truncate(loginJson, 500));

        JsonNode loginRoot = mapper.readTree(loginJson);

        // Check for top-level API error (e.g. badtoken)
        if (loginRoot.has("error")) {
            String code = loginRoot.path("error").path("code").asText();
            String info = loginRoot.path("error").path("info").asText("Unknown error");
            throw new IOException("clientlogin error " + code + ": " + info);
        }

        String status = loginRoot.path("clientlogin").path("status").asText();
        String message = loginRoot.path("clientlogin").path("message").asText("");
        LOG.info("[Wiki] clientlogin status='" + status + "' message='" + message + "'");

        if ("PASS".equalsIgnoreCase(status)) {
            return; // success
        }
        if ("UI".equalsIgnoreCase(status) || "REDIRECT".equalsIgnoreCase(status)
                || "CONTINUE".equalsIgnoreCase(status) || "RESTART".equalsIgnoreCase(status)) {
            throw new IOException("clientlogin requires interactive continuation (" + status + ")"
                    + (message.isEmpty() ? "" : ": " + message));
        }
        throw new IOException("clientlogin " + status + (message.isEmpty() ? "" : ": " + message));
    }

    private void attemptLegacyLogin(WikiSiteDescriptor site, WikiCredentials credentials) throws IOException {
        String apiBase = buildApiUrl(site, "");
        String loginToken = requestLoginToken(site);

        String postBody = "action=login&format=json"
                + "&lgname=" + enc(credentials.username())
                + "&lgpassword=" + enc(new String(credentials.password()))
                + "&lgtoken=" + enc(loginToken);

        LOG.info("[Wiki] Sending legacy action=login");
        String loginJson = httpRequest(site, apiBase, postBody);
        LOG.info("[Wiki] legacy login response: " + truncate(loginJson, 500));

        JsonNode loginRoot = mapper.readTree(loginJson);
        String result = loginRoot.path("login").path("result").asText();

        if ("Success".equalsIgnoreCase(result)) {
            return;
        }

        // Older wikis: first call returns "NeedToken" with a dedicated lgtoken
        if ("NeedToken".equalsIgnoreCase(result)) {
            String lgToken = loginRoot.path("login").path("token").asText();
            LOG.info("[Wiki] NeedToken received, retrying with lgtoken=" + truncate(lgToken, 20));

            postBody = "action=login&format=json"
                    + "&lgname=" + enc(credentials.username())
                    + "&lgpassword=" + enc(new String(credentials.password()))
                    + "&lgtoken=" + enc(lgToken);

            loginJson = httpRequest(site, apiBase, postBody);
            LOG.info("[Wiki] second legacy login response: " + truncate(loginJson, 500));

            loginRoot = mapper.readTree(loginJson);
            result = loginRoot.path("login").path("result").asText();

            if ("Success".equalsIgnoreCase(result)) {
                return;
            }
        }

        String reason = loginRoot.path("login").path("reason").asText(result);
        throw new IOException(reason);
    }

    private String loginReturnUrl(WikiSiteDescriptor site) {
        String base = site.apiUrl();
        if (base.endsWith("api.php")) {
            int idx = base.lastIndexOf("/api.php");
            if (idx > 0) return base.substring(0, idx + 1);
        }
        return base.endsWith("/") ? base : base + "/";
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
