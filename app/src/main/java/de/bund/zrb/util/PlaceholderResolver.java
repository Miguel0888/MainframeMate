package de.bund.zrb.util;

import java.util.Collections;
import java.util.Map;

/**
 * Set the value from the map for a placeholder
 *
 * throws  IllegalArgumenteException if a value is not set
 */
public final class PlaceholderResolver {

    private final Map<String, String> variables;

    public PlaceholderResolver(Map<String, String> variables) {
        this.variables = variables != null ? variables : Collections.emptyMap();
    }

    public String resolve(String input) {
        if (input == null) return "";
        StringBuilder result = new StringBuilder();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\\{([^}]+)}}");
        java.util.regex.Matcher matcher = pattern.matcher(input);

        int lastEnd = 0;
        while (matcher.find()) {
            result.append(input, lastEnd, matcher.start());

            String key = matcher.group(1);
            if (!variables.containsKey(key)) {
                throw new IllegalArgumentException("Variable not set: \"" + key + "\"");
            }

            String value = variables.get(key);
            result.append(value != null ? value : "");

            lastEnd = matcher.end();
        }

        result.append(input.substring(lastEnd));
        return result.toString();
    }
}
