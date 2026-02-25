package de.bund.zrb.archive.service;

import de.bund.zrb.archive.model.ResourceKind;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages file storage for the Data Lake with hash-based, run-based layout:
 * <pre>
 *   ~/.mainframemate/archive/runs/{runId}/resources/{host}/{kind}/{contentHash}.{ext}
 * </pre>
 * <p>
 * Provides content-hash-based deduplication: if a blob with the same contentHash
 * already exists, no new file is written.
 */
public class ResourceStorageService {

    private static final Logger LOG = Logger.getLogger(ResourceStorageService.class.getName());

    private final File archiveBaseDir;

    public ResourceStorageService() {
        String home = System.getProperty("user.home");
        this.archiveBaseDir = new File(home, ".mainframemate" + File.separator + "archive");
        if (!archiveBaseDir.exists()) {
            archiveBaseDir.mkdirs();
        }
    }

    /**
     * Store a text blob in the run-based directory structure.
     * Returns the relative path (from archive base) to the stored file.
     * If a file with the same contentHash already exists, returns its path without re-writing.
     *
     * @param runId       the run this resource belongs to
     * @param host        the extracted hostname
     * @param kind        the ResourceKind
     * @param contentHash SHA-256 content hash
     * @param content     the text content to store
     * @return relative path from archive base dir, or null on failure
     */
    public String store(String runId, String host, ResourceKind kind, String contentHash, String content) {
        if (contentHash == null || contentHash.isEmpty()) {
            LOG.warning("[ResourceStorage] Cannot store without contentHash");
            return null;
        }

        try {
            String ext = kind.getDefaultExtension();
            String safeHost = sanitizePathComponent(host);
            String relativePath = "runs" + File.separator + runId
                    + File.separator + "resources"
                    + File.separator + safeHost
                    + File.separator + kind.name().toLowerCase()
                    + File.separator + contentHash + "." + ext;

            File targetFile = new File(archiveBaseDir, relativePath);

            // Dedupe: if file already exists with same hash, skip writing
            if (targetFile.exists()) {
                LOG.fine("[ResourceStorage] Dedupe hit: " + relativePath);
                return relativePath;
            }

            // Create directories
            File parentDir = targetFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }

            // Write content
            FileOutputStream fos = new FileOutputStream(targetFile);
            OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            writer.write(content != null ? content : "");
            writer.flush();
            writer.close();

            LOG.fine("[ResourceStorage] Stored: " + relativePath + " (" + targetFile.length() + " bytes)");
            return relativePath;

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[ResourceStorage] Failed to store blob", e);
            return null;
        }
    }

    /**
     * Read text content from a stored blob by its relative path.
     */
    public String readContent(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return null;
        try {
            File file = new File(archiveBaseDir, relativePath);
            if (!file.exists()) return null;
            return readFile(file);
        } catch (Exception e) {
            LOG.fine("[ResourceStorage] Failed to read: " + relativePath + " – " + e.getMessage());
            return null;
        }
    }

    /**
     * Read text content with max length limit.
     */
    public String readContent(String relativePath, int maxLength) {
        String content = readContent(relativePath);
        if (content != null && content.length() > maxLength) {
            return content.substring(0, maxLength) + "\n[... truncated at " + maxLength + " chars]";
        }
        return content;
    }

    /**
     * Get the absolute file for a relative storage path.
     */
    public File getFile(String relativePath) {
        return new File(archiveBaseDir, relativePath);
    }

    /**
     * Get archive base directory.
     */
    public File getArchiveBaseDir() {
        return archiveBaseDir;
    }

    private String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        try {
            char[] buf = new char[8192];
            int read;
            while ((read = reader.read(buf)) != -1) {
                sb.append(buf, 0, read);
            }
        } finally {
            reader.close();
        }
        return sb.toString();
    }

    private String sanitizePathComponent(String name) {
        if (name == null || name.isEmpty()) return "unknown";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
