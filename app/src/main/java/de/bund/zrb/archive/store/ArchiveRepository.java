package de.bund.zrb.archive.store;

import de.bund.zrb.archive.model.*;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * H2-backed repository for archive entries and web-cache entries.
 */
public class ArchiveRepository {

    private static final Logger LOG = Logger.getLogger(ArchiveRepository.class.getName());
    private static ArchiveRepository instance;

    private final String jdbcUrl;
    private Connection connection;

    private ArchiveRepository() {
        String home = System.getProperty("user.home");
        File dbDir = new File(home, ".mainframemate" + File.separator + "db");
        if (!dbDir.exists()) {
            dbDir.mkdirs();
        }
        this.jdbcUrl = "jdbc:h2:" + new File(dbDir, "archive").getAbsolutePath() + ";AUTO_SERVER=TRUE";
        initDatabase();
    }

    public static synchronized ArchiveRepository getInstance() {
        if (instance == null) {
            instance = new ArchiveRepository();
        }
        return instance;
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(jdbcUrl, "sa", "");
        }
        return connection;
    }

    private void initDatabase() {
        try {
            Class.forName("org.h2.Driver");
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();

            stmt.execute("CREATE TABLE IF NOT EXISTS archive_entries ("
                    + "entry_id VARCHAR(36) PRIMARY KEY,"
                    + "url VARCHAR(2048),"
                    + "title VARCHAR(512),"
                    + "mime_type VARCHAR(128),"
                    + "snapshot_path VARCHAR(1024),"
                    + "content_length BIGINT,"
                    + "file_size_bytes BIGINT,"
                    + "crawl_timestamp BIGINT,"
                    + "last_indexed BIGINT,"
                    + "status VARCHAR(20),"
                    + "source_id VARCHAR(36),"
                    + "error_message VARCHAR(2048)"
                    + ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS archive_metadata ("
                    + "entry_id VARCHAR(36),"
                    + "meta_key VARCHAR(256),"
                    + "meta_value CLOB,"
                    + "PRIMARY KEY (entry_id, meta_key)"
                    + ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS web_cache ("
                    + "url VARCHAR(2048) PRIMARY KEY,"
                    + "source_id VARCHAR(36) NOT NULL,"
                    + "status VARCHAR(20) NOT NULL DEFAULT 'PENDING',"
                    + "depth INT DEFAULT 0,"
                    + "parent_url VARCHAR(2048),"
                    + "discovered_at BIGINT,"
                    + "archive_entry_id VARCHAR(36)"
                    + ")");

            // ── Data Lake tables ──────────────────────────────────

            stmt.execute("CREATE TABLE IF NOT EXISTS archive_runs ("
                    + "run_id VARCHAR(36) PRIMARY KEY,"
                    + "mode VARCHAR(20),"
                    + "created_at BIGINT,"
                    + "ended_at BIGINT DEFAULT 0,"
                    + "seed_urls CLOB,"
                    + "domain_policy_json CLOB,"
                    + "status VARCHAR(20),"
                    + "notes CLOB,"
                    + "resource_count INT DEFAULT 0,"
                    + "document_count INT DEFAULT 0"
                    + ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS archive_resources ("
                    + "resource_id VARCHAR(36) PRIMARY KEY,"
                    + "run_id VARCHAR(36),"
                    + "captured_at BIGINT,"
                    + "source VARCHAR(20),"
                    + "url VARCHAR(2048),"
                    + "canonical_url VARCHAR(2048),"
                    + "url_hash VARCHAR(64),"
                    + "content_hash VARCHAR(64),"
                    + "mime_type VARCHAR(128),"
                    + "http_status INT,"
                    + "kind VARCHAR(30),"
                    + "size_bytes BIGINT,"
                    + "indexable BOOLEAN,"
                    + "storage_path VARCHAR(1024),"
                    + "title VARCHAR(512),"
                    + "seen_count INT DEFAULT 1,"
                    + "first_seen_at BIGINT,"
                    + "last_seen_at BIGINT,"
                    + "error_message VARCHAR(2048)"
                    + ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS archive_documents ("
                    + "doc_id VARCHAR(36) PRIMARY KEY,"
                    + "run_id VARCHAR(36),"
                    + "created_at BIGINT,"
                    + "kind VARCHAR(30),"
                    + "title VARCHAR(512),"
                    + "canonical_url VARCHAR(2048),"
                    + "source_resource_ids CLOB,"
                    + "excerpt CLOB,"
                    + "text_content_path VARCHAR(1024),"
                    + "language VARCHAR(10),"
                    + "indexed_at BIGINT DEFAULT 0,"
                    + "word_count INT DEFAULT 0,"
                    + "host VARCHAR(256)"
                    + ")");

            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_cache_source ON web_cache(source_id)"); } catch (Exception ignored) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_cache_status ON web_cache(status)"); } catch (Exception ignored) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_entries_status ON archive_entries(status)"); } catch (Exception ignored) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_entries_url ON archive_entries(url)"); } catch (Exception ignored) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_resources_run ON archive_resources(run_id)"); } catch (Exception ignored) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_resources_content ON archive_resources(content_hash, canonical_url)"); } catch (Exception ignored) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_documents_run ON archive_documents(run_id)"); } catch (Exception ignored) {}

            stmt.close();
            LOG.info("[Archive] Database initialized at " + jdbcUrl);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[Archive] Failed to initialize database", e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  CRUD for ArchiveEntry
    // ═══════════════════════════════════════════════════════════

    public ArchiveEntry save(ArchiveEntry entry) {
        try {
            Connection conn = getConnection();
            // Upsert via MERGE
            PreparedStatement ps = conn.prepareStatement(
                    "MERGE INTO archive_entries (entry_id, url, title, mime_type, snapshot_path, "
                            + "content_length, file_size_bytes, crawl_timestamp, last_indexed, status, source_id, error_message) "
                            + "KEY(entry_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)");
            ps.setString(1, entry.getEntryId());
            ps.setString(2, truncate(entry.getUrl(), 2048));
            ps.setString(3, truncate(entry.getTitle(), 512));
            ps.setString(4, entry.getMimeType());
            ps.setString(5, entry.getSnapshotPath());
            ps.setLong(6, entry.getContentLength());
            ps.setLong(7, entry.getFileSizeBytes());
            ps.setLong(8, entry.getCrawlTimestamp());
            ps.setLong(9, entry.getLastIndexed());
            ps.setString(10, entry.getStatus().name());
            ps.setString(11, entry.getSourceId());
            ps.setString(12, entry.getErrorMessage());
            ps.executeUpdate();
            ps.close();

            // Save metadata
            saveMetadata(conn, entry);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] Failed to save entry: " + entry.getEntryId(), e);
        }
        return entry;
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) return null;
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    private void saveMetadata(Connection conn, ArchiveEntry entry) throws SQLException {
        // Delete existing, then re-insert
        PreparedStatement del = conn.prepareStatement("DELETE FROM archive_metadata WHERE entry_id=?");
        del.setString(1, entry.getEntryId());
        del.executeUpdate();
        del.close();

        if (entry.getMetadata() != null && !entry.getMetadata().isEmpty()) {
            PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO archive_metadata (entry_id, meta_key, meta_value) VALUES (?,?,?)");
            for (Map.Entry<String, String> e : entry.getMetadata().entrySet()) {
                ins.setString(1, entry.getEntryId());
                ins.setString(2, e.getKey());
                ins.setString(3, e.getValue());
                ins.addBatch();
            }
            ins.executeBatch();
            ins.close();
        }
    }

    public ArchiveEntry findById(String entryId) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM archive_entries WHERE entry_id=?");
            ps.setString(1, entryId);
            ResultSet rs = ps.executeQuery();
            ArchiveEntry entry = rs.next() ? mapEntry(rs) : null;
            rs.close();
            ps.close();
            if (entry != null) {
                entry.setMetadata(loadMetadata(entry.getEntryId()));
            }
            return entry;
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] findById failed", e);
            return null;
        }
    }

    public ArchiveEntry findByUrl(String url) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM archive_entries WHERE url=?");
            ps.setString(1, url);
            ResultSet rs = ps.executeQuery();
            ArchiveEntry entry = rs.next() ? mapEntry(rs) : null;
            rs.close();
            ps.close();
            return entry;
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] findByUrl failed", e);
            return null;
        }
    }

    public List<ArchiveEntry> findBySourceId(String sourceId) {
        return queryEntries("SELECT * FROM archive_entries WHERE source_id=?", sourceId);
    }

    public List<ArchiveEntry> findByStatus(ArchiveEntryStatus status) {
        return queryEntries("SELECT * FROM archive_entries WHERE status=?", status.name());
    }

    public List<ArchiveEntry> findAll() {
        return queryEntries("SELECT * FROM archive_entries ORDER BY crawl_timestamp DESC", null);
    }

    public void delete(String entryId) {
        try {
            Connection conn = getConnection();
            // Also delete snapshot file
            ArchiveEntry entry = findById(entryId);
            if (entry != null && entry.getSnapshotPath() != null && !entry.getSnapshotPath().isEmpty()) {
                deleteSnapshotFile(entry.getSnapshotPath());
            }
            PreparedStatement ps1 = conn.prepareStatement("DELETE FROM archive_metadata WHERE entry_id=?");
            ps1.setString(1, entryId);
            ps1.executeUpdate();
            ps1.close();
            PreparedStatement ps2 = conn.prepareStatement("DELETE FROM archive_entries WHERE entry_id=?");
            ps2.setString(1, entryId);
            ps2.executeUpdate();
            ps2.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] delete failed", e);
        }
    }

    public void deleteAll() {
        try {
            // Delete all snapshot files
            List<ArchiveEntry> all = findAll();
            for (ArchiveEntry entry : all) {
                if (entry.getSnapshotPath() != null && !entry.getSnapshotPath().isEmpty()) {
                    deleteSnapshotFile(entry.getSnapshotPath());
                }
            }
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("DELETE FROM archive_metadata");
            stmt.executeUpdate("DELETE FROM archive_entries");
            stmt.close();
            LOG.info("[Archive] All entries deleted");
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] deleteAll failed", e);
        }
    }

    private void deleteSnapshotFile(String snapshotPath) {
        try {
            String home = System.getProperty("user.home");
            File snapshotFile = new File(home + File.separator + ".mainframemate"
                    + File.separator + "archive" + File.separator + "snapshots"
                    + File.separator + snapshotPath);
            if (snapshotFile.exists()) {
                snapshotFile.delete();
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "[Archive] Could not delete snapshot file: " + snapshotPath, e);
        }
    }

    public void updateStatus(String entryId, ArchiveEntryStatus status, String errorMessage) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "UPDATE archive_entries SET status=?, error_message=? WHERE entry_id=?");
            ps.setString(1, status.name());
            ps.setString(2, errorMessage != null ? errorMessage : "");
            ps.setString(3, entryId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] updateStatus failed", e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Web-Cache
    // ═══════════════════════════════════════════════════════════

    public void addWebCacheEntry(WebCacheEntry entry) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "MERGE INTO web_cache (url, source_id, status, depth, parent_url, discovered_at, archive_entry_id) "
                            + "KEY(url) VALUES (?,?,?,?,?,?,?)");
            ps.setString(1, entry.getUrl());
            ps.setString(2, entry.getSourceId());
            ps.setString(3, entry.getStatus().name());
            ps.setInt(4, entry.getDepth());
            ps.setString(5, entry.getParentUrl());
            ps.setLong(6, entry.getDiscoveredAt());
            ps.setString(7, entry.getArchiveEntryId());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] addWebCacheEntry failed", e);
        }
    }

    public List<WebCacheEntry> getPendingUrls(String sourceId, int limit) {
        List<WebCacheEntry> list = new ArrayList<WebCacheEntry>();
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM web_cache WHERE source_id=? AND status='PENDING' ORDER BY depth, discovered_at LIMIT ?");
            ps.setString(1, sourceId);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapCacheEntry(rs));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] getPendingUrls failed", e);
        }
        return list;
    }

    public WebCacheEntry getWebCacheEntry(String url) {
        try {
            PreparedStatement ps = getConnection().prepareStatement("SELECT * FROM web_cache WHERE url=?");
            ps.setString(1, url);
            ResultSet rs = ps.executeQuery();
            WebCacheEntry entry = rs.next() ? mapCacheEntry(rs) : null;
            rs.close();
            ps.close();
            return entry;
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] getWebCacheEntry failed", e);
            return null;
        }
    }

    public void updateWebCacheStatus(String url, ArchiveEntryStatus status, String archiveEntryId) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "UPDATE web_cache SET status=?, archive_entry_id=? WHERE url=?");
            ps.setString(1, status.name());
            ps.setString(2, archiveEntryId != null ? archiveEntryId : "");
            ps.setString(3, url);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] updateWebCacheStatus failed", e);
        }
    }

    public int countByStatus(String sourceId, ArchiveEntryStatus status) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT COUNT(*) FROM web_cache WHERE source_id=? AND status=?");
            ps.setString(1, sourceId);
            ps.setString(2, status.name());
            ResultSet rs = ps.executeQuery();
            int count = rs.next() ? rs.getInt(1) : 0;
            rs.close();
            ps.close();
            return count;
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] countByStatus failed", e);
            return 0;
        }
    }

    public int countBySourceId(String sourceId) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT COUNT(*) FROM web_cache WHERE source_id=?");
            ps.setString(1, sourceId);
            ResultSet rs = ps.executeQuery();
            int count = rs.next() ? rs.getInt(1) : 0;
            rs.close();
            ps.close();
            return count;
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] countBySourceId failed", e);
            return 0;
        }
    }

    public boolean urlExists(String url) {
        try {
            PreparedStatement ps = getConnection().prepareStatement("SELECT 1 FROM web_cache WHERE url=?");
            ps.setString(1, url);
            ResultSet rs = ps.executeQuery();
            boolean exists = rs.next();
            rs.close();
            ps.close();
            return exists;
        } catch (SQLException e) {
            return false;
        }
    }

    public List<WebCacheEntry> getWebCacheEntries(String sourceId) {
        List<WebCacheEntry> list = new ArrayList<WebCacheEntry>();
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM web_cache WHERE source_id=? ORDER BY depth, discovered_at");
            ps.setString(1, sourceId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapCacheEntry(rs));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] getWebCacheEntries failed", e);
        }
        return list;
    }

    // ═══════════════════════════════════════════════════════════
    //  CRUD for ArchiveRun
    // ═══════════════════════════════════════════════════════════

    public void saveRun(ArchiveRun run) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "MERGE INTO archive_runs (run_id, mode, created_at, ended_at, seed_urls, "
                            + "domain_policy_json, status, notes, resource_count, document_count) "
                            + "KEY(run_id) VALUES (?,?,?,?,?,?,?,?,?,?)");
            ps.setString(1, run.getRunId());
            ps.setString(2, run.getMode());
            ps.setLong(3, run.getCreatedAt());
            ps.setLong(4, run.getEndedAt());
            ps.setString(5, run.getSeedUrls());
            ps.setString(6, run.getDomainPolicyJson());
            ps.setString(7, run.getStatus());
            ps.setString(8, run.getNotes());
            ps.setInt(9, run.getResourceCount());
            ps.setInt(10, run.getDocumentCount());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] saveRun failed: " + run.getRunId(), e);
        }
    }

    public void updateRunStatus(String runId, String status) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "UPDATE archive_runs SET status=?, ended_at=? WHERE run_id=?");
            ps.setString(1, status);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, runId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] updateRunStatus failed: " + runId, e);
        }
    }

    public void updateRunCounts(String runId) {
        try {
            Connection conn = getConnection();
            int resourceCount = 0;
            int documentCount = 0;
            PreparedStatement ps1 = conn.prepareStatement(
                    "SELECT COUNT(*) FROM archive_resources WHERE run_id=?");
            ps1.setString(1, runId);
            ResultSet rs1 = ps1.executeQuery();
            if (rs1.next()) resourceCount = rs1.getInt(1);
            rs1.close(); ps1.close();

            PreparedStatement ps2 = conn.prepareStatement(
                    "SELECT COUNT(*) FROM archive_documents WHERE run_id=?");
            ps2.setString(1, runId);
            ResultSet rs2 = ps2.executeQuery();
            if (rs2.next()) documentCount = rs2.getInt(1);
            rs2.close(); ps2.close();

            PreparedStatement upd = conn.prepareStatement(
                    "UPDATE archive_runs SET resource_count=?, document_count=? WHERE run_id=?");
            upd.setInt(1, resourceCount);
            upd.setInt(2, documentCount);
            upd.setString(3, runId);
            upd.executeUpdate();
            upd.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] updateRunCounts failed: " + runId, e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  CRUD for ArchiveResource
    // ═══════════════════════════════════════════════════════════

    public void saveResource(ArchiveResource res) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "MERGE INTO archive_resources (resource_id, run_id, captured_at, source, url, "
                            + "canonical_url, url_hash, content_hash, mime_type, http_status, kind, "
                            + "size_bytes, indexable, storage_path, title, seen_count, first_seen_at, "
                            + "last_seen_at, error_message) "
                            + "KEY(resource_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
            ps.setString(1, res.getResourceId());
            ps.setString(2, res.getRunId());
            ps.setLong(3, res.getCapturedAt());
            ps.setString(4, res.getSource());
            ps.setString(5, truncate(res.getUrl(), 2048));
            ps.setString(6, truncate(res.getCanonicalUrl(), 2048));
            ps.setString(7, res.getUrlHash());
            ps.setString(8, res.getContentHash());
            ps.setString(9, res.getMimeType());
            ps.setInt(10, res.getHttpStatus());
            ps.setString(11, res.getKind());
            ps.setLong(12, res.getSizeBytes());
            ps.setBoolean(13, res.isIndexable());
            ps.setString(14, res.getStoragePath());
            ps.setString(15, truncate(res.getTitle(), 512));
            ps.setInt(16, res.getSeenCount());
            ps.setLong(17, res.getFirstSeenAt());
            ps.setLong(18, res.getLastSeenAt());
            ps.setString(19, res.getErrorMessage());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] saveResource failed: " + res.getResourceId(), e);
        }
    }

    public ArchiveResource findResourceByContentHashAndUrl(String contentHash, String canonicalUrl) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM archive_resources WHERE content_hash=? AND canonical_url=?");
            ps.setString(1, contentHash);
            ps.setString(2, canonicalUrl);
            ResultSet rs = ps.executeQuery();
            ArchiveResource res = rs.next() ? mapResource(rs) : null;
            rs.close();
            ps.close();
            return res;
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] findResourceByContentHashAndUrl failed", e);
            return null;
        }
    }

    public void updateResourceSeen(String resourceId) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "UPDATE archive_resources SET seen_count = seen_count + 1, last_seen_at=? WHERE resource_id=?");
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, resourceId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] updateResourceSeen failed: " + resourceId, e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  CRUD for ArchiveDocument
    // ═══════════════════════════════════════════════════════════

    public void saveDocument(ArchiveDocument doc) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "MERGE INTO archive_documents (doc_id, run_id, created_at, kind, title, "
                            + "canonical_url, source_resource_ids, excerpt, text_content_path, "
                            + "language, indexed_at, word_count, host) "
                            + "KEY(doc_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)");
            ps.setString(1, doc.getDocId());
            ps.setString(2, doc.getRunId());
            ps.setLong(3, doc.getCreatedAt());
            ps.setString(4, doc.getKind());
            ps.setString(5, truncate(doc.getTitle(), 512));
            ps.setString(6, truncate(doc.getCanonicalUrl(), 2048));
            ps.setString(7, doc.getSourceResourceIds());
            ps.setString(8, doc.getExcerpt());
            ps.setString(9, doc.getTextContentPath());
            ps.setString(10, doc.getLanguage());
            ps.setLong(11, doc.getIndexedAt());
            ps.setInt(12, doc.getWordCount());
            ps.setString(13, doc.getHost());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] saveDocument failed: " + doc.getDocId(), e);
        }
    }

    public ArchiveDocument findDocumentById(String docId) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM archive_documents WHERE doc_id=?");
            ps.setString(1, docId);
            ResultSet rs = ps.executeQuery();
            ArchiveDocument doc = rs.next() ? mapDocument(rs) : null;
            rs.close();
            ps.close();
            return doc;
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] findDocumentById failed", e);
            return null;
        }
    }

    public ArchiveDocument findDocumentByCanonicalUrl(String canonicalUrl) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM archive_documents WHERE canonical_url=? ORDER BY created_at DESC LIMIT 1");
            ps.setString(1, canonicalUrl);
            ResultSet rs = ps.executeQuery();
            ArchiveDocument doc = rs.next() ? mapDocument(rs) : null;
            rs.close();
            ps.close();
            return doc;
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] findDocumentByCanonicalUrl failed", e);
            return null;
        }
    }

    public ArchiveResource findResourceById(String resourceId) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM archive_resources WHERE resource_id=?");
            ps.setString(1, resourceId);
            ResultSet rs = ps.executeQuery();
            ArchiveResource res = rs.next() ? mapResource(rs) : null;
            rs.close();
            ps.close();
            return res;
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] findResourceById failed", e);
            return null;
        }
    }

    public List<ArchiveDocument> searchDocuments(String query, String host, int maxResults) {
        List<ArchiveDocument> list = new ArrayList<ArchiveDocument>();
        try {
            String sql;
            boolean hasHost = host != null && !host.isEmpty();
            boolean hasQuery = query != null && !query.isEmpty();

            if (hasQuery && hasHost) {
                sql = "SELECT * FROM archive_documents WHERE host=? "
                        + "AND (LOWER(title) LIKE ? OR LOWER(excerpt) LIKE ?) "
                        + "ORDER BY created_at DESC LIMIT ?";
            } else if (hasQuery) {
                sql = "SELECT * FROM archive_documents WHERE "
                        + "(LOWER(title) LIKE ? OR LOWER(excerpt) LIKE ?) "
                        + "ORDER BY created_at DESC LIMIT ?";
            } else if (hasHost) {
                sql = "SELECT * FROM archive_documents WHERE host=? ORDER BY created_at DESC LIMIT ?";
            } else {
                sql = "SELECT * FROM archive_documents ORDER BY created_at DESC LIMIT ?";
            }

            PreparedStatement ps = getConnection().prepareStatement(sql);
            int idx = 1;
            if (hasQuery && hasHost) {
                ps.setString(idx++, host);
                String pattern = "%" + query.toLowerCase() + "%";
                ps.setString(idx++, pattern);
                ps.setString(idx++, pattern);
                ps.setInt(idx, maxResults);
            } else if (hasQuery) {
                String pattern = "%" + query.toLowerCase() + "%";
                ps.setString(idx++, pattern);
                ps.setString(idx++, pattern);
                ps.setInt(idx, maxResults);
            } else if (hasHost) {
                ps.setString(idx++, host);
                ps.setInt(idx, maxResults);
            } else {
                ps.setInt(idx, maxResults);
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapDocument(rs));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] searchDocuments failed", e);
        }
        return list;
    }

    private ArchiveDocument mapDocument(ResultSet rs) throws SQLException {
        ArchiveDocument d = new ArchiveDocument();
        d.setDocId(rs.getString("doc_id"));
        d.setRunId(rs.getString("run_id"));
        d.setCreatedAt(rs.getLong("created_at"));
        d.setKind(rs.getString("kind"));
        d.setTitle(rs.getString("title"));
        d.setCanonicalUrl(rs.getString("canonical_url"));
        d.setSourceResourceIds(rs.getString("source_resource_ids"));
        d.setExcerpt(rs.getString("excerpt"));
        d.setTextContentPath(rs.getString("text_content_path"));
        d.setLanguage(rs.getString("language"));
        d.setIndexedAt(rs.getLong("indexed_at"));
        d.setWordCount(rs.getInt("word_count"));
        d.setHost(rs.getString("host"));
        return d;
    }

    private ArchiveResource mapResource(ResultSet rs) throws SQLException {
        ArchiveResource r = new ArchiveResource();
        r.setResourceId(rs.getString("resource_id"));
        r.setRunId(rs.getString("run_id"));
        r.setCapturedAt(rs.getLong("captured_at"));
        r.setSource(rs.getString("source"));
        r.setUrl(rs.getString("url"));
        r.setCanonicalUrl(rs.getString("canonical_url"));
        r.setUrlHash(rs.getString("url_hash"));
        r.setContentHash(rs.getString("content_hash"));
        r.setMimeType(rs.getString("mime_type"));
        r.setHttpStatus(rs.getInt("http_status"));
        r.setKind(rs.getString("kind"));
        r.setSizeBytes(rs.getLong("size_bytes"));
        r.setIndexable(rs.getBoolean("indexable"));
        r.setStoragePath(rs.getString("storage_path"));
        r.setTitle(rs.getString("title"));
        r.setSeenCount(rs.getInt("seen_count"));
        r.setFirstSeenAt(rs.getLong("first_seen_at"));
        r.setLastSeenAt(rs.getLong("last_seen_at"));
        r.setErrorMessage(rs.getString("error_message"));
        return r;
    }

    // ═══════════════════════════════════════════════════════════
    //  Bulk queries for Documents
    // ═══════════════════════════════════════════════════════════

    public List<ArchiveDocument> findAllDocuments() {
        List<ArchiveDocument> list = new ArrayList<ArchiveDocument>();
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM archive_documents ORDER BY created_at DESC");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) { list.add(mapDocument(rs)); }
            rs.close(); ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] findAllDocuments failed", e);
        }
        return list;
    }

    public List<ArchiveDocument> findDocumentsByRunId(String runId) {
        List<ArchiveDocument> list = new ArrayList<ArchiveDocument>();
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM archive_documents WHERE run_id=? ORDER BY created_at DESC");
            ps.setString(1, runId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) { list.add(mapDocument(rs)); }
            rs.close(); ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] findDocumentsByRunId failed: " + runId, e);
        }
        return list;
    }

    public List<ArchiveRun> findAllRuns() {
        List<ArchiveRun> list = new ArrayList<ArchiveRun>();
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM archive_runs ORDER BY created_at DESC");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) { list.add(mapRun(rs)); }
            rs.close(); ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] findAllRuns failed", e);
        }
        return list;
    }

    private ArchiveRun mapRun(ResultSet rs) throws SQLException {
        ArchiveRun r = new ArchiveRun();
        r.setRunId(rs.getString("run_id"));
        r.setMode(rs.getString("mode"));
        r.setCreatedAt(rs.getLong("created_at"));
        r.setEndedAt(rs.getLong("ended_at"));
        r.setSeedUrls(rs.getString("seed_urls"));
        r.setDomainPolicyJson(rs.getString("domain_policy_json"));
        r.setStatus(rs.getString("status"));
        r.setNotes(rs.getString("notes"));
        r.setResourceCount(rs.getInt("resource_count"));
        r.setDocumentCount(rs.getInt("document_count"));
        return r;
    }

    public void deleteDocument(String docId) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "DELETE FROM archive_documents WHERE doc_id=?");
            ps.setString(1, docId);
            ps.executeUpdate(); ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] deleteDocument failed: " + docId, e);
        }
    }

    public void deleteRun(String runId) {
        try {
            Connection conn = getConnection();
            PreparedStatement ps1 = conn.prepareStatement("DELETE FROM archive_documents WHERE run_id=?");
            ps1.setString(1, runId); ps1.executeUpdate(); ps1.close();
            PreparedStatement ps2 = conn.prepareStatement("DELETE FROM archive_resources WHERE run_id=?");
            ps2.setString(1, runId); ps2.executeUpdate(); ps2.close();
            PreparedStatement ps3 = conn.prepareStatement("DELETE FROM archive_runs WHERE run_id=?");
            ps3.setString(1, runId); ps3.executeUpdate(); ps3.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] deleteRun failed: " + runId, e);
        }
    }

    public void deleteAllDocuments() {
        try {
            Statement stmt = getConnection().createStatement();
            stmt.executeUpdate("DELETE FROM archive_documents");
            stmt.executeUpdate("DELETE FROM archive_resources");
            stmt.executeUpdate("DELETE FROM archive_runs");
            stmt.close();
            LOG.info("[Archive] All documents, resources and runs deleted");
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] deleteAllDocuments failed", e);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    private List<ArchiveEntry> queryEntries(String sql, String param) {
        List<ArchiveEntry> list = new ArrayList<ArchiveEntry>();
        try {
            PreparedStatement ps = getConnection().prepareStatement(sql);
            if (param != null) {
                ps.setString(1, param);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapEntry(rs));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] queryEntries failed", e);
        }
        return list;
    }

    private ArchiveEntry mapEntry(ResultSet rs) throws SQLException {
        ArchiveEntry e = new ArchiveEntry();
        e.setEntryId(rs.getString("entry_id"));
        e.setUrl(rs.getString("url"));
        e.setTitle(rs.getString("title"));
        e.setMimeType(rs.getString("mime_type"));
        e.setSnapshotPath(rs.getString("snapshot_path"));
        e.setContentLength(rs.getLong("content_length"));
        e.setFileSizeBytes(rs.getLong("file_size_bytes"));
        e.setCrawlTimestamp(rs.getLong("crawl_timestamp"));
        e.setLastIndexed(rs.getLong("last_indexed"));
        e.setStatus(ArchiveEntryStatus.valueOf(rs.getString("status")));
        e.setSourceId(rs.getString("source_id"));
        e.setErrorMessage(rs.getString("error_message"));
        return e;
    }

    private Map<String, String> loadMetadata(String entryId) throws SQLException {
        Map<String, String> map = new HashMap<String, String>();
        PreparedStatement ps = getConnection().prepareStatement(
                "SELECT meta_key, meta_value FROM archive_metadata WHERE entry_id=?");
        ps.setString(1, entryId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            map.put(rs.getString("meta_key"), rs.getString("meta_value"));
        }
        rs.close();
        ps.close();
        return map;
    }

    private WebCacheEntry mapCacheEntry(ResultSet rs) throws SQLException {
        WebCacheEntry e = new WebCacheEntry();
        e.setUrl(rs.getString("url"));
        e.setSourceId(rs.getString("source_id"));
        e.setStatus(ArchiveEntryStatus.valueOf(rs.getString("status")));
        e.setDepth(rs.getInt("depth"));
        e.setParentUrl(rs.getString("parent_url"));
        e.setDiscoveredAt(rs.getLong("discovered_at"));
        e.setArchiveEntryId(rs.getString("archive_entry_id"));
        return e;
    }
}
