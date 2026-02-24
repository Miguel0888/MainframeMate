package de.bund.zrb.mail.model;

/**
 * Reference to a mailbox (OST/PST file on disk).
 */
public class MailboxRef {
    private final String path;
    private final String displayName;

    public MailboxRef(String path, String displayName) {
        this.path = path;
        this.displayName = displayName;
    }

    public String getPath() {
        return path;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
