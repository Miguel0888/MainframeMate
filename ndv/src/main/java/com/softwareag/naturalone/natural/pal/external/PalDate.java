package com.softwareag.naturalone.natural.pal.external;

/**
 * Stub für PalDate.
 */
public class PalDate {

    private final Object delegate;

    public PalDate(Object delegate) {
        this.delegate = delegate;
    }

    @Override
    public String toString() {
        return delegate != null ? delegate.toString() : "";
    }
}

