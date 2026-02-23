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
 * Content Root discovery:
 *   Searches for the IPM_SUBTREE node that contains user content.
 *   Typical structure: Root → "Stamm - Postfach" → IPM_SUBTREE → Posteingang, Sent, ...
 *   All folder paths returned are relative to the content root.
 *
 * ContainerClass mapping:
 *   IPF.Note AND IPF.Imap → MAIL (IMAP accounts use IPF.Imap!)
 *   See MailboxCategory.fromContainerClass() for full mapping.
 */
public class PstMailboxReader implements MailboxReader {

    private static final Logger LOG = Logger.getLogger(PstMailboxReader.class.getName());

    // ─── Content Root Discovery ───

    /**
     * Finds the content root – the IPM_SUBTREE folder that contains user-visible
     * mail folders (Posteingang, Sent, Drafts, etc.).
     *
     * Search strategy:
     * 1. Look for a folder named "IPM_SUBTREE" that has children with content (items > 0)
     * 2. Walk up to 3 levels deep from root
     * 3. If multiple IPM_SUBTREE nodes exist, pick the one with more content children
     * 4. Fallback: root
     */
    private PSTFolder findContentRoot(PSTFile pstFile) {
        PSTFolder root;
        try { root = pstFile.getRootFolder(); }
        catch (Exception e) {
            System.out.println("[MAIL-DIAG] ERROR getting root folder: " + e.getMessage());
            return null;
        }

        System.out.println("[MAIL-DIAG] === Content Root Discovery ===");

        // Search for IPM_SUBTREE with content
        PSTFolder best = null;
        int bestChildCount = -1;

        for (PSTFolder level1 : safeGetSubFolders(root)) {
            String name1 = level1.getDisplayName();
            System.out.println("[MAIL-DIAG]   L1: '" + name1 + "'");

            if ("IPM_SUBTREE".equalsIgnoreCase(name1)) {
                int cc = countContentChildren(level1);
                System.out.println("[MAIL-DIAG]   → IPM_SUBTREE found at L1, content children: " + cc);
                if (cc > bestChildCount) { best = level1; bestChildCount = cc; }
            }

            for (PSTFolder level2 : safeGetSubFolders(level1)) {
                String name2 = level2.getDisplayName();
                if ("IPM_SUBTREE".equalsIgnoreCase(name2)) {
                    int cc = countContentChildren(level2);
                    System.out.println("[MAIL-DIAG]   → IPM_SUBTREE found at L2 under '" + name1 + "', content children: " + cc);
                    if (cc > bestChildCount) { best = level2; bestChildCount = cc; }
                }

                for (PSTFolder level3 : safeGetSubFolders(level2)) {
                    if ("IPM_SUBTREE".equalsIgnoreCase(level3.getDisplayName())) {
                        int cc = countContentChildren(level3);
                        System.out.println("[MAIL-DIAG]   → IPM_SUBTREE found at L3 under '" + name1 + "/" + name2 + "', content children: " + cc);
                        if (cc > bestChildCount) { best = level3; bestChildCount = cc; }
                    }
                }
            }
        }

        if (best != null) {
            System.out.println("[MAIL-DIAG] ✅ Content Root = IPM_SUBTREE with " + bestChildCount + " content children");
            return best;
        }

        System.out.println("[MAIL-DIAG] ⚠ No IPM_SUBTREE with content found, falling back to root");
        return root;
    }

    /** Counts how many direct children of a folder have content (items > 0). */
    private int countContentChildren(PSTFolder folder) {
        int count = 0;
        for (PSTFolder child : safeGetSubFolders(folder)) {
            try {
                if (child.getContentCount() > 0) count++;
            } catch (Exception ignored) {}
        }
        return count;
    }

    // ─── Interface: listFolders ───

    @Override
    public List<MailFolderRef> listFolders(String mailboxPath) throws Exception {
        List<MailFolderRef> result = new ArrayList<>();
        PSTFile pstFile = new PSTFile(new File(mailboxPath));
        try {
            PSTFolder contentRoot = findContentRoot(pstFile);
            if (contentRoot == null) return result;

            // First: dump full tree for diagnosis
            System.out.println("═══════════════════════════════════════════════════════════════");
            System.out.println("[MAIL-DIAG] *** FULL TREE DUMP for: " + mailboxPath);
            System.out.println("═══════════════════════════════════════════════════════════════");
            dumpFolderTree(pstFile.getRootFolder(), "", 0);
            System.out.println("═══════════════════════════════════════════════════════════════");

            // Return direct children of content root
            for (PSTFolder folder : safeGetSubFolders(contentRoot)) {
                String name = folder.getDisplayName();
                String folderPath = "/" + name;
                int count = 0;
                try { count = folder.getContentCount(); } catch (Exception ignored) {}
                String cc = safeGetContainerClass(folder);
                int subCount = safeGetSubFolderCount(folder);
                result.add(new MailFolderRef(mailboxPath, folderPath, name, count, cc, subCount));
            }
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
                System.out.println("[MAIL-DIAG] listSubFolders: folder not found: " + folderPath);
                return result;
            }

            List<PSTFolder> subFolders = safeGetSubFolders(folder);
            System.out.println("[MAIL-DIAG] listSubFolders('" + folderPath + "'): " + subFolders.size() + " children");

            for (PSTFolder sub : subFolders) {
                String name = sub.getDisplayName();
                String subPath = folderPath + "/" + name;
                int count = 0;
                try { count = sub.getContentCount(); } catch (Exception ignored) {}
                String cc = safeGetContainerClass(sub);
                int subCount = safeGetSubFolderCount(sub);
                System.out.println("[MAIL-DIAG]   📁 '" + name + "' cc=" + cc
                        + " items=" + count + " subfolders=" + subCount);
                result.add(new MailFolderRef(mailboxPath, subPath, name, count, cc, subCount));
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
            PSTFolder contentRoot = findContentRoot(pstFile);
            if (contentRoot == null) return result;

            System.out.println("[MAIL-DIAG] listFoldersByCategory(" + category + ") from content root...");
            collectFoldersByCategory(contentRoot, "", mailboxPath, category, result, 0);
            System.out.println("[MAIL-DIAG] listFoldersByCategory(" + category + ") → " + result.size() + " folders found");
            for (MailFolderRef f : result) {
                System.out.println("[MAIL-DIAG]   → '" + f.getFolderPath() + "' (" + f.getItemCount() + " items)");
            }
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
                System.out.println("[MAIL-DIAG] listMessages: folder not found: " + folderPath);
                return result;
            }

            int contentCount = folder.getContentCount();
            System.out.println("[MAIL-DIAG] listMessages('" + folderPath + "', offset=" + offset
                    + ", limit=" + limit + ") contentCount=" + contentCount);

            int skipped = 0;
            int collected = 0;

            PSTObject child = safeGetNextChild(folder);
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

                            if (collected <= 3) {
                                System.out.println("[MAIL-DIAG]   ✉ msgClass='" + message.getMessageClass()
                                        + "' subject='" + safeSubstring(message.getSubject(), 50) + "'");
                            }
                        } catch (Exception e) {
                            System.out.println("[MAIL-DIAG]   ⚠ ERROR reading message: " + e.getMessage());
                        }
                    }
                } else {
                    if (collected == 0) {
                        System.out.println("[MAIL-DIAG]   ⚠ non-PSTMessage: " + child.getClass().getSimpleName());
                    }
                }

                child = safeGetNextChild(folder);
            }

            if (collected > 3) {
                System.out.println("[MAIL-DIAG]   ... (" + collected + " total in page)");
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
            int count = folder.getContentCount();
            System.out.println("[MAIL-DIAG] getMessageCount('" + folderPath + "') = " + count);
            return count;
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
            if (folder == null) throw new Exception("Ordner nicht gefunden: " + folderPath);

            PSTObject child = safeGetNextChild(folder);
            while (child != null) {
                if (child instanceof PSTMessage && child.getDescriptorNodeId() == descriptorNodeId) {
                    return buildContent((PSTMessage) child, folderPath);
                }
                child = safeGetNextChild(folder);
            }
            throw new Exception("Nachricht nicht gefunden (NodeId: " + descriptorNodeId + ")");
        } finally {
            closeSilently(pstFile);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Folder collection (category-aware, recursive)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Recursively collects folders matching the target category.
     * Uses MailboxCategory.fromContainerClass() which now handles IPF.Imap → MAIL.
     */
    private void collectFoldersByCategory(PSTFolder parent, String parentPath, String mailboxPath,
                                           MailboxCategory targetCategory, List<MailFolderRef> result,
                                           int depth) {
        for (PSTFolder sub : safeGetSubFolders(parent)) {
            String name = sub.getDisplayName();
            String path = parentPath.isEmpty() ? "/" + name : parentPath + "/" + name;
            String cc = safeGetContainerClass(sub);
            int count = 0;
            try { count = sub.getContentCount(); } catch (Exception ignored) {}
            int subCount = safeGetSubFolderCount(sub);

            MailboxCategory resolved = MailboxCategory.fromContainerClass(cc);

            System.out.println("[MAIL-DIAG] collect[" + targetCategory + "] d=" + depth
                    + " '" + name + "' cc='" + (cc != null ? cc : "") + "'"
                    + " → " + resolved + " items=" + count);

            if (resolved == targetCategory) {
                result.add(new MailFolderRef(mailboxPath, path, name, count, cc, subCount));
            }

            // Recurse (depth limit for safety)
            if (depth < 10) {
                collectFoldersByCategory(sub, path, mailboxPath, targetCategory, result, depth + 1);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Navigation – relative to content root
    // ═══════════════════════════════════════════════════════════════

    /**
     * Navigates to a folder by path. Paths are relative to the content root (IPM_SUBTREE).
     * Example: "/Posteingang/bolt" → content_root → Posteingang → bolt
     */
    private PSTFolder navigateToFolder(PSTFile pstFile, String folderPath) throws Exception {
        PSTFolder base = findContentRoot(pstFile);
        if (base == null) return null;

        if (folderPath == null || folderPath.isEmpty() || "/".equals(folderPath)) {
            return base;
        }

        String[] parts = folderPath.split("/");
        PSTFolder current = base;

        for (String part : parts) {
            if (part.isEmpty()) continue;
            PSTFolder found = null;
            for (PSTFolder sub : safeGetSubFolders(current)) {
                if (part.equals(sub.getDisplayName())) {
                    found = sub;
                    break;
                }
            }
            if (found == null) {
                System.out.println("[MAIL-DIAG] navigateToFolder: NOT FOUND '" + part + "' in path '" + folderPath + "'");
                System.out.println("[MAIL-DIAG]   available: ");
                for (PSTFolder sub : safeGetSubFolders(current)) {
                    System.out.println("[MAIL-DIAG]     - '" + sub.getDisplayName() + "'");
                }
                return null;
            }
            current = found;
        }
        return current;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Diagnostics: full tree dump
    // ═══════════════════════════════════════════════════════════════

    private void dumpFolderTree(PSTFolder folder, String indent, int depth) {
        String name = folder.getDisplayName();
        String cc = safeGetContainerClass(folder);
        int contentCount = 0;
        try { contentCount = folder.getContentCount(); } catch (Exception ignored) {}
        int subFolderCount = safeGetSubFolderCount(folder);

        System.out.println("[MAIL-DIAG] " + indent + "📁 '" + name + "'"
                + " | cc='" + (cc != null ? cc : "") + "'"
                + " | items=" + contentCount
                + " | subs=" + subFolderCount
                + " | depth=" + depth);

        if (depth < 4) {
            for (PSTFolder sub : safeGetSubFolders(folder)) {
                dumpFolderTree(sub, indent + "  ", depth + 1);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Message extraction
    // ═══════════════════════════════════════════════════════════════

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
        try { bodyText = message.getBody(); } catch (Exception e) { LOG.log(Level.FINE, "No body", e); }
        try { bodyHtml = message.getBodyHTML(); } catch (Exception e) { LOG.log(Level.FINE, "No HTML", e); }

        List<String> attachmentNames = new ArrayList<>();
        try {
            int n = message.getNumberOfAttachments();
            for (int i = 0; i < n; i++) {
                try {
                    PSTAttachment att = message.getAttachment(i);
                    String nm = att.getLongFilename();
                    if (nm == null || nm.isEmpty()) nm = att.getFilename();
                    if (nm == null || nm.isEmpty()) nm = "Anhang " + (i + 1);
                    attachmentNames.add(nm);
                } catch (Exception e) {
                    attachmentNames.add("(Anhang " + (i + 1) + " nicht lesbar)");
                }
            }
        } catch (Exception ignored) {}

        return new MailMessageContent(header, bodyText, bodyHtml, attachmentNames);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Safe helpers (exception-proof access)
    // ═══════════════════════════════════════════════════════════════

    private List<PSTFolder> safeGetSubFolders(PSTFolder folder) {
        try {
            Vector<PSTFolder> v = folder.getSubFolders();
            return v != null ? v : new ArrayList<>();
        } catch (Exception e) {
            System.out.println("[MAIL-DIAG] ⛔ getSubFolders('" + folder.getDisplayName() + "'): " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private PSTObject safeGetNextChild(PSTFolder folder) {
        try { return folder.getNextChild(); }
        catch (Exception e) {
            System.out.println("[MAIL-DIAG] ⛔ getNextChild(): " + e.getMessage());
            return null;
        }
    }

    private String safeGetContainerClass(PSTFolder folder) {
        try { return folder.getContainerClass(); }
        catch (Exception e) { return null; }
    }

    private int safeGetSubFolderCount(PSTFolder folder) {
        try { return folder.getSubFolderCount(); }
        catch (Exception e) { return -1; }
    }

    private String safeSubstring(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private void closeSilently(PSTFile pstFile) {
        try { if (pstFile != null) pstFile.close(); }
        catch (Exception e) { LOG.log(Level.FINE, "Error closing PST", e); }
    }
}
