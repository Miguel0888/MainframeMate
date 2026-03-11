package com.acme.betaview;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HiddenInputExtractor {

    private static final Pattern HIDDEN_INPUT_PATTERN = Pattern.compile(
            "<input\\s+[^>]*type\\s*=\\s*['\"]hidden['\"][^>]*>",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NAME_PATTERN = Pattern.compile(
            "name\\s*=\\s*['\"]([^'\"]+)['\"]",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern VALUE_PATTERN = Pattern.compile(
            "value\\s*=\\s*['\"]([^'\"]*)['\"]",
            Pattern.CASE_INSENSITIVE);

    public static Map<String, String> extractHiddenInputs(String html) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        if (html == null) {
            return result;
        }

        Matcher inputs = HIDDEN_INPUT_PATTERN.matcher(html);
        while (inputs.find()) {
            String tag = inputs.group();

            String name = firstMatch(NAME_PATTERN, tag);
            if (name == null) {
                continue;
            }

            String value = firstMatch(VALUE_PATTERN, tag);
            if (value == null) {
                value = "";
            }

            result.put(name, value);
        }
        return result;
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
