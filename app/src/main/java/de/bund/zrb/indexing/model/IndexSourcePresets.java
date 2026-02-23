package de.bund.zrb.indexing.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Factory for standard index source presets.
 * Provides sensible defaults for common use cases.
 */
public final class IndexSourcePresets {

    private IndexSourcePresets() {}

    /**
     * Create a preset for indexing local documents (Eigene Dateien).
     * Includes: PDF, DOCX, DOC, XLSX, XLS, PPTX, PPT, TXT, MD, EML, MSG, CSV, HTML, XML, JSON
     * Default: content hash, daily at 12:00, 30 min max, newest first, no embeddings
     */
    public static IndexSource localDocuments() {
        IndexSource source = new IndexSource();
        source.setName("Eigene Dateien");
        source.setSourceType(SourceType.LOCAL);
        source.setEnabled(true);

        // Scope: user's documents folder
        String userHome = System.getProperty("user.home", "");
        List<String> paths = new ArrayList<>();
        paths.add(userHome + "\\Documents");
        paths.add(userHome + "\\Desktop");
        source.setScopePaths(paths);

        // Include business-relevant file types
        source.setIncludePatterns(Arrays.asList(
                "*.pdf", "*.docx", "*.doc", "*.xlsx", "*.xls", "*.pptx", "*.ppt",
                "*.txt", "*.md", "*.eml", "*.msg", "*.csv", "*.html", "*.htm",
                "*.xml", "*.json", "*.rtf", "*.odt", "*.ods"
        ));

        // Exclude typical noise
        source.setExcludePatterns(Arrays.asList(
                "*.tmp", "*.bak", "~*", "Thumbs.db", "desktop.ini",
                "node_modules/**", ".git/**", ".svn/**"
        ));

        source.setMaxDepth(10);
        source.setMaxFileSizeBytes(50 * 1024 * 1024); // 50 MB

        // Schedule: daily at 12:00, max 30 min
        source.setScheduleMode(ScheduleMode.DAILY);
        source.setStartHour(12);
        source.setStartMinute(0);
        source.setMaxDurationMinutes(30);
        source.setIndexDirection(IndexDirection.NEWEST_FIRST);

        // Change detection: content hash for reliability
        source.setChangeDetection(ChangeDetectionMode.MTIME_THEN_HASH);

        // Processing: fulltext only, no embeddings (requires AI config)
        source.setFulltextEnabled(true);
        source.setEmbeddingEnabled(false);
        source.setChunkSize(512);
        source.setChunkOverlap(64);
        source.setMaxChunksPerItem(100);

        return source;
    }

    /**
     * Create a preset for indexing mails (E-Mails and attachments).
     * Indexes all mails from the configured Outlook store path.
     */
    public static IndexSource mails() {
        IndexSource source = new IndexSource();
        source.setName("E-Mails");
        source.setSourceType(SourceType.MAIL);
        source.setEnabled(true);

        // Scope: default Outlook data path
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null) localAppData = System.getProperty("user.home") + "\\AppData\\Local";
        List<String> paths = new ArrayList<>();
        paths.add(localAppData + "\\Microsoft\\Outlook");
        source.setScopePaths(paths);

        // Include: all mail files
        source.setIncludePatterns(Arrays.asList("*.ost", "*.pst"));
        source.setExcludePatterns(new ArrayList<String>());

        source.setMaxDepth(1); // OST/PST files are in the folder directly
        source.setMaxFileSizeBytes(10L * 1024 * 1024 * 1024); // 10 GB (OST files can be large)

        // Schedule: daily at 12:00, max 30 min, newest first
        source.setScheduleMode(ScheduleMode.DAILY);
        source.setStartHour(12);
        source.setStartMinute(0);
        source.setMaxDurationMinutes(30);
        source.setIndexDirection(IndexDirection.NEWEST_FIRST);

        source.setChangeDetection(ChangeDetectionMode.MTIME_SIZE);

        // Processing
        source.setFulltextEnabled(true);
        source.setEmbeddingEnabled(false);
        source.setChunkSize(512);
        source.setChunkOverlap(64);
        source.setMaxChunksPerItem(50);

        return source;
    }

    /**
     * Create a preset for indexing calendar entries (Termine).
     * Separate rule so calendar items can be managed independently.
     */
    public static IndexSource calendar() {
        IndexSource source = new IndexSource();
        source.setName("Kalender / Termine");
        source.setSourceType(SourceType.MAIL);
        source.setEnabled(true);

        // Same scope as mails
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null) localAppData = System.getProperty("user.home") + "\\AppData\\Local";
        List<String> paths = new ArrayList<>();
        paths.add(localAppData + "\\Microsoft\\Outlook");
        source.setScopePaths(paths);

        source.setIncludePatterns(Arrays.asList("*.ost", "*.pst"));
        source.setExcludePatterns(new ArrayList<String>());
        source.setMaxDepth(1);
        source.setMaxFileSizeBytes(10L * 1024 * 1024 * 1024);

        // Schedule: daily at 12:05, max 15 min (calendar is usually smaller)
        source.setScheduleMode(ScheduleMode.DAILY);
        source.setStartHour(12);
        source.setStartMinute(5);
        source.setMaxDurationMinutes(15);
        source.setIndexDirection(IndexDirection.NEWEST_FIRST);

        source.setChangeDetection(ChangeDetectionMode.MTIME_SIZE);

        source.setFulltextEnabled(true);
        source.setEmbeddingEnabled(false);
        source.setChunkSize(256);
        source.setChunkOverlap(32);
        source.setMaxChunksPerItem(10);

        return source;
    }

    /**
     * Returns all default presets.
     */
    public static List<IndexSource> allDefaults() {
        List<IndexSource> list = new ArrayList<>();
        list.add(localDocuments());
        list.add(mails());
        list.add(calendar());
        return list;
    }
}
