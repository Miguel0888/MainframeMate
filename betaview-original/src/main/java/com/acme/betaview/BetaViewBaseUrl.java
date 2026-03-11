package com.acme.betaview;

import java.net.URL;
import java.util.Objects;

final class BetaViewBaseUrl {

    private final URL base;

    public BetaViewBaseUrl(URL base) {
        this.base = Objects.requireNonNull(base, "base must not be null");
    }

    public URL resolve(String relativeOrAbsolute) {
        try {
            return new URL(base, relativeOrAbsolute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Resolve URL: " + relativeOrAbsolute, e);
        }
    }
}
