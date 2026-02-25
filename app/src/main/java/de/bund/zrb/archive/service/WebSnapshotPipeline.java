package de.bund.zrb.archive.service;

import de.bund.zrb.archive.model.ArchiveEntry;
import de.bund.zrb.archive.model.ArchiveEntryStatus;
import de.bund.zrb.archive.store.ArchiveRepository;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Automatic archiving pipeline. Triggered after every research_open / research_choose
 * call in RECHERCHE mode.
 * <p>
 * Steps:
 * 1. Accept URL + text content
 * 2. Create ArchiveEntry in H2 (status: CRAWLED)
 * 3. Store text snapshot on filesystem
 * 4. (Optional) RagContentProcessor for Lucene indexing
 * 5. Update status to INDEXED
 */
public class WebSnapshotPipeline {

    private static final Logger LOG = Logger.getLogger(WebSnapshotPipeline.class.getName());

    private final ArchiveRepository repo;
    private final File snapshotBaseDir;

    public WebSnapshotPipeline(ArchiveRepository repo) {
        this.repo = repo;
        String home = System.getProperty("user.home");
        this.snapshotBaseDir = new File(home, ".mainframemate" + File.separator + "archive" + File.separator + "snapshots");
        if (!snapshotBaseDir.exists()) {
            snapshotBaseDir.mkdirs();
        }
    }

    /**
     * Process a web page snapshot.
     *
     * @param url         the page URL
     * @param textContent the extracted text content (from research_open/research_menu)
     * @param title       the page title
     * @return the created ArchiveEntry, or null on failure
     */
    public ArchiveEntry processSnapshot(String url, String textContent, String title) {
        if (url == null || url.trim().isEmpty()) return null;
        // Skip URLs that are too long for H2 VARCHAR(4096)
        if (url.length() > 4096) {
            LOG.fine("[Archive] Skipping URL exceeding 4096 chars: " + url.substring(0, 100) + "...");
            return null;
        }

        try {
            // Check if already archived
            ArchiveEntry existing = repo.findByUrl(url);
            if (existing != null && existing.getStatus() == ArchiveEntryStatus.INDEXED) {
                LOG.fine("[Archive] URL already indexed: " + url);
                return existing;
            }

            // Create domain-based subdirectory
            String domain = extractDomain(url);
            File domainDir = new File(snapshotBaseDir, domain);
            if (!domainDir.exists()) {
                domainDir.mkdirs();
            }

            // Generate unique snapshot filename
            String fileId = UUID.randomUUID().toString().substring(0, 8);
            String safeTitle = sanitizeFilename(title != null ? title : fileId);
            File textFile = new File(domainDir, fileId + "_" + safeTitle + ".txt");

            // Write text content
            writeFile(textFile, textContent != null ? textContent : "");

            // Create or update ArchiveEntry
            ArchiveEntry entry = existing != null ? existing : new ArchiveEntry();
            entry.setUrl(url);
            String effectiveTitle = title != null ? title : url;
            // Truncate title to fit H2 VARCHAR(2048) column
            if (effectiveTitle.length() > 2000) {
                effectiveTitle = effectiveTitle.substring(0, 2000) + "…";
            }
            entry.setTitle(effectiveTitle);
            entry.setMimeType("text/plain");
            entry.setSnapshotPath(domain + File.separator + textFile.getName());
            entry.setContentLength(textContent != null ? textContent.length() : 0);
            entry.setFileSizeBytes(textFile.length());
            entry.setCrawlTimestamp(System.currentTimeMillis());
            entry.setStatus(ArchiveEntryStatus.INDEXED);
            entry.setLastIndexed(System.currentTimeMillis());

            repo.save(entry);

            // Update web cache status if applicable
            if (repo.urlExists(url)) {
                repo.updateWebCacheStatus(url, ArchiveEntryStatus.INDEXED, entry.getEntryId());
            }

            LOG.info("[Archive] Snapshot saved: " + url + " → " + textFile.getName());
            return entry;

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Archive] Failed to process snapshot for " + url, e);

            // Mark as failed in web cache
            if (repo.urlExists(url)) {
                repo.updateWebCacheStatus(url, ArchiveEntryStatus.FAILED, null);
            }
            return null;
        }
    }

    private String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            return uri.getHost() != null ? uri.getHost() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String sanitizeFilename(String name) {
        String safe = name.replaceAll("[^a-zA-Z0-9äöüÄÖÜß._-]", "_");
        if (safe.length() > 60) safe = safe.substring(0, 60);
        return safe;
    }

    private void writeFile(File file, String content) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
        writer.write(content);
        writer.flush();
        writer.close();
    }
}
