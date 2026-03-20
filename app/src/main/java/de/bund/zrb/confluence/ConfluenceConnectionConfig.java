package de.bund.zrb.confluence;

import java.util.Objects;

/**
 * Immutable configuration for a Confluence Data Center REST connection.
 * <p>
 * Supports:
 * <ul>
 *   <li>Optional mTLS with a client certificate from {@code Windows-MY} (selected by alias)</li>
 *   <li>HTTP Basic Auth (username + password)</li>
 * </ul>
 * If {@code clientCertificateAlias} is {@code null} or empty, mTLS is disabled and
 * only Basic Auth is used.
 */
public final class ConfluenceConnectionConfig {

    private final String baseUrl;
    private final String username;
    private final String password;
    private final String clientCertificateAlias;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public ConfluenceConnectionConfig(
            String baseUrl,
            String username,
            String password,
            String clientCertificateAlias,
            int connectTimeoutMillis,
            int readTimeoutMillis) {

        this.baseUrl = requireNonBlank(baseUrl, "baseUrl");
        this.username = requireNonBlank(username, "username");
        this.password = Objects.requireNonNull(password, "password must not be null");
        this.clientCertificateAlias = (clientCertificateAlias != null && !clientCertificateAlias.trim().isEmpty())
                ? clientCertificateAlias : null;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public String getBaseUrl()                { return baseUrl; }
    public String getUsername()               { return username; }
    public String getPassword()               { return password; }
    public String getClientCertificateAlias() { return clientCertificateAlias; }
    public boolean hasCertificate()           { return clientCertificateAlias != null; }
    public int getConnectTimeoutMillis()      { return connectTimeoutMillis; }
    public int getReadTimeoutMillis()         { return readTimeoutMillis; }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}

