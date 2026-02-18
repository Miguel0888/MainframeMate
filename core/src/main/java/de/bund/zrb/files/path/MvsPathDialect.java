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
}

