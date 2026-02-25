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
                    + "url VARCHAR(4096),"
                    + "title VARCHAR(2048),"
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

            // ── Schema migration for existing databases ──
            migrateColumn(stmt, conn, "archive_entries", "url", 4096);
            migrateColumn(stmt, conn, "archive_entries", "title", 2048);

            stmt.execute("CREATE TABLE IF NOT EXISTS archive_metadata ("
                    + "entry_id VARCHAR(36),"
                    + "meta_key VARCHAR(256),"
                    + "meta_value CLOB,"
                    + "PRIMARY KEY (entry_id, meta_key)"
                    + ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS web_cache ("
                    + "url VARCHAR(4096) PRIMARY KEY,"
                    + "source_id VARCHAR(36) NOT NULL,"
                    + "status VARCHAR(20) NOT NULL DEFAULT 'PENDING',"
                    + "depth INT DEFAULT 0,"
                    + "parent_url VARCHAR(4096),"
                    + "discovered_at BIGINT,"
                    + "archive_entry_id VARCHAR(36)"
                    + ")");

            // ── Schema migration for existing web_cache ──
            migrateColumn(stmt, conn, "web_cache", "url", 4096);
            migrateColumn(stmt, conn, "web_cache", "parent_url", 4096);

            // Indices (ignore if already exist)
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_cache_source ON web_cache(source_id)"); } catch (Exception e) { LOG.fine("[Archive] Index idx_cache_source: " + e.getMessage()); }
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_cache_status ON web_cache(status)"); } catch (Exception e) { LOG.fine("[Archive] Index idx_cache_status: " + e.getMessage()); }
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_entries_status ON archive_entries(status)"); } catch (Exception e) { LOG.fine("[Archive] Index idx_entries_status: " + e.getMessage()); }
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_entries_url ON archive_entries(url)"); } catch (Exception e) { LOG.fine("[Archive] Index idx_entries_url: " + e.getMessage()); }

            // ═══════════════════════════════════════════════════════════
            //  NEW Data Lake + Catalog tables
            // ═══════════════════════════════════════════════════════════

            stmt.execute("CREATE TABLE IF NOT EXISTS archive_runs ("
                    + "run_id VARCHAR(36) PRIMARY KEY,"
                    + "mode VARCHAR(20) NOT NULL DEFAULT 'RESEARCH',"
                    + "created_at BIGINT NOT NULL,"
                    + "ended_at BIGINT DEFAULT 0,"
                    + "seed_urls CLOB,"
                    + "domain_policy_json CLOB,"
                    + "status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',"
                    + "notes CLOB,"
                    + "resource_count INT DEFAULT 0,"
                    + "document_count INT DEFAULT 0"
                    + ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS archive_resources ("
                    + "resource_id VARCHAR(36) PRIMARY KEY,"
                    + "run_id VARCHAR(36) NOT NULL,"
                    + "captured_at BIGINT NOT NULL,"
                    + "source VARCHAR(20) NOT NULL DEFAULT 'NETWORK',"
                    + "url CLOB,"
                    + "canonical_url VARCHAR(8192),"
                    + "url_hash VARCHAR(64),"
                    + "content_hash VARCHAR(64),"
                    + "mime_type VARCHAR(128),"
                    + "charset VARCHAR(64),"
                    + "http_status INT DEFAULT 0,"
                    + "http_method VARCHAR(10) DEFAULT 'GET',"
                    + "kind VARCHAR(30) NOT NULL DEFAULT 'OTHER',"
                    + "size_bytes BIGINT DEFAULT 0,"
                    + "top_level_url VARCHAR(8192),"
                    + "parent_url VARCHAR(8192),"
                    + "depth INT DEFAULT 0,"
                    + "indexable BOOLEAN DEFAULT FALSE,"
                    + "storage_path VARCHAR(2048),"
                    + "title VARCHAR(2048),"
                    + "seen_count INT DEFAULT 1,"
                    + "first_seen_at BIGINT,"
                    + "last_seen_at BIGINT,"
                    + "error_message VARCHAR(2048)"
                    + ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS archive_documents ("
                    + "doc_id VARCHAR(36) PRIMARY KEY,"
                    + "run_id VARCHAR(36) NOT NULL,"
                    + "created_at BIGINT NOT NULL,"
                    + "kind VARCHAR(30) NOT NULL DEFAULT 'PAGE',"
                    + "title VARCHAR(2048),"
                    + "canonical_url VARCHAR(8192),"
                    + "source_resource_ids CLOB,"
                    + "excerpt CLOB,"
                    + "text_content_path VARCHAR(2048),"
                    + "language VARCHAR(10),"
                    + "indexed_at BIGINT DEFAULT 0,"
                    + "word_count INT DEFAULT 0,"
                    + "host VARCHAR(512)"
                    + ")");

            // Indices for new tables
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_runs_created ON archive_runs(created_at)"); } catch (Exception ignore) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_runs_status ON archive_runs(status)"); } catch (Exception ignore) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_res_run ON archive_resources(run_id)"); } catch (Exception ignore) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_res_url_hash ON archive_resources(url_hash)"); } catch (Exception ignore) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_res_content_hash ON archive_resources(content_hash)"); } catch (Exception ignore) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_res_captured ON archive_resources(captured_at)"); } catch (Exception ignore) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_res_kind ON archive_resources(kind)"); } catch (Exception ignore) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_res_indexable ON archive_resources(indexable)"); } catch (Exception ignore) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_doc_run ON archive_documents(run_id)"); } catch (Exception ignore) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_doc_canonical ON archive_documents(canonical_url)"); } catch (Exception ignore) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_doc_created ON archive_documents(created_at)"); } catch (Exception ignore) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_doc_host ON archive_documents(host)"); } catch (Exception ignore) {}


            stmt.close();
            LOG.info("[Archive] Database initialized at " + jdbcUrl);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[Archive] Failed to initialize database", e);
        }
    }

    /**
     * Migrate a VARCHAR column to the required size. Verifies the result via INFORMATION_SCHEMA.
     */
    private void migrateColumn(Statement stmt, Connection conn, String table, String column, int requiredSize) {
        try {
            stmt.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " VARCHAR(" + requiredSize + ")");
            LOG.info("[Archive] Migration " + table + "." + column + " → VARCHAR(" + requiredSize + ") executed.");
        } catch (Exception e) {
            // ALTER may fail if column already has the correct size or table was just created
            LOG.fine("[Archive] Migration " + table + "." + column + ": " + e.getMessage());
        }

        // Verify the actual column size via INFORMATION_SCHEMA
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE UPPER(TABLE_NAME) = UPPER(?) AND UPPER(COLUMN_NAME) = UPPER(?)");
            ps.setString(1, table);
            ps.setString(2, column);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long actualSize = rs.getLong(1);
                if (actualSize < requiredSize) {
                    LOG.warning("[Archive] ⚠ Column " + table + "." + column
                            + " has size " + actualSize + " but requires " + requiredSize
                            + ". Data truncation may occur!");
                }
            }
            rs.close();
            ps.close();
        } catch (Exception e) {
            LOG.fine("[Archive] Could not verify column size for " + table + "." + column + ": " + e.getMessage());
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
            ps.setString(2, truncate(entry.getUrl(), 4096));
            ps.setString(3, truncate(entry.getTitle(), 2048));
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

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}
    }

    // ═══════════════════════════════════════════════════════════
    //  CRUD for ArchiveRun
    // ═══════════════════════════════════════════════════════════

    public ArchiveRun saveRun(ArchiveRun run) {
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
            LOG.log(Level.WARNING, "[Archive] Failed to save run: " + run.getRunId(), e);
        }
        return run;
    }

    public ArchiveRun findRunById(String runId) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM archive_runs WHERE run_id=?");
            ps.setString(1, runId);
            ResultSet rs = ps.executeQuery();
            ArchiveRun run = rs.next() ? mapRun(rs) : null;
            rs.close();
            ps.close();
            return run;
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] findRunById failed", e);
            return null;
        }
    }

    public List<ArchiveRun> findAllRuns() {
        List<ArchiveRun> list = new ArrayList<ArchiveRun>();
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM archive_runs ORDER BY created_at DESC");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRun(rs));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] findAllRuns failed", e);
        }
        return list;
    }

    public void updateRunStatus(String runId, String status) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "UPDATE archive_runs SET status=?, ended_at=? WHERE run_id=?");
            ps.setString(1, status);
            ps.setLong(2, "RUNNING".equals(status) ? 0 : System.currentTimeMillis());
            ps.setString(3, runId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] updateRunStatus failed", e);
        }
    }

    public void updateRunCounts(String runId) {
        try {
            Connection conn = getConnection();
            PreparedStatement psRes = conn.prepareStatement(
                    "SELECT COUNT(*) FROM archive_resources WHERE run_id=?");
            psRes.setString(1, runId);
            ResultSet rsRes = psRes.executeQuery();
            int resCount = rsRes.next() ? rsRes.getInt(1) : 0;
            rsRes.close();
            psRes.close();

            PreparedStatement psDoc = conn.prepareStatement(
                    "SELECT COUNT(*) FROM archive_documents WHERE run_id=?");
            psDoc.setString(1, runId);
            ResultSet rsDoc = psDoc.executeQuery();
            int docCount = rsDoc.next() ? rsDoc.getInt(1) : 0;
            rsDoc.close();
            psDoc.close();

            PreparedStatement psUpdate = conn.prepareStatement(
                    "UPDATE archive_runs SET resource_count=?, document_count=? WHERE run_id=?");
            psUpdate.setInt(1, resCount);
            psUpdate.setInt(2, docCount);
            psUpdate.setString(3, runId);
            psUpdate.executeUpdate();
            psUpdate.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] updateRunCounts failed", e);
        }
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

    // ═══════════════════════════════════════════════════════════
    //  CRUD for ArchiveResource
    // ═══════════════════════════════════════════════════════════

    public ArchiveResource saveResource(ArchiveResource res) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "MERGE INTO archive_resources (resource_id, run_id, captured_at, source, url, "
                  + "canonical_url, url_hash, content_hash, mime_type, charset, http_status, http_method, "
                  + "kind, size_bytes, top_level_url, parent_url, depth, indexable, storage_path, "
                  + "title, seen_count, first_seen_at, last_seen_at, error_message) "
                  + "KEY(resource_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
            ps.setString(1, res.getResourceId());
            ps.setString(2, res.getRunId());
            ps.setLong(3, res.getCapturedAt());
            ps.setString(4, res.getSource());
            ps.setString(5, res.getUrl());
            ps.setString(6, truncate(res.getCanonicalUrl(), 8192));
            ps.setString(7, res.getUrlHash());
            ps.setString(8, res.getContentHash());
            ps.setString(9, res.getMimeType());
            ps.setString(10, res.getCharset());
            ps.setInt(11, res.getHttpStatus());
            ps.setString(12, res.getHttpMethod());
            ps.setString(13, res.getKind());
            ps.setLong(14, res.getSizeBytes());
            ps.setString(15, truncate(res.getTopLevelUrl(), 8192));
            ps.setString(16, truncate(res.getParentUrl(), 8192));
            ps.setInt(17, res.getDepth());
            ps.setBoolean(18, res.isIndexable());
            ps.setString(19, res.getStoragePath());
            ps.setString(20, truncate(res.getTitle(), 2048));
            ps.setInt(21, res.getSeenCount());
            ps.setLong(22, res.getFirstSeenAt());
            ps.setLong(23, res.getLastSeenAt());
            ps.setString(24, res.getErrorMessage());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] Failed to save resource: " + res.getResourceId(), e);
        }
        return res;
    }

    /**
     * Find an existing resource by content hash and canonical URL for deduplication.
     */
    public ArchiveResource findResourceByContentHashAndUrl(String contentHash, String canonicalUrl) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM archive_resources WHERE content_hash=? AND canonical_url=? LIMIT 1");
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

    public List<ArchiveResource> findResourcesByRunId(String runId) {
        List<ArchiveResource> list = new ArrayList<ArchiveResource>();
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM archive_resources WHERE run_id=? ORDER BY captured_at DESC");
            ps.setString(1, runId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapResource(rs));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] findResourcesByRunId failed", e);
        }
        return list;
    }

    /**
     * Update seen count and last_seen_at for deduplication.
     */
    public void updateResourceSeen(String resourceId) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "UPDATE archive_resources SET seen_count = seen_count + 1, last_seen_at = ? WHERE resource_id = ?");
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, resourceId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] updateResourceSeen failed", e);
        }
    }

    public List<ArchiveResource> findResourcesByRunAndKind(String runId, String kind) {
        List<ArchiveResource> list = new ArrayList<ArchiveResource>();
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM archive_resources WHERE run_id=? AND kind=? ORDER BY captured_at DESC");
            ps.setString(1, runId);
            ps.setString(2, kind);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapResource(rs));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] findResourcesByRunAndKind failed", e);
        }
        return list;
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
        r.setCharset(rs.getString("charset"));
        r.setHttpStatus(rs.getInt("http_status"));
        r.setHttpMethod(rs.getString("http_method"));
        r.setKind(rs.getString("kind"));
        r.setSizeBytes(rs.getLong("size_bytes"));
        r.setTopLevelUrl(rs.getString("top_level_url"));
        r.setParentUrl(rs.getString("parent_url"));
        r.setDepth(rs.getInt("depth"));
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
    //  CRUD for ArchiveDocument
    // ═══════════════════════════════════════════════════════════

    public ArchiveDocument saveDocument(ArchiveDocument doc) {
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
            ps.setString(5, truncate(doc.getTitle(), 2048));
            ps.setString(6, truncate(doc.getCanonicalUrl(), 8192));
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
            LOG.log(Level.WARNING, "[Archive] Failed to save document: " + doc.getDocId(), e);
        }
        return doc;
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

    public List<ArchiveDocument> findDocumentsByRunId(String runId) {
        List<ArchiveDocument> list = new ArrayList<ArchiveDocument>();
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM archive_documents WHERE run_id=? ORDER BY created_at DESC");
            ps.setString(1, runId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapDocument(rs));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] findDocumentsByRunId failed", e);
        }
        return list;
    }

    public List<ArchiveDocument> findAllDocuments() {
        List<ArchiveDocument> list = new ArrayList<ArchiveDocument>();
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM archive_documents ORDER BY created_at DESC");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapDocument(rs));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[Archive] findAllDocuments failed", e);
        }
        return list;
    }

    public List<ArchiveDocument> searchDocuments(String titlePattern, String host, int limit) {
        List<ArchiveDocument> list = new ArrayList<ArchiveDocument>();
        try {
            StringBuilder sql = new StringBuilder("SELECT * FROM archive_documents WHERE 1=1");
            List<String> params = new ArrayList<String>();
            if (titlePattern != null && !titlePattern.isEmpty()) {
                sql.append(" AND LOWER(title) LIKE ?");
                params.add("%" + titlePattern.toLowerCase() + "%");
            }
            if (host != null && !host.isEmpty()) {
                sql.append(" AND host=?");
                params.add(host);
            }
            sql.append(" ORDER BY created_at DESC LIMIT ?");

            PreparedStatement ps = getConnection().prepareStatement(sql.toString());
            int idx = 1;
            for (String p : params) {
                ps.setString(idx++, p);
            }
            ps.setInt(idx, limit);
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
