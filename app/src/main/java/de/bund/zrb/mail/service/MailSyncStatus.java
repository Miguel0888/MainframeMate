package de.bund.zrb.mail.service;

/**
 * Observable sync status for UI display.
 */
public enum MailSyncStatus {
    /** Index is up-to-date; nothing pending. */
    UP_TO_DATE("Mail-Index aktuell"),
    /** A file-system change was detected; sync is scheduled. */
    CHANGES_DETECTED("Mail-Änderungen erkannt"),
    /** A delta-sync is in progress. */
    SYNCING("Mail-Synchronisierung läuft"),
    /** The last sync encountered an error. */
    ERROR("Mail-Synchronisierung fehlgeschlagen"),
    /** No mail connection configured / active. */
    INACTIVE("Mail-Verbindung inaktiv");

    private final String label;

    MailSyncStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

