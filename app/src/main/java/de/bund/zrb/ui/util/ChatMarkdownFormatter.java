
package de.bund.zrb.ui.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatMarkdownFormatter {

    /**
     * Converts Markdown-style code blocks into styled HTML <pre> blocks.
     * Recognizes ```<language>\n...\n``` and wraps with monospaced formatting.
     */
    public static String format(String rawText) {
        String escaped = escapeHtml(rawText);

        // Pattern: ```lang\ncontent\n```
        Pattern codeBlockPattern = Pattern.compile("(?s)```(\\w+)\\s+(.*?)```");
        Matcher matcher = codeBlockPattern.matcher(escaped);

        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String language = matcher.group(1).toLowerCase();
            String code = matcher.group(2);

            String color = getBackgroundColorFor(language);

            String replacement = String.format(
                    "<pre style='background:%s; padding:6px; border-radius:6px; border:1px solid #ccc; " +
                            "font-family:monospace; white-space:pre-wrap;'>%s</pre>",
                    color, code
            );

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        // Nach allen Ersetzungen, <br/> für einfache Zeilenumbrüche
        return result.toString().replace("\n", "<br/>");
    }

    private static String getBackgroundColorFor(String language) {
        switch (language) {
            case "json":
                return "#f6f8fa";
            case "java":
                return "#f0f8ff";
            case "bash":
            case "sh":
                return "#fdf6e3";
            case "xml":
                return "#fef5f5";
            default:
                return "#f0f0f0";
        }
    }

    private static String escapeHtml(String input) {
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
