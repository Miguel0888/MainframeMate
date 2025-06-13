package de.bund.zrb.util;

import de.zrb.bund.newApi.VariableRegistry;

import java.util.concurrent.TimeoutException;

public final class AsyncPlaceholderResolver {

    private final VariableRegistry registry;
    private final long timeoutMillis;

    public AsyncPlaceholderResolver(VariableRegistry registry, long timeoutMillis) {
        this.registry = registry;
        this.timeoutMillis = timeoutMillis;
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
            try {
                String value = registry.get(key, timeoutMillis);
                result.append(value != null ? value : "");
            } catch (TimeoutException | InterruptedException e) {
                throw new IllegalStateException("Variable not set within timeout: \"" + key + "\"", e);
            }

            lastEnd = matcher.end();
        }

        result.append(input.substring(lastEnd));
        return result.toString();
    }
}
