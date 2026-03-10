package de.bund.zrb.betaview.infrastructure;

import java.net.CookieManager;
import java.util.Objects;

/**
 * Represents an active BetaView server session.
 * Holds the base URL, cookie state and CSRF token for subsequent requests.
 * Immutable except for the CSRF token which can be updated via {@link #withCsrfToken}.
 */
public final class BetaViewSession {

    private final BetaViewBaseUrl baseUrl;
    private final CookieManager cookieManager;
    private final BetaViewCsrfToken csrfToken;

    public BetaViewSession(BetaViewBaseUrl baseUrl, CookieManager cookieManager) {
        this(baseUrl, cookieManager, null);
    }

    public BetaViewSession(BetaViewBaseUrl baseUrl, CookieManager cookieManager, BetaViewCsrfToken csrfToken) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.cookieManager = Objects.requireNonNull(cookieManager, "cookieManager must not be null");
        this.csrfToken = csrfToken;
    }

    public BetaViewBaseUrl baseUrl() {
        return baseUrl;
    }

    public CookieManager cookieManager() {
        return cookieManager;
    }

    public BetaViewCsrfToken csrfToken() {
        return csrfToken;
    }

    public BetaViewSession withCsrfToken(BetaViewCsrfToken token) {
        return new BetaViewSession(baseUrl, cookieManager, token);
    }
}

