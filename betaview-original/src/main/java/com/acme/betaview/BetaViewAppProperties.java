package com.acme.betaview;

public final class BetaViewAppProperties {

    private final String url;
    private final String user;
    private final String password;

    private final String favoriteId;
    private final String locale;

    private final String extension;
    private final String form;
    private final String report;
    private final String jobName;

    private final int daysBack;

    public BetaViewAppProperties(String url,
                                 String user,
                                 String password,
                                 String favoriteId,
                                 String locale,
                                 String extension,
                                 String form,
                                 String report,
                                 String jobName,
                                 int daysBack) {
        this.url = nullToEmpty(url);
        this.user = nullToEmpty(user);
        this.password = nullToEmpty(password);

        this.favoriteId = emptyToDefault(favoriteId, "A158");
        this.locale = emptyToDefault(locale, "de");

        this.extension = emptyToDefault(extension, "*");
        this.form = emptyToDefault(form, "APZF");
        this.report = emptyToDefault(report, "*");
        this.jobName = emptyToDefault(jobName, "*");

        this.daysBack = daysBack > 0 ? daysBack : 60;
    }

    public String url() {
        return url;
    }

    public String user() {
        return user;
    }

    public String password() {
        return password;
    }

    public String favoriteId() {
        return favoriteId;
    }

    public String locale() {
        return locale;
    }

    public String extension() {
        return extension;
    }

    public String form() {
        return form;
    }

    public String report() {
        return report;
    }

    public String jobName() {
        return jobName;
    }

    public int daysBack() {
        return daysBack;
    }

    private String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private String emptyToDefault(String v, String def) {
        if (v == null) {
            return def;
        }
        return v.trim().isEmpty() ? def : v;
    }
}