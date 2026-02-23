package de.bund.zrb.mail.infrastructure;

import com.pff.*;
import de.bund.zrb.mail.model.MailFolderRef;
import de.bund.zrb.mail.model.MailMessageContent;
import de.bund.zrb.mail.model.MailMessageHeader;
import de.bund.zrb.mail.port.MailboxReader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads PST/OST mailbox files using java-libpst (com.pff).
 */
public class PstMailboxReader implements MailboxReader {

    private static final Logger LOG = Logger.getLogger(PstMailboxReader.class.getName());

    @Override
    public List<MailFolderRef> listFolders(String mailboxPath) throws Exception {
        List<MailFolderRef> result = new ArrayList<>();
        PSTFile pstFile = new PSTFile(new File(mailboxPath));
        try {
            PSTFolder rootFolder = pstFile.getRootFolder();
            Vector<PSTFolder> subFolders = rootFolder.getSubFolders();
            for (PSTFolder folder : subFolders) {
                String folderPath = "/" + folder.getDisplayName();
                int count = folder.getContentCount();
                result.add(new MailFolderRef(mailboxPath, folderPath, folder.getDisplayName(), count));
            }
        } finally {
            closeSilently(pstFile);
        }
        return result;
    }

    @Override
    public List<MailFolderRef> listSubFolders(String mailboxPath, String folderPath) throws Exception {
        List<MailFolderRef> result = new ArrayList<>();
        PSTFile pstFile = new PSTFile(new File(mailboxPath));
        try {
            PSTFolder folder = navigateToFolder(pstFile, folderPath);
            if (folder == null) {
                return result;
            }
            Vector<PSTFolder> subFolders = folder.getSubFolders();
            for (PSTFolder sub : subFolders) {
                String subPath = folderPath + "/" + sub.getDisplayName();
                int count = sub.getContentCount();
                result.add(new MailFolderRef(mailboxPath, subPath, sub.getDisplayName(), count));
            }
        } finally {
            closeSilently(pstFile);
        }
        return result;
    }

    @Override
    public List<MailMessageHeader> listMessages(String mailboxPath, String folderPath) throws Exception {
        List<MailMessageHeader> result = new ArrayList<>();
        PSTFile pstFile = new PSTFile(new File(mailboxPath));
        try {
            PSTFolder folder = navigateToFolder(pstFile, folderPath);
            if (folder == null) {
                return result;
            }

            PSTMessage message = (PSTMessage) folder.getNextChild();
            while (message != null) {
                try {
                    String subject = message.getSubject();
                    String from = message.getSenderName();
                    String senderEmail = message.getSenderEmailAddress();
                    if (senderEmail != null && !senderEmail.isEmpty() && !senderEmail.equals(from)) {
                        from = from + " <" + senderEmail + ">";
                    }
                    String to = message.getDisplayTo();
                    java.util.Date date = message.getMessageDeliveryTime();
                    boolean hasAttachments = message.hasAttachments();
                    long nodeId = message.getDescriptorNodeId();

                    result.add(new MailMessageHeader(subject, from, to, date, folderPath, nodeId, hasAttachments));
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Error reading message in " + folderPath, e);
                }
                message = (PSTMessage) folder.getNextChild();
            }
        } finally {
            closeSilently(pstFile);
        }
        return result;
    }

    @Override
    public MailMessageContent readMessage(String mailboxPath, String folderPath, long descriptorNodeId) throws Exception {
        PSTFile pstFile = new PSTFile(new File(mailboxPath));
        try {
            PSTFolder folder = navigateToFolder(pstFile, folderPath);
            if (folder == null) {
                throw new Exception("Ordner nicht gefunden: " + folderPath);
            }

            // Iterate to find message by descriptor node ID
            PSTMessage message = (PSTMessage) folder.getNextChild();
            while (message != null) {
                if (message.getDescriptorNodeId() == descriptorNodeId) {
                    return buildContent(message, folderPath);
                }
                message = (PSTMessage) folder.getNextChild();
            }

            throw new Exception("Nachricht nicht gefunden (NodeId: " + descriptorNodeId + ")");
        } finally {
            closeSilently(pstFile);
        }
    }

    private MailMessageContent buildContent(PSTMessage message, String folderPath) throws Exception {
        String subject = message.getSubject();
        String from = message.getSenderName();
        String senderEmail = message.getSenderEmailAddress();
        if (senderEmail != null && !senderEmail.isEmpty() && !senderEmail.equals(from)) {
            from = from + " <" + senderEmail + ">";
        }
        String to = message.getDisplayTo();
        java.util.Date date = message.getMessageDeliveryTime();
        boolean hasAttachments = message.hasAttachments();
        long nodeId = message.getDescriptorNodeId();

        MailMessageHeader header = new MailMessageHeader(subject, from, to, date, folderPath, nodeId, hasAttachments);

        String bodyText = message.getBody();
        String bodyHtml = message.getBodyHTML();

        // Attachment names
        List<String> attachmentNames = new ArrayList<>();
        int numAttachments = message.getNumberOfAttachments();
        for (int i = 0; i < numAttachments; i++) {
            try {
                PSTAttachment attachment = message.getAttachment(i);
                String name = attachment.getLongFilename();
                if (name == null || name.isEmpty()) {
                    name = attachment.getFilename();
                }
                if (name == null || name.isEmpty()) {
                    name = "Anhang " + (i + 1);
                }
                attachmentNames.add(name);
            } catch (Exception e) {
                attachmentNames.add("(Anhang " + (i + 1) + " nicht lesbar)");
            }
        }

        return new MailMessageContent(header, bodyText, bodyHtml, attachmentNames);
    }

    /**
     * Navigate to a folder by path like "/Inbox/Subfolder".
     */
    private PSTFolder navigateToFolder(PSTFile pstFile, String folderPath) throws Exception {
        if (folderPath == null || folderPath.isEmpty() || "/".equals(folderPath)) {
            return pstFile.getRootFolder();
        }

        String[] parts = folderPath.split("/");
        PSTFolder current = pstFile.getRootFolder();

        for (String part : parts) {
            if (part.isEmpty()) continue;
            PSTFolder found = null;
            Vector<PSTFolder> subFolders = current.getSubFolders();
            for (PSTFolder sub : subFolders) {
                if (part.equals(sub.getDisplayName())) {
                    found = sub;
                    break;
                }
            }
            if (found == null) {
                LOG.warning("Folder not found: " + part + " in path " + folderPath);
                return null;
            }
            current = found;
        }
        return current;
    }

    private void closeSilently(PSTFile pstFile) {
        try {
            if (pstFile != null) {
                pstFile.close();
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Error closing PST file", e);
        }
    }
}
