package de.bund.zrb.ingestion.infrastructure;

import de.bund.zrb.ingestion.port.TextPostProcessor;

import java.util.regex.Pattern;

/**
 * Default text normalizer/post-processor.
 * Cleans and normalizes extracted text for consistent output.
 */
public class TextNormalizer implements TextPostProcessor {

    // Patterns for normalization
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
    private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\\n{4,}");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile(" {3,}");
    private static final Pattern TRAILING_WHITESPACE = Pattern.compile("[ \\t]+\\n");
    private static final Pattern LEADING_WHITESPACE_LINES = Pattern.compile("\\n[ \\t]+\\n");
    private static final Pattern WINDOWS_NEWLINES = Pattern.compile("\\r\\n");
    private static final Pattern MAC_NEWLINES = Pattern.compile("\\r(?!\\n)");

    private final boolean removeExcessiveNewlines;
    private final boolean normalizeWhitespace;
    private final boolean trimLines;

    public TextNormalizer() {
        this(true, true, true);
    }

    public TextNormalizer(boolean removeExcessiveNewlines, boolean normalizeWhitespace, boolean trimLines) {
        this.removeExcessiveNewlines = removeExcessiveNewlines;
        this.normalizeWhitespace = normalizeWhitespace;
        this.trimLines = trimLines;
    }

    @Override
    public String process(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;

        // 1. Remove control characters (except newline, tab)
        result = CONTROL_CHARS.matcher(result).replaceAll("");

        // 2. Normalize line endings to \n
        result = WINDOWS_NEWLINES.matcher(result).replaceAll("\n");
        result = MAC_NEWLINES.matcher(result).replaceAll("\n");

        // 3. Trim trailing whitespace on each line
        if (trimLines) {
            result = TRAILING_WHITESPACE.matcher(result).replaceAll("\n");
        }

        // 4. Reduce excessive blank lines
        if (removeExcessiveNewlines) {
            result = MULTIPLE_NEWLINES.matcher(result).replaceAll("\n\n\n");
            result = LEADING_WHITESPACE_LINES.matcher(result).replaceAll("\n\n");
        }

        // 5. Normalize multiple consecutive spaces
        if (normalizeWhitespace) {
            result = MULTIPLE_SPACES.matcher(result).replaceAll("  ");
        }

        // 6. Trim overall result
        result = result.trim();

        return result;
    }

    /**
     * Remove binary garbage from text.
     * Useful for texts extracted from binary-mixed content.
     */
    public String removeBinaryGarbage(String text) {
        if (text == null) return null;

        StringBuilder sb = new StringBuilder();
        int unprintableCount = 0;
        int totalCount = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            totalCount++;

            if (isPrintable(c)) {
                sb.append(c);
                unprintableCount = 0; // Reset count on printable char
            } else {
                unprintableCount++;
                // If too many consecutive unprintable chars, skip them
                if (unprintableCount < 3) {
                    sb.append(' '); // Replace with space
                }
            }
        }

        return sb.toString();
    }

    private boolean isPrintable(char c) {
        // Allow printable ASCII, common whitespace, and common Unicode
        return (c >= 0x20 && c < 0x7F) || // Basic ASCII printable
               c == '\n' || c == '\r' || c == '\t' || // Whitespace
               (c >= 0x00A0 && c < 0xFFFE); // Extended Unicode (excluding control)
    }

    /**
     * Extract only ASCII-compatible text.
     * Useful as last resort for heavily corrupted content.
     */
    public String extractAsciiOnly(String text) {
        if (text == null) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 0x20 && c <= 0x7E) || c == '\n' || c == '\r' || c == '\t') {
                sb.append(c);
            } else if (c > 0x7F) {
                // Non-ASCII: try to keep common extended chars
                sb.append(c);
            }
        }
        return sb.toString();
    }
}

