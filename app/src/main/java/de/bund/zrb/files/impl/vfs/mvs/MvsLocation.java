package de.bund.zrb.files.impl.vfs.mvs;

/**
 * Represents an MVS location with separate logical and query paths.
 *
 * - Logical Path: Display/State path (e.g., 'USERID')
 * - Query Path: Server query path (e.g., 'USERID.*' for listing)
 */
public final class MvsLocation {

    private final MvsLocationType type;
    private final String logicalPath;
    private final String displayName;

    private MvsLocation(MvsLocationType type, String logicalPath, String displayName) {
        this.type = type;
        this.logicalPath = logicalPath;
        this.displayName = displayName;
    }

    /**
     * Create ROOT location.
     */
    public static MvsLocation root() {
        return new MvsLocation(MvsLocationType.ROOT, "", "");
    }

    /**
     * Create HLQ location.
     * @param hlq the HLQ (will be normalized/quoted)
     */
    public static MvsLocation hlq(String hlq) {
        String normalized = MvsQuoteNormalizer.normalize(hlq);
        String display = MvsQuoteNormalizer.unquote(normalized);
        return new MvsLocation(MvsLocationType.HLQ, normalized, display);
    }

    /**
     * Create qualifier context location for hierarchical browsing.
     */
    public static MvsLocation qualifierContext(String qualifierPath) {
        String normalized = MvsQuoteNormalizer.normalize(qualifierPath);
        String display = extractLastQualifier(MvsQuoteNormalizer.unquote(normalized));
        return new MvsLocation(MvsLocationType.QUALIFIER_CONTEXT, normalized, display);
    }

    /**
     * Create DATASET location.
     * @param datasetName the full dataset name (will be normalized/quoted)
     */
    public static MvsLocation dataset(String datasetName) {
        String normalized = MvsQuoteNormalizer.normalize(datasetName);
        String display = extractLastQualifier(MvsQuoteNormalizer.unquote(normalized));
        return new MvsLocation(MvsLocationType.DATASET, normalized, display);
    }

    /**
     * Create MEMBER location.
     * @param memberPath the full member path like 'DATASET(MEMBER)'
     */
    public static MvsLocation member(String memberPath) {
        String normalized = MvsQuoteNormalizer.normalize(memberPath);
        String display = extractMemberName(MvsQuoteNormalizer.unquote(normalized));
        return new MvsLocation(MvsLocationType.MEMBER, normalized, display);
    }

    /**
     * Parse a path string and determine its location type.
     */
    public static MvsLocation parse(String path) {
        if (path == null || path.trim().isEmpty()) {
            return root();
        }

        String normalized = MvsQuoteNormalizer.normalize(path.trim());
        String unquoted = MvsQuoteNormalizer.unquote(normalized);

        if (unquoted.isEmpty()) {
            return root();
        }

        // Check if it's a member path (contains parentheses)
        if (unquoted.contains("(") && unquoted.contains(")")) {
            return member(normalized);
        }

        // Bare qualifier (no dot) -> HLQ. Dotted path -> qualifier context.
        if (unquoted.contains(".")) {
            return qualifierContext(normalized);
        }

        return hlq(normalized);
    }

    /**
     * Get the query path for listing children.
     * For HLQ: 'HLQ' -> 'HLQ.*'
     * For DATASET: 'DSN' -> 'DSN' (member listing)
     */
    public String getQueryPath() {
        switch (type) {
            case ROOT:
                return "''";
            case HLQ:
            case QUALIFIER_CONTEXT:
                // Qualifier browsing requires wildcard
                return MvsQuoteNormalizer.toWildcardQuery(logicalPath);
            case DATASET:
                // Dataset listing (members) uses the dataset itself
                return logicalPath;
            case MEMBER:
                // Members can't be listed
                return logicalPath;
            default:
                return logicalPath;
        }
    }

    /**
     * Get the logical path for display/state.
     */
    public String getLogicalPath() {
        return logicalPath;
    }

    /**
     * Get the display name (last qualifier or member name).
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the location type.
     */
    public MvsLocationType getType() {
        return type;
    }

    /**
     * Check if this location represents a "directory" (can have children).
     */
    public boolean isDirectory() {
        return type == MvsLocationType.ROOT ||
               type == MvsLocationType.HLQ ||
               type == MvsLocationType.QUALIFIER_CONTEXT ||
               type == MvsLocationType.DATASET;
    }

    /**
     * Create a child location from a listing result.
     * @param childName the child name from NLST/LIST
     */
    public MvsLocation createChild(String childName) {
        if (childName == null || childName.trim().isEmpty()) {
            return this;
        }

        String trimmed = childName.trim();
        String unquotedChild = MvsQuoteNormalizer.unquote(trimmed);
        String unquotedParent = MvsQuoteNormalizer.unquote(logicalPath);

        switch (type) {
            case ROOT:
                // Child of root is always HLQ
                return hlq(unquotedChild);

            case HLQ:
            case QUALIFIER_CONTEXT:
                // Child of qualifier context is another qualifier context.
                if (unquotedChild.toUpperCase().startsWith(unquotedParent.toUpperCase() + ".")) {
                    return qualifierContext(unquotedChild);
                }
                return qualifierContext(unquotedParent + "." + unquotedChild);

            case DATASET:
                // Child of DATASET is MEMBER
                // If child looks like a member name (no dots, 1-8 chars)
                if (!unquotedChild.contains(".") && unquotedChild.length() <= 8) {
                    return member(unquotedParent + "(" + unquotedChild + ")");
                }
                // Otherwise, it's a sub-dataset
                if (unquotedChild.toUpperCase().startsWith(unquotedParent.toUpperCase() + ".")) {
                    return dataset(unquotedChild);
                }
                return dataset(unquotedParent + "." + unquotedChild);

            case MEMBER:
                // Members can't have children
                return this;

            default:
                return this;
        }
    }

    private static String extractLastQualifier(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int lastDot = path.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < path.length() - 1) {
            return path.substring(lastDot + 1);
        }
        return path;
    }

    private static String extractMemberName(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int open = path.indexOf('(');
        int close = path.indexOf(')');
        if (open >= 0 && close > open) {
            return path.substring(open + 1, close);
        }
        return path;
    }

    @Override
    public String toString() {
        // Don't wrap logicalPath in quotes since it's already quoted internally
        return "MvsLocation{type=" + type + ", logicalPath=" + logicalPath + ", displayName=" + displayName + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MvsLocation that = (MvsLocation) o;
        return type == that.type &&
               (logicalPath != null ? logicalPath.equalsIgnoreCase(that.logicalPath) : that.logicalPath == null);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + (logicalPath != null ? logicalPath.toUpperCase().hashCode() : 0);
        return result;
    }
}

