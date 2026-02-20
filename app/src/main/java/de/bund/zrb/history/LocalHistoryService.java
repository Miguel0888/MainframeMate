package de.bund.zrb.history;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.VirtualBackendType;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lucene-based local history service.
 * Persists file versions across app restarts in FSDirectory indices.
 * <p>
 * Separate indices per backend (local, ftp, ndv) under ~/.mainframemate/db/history/
 * <p>
 * Performance notes:
 * - IndexWriter kept open for the lifetime of the app (singleton)
 * - RAMBufferSize = 4 MB (small docs, fast commits)
 * - Commit after each recordVersion() (~1-5ms for small files)
 * - Lazy init: index opened on first access
 * - Pruning runs once at startup in a background thread
 */
public final class LocalHistoryService {

    private static final Logger LOG = Logger.getLogger(LocalHistoryService.class.getName());

    // Lucene field names
    private static final String F_VERSION_ID = "versionId";
    private static final String F_FILE_PATH = "filePath";
    private static final String F_FILE_PATH_EXACT = "filePathExact";
    private static final String F_BACKEND = "backend";
    private static final String F_TIMESTAMP = "timestamp";
    private static final String F_CONTENT = "content";
    private static final String F_CONTENT_SEARCH = "contentSearchable";
    private static final String F_CONTENT_LENGTH = "contentLength";
    private static final String F_LABEL = "label";

    // Singleton
    private static volatile LocalHistoryService INSTANCE;

    public static LocalHistoryService getInstance() {
        if (INSTANCE == null) {
            synchronized (LocalHistoryService.class) {
                if (INSTANCE == null) {
                    INSTANCE = new LocalHistoryService();
                }
            }
        }
        return INSTANCE;
    }

    // One index per backend type
    private final Map<String, IndexHolder> indices = new ConcurrentHashMap<>();
    private volatile boolean shutdownHookRegistered = false;

    private LocalHistoryService() {
        registerShutdownHook();
    }

    // ==================== Public API ====================

    /**
     * Record a version of a file before saving.
     * Call this BEFORE writing to the backend.
     */
    public void recordVersion(VirtualBackendType backendType, String filePath, String content, String label) {
        if (filePath == null || filePath.isEmpty() || content == null) return;

        String backend = backendName(backendType);
        String versionId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        try {
            IndexHolder holder = getOrCreateIndex(backend);
            Document doc = new Document();
            doc.add(new StringField(F_VERSION_ID, versionId, Field.Store.YES));
            doc.add(new StringField(F_FILE_PATH_EXACT, filePath, Field.Store.YES));
            doc.add(new TextField(F_FILE_PATH, filePath, Field.Store.NO));
            doc.add(new StringField(F_BACKEND, backend, Field.Store.YES));
            doc.add(new LongPoint(F_TIMESTAMP, timestamp));
            doc.add(new StoredField(F_TIMESTAMP, timestamp));
            doc.add(new NumericDocValuesField(F_TIMESTAMP, timestamp));
            doc.add(new StoredField(F_CONTENT, content));
            doc.add(new TextField(F_CONTENT_SEARCH, content, Field.Store.NO));
            doc.add(new StoredField(F_CONTENT_LENGTH, content.length()));
            if (label != null && !label.isEmpty()) {
                doc.add(new StoredField(F_LABEL, label));
            }

            holder.writer.addDocument(doc);
            holder.writer.commit();
            holder.refreshReader();

            LOG.fine("[LocalHistory] Recorded version for " + filePath + " (" + content.length() + " chars)");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to record history for: " + filePath, e);
        }
    }

    /**
     * Get all versions for a file, newest first.
     */
    public List<HistoryEntry> getVersions(VirtualBackendType backendType, String filePath) {
        String backend = backendName(backendType);
        List<HistoryEntry> result = new ArrayList<>();

        try {
            IndexHolder holder = getOrCreateIndex(backend);
            IndexSearcher searcher = holder.getSearcher();
            if (searcher == null) return result;

            Query query = new TermQuery(new Term(F_FILE_PATH_EXACT, filePath));
            TopDocs topDocs = searcher.search(query, 10000,
                    new Sort(new SortField(F_TIMESTAMP, SortField.Type.LONG, true))); // newest first

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                result.add(docToEntry(doc));
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to get versions for: " + filePath, e);
        }
        return result;
    }

    /**
     * Get the content of a specific version.
     */
    public String getVersionContent(VirtualBackendType backendType, String versionId) {
        String backend = backendName(backendType);
        try {
            IndexHolder holder = getOrCreateIndex(backend);
            IndexSearcher searcher = holder.getSearcher();
            if (searcher == null) return null;

            Query query = new TermQuery(new Term(F_VERSION_ID, versionId));
            TopDocs topDocs = searcher.search(query, 1);
            if (topDocs.totalHits.value > 0) {
                Document doc = searcher.doc(topDocs.scoreDocs[0].doc);
                return doc.get(F_CONTENT);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to get version content: " + versionId, e);
        }
        return null;
    }

    /**
     * Search history content across all files for a backend.
     */
    public List<HistoryEntry> searchHistory(VirtualBackendType backendType, String queryText, int maxResults) {
        String backend = backendName(backendType);
        List<HistoryEntry> result = new ArrayList<>();
        if (queryText == null || queryText.trim().isEmpty()) return result;

        try {
            IndexHolder holder = getOrCreateIndex(backend);
            IndexSearcher searcher = holder.getSearcher();
            if (searcher == null) return result;

            org.apache.lucene.queryparser.classic.QueryParser parser =
                    new org.apache.lucene.queryparser.classic.QueryParser(F_CONTENT_SEARCH, holder.analyzer);
            parser.setDefaultOperator(org.apache.lucene.queryparser.classic.QueryParser.Operator.AND);
            String escaped = org.apache.lucene.queryparser.classic.QueryParser.escape(queryText);
            Query query = parser.parse(escaped);

            TopDocs topDocs = searcher.search(query, maxResults,
                    new Sort(new SortField(F_TIMESTAMP, SortField.Type.LONG, true)));

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                result.add(docToEntry(doc));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "History search failed: " + queryText, e);
        }
        return result;
    }

    // ==================== Pruning ====================

    /**
     * Prune old versions. Called at startup and manually.
     * Removes versions that exceed maxVersionsPerFile or are older than maxAgeDays.
     */
    public void prune(int maxVersionsPerFile, int maxAgeDays) {
        for (String backend : Arrays.asList("local", "ftp", "ndv")) {
            try {
                pruneBackend(backend, maxVersionsPerFile, maxAgeDays);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Pruning failed for backend: " + backend, e);
            }
        }
    }

    private void pruneBackend(String backend, int maxVersionsPerFile, int maxAgeDays) throws IOException {
        IndexHolder holder = getOrCreateIndex(backend);
        IndexSearcher searcher = holder.getSearcher();
        if (searcher == null) return;

        long cutoffMs = System.currentTimeMillis() - ((long) maxAgeDays * 24 * 60 * 60 * 1000);
        int deletedByAge = 0;
        int deletedByCount = 0;

        // 1) Delete versions older than maxAgeDays
        Query oldQuery = LongPoint.newRangeQuery(F_TIMESTAMP, Long.MIN_VALUE, cutoffMs - 1);
        TopDocs oldDocs = searcher.search(oldQuery, Integer.MAX_VALUE);
        for (ScoreDoc sd : oldDocs.scoreDocs) {
            Document doc = searcher.doc(sd.doc);
            String versionId = doc.get(F_VERSION_ID);
            holder.writer.deleteDocuments(new Term(F_VERSION_ID, versionId));
            deletedByAge++;
        }

        // 2) Per file: keep only newest maxVersionsPerFile
        // Collect all distinct filePaths
        Set<String> filePaths = new HashSet<>();
        TopDocs allDocs = searcher.search(new MatchAllDocsQuery(), Integer.MAX_VALUE);
        for (ScoreDoc sd : allDocs.scoreDocs) {
            Document doc = searcher.doc(sd.doc);
            String fp = doc.get(F_FILE_PATH_EXACT);
            if (fp != null) filePaths.add(fp);
        }

        for (String fp : filePaths) {
            Query fileQuery = new TermQuery(new Term(F_FILE_PATH_EXACT, fp));
            TopDocs fileDocs = searcher.search(fileQuery, Integer.MAX_VALUE,
                    new Sort(new SortField(F_TIMESTAMP, SortField.Type.LONG, true)));

            if (fileDocs.scoreDocs.length > maxVersionsPerFile) {
                // Delete oldest versions beyond limit
                for (int i = maxVersionsPerFile; i < fileDocs.scoreDocs.length; i++) {
                    Document doc = searcher.doc(fileDocs.scoreDocs[i].doc);
                    String versionId = doc.get(F_VERSION_ID);
                    holder.writer.deleteDocuments(new Term(F_VERSION_ID, versionId));
                    deletedByCount++;
                }
            }
        }

        if (deletedByAge + deletedByCount > 0) {
            holder.writer.commit();
            holder.writer.forceMergeDeletes();
            holder.refreshReader();
            LOG.info("[LocalHistory] Pruned backend=" + backend
                    + ": " + deletedByAge + " by age, " + deletedByCount + " by count limit");
        }
    }

    /**
     * Delete ALL history for a specific backend.
     */
    public void clearBackend(VirtualBackendType backendType) {
        String backend = backendName(backendType);
        try {
            IndexHolder holder = getOrCreateIndex(backend);
            holder.writer.deleteAll();
            holder.writer.commit();
            holder.refreshReader();
            LOG.info("[LocalHistory] Cleared all history for backend: " + backend);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to clear history for backend: " + backend, e);
        }
    }

    /**
     * Delete history for a specific file.
     */
    public void clearFile(VirtualBackendType backendType, String filePath) {
        String backend = backendName(backendType);
        try {
            IndexHolder holder = getOrCreateIndex(backend);
            holder.writer.deleteDocuments(new Term(F_FILE_PATH_EXACT, filePath));
            holder.writer.commit();
            holder.refreshReader();
            LOG.info("[LocalHistory] Cleared history for file: " + filePath + " (backend=" + backend + ")");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to clear file history: " + filePath, e);
        }
    }

    /**
     * Get total version count for a backend (for UI/stats).
     */
    public int getVersionCount(VirtualBackendType backendType) {
        String backend = backendName(backendType);
        try {
            IndexHolder holder = getOrCreateIndex(backend);
            IndexSearcher searcher = holder.getSearcher();
            if (searcher == null) return 0;
            return searcher.getIndexReader().numDocs();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Run auto-pruning based on settings. Should be called at app startup.
     */
    public void autoPruneAsync() {
        Settings settings = SettingsHelper.load();
        if (!settings.historyEnabled) return;

        Thread pruneThread = new Thread(() -> {
            try {
                Thread.sleep(5000); // wait 5s after startup
                prune(settings.historyMaxVersionsPerFile, settings.historyMaxAgeDays);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "HistoryAutoPrune");
        pruneThread.setDaemon(true);
        pruneThread.start();
    }

    // ==================== Shutdown ====================

    public void shutdown() {
        for (IndexHolder holder : indices.values()) {
            holder.close();
        }
        indices.clear();
        LOG.info("[LocalHistory] All indices closed");
    }

    private void registerShutdownHook() {
        if (!shutdownHookRegistered) {
            shutdownHookRegistered = true;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    shutdown();
                } catch (Exception e) {
                    // ignore during shutdown
                }
            }, "HistoryShutdown"));
        }
    }

    // ==================== Internal ====================

    private IndexHolder getOrCreateIndex(String backend) throws IOException {
        IndexHolder holder = indices.get(backend);
        if (holder != null) return holder;

        synchronized (this) {
            holder = indices.get(backend);
            if (holder != null) return holder;

            Path indexPath = getIndexPath(backend);
            indexPath.toFile().mkdirs();
            holder = new IndexHolder(indexPath);
            indices.put(backend, holder);
            LOG.info("[LocalHistory] Opened index for backend=" + backend + " at " + indexPath);
            return holder;
        }
    }

    private Path getIndexPath(String backend) {
        File settingsFolder = SettingsHelper.getSettingsFolder();
        return new File(settingsFolder, "db/history/" + backend).toPath();
    }

    private String backendName(VirtualBackendType type) {
        if (type == null) return "local";
        switch (type) {
            case FTP: return "ftp";
            case NDV: return "ndv";
            default:  return "local";
        }
    }

    private HistoryEntry docToEntry(Document doc) {
        String versionId = doc.get(F_VERSION_ID);
        String filePath = doc.get(F_FILE_PATH_EXACT);
        String backend = doc.get(F_BACKEND);
        long timestamp = 0;
        IndexableField tsField = doc.getField(F_TIMESTAMP);
        if (tsField != null) {
            Number n = tsField.numericValue();
            if (n != null) timestamp = n.longValue();
        }
        int contentLength = 0;
        IndexableField clField = doc.getField(F_CONTENT_LENGTH);
        if (clField != null) {
            Number n = clField.numericValue();
            if (n != null) contentLength = n.intValue();
        }
        String label = doc.get(F_LABEL);

        return new HistoryEntry(versionId, filePath, backend, timestamp, contentLength, label);
    }

    // ==================== IndexHolder ====================

    /**
     * Holds the Lucene writer/reader/searcher for one backend's history index.
     * Writer is kept open for the app lifetime; reader is refreshed on demand.
     */
    private static final class IndexHolder {
        final Directory directory;
        final Analyzer analyzer;
        final IndexWriter writer;
        private DirectoryReader reader;
        private IndexSearcher searcher;

        IndexHolder(Path indexPath) throws IOException {
            this.directory = FSDirectory.open(indexPath);
            this.analyzer = new StandardAnalyzer();

            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            config.setRAMBufferSizeMB(4.0);
            this.writer = new IndexWriter(directory, config);
            // Ensure segments are written so DirectoryReader can open
            this.writer.commit();
        }

        synchronized IndexSearcher getSearcher() throws IOException {
            refreshReader();
            return searcher;
        }

        synchronized void refreshReader() throws IOException {
            if (reader == null) {
                if (DirectoryReader.indexExists(directory)) {
                    reader = DirectoryReader.open(directory);
                    searcher = new IndexSearcher(reader);
                }
            } else {
                DirectoryReader newReader = DirectoryReader.openIfChanged(reader);
                if (newReader != null) {
                    reader.close();
                    reader = newReader;
                    searcher = new IndexSearcher(reader);
                }
            }
        }

        void close() {
            try {
                if (reader != null) reader.close();
                if (writer != null) {
                    writer.commit();
                    writer.close();
                }
                directory.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Error closing history index", e);
            }
        }
    }
}

