package de.bund.zrb.indexing.connector;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.files.model.FilePayload;
import de.bund.zrb.indexing.model.IndexSource;
import de.bund.zrb.indexing.model.ScannedItem;
import de.bund.zrb.indexing.port.SourceScanner;
import de.bund.zrb.service.FtpSourceCacheService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Scans FTP/Mainframe file systems for indexable items.
 *
 * <p>Uses a {@link FileService} to list directories on the FTP server and
 * {@link FtpSourceCacheService} for cached content retrieval.
 * The FileService must be set before scanning – it is wired by
 * {@link de.bund.zrb.ui.FtpConnectionTabImpl} when the user connects.</p>
 *
 * <p>Scope paths are interpreted as FTP directory paths to scan.
 * The scanner lists files in each scope path, filters by include/exclude
 * patterns and file size limits, and returns ScannedItems for indexing.</p>
 */
public class FtpSourceScanner implements SourceScanner {

    private static final Logger LOG = Logger.getLogger(FtpSourceScanner.class.getName());

    private volatile FileService fileService;
    private volatile String ftpHost;

    /**
     * Set the active FTP file service. Called when a connection is established.
     */
    public void setFileService(FileService fileService) {
        this.fileService = fileService;
    }

    public FileService getFileService() {
        return fileService;
    }

    /**
     * Set the FTP host name (used for document ID generation).
     */
    public void setFtpHost(String ftpHost) {
        this.ftpHost = ftpHost;
    }

    public String getFtpHost() {
        return ftpHost;
    }

    @Override
    public List<ScannedItem> scan(IndexSource source) throws Exception {
        if (fileService == null) {
            throw new IllegalStateException(
                    "FTP nicht verbunden – bitte zuerst einen FTP-Verbindungs-Tab öffnen.");
        }

        String host = ftpHost;
        if ((host == null || host.isEmpty()) && source.getConnectionHost() != null) {
            host = source.getConnectionHost();
        }

        List<ScannedItem> items = new ArrayList<ScannedItem>();

        for (String scopePath : source.getScopePaths()) {
            String dirPath = scopePath.trim();
            if (dirPath.isEmpty()) continue;

            LOG.info("[Indexing] FTP scan: listing " + dirPath + " on " + host);

            try {
                List<FileNode> nodes = fileService.list(dirPath);
                if (nodes == null) continue;

                scanDirectory(source, host, dirPath, nodes, items, 0, source.getMaxDepth());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[Indexing] FTP scan failed for path: " + dirPath, e);
                throw new IllegalStateException("FTP-Scan fehlgeschlagen für '"
                        + dirPath + "': " + e.getMessage(), e);
            }
        }

        LOG.info("[Indexing] FTP scan complete: " + items.size() + " items found");
        return items;
    }

    private void scanDirectory(IndexSource source, String host, String dirPath,
                               List<FileNode> nodes, List<ScannedItem> items,
                               int depth, int maxDepth) throws Exception {
        for (FileNode node : nodes) {
            if (node.isDirectory()) {
                // Recurse into subdirectories if within depth limit
                if (depth < maxDepth) {
                    String subPath = dirPath + (dirPath.endsWith("/") ? "" : "/") + node.getName();
                    try {
                        List<FileNode> subNodes = fileService.list(subPath);
                        if (subNodes != null) {
                            scanDirectory(source, host, subPath, subNodes, items, depth + 1, maxDepth);
                        }
                    } catch (Exception e) {
                        LOG.fine("[Indexing] FTP cannot list subdirectory: " + subPath + " - " + e.getMessage());
                    }
                }
                continue;
            }

            // Skip non-text files
            if (!FtpSourceCacheService.isTextFile(node.getName())) continue;

            // Check file size limit
            if (node.getSize() > source.getMaxFileSizeBytes() && node.getSize() > 0) continue;

            // Apply include/exclude patterns
            if (!matchesInclude(node.getName(), source)) continue;
            String relativePath = node.getPath() != null ? node.getPath() : node.getName();
            if (matchesExclude(relativePath, source)) continue;

            String absolutePath = node.getPath();
            if (absolutePath == null || absolutePath.isEmpty()) {
                absolutePath = dirPath + (dirPath.endsWith("/") ? "" : "/") + node.getName();
            }

            // Build document ID compatible with FtpSourceCacheService
            String docId = FtpSourceCacheService.documentId(
                    host != null ? host : "", absolutePath);

            long lastModified = node.getLastModifiedMillis();
            long size = node.getSize();
            String mimeType = detectMimeType(node.getName());

            items.add(new ScannedItem(docId, lastModified, size, false, mimeType));
        }
    }

    @Override
    public byte[] fetchContent(IndexSource source, String itemPath) throws Exception {
        if (fileService == null) {
            throw new IllegalStateException("FTP nicht verbunden");
        }

        String host = ftpHost;
        if ((host == null || host.isEmpty()) && source.getConnectionHost() != null) {
            host = source.getConnectionHost();
        }

        // itemPath format: "FTP:host/path/to/file"
        String withoutPrefix = itemPath.startsWith("FTP:") ? itemPath.substring(4) : itemPath;

        // Strip host from path if present
        String ftpPath;
        if (host != null && !host.isEmpty() && withoutPrefix.startsWith(host + "/")) {
            ftpPath = withoutPrefix.substring(host.length() + 1);
        } else {
            ftpPath = withoutPrefix;
        }

        // Try memory cache first (instant, O(1))
        FtpSourceCacheService cacheService = FtpSourceCacheService.getInstance();
        String cached = cacheService.getCachedContent(
                host != null ? host : "", ftpPath);
        if (cached != null) {
            return cached.getBytes(StandardCharsets.UTF_8);
        }

        // Fetch from FTP server
        FilePayload payload = fileService.readFile(ftpPath);
        String content = payload.getEditorText();
        if (content != null && !content.isEmpty()) {
            // Cache for future use
            String fileName = ftpPath.contains("/")
                    ? ftpPath.substring(ftpPath.lastIndexOf('/') + 1)
                    : ftpPath;
            cacheService.cacheContent(host != null ? host : "",
                    ftpPath, fileName, content, -1, -1);
            return content.getBytes(StandardCharsets.UTF_8);
        }

        return new byte[0];
    }

    // ─── Pattern matching (same as LocalSourceScanner) ───

    private boolean matchesInclude(String fileName, IndexSource source) {
        List<String> patterns = source.getIncludePatterns();
        if (patterns == null || patterns.isEmpty()) return true;
        String lower = fileName.toLowerCase();
        for (String pattern : patterns) {
            if (globMatches(lower, pattern.toLowerCase())) return true;
        }
        return false;
    }

    private boolean matchesExclude(String relativePath, IndexSource source) {
        List<String> patterns = source.getExcludePatterns();
        if (patterns == null || patterns.isEmpty()) return false;
        String lower = relativePath.toLowerCase();
        for (String pattern : patterns) {
            if (globMatches(lower, pattern.toLowerCase())) return true;
        }
        return false;
    }

    private boolean globMatches(String text, String pattern) {
        if (pattern.startsWith("*.")) {
            String ext = pattern.substring(1);
            return text.endsWith(ext);
        }
        if (pattern.contains("**")) {
            String prefix = pattern.substring(0, pattern.indexOf("**"));
            return text.startsWith(prefix);
        }
        return text.equals(pattern) || text.endsWith("/" + pattern);
    }

    private static String detectMimeType(String fileName) {
        if (fileName == null) return null;
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".jcl")) return "text/x-jcl";
        if (lower.endsWith(".cbl") || lower.endsWith(".cob") || lower.endsWith(".cpy")) return "text/x-cobol";
        if (lower.endsWith(".xml")) return "text/xml";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".sql") || lower.endsWith(".ddl") || lower.endsWith(".dml")) return "text/x-sql";
        if (lower.endsWith(".rexx")) return "text/x-rexx";
        if (lower.endsWith(".asm")) return "text/x-asm";
        if (lower.endsWith(".pl1") || lower.endsWith(".pli")) return "text/x-pl1";
        if (lower.endsWith(".txt") || lower.endsWith(".log")) return "text/plain";
        return null;
    }
}

