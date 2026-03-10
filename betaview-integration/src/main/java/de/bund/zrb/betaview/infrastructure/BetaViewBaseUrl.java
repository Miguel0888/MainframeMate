package de.bund.zrb.betaview.infrastructure;

import java.net.URL;
import java.util.Objects;

/**
 * Value object for the BetaView base URL.
 * Provides URL resolution relative to the base.
 */
public final class BetaViewBaseUrl {

    private final URL base;

    public BetaViewBaseUrl(URL base) {
        this.base = Objects.requireNonNull(base, "base must not be null");
    }

    public BetaViewBaseUrl(String urlString) {
        try {
            String s = urlString.trim();
            if (!s.endsWith("/")) {
                s = s + "/";
            }
            this.base = new URL(s);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid BetaView URL: " + urlString, e);
        }
    }

    public URL resolve(String relativeOrAbsolute) {
        try {
            return new URL(base, relativeOrAbsolute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Resolve URL: " + relativeOrAbsolute, e);
        }
    }

    public URL toUrl() {
        return base;
    }

    @Override
    public String toString() {
        return base.toString();
    }
}

