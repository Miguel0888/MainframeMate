package de.bund.zrb.files.auth;

import java.util.Objects;

/**
 * Stable identifier for a connection context.
 *
 * Used to resolve credentials without leaking UI concerns into infrastructure code.
 */
public final class ConnectionId {

    private final String scheme;
    private final String host;
    private final String username;

    public ConnectionId(String scheme, String host, String username) {
        this.scheme = scheme == null ? "" : scheme;
        this.host = host == null ? "" : host;
        this.username = username == null ? "" : username;
    }

    public String getScheme() {
        return scheme;
    }

    public String getHost() {
        return host;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConnectionId)) return false;
        ConnectionId that = (ConnectionId) o;
        return Objects.equals(scheme, that.scheme)
                && Objects.equals(host, that.host)
                && Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, host, username);
    }

    @Override
    public String toString() {
        return scheme + "://" + username + "@" + host;
    }
}

