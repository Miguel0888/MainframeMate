package de.bund.zrb.betaview.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP-based BetaViewClient implementation.
 * Handles login, cookie management, CSRF tokens and redirect following.
 * Adapted from the betaview-example architecture.
 */
public final class BetaViewHttpClient implements BetaViewClient {

    private static final int MAX_REDIRECT_HOPS = 10;

    private final BetaViewBaseUrl baseUrl;

    public BetaViewHttpClient(BetaViewBaseUrl baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
    }

    @Override
    public BetaViewSession login(BetaViewCredentials credentials) throws IOException {
        Objects.requireNonNull(credentials, "credentials must not be null");

        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        BetaViewSession session = new BetaViewSession(baseUrl, cookieManager);

        // Fetch login page (follows redirects while collecting cookies)
        String loginPageHtml = getText(session, "login.action");

        Map<String, String> hidden = HiddenInputExtractor.extractHiddenInputs(loginPageHtml);

        Map<String, String> form = new LinkedHashMap<String, String>();

        // Keep Struts token fields
        copyIfPresent(hidden, form, "struts.token.name");
        String tokenName = hidden.get("struts.token.name");
        if (tokenName != null) {
            copyIfPresent(hidden, form, tokenName);
        }

        // Keep additional hidden fields
        copyIfPresent(hidden, form, "newpasswordmode");
        copyIfPresent(hidden, form, "ssID");
        copyIfPresent(hidden, form, "favoriteID");
        copyIfPresent(hidden, form, "systemID");

        // Add credentials
        form.put("userName", credentials.username());
        form.put("password", credentials.password());

        HttpResponse loginResponse = postForm(session, "login.action", form);
        HttpResponse finalResponse = followRedirects(session, loginResponse, MAX_REDIRECT_HOPS);

        if (finalResponse.statusCode() / 100 != 2) {
            throw new IOException("Login failed with HTTP " + finalResponse.statusCode()
                    + " (last redirect location: " + safe(finalResponse.location()) + ")");
        }

        // Fetch app context to get CSRF token for subsequent POST requests.
        // Some BetaView deployments may not support getAppContext.action,
        // so we tolerate failure here and continue without CSRF token.
        try {
            String appContextJson = getText(session, "getAppContext.action");
            BetaViewCsrfToken csrf = new AppContextCsrfTokenParser().parseFrom(appContextJson);
            if (csrf != null) {
                session = session.withCsrfToken(csrf);
                System.out.println("[BetaView] CSRF token acquired: field=" + csrf.field() + " name=" + csrf.name());
            } else {
                System.out.println("[BetaView] No CSRF token found in appContext response.");
            }
        } catch (IOException csrfEx) {
            System.out.println("[BetaView] Could not fetch CSRF token (non-fatal): " + csrfEx.getMessage());
        }
        return session;
    }

    @Override
    public String getText(BetaViewSession session, String relativePath) throws IOException {
        byte[] bytes = getBinary(session, relativePath);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public byte[] getBinary(BetaViewSession session, String relativePath) throws IOException {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(relativePath, "relativePath must not be null");

        URL url = session.baseUrl().resolve(relativePath);
        HttpResponse response = get(session, url);
        HttpResponse finalResponse = followRedirects(session, response, MAX_REDIRECT_HOPS);

        if (finalResponse.statusCode() / 100 != 2) {
            throw new IOException("GET failed with HTTP " + finalResponse.statusCode()
                    + " for " + relativePath
                    + " (last redirect location: " + safe(finalResponse.location()) + ")");
        }

        return finalResponse.body();
    }

    @Override
    public String postFormText(BetaViewSession session, String relativePath, Map<String, String> fields) throws IOException {
        HttpResponse response = postForm(session, relativePath, fields);
        HttpResponse finalResponse = followRedirects(session, response, MAX_REDIRECT_HOPS);

        if (finalResponse.statusCode() / 100 != 2) {
            throw new IOException("POST failed with HTTP " + finalResponse.statusCode()
                    + " for " + relativePath
                    + " (last redirect location: " + safe(finalResponse.location()) + ")");
        }

        return new String(finalResponse.body(), StandardCharsets.UTF_8);
    }

    // ── Internal HTTP methods ───────────────────────────────────────────

    private HttpResponse get(BetaViewSession session, URL url) throws IOException {
        HttpURLConnection conn = open(session, "GET", url);
        conn.setInstanceFollowRedirects(false);

        int status = conn.getResponseCode();
        storeCookies(session, conn);

        byte[] body = readResponseBody(conn);
        String location = conn.getHeaderField("Location");
        conn.disconnect();

        return new HttpResponse(status, body, location);
    }

    private HttpResponse postForm(BetaViewSession session, String relativePath, Map<String, String> fields) throws IOException {
        Objects.requireNonNull(fields, "fields must not be null");

        Map<String, String> effectiveFields = new LinkedHashMap<String, String>(fields);

        // Add CSRF token automatically ONLY when the form does not already carry
        // its own Struts token (page-specific tokens must not be overwritten by
        // the session-level CSRF token – doing so causes HTTP 400).
        BetaViewCsrfToken csrf = session.csrfToken();
        if (csrf != null && !effectiveFields.containsKey("struts.token.name")) {
            effectiveFields.put(csrf.field(), csrf.name());
            effectiveFields.put(csrf.name(), csrf.value());
        }

        URL url = session.baseUrl().resolve(relativePath);
        HttpURLConnection conn = open(session, "POST", url);
        conn.setInstanceFollowRedirects(false);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

        String body = FormUrlEncodedBody.encode(effectiveFields);
        java.io.OutputStream out = conn.getOutputStream();
        try {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }

        int status = conn.getResponseCode();
        storeCookies(session, conn);

        byte[] responseBody = readResponseBody(conn);
        String location = conn.getHeaderField("Location");
        conn.disconnect();

        return new HttpResponse(status, responseBody, location);
    }

    private HttpResponse followRedirects(BetaViewSession session, HttpResponse response, int maxHops) throws IOException {
        HttpResponse current = response;
        int hops = 0;

        while (isRedirect(current.statusCode()) && current.location() != null && hops < maxHops) {
            hops++;
            URL nextUrl = session.baseUrl().resolve(current.location());
            current = get(session, nextUrl);
        }

        return current;
    }

    private HttpURLConnection open(BetaViewSession session, String method, URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);

        String cookieHeader = CookieHeaderBuilder.build(session.cookieManager());
        if (!cookieHeader.isEmpty()) {
            conn.setRequestProperty("Cookie", cookieHeader);
        }

        return conn;
    }

    private void storeCookies(BetaViewSession session, HttpURLConnection conn) throws IOException {
        try {
            URI uri = conn.getURL().toURI();
            session.cookieManager().put(uri, conn.getHeaderFields());
        } catch (Exception e) {
            throw new IOException("Store cookies failed", e);
        }
    }

    private byte[] readResponseBody(HttpURLConnection conn) throws IOException {
        InputStream in;
        try {
            in = conn.getInputStream();
        } catch (IOException e) {
            in = conn.getErrorStream();
        }
        if (in == null) {
            return new byte[0];
        }

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int n;
            while ((n = in.read(data)) >= 0) {
                buffer.write(data, 0, n);
            }
            return buffer.toByteArray();
        } finally {
            in.close();
        }
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static void copyIfPresent(Map<String, String> source, Map<String, String> target, String key) {
        String value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}

