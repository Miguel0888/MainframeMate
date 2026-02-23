package de.bund.zrb.mail.model;

/**
 * Reference to a folder inside a mailbox (e.g. Inbox, Sent Items).
 */
public class MailFolderRef {
    private final String mailboxPath;
    private final String folderPath;
    private final String displayName;
    private final int itemCount;
    private final String containerClass;
    private final int subFolderCount;

    public MailFolderRef(String mailboxPath, String folderPath, String displayName,
                         int itemCount, String containerClass, int subFolderCount) {
        this.mailboxPath = mailboxPath;
        this.folderPath = folderPath;
        this.displayName = displayName;
        this.itemCount = itemCount;
        this.containerClass = containerClass;
        this.subFolderCount = subFolderCount;
    }

    /** Convenience constructor without containerClass/subFolderCount (backward compat). */
    public MailFolderRef(String mailboxPath, String folderPath, String displayName, int itemCount) {
        this(mailboxPath, folderPath, displayName, itemCount, null, 0);
    }

    public String getMailboxPath() {
        return mailboxPath;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getItemCount() {
        return itemCount;
    }

    public String getContainerClass() {
        return containerClass;
    }

    public int getSubFolderCount() {
        return subFolderCount;
    }

    /**
     * Returns the MailboxCategory this folder belongs to (or null for system/unknown).
     */
    public MailboxCategory getCategory() {
        return MailboxCategory.fromContainerClass(containerClass);
    }

    @Override
    public String toString() {
        if (itemCount > 0) {
            return "📁 " + displayName + " (" + itemCount + ")";
        }
        return "📁 " + displayName;
    }
}
