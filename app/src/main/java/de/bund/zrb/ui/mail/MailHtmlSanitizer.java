package de.bund.zrb.ui.mail;

import java.util.regex.Pattern;

/**
 * Sanitizes HTML email content for safe display in JEditorPane.
 *
 * JEditorPane (HTMLEditorKit) cannot handle:
 * - CSS @import rules
 * - CSS media queries
 * - Complex inline styles with unknown properties
 * - Hidden preheader text (zero-width spaces, display:none spans)
 *
 * This sanitizer strips problematic elements while preserving readable content.
 */
public final class MailHtmlSanitizer {

    private MailHtmlSanitizer() {}

    // Remove <style>...</style> blocks (case-insensitive, dotall for multiline)
    private static final Pattern STYLE_BLOCK = Pattern.compile(
            "<style[^>]*>.*?</style>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // Remove <script>...</script> blocks
    private static final Pattern SCRIPT_BLOCK = Pattern.compile(
            "<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // Remove <head>...</head> blocks (contains meta, style, link tags)
    private static final Pattern HEAD_BLOCK = Pattern.compile(
            "<head[^>]*>.*?</head>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // Remove <link ...> tags (CSS imports)
    private static final Pattern LINK_TAG = Pattern.compile(
            "<link[^>]*>", Pattern.CASE_INSENSITIVE);

    // Remove class="..." attributes (references unresolved CSS classes)
    private static final Pattern CLASS_ATTR = Pattern.compile(
            "\\s+class\\s*=\\s*\"[^\"]*\"", Pattern.CASE_INSENSITIVE);

    // Remove style="..." attributes that contain display:none (hidden preheader text)
    private static final Pattern DISPLAY_NONE = Pattern.compile(
            "<[^>]+style\\s*=\\s*\"[^\"]*display\\s*:\\s*none[^\"]*\"[^>]*>.*?</[^>]+>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // Remove zero-width/invisible characters and their wrapper spans
    // Unicode: \u200B (zero-width space), \u200C, \u200D, \uFEFF (BOM), \u00AD (soft hyphen)
    private static final Pattern INVISIBLE_CHARS = Pattern.compile(
            "[\u200B\u200C\u200D\uFEFF\u00AD\u034F\u2060\u2061\u2062\u2063\u2064]+");

    // Remove <!--[if ...]>...<![endif]--> conditional comments (Outlook/IE)
    private static final Pattern CONDITIONAL_COMMENTS = Pattern.compile(
            "<!--\\[if[^]]*]>.*?<!\\[endif]-->", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // Remove XML processing instructions
    private static final Pattern XML_PI = Pattern.compile(
            "<\\?xml[^>]*\\?>", Pattern.CASE_INSENSITIVE);

    // Remove xmlns attributes
    private static final Pattern XMLNS_ATTR = Pattern.compile(
            "\\s+xmlns(:[a-z]+)?\\s*=\\s*\"[^\"]*\"", Pattern.CASE_INSENSITIVE);

    // Remove o: and v: namespace tags (Outlook VML)
    private static final Pattern OUTLOOK_TAGS = Pattern.compile(
            "</?[ov]:[^>]*>", Pattern.CASE_INSENSITIVE);

    /**
     * Sanitizes HTML email content for safe display in JEditorPane.
     * Strips style blocks, scripts, hidden preheader text, and other
     * elements that JEditorPane cannot render properly.
     *
     * @param html raw HTML from the email
     * @return sanitized HTML safe for JEditorPane rendering
     */
    public static String sanitize(String html) {
        if (html == null || html.trim().isEmpty()) {
            return "";
        }

        String result = html;

        // 1. Remove conditional comments (before other processing)
        result = CONDITIONAL_COMMENTS.matcher(result).replaceAll("");

        // 2. Remove <head> block (contains all the problematic CSS/meta)
        result = HEAD_BLOCK.matcher(result).replaceAll("");

        // 3. Remove remaining <style> blocks
        result = STYLE_BLOCK.matcher(result).replaceAll("");

        // 4. Remove <script> blocks
        result = SCRIPT_BLOCK.matcher(result).replaceAll("");

        // 5. Remove <link> tags
        result = LINK_TAG.matcher(result).replaceAll("");

        // 6. Remove XML processing instructions
        result = XML_PI.matcher(result).replaceAll("");

        // 7. Remove Outlook VML tags
        result = OUTLOOK_TAGS.matcher(result).replaceAll("");

        // 8. Remove xmlns attributes
        result = XMLNS_ATTR.matcher(result).replaceAll("");

        // 9. Remove elements with display:none (hidden preheader text)
        result = DISPLAY_NONE.matcher(result).replaceAll("");

        // 10. Remove class attributes (reference non-existent CSS)
        result = CLASS_ATTR.matcher(result).replaceAll("");

        // 11. Remove invisible Unicode characters
        result = INVISIBLE_CHARS.matcher(result).replaceAll("");

        // 12. Clean up excessive whitespace/newlines left by removals
        result = result.replaceAll("(?m)^\\s*$\\n", ""); // blank lines
        result = result.replaceAll("\\n{3,}", "\n\n"); // max 2 consecutive newlines

        // Ensure wrapped in <html><body> if not already
        String lower = result.trim().toLowerCase();
        if (!lower.startsWith("<html") && !lower.startsWith("<!doctype")) {
            result = "<html><body>" + result + "</body></html>";
        }

        return result;
    }

    /**
     * Extracts just the email address from a From header string.
     * "Max Mustermann &lt;max@example.com&gt;" → "max@example.com"
     * "max@example.com" → "max@example.com"
     *
     * @return lowercase email address, or the original string lowercased if no angle brackets found
     */
    public static String extractEmailAddress(String from) {
        if (from == null || from.trim().isEmpty()) return "";
        String trimmed = from.trim();
        int start = trimmed.lastIndexOf('<');
        int end = trimmed.lastIndexOf('>');
        if (start >= 0 && end > start) {
            return trimmed.substring(start + 1, end).trim().toLowerCase();
        }
        // If it looks like an email, return as-is
        if (trimmed.contains("@")) {
            return trimmed.toLowerCase();
        }
        return trimmed.toLowerCase();
    }
}
