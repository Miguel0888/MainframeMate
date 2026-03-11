package com.acme.betaview;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AppContextCsrfTokenParser {

    private static final Pattern CSRF_PATTERN = Pattern.compile(
            "\"csrfToken\"\\s*:\\s*\\{\\s*\"field\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"value\"\\s*:\\s*\"([^\"]+)\"\\s*\\}",
            Pattern.CASE_INSENSITIVE);

    public BetaViewCsrfToken parseFrom(String appContextJson) {
        if (appContextJson == null) {
            return null;
        }

        Matcher matcher = CSRF_PATTERN.matcher(appContextJson);
        if (!matcher.find()) {
            return null;
        }

        String field = matcher.group(1);
        String name = matcher.group(2);
        String value = matcher.group(3);

        return new BetaViewCsrfToken(field, name, value);
    }
}