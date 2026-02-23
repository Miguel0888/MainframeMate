package de.bund.zrb.mail.usecase;

import de.bund.zrb.mail.model.MailFolderRef;
import de.bund.zrb.mail.model.MailMessageHeader;
import de.bund.zrb.mail.port.MailboxReader;

import java.util.List;

/**
 * Use case: list folders and messages within a mailbox.
 */
public class ListMailboxItemsUseCase {

    private final MailboxReader reader;

    public ListMailboxItemsUseCase(MailboxReader reader) {
        this.reader = reader;
    }

    public List<MailFolderRef> listTopFolders(String mailboxPath) throws Exception {
        return reader.listFolders(mailboxPath);
    }

    public List<MailFolderRef> listSubFolders(String mailboxPath, String folderPath) throws Exception {
        return reader.listSubFolders(mailboxPath, folderPath);
    }

    public List<MailMessageHeader> listMessages(String mailboxPath, String folderPath) throws Exception {
        return reader.listMessages(mailboxPath, folderPath);
    }
}
