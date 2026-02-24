package de.bund.zrb.mail.model;

/**
 * Reference to a message inside a mailbox folder.
 */
public class MailMessageRef {
    private final String mailboxPath;
    private final String folderPath;
    private final long descriptorNodeId;

    public MailMessageRef(String mailboxPath, String folderPath, long descriptorNodeId) {
        this.mailboxPath = mailboxPath;
        this.folderPath = folderPath;
        this.descriptorNodeId = descriptorNodeId;
    }

    public String getMailboxPath() {
        return mailboxPath;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public long getDescriptorNodeId() {
        return descriptorNodeId;
    }
}
