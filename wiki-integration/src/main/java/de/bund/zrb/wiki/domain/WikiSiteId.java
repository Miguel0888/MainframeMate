package de.bund.zrb.wiki.domain;

import java.util.Objects;

/**
 * Unique identifier for a configured wiki site.
 */
public final class WikiSiteId {
    private final String value;

    public WikiSiteId(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public String value() { return value; }

    @Override
    public boolean equals(Object o) {
        return o instanceof WikiSiteId && ((WikiSiteId) o).value.equals(this.value);
    }

    @Override
    public int hashCode() { return value.hashCode(); }

    @Override
    public String toString() { return value; }
}

