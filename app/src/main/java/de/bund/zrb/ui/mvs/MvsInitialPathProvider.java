package de.bund.zrb.ui.mvs;

import de.bund.zrb.files.impl.vfs.mvs.MvsQuoteNormalizer;
import de.bund.zrb.model.Settings;

public final class MvsInitialPathProvider {

    public boolean isRoot(String path) {
        if (path == null) {
            return true;
        }
        String trimmed = path.trim();
        return trimmed.isEmpty() || "''".equals(trimmed) || "/".equals(trimmed);
    }

    /**
     * Resolve the initial MVS path to open.
     * If explicitPath is not ROOT, it is returned unchanged.
     * Otherwise the configured HLQ (login/custom) is applied if present.
     */
    public String resolveInitialPath(String explicitPath, String loginName, Settings settings) {
        if (!isRoot(explicitPath)) {
            return explicitPath;
        }

        String configured = resolveConfiguredInitialHlqPath(loginName, settings);
        return configured != null ? configured : explicitPath;
    }

    /**
     * Resolve only the configured HLQ path (login/custom).
     *
     * @return quoted HLQ path (e.g. 'USERID') or null if nothing is configured
     */
    public String resolveConfiguredInitialHlqPath(String loginName, Settings settings) {
        if (settings == null) {
            return null;
        }

        if (settings.ftpUseLoginAsHlq) {
            String user = normalizeQualifier(loginName);
            return user.isEmpty() ? null : MvsQuoteNormalizer.normalize(user);
        }

        String custom = normalizeQualifier(settings.ftpCustomHlq);
        return custom.isEmpty() ? null : MvsQuoteNormalizer.normalize(custom);
    }

    private String normalizeQualifier(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase();
    }
}
