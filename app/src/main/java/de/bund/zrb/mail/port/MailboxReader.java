package de.bund.zrb.mail.port;

import de.bund.zrb.mail.model.MailFolderRef;
import de.bund.zrb.mail.model.MailMessageContent;
import de.bund.zrb.mail.model.MailMessageHeader;
import de.bund.zrb.mail.model.MailboxCategory;

import java.util.List;

/**
 * Port: reads structure and content from a single mailbox (OST/PST file).
 */
public interface MailboxReader {

    /**
     * Lists top-level content folders (from IPM Subtree), filtered to known categories.
     * System/search/config folders are excluded.
     */
    List<MailFolderRef> listFolders(String mailboxPath) throws Exception;

    /**
     * Lists sub-folders of the given folder, filtered to relevant container classes.
     */
    List<MailFolderRef> listSubFolders(String mailboxPath, String folderPath) throws Exception;

    /**
     * Lists all content folders recursively that belong to the given category.
     *
     * @param mailboxPath absolute path to the OST/PST file
     * @param category    the category to filter by
     * @return flat list of folders matching the category
     */
    List<MailFolderRef> listFoldersByCategory(String mailboxPath, MailboxCategory category) throws Exception;

    /**
     * Lists message headers in the given folder with paging support.
     *
     * @param mailboxPath absolute path to the OST/PST file
     * @param folderPath  internal folder path
     * @param offset      number of messages to skip (0-based)
     * @param limit       max number of messages to return (use Integer.MAX_VALUE for all)
     * @return list of message headers for the requested page
     */
    List<MailMessageHeader> listMessages(String mailboxPath, String folderPath, int offset, int limit) throws Exception;

    /**
     * Returns the total message count in a folder (without loading them).
     */
    int getMessageCount(String mailboxPath, String folderPath) throws Exception;

    /**
     * Reads full content of a single message.
     */
    MailMessageContent readMessage(String mailboxPath, String folderPath, long descriptorNodeId) throws Exception;
}
