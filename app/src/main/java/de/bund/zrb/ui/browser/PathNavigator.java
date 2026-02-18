package de.bund.zrb.ui.browser;

import de.bund.zrb.files.path.MvsPathDialect;

/**
 * Small UI helper to build child/parent paths without relying on server CWD.
 *
 * Note: This is intentionally UI-only for now. If it proves useful, we can move it to core later.
 */
public class PathNavigator {

    private final boolean mvsMode;
    private final MvsPathDialect mvsDialect;

    public PathNavigator(boolean mvsMode) {
        this(mvsMode, new MvsPathDialect());
    }

    public PathNavigator(boolean mvsMode, MvsPathDialect mvsDialect) {
        this.mvsMode = mvsMode;
        this.mvsDialect = mvsDialect;
    }

    public String normalize(String path) {
        if (mvsMode) {
            return mvsDialect.toAbsolutePath(path);
        }
        if (path == null || path.trim().isEmpty()) {
            return "/";
        }
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    public String childOf(String parentPath, String name) {
        if (mvsMode) {
            return mvsDialect.childOf(parentPath, name);
        }
        String parent = normalize(parentPath);
        String child = name == null ? "" : name.trim();
        if (child.isEmpty()) {
            return parent;
        }
        if ("/".equals(parent)) {
            return "/" + child;
        }
        return parent.endsWith("/") ? parent + child : parent + "/" + child;
    }

    public String parentOf(String path) {
        if (mvsMode) {
            // Minimal, conservative: keep same for now. (Full MVS semantics come in fileservice-004.)
            return mvsDialect.toAbsolutePath(path);
        }

        String normalized = normalize(path);
        if ("/".equals(normalized)) {
            return "/";
        }

        String tmp = normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        int idx = tmp.lastIndexOf('/');
        if (idx <= 0) {
            return "/";
        }
        return tmp.substring(0, idx);
    }
}

