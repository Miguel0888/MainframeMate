package de.bund.zrb.mail.service;

import de.bund.zrb.mail.model.MailFolderRef;
import de.bund.zrb.mail.model.MailMessageHeader;
import de.bund.zrb.mail.port.MailboxReader;

import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Detects new and changed mails in a PST/OST store by comparing mail metadata
 * against persisted watermarks and fingerprints.
 * <p>
 * Delegates ALL file access to {@link MailboxReader} — does NOT use com.pff directly.
 * This ensures the same robust error handling that PstMailboxReader provides.
 * <p>
 * Delta strategy:
 * <ol>
 *   <li>Read mail headers from the store via MailboxReader</li>
 *   <li>Select candidates via time-based watermarks with overlap window</li>
 *   <li>Compare each candidate's stable key and fingerprint against known state</li>
 *   <li>Report new and changed mails</li>
 * </ol>
 * <p>
 * Does NOT perform indexing — reports results via {@link DeltaResult}.
 */
public class MailDeltaDetector {

    private static final Logger LOG = Logger.getLogger(MailDeltaDetector.class.getName());

    /** Overlap window for watermark comparison (24 hours in milliseconds). */
    static final long OVERLAP_WINDOW_MS = 24L * 60 * 60 * 1000;

    /** Maximum folder recursion depth. */
    private static final int MAX_DEPTH = 15;

    /** Maximum messages to process per folder (safety limit for huge folders). */
    private static final int MAX_MESSAGES_PER_FOLDER = 50000;

    /** The reader that handles all PST/OST file access. */
    private final MailboxReader mailboxReader;

    // ── Persisted state per connection ──
    // Key: connectionId → watermarks and known fingerprints

    /** Max delivery time seen per connection. */
    private final Map<String, Long> maxDeliveryTime = new ConcurrentHashMap<String, Long>();
    /** Max modification time seen per connection. */
    private final Map<String, Long> maxModificationTime = new ConcurrentHashMap<String, Long>();
    /** Known mail fingerprints: connectionId → (mailKey → fingerprint). */
    private final Map<String, Map<String, String>> knownFingerprints =
            new ConcurrentHashMap<String, Map<String, String>>();
    /** Last successful sync time per connection. */
    private final Map<String, Long> lastSyncTime = new ConcurrentHashMap<String, Long>();

    public MailDeltaDetector(MailboxReader mailboxReader) {
        this.mailboxReader = mailboxReader;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Delta result
    // ═══════════════════════════════════════════════════════════════

    public static class DeltaResult {
        public final List<MailCandidate> newMails;
        public final List<MailCandidate> changedMails;
        public final int totalScanned;
        public final int skipped;
        public final int errors;

        public DeltaResult(List<MailCandidate> newMails, List<MailCandidate> changedMails,
                           int totalScanned, int skipped, int errors) {
            this.newMails = Collections.unmodifiableList(newMails);
            this.changedMails = Collections.unmodifiableList(changedMails);
            this.totalScanned = totalScanned;
            this.skipped = skipped;
            this.errors = errors;
        }
    }

    /**
     * A mail identified as new or changed, carrying enough metadata
     * for the index updater to extract content.
     */
    public static class MailCandidate {
        public final String mailKey;
        public final String fingerprint;
        public final String mailboxPath;
        public final String folderPath;
        public final long descriptorNodeId;
        public final String subject;
        public final String sender;
        public final String recipients;
        public final Date deliveryTime;
        public final Date modificationTime;
        public final long size;
        public final String messageClass;
        public final boolean hasAttachments;

        public MailCandidate(String mailKey, String fingerprint,
                             String mailboxPath, String folderPath, long descriptorNodeId,
                             String subject, String sender, String recipients,
                             Date deliveryTime, Date modificationTime,
                             long size, String messageClass, boolean hasAttachments) {
            this.mailKey = mailKey;
            this.fingerprint = fingerprint;
            this.mailboxPath = mailboxPath;
            this.folderPath = folderPath;
            this.descriptorNodeId = descriptorNodeId;
            this.subject = subject;
            this.sender = sender;
            this.recipients = recipients;
            this.deliveryTime = deliveryTime;
            this.modificationTime = modificationTime;
            this.size = size;
            this.messageClass = messageClass;
            this.hasAttachments = hasAttachments;
        }

        /** Build the item path used for indexing: "mailboxPath#folderPath#nodeId". */
        public String toItemPath() {
            return mailboxPath + "#" + folderPath + "#" + descriptorNodeId;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Initial sync
    // ═══════════════════════════════════════════════════════════════

    /**
     * Perform initial full scan of a PST/OST file.
     * Builds the watermarks and fingerprint map from scratch.
     *
     * @return DeltaResult where all mails are "new"
     */
    public DeltaResult initialSync(MailConnection connection) {
        String connId = connection.getConnectionId();
        String mailboxPath = connection.getFilePath();
        LOG.info("[MailDelta] Initial sync: " + mailboxPath);

        // Reset state for this connection
        maxDeliveryTime.put(connId, 0L);
        maxModificationTime.put(connId, 0L);
        Map<String, String> fps = new ConcurrentHashMap<String, String>();
        knownFingerprints.put(connId, fps);

        List<MailCandidate> allMails = new ArrayList<MailCandidate>();
        int[] counters = {0, 0, 0}; // scanned, skipped, errors

        try {
            // List all folders via MailboxReader (robust, handles OST quirks)
            List<MailFolderRef> folders = listAllFoldersRecursive(mailboxPath);
            LOG.info("[MailDelta] Found " + folders.size() + " folders in " + mailboxPath);

            for (MailFolderRef folder : folders) {
                scanFolder(mailboxPath, folder, allMails, counters, false, connId);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[MailDelta] Initial sync error: " + mailboxPath, e);
            counters[2]++;
        }

        // Update watermarks and fingerprints
        long maxDel = 0, maxMod = 0;
        for (MailCandidate mc : allMails) {
            fps.put(mc.mailKey, mc.fingerprint);
            if (mc.deliveryTime != null && mc.deliveryTime.getTime() > maxDel) {
                maxDel = mc.deliveryTime.getTime();
            }
            if (mc.deliveryTime != null && mc.deliveryTime.getTime() > maxMod) {
                maxMod = mc.deliveryTime.getTime();
            }
        }
        maxDeliveryTime.put(connId, maxDel);
        maxModificationTime.put(connId, maxMod);
        lastSyncTime.put(connId, System.currentTimeMillis());

        connection.snapshotFileSignature();

        LOG.info("[MailDelta] Initial sync complete: " + allMails.size() + " mails, "
                + counters[1] + " skipped, " + counters[2] + " errors");

        return new DeltaResult(allMails, Collections.<MailCandidate>emptyList(),
                counters[0], counters[1], counters[2]);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Delta sync
    // ═══════════════════════════════════════════════════════════════

    /**
     * Perform a delta sync — only check candidates that may be new or changed.
     * Uses time-based watermarks with overlap window.
     */
    public DeltaResult deltaSync(MailConnection connection) {
        String connId = connection.getConnectionId();
        String mailboxPath = connection.getFilePath();
        LOG.info("[MailDelta] Delta sync: " + mailboxPath);

        Map<String, String> fps = knownFingerprints.get(connId);
        if (fps == null) {
            // No prior state — fall back to initial sync
            LOG.info("[MailDelta] No prior state, falling back to initial sync");
            return initialSync(connection);
        }

        List<MailCandidate> newMails = new ArrayList<MailCandidate>();
        List<MailCandidate> changedMails = new ArrayList<MailCandidate>();
        int[] counters = {0, 0, 0}; // scanned, skipped, errors

        try {
            List<MailFolderRef> folders = listAllFoldersRecursive(mailboxPath);

            // Collect candidates
            List<MailCandidate> candidates = new ArrayList<MailCandidate>();
            for (MailFolderRef folder : folders) {
                scanFolder(mailboxPath, folder, candidates, counters, true, connId);
            }

            // Compare against known state
            for (MailCandidate mc : candidates) {
                String knownFp = fps.get(mc.mailKey);
                if (knownFp == null) {
                    // Unknown key → new mail
                    newMails.add(mc);
                    fps.put(mc.mailKey, mc.fingerprint);
                } else if (!knownFp.equals(mc.fingerprint)) {
                    // Known key, different fingerprint → changed mail
                    changedMails.add(mc);
                    fps.put(mc.mailKey, mc.fingerprint);
                }
                // else: unchanged, skip
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[MailDelta] Delta sync error: " + mailboxPath, e);
            counters[2]++;
        }

        // Update watermarks with new data
        long maxDel = getOrDefault(maxDeliveryTime, connId, 0L);
        long maxMod = getOrDefault(maxModificationTime, connId, 0L);
        for (MailCandidate mc : newMails) {
            if (mc.deliveryTime != null && mc.deliveryTime.getTime() > maxDel) maxDel = mc.deliveryTime.getTime();
            if (mc.deliveryTime != null && mc.deliveryTime.getTime() > maxMod) maxMod = mc.deliveryTime.getTime();
        }
        for (MailCandidate mc : changedMails) {
            if (mc.deliveryTime != null && mc.deliveryTime.getTime() > maxDel) maxDel = mc.deliveryTime.getTime();
            if (mc.deliveryTime != null && mc.deliveryTime.getTime() > maxMod) maxMod = mc.deliveryTime.getTime();
        }
        maxDeliveryTime.put(connId, maxDel);
        maxModificationTime.put(connId, maxMod);
        lastSyncTime.put(connId, System.currentTimeMillis());

        connection.snapshotFileSignature();

        LOG.info("[MailDelta] Delta sync complete: " + newMails.size() + " new, "
                + changedMails.size() + " changed, " + counters[1] + " skipped, " + counters[2] + " errors");

        return new DeltaResult(newMails, changedMails, counters[0], counters[1], counters[2]);
    }

    // ═══════════════════════════════════════════════════════════════
    //  State access
    // ═══════════════════════════════════════════════════════════════

    /** Returns the last successful sync time for the given connection, or 0. */
    public long getLastSyncTime(String connectionId) {
        return getOrDefault(lastSyncTime, connectionId, 0L);
    }

    /** Returns the number of known mails for the given connection. */
    public int getKnownMailCount(String connectionId) {
        Map<String, String> fps = knownFingerprints.get(connectionId);
        return fps != null ? fps.size() : 0;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Folder enumeration via MailboxReader
    // ═══════════════════════════════════════════════════════════════

    /**
     * Recursively enumerates all folders in the mailbox via MailboxReader.
     * Uses the same robust PST/OST reading that PstMailboxReader provides.
     */
    private List<MailFolderRef> listAllFoldersRecursive(String mailboxPath) throws Exception {
        List<MailFolderRef> allFolders = new ArrayList<MailFolderRef>();

        // Get top-level folders
        List<MailFolderRef> topLevel = mailboxReader.listFolders(mailboxPath);
        for (MailFolderRef folder : topLevel) {
            allFolders.add(folder);
            if (folder.getSubFolderCount() > 0) {
                collectSubFolders(mailboxPath, folder.getFolderPath(), allFolders, 1);
            }
        }

        return allFolders;
    }

    private void collectSubFolders(String mailboxPath, String folderPath,
                                   List<MailFolderRef> result, int depth) {
        if (depth > MAX_DEPTH) return;
        try {
            List<MailFolderRef> subs = mailboxReader.listSubFolders(mailboxPath, folderPath);
            for (MailFolderRef sub : subs) {
                result.add(sub);
                if (sub.getSubFolderCount() > 0) {
                    collectSubFolders(mailboxPath, sub.getFolderPath(), result, depth + 1);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "[MailDelta] Cannot list subfolders of " + folderPath, e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Message scanning via MailboxReader
    // ═══════════════════════════════════════════════════════════════

    /**
     * Scan messages in a single folder via MailboxReader.
     *
     * @param deltaOnly if true, only consider mails within the watermark + overlap window
     */
    private void scanFolder(String mailboxPath, MailFolderRef folder,
                            List<MailCandidate> results, int[] counters,
                            boolean deltaOnly, String connectionId) {
        if (folder.getItemCount() <= 0) return;

        try {
            List<MailMessageHeader> headers = mailboxReader.listMessages(
                    mailboxPath, folder.getFolderPath(), 0, MAX_MESSAGES_PER_FOLDER);

            for (MailMessageHeader header : headers) {
                counters[0]++;
                try {
                    if (shouldSkipMessageClass(header.getMessageClass())) {
                        counters[1]++;
                    } else if (deltaOnly && !isCandidate(header, connectionId)) {
                        counters[1]++;
                    } else {
                        MailCandidate mc = buildCandidate(header, mailboxPath);
                        results.add(mc);
                    }
                } catch (Exception e) {
                    counters[2]++;
                    LOG.log(Level.FINE, "[MailDelta] Error processing header in "
                            + folder.getFolderPath(), e);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "[MailDelta] Error scanning folder "
                    + folder.getFolderPath() + ": " + e.getMessage());
            counters[2]++;
        }
    }

    /**
     * Check whether a message is a delta candidate (within watermark + overlap window).
     */
    private boolean isCandidate(MailMessageHeader header, String connectionId) {
        long deliveryCutoff = getOrDefault(maxDeliveryTime, connectionId, 0L) - OVERLAP_WINDOW_MS;

        Date delivery = header.getDate();
        if (delivery != null && delivery.getTime() >= deliveryCutoff) return true;

        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Key and fingerprint computation
    // ═══════════════════════════════════════════════════════════════

    private MailCandidate buildCandidate(MailMessageHeader header, String mailboxPath) {
        String subject = safe(header.getSubject());
        String sender = safe(header.getFrom());
        String recipients = safe(header.getTo());
        Date deliveryTime = header.getDate();
        long size = header.getMessageSize();
        long nodeId = header.getDescriptorNodeId();
        String messageClass = header.getMessageClass();
        boolean hasAttachments = header.hasAttachments();
        String folderPath = header.getFolderPath();

        // Internet Message-ID (preferred key)
        String messageId = safe(header.getInternetMessageId());

        String mailKey = buildMailKey(messageId, mailboxPath, folderPath, subject, sender, deliveryTime, size);
        String fingerprint = buildFingerprint(mailKey, deliveryTime, subject, size, sender);

        return new MailCandidate(mailKey, fingerprint, mailboxPath, folderPath, nodeId,
                subject, sender, recipients, deliveryTime, deliveryTime,
                size, messageClass, hasAttachments);
    }

    /**
     * Build a stable mail key.
     * Prefer Internet Message-ID if available, otherwise composite fallback.
     */
    static String buildMailKey(String messageId, String mailboxPath, String folderPath,
                               String subject, String sender, Date deliveryTime, long size) {
        if (messageId != null && !messageId.trim().isEmpty()) {
            // Normalize: trim angle brackets, lowercase
            String normalized = messageId.trim();
            if (normalized.startsWith("<")) normalized = normalized.substring(1);
            if (normalized.endsWith(">")) normalized = normalized.substring(0, normalized.length() - 1);
            return "mid:" + normalized.toLowerCase();
        }

        // Fallback: composite key
        StringBuilder sb = new StringBuilder();
        sb.append(safe(mailboxPath)).append('|');
        sb.append(safe(folderPath)).append('|');
        sb.append(normalizeSubject(subject)).append('|');
        sb.append(safe(sender).toLowerCase()).append('|');
        sb.append(deliveryTime != null ? deliveryTime.getTime() : 0).append('|');
        sb.append(size);
        return "comp:" + sha256Short(sb.toString());
    }

    /**
     * Build a fingerprint for change detection.
     * Based on key + delivery time + subject + size.
     */
    static String buildFingerprint(String mailKey, Date deliveryTime,
                                   String subject, long size, String sender) {
        StringBuilder sb = new StringBuilder();
        sb.append(mailKey).append('|');
        sb.append(deliveryTime != null ? deliveryTime.getTime() : 0).append('|');
        sb.append(normalizeSubject(subject)).append('|');
        sb.append(size).append('|');
        sb.append(safe(sender).toLowerCase());
        return sha256Short(sb.toString());
    }

    /**
     * Normalize subject for comparison: trim, lowercase, remove Re:/Fwd: prefixes.
     */
    static String normalizeSubject(String subject) {
        if (subject == null) return "";
        String s = subject.trim().toLowerCase();
        // Remove common prefixes
        while (s.startsWith("re:") || s.startsWith("aw:") || s.startsWith("fwd:")
                || s.startsWith("wg:") || s.startsWith("fw:")) {
            int colon = s.indexOf(':');
            s = s.substring(colon + 1).trim();
        }
        return s;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Filter
    // ═══════════════════════════════════════════════════════════════

    private boolean shouldSkipMessageClass(String msgClass) {
        if (msgClass == null || msgClass.isEmpty()) return false;
        String upper = msgClass.toUpperCase();
        if (upper.startsWith("REPORT.")) return true;
        if (upper.startsWith("IPM.ABCHPERSON")) return true;
        if (upper.equals("IPM.CONTACT")) return true;
        if (upper.startsWith("IPM.CONFIGURATION")) return true;
        if (upper.startsWith("IPM.MICROSOFT.")) return true;
        if (upper.startsWith("IPM.INFOPATH")) return true;
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Utility
    // ═══════════════════════════════════════════════════════════════

    private static String safe(String s) { return s != null ? s : ""; }

    private static long getOrDefault(Map<String, Long> map, String key, long defaultValue) {
        Long v = map.get(key);
        return v != null ? v : defaultValue;
    }

    /**
     * Short SHA-256 hash (first 16 hex chars) for fingerprinting.
     */
    private static String sha256Short(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) {
                sb.append(String.format("%02x", hash[i] & 0xFF));
            }
            return sb.toString();
        } catch (Exception e) {
            // Fallback: hashCode
            return Integer.toHexString(input.hashCode());
        }
    }
}

