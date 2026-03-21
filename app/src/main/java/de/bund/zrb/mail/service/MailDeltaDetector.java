package de.bund.zrb.mail.service;

import com.pff.*;

import java.io.File;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Detects new and changed mails in a PST/OST store by comparing mail metadata
 * against persisted watermarks and fingerprints.
 * <p>
 * Delta strategy:
 * <ol>
 *   <li>Read mail metadata from the store (subject, sender, date, size, nodeId)</li>
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
        LOG.info("[MailDelta] Initial sync: " + connection.getFilePath());

        // Reset state for this connection
        maxDeliveryTime.put(connId, 0L);
        maxModificationTime.put(connId, 0L);
        Map<String, String> fps = new ConcurrentHashMap<String, String>();
        knownFingerprints.put(connId, fps);

        List<MailCandidate> allMails = new ArrayList<MailCandidate>();
        int[] counters = {0, 0, 0}; // scanned, skipped, errors

        PSTFile pstFile = null;
        try {
            pstFile = new PSTFile(new File(connection.getFilePath()));
            PSTFolder contentRoot = findContentRoot(pstFile);
            scanFolder(contentRoot, "", connection, allMails, counters, 0, false);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[MailDelta] Initial sync error: " + connection.getFilePath(), e);
            counters[2]++;
        } finally {
            closeSilently(pstFile);
        }

        // Update watermarks and fingerprints
        long maxDel = 0, maxMod = 0;
        for (MailCandidate mc : allMails) {
            fps.put(mc.mailKey, mc.fingerprint);
            if (mc.deliveryTime != null && mc.deliveryTime.getTime() > maxDel) {
                maxDel = mc.deliveryTime.getTime();
            }
            if (mc.modificationTime != null && mc.modificationTime.getTime() > maxMod) {
                maxMod = mc.modificationTime.getTime();
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
        LOG.info("[MailDelta] Delta sync: " + connection.getFilePath());

        Map<String, String> fps = knownFingerprints.get(connId);
        if (fps == null) {
            // No prior state — fall back to initial sync
            LOG.info("[MailDelta] No prior state, falling back to initial sync");
            return initialSync(connection);
        }

        List<MailCandidate> newMails = new ArrayList<MailCandidate>();
        List<MailCandidate> changedMails = new ArrayList<MailCandidate>();
        int[] counters = {0, 0, 0}; // scanned, skipped, errors

        PSTFile pstFile = null;
        try {
            pstFile = new PSTFile(new File(connection.getFilePath()));
            PSTFolder contentRoot = findContentRoot(pstFile);

            // Collect candidates
            List<MailCandidate> candidates = new ArrayList<MailCandidate>();
            scanFolder(contentRoot, "", connection, candidates, counters, 0, true);

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
            LOG.log(Level.WARNING, "[MailDelta] Delta sync error: " + connection.getFilePath(), e);
            counters[2]++;
        } finally {
            closeSilently(pstFile);
        }

        // Update watermarks with new data
        long maxDel = getOrDefault(maxDeliveryTime, connId, 0L);
        long maxMod = getOrDefault(maxModificationTime, connId, 0L);
        for (MailCandidate mc : newMails) {
            if (mc.deliveryTime != null && mc.deliveryTime.getTime() > maxDel) maxDel = mc.deliveryTime.getTime();
            if (mc.modificationTime != null && mc.modificationTime.getTime() > maxMod) maxMod = mc.modificationTime.getTime();
        }
        for (MailCandidate mc : changedMails) {
            if (mc.deliveryTime != null && mc.deliveryTime.getTime() > maxDel) maxDel = mc.deliveryTime.getTime();
            if (mc.modificationTime != null && mc.modificationTime.getTime() > maxMod) maxMod = mc.modificationTime.getTime();
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
    //  Folder scanning
    // ═══════════════════════════════════════════════════════════════

    /**
     * Recursively scan a folder for mail candidates.
     *
     * @param deltaOnly if true, only consider mails within the watermark + overlap window
     */
    private void scanFolder(PSTFolder folder, String path, MailConnection connection,
                            List<MailCandidate> results, int[] counters,
                            int depth, boolean deltaOnly) {
        if (depth > MAX_DEPTH) return;

        try {
            int contentCount = folder.getContentCount();
            if (contentCount > 0) {
                PSTObject child = safeGetNextChild(folder);
                while (child != null) {
                    if (child instanceof PSTMessage) {
                        PSTMessage msg = (PSTMessage) child;
                        counters[0]++;
                        try {
                            if (shouldSkipMessageClass(msg.getMessageClass())) {
                                counters[1]++;
                            } else if (deltaOnly && !isCandidate(msg, connection.getConnectionId())) {
                                counters[1]++;
                            } else {
                                MailCandidate mc = buildCandidate(msg, connection.getFilePath(), path);
                                results.add(mc);
                            }
                        } catch (Exception e) {
                            counters[2]++;
                            LOG.log(Level.FINE, "[MailDelta] Error processing message in " + path, e);
                        }
                    }
                    child = safeGetNextChild(folder);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "[MailDelta] Error scanning folder " + path, e);
            counters[2]++;
        }

        // Recurse into sub-folders
        try {
            for (PSTFolder sub : folder.getSubFolders()) {
                String subPath = path.isEmpty() ? "/" + sub.getDisplayName() : path + "/" + sub.getDisplayName();
                scanFolder(sub, subPath, connection, results, counters, depth + 1, deltaOnly);
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "[MailDelta] Cannot get subfolders of " + path, e);
        }
    }

    /**
     * Check whether a message is a delta candidate (within watermark + overlap window).
     */
    private boolean isCandidate(PSTMessage msg, String connectionId) {
        long deliveryCutoff = getOrDefault(maxDeliveryTime, connectionId, 0L) - OVERLAP_WINDOW_MS;
        long modifCutoff = getOrDefault(maxModificationTime, connectionId, 0L) - OVERLAP_WINDOW_MS;

        Date delivery = msg.getMessageDeliveryTime();
        Date modification = msg.getLastModificationTime();

        if (delivery != null && delivery.getTime() >= deliveryCutoff) return true;
        if (modification != null && modification.getTime() >= modifCutoff) return true;

        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Key and fingerprint computation
    // ═══════════════════════════════════════════════════════════════

    private MailCandidate buildCandidate(PSTMessage msg, String mailboxPath, String folderPath) {
        String subject = safe(msg.getSubject());
        String sender = safe(msg.getSenderName());
        String senderEmail = safe(msg.getSenderEmailAddress());
        String recipients = safe(msg.getDisplayTo());
        Date deliveryTime = msg.getMessageDeliveryTime();
        Date modificationTime = msg.getLastModificationTime();
        long size = msg.getMessageSize();
        long nodeId = msg.getDescriptorNodeId();
        String messageClass = msg.getMessageClass();
        boolean hasAttachments = msg.hasAttachments();

        // Internet Message-ID (preferred key)
        String messageId = safe(msg.getInternetMessageId());

        String mailKey = buildMailKey(messageId, mailboxPath, folderPath, subject, sender, deliveryTime, size);
        String fingerprint = buildFingerprint(mailKey, modificationTime, subject, size, sender);

        return new MailCandidate(mailKey, fingerprint, mailboxPath, folderPath, nodeId,
                subject, senderEmail.isEmpty() ? sender : sender + " <" + senderEmail + ">",
                recipients, deliveryTime, modificationTime, size, messageClass, hasAttachments);
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
     * Based on key + modification time + subject + size.
     */
    static String buildFingerprint(String mailKey, Date modificationTime,
                                   String subject, long size, String sender) {
        StringBuilder sb = new StringBuilder();
        sb.append(mailKey).append('|');
        sb.append(modificationTime != null ? modificationTime.getTime() : 0).append('|');
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
    //  PST helpers
    // ═══════════════════════════════════════════════════════════════

    private PSTFolder findContentRoot(PSTFile pstFile) throws Exception {
        PSTFolder root = pstFile.getRootFolder();
        PSTFolder best = null;
        int bestCount = -1;

        for (PSTFolder l1 : root.getSubFolders()) {
            if ("IPM_SUBTREE".equalsIgnoreCase(l1.getDisplayName())) {
                int cc = countContentChildren(l1);
                if (cc > bestCount) { best = l1; bestCount = cc; }
            }
            try {
                for (PSTFolder l2 : l1.getSubFolders()) {
                    if ("IPM_SUBTREE".equalsIgnoreCase(l2.getDisplayName())) {
                        int cc = countContentChildren(l2);
                        if (cc > bestCount) { best = l2; bestCount = cc; }
                    }
                    try {
                        for (PSTFolder l3 : l2.getSubFolders()) {
                            if ("IPM_SUBTREE".equalsIgnoreCase(l3.getDisplayName())) {
                                int cc = countContentChildren(l3);
                                if (cc > bestCount) { best = l3; bestCount = cc; }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
        return best != null ? best : root;
    }

    private int countContentChildren(PSTFolder folder) {
        try {
            int count = 0;
            for (PSTFolder sub : folder.getSubFolders()) {
                if (sub.getContentCount() > 0) count++;
            }
            return count;
        } catch (Exception e) { return 0; }
    }

    private PSTObject safeGetNextChild(PSTFolder folder) {
        try { return folder.getNextChild(); }
        catch (Exception e) { return null; }
    }

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

    private void closeSilently(PSTFile pstFile) {
        if (pstFile != null) {
            try { pstFile.close(); } catch (Exception ignored) {}
        }
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

