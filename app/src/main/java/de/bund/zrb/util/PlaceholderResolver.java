package de.bund.zrb.util;

import java.util.Collections;
import java.util.Map;

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
            String value = variables.getOrDefault(key, "");
            result.append(value);

            lastEnd = matcher.end();
        }

        result.append(input.substring(lastEnd));
        return result.toString();
    }
}
