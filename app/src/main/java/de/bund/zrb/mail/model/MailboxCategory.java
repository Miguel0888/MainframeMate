package de.bund.zrb.mail.model;

/**
 * Categories for the mailbox entry page.
 * Mapped from Outlook ContainerClass values.
 */
public enum MailboxCategory {

    MAIL("IPF.Note", "📧 E-Mails", "E-Mails"),
    CALENDAR("IPF.Appointment", "📅 Kalender", "Kalender"),
    CONTACTS("IPF.Contact", "👥 Kontakte", "Kontakte"),
    TASKS("IPF.Task", "✅ Aufgaben", "Aufgaben"),
    NOTES("IPF.StickyNote", "📝 Notizen", "Notizen");

    private final String containerClass;
    private final String displayName;
    private final String label;

    MailboxCategory(String containerClass, String displayName, String label) {
        this.containerClass = containerClass;
        this.displayName = displayName;
        this.label = label;
    }

    public String getContainerClass() {
        return containerClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Additional container classes that map to MAIL.
     * Configurable via Settings.mailContainerClasses (comma-separated).
     * Default: IPF.Note, IPF.Imap
     */
    private static volatile String[] mailClasses = {"IPF.Note", "IPF.Imap"};

    /**
     * Updates the MAIL container class mappings from a comma-separated string.
     * Called from Settings initialization.
     * Example: "IPF.Note,IPF.Imap" or "IPF.Note,IPF.Imap,IPF.Imap.Raw"
     */
    public static void setMailContainerClasses(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.trim().isEmpty()) {
            mailClasses = new String[]{"IPF.Note", "IPF.Imap"};
            return;
        }
        String[] parts = commaSeparated.split(",");
        java.util.List<String> cleaned = new java.util.ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) cleaned.add(trimmed);
        }
        mailClasses = cleaned.toArray(new String[0]);
    }

    /**
     * Returns the current MAIL container classes (for display in Settings).
     */
    public static String getMailContainerClassesAsString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mailClasses.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(mailClasses[i]);
        }
        return sb.toString();
    }

    /**
     * Resolves a ContainerClass string to a category, or null if unknown/system.
     *
     * Known mappings:
     *   IPF.Note, IPF.Imap → MAIL
     *   IPF.Appointment    → CALENDAR
     *   IPF.Contact        → CONTACTS
     *   IPF.Task           → TASKS
     *   IPF.StickyNote     → NOTES
     */
    public static MailboxCategory fromContainerClass(String containerClass) {
        if (containerClass == null || containerClass.isEmpty()) {
            return null;
        }
        // Check MAIL aliases (configurable via Settings.mailContainerClasses)
        for (String mailClass : mailClasses) {
            if (containerClass.startsWith(mailClass)) {
                return MAIL;
            }
        }
        // Check other categories
        for (MailboxCategory cat : values()) {
            if (cat != MAIL && containerClass.startsWith(cat.containerClass)) {
                return cat;
            }
        }
        return null;
    }
}
