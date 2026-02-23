package de.bund.zrb.mail.usecase;

import de.bund.zrb.mail.model.MailMessageContent;
import de.bund.zrb.mail.port.MailboxReader;

/**
 * Use case: read the full content of a single mail message.
 */
public class OpenMailMessageUseCase {

    private final MailboxReader reader;

    public OpenMailMessageUseCase(MailboxReader reader) {
        this.reader = reader;
    }

    public MailMessageContent execute(String mailboxPath, String folderPath, long descriptorNodeId) throws Exception {
        return reader.readMessage(mailboxPath, folderPath, descriptorNodeId);
    }
}
