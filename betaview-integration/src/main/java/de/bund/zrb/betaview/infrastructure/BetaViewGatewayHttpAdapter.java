package de.bund.zrb.betaview.infrastructure;

import de.bund.zrb.betaview.domain.*;
import de.bund.zrb.betaview.port.BetaViewGateway;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapter that communicates with a BetaView web application via HTTP.
 * Encapsulates login, search, link navigation and document loading.
 * <p>
 * This is a self-contained HTTP client so that betaview-integration has no
 * compile dependency on the external betaview project.
 */
public class BetaViewGatewayHttpAdapter implements BetaViewGateway {

    private static final int MAX_REDIRECT_HOPS = 10;

    private URL baseUrl;
    private CookieManager cookieManager;
    private String csrfField;
    private String csrfName;
    private String csrfValue;
    private boolean connected;

    // ── BetaViewGateway implementation ──────────────────────────────────

    @Override
    public BetaViewSearchResult search(BetaViewSearchQuery query) throws Exception {
        connect(query);

        String selectHtml = getText("select.action?favoriteID=" + query.favoriteId());
        Map<String, String> hidden = extractHiddenInputs(selectHtml);

        Map<String, String> form = new LinkedHashMap<String, String>();
        addStrutsToken(form, hidden);

        form.put("getDateMask()", "DD.MM.YYYY");
        form.put("favID", query.favoriteId());
        form.put("locale", query.locale());
        form.put("focus", "we_id_EXTENSION");

        form.put("lastdate", "last");
        form.put("timesel", "sub");
        form.put("lastsel", "last7days");
        form.put("lasthoursdaysvalue", Integer.toString(query.daysBack()));
        form.put("timeunit", "days");

        form.put("datefrom", "");
        form.put("timefrom", "");
        form.put("dateto", "");
        form.put("timeto", "");

        form.put("FOLDER", "*");
        form.put("TAB", "*");
        form.put("TITLE", "");
        form.put("FTITLE", "0");
        form.put("RECI", "*");
        form.put("FORM", query.form());
        form.put("EXTENSION", query.extensionPattern());

        form.put("REPORT", "*");
        form.put("JOBNAME", "*");
        form.put("ONLINE", "");
        form.put("LGRNOTE", "");
        form.put("LGRXREAD", "");
        form.put("ARCHIVE", "");
        form.put("PROCESS", "");
        form.put("DELETE", "");
        form.put("LGRSTAT", "");
        form.put("RELOAD", "");

        postFormText("result.action", form);
        String rawHtml = getText("showResult.action");

        List<BetaViewDocumentRef> docs = parseDocumentRefs(rawHtml);
        return new BetaViewSearchResult(rawHtml, docs);
    }

    @Override
    public BetaViewDocument loadDocument(BetaViewDocumentRef ref) throws Exception {
        ensureConnected();
        String html = getText(ref.actionPath());
        return new BetaViewDocument(ref, html, true);
    }

    @Override
    public String navigateLink(String relativePath) throws Exception {
        ensureConnected();
        return getText(relativePath);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    // ── Connection ──────────────────────────────────────────────────────

    private void connect(BetaViewSearchQuery query) throws Exception {
        baseUrl = normalizeUrl(query.baseUrl());
        cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        connected = false;

        // Login
        String loginPageHtml = getText("login.action");
        Map<String, String> hidden = extractHiddenInputs(loginPageHtml);

        Map<String, String> form = new LinkedHashMap<String, String>();
        addStrutsToken(form, hidden);
        copyIfPresent(hidden, form, "newpasswordmode");
        copyIfPresent(hidden, form, "ssID");
        copyIfPresent(hidden, form, "favoriteID");
        copyIfPresent(hidden, form, "systemID");

        form.put("userName", query.user());
        form.put("password", query.password());

        postFormText("login.action", form);

        // Get CSRF token
        String appCtx = getText("getAppContext.action");
        parseCsrfFromAppContext(appCtx);

        connected = true;
    }

    private void ensureConnected() {
        if (!connected) {
            throw new IllegalStateException("Not connected to BetaView. Perform a search first.");
        }
    }

    // ── HTTP primitives ─────────────────────────────────────────────────

    private String getText(String relativePath) throws IOException {
        byte[] bytes = getBytes(relativePath);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] getBytes(String relativePath) throws IOException {
        URL url = new URL(baseUrl, relativePath);
        HttpURLConnection conn = openConnection("GET", url);
        conn.setInstanceFollowRedirects(false);

        int status = conn.getResponseCode();
        storeCookies(conn);
        byte[] body = readBody(conn);
        String location = conn.getHeaderField("Location");
        conn.disconnect();

        // Follow redirects
        int hops = 0;
        while (isRedirect(status) && location != null && hops < MAX_REDIRECT_HOPS) {
            hops++;
            URL next = new URL(baseUrl, location);
            conn = openConnection("GET", next);
            conn.setInstanceFollowRedirects(false);
            status = conn.getResponseCode();
            storeCookies(conn);
            body = readBody(conn);
            location = conn.getHeaderField("Location");
            conn.disconnect();
        }

        if (status / 100 != 2) {
            throw new IOException("GET " + relativePath + " failed with HTTP " + status);
        }
        return body;
    }

    private String postFormText(String relativePath, Map<String, String> fields) throws IOException {
        Map<String, String> effective = new LinkedHashMap<String, String>(fields);
        if (csrfField != null && csrfName != null && csrfValue != null) {
            effective.put(csrfField, csrfName);
            effective.put(csrfName, csrfValue);
        }

        URL url = new URL(baseUrl, relativePath);
        HttpURLConnection conn = openConnection("POST", url);
        conn.setInstanceFollowRedirects(false);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

        String body = encodeForm(effective);
        OutputStream out = conn.getOutputStream();
        try {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }

        int status = conn.getResponseCode();
        storeCookies(conn);
        byte[] responseBody = readBody(conn);
        String location = conn.getHeaderField("Location");
        conn.disconnect();

        // Follow redirects
        int hops = 0;
        while (isRedirect(status) && location != null && hops < MAX_REDIRECT_HOPS) {
            hops++;
            URL next = new URL(baseUrl, location);
            conn = openConnection("GET", next);
            conn.setInstanceFollowRedirects(false);
            status = conn.getResponseCode();
            storeCookies(conn);
            responseBody = readBody(conn);
            location = conn.getHeaderField("Location");
            conn.disconnect();
        }

        if (status / 100 != 2) {
            throw new IOException("POST " + relativePath + " failed with HTTP " + status);
        }
        return new String(responseBody, StandardCharsets.UTF_8);
    }

    private HttpURLConnection openConnection(String method, URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        if (cookieManager != null) {
            String cookieHeader = buildCookieHeader();
            if (!cookieHeader.isEmpty()) {
                conn.setRequestProperty("Cookie", cookieHeader);
            }
        }
        return conn;
    }

    private void storeCookies(HttpURLConnection conn) {
        if (cookieManager == null) return;
        try {
            URI uri = conn.getURL().toURI();
            cookieManager.put(uri, conn.getHeaderFields());
        } catch (Exception ignored) {
        }
    }

    private String buildCookieHeader() {
        if (cookieManager == null) return "";
        try {
            Map<String, List<String>> cookies = cookieManager.get(baseUrl.toURI(), new LinkedHashMap<String, List<String>>());
            List<String> values = cookies.get("Cookie");
            if (values == null || values.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (String v : values) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(v);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private byte[] readBody(HttpURLConnection conn) throws IOException {
        InputStream in;
        try {
            in = conn.getInputStream();
        } catch (IOException e) {
            in = conn.getErrorStream();
        }
        if (in == null) return new byte[0];

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            byte[] data = new byte[8192];
            int n;
            while ((n = in.read(data)) >= 0) {
                buffer.write(data, 0, n);
            }
        } finally {
            in.close();
        }
        return buffer.toByteArray();
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    // ── HTML / Form helpers ─────────────────────────────────────────────

    private Map<String, String> extractHiddenInputs(String html) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        Pattern p = Pattern.compile("<input[^>]*type=[\"']hidden[\"'][^>]*/?>", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(html);
        while (m.find()) {
            String tag = m.group();
            String name = extractAttr(tag, "name");
            String value = extractAttr(tag, "value");
            if (name != null) {
                map.put(name, value != null ? value : "");
            }
        }
        return map;
    }

    private String extractAttr(String tag, String attr) {
        Pattern p = Pattern.compile(attr + "=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    private void addStrutsToken(Map<String, String> form, Map<String, String> hidden) {
        String tokenNameField = hidden.get("struts.token.name");
        if (tokenNameField != null) {
            form.put("struts.token.name", tokenNameField);
            String tokenValue = hidden.get(tokenNameField);
            if (tokenValue != null) {
                form.put(tokenNameField, tokenValue);
            }
        }
    }

    private String encodeForm(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(urlEncode(entry.getKey()));
            sb.append('=');
            sb.append(urlEncode(entry.getValue()));
        }
        return sb.toString();
    }

    private String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private void parseCsrfFromAppContext(String json) {
        // Simple extraction: look for csrfToken fields in JSON
        csrfField = extractJsonValue(json, "csrfField");
        csrfName = extractJsonValue(json, "csrfName");
        csrfValue = extractJsonValue(json, "csrfValue");
        if (csrfField == null) {
            csrfField = extractJsonValue(json, "field");
        }
        if (csrfName == null) {
            csrfName = extractJsonValue(json, "name");
        }
        if (csrfValue == null) {
            csrfValue = extractJsonValue(json, "value");
        }
    }

    private String extractJsonValue(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static void copyIfPresent(Map<String, String> source, Map<String, String> target, String key) {
        String value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    private URL normalizeUrl(String text) throws Exception {
        String s = text.trim();
        if (!s.endsWith("/")) {
            s = s + "/";
        }
        return new URL(s);
    }

    // ── Result parsing ──────────────────────────────────────────────────

    private List<BetaViewDocumentRef> parseDocumentRefs(String html) {
        List<BetaViewDocumentRef> refs = new ArrayList<BetaViewDocumentRef>();
        try {
            Document doc = Jsoup.parse(html);
            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String href = link.attr("href");
                String text = link.text().trim();
                if (href.contains("show") || href.contains("view") || href.contains("display")
                        || href.contains("docId") || href.contains("documentID")) {
                    if (!text.isEmpty()) {
                        refs.add(new BetaViewDocumentRef(text, href, ""));
                    }
                }
            }
        } catch (Exception e) {
            // If parsing fails, return empty list
        }
        return refs;
    }
}

