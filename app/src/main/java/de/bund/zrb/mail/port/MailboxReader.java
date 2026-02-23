package de.bund.zrb.mail.port;

import de.bund.zrb.mail.model.MailFolderRef;
import de.bund.zrb.mail.model.MailMessageContent;
import de.bund.zrb.mail.model.MailMessageHeader;

import java.util.List;

/**
 * Port: reads structure and content from a single mailbox (OST/PST file).
 */
public interface MailboxReader {

    /**
     * Lists top-level folders in the mailbox.
     *
     * @param mailboxPath absolute path to the OST/PST file
     * @return list of folder references
     */
    List<MailFolderRef> listFolders(String mailboxPath) throws Exception;

    /**
     * Lists sub-folders of the given folder.
     *
     * @param mailboxPath absolute path to the OST/PST file
     * @param folderPath  internal folder path (e.g. "/Inbox")
     * @return list of sub-folder references
     */
    List<MailFolderRef> listSubFolders(String mailboxPath, String folderPath) throws Exception;

    /**
     * Lists message headers in the given folder.
     *
     * @param mailboxPath absolute path to the OST/PST file
     * @param folderPath  internal folder path
     * @return list of message headers
     */
    List<MailMessageHeader> listMessages(String mailboxPath, String folderPath) throws Exception;

    /**
     * Reads full content of a single message.
     *
     * @param mailboxPath      absolute path to the OST/PST file
     * @param folderPath       internal folder path
     * @param descriptorNodeId the message descriptor node ID
     * @return full message content
     */
    MailMessageContent readMessage(String mailboxPath, String folderPath, long descriptorNodeId) throws Exception;
}
