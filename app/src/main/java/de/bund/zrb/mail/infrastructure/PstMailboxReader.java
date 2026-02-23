package de.bund.zrb.mail.infrastructure;

import com.pff.*;
import de.bund.zrb.mail.model.MailFolderRef;
import de.bund.zrb.mail.model.MailMessageContent;
import de.bund.zrb.mail.model.MailMessageHeader;
import de.bund.zrb.mail.model.MailboxCategory;
import de.bund.zrb.mail.port.MailboxReader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads PST/OST mailbox files using java-libpst (com.pff).
 *
 * Key design decisions:
 * - Uses IPM Subtree as content root (skips system/config folders)
 * - Filters folders by ContainerClass (IPF.Note, IPF.Appointment, etc.)
 * - Skips Search folders (they are virtual and can't be read)
 * - Supports paging for large folders (offset/limit)
 * - Handles Report messages (read receipts etc.) gracefully
 */
public class PstMailboxReader implements MailboxReader {

    private static final Logger LOG = Logger.getLogger(PstMailboxReader.class.getName());

    /** Known container classes for user-visible content folders. */
    private static final String[] KNOWN_CONTAINER_CLASSES = {
            "IPF.Note", "IPF.Appointment", "IPF.Contact", "IPF.Task", "IPF.StickyNote"
    };

    /** Folder names that are known system/internal folders to skip. */
    private static final String[] SYSTEM_FOLDER_NAMES = {
            "Finder", "Views", "Common Views", "Shortcuts", "Schedule",
            "Reminders", "To-Do Search", "Quick Step Settings",
            "Conversation Action Settings", "ExternalContacts",
            "PersonMetadata", "Files", "Yammer Root",
            // Sync problem folders (these are infrastructure, not user content)
            "Synchronisierungsprobleme", "Sync Issues",
            "Lokale Fehler", "Local Failures",
            "Serverfehler", "Server Failures",
            "Konflikte", "Conflicts",
            // Search / virtual folders (java-libpst can't read their tables)
            "SPAM Search Folder 2", "ItemProcSearch",
            "Nachverfolgte E-Mail-Verarbeitung",
            "Tracked Mail Processing"
    };

    /** Substrings in folder names that indicate virtual/search folders. */
    private static final String[] SEARCH_FOLDER_INDICATORS = {
            "Search", "Suche", "search"
    };

    // ─── Interface: listFolders ───

    @Override
    public List<MailFolderRef> listFolders(String mailboxPath) throws Exception {
        List<MailFolderRef> result = new ArrayList<>();
        PSTFile pstFile = new PSTFile(new File(mailboxPath));
        try {
            PSTFolder ipmSubtree = findIpmSubtree(pstFile);
            if (ipmSubtree == null) {
                LOG.warning("IPM Subtree not found, falling back to root folder");
                ipmSubtree = pstFile.getRootFolder();
            }

            collectRelevantFolders(ipmSubtree, "", mailboxPath, result, false);
        } finally {
            closeSilently(pstFile);
        }
        return result;
    }

    // ─── Interface: listSubFolders ───

    @Override
    public List<MailFolderRef> listSubFolders(String mailboxPath, String folderPath) throws Exception {
        List<MailFolderRef> result = new ArrayList<>();
        PSTFile pstFile = new PSTFile(new File(mailboxPath));
        try {
            PSTFolder folder = navigateToFolder(pstFile, folderPath);
            if (folder == null) {
                return result;
            }

            Vector<PSTFolder> subFolders;
            try {
                subFolders = folder.getSubFolders();
            } catch (PSTException e) {
                LOG.log(Level.WARNING, "Cannot list subfolders of " + folderPath
                        + " (possibly a search/virtual folder): " + e.getMessage());
                return result;
            }

            for (PSTFolder sub : subFolders) {
                if (isSystemFolder(sub)) continue;
                try {
                    String subPath = folderPath + "/" + sub.getDisplayName();
                    int count = sub.getContentCount();
                    String containerClass = sub.getContainerClass();
                    int subCount = 0;
                    try { subCount = sub.getSubFolderCount(); } catch (Exception ignored) {}
                    result.add(new MailFolderRef(mailboxPath, subPath, sub.getDisplayName(),
                            count, containerClass, subCount));
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Error reading subfolder metadata in " + folderPath, e);
                }
            }
        } finally {
            closeSilently(pstFile);
        }
        return result;
    }

    // ─── Interface: listFoldersByCategory ───

    @Override
    public List<MailFolderRef> listFoldersByCategory(String mailboxPath, MailboxCategory category) throws Exception {
        List<MailFolderRef> result = new ArrayList<>();
        PSTFile pstFile = new PSTFile(new File(mailboxPath));
        try {
            PSTFolder ipmSubtree = findIpmSubtree(pstFile);
            if (ipmSubtree == null) {
                ipmSubtree = pstFile.getRootFolder();
            }
            collectFoldersByCategory(ipmSubtree, "", mailboxPath, category, result);
        } finally {
            closeSilently(pstFile);
        }
        return result;
    }

    // ─── Interface: listMessages (with paging) ───

    @Override
    public List<MailMessageHeader> listMessages(String mailboxPath, String folderPath,
                                                 int offset, int limit) throws Exception {
        List<MailMessageHeader> result = new ArrayList<>();
        PSTFile pstFile = new PSTFile(new File(mailboxPath));
        try {
            PSTFolder folder = navigateToFolder(pstFile, folderPath);
            if (folder == null) {
                return result;
            }

            int skipped = 0;
            int collected = 0;

            PSTObject child;
            try {
                child = folder.getNextChild();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Error starting child iteration in " + folderPath
                        + " (possibly corrupt table): " + e.getMessage());
                return result;
            }

            while (child != null && collected < limit) {
                if (child instanceof PSTMessage) {
                    if (skipped < offset) {
                        skipped++;
                    } else {
                        PSTMessage message = (PSTMessage) child;
                        try {
                            MailMessageHeader header = extractHeader(message, folderPath);
                            result.add(header);
                            collected++;
                        } catch (Exception e) {
                            LOG.log(Level.WARNING, "Error reading message in " + folderPath, e);
                        }
                    }
                }
                try {
                    child = folder.getNextChild();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Error iterating children in " + folderPath
                            + " after " + collected + " messages: " + e.getMessage());
                    break;
                }
            }
        } finally {
            closeSilently(pstFile);
        }
        return result;
    }

    // ─── Interface: getMessageCount ───

    @Override
    public int getMessageCount(String mailboxPath, String folderPath) throws Exception {
        PSTFile pstFile = new PSTFile(new File(mailboxPath));
        try {
            PSTFolder folder = navigateToFolder(pstFile, folderPath);
            if (folder == null) return 0;
            return folder.getContentCount();
        } finally {
            closeSilently(pstFile);
        }
    }

    // ─── Interface: readMessage ───

    @Override
    public MailMessageContent readMessage(String mailboxPath, String folderPath, long descriptorNodeId) throws Exception {
        PSTFile pstFile = new PSTFile(new File(mailboxPath));
        try {
            PSTFolder folder = navigateToFolder(pstFile, folderPath);
            if (folder == null) {
                throw new Exception("Ordner nicht gefunden: " + folderPath);
            }

            PSTObject child = folder.getNextChild();
            while (child != null) {
                if (child instanceof PSTMessage && child.getDescriptorNodeId() == descriptorNodeId) {
                    return buildContent((PSTMessage) child, folderPath);
                }
                child = folder.getNextChild();
            }

            throw new Exception("Nachricht nicht gefunden (NodeId: " + descriptorNodeId + ")");
        } finally {
            closeSilently(pstFile);
        }
    }

    // ─── IPM Subtree Discovery ───

    /**
     * Finds the IPM Subtree – the root of all user-visible content.
     *
     * OST/PST structures vary. Common patterns:
     *   (A) Root → "Top of Personal Folders" → Posteingang, Entwürfe, ...
     *   (B) Root → "user@email.com" → Posteingang, Entwürfe, ...
     *   (C) Root → Posteingang, Entwürfe, ... (flat)
     *
     * Strategy: Walk root's children. For each non-system child, check if
     * IT or its children contain IPF.Note folders. The folder whose DIRECT
     * children have the most known-class folders wins.
     */
    private PSTFolder findIpmSubtree(PSTFile pstFile) throws Exception {
        PSTFolder root = pstFile.getRootFolder();
        Vector<PSTFolder> topLevel;
        try {
            topLevel = root.getSubFolders();
        } catch (Exception e) {
            LOG.warning("Cannot read root subfolders: " + e.getMessage());
            return root;
        }

        LOG.info("[IPM Discovery] Root has " + topLevel.size() + " top-level folders");

        // Check if root itself has direct content folders (pattern C)
        int rootScore = countKnownChildFolders(root);
        LOG.info("[IPM Discovery] Root direct score: " + rootScore);

        PSTFolder bestCandidate = null;
        int bestScore = 0;

        for (PSTFolder folder : topLevel) {
            String name = folder.getDisplayName();
            if (isSystemFolderName(name) || isSearchFolderName(name)) {
                LOG.fine("[IPM Discovery] Skipping system folder: " + name);
                continue;
            }

            int score = countKnownChildFolders(folder);
            LOG.info("[IPM Discovery] Candidate '" + name + "' score: " + score
                    + " (containerClass: " + folder.getContainerClass() + ")");

            if (score > bestScore) {
                bestScore = score;
                bestCandidate = folder;
            }
        }

        if (bestCandidate != null && bestScore > 0) {
            LOG.info("[IPM Discovery] Selected: '" + bestCandidate.getDisplayName()
                    + "' with score " + bestScore);
            return bestCandidate;
        }

        // If root itself scores well, use it
        if (rootScore > 0) {
            LOG.info("[IPM Discovery] Using root as IPM Subtree (score: " + rootScore + ")");
            return root;
        }

        // Fallback: if only one top-level non-system folder, use it
        List<PSTFolder> nonSystem = new ArrayList<>();
        for (PSTFolder folder : topLevel) {
            if (!isSystemFolderName(folder.getDisplayName()) && !isSearchFolderName(folder.getDisplayName())) {
                nonSystem.add(folder);
            }
        }
        if (nonSystem.size() == 1) {
            LOG.info("[IPM Discovery] Fallback: single non-system folder '"
                    + nonSystem.get(0).getDisplayName() + "'");
            return nonSystem.get(0);
        }

        LOG.warning("[IPM Discovery] No IPM Subtree found, using root");
        return root;
    }

    /**
     * Counts how many direct children of a folder have a known container class.
     */
    private int countKnownChildFolders(PSTFolder folder) {
        try {
            Vector<PSTFolder> children = folder.getSubFolders();
            int count = 0;
            for (PSTFolder child : children) {
                try {
                    String cc = child.getContainerClass();
                    if (isKnownContainerClass(cc)) {
                        count++;
                    }
                } catch (Exception ignored) {}
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    // ─── Folder Collection ───

    /**
     * Collects relevant (non-system, non-search) folders from a parent.
     */
    private void collectRelevantFolders(PSTFolder parent, String parentPath, String mailboxPath,
                                         List<MailFolderRef> result, boolean recursive) {
        Vector<PSTFolder> subFolders;
        try {
            subFolders = parent.getSubFolders();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Cannot list subfolders of " + parentPath + ": " + e.getMessage());
            return;
        }

        for (PSTFolder sub : subFolders) {
            if (isSystemFolder(sub)) continue;

            String name = sub.getDisplayName();
            String path = parentPath.isEmpty() ? "/" + name : parentPath + "/" + name;

            try {
                int count = sub.getContentCount();
                String containerClass = sub.getContainerClass();
                int subCount = 0;
                try { subCount = sub.getSubFolderCount(); } catch (Exception ignored) {}

                // Only add folders with known container classes (or no class but with content)
                if (isKnownContainerClass(containerClass) || (containerClass == null && count > 0)
                        || containerClass == null || containerClass.isEmpty()) {
                    result.add(new MailFolderRef(mailboxPath, path, name, count, containerClass, subCount));
                }
            } catch (Exception e) {
                LOG.log(Level.FINE, "Error reading folder metadata: " + path + ": " + e.getMessage());
            }

            if (recursive) {
                try {
                    collectRelevantFolders(sub, path, mailboxPath, result, true);
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Error recursing into folder: " + path + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Collects all folders recursively that match the given category.
     * Each subfolder access is individually protected against PSTExceptions
     * from corrupt/virtual folder tables.
     */
    private void collectFoldersByCategory(PSTFolder parent, String parentPath, String mailboxPath,
                                           MailboxCategory category, List<MailFolderRef> result) {
        Vector<PSTFolder> subFolders;
        try {
            subFolders = parent.getSubFolders();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Cannot list subfolders of " + parentPath + ": " + e.getMessage());
            return;
        }

        for (PSTFolder sub : subFolders) {
            String name = sub.getDisplayName();
            if (isSystemFolder(sub)) {
                LOG.log(Level.FINE, "[Category " + category + "] Skipping system folder: " + name);
                continue;
            }

            String path = parentPath.isEmpty() ? "/" + name : parentPath + "/" + name;

            try {
                String containerClass = sub.getContainerClass();
                int count = sub.getContentCount();
                int subCount = 0;
                try { subCount = sub.getSubFolderCount(); } catch (Exception ignored) {}

                MailboxCategory folderCat = MailboxCategory.fromContainerClass(containerClass);
                LOG.fine("[Category " + category + "] Folder '" + name + "' containerClass="
                        + containerClass + " → category=" + folderCat + " items=" + count);

                if (folderCat == category) {
                    result.add(new MailFolderRef(mailboxPath, path, name, count, containerClass, subCount));
                }
            } catch (Exception e) {
                LOG.log(Level.FINE, "Error reading folder metadata: " + path + ": " + e.getMessage());
            }

            // Recurse into child – individually protected
            try {
                collectFoldersByCategory(sub, path, mailboxPath, category, result);
            } catch (Exception e) {
                LOG.log(Level.FINE, "Error recursing into folder: " + path + ": " + e.getMessage());
            }
        }
    }

    // ─── Message Extraction ───

    private MailMessageHeader extractHeader(PSTMessage message, String folderPath) {
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
        String messageClass = message.getMessageClass();

        return new MailMessageHeader(subject, from, to, date, folderPath, nodeId, hasAttachments, messageClass);
    }

    private MailMessageContent buildContent(PSTMessage message, String folderPath) throws Exception {
        MailMessageHeader header = extractHeader(message, folderPath);

        String bodyText = null;
        String bodyHtml = null;
        try {
            bodyText = message.getBody();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not read body text", e);
        }
        try {
            bodyHtml = message.getBodyHTML();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not read body HTML", e);
        }

        // Attachment names
        List<String> attachmentNames = new ArrayList<>();
        try {
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
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not read attachments", e);
        }

        return new MailMessageContent(header, bodyText, bodyHtml, attachmentNames);
    }

    // ─── Folder Navigation ───

    /**
     * Navigate to a folder by path like "/Inbox/Subfolder".
     * Uses the IPM Subtree as base.
     */
    private PSTFolder navigateToFolder(PSTFile pstFile, String folderPath) throws Exception {
        PSTFolder base = findIpmSubtree(pstFile);
        if (base == null) {
            base = pstFile.getRootFolder();
        }

        if (folderPath == null || folderPath.isEmpty() || "/".equals(folderPath)) {
            return base;
        }

        String[] parts = folderPath.split("/");
        PSTFolder current = base;

        for (String part : parts) {
            if (part.isEmpty()) continue;
            PSTFolder found = null;
            Vector<PSTFolder> subFolders;
            try {
                subFolders = current.getSubFolders();
            } catch (PSTException e) {
                LOG.warning("Cannot access subfolders while navigating to '" + part
                        + "' in path '" + folderPath + "': " + e.getMessage());
                return null;
            }
            for (PSTFolder sub : subFolders) {
                if (part.equals(sub.getDisplayName())) {
                    found = sub;
                    break;
                }
            }
            if (found == null) {
                LOG.warning("Folder not found: '" + part + "' in path '" + folderPath + "'");
                return null;
            }
            current = found;
        }
        return current;
    }

    // ─── Filtering Helpers ───

    private boolean isSystemFolder(PSTFolder folder) {
        String name = folder.getDisplayName();
        if (isSystemFolderName(name)) return true;

        // Skip search folders (they are virtual – java-libpst can't read their tables)
        if (isSearchFolderName(name)) return true;

        // Skip folders with known-problematic container classes
        String containerClass = folder.getContainerClass();
        if (containerClass != null && containerClass.contains("Outlook.Reminder")) return true;

        return false;
    }

    private boolean isSystemFolderName(String name) {
        if (name == null || name.isEmpty()) return true;
        for (String sysName : SYSTEM_FOLDER_NAMES) {
            if (sysName.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /**
     * Detects search/virtual folders by name patterns.
     * These folders have internal tables that java-libpst often can't parse.
     */
    private boolean isSearchFolderName(String name) {
        if (name == null) return false;
        for (String indicator : SEARCH_FOLDER_INDICATORS) {
            if (name.contains(indicator)) return true;
        }
        return false;
    }

    private boolean isKnownContainerClass(String containerClass) {
        if (containerClass == null || containerClass.isEmpty()) return false;
        for (String known : KNOWN_CONTAINER_CLASSES) {
            if (containerClass.startsWith(known)) return true;
        }
        return false;
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
