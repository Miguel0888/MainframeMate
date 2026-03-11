package de.bund.zrb.betaview.infrastructure;

public final class ResultFilter {

    private final String favoriteId;
    private final String locale;
    private final String extensionPattern;
    private final String form;
    private final String report;
    private final String jobName;
    private final int daysBack;

    // Time period
    private final String lastsel;       // last7days, today, yesterday, individual
    private final String timeunit;      // days, hours, minutes
    private final String lastdate;      // "last" (relative) or "date" (absolute range)
    private final String datefrom;      // DD.MM.YYYY or empty
    private final String timefrom;      // HH:MM or empty
    private final String dateto;        // DD.MM.YYYY or empty
    private final String timeto;        // HH:MM or empty

    // Extended filters
    private final String folder;        // default *
    private final String tab;           // default *
    private final String title;         // default empty
    private final String ftitle;        // 0 or 1
    private final String recipient;     // default *

    // Status filters
    private final String online;        // JA, NEIN, or empty
    private final String lgrnote;       // JA, NEIN, or empty
    private final String lgrxread;      // JA, NEIN, or empty
    private final String archive;       // JA, NEIN, or empty
    private final String process;       // LIST, REPORT, or empty
    private final String delete;        // JA, NEIN, or empty
    private final String lgrstat;       // H, C, N, T, or empty
    private final String reload;        // JA, NEIN, or empty

    private ResultFilter(Builder b) {
        this.favoriteId = defaultIfBlank(b.favoriteId, "A158");
        this.locale = defaultIfBlank(b.locale, "de");
        this.extensionPattern = defaultIfBlank(b.extensionPattern, "*");
        this.form = defaultIfBlank(b.form, "APZC");
        this.report = defaultIfBlank(b.report, "*");
        this.jobName = defaultIfBlank(b.jobName, "*");
        this.daysBack = b.daysBack > 0 ? b.daysBack : 60;

        this.lastsel = defaultIfBlank(b.lastsel, "last7days");
        this.timeunit = defaultIfBlank(b.timeunit, "days");
        this.lastdate = defaultIfBlank(b.lastdate, "last");
        this.datefrom = nullToEmpty(b.datefrom);
        this.timefrom = nullToEmpty(b.timefrom);
        this.dateto = nullToEmpty(b.dateto);
        this.timeto = nullToEmpty(b.timeto);

        this.folder = defaultIfBlank(b.folder, "*");
        this.tab = defaultIfBlank(b.tab, "*");
        this.title = nullToEmpty(b.title);
        this.ftitle = defaultIfBlank(b.ftitle, "0");
        this.recipient = defaultIfBlank(b.recipient, "*");

        this.online = nullToEmpty(b.online);
        this.lgrnote = nullToEmpty(b.lgrnote);
        this.lgrxread = nullToEmpty(b.lgrxread);
        this.archive = nullToEmpty(b.archive);
        this.process = nullToEmpty(b.process);
        this.delete = nullToEmpty(b.delete);
        this.lgrstat = nullToEmpty(b.lgrstat);
        this.reload = nullToEmpty(b.reload);
    }

    /** Legacy constructor for backwards compatibility. */
    public ResultFilter(String favoriteId,
                        String locale,
                        String extensionPattern,
                        String form,
                        String report,
                        String jobName,
                        int daysBack) {
        this(new Builder()
                .favoriteId(favoriteId)
                .locale(locale)
                .extensionPattern(extensionPattern)
                .form(form)
                .report(report)
                .jobName(jobName)
                .daysBack(daysBack));
    }

    // ======== Getters ========

    public String favoriteId()       { return favoriteId; }
    public String locale()           { return locale; }
    public String extensionPattern() { return extensionPattern; }
    public String form()             { return form; }
    public String report()           { return report; }
    public String jobName()          { return jobName; }
    public int    daysBack()         { return daysBack; }

    public String lastsel()          { return lastsel; }
    public String timeunit()         { return timeunit; }
    public String lastdate()         { return lastdate; }
    public String datefrom()         { return datefrom; }
    public String timefrom()         { return timefrom; }
    public String dateto()           { return dateto; }
    public String timeto()           { return timeto; }

    public String folder()           { return folder; }
    public String tab()              { return tab; }
    public String title()            { return title; }
    public String ftitle()           { return ftitle; }
    public String recipient()        { return recipient; }

    public String online()           { return online; }
    public String lgrnote()          { return lgrnote; }
    public String lgrxread()         { return lgrxread; }
    public String archive()          { return archive; }
    public String process()          { return process; }
    public String delete_()          { return delete; }
    public String lgrstat()          { return lgrstat; }
    public String reload()           { return reload; }

    /** Whether SELGEN=yes should be sent (non-default folder/tab/title/recipient). */
    public boolean needsSelgen() {
        return !"*".equals(folder) || !"*".equals(tab)
                || !title.isEmpty() || !"*".equals(recipient);
    }

    // ======== Builder ========

    public static final class Builder {
        private String favoriteId;
        private String locale;
        private String extensionPattern;
        private String form;
        private String report;
        private String jobName;
        private int daysBack = 60;

        private String lastsel;
        private String timeunit;
        private String lastdate;
        private String datefrom;
        private String timefrom;
        private String dateto;
        private String timeto;

        private String folder;
        private String tab;
        private String title;
        private String ftitle;
        private String recipient;

        private String online;
        private String lgrnote;
        private String lgrxread;
        private String archive;
        private String process;
        private String delete;
        private String lgrstat;
        private String reload;

        public Builder favoriteId(String v)       { this.favoriteId = v; return this; }
        public Builder locale(String v)           { this.locale = v; return this; }
        public Builder extensionPattern(String v) { this.extensionPattern = v; return this; }
        public Builder form(String v)             { this.form = v; return this; }
        public Builder report(String v)           { this.report = v; return this; }
        public Builder jobName(String v)          { this.jobName = v; return this; }
        public Builder daysBack(int v)            { this.daysBack = v; return this; }

        public Builder lastsel(String v)          { this.lastsel = v; return this; }
        public Builder timeunit(String v)         { this.timeunit = v; return this; }
        public Builder lastdate(String v)         { this.lastdate = v; return this; }
        public Builder datefrom(String v)         { this.datefrom = v; return this; }
        public Builder timefrom(String v)         { this.timefrom = v; return this; }
        public Builder dateto(String v)           { this.dateto = v; return this; }
        public Builder timeto(String v)           { this.timeto = v; return this; }

        public Builder folder(String v)           { this.folder = v; return this; }
        public Builder tab(String v)              { this.tab = v; return this; }
        public Builder title(String v)            { this.title = v; return this; }
        public Builder ftitle(String v)           { this.ftitle = v; return this; }
        public Builder recipient(String v)        { this.recipient = v; return this; }

        public Builder online(String v)           { this.online = v; return this; }
        public Builder lgrnote(String v)          { this.lgrnote = v; return this; }
        public Builder lgrxread(String v)         { this.lgrxread = v; return this; }
        public Builder archive(String v)          { this.archive = v; return this; }
        public Builder process(String v)          { this.process = v; return this; }
        public Builder delete(String v)           { this.delete = v; return this; }
        public Builder lgrstat(String v)          { this.lgrstat = v; return this; }
        public Builder reload(String v)           { this.reload = v; return this; }

        public ResultFilter build() { return new ResultFilter(this); }
    }

    // ======== Helpers ========

    private static String requireNotBlank(String v, String message) {
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return v.trim();
    }

    private static String defaultIfBlank(String v, String def) {
        if (v == null) return def;
        String t = v.trim();
        return t.isEmpty() ? def : t;
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v.trim();
    }
}
