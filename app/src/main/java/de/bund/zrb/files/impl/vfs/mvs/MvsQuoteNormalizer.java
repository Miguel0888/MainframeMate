package de.bund.zrb.files.impl.vfs.mvs;

/**
 * Normalizes MVS dataset names with proper quoting.
 *
 * Rules:
 * - Dataset names must be enclosed in single quotes for FTP commands
 * - Never double-quote (no ''HLQ'')
 * - Wildcards like .* are preserved inside quotes
 */
public final class MvsQuoteNormalizer {

    private MvsQuoteNormalizer() {
        // Utility class
    }

    /**
     * Normalize a path to have exactly one pair of outer quotes.
     *
     * Examples:
     * - HLQ -> 'HLQ'
     * - 'HLQ' -> 'HLQ'
     * - ''HLQ'' -> 'HLQ'
     * - HLQ.* -> 'HLQ.*'
     * - 'HLQ.*' -> 'HLQ.*'
     * - HLQ.DATA.SET -> 'HLQ.DATA.SET'
     * - HLQ.PDS(MEM) -> 'HLQ.PDS(MEM)'
     *
     * @param path the path to normalize
     * @return normalized path with exactly one pair of outer quotes
     */
    public static String normalize(String path) {
        if (path == null) {
            return "''";
        }

        String trimmed = path.trim();
        if (trimmed.isEmpty()) {
            return "''";
        }

        // Strip all leading and trailing quotes
        String stripped = stripQuotes(trimmed);

        if (stripped.isEmpty()) {
            return "''";
        }

        // Add exactly one pair of quotes
        return "'" + stripped + "'";
    }

    /**
     * Remove outer quotes from a path.
     *
     * Examples:
     * - 'HLQ' -> HLQ
     * - ''HLQ'' -> HLQ
     * - HLQ -> HLQ
     *
     * @param path the path to unquote
     * @return path without outer quotes
     */
    public static String unquote(String path) {
        if (path == null) {
            return "";
        }
        return stripQuotes(path.trim());
    }

    /**
     * Strip all leading and trailing single quotes.
     */
    private static String stripQuotes(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }

        int start = 0;
        int end = s.length();

        // Strip leading quotes
        while (start < end && s.charAt(start) == '\'') {
            start++;
        }

        // Strip trailing quotes
        while (end > start && s.charAt(end - 1) == '\'') {
            end--;
        }

        if (start >= end) {
            return "";
        }

        return s.substring(start, end);
    }

    /**
     * Check if a path is already properly quoted.
     */
    public static boolean isQuoted(String path) {
        if (path == null || path.length() < 2) {
            return false;
        }
        String trimmed = path.trim();
        return trimmed.startsWith("'") && trimmed.endsWith("'") &&
               !trimmed.startsWith("''");
    }

    /**
     * Check if a path contains a wildcard.
     */
    public static boolean hasWildcard(String path) {
        if (path == null) {
            return false;
        }
        String unquoted = unquote(path);
        return unquoted.contains("*") || unquoted.contains("%");
    }

    /**
     * Create a wildcard query path from a logical path.
     * Example: 'HLQ' -> 'HLQ.*'
     */
    public static String toWildcardQuery(String logicalPath) {
        String unquoted = unquote(logicalPath);
        if (unquoted.isEmpty()) {
            return "''";
        }

        // Don't add wildcard if already present
        if (unquoted.endsWith(".*") || unquoted.endsWith("*")) {
            return normalize(unquoted);
        }

        return normalize(unquoted + ".*");
    }

    /**
     * Extract the HLQ from a fully qualified dataset name.
     * Example: 'USERID.DATA.SET' -> USERID
     */
    public static String extractHlq(String datasetName) {
        String unquoted = unquote(datasetName);
        if (unquoted.isEmpty()) {
            return "";
        }

        int firstDot = unquoted.indexOf('.');
        if (firstDot > 0) {
            return unquoted.substring(0, firstDot);
        }

        // Handle member paths
        int paren = unquoted.indexOf('(');
        if (paren > 0) {
            String dsn = unquoted.substring(0, paren);
            int dot = dsn.indexOf('.');
            return dot > 0 ? dsn.substring(0, dot) : dsn;
        }

        return unquoted;
    }
}

