package de.bund.zrb.mail.port;

import de.bund.zrb.mail.model.MailboxRef;

import java.util.List;

/**
 * Port: lists available mailboxes (OST/PST files) in a directory.
 */
public interface MailStore {

    /**
     * Scans the given directory for .ost and .pst files.
     *
     * @param directoryPath path to the mail store directory
     * @return list of discovered mailbox references
     */
    List<MailboxRef> listMailboxes(String directoryPath);
}
