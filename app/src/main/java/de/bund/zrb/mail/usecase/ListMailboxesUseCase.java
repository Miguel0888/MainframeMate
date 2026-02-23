package de.bund.zrb.mail.usecase;

import de.bund.zrb.mail.model.MailboxRef;
import de.bund.zrb.mail.port.MailStore;

import java.util.List;

/**
 * Use case: list all mailboxes (OST/PST files) in a configured directory.
 */
public class ListMailboxesUseCase {

    private final MailStore mailStore;

    public ListMailboxesUseCase(MailStore mailStore) {
        this.mailStore = mailStore;
    }

    public List<MailboxRef> execute(String directoryPath) {
        return mailStore.listMailboxes(directoryPath);
    }
}
