package de.bund.zrb.mail.model;

/**
 * Reference to a folder inside a mailbox (e.g. Inbox, Sent Items).
 */
public class MailFolderRef {
    private final String mailboxPath;
    private final String folderPath;
    private final String displayName;
    private final int itemCount;

    public MailFolderRef(String mailboxPath, String folderPath, String displayName, int itemCount) {
        this.mailboxPath = mailboxPath;
        this.folderPath = folderPath;
        this.displayName = displayName;
        this.itemCount = itemCount;
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

    @Override
    public String toString() {
        if (itemCount > 0) {
            return "📁 " + displayName + " (" + itemCount + ")";
        }
        return "📁 " + displayName;
    }
}
