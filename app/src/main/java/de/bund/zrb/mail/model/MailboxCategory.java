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

    /** Additional container classes that map to MAIL (beyond IPF.Note). */
    private static final String[] MAIL_CLASSES = {
            "IPF.Note", "IPF.Imap"
    };

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
        // Check MAIL aliases first (IPF.Note AND IPF.Imap)
        for (String mailClass : MAIL_CLASSES) {
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
