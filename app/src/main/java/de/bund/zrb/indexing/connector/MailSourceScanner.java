package de.bund.zrb.indexing.connector;

import com.pff.*;
import de.bund.zrb.indexing.model.IndexSource;
import de.bund.zrb.indexing.model.ScannedItem;
import de.bund.zrb.indexing.port.SourceScanner;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Scans OST/PST mail files for indexable items (individual messages).
 *
 * Each message becomes a ScannedItem with:
 * - path: "mailboxPath#folderPath#descriptorNodeId"
 * - lastModified: message delivery time (or modification time)
 * - size: approximate message size
 * - mimeType: "message/rfc822"
 *
 * The scanner walks the folder tree of each OST/PST file found in the scope paths
 * and emits one ScannedItem per message.
 */
public class MailSourceScanner implements SourceScanner {

    private static final Logger LOG = Logger.getLogger(MailSourceScanner.class.getName());

    /** Max items to scan per folder to prevent hangs on huge folders (alternate child tree). */
    private static final int MAX_ITEMS_PER_FOLDER = 5000;
    /** Max total items across all folders/mailboxes per scan run. */
    private static final int MAX_TOTAL_ITEMS = 50000;

    /** Sentinel value to signal end of streaming. */
    private static final ScannedItem END_MARKER = new ScannedItem("__END__", 0, 0);

    @Override
    public List<ScannedItem> scan(IndexSource source) throws Exception {
        // Batch mode: collect all items (used by LocalSourceScanner-style pipeline fallback)
        List<ScannedItem> items = new ArrayList<>();
        Iterator<ScannedItem> it = scanStreaming(source);
        while (it.hasNext()) {
            items.add(it.next());
        }
        return items;
    }

    @Override
    public Iterator<ScannedItem> scanStreaming(IndexSource source) throws Exception {
        final LinkedBlockingQueue<ScannedItem> queue = new LinkedBlockingQueue<>(100);

        boolean isManual = source.getScheduleMode() != null
                && "MANUAL".equals(source.getScheduleMode().name());

        // Producer thread: scans in background and puts items into queue
        Thread producer = new Thread(() -> {
            try {
                for (String scopePath : source.getScopePaths()) {
                    if (scopePath.contains("#")) {
                        String[] parts = scopePath.split("#", 2);
                        File mailFile = new File(parts[0]);
                        if (mailFile.isFile()) {
                            scanMailFileStreaming(mailFile, parts[1], queue, isManual);
                        }
                    } else {
                        File target = new File(scopePath);
                        if (target.isFile() && (scopePath.toLowerCase().endsWith(".ost")
                                || scopePath.toLowerCase().endsWith(".pst"))) {
                            scanMailFileStreaming(target, null, queue, isManual);
                        } else if (target.isDirectory()) {
                            File[] mailFiles = target.listFiles((d, name) -> {
                                String lower = name.toLowerCase();
                                return lower.endsWith(".ost") || lower.endsWith(".pst");
                            });
                            if (mailFiles != null) {
                                for (File mf : mailFiles) {
                                    scanMailFileStreaming(mf, null, queue, isManual);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[Indexing-Mail] Streaming scan error", e);
            } finally {
                try { queue.put(END_MARKER); } catch (InterruptedException ignored) {}
            }
        }, "MailScanner-Producer");
        producer.setDaemon(true);
        producer.start();

        // Return a consuming iterator
        return new Iterator<ScannedItem>() {
            private ScannedItem next = null;
            private boolean done = false;

            @Override
            public boolean hasNext() {
                if (done) return false;
                if (next != null) return true;
                try {
                    next = queue.take(); // blocks until item available
                    if (next == END_MARKER) {
                        done = true;
                        next = null;
                        return false;
                    }
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    done = true;
                    return false;
                }
            }

            @Override
            public ScannedItem next() {
                if (!hasNext()) throw new NoSuchElementException();
                ScannedItem result = next;
                next = null;
                return result;
            }
        };
    }

    @Override
    public byte[] fetchContent(IndexSource source, String itemPath) throws Exception {
        // itemPath format: "mailboxPath#folderPath#descriptorNodeId"
        String[] parts = itemPath.split("#", 3);
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid mail item path: " + itemPath);
        }

        String mailboxPath = parts[0];
        String folderPath = parts[1];
        long descriptorNodeId = Long.parseLong(parts[2]);

        PSTFile pstFile = new PSTFile(new File(mailboxPath));
        try {
            PSTFolder folder = navigateToFolder(pstFile, folderPath);
            if (folder == null) {
                throw new Exception("Folder not found: " + folderPath);
            }

            PSTObject child = folder.getNextChild();
            while (child != null) {
                if (child instanceof PSTMessage
                        && child.getDescriptorNodeId() == descriptorNodeId) {
                    PSTMessage msg = (PSTMessage) child;
                    String content = buildIndexableText(msg, folderPath);
                    return content.getBytes(StandardCharsets.UTF_8);
                }
                child = folder.getNextChild();
            }

            throw new Exception("Message not found: nodeId=" + descriptorNodeId);
        } finally {
            pstFile.close();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Streaming scan – items pushed to queue on demand
    // ═══════════════════════════════════════════════════════════════

    /**
     * Scans a mailbox file and pushes items directly into the queue.
     * @param folderFilter if non-null, only scan this specific folder
     */
    private void scanMailFileStreaming(File mailFile, String folderFilter,
                                       LinkedBlockingQueue<ScannedItem> queue,
                                       boolean isManual) {
        LOG.info("[Indexing-Mail] Streaming scan: " + mailFile.getName()
                + (folderFilter != null ? " folder=" + folderFilter : ""));

        java.io.PrintStream originalErr = System.err;
        System.setErr(createFilteredErrStream(originalErr));

        int[] totalCount = {0};
        int maxPerFolder = isManual ? Integer.MAX_VALUE : MAX_ITEMS_PER_FOLDER;

        try {
            PSTFile pstFile = new PSTFile(mailFile);
            try {
                PSTFolder scanRoot;
                String basePath;

                if (folderFilter != null) {
                    scanRoot = navigateToFolder(pstFile, folderFilter);
                    if (scanRoot == null) {
                        LOG.warning("[Indexing-Mail] Folder not found: " + folderFilter);
                        return;
                    }
                    basePath = folderFilter;
                } else {
                    scanRoot = pstFile.getRootFolder();
                    basePath = "";
                }

                scanFolderStreaming(scanRoot, basePath, mailFile.getAbsolutePath(),
                        queue, 0, maxPerFolder, totalCount);
            } finally {
                pstFile.close();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Indexing-Mail] Error in streaming scan of "
                    + mailFile.getName(), e);
        } finally {
            System.setErr(originalErr);
        }

        LOG.info("[Indexing-Mail] Streaming scan of " + mailFile.getName()
                + " complete: " + totalCount[0] + " items emitted");
    }

    private void scanFolderStreaming(PSTFolder folder, String path, String mailboxPath,
                                     LinkedBlockingQueue<ScannedItem> queue, int depth,
                                     int maxPerFolder, int[] totalCount) {
        if (depth > 15) return;

        // Scan messages
        try {
            int contentCount = folder.getContentCount();
            if (contentCount > 0) {
                int scanned = 0;
                int skipped = 0;
                PSTObject child = folder.getNextChild();
                while (child != null) {
                    if (scanned >= maxPerFolder) {
                        LOG.info("[Indexing-Mail] Folder limit in " + path
                                + " (" + scanned + "/" + contentCount + ")");
                        break;
                    }

                    if (child instanceof PSTMessage) {
                        PSTMessage msg = (PSTMessage) child;
                        try {
                            String msgClass = msg.getMessageClass();
                            if (shouldSkipMessageClass(msgClass)) {
                                skipped++;
                            } else {
                                long nodeId = msg.getDescriptorNodeId();
                                String itemPath = mailboxPath + "#" + path + "#" + nodeId;

                                long lastModified = 0;
                                if (msg.getMessageDeliveryTime() != null) {
                                    lastModified = msg.getMessageDeliveryTime().getTime();
                                } else if (msg.getLastModificationTime() != null) {
                                    lastModified = msg.getLastModificationTime().getTime();
                                }

                                long size = msg.getMessageSize();

                                ScannedItem item = new ScannedItem(itemPath, lastModified, size,
                                        false, "message/rfc822");
                                queue.put(item); // blocks if queue full (backpressure)
                                scanned++;
                                totalCount[0]++;
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        } catch (Exception e) {
                            skipped++;
                        }
                    }
                    try {
                        child = folder.getNextChild();
                    } catch (Exception e) {
                        break;
                    }
                }
                if (scanned > 0 || skipped > 0) {
                    LOG.fine("[Indexing-Mail] " + path + ": emitted=" + scanned + " skipped=" + skipped);
                }
            }
        } catch (Exception e) {
            LOG.warning("[Indexing-Mail] Error reading " + path + ": " + e.getMessage());
        }

        // Recurse
        try {
            List<PSTFolder> subFolders = folder.getSubFolders();
            for (PSTFolder sub : subFolders) {
                String subPath = path.isEmpty()
                        ? "/" + sub.getDisplayName()
                        : path + "/" + sub.getDisplayName();
                scanFolderStreaming(sub, subPath, mailboxPath, queue, depth + 1,
                        maxPerFolder, totalCount);
            }
        } catch (Exception e) {
            LOG.fine("[Indexing-Mail] Cannot get subfolders of " + path + ": " + e.getMessage());
        }
    }

    private java.io.PrintStream createFilteredErrStream(java.io.PrintStream originalErr) {
        return new java.io.PrintStream(new java.io.OutputStream() {
            private final StringBuilder line = new StringBuilder();
            @Override
            public void write(int b) {
                if (b == '\n') {
                    String msg = line.toString().trim();
                    if (!msg.startsWith("Unknown message type:")
                            && !msg.startsWith("---")
                            && !msg.isEmpty()) {
                        originalErr.println(msg);
                    }
                    line.setLength(0);
                } else {
                    line.append((char) b);
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  Content extraction
    // ═══════════════════════════════════════════════════════════════

    /**
     * Build indexable text from a PST message (subject + from + to + date + body).
     */
    private String buildIndexableText(PSTMessage msg, String folderPath) {
        StringBuilder sb = new StringBuilder();

        sb.append("Betreff: ").append(safe(msg.getSubject())).append("\n");
        sb.append("Von: ").append(safe(msg.getSenderName()));
        String email = msg.getSenderEmailAddress();
        if (email != null && !email.isEmpty()) {
            sb.append(" <").append(email).append(">");
        }
        sb.append("\n");

        sb.append("An: ").append(safe(msg.getDisplayTo())).append("\n");

        if (msg.getMessageDeliveryTime() != null) {
            sb.append("Datum: ").append(msg.getMessageDeliveryTime()).append("\n");
        }

        sb.append("Ordner: ").append(folderPath).append("\n");
        sb.append("MessageClass: ").append(safe(msg.getMessageClass())).append("\n");
        sb.append("\n");

        // Body
        try {
            String body = msg.getBody();
            if (body != null && !body.isEmpty()) {
                sb.append(body);
            }
        } catch (Exception e) {
            sb.append("[Fehler beim Lesen des Nachrichtentexts]");
        }

        return sb.toString();
    }

    private String safe(String s) {
        return s != null ? s : "";
    }

    /**
     * Skip non-indexable message types.
     * - Reports/receipts (REPORT.IPM.*)
     * - Contacts (IPM.AbchPerson, IPM.Contact) – not useful for fulltext search
     * - Configuration/system items
     */
    private boolean shouldSkipMessageClass(String msgClass) {
        if (msgClass == null || msgClass.isEmpty()) return false;
        String upper = msgClass.toUpperCase();
        // Read receipts, delivery reports
        if (upper.startsWith("REPORT.")) return true;
        // Contacts (Outlook address book entries)
        if (upper.startsWith("IPM.ABCHPERSON")) return true;
        if (upper.equals("IPM.CONTACT")) return true;
        // Configuration items
        if (upper.startsWith("IPM.CONFIGURATION")) return true;
        if (upper.startsWith("IPM.MICROSOFT.")) return true;
        // Infopaths / forms
        if (upper.startsWith("IPM.INFOPATH")) return true;
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Folder navigation (for fetchContent)
    // ═══════════════════════════════════════════════════════════════

    private PSTFolder navigateToFolder(PSTFile pstFile, String folderPath) {
        try {
            PSTFolder root = pstFile.getRootFolder();
            if (folderPath == null || folderPath.isEmpty() || "/".equals(folderPath)) {
                return root;
            }

            // Search through all top-level containers
            for (PSTFolder l1 : root.getSubFolders()) {
                PSTFolder result = navigateFromBase(l1, folderPath);
                if (result != null) return result;
            }

            // Direct navigation from root
            return navigateFromBase(root, folderPath);
        } catch (Exception e) {
            LOG.warning("[Indexing-Mail] Navigation failed: " + e.getMessage());
            return null;
        }
    }

    private PSTFolder navigateFromBase(PSTFolder base, String folderPath) {
        String[] parts = folderPath.split("/");
        PSTFolder current = base;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            try {
                PSTFolder found = null;
                for (PSTFolder sub : current.getSubFolders()) {
                    if (part.equals(sub.getDisplayName())) {
                        found = sub;
                        break;
                    }
                }
                if (found == null) return null;
                current = found;
            } catch (Exception e) {
                return null;
            }
        }
        return current;
    }
}
