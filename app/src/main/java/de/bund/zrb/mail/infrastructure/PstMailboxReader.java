package de.bund.zrb.mail.infrastructure;

import com.pff.*;
import de.bund.zrb.mail.model.MailFolderRef;
import de.bund.zrb.mail.model.MailMessageContent;
import de.bund.zrb.mail.model.MailMessageHeader;
import de.bund.zrb.mail.model.MailboxCategory;
import de.bund.zrb.mail.port.MailboxReader;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads PST/OST mailbox files using java-libpst (com.pff).
 *
 * Implements AR-01..AR-07:
 * - AR-01: Content-root discovery via explicit IPM_SUBTREE, wrapper unwrap, heuristic
 * - AR-02: All folder paths are relative to the content root (no technical segments)
 * - AR-03: System folder filter with normalized name matching (suffixes, case-insensitive)
 * - AR-04: Virtual/search folders are silently skipped; parser exceptions per-folder
 * - AR-05: Category assignment via ContainerClass → inheritance → name fallback → content probe
 * - AR-06: Item listing accepts all PSTObject children (not just PSTMessage)
 * - AR-07: Paging never shows "load more" as sole element
 */
public class PstMailboxReader implements MailboxReader {

    private static final Logger LOG = Logger.getLogger(PstMailboxReader.class.getName());

    // ─── AR-03: System folder base names (DE + EN). Compared after normalization. ───

    private static final Set<String> SYSTEM_BASE_NAMES = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    static {
        Collections.addAll(SYSTEM_BASE_NAMES,
                // Infrastructure
                "Synchronisierungsprobleme", "Sync Issues",
                "Lokale Fehler", "Local Failures",
                "Serverfehler", "Server Failures",
                "Konflikte", "Conflicts",
                // Outlook internal
                "Finder", "Views", "Common Views", "Shortcuts", "Schedule",
                "Reminders", "To-Do Search", "Quick Step Settings",
                "Conversation Action Settings", "ExternalContacts",
                "PersonMetadata", "Files", "Yammer Root",
                // Virtual / search folders
                "SPAM Search Folder 2", "ItemProcSearch",
                "Nachverfolgte E-Mail-Verarbeitung", "Tracked Mail Processing",
                // Wrapper nodes (not content themselves)
                "IPM_SUBTREE"
        );
    }

    /** Suffixes to strip before name matching (DE + EN). */
    private static final String[] STRIP_SUFFIXES = {
            " (Nur dieser Computer)", " (This computer only)"
    };

    // ─── AR-05 fallback: well-known standard folder names → category ───

    private static final Map<String, MailboxCategory> WELL_KNOWN_NAMES = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    static {
        // MAIL
        for (String n : new String[]{
                "Posteingang", "Inbox",
                "Gesendete Elemente", "Sent Items", "Sent",
                "Entwürfe", "Drafts",
                "Gelöschte Elemente", "Deleted Items", "Trash",
                "Junk-E-Mail", "Junk Email", "Junk", "Spam",
                "Postausgang", "Outbox",
                "Archiv", "Archive",
                "RSS-Feeds", "RSS Feeds"}) {
            WELL_KNOWN_NAMES.put(n, MailboxCategory.MAIL);
        }
        // CALENDAR
        for (String n : new String[]{"Kalender", "Calendar"}) {
            WELL_KNOWN_NAMES.put(n, MailboxCategory.CALENDAR);
        }
        // CONTACTS
        for (String n : new String[]{"Kontakte", "Contacts", "Vorgeschlagene Kontakte", "Suggested Contacts"}) {
            WELL_KNOWN_NAMES.put(n, MailboxCategory.CONTACTS);
        }
        // TASKS
        for (String n : new String[]{"Aufgaben", "Tasks", "To-Do"}) {
            WELL_KNOWN_NAMES.put(n, MailboxCategory.TASKS);
        }
        // NOTES
        for (String n : new String[]{"Notizen", "Notes", "Journal"}) {
            WELL_KNOWN_NAMES.put(n, MailboxCategory.NOTES);
        }
    }

    /** Content-probe: MessageClass prefix → category. */
    private static final String[][] MSG_CLASS_MAP = {
            {"IPM.Note", "MAIL"},
            {"IPM.Appointment", "CALENDAR"},
            {"IPM.Schedule", "CALENDAR"},
            {"IPM.Contact", "CONTACTS"},
            {"IPM.DistList", "CONTACTS"},
            {"IPM.Task", "TASKS"},
            {"IPM.StickyNote", "NOTES"},
            {"IPM.Activity", "NOTES"},   // Journal
    };
    private static final int CONTENT_PROBE_LIMIT = 50;

    // ────────────────────────────────────────────────────────────────────
    // Interface: listFolders  (AR-01, AR-02, AR-03)
    // ────────────────────────────────────────────────────────────────────

    @Override
    public List<MailFolderRef> listFolders(String mailboxPath) throws Exception {
        List<MailFolderRef> result = new ArrayList<>();
        PSTFile pstFile = new PSTFile(new File(mailboxPath));
        try {
            PSTFolder contentRoot = findContentRoot(pstFile);
            collectAllUserFolders(contentRoot, "", mailboxPath, result, false);
        } finally {
            closeSilently(pstFile);
        }
        return result;
    }

    // ────────────────────────────────────────────────────────────────────
    // Interface: listSubFolders
    // ────────────────────────────────────────────────────────────────────

    @Override
    public List<MailFolderRef> listSubFolders(String mailboxPath, String folderPath) throws Exception {
        List<MailFolderRef> result = new ArrayList<>();
        PSTFile pstFile = new PSTFile(new File(mailboxPath));
        try {
            PSTFolder folder = navigateToFolder(pstFile, folderPath);
            if (folder == null) return result;

            for (PSTFolder sub : safeGetSubFolders(folder)) {
                if (isSystemFolder(sub)) continue;
                try {
                    String subPath = folderPath + "/" + sub.getDisplayName();
                    result.add(buildFolderRef(mailboxPath, subPath, sub));
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Error reading subfolder in " + folderPath, e);
                }
            }
        } finally {
            closeSilently(pstFile);
        }
        return result;
    }

    // ────────────────────────────────────────────────────────────────────
    // Interface: listFoldersByCategory  (AR-05)
    // ────────────────────────────────────────────────────────────────────

    @Override
    public List<MailFolderRef> listFoldersByCategory(String mailboxPath, MailboxCategory category) throws Exception {
        List<MailFolderRef> result = new ArrayList<>();
        PSTFile pstFile = new PSTFile(new File(mailboxPath));
        try {
            PSTFolder contentRoot = findContentRoot(pstFile);
            collectFoldersByCategory(contentRoot, "", mailboxPath, category, null, result);
        } finally {
            closeSilently(pstFile);
        }
        return result;
    }

    // ────────────────────────────────────────────────────────────────────
    // Interface: listMessages  (AR-06, AR-07)
    // ────────────────────────────────────────────────────────────────────

    @Override
    public List<MailMessageHeader> listMessages(String mailboxPath, String folderPath,
                                                 int offset, int limit) throws Exception {
        List<MailMessageHeader> result = new ArrayList<>();
        PSTFile pstFile = new PSTFile(new File(mailboxPath));
        try {
            PSTFolder folder = navigateToFolder(pstFile, folderPath);
            if (folder == null) return result;

            int skipped = 0;
            int collected = 0;
            PSTObject child = safeGetNextChild(folder);

            while (child != null && collected < limit) {
                // AR-06: accept ANY PSTObject that carries message-like data
                if (child instanceof PSTMessage) {
                    if (skipped < offset) {
                        skipped++;
                    } else {
                        try {
                            result.add(extractHeader((PSTMessage) child, folderPath));
                            collected++;
                        } catch (Exception e) {
                            LOG.log(Level.FINE, "Error reading item in " + folderPath, e);
                        }
                    }
                }
                child = safeGetNextChild(folder);
            }
        } finally {
            closeSilently(pstFile);
        }
        return result;
    }

    // ────────────────────────────────────────────────────────────────────
    // Interface: getMessageCount
    // ────────────────────────────────────────────────────────────────────

    @Override
    public int getMessageCount(String mailboxPath, String folderPath) throws Exception {
        PSTFile pstFile = new PSTFile(new File(mailboxPath));
        try {
            PSTFolder folder = navigateToFolder(pstFile, folderPath);
            return folder != null ? folder.getContentCount() : 0;
        } finally {
            closeSilently(pstFile);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Interface: readMessage
    // ────────────────────────────────────────────────────────────────────

    @Override
    public MailMessageContent readMessage(String mailboxPath, String folderPath,
                                           long descriptorNodeId) throws Exception {
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

    // ═══════════════════════════════════════════════════════════════════
    //  AR-01  Content Root Discovery
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Finds the content root – the folder whose direct children are the
     * user-visible top-level folders (Posteingang, Kalender, …).
     *
     * Priority (AR-01):
     * 1. Explicit IPM_SUBTREE node (anywhere in top 2 levels)
     * 2. Wrapper with single non-system child that IS the IPM_SUBTREE
     * 3. Heuristic: folder whose children have the most known ContainerClasses
     * 4. Fallback: root
     */
    private PSTFolder findContentRoot(PSTFile pstFile) throws Exception {
        PSTFolder root = pstFile.getRootFolder();

        // --- Priority 1: explicit IPM_SUBTREE ---
        PSTFolder ipmExplicit = findExplicitIpmSubtree(root, 0, 3);
        if (ipmExplicit != null) {
            LOG.info("[ContentRoot] Found explicit IPM_SUBTREE");
            return ipmExplicit;
        }

        // --- Priority 3: heuristic by child score ---
        List<PSTFolder> topLevel = safeGetSubFolders(root);
        PSTFolder best = null;
        int bestScore = 0;

        // Also check root itself
        int rootScore = countKnownChildFolders(root);

        for (PSTFolder folder : topLevel) {
            if (isSystemFolder(folder)) continue;

            int score = countKnownChildFolders(folder);
            LOG.fine("[ContentRoot] '" + folder.getDisplayName() + "' score=" + score);

            if (score > bestScore) {
                bestScore = score;
                best = folder;
            }

            // Priority 2: wrapper with single meaningful child
            if (score == 0) {
                List<PSTFolder> wrapperChildren = safeGetSubFolders(folder);
                List<PSTFolder> meaningful = new ArrayList<>();
                for (PSTFolder wc : wrapperChildren) {
                    if (!isSystemFolder(wc)) meaningful.add(wc);
                }
                if (meaningful.size() == 1) {
                    int innerScore = countKnownChildFolders(meaningful.get(0));
                    if (innerScore > bestScore) {
                        bestScore = innerScore;
                        best = meaningful.get(0);
                        LOG.info("[ContentRoot] Unwrapped via '" + folder.getDisplayName()
                                + "' → '" + best.getDisplayName() + "' score=" + innerScore);
                    }
                }
            }
        }

        if (best != null && bestScore > 0) {
            LOG.info("[ContentRoot] Selected '" + best.getDisplayName() + "' score=" + bestScore);
            return best;
        }

        if (rootScore > 0) {
            LOG.info("[ContentRoot] Using root directly, score=" + rootScore);
            return root;
        }

        // Fallback: single non-system top-level folder
        List<PSTFolder> nonSys = new ArrayList<>();
        for (PSTFolder f : topLevel) { if (!isSystemFolder(f)) nonSys.add(f); }
        if (nonSys.size() == 1) {
            LOG.info("[ContentRoot] Fallback single: '" + nonSys.get(0).getDisplayName() + "'");
            return nonSys.get(0);
        }

        LOG.info("[ContentRoot] Fallback to root");
        return root;
    }

    /**
     * Searches up to {@code maxDepth} levels for a folder named "IPM_SUBTREE" (case-insensitive).
     */
    private PSTFolder findExplicitIpmSubtree(PSTFolder parent, int depth, int maxDepth) {
        if (depth >= maxDepth) return null;
        for (PSTFolder child : safeGetSubFolders(parent)) {
            String name = child.getDisplayName();
            if (name != null && name.equalsIgnoreCase("IPM_SUBTREE")) {
                return child;
            }
            PSTFolder found = findExplicitIpmSubtree(child, depth + 1, maxDepth);
            if (found != null) return found;
        }
        return null;
    }

    private int countKnownChildFolders(PSTFolder folder) {
        int count = 0;
        for (PSTFolder child : safeGetSubFolders(folder)) {
            try {
                String cc = child.getContainerClass();
                if (cc != null && !cc.isEmpty() && MailboxCategory.fromContainerClass(cc) != null) {
                    count++;
                }
            } catch (Exception ignored) {}
        }
        return count;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  AR-05  Category-aware folder collection
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Recursively collects all user-visible folders matching {@code category}.
     * Uses the four-level category resolution: ContainerClass → inherit → name → probe.
     *
     * @param parentCategory the category of the parent (for inheritance), may be null
     */
    private void collectFoldersByCategory(PSTFolder parent, String parentPath, String mailboxPath,
                                           MailboxCategory targetCategory, MailboxCategory parentCategory,
                                           List<MailFolderRef> result) {
        for (PSTFolder sub : safeGetSubFolders(parent)) {
            String name = sub.getDisplayName();
            if (isSystemFolder(sub)) continue;

            String path = parentPath.isEmpty() ? "/" + name : parentPath + "/" + name;
            MailboxCategory resolved = null;

            try {
                resolved = resolveCategory(sub, parentCategory);

                if (resolved == targetCategory) {
                    result.add(buildFolderRef(mailboxPath, path, sub));
                }
            } catch (Exception e) {
                LOG.log(Level.FINE, "Error reading folder '" + path + "': " + e.getMessage());
            }

            // Recurse – pass resolved category for inheritance
            try {
                collectFoldersByCategory(sub, path, mailboxPath, targetCategory,
                        resolved != null ? resolved : parentCategory, result);
            } catch (Exception e) {
                LOG.log(Level.FINE, "Error recursing '" + path + "': " + e.getMessage());
            }
        }
    }

    /**
     * Collects ALL user-visible folders (for listFolders).
     */
    private void collectAllUserFolders(PSTFolder parent, String parentPath, String mailboxPath,
                                        List<MailFolderRef> result, boolean recursive) {
        for (PSTFolder sub : safeGetSubFolders(parent)) {
            if (isSystemFolder(sub)) continue;
            String name = sub.getDisplayName();
            String path = parentPath.isEmpty() ? "/" + name : parentPath + "/" + name;
            try {
                result.add(buildFolderRef(mailboxPath, path, sub));
            } catch (Exception e) {
                LOG.log(Level.FINE, "Error reading folder '" + path + "'", e);
            }
            if (recursive) {
                collectAllUserFolders(sub, path, mailboxPath, result, true);
            }
        }
    }

    /**
     * AR-05: Resolves category for a folder with four-level fallback:
     * 1. ContainerClass
     * 2. Inherit from parent
     * 3. Well-known name
     * 4. Content probe (sample first N items)
     */
    private MailboxCategory resolveCategory(PSTFolder folder, MailboxCategory parentCategory) {
        // Level 1: ContainerClass
        String cc = null;
        try { cc = folder.getContainerClass(); } catch (Exception ignored) {}
        if (cc != null && !cc.isEmpty()) {
            MailboxCategory fromCC = MailboxCategory.fromContainerClass(cc);
            if (fromCC != null) return fromCC;
        }

        // Level 2: Inherit from parent
        if (parentCategory != null) {
            return parentCategory;
        }

        // Level 3: Well-known name
        String name = folder.getDisplayName();
        if (name != null) {
            String normalized = normalizeFolderName(name);
            MailboxCategory fromName = WELL_KNOWN_NAMES.get(normalized);
            if (fromName != null) return fromName;
        }

        // Level 4: Content probe
        return probeContentCategory(folder);
    }

    /**
     * Samples up to CONTENT_PROBE_LIMIT items and determines category by MessageClass majority.
     * Returns null if no items or indeterminate.
     */
    private MailboxCategory probeContentCategory(PSTFolder folder) {
        try {
            if (folder.getContentCount() == 0) return null;
        } catch (Exception e) { return null; }

        Map<MailboxCategory, Integer> votes = new EnumMap<>(MailboxCategory.class);
        int probed = 0;

        try {
            PSTObject child = folder.getNextChild();
            while (child != null && probed < CONTENT_PROBE_LIMIT) {
                if (child instanceof PSTMessage) {
                    String mc = ((PSTMessage) child).getMessageClass();
                    if (mc != null) {
                        MailboxCategory cat = categoryFromMessageClass(mc);
                        if (cat != null) {
                            votes.merge(cat, 1, Integer::sum);
                        }
                    }
                    probed++;
                }
                try { child = folder.getNextChild(); }
                catch (Exception e) { break; }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Content probe failed: " + e.getMessage());
        }

        if (votes.isEmpty()) return null;

        // Majority wins; tie → MAIL
        MailboxCategory best = MailboxCategory.MAIL;
        int bestCount = 0;
        for (Map.Entry<MailboxCategory, Integer> entry : votes.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    private static MailboxCategory categoryFromMessageClass(String messageClass) {
        if (messageClass == null) return null;
        String upper = messageClass.toUpperCase(Locale.ROOT);
        for (String[] mapping : MSG_CLASS_MAP) {
            if (upper.startsWith(mapping[0].toUpperCase(Locale.ROOT))) {
                return MailboxCategory.valueOf(mapping[1]);
            }
        }
        // Reports are mail-like
        if (upper.startsWith("REPORT.")) return MailboxCategory.MAIL;
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Message extraction
    // ═══════════════════════════════════════════════════════════════════

    private MailMessageHeader extractHeader(PSTMessage message, String folderPath) {
        String subject = message.getSubject();
        String from = message.getSenderName();
        String senderEmail = message.getSenderEmailAddress();
        if (senderEmail != null && !senderEmail.isEmpty() && !senderEmail.equals(from)) {
            from = from + " <" + senderEmail + ">";
        }
        String to = message.getDisplayTo();
        Date date = message.getMessageDeliveryTime();
        boolean hasAttachments = message.hasAttachments();
        long nodeId = message.getDescriptorNodeId();
        String messageClass = message.getMessageClass();
        return new MailMessageHeader(subject, from, to, date, folderPath, nodeId, hasAttachments, messageClass);
    }

    private MailMessageContent buildContent(PSTMessage message, String folderPath) throws Exception {
        MailMessageHeader header = extractHeader(message, folderPath);

        String bodyText = null;
        String bodyHtml = null;
        try { bodyText = message.getBody(); } catch (Exception e) { LOG.log(Level.FINE, "No body text", e); }
        try { bodyHtml = message.getBodyHTML(); } catch (Exception e) { LOG.log(Level.FINE, "No body HTML", e); }

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
        } catch (Exception e) { LOG.log(Level.FINE, "Cannot read attachments", e); }

        return new MailMessageContent(header, bodyText, bodyHtml, attachmentNames);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Folder navigation  (AR-02: paths are relative to content root)
    // ═══════════════════════════════════════════════════════════════════

    private PSTFolder navigateToFolder(PSTFile pstFile, String folderPath) throws Exception {
        PSTFolder base = findContentRoot(pstFile);

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
                LOG.warning("Folder not found: '" + part + "' in path '" + folderPath + "'");
                return null;
            }
            current = found;
        }
        return current;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  AR-03 / AR-04  System folder & search folder filtering
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks if a folder should be hidden from the user.
     */
    private boolean isSystemFolder(PSTFolder folder) {
        String name = folder.getDisplayName();
        if (name == null || name.trim().isEmpty()) return true;

        // Normalize and check against known system base names
        String normalized = normalizeFolderName(name);
        if (SYSTEM_BASE_NAMES.contains(normalized)) return true;

        // Prefix check: any system name that is a prefix of the normalized name
        for (String sysName : SYSTEM_BASE_NAMES) {
            if (normalized.toLowerCase(Locale.ROOT).startsWith(sysName.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        // Container class check
        try {
            String cc = folder.getContainerClass();
            if (cc != null && cc.contains("Outlook.Reminder")) return true;
        } catch (Exception ignored) {}

        return false;
    }

    /**
     * AR-03: Normalizes a folder name by stripping known suffixes and trimming.
     */
    private static String normalizeFolderName(String name) {
        if (name == null) return "";
        String result = name.trim();
        for (String suffix : STRIP_SUFFIXES) {
            if (result.endsWith(suffix)) {
                result = result.substring(0, result.length() - suffix.length()).trim();
            }
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Safe helpers (AR-04: per-folder exception handling)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Safely gets subfolders. Returns empty list on parser failure.
     */
    private List<PSTFolder> safeGetSubFolders(PSTFolder folder) {
        try {
            Vector<PSTFolder> v = folder.getSubFolders();
            return v != null ? v : Collections.emptyList();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Cannot read subfolders of '" + folder.getDisplayName() + "': " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Safely gets next child. Returns null on parser failure (stops iteration).
     */
    private PSTObject safeGetNextChild(PSTFolder folder) {
        try {
            return folder.getNextChild();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Error iterating children: " + e.getMessage());
            return null;
        }
    }

    private MailFolderRef buildFolderRef(String mailboxPath, String path, PSTFolder folder) {
        int count = 0;
        String containerClass = null;
        int subCount = 0;
        try { count = folder.getContentCount(); } catch (Exception ignored) {}
        try { containerClass = folder.getContainerClass(); } catch (Exception ignored) {}
        try { subCount = folder.getSubFolderCount(); } catch (Exception ignored) {}
        return new MailFolderRef(mailboxPath, path, folder.getDisplayName(), count, containerClass, subCount);
    }

    private void closeSilently(PSTFile pstFile) {
        try { if (pstFile != null) pstFile.close(); }
        catch (Exception e) { LOG.log(Level.FINE, "Error closing PST", e); }
    }
}
