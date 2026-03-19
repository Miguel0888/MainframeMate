package de.bund.zrb.confluence;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.net.ProxyResolver;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509KeyManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST client for Confluence Data Center.
 * <p>
 * Authenticates via two layers:
 * <ol>
 *   <li>mTLS — client certificate from {@code Windows-MY}, forced to a specific alias
 *       via {@link AliasForcingX509KeyManager}</li>
 *   <li>HTTP Basic Auth header</li>
 * </ol>
 * <p>
 * Java 8 compatible — no {@code java.net.http.HttpClient}, no {@code var}.
 */
public final class ConfluenceRestClient {

    private static final Logger LOG = Logger.getLogger(ConfluenceRestClient.class.getName());
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final ConfluenceConnectionConfig config;
    private final SSLContext sslContext;
    private final String authorizationHeaderValue;
    private final Proxy proxy;

    public ConfluenceRestClient(ConfluenceConnectionConfig config) {
        this.config = config;
        this.sslContext = createSslContext(config.getClientCertificateAlias());
        this.authorizationHeaderValue = createBasicAuthHeader(
                config.getUsername(), config.getPassword());

        // Resolve proxy from central proxy settings
        Proxy resolved = Proxy.NO_PROXY;
        try {
            Settings settings = SettingsHelper.load();
            ProxyResolver.ProxyResolution resolution = ProxyResolver.resolveForUrl(
                    config.getBaseUrl(), settings);
            resolved = resolution.getProxy();
            LOG.info("[Confluence] Proxy für " + config.getBaseUrl() + ": "
                    + (resolution.isDirect() ? "DIRECT" : resolved)
                    + " (" + resolution.getReason() + ")");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Confluence] Proxy-Auflösung fehlgeschlagen, verwende DIRECT", e);
        }
        this.proxy = resolved;
    }

    // ════════════════════════════════════════════════════════════════
    //  Public API
    // ════════════════════════════════════════════════════════════════

    /**
     * Execute a GET request against the given relative path.
     *
     * @param relativePath e.g. {@code "/rest/api/space"}
     * @return the response object
     */
    public RestResponse get(String relativePath) {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(relativePath, "GET");
            int statusCode = connection.getResponseCode();
            String statusMessage = connection.getResponseMessage();
            String responseBody = readResponseBody(connection);
            return new RestResponse(statusCode, statusMessage, responseBody);
        } catch (IOException e) {
            throw new ConfluenceException("GET fehlgeschlagen: " + relativePath, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Convenience: GET {@code /rest/api/space} and return the JSON body.
     */
    public String getSpacesJson() {
        RestResponse response = get("/rest/api/space");
        ensureSuccess(response);
        return response.getBody();
    }

    /**
     * Convenience: GET {@code /rest/api/space?limit=<limit>&start=<start>}.
     */
    public String getSpacesJson(int start, int limit) {
        RestResponse response = get("/rest/api/space?start=" + start + "&limit=" + limit);
        ensureSuccess(response);
        return response.getBody();
    }

    /**
     * GET content for a specific space.
     *
     * @param spaceKey e.g. "DEV"
     * @param start    pagination start
     * @param limit    page size
     * @return JSON body
     */
    public String getContentJson(String spaceKey, int start, int limit) {
        RestResponse response = get("/rest/api/content?spaceKey=" + spaceKey
                + "&start=" + start + "&limit=" + limit
                + "&expand=version,space");
        ensureSuccess(response);
        return response.getBody();
    }

    /**
     * GET a single content item by ID, expanding body + version.
     */
    public String getContentByIdJson(String contentId) {
        RestResponse response = get("/rest/api/content/" + contentId
                + "?expand=body.view,version,space");
        ensureSuccess(response);
        return response.getBody();
    }

    /**
     * Search via CQL.
     */
    public String searchJson(String cql, int start, int limit) {
        try {
            String encoded = java.net.URLEncoder.encode(cql, "UTF-8");
            RestResponse response = get("/rest/api/content/search?cql=" + encoded
                    + "&start=" + start + "&limit=" + limit);
            ensureSuccess(response);
            return response.getBody();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new ConfluenceException("UTF-8 encoding nicht verfügbar", e);
        }
    }

    /**
     * GET child pages of a content item.
     *
     * @param contentId parent page id
     * @param start     pagination start
     * @param limit     page size
     * @return JSON body with child pages
     */
    public String getChildrenJson(String contentId, int start, int limit) {
        RestResponse response = get("/rest/api/content/" + contentId
                + "/child/page?start=" + start + "&limit=" + limit
                + "&expand=version,space");
        ensureSuccess(response);
        return response.getBody();
    }

    /**
     * GET a content item with ancestors expanded.
     *
     * @param contentId page id
     * @return JSON body including ancestors array
     */
    public String getContentWithAncestorsJson(String contentId) {
        RestResponse response = get("/rest/api/content/" + contentId
                + "?expand=ancestors,space,version");
        ensureSuccess(response);
        return response.getBody();
    }

    /**
     * GET labels of a content item.
     *
     * @param contentId page id
     * @return JSON body with labels
     */
    public String getLabelsJson(String contentId) {
        RestResponse response = get("/rest/api/content/" + contentId + "/label");
        ensureSuccess(response);
        return response.getBody();
    }

    /**
     * Quick connectivity test. Returns a {@link TestResult} with HTTP status code
     * and, on failure, a detailed error message.
     */
    public TestResult testConnection() {
        try {
            RestResponse response = get("/rest/api/space?limit=1");
            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                return new TestResult(response.getStatusCode(), null);
            } else {
                return new TestResult(response.getStatusCode(),
                        "HTTP " + response.getStatusCode() + " " + response.getStatusMessage()
                                + "\n" + truncate(response.getBody(), 400));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Confluence] Verbindungstest fehlgeschlagen", e);
            // Build a human-readable chain of causes
            String detail = buildErrorChain(e);
            return new TestResult(-1, detail);
        }
    }

    /**
     * Build a readable error chain from nested exceptions.
     */
    private static String buildErrorChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable current = t;
        int depth = 0;
        while (current != null && depth < 5) {
            if (depth > 0) sb.append("\n  ← ");
            sb.append(current.getClass().getSimpleName());
            if (current.getMessage() != null) {
                sb.append(": ").append(current.getMessage());
            }
            current = current.getCause();
            depth++;
        }
        return sb.toString();
    }

    /**
     * Result of a connectivity test.
     */
    public static final class TestResult {
        private final int statusCode;
        private final String errorDetail;

        public TestResult(int statusCode, String errorDetail) {
            this.statusCode = statusCode;
            this.errorDetail = errorDetail;
        }

        public int getStatusCode() { return statusCode; }
        public String getErrorDetail() { return errorDetail; }
        public boolean isSuccess() { return statusCode >= 200 && statusCode < 300; }
    }

    // ════════════════════════════════════════════════════════════════
    //  HTTP internals
    // ════════════════════════════════════════════════════════════════

    private HttpURLConnection openConnection(String relativePath, String method) throws IOException {
        URL url = new URL(buildAbsoluteUrl(relativePath));
        HttpURLConnection connection = (HttpURLConnection) (
                proxy != null && proxy != Proxy.NO_PROXY
                        ? url.openConnection(proxy)
                        : url.openConnection());

        if (connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(sslContext.getSocketFactory());
        }

        connection.setRequestMethod(method);
        connection.setConnectTimeout(config.getConnectTimeoutMillis());
        connection.setReadTimeout(config.getReadTimeoutMillis());
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Authorization", authorizationHeaderValue);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        return connection;
    }

    private String buildAbsoluteUrl(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new IllegalArgumentException("relativePath must not be blank");
        }
        String base = config.getBaseUrl();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (!relativePath.startsWith("/")) relativePath = "/" + relativePath;
        return base + relativePath;
    }

    private void ensureSuccess(RestResponse response) {
        if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
            throw new ConfluenceException(
                    "HTTP " + response.getStatusCode() + " " + response.getStatusMessage()
                            + "\n" + truncate(response.getBody(), 500));
        }
    }

    private String readResponseBody(HttpURLConnection connection) throws IOException {
        InputStream stream = connection.getErrorStream();
        if (stream == null) {
            try {
                stream = connection.getInputStream();
            } catch (IOException e) {
                return "";
            }
        }
        if (stream == null) return "";
        try {
            return readFully(stream);
        } finally {
            stream.close();
        }
    }

    private String readFully(InputStream inputStream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = inputStream.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return new String(out.toByteArray(), UTF8);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    // ════════════════════════════════════════════════════════════════
    //  SSL / mTLS
    // ════════════════════════════════════════════════════════════════

    private SSLContext createSslContext(String alias) {
        try {
            KeyStore windowsMyKeyStore = KeyStore.getInstance("Windows-MY");
            windowsMyKeyStore.load(null, null);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(windowsMyKeyStore, null);

            X509KeyManager originalKm = findX509KeyManager(kmf.getKeyManagers());
            ensureAliasExists(originalKm, alias);

            X509KeyManager forcedKm = new AliasForcingX509KeyManager(originalKm, alias);

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(new KeyManager[]{forcedKm}, null, null);
            return ctx;
        } catch (Exception e) {
            throw new ConfluenceException(
                    "SSL-Kontext konnte nicht mit Zertifikat-Alias '" + alias + "' initialisiert werden", e);
        }
    }

    private X509KeyManager findX509KeyManager(KeyManager[] keyManagers) {
        for (KeyManager km : keyManagers) {
            if (km instanceof X509KeyManager) return (X509KeyManager) km;
        }
        throw new ConfluenceException("Kein X509KeyManager im Windows-Zertifikatsspeicher gefunden");
    }

    private void ensureAliasExists(X509KeyManager km, String alias) {
        X509Certificate[] chain = km.getCertificateChain(alias);
        PrivateKey pk = km.getPrivateKey(alias);
        if (chain == null || chain.length == 0) {
            throw new ConfluenceException("Keine Zertifikatskette für Alias: " + alias);
        }
        if (pk == null) {
            throw new ConfluenceException("Kein privater Schlüssel für Alias: " + alias);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Basic Auth
    // ════════════════════════════════════════════════════════════════

    private static String createBasicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        // Java 8: use javax.xml.bind.DatatypeConverter as Base64 alternative
        String encoded = javax.xml.bind.DatatypeConverter.printBase64Binary(credentials.getBytes(UTF8));
        return "Basic " + encoded;
    }

    // ════════════════════════════════════════════════════════════════
    //  Inner classes
    // ════════════════════════════════════════════════════════════════

    /**
     * Forces a specific certificate alias for client authentication,
     * preventing Java from accidentally choosing an expired or wrong cert.
     */
    private static final class AliasForcingX509KeyManager implements X509KeyManager {
        private final X509KeyManager delegate;
        private final String alias;

        AliasForcingX509KeyManager(X509KeyManager delegate, String alias) {
            this.delegate = delegate;
            this.alias = alias;
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return new String[]{alias};
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, java.net.Socket socket) {
            return alias;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return delegate.getServerAliases(keyType, issuers);
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, java.net.Socket socket) {
            return delegate.chooseServerAlias(keyType, issuers, socket);
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return delegate.getCertificateChain(alias);
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return delegate.getPrivateKey(alias);
        }
    }

    /**
     * Simple response wrapper.
     */
    public static final class RestResponse {
        private final int statusCode;
        private final String statusMessage;
        private final String body;

        public RestResponse(int statusCode, String statusMessage, String body) {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.body = body;
        }

        public int getStatusCode()     { return statusCode; }
        public String getStatusMessage() { return statusMessage; }
        public String getBody()         { return body; }
    }

    /**
     * Runtime exception for Confluence REST errors.
     */
    public static class ConfluenceException extends RuntimeException {
        public ConfluenceException(String message) { super(message); }
        public ConfluenceException(String message, Throwable cause) { super(message, cause); }
    }
}

