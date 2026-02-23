package de.bund.zrb.indexing.connector;

import com.pff.*;
import de.bund.zrb.indexing.model.IndexSource;
import de.bund.zrb.indexing.model.ScannedItem;
import de.bund.zrb.indexing.port.SourceScanner;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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

    @Override
    public List<ScannedItem> scan(IndexSource source) throws Exception {
        List<ScannedItem> items = new ArrayList<>();

        for (String scopePath : source.getScopePaths()) {
            File dir = new File(scopePath);
            if (!dir.isDirectory()) {
                LOG.warning("[Indexing-Mail] Scope path not a directory: " + scopePath);
                continue;
            }

            File[] mailFiles = dir.listFiles((d, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".ost") || lower.endsWith(".pst");
            });

            if (mailFiles == null || mailFiles.length == 0) {
                LOG.info("[Indexing-Mail] No OST/PST files in: " + scopePath);
                continue;
            }

            for (File mailFile : mailFiles) {
                try {
                    scanMailFile(mailFile, items, source);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[Indexing-Mail] Error scanning " + mailFile.getName()
                            + ": " + e.getMessage(), e);
                }
            }
        }

        LOG.info("[Indexing-Mail] Scan complete: " + items.size() + " messages found");
        return items;
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
    //  Internal scanning
    // ═══════════════════════════════════════════════════════════════

    private void scanMailFile(File mailFile, List<ScannedItem> items, IndexSource source) throws Exception {
        LOG.info("[Indexing-Mail] Scanning: " + mailFile.getName());
        PSTFile pstFile = new PSTFile(mailFile);
        try {
            PSTFolder root = pstFile.getRootFolder();
            scanFolder(root, "", mailFile.getAbsolutePath(), items, 0);
        } finally {
            pstFile.close();
        }
    }

    private void scanFolder(PSTFolder folder, String path, String mailboxPath,
                            List<ScannedItem> items, int depth) {
        if (depth > 15) return; // Safety limit

        // Scan messages in this folder
        try {
            int contentCount = folder.getContentCount();
            if (contentCount > 0) {
                PSTObject child = folder.getNextChild();
                while (child != null) {
                    if (child instanceof PSTMessage) {
                        PSTMessage msg = (PSTMessage) child;
                        try {
                            long nodeId = msg.getDescriptorNodeId();
                            String itemPath = mailboxPath + "#" + path + "#" + nodeId;

                            long lastModified = 0;
                            if (msg.getMessageDeliveryTime() != null) {
                                lastModified = msg.getMessageDeliveryTime().getTime();
                            } else if (msg.getLastModificationTime() != null) {
                                lastModified = msg.getLastModificationTime().getTime();
                            }

                            // Approximate size from subject + body length
                            long size = 0;
                            try {
                                String body = msg.getBody();
                                if (body != null) size = body.length();
                            } catch (Exception ignored) {}

                            items.add(new ScannedItem(itemPath, lastModified, size,
                                    false, "message/rfc822"));
                        } catch (Exception e) {
                            // Skip problematic messages
                        }
                    }
                    try {
                        child = folder.getNextChild();
                    } catch (Exception e) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            LOG.fine("[Indexing-Mail] Error reading messages in " + path + ": " + e.getMessage());
        }

        // Recurse into subfolders
        try {
            List<PSTFolder> subFolders = folder.getSubFolders();
            for (PSTFolder sub : subFolders) {
                String subPath = path.isEmpty()
                        ? "/" + sub.getDisplayName()
                        : path + "/" + sub.getDisplayName();
                scanFolder(sub, subPath, mailboxPath, items, depth + 1);
            }
        } catch (PSTException e) {
            // Some folders (search folders, system folders) can't be traversed
            LOG.fine("[Indexing-Mail] Cannot get subfolders of " + path + ": " + e.getMessage());
        } catch (Exception e) {
            LOG.fine("[Indexing-Mail] Error getting subfolders of " + path + ": " + e.getMessage());
        }
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
