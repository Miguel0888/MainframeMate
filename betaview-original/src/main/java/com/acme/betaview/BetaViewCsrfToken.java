package com.acme.betaview;

final class BetaViewCsrfToken {

    private final String field;
    private final String name;
    private final String value;

    public BetaViewCsrfToken(String field, String name, String value) {
        this.field = requireNotBlank(field, "field must not be blank");
        this.name = requireNotBlank(name, "name must not be blank");
        this.value = requireNotBlank(value, "value must not be blank");
    }

    public String field() {
        return field;
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }

    private static String requireNotBlank(String v, String message) {
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return v;
    }
}