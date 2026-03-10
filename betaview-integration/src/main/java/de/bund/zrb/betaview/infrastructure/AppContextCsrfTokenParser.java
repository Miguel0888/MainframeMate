package de.bund.zrb.betaview.infrastructure;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses CSRF token from BetaView's getAppContext.action JSON response.
 * Supports two known formats:
 * <ul>
 *   <li>Nested: {@code "csrfToken":{"field":"...","name":"...","value":"..."}}</li>
 *   <li>Flat: {@code "csrfField":"...","csrfName":"...","csrfValue":"..."}
 *        or any combo of {@code "field":"...","name":"...","value":"..."}</li>
 * </ul>
 */
public final class AppContextCsrfTokenParser {

    // Format 1: nested csrfToken object (betaview-example format)
    private static final Pattern CSRF_NESTED = Pattern.compile(
            "\"csrfToken\"\\s*:\\s*\\{[^}]*\"field\"\\s*:\\s*\"([^\"]+)\"[^}]*\"name\"\\s*:\\s*\"([^\"]+)\"[^}]*\"value\"\\s*:\\s*\"([^\"]+)\"[^}]*\\}",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // Individual field extractors for flat format
    private static final Pattern FIELD_PAT = Pattern.compile("\"(?:csrf[Ff]ield|field)\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NAME_PAT  = Pattern.compile("\"(?:csrf[Nn]ame|name)\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern VALUE_PAT = Pattern.compile("\"(?:csrf[Vv]alue|value)\"\\s*:\\s*\"([^\"]+)\"");

    public BetaViewCsrfToken parseFrom(String appContextJson) {
        if (appContextJson == null || appContextJson.trim().isEmpty()) {
            return null;
        }

        // Try nested format first
        Matcher nested = CSRF_NESTED.matcher(appContextJson);
        if (nested.find()) {
            return new BetaViewCsrfToken(nested.group(1), nested.group(2), nested.group(3));
        }

        // Fall back to flat field extraction
        String field = firstMatch(FIELD_PAT, appContextJson);
        String name  = firstMatch(NAME_PAT, appContextJson);
        String value = firstMatch(VALUE_PAT, appContextJson);

        if (field != null && name != null && value != null) {
            return new BetaViewCsrfToken(field, name, value);
        }

        return null;
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
