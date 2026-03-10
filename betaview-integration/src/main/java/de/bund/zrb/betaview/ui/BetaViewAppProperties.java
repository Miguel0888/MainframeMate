package de.bund.zrb.betaview.ui;

/**
 * Default filter values for BetaView, loaded from MainframeMate Settings.
 * In the standalone BetaView app this was loaded from a properties file.
 */
public final class BetaViewAppProperties {

    private final String favoriteId;
    private final String locale;
    private final String extension;
    private final String form;
    private final String report;
    private final String jobName;
    private final int daysBack;

    public BetaViewAppProperties(String favoriteId, String locale, String extension,
                                 String form, String report, String jobName, int daysBack) {
        this.favoriteId = favoriteId != null ? favoriteId : "";
        this.locale = locale != null ? locale : "de";
        this.extension = extension != null ? extension : "*";
        this.form = form != null ? form : "APZF";
        this.report = report != null ? report : "*";
        this.jobName = jobName != null ? jobName : "*";
        this.daysBack = daysBack > 0 ? daysBack : 60;
    }

    public String favoriteId() { return favoriteId; }
    public String locale()     { return locale; }
    public String extension()  { return extension; }
    public String form()       { return form; }
    public String report()     { return report; }
    public String jobName()    { return jobName; }
    public int daysBack()      { return daysBack; }

    // Not used in MainframeMate but kept for API compat
    public String url()      { return ""; }
    public String user()     { return ""; }
    public String password() { return ""; }
}

