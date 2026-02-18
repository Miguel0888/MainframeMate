package de.bund.zrb.files.path;

public class MvsPathDialect implements PathDialect {

    public static final String MVS_ROOT = "''";

    @Override
    public String toAbsolutePath(String path) {
        if (path == null) {
            return MVS_ROOT;
        }

        String trimmed = path.trim();
        if (trimmed.isEmpty() || "/".equals(trimmed)) {
            return MVS_ROOT;
        }

        if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
            return trimmed;
        }

        String normalized = trimmed;
        if (normalized.contains("/")) {
            normalized = normalized.replace('/', '.');
        }

        if (normalized.startsWith("'")) {
            return normalized + "'";
        }

        if (normalized.endsWith("'")) {
            return "'" + normalized;
        }

        return "'" + normalized + "'";
    }

    /**
     * Returns true if the path represents a PDS member spec like DATA.SET(MEMBER).
     * Quotes around the dataset are allowed.
     */
    public boolean isMemberPath(String path) {
        if (path == null) return false;
        String trimmed = path.trim();
        if (trimmed.isEmpty()) return false;

        String unquoted = unquote(trimmed);
        int open = unquoted.indexOf('(');
        int close = unquoted.indexOf(')');
        return open > 0 && close == unquoted.length() - 1 && open < close;
    }

    /**
     * Splits a member spec into dataset and member name.
     *
     * @return array of size 2: [dataset, member]
     * @throws IllegalArgumentException if the input isn't a member path
     */
    public String[] splitMember(String path) {
        if (!isMemberPath(path)) {
            throw new IllegalArgumentException("Not a member path: " + path);
        }

        String unquoted = unquote(path.trim());
        int open = unquoted.indexOf('(');
        int close = unquoted.lastIndexOf(')');

        String dataset = unquoted.substring(0, open);
        String member = unquoted.substring(open + 1, close);
        return new String[]{dataset, member};
    }

    /**
     * Joins a dataset and member into DATASET(MEMBER) (unquoted, not absolute).
     */
    public String toMemberSpec(String dataset, String member) {
        String ds = dataset == null ? "" : dataset.trim();
        String mem = member == null ? "" : member.trim();
        if (ds.isEmpty()) {
            return mem;
        }
        if (mem.isEmpty()) {
            return ds;
        }
        return ds + "(" + mem + ")";
    }

    /**
     * Returns a list of plausible absolute paths for reading/writing.
     * If the input looks like DATASET.MEMBER, also tries DATASET(MEMBER).
     */
    public java.util.List<String> resolveCandidates(String path) {
        String trimmed = path == null ? "" : path.trim();
        if (trimmed.isEmpty() || "/".equals(trimmed)) {
            return java.util.Collections.singletonList(MVS_ROOT);
        }

        if (trimmed.startsWith("'")) {
            return java.util.Collections.singletonList(toAbsolutePath(trimmed));
        }

        if (isMemberPath(trimmed)) {
            return java.util.Collections.singletonList(toAbsolutePath(trimmed));
        }

        if (trimmed.contains(".")) {
            int last = trimmed.lastIndexOf('.');
            String dataset = trimmed.substring(0, last);
            String member = trimmed.substring(last + 1);
            String candidate1 = toAbsolutePath(toMemberSpec(dataset, member));
            String candidate2 = toAbsolutePath(trimmed);
            java.util.List<String> candidates = new java.util.ArrayList<String>(2);
            candidates.add(candidate1);
            candidates.add(candidate2);
            return candidates;
        }

        return java.util.Collections.singletonList(toAbsolutePath(trimmed));
    }

    /**
     * Creates a child path for listings.
     * For member paths the join uses DATASET(MEMBER). Otherwise it joins with a dot.
     */
    public String childOf(String parentAbsolutePath, String childName) {
        String rawParent = unquote(parentAbsolutePath == null ? "" : parentAbsolutePath.trim());
        String name = childName == null ? "" : childName.trim();

        if (rawParent.isEmpty()) {
            return toAbsolutePath(name);
        }

        // If the child itself already looks like a member spec, keep it as-is.
        if (isMemberPath(name)) {
            return toAbsolutePath(name);
        }

        // If parent is a dataset and child is a member name, generate DATASET(MEMBER).
        // This is the common representation for PDS member navigation.
        if (!name.isEmpty() && rawParent.indexOf('(') < 0) {
            return toAbsolutePath(toMemberSpec(rawParent, name));
        }

        return toAbsolutePath(rawParent + "." + name);
    }

    private String unquote(String path) {
        if (path == null) {
            return "";
        }
        String trimmed = path.trim();
        if (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
