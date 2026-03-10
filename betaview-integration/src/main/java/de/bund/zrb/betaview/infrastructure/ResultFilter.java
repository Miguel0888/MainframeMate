package de.bund.zrb.betaview.infrastructure;

/**
 * Value object for BetaView search filter parameters.
 * Taken 1:1 from the tested betaview-example (com.acme.betaview.ResultFilter).
 */
public final class ResultFilter {

    private final String favoriteId;
    private final String locale;
    private final String extensionPattern;
    private final String form;
    private final int daysBack;

    public ResultFilter(String favoriteId, String locale, String extensionPattern, String form, int daysBack) {
        this.favoriteId = requireNotBlank(favoriteId, "favoriteId must not be blank");
        this.locale = requireNotBlank(locale, "locale must not be blank");
        this.extensionPattern = requireNotBlank(extensionPattern, "extensionPattern must not be blank");
        this.form = requireNotBlank(form, "form must not be blank");
        this.daysBack = daysBack > 0 ? daysBack : 60;
    }

    public String favoriteId() {
        return favoriteId;
    }

    public String locale() {
        return locale;
    }

    public String extensionPattern() {
        return extensionPattern;
    }

    public String form() {
        return form;
    }

    public int daysBack() {
        return daysBack;
    }

    private static String requireNotBlank(String v, String message) {
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return v;
    }
}

