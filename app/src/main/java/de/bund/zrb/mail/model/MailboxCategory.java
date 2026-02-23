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
     * Resolves a ContainerClass string to a category, or null if unknown/system.
     */
    public static MailboxCategory fromContainerClass(String containerClass) {
        if (containerClass == null || containerClass.isEmpty()) {
            return null;
        }
        for (MailboxCategory cat : values()) {
            if (containerClass.startsWith(cat.containerClass)) {
                return cat;
            }
        }
        return null;
    }
}
