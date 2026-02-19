
package de.bund.zrb.ui.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @deprecated Use {@link de.bund.zrb.ingestion.ui.ChatMarkdownFormatter} instead.
 *             This class only handles code blocks with regex and is replaced by the
 *             library-based commonmark renderer that supports tables, bold, headings, etc.
 */
@Deprecated
public class LegacyChatMarkdownFormatter {

    /**
     * @deprecated Use {@link de.bund.zrb.ingestion.ui.ChatMarkdownFormatter#format(String)} instead.
     */
    @Deprecated
    public static String format(String rawText) {
        String escaped = escapeHtml(rawText);

        // Sprache-spezifische Codeblöcke: ```lang\n...\n```
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

        // Sprachlose Codeblöcke: ``` irgendwas ```
        Pattern genericBlockPattern = Pattern.compile("(?s)```\\s*(.*?)```");
        Matcher fallbackMatcher = genericBlockPattern.matcher(result.toString());
        StringBuffer finalResult = new StringBuffer();
        while (fallbackMatcher.find()) {
            String code = fallbackMatcher.group(1);
            String replacement = String.format(
                    "<pre style='background:#f8f9fa; padding:6px; border-radius:6px; border:1px solid #ccc; " +
                            "font-family:monospace; white-space:pre-wrap;'>%s</pre>",
                    code
            );
            fallbackMatcher.appendReplacement(finalResult, Matcher.quoteReplacement(replacement));
        }
        fallbackMatcher.appendTail(finalResult);

        return finalResult.toString().replace("\n", "<br/>");
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

