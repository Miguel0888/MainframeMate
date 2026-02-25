package de.bund.zrb.archive.store;

import de.bund.zrb.archive.model.ArchiveEntry;
import de.bund.zrb.archive.model.ArchiveEntryStatus;
import de.bund.zrb.archive.model.WebCacheEntry;

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
            try { stmt.execute("ALTER TABLE archive_entries ALTER COLUMN url VARCHAR(4096)"); } catch (Exception e) { LOG.fine("[Archive] Migration archive_entries.url: " + e.getMessage()); }
            try { stmt.execute("ALTER TABLE archive_entries ALTER COLUMN title VARCHAR(2048)"); } catch (Exception e) { LOG.fine("[Archive] Migration archive_entries.title: " + e.getMessage()); }

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
            try { stmt.execute("ALTER TABLE web_cache ALTER COLUMN url VARCHAR(4096)"); } catch (Exception e) { LOG.fine("[Archive] Migration web_cache.url: " + e.getMessage()); }
            try { stmt.execute("ALTER TABLE web_cache ALTER COLUMN parent_url VARCHAR(4096)"); } catch (Exception e) { LOG.fine("[Archive] Migration web_cache.parent_url: " + e.getMessage()); }

            // Indices (ignore if already exist)
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_cache_source ON web_cache(source_id)"); } catch (Exception e) { LOG.fine("[Archive] Index idx_cache_source: " + e.getMessage()); }
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_cache_status ON web_cache(status)"); } catch (Exception e) { LOG.fine("[Archive] Index idx_cache_status: " + e.getMessage()); }
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_entries_status ON archive_entries(status)"); } catch (Exception e) { LOG.fine("[Archive] Index idx_entries_status: " + e.getMessage()); }
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_entries_url ON archive_entries(url)"); } catch (Exception e) { LOG.fine("[Archive] Index idx_entries_url: " + e.getMessage()); }

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
