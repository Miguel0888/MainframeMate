package de.bund.zrb.search.io;

import de.bund.zrb.archive.service.ResourceStorageService;
import de.bund.zrb.archive.store.ArchiveRepository;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.rag.model.Chunk;
import de.bund.zrb.rag.service.RagService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.*;

/**
 * Service for exporting/importing the Lucene index, H2 archive database,
 * and archive file snapshots as a single ZIP file.
 * <p>
 * <b>All data is read through the live service instances</b> (RagService,
 * ArchiveRepository, ResourceStorageService) so that no file-level locks
 * can cause failures on Windows.
 * <p>
 * ZIP layout (v2):
 * <pre>
 *   manifest.properties          – metadata (version, date, source types)
 *   chunks.jsonl                 – one JSON object per Lucene chunk
 *   archive-db.sql               – H2 SCRIPT dump (portable SQL)
 *   snapshots/                   – archive file snapshots (runs/…)
 * </pre>
 */
public final class SearchDataExportImportService {

    private static final Logger LOG = Logger.getLogger(SearchDataExportImportService.class.getName());
    private static final int BUFFER = 8192;
    private static final String MANIFEST = "manifest.properties";
    private static final String CHUNKS_FILE = "chunks.jsonl";

    // ═══════════════════════════════════════════════════════════
    //  EXPORT
    // ═══════════════════════════════════════════════════════════

    /**
     * Callback for progress reporting.
     */
    public interface ProgressCallback {
        /** @param percent 0–100 */
        void onProgress(int percent, String message);
    }

    /**
     * Export selected source types to a ZIP file.
     * All data is pulled from live service instances – no direct file I/O
     * on database or index files.
     *
     * @param zipFile     target ZIP file
     * @param sourceTypes set of source type names to include (LOCAL, FTP, NDV, MAIL, ARCHIVE)
     * @param callback    optional progress callback (may be null)
     */
    public static void exportToZip(File zipFile, Set<String> sourceTypes,
                                   ProgressCallback callback) throws IOException {
        if (callback == null) callback = (p, m) -> {};

        callback.onProgress(0, "Export wird vorbereitet…");

        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            zos.setLevel(Deflater.DEFAULT_COMPRESSION);

            // 1. Manifest
            callback.onProgress(5, "Manifest schreiben…");
            Properties manifest = new Properties();
            manifest.setProperty("version", "2");
            manifest.setProperty("exportDate",
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new java.util.Date()));
            manifest.setProperty("sourceTypes", String.join(",", sourceTypes));
            writeManifest(zos, manifest);

            // 2. Lucene index – export all chunks as JSON lines via live service
            callback.onProgress(10, "Lucene-Index exportieren…");
            exportLuceneChunks(zos, callback);

            // 3. H2 database dump via the existing ArchiveRepository connection
            callback.onProgress(40, "Archiv-Datenbank exportieren…");
            if (sourceTypes.contains("ARCHIVE")) {
                exportH2Dump(zos);
            }

            // 4. Archive snapshot files – read via ResourceStorageService
            callback.onProgress(60, "Archiv-Snapshots exportieren…");
            if (sourceTypes.contains("ARCHIVE")) {
                exportSnapshotsViaService(zos, callback);
            }

            callback.onProgress(95, "ZIP wird finalisiert…");
            zos.flush();
        }

        callback.onProgress(100, "Export abgeschlossen: " + zipFile.getName());
    }

    // ── Lucene export via service ────────────────────────────

    /**
     * Export all Lucene chunks as JSON-lines through the live RagService /
     * LuceneLexicalIndex.  No file-system access on the index directory.
     */
    private static void exportLuceneChunks(ZipOutputStream zos,
                                           ProgressCallback callback) throws IOException {
        RagService rag = RagService.getInstance();
        if (rag == null) {
            LOG.warning("[Export] RagService not available – skipping Lucene export");
            return;
        }

        List<Chunk> allChunks = rag.exportAllChunks();
        if (allChunks == null || allChunks.isEmpty()) {
            LOG.info("[Export] Lucene index is empty – nothing to export");
            return;
        }

        callback.onProgress(15, "Schreibe " + allChunks.size() + " Chunks…");

        zos.putNextEntry(new ZipEntry(CHUNKS_FILE));
        Writer writer = new OutputStreamWriter(zos, StandardCharsets.UTF_8);

        for (int i = 0; i < allChunks.size(); i++) {
            Chunk c = allChunks.get(i);
            // Simple JSON serialisation – no external library required
            writer.write(chunkToJson(c));
            writer.write('\n');

            if (i % 500 == 0) {
                int pct = 15 + (int) (25.0 * i / allChunks.size());
                callback.onProgress(pct, "Chunk " + i + " / " + allChunks.size());
            }
        }
        writer.flush();           // flush the writer but don't close (would close zos)
        zos.closeEntry();

        LOG.info("[Export] Exported " + allChunks.size() + " chunks to " + CHUNKS_FILE);
    }

    /** Minimal JSON serialisation for a Chunk – no dependency on Gson etc. */
    private static String chunkToJson(Chunk c) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        jsonField(sb, "chunkId", c.getChunkId()); sb.append(',');
        jsonField(sb, "documentId", c.getDocumentId()); sb.append(',');
        jsonField(sb, "sourceName", c.getSourceName()); sb.append(',');
        jsonField(sb, "mimeType", c.getMimeType()); sb.append(',');
        sb.append("\"position\":").append(c.getPosition()).append(',');
        jsonField(sb, "text", c.getText()); sb.append(',');
        jsonField(sb, "heading", c.getHeading()); sb.append(',');
        sb.append("\"startOffset\":").append(c.getStartOffset()).append(',');
        sb.append("\"endOffset\":").append(c.getEndOffset());
        sb.append('}');
        return sb.toString();
    }

    private static void jsonField(StringBuilder sb, String key, String value) {
        sb.append('"').append(key).append("\":");
        if (value == null) {
            sb.append("null");
        } else {
            sb.append('"').append(jsonEscape(value)).append('"');
        }
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }

    // ── H2 export via the live repository connection ─────────

    /**
     * Export the H2 database using the <b>already-open</b> connection held by
     * {@link ArchiveRepository}.  This avoids opening a second connection that
     * would compete for the database lock file.
     */
    private static void exportH2Dump(ZipOutputStream zos) throws IOException {
        try {
            ArchiveRepository repo = ArchiveRepository.getInstance();
            String sql = repo.exportDatabaseScript();

            if (sql != null && !sql.isEmpty()) {
                zos.putNextEntry(new ZipEntry("archive-db.sql"));
                zos.write(sql.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                LOG.info("[Export] H2 dump written (" + sql.length() + " chars)");
            } else {
                LOG.warning("[Export] H2 dump returned empty – skipping");
            }
        } catch (Exception e) {
            throw new IOException("H2 export via repository failed: " + e.getMessage(), e);
        }
    }

    // ── Snapshot export via ResourceStorageService ────────────

    /**
     * Export archive snapshot files by iterating over ArchiveEntries and
     * ArchiveResources from the repository and reading each file through
     * {@link ResourceStorageService} rather than walking the filesystem
     * directly.
     */
    private static void exportSnapshotsViaService(ZipOutputStream zos,
                                                  ProgressCallback callback) throws IOException {
        ArchiveRepository repo = ArchiveRepository.getInstance();
        ResourceStorageService storage = new ResourceStorageService();
        File archiveBaseDir = storage.getArchiveBaseDir();

        // Collect all referenced storage paths from DB objects
        Set<String> exportedPaths = new HashSet<>();

        // 1. Snapshot files from ArchiveEntries (legacy web-snapshots)
        try {
            List<de.bund.zrb.archive.model.ArchiveEntry> entries = repo.findAll();
            for (de.bund.zrb.archive.model.ArchiveEntry entry : entries) {
                String sp = entry.getSnapshotPath();
                if (sp != null && !sp.isEmpty() && !exportedPaths.contains(sp)) {
                    File snapFile = resolveSnapshotFile(sp);
                    if (snapFile != null && snapFile.exists() && snapFile.isFile()) {
                        addFileToZip(zos, snapFile, "snapshots/snapshots/" + sp);
                        exportedPaths.add(sp);
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Export] Error exporting ArchiveEntry snapshots", e);
        }

        // 2. Storage files from ArchiveResources + ArchiveDocuments (Data Lake)
        try {
            List<de.bund.zrb.archive.model.ArchiveResource> resources = repo.findAllResources();
            for (de.bund.zrb.archive.model.ArchiveResource res : resources) {
                String sp = res.getStoragePath();
                if (sp != null && !sp.isEmpty() && !exportedPaths.contains(sp)) {
                    File file = storage.getFile(sp);
                    if (file != null && file.exists() && file.isFile()) {
                        addFileToZip(zos, file, "snapshots/" + sp);
                        exportedPaths.add(sp);
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Export] Error exporting ArchiveResource storage files", e);
        }

        try {
            List<de.bund.zrb.archive.model.ArchiveDocument> docs = repo.findAllDocuments();
            for (de.bund.zrb.archive.model.ArchiveDocument doc : docs) {
                String tp = doc.getTextContentPath();
                if (tp != null && !tp.isEmpty() && !exportedPaths.contains(tp)) {
                    File file = storage.getFile(tp);
                    if (file != null && file.exists() && file.isFile()) {
                        addFileToZip(zos, file, "snapshots/" + tp);
                        exportedPaths.add(tp);
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Export] Error exporting ArchiveDocument text files", e);
        }

        callback.onProgress(85, exportedPaths.size() + " Snapshot-Dateien exportiert");
        LOG.info("[Export] Exported " + exportedPaths.size() + " snapshot/storage files");
    }

    private static File resolveSnapshotFile(String snapshotPath) {
        String home = System.getProperty("user.home");
        return new File(home + File.separator + ".mainframemate"
                + File.separator + "archive" + File.separator + "snapshots"
                + File.separator + snapshotPath);
    }

    /**
     * Add a single file to the ZIP, with retry for transient lock issues.
     */
    private static void addFileToZip(ZipOutputStream zos, File file, String entryName) throws IOException {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                zos.putNextEntry(new ZipEntry(entryName));
                try (InputStream fis = Files.newInputStream(file.toPath(), StandardOpenOption.READ)) {
                    copy(fis, zos);
                }
                zos.closeEntry();
                return; // success
            } catch (IOException e) {
                LOG.warning("[Export] Attempt " + attempt + "/3 failed for " + file + ": " + e.getMessage());
                if (attempt == 3) {
                    LOG.warning("[Export] Skipping file after 3 attempts: " + entryName);
                } else {
                    try { Thread.sleep(200 * attempt); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
    }

    private static void writeManifest(ZipOutputStream zos, Properties props) throws IOException {
        zos.putNextEntry(new ZipEntry(MANIFEST));
        StringWriter sw = new StringWriter();
        props.store(sw, "MainframeMate Search Data Export");
        zos.write(sw.toString().getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    // ═══════════════════════════════════════════════════════════
    //  IMPORT
    // ═══════════════════════════════════════════════════════════

    /**
     * Conflict resolution strategy for duplicate files.
     */
    public enum ConflictResolution {
        ASK,            // ask per file (handled in UI)
        OVERWRITE_ALL,
        SKIP_ALL
    }

    /**
     * Information about a single entry in the import ZIP.
     */
    public static class ImportEntry {
        public final String zipPath;
        public final long size;
        public final File targetFile;
        public final boolean exists;
        /** User decision: true = overwrite, false = skip, null = not yet decided */
        public Boolean overwrite;

        public ImportEntry(String zipPath, long size, File targetFile, boolean exists) {
            this.zipPath = zipPath;
            this.size = size;
            this.targetFile = targetFile;
            this.exists = exists;
            this.overwrite = exists ? null : true; // new files default to import
        }
    }

    /**
     * Scan a ZIP file and return a list of ImportEntries with conflict information.
     */
    public static List<ImportEntry> scanZip(File zipFile) throws IOException {
        List<ImportEntry> entries = new ArrayList<ImportEntry>();

        File settingsFolder = SettingsHelper.getSettingsFolder();
        File luceneDir = new File(settingsFolder, "db/rag/lexical");
        File archiveDir = new File(System.getProperty("user.home"),
                ".mainframemate" + File.separator + "archive");

        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.isDirectory()) continue;
                String name = ze.getName();

                // Skip internal entries handled separately
                if (MANIFEST.equals(name) || "archive-db.sql".equals(name)
                        || CHUNKS_FILE.equals(name)) {
                    continue;
                }

                File targetFile = resolveTarget(name, settingsFolder, luceneDir, archiveDir);
                if (targetFile != null) {
                    entries.add(new ImportEntry(
                            name, ze.getSize(), targetFile, targetFile.exists()));
                }
            }
        }
        return entries;
    }

    /**
     * Import from a ZIP file, restoring Lucene index (via service), H2 database,
     * and snapshots.
     *
     * @param zipFile            source ZIP
     * @param resolvedEntries    entries with overwrite decisions (only for file conflicts)
     * @param callback           optional progress callback
     */
    public static void importFromZip(File zipFile, List<ImportEntry> resolvedEntries,
                                     ProgressCallback callback) throws IOException {
        if (callback == null) callback = (p, m) -> {};

        callback.onProgress(0, "Import wird vorbereitet…");

        File settingsFolder = SettingsHelper.getSettingsFolder();
        File luceneDir = new File(settingsFolder, "db/rag/lexical");
        File archiveDir = new File(System.getProperty("user.home"),
                ".mainframemate" + File.separator + "archive");

        // Build a set of paths to skip (user chose skip for conflicts)
        Set<String> skipPaths = new HashSet<String>();
        if (resolvedEntries != null) {
            for (ImportEntry e : resolvedEntries) {
                if (e.exists && !Boolean.TRUE.equals(e.overwrite)) {
                    skipPaths.add(e.zipPath);
                }
            }
        }

        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry ze;
            int total = resolvedEntries != null ? resolvedEntries.size() : 100;
            int count = 0;

            while ((ze = zis.getNextEntry()) != null) {
                String name = ze.getName();

                // Lucene chunks – import via service
                if (CHUNKS_FILE.equals(name)) {
                    callback.onProgress(10, "Lucene-Index importieren…");
                    importLuceneChunks(zis);
                    continue;
                }

                // H2 database restore via repository
                if ("archive-db.sql".equals(name)) {
                    callback.onProgress(30, "Archiv-Datenbank importieren…");
                    importH2Dump(zis);
                    continue;
                }

                // Skip manifest
                if (MANIFEST.equals(name) || ze.isDirectory()) continue;

                // Skip files the user chose to not overwrite
                if (skipPaths.contains(name)) {
                    count++;
                    continue;
                }

                // Resolve target file
                File target = resolveTarget(name, settingsFolder, luceneDir, archiveDir);
                if (target == null) {
                    count++;
                    continue;
                }

                // Create parent dirs
                target.getParentFile().mkdirs();

                // Write file
                try (FileOutputStream fos = new FileOutputStream(target)) {
                    copy(zis, fos);
                }

                count++;
                int pct = Math.min(95, (int) (count * 95.0 / Math.max(total, 1)));
                callback.onProgress(pct, "Datei: " + name);
            }
        }

        callback.onProgress(100, "Import abgeschlossen.");
    }

    // ── Lucene import via service ────────────────────────────

    /**
     * Import Lucene chunks from a JSONL stream via the live RagService.
     * Each line is a JSON object representing one Chunk.
     */
    private static void importLuceneChunks(InputStream zis) {
        try {
            RagService rag = RagService.getInstance();
            if (rag == null) {
                LOG.warning("[Import] RagService not available – skipping chunk import");
                return;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(zis, StandardCharsets.UTF_8));

            List<Chunk> batch = new ArrayList<>(500);
            String line;
            int total = 0;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                Chunk chunk = parseChunkJson(line);
                if (chunk != null) {
                    batch.add(chunk);
                    total++;

                    if (batch.size() >= 500) {
                        rag.importChunks(batch);
                        batch.clear();
                    }
                }
            }

            // Flush remaining
            if (!batch.isEmpty()) {
                rag.importChunks(batch);
            }

            LOG.info("[Import] Imported " + total + " chunks into Lucene index");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Import] Failed to import Lucene chunks", e);
        }
    }

    /** Minimal JSON parser for a single Chunk JSON object. */
    private static Chunk parseChunkJson(String json) {
        try {
            Chunk.Builder b = Chunk.builder();
            b.chunkId(extractJsonString(json, "chunkId"));
            b.documentId(extractJsonString(json, "documentId"));
            b.sourceName(extractJsonString(json, "sourceName"));
            b.mimeType(extractJsonString(json, "mimeType"));
            b.text(extractJsonString(json, "text"));
            b.heading(extractJsonString(json, "heading"));

            String pos = extractJsonNumber(json, "position");
            if (pos != null) b.position(Integer.parseInt(pos));

            String so = extractJsonNumber(json, "startOffset");
            if (so != null) b.startOffset(Integer.parseInt(so));

            String eo = extractJsonNumber(json, "endOffset");
            if (eo != null) b.endOffset(Integer.parseInt(eo));

            return b.build();
        } catch (Exception e) {
            LOG.fine("[Import] Failed to parse chunk JSON: " + e.getMessage());
            return null;
        }
    }

    /** Extract a string value for a given key from a simple JSON object. */
    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();

        // skip whitespace
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;

        if (json.charAt(start) == 'n') return null; // null

        if (json.charAt(start) != '"') return null;
        start++; // skip opening quote

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"': sb.append('"'); i++; break;
                    case '\\': sb.append('\\'); i++; break;
                    case 'n': sb.append('\n'); i++; break;
                    case 'r': sb.append('\r'); i++; break;
                    case 't': sb.append('\t'); i++; break;
                    case 'u':
                        if (i + 5 < json.length()) {
                            String hex = json.substring(i + 2, i + 6);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 5;
                        }
                        break;
                    default: sb.append(next); i++; break;
                }
            } else if (ch == '"') {
                break;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /** Extract a numeric value for a given key from a simple JSON object. */
    private static String extractJsonNumber(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '-' || ch == '.' || (ch >= '0' && ch <= '9')) {
                sb.append(ch);
            } else {
                break;
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    // ── H2 import via the live repository connection ─────────

    /**
     * Import the H2 database dump using the repository's existing connection.
     */
    private static void importH2Dump(InputStream sqlStream) throws IOException {
        // Read the SQL dump into a temp file (H2 RUNSCRIPT requires file path)
        File tempSql = File.createTempFile("archive_import_", ".sql");
        try {
            try (FileOutputStream fos = new FileOutputStream(tempSql)) {
                copy(sqlStream, fos);
            }

            ArchiveRepository repo = ArchiveRepository.getInstance();
            repo.importDatabaseScript(tempSql);

        } finally {
            if (!tempSql.delete()) {
                tempSql.deleteOnExit();
            }
        }
    }

    // ── Target resolution ────────────────────────────────────

    private static File resolveTarget(String zipPath, File settingsFolder,
                                      File luceneDir, File archiveDir) {
        // Legacy v1 format: lucene/ directory entries
        if (zipPath.startsWith("lucene/")) {
            String relative = zipPath.substring("lucene/".length());
            if (relative.isEmpty()) return null;
            return new File(luceneDir, relative);
        }
        if (zipPath.startsWith("snapshots/")) {
            String relative = zipPath.substring("snapshots/".length());
            if (relative.isEmpty()) return null;
            return new File(archiveDir, relative);
        }
        // archive-db.sql, chunks.jsonl, and manifest are handled separately
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  Utility
    // ═══════════════════════════════════════════════════════════

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[BUFFER];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
    }

    /**
     * Read the manifest from a ZIP file.
     */
    public static Properties readManifest(File zipFile) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (MANIFEST.equals(ze.getName())) {
                    Properties props = new Properties();
                    byte[] data = readBytes(zis);
                    props.load(new StringReader(new String(data, StandardCharsets.UTF_8)));
                    return props;
                }
            }
        }
        return new Properties();
    }

    private static byte[] readBytes(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[BUFFER];
        int len;
        while ((len = is.read(buf)) > 0) {
            bos.write(buf, 0, len);
        }
        return bos.toByteArray();
    }
}

