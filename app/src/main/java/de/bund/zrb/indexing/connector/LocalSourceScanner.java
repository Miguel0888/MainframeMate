package de.bund.zrb.indexing.connector;

import de.bund.zrb.indexing.model.IndexSource;
import de.bund.zrb.indexing.model.ScannedItem;
import de.bund.zrb.indexing.port.SourceScanner;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Scans local file system directories for indexable items.
 *
 * Uses IndexSource.scopePaths as root directories.
 * Applies include/exclude glob patterns and maxDepth/maxFileSize limits.
 */
public class LocalSourceScanner implements SourceScanner {

    private static final Logger LOG = Logger.getLogger(LocalSourceScanner.class.getName());

    @Override
    public List<ScannedItem> scan(IndexSource source) throws Exception {
        List<ScannedItem> items = new ArrayList<>();

        for (String scopePath : source.getScopePaths()) {
            Path root = Paths.get(scopePath);
            if (!Files.isDirectory(root)) {
                LOG.warning("[Indexing] Scope path not a directory: " + scopePath);
                continue;
            }

            LOG.info("[Indexing] Scanning: " + root);

            Files.walkFileTree(root, java.util.EnumSet.noneOf(FileVisitOption.class),
                    source.getMaxDepth(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isRegularFile()) return FileVisitResult.CONTINUE;
                    if (attrs.size() > source.getMaxFileSizeBytes()) return FileVisitResult.CONTINUE;

                    String relativePath = root.relativize(file).toString().replace('\\', '/');
                    String fileName = file.getFileName().toString();

                    // Apply include/exclude patterns
                    if (!matchesInclude(fileName, source)) return FileVisitResult.CONTINUE;
                    if (matchesExclude(relativePath, source)) return FileVisitResult.CONTINUE;

                    items.add(new ScannedItem(
                            file.toAbsolutePath().toString(),
                            attrs.lastModifiedTime().toMillis(),
                            attrs.size(),
                            false,
                            detectMimeType(fileName)
                    ));

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    LOG.fine("[Indexing] Cannot access: " + file + " - " + exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        LOG.info("[Indexing] Scan complete: " + items.size() + " items found");
        return items;
    }

    @Override
    public byte[] fetchContent(IndexSource source, String itemPath) throws Exception {
        return java.nio.file.Files.readAllBytes(Paths.get(itemPath));
    }

    // ─── Pattern matching ───

    private boolean matchesInclude(String fileName, IndexSource source) {
        List<String> patterns = source.getIncludePatterns();
        if (patterns == null || patterns.isEmpty()) return true; // no filter = include all
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

    /**
     * Simple glob matching: supports * and ** wildcards.
     */
    private boolean globMatches(String text, String pattern) {
        // Simple implementation: *.ext matching
        if (pattern.startsWith("*.")) {
            String ext = pattern.substring(1); // ".ext"
            return text.endsWith(ext);
        }
        if (pattern.contains("**")) {
            String prefix = pattern.substring(0, pattern.indexOf("**"));
            return text.startsWith(prefix);
        }
        return text.equals(pattern) || text.endsWith("/" + pattern);
    }

    private String detectMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".md")) return "text/markdown";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".java")) return "text/x-java-source";
        if (lower.endsWith(".xml")) return "text/xml";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".csv")) return "text/csv";
        return null; // unknown – Tika will detect
    }
}
