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
import java.util.concurrent.atomic.AtomicBoolean;
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
    //  Cancellation
    // ═══════════════════════════════════════════════════════════

    /** Thrown when the user cancels the export/import. */
    public static class CancelledException extends IOException {
        public CancelledException() { super("Vorgang vom Benutzer abgebrochen"); }
    }

    /**
     * Token that can be shared with an export/import call to request
     * cancellation from another thread.
     */
    public static class CancelToken {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        /** Request cancellation. Thread-safe. */
        public void cancel() { cancelled.set(true); }

        /** Check if cancelled. */
        public boolean isCancelled() { return cancelled.get(); }
    }

    private static void checkCancelled(CancelToken token) throws CancelledException {
        if (token != null && token.isCancelled()) {
            throw new CancelledException();
        }
    }

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
     * Export selected source types to a ZIP file (non-cancellable overload
     * kept for backward compatibility).
     */
    public static void exportToZip(File zipFile, Set<String> sourceTypes,
                                   ProgressCallback callback) throws IOException {
        exportToZip(zipFile, sourceTypes, callback, null);
    }

    /**
     * Export selected source types to a ZIP file.
     * All data is pulled from live service instances – no direct file I/O
     * on database or index files.
     *
     * @param zipFile     target ZIP file
     * @param sourceTypes set of source typSchluessel names to include (LOCAL, FTP, NDV, MAIL, ARCHIVE)
     * @param callback    optional progress callback (may be null)
     * @param cancelToken optional token to cancel from another thread (may be null)
     */
    public static void exportToZip(File zipFile, Set<String> sourceTypes,
                                   ProgressCallback callback,
                                   CancelToken cancelToken) throws IOException {
        if (callback == null) callback = (p, m) -> {};

        callback.onProgress(0, "Export wird vorbereitet…");

        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            zos.setLevel(Deflater.DEFAULT_COMPRESSION);

            checkCancelled(cancelToken);

            // 1. Manifest  (0–5 %)
            callback.onProgress(2, "Manifest schreiben…");
            Properties manifest = new Properties();
            manifest.setProperty("version", "2");
            manifest.setProperty("exportDate",
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new java.util.Date()));
            manifest.setProperty("sourceTypes", String.join(",", sourceTypes));
            writeManifest(zos, manifest);

            checkCancelled(cancelToken);

            // 2. Lucene index – export all chunks as JSON lines  (5–40 %)
            callback.onProgress(5, "Lucene-Index exportieren…");
            exportLuceneChunks(zos, callback, cancelToken);

            checkCancelled(cancelToken);

            // 3. H2 database dump  (40–55 %)
            callback.onProgress(40, "Archiv-Datenbank exportieren…");
            if (sourceTypes.contains("ARCHIVE")) {
                exportH2Dump(zos);
            }
            callback.onProgress(55, "Datenbank exportiert.");

            checkCancelled(cancelToken);

            // 4. Archive snapshot files  (55–95 %)
            callback.onProgress(55, "Archiv-Snapshots exportieren…");
            if (sourceTypes.contains("ARCHIVE")) {
                exportSnapshotsViaService(zos, callback, cancelToken);
            }

            checkCancelled(cancelToken);

            callback.onProgress(96, "ZIP wird finalisiert…");
            zos.flush();
        } catch (CancelledException ce) {
            // Clean up partial ZIP file on cancel
            LOG.info("[Export] Cancelled by user – deleting partial ZIP");
            if (zipFile.exists() && !zipFile.delete()) {
                zipFile.deleteOnExit();
            }
            throw ce;
        }

        callback.onProgress(100, "Export abgeschlossen: " + zipFile.getName());
    }

    // ── Lucene export via service ────────────────────────────

    /**
     * Export all Lucene chunks as JSON-lines through the live RagService /
     * LuceneLexicalIndex.  No file-system access on the index directory.
     * Progress range: 5 % → 40 %
     */
    private static void exportLuceneChunks(ZipOutputStream zos,
                                           ProgressCallback callback,
                                           CancelToken cancelToken) throws IOException {
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

        callback.onProgress(8, "Schreibe " + allChunks.size() + " Chunks…");

        zos.putNextEntry(new ZipEntry(CHUNKS_FILE));
        Writer writer = new OutputStreamWriter(zos, StandardCharsets.UTF_8);

        int total = allChunks.size();
        for (int i = 0; i < total; i++) {
            checkCancelled(cancelToken);

            Chunk c = allChunks.get(i);
            writer.write(chunkToJson(c));
            writer.write('\n');

            // Report every 200 chunks or on the last one
            if (i % 200 == 0 || i == total - 1) {
                int pct = 8 + (int) (32.0 * i / total);   // 8 → 40
                callback.onProgress(pct, "Chunk " + (i + 1) + " / " + total);
            }
        }
        writer.flush();
        zos.closeEntry();

        LOG.info("[Export] Exported " + total + " chunks to " + CHUNKS_FILE);
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
     * Export archive snapshot files.  Progress range: 55 % → 95 %.
     * Reports progress per file so the bar never appears stuck.
     */
    private static void exportSnapshotsViaService(ZipOutputStream zos,
                                                  ProgressCallback callback,
                                                  CancelToken cancelToken) throws IOException {
        ArchiveRepository repo = ArchiveRepository.getInstance();
        ResourceStorageService storage = new ResourceStorageService();

        // ── Phase 1: collect all file references from DB (fast, in-memory) ──
        callback.onProgress(56, "Snapshot-Referenzen sammeln…");

        // List of (File, zipEntryName) pairs to export
        List<String[]> filesToExport = new ArrayList<>();  // [0]=absolutePath, [1]=zipEntryName

        try {
            List<de.bund.zrb.archive.model.ArchiveEntry> entries = repo.findAll();
            Set<String> seen = new HashSet<>();
            for (de.bund.zrb.archive.model.ArchiveEntry entry : entries) {
                String sp = entry.getSnapshotPath();
                if (sp != null && !sp.isEmpty() && seen.add(sp)) {
                    File f = resolveSnapshotFile(sp);
                    if (f != null && f.exists() && f.isFile()) {
                        filesToExport.add(new String[]{f.getAbsolutePath(), "snapshots/snapshots/" + sp});
                    }
                }
            }

            List<de.bund.zrb.archive.model.ArchiveResource> resources = repo.findAllResources();
            for (de.bund.zrb.archive.model.ArchiveResource res : resources) {
                String sp = res.getStoragePath();
                if (sp != null && !sp.isEmpty() && seen.add(sp)) {
                    File f = storage.getFile(sp);
                    if (f != null && f.exists() && f.isFile()) {
                        filesToExport.add(new String[]{f.getAbsolutePath(), "snapshots/" + sp});
                    }
                }
            }

            List<de.bund.zrb.archive.model.ArchiveDocument> docs = repo.findAllDocuments();
            for (de.bund.zrb.archive.model.ArchiveDocument doc : docs) {
                String tp = doc.getTextContentPath();
                if (tp != null && !tp.isEmpty() && seen.add(tp)) {
                    File f = storage.getFile(tp);
                    if (f != null && f.exists() && f.isFile()) {
                        filesToExport.add(new String[]{f.getAbsolutePath(), "snapshots/" + tp});
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Export] Error collecting snapshot references", e);
        }

        // ── Phase 2: write files into ZIP with per-file progress ──
        int total = filesToExport.size();
        if (total == 0) {
            callback.onProgress(95, "Keine Snapshot-Dateien vorhanden.");
            return;
        }

        callback.onProgress(58, total + " Snapshot-Dateien exportieren…");

        int exported = 0;
        int skipped = 0;
        for (int i = 0; i < total; i++) {
            checkCancelled(cancelToken);

            String absPath = filesToExport.get(i)[0];
            String entryName = filesToExport.get(i)[1];
            File file = new File(absPath);

            boolean ok = addFileToZip(zos, file, entryName);
            if (ok) exported++; else skipped++;

            // Progress: 58 → 95 spread evenly over all files
            int pct = 58 + (int) (37.0 * (i + 1) / total);
            callback.onProgress(pct, "Datei " + (i + 1) + " / " + total
                    + ": " + file.getName());
        }

        LOG.info("[Export] Exported " + exported + " snapshot files, skipped " + skipped);
    }

    private static File resolveSnapshotFile(String snapshotPath) {
        String home = System.getProperty("user.home");
        return new File(home + File.separator + ".mainframemate"
                + File.separator + "archive" + File.separator + "snapshots"
                + File.separator + snapshotPath);
    }

    /**
     * Add a single file to the ZIP, with retry for transient lock issues.
     * @return true if the file was added, false if skipped after retries
     */
    private static boolean addFileToZip(ZipOutputStream zos, File file, String entryName) throws IOException {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                zos.putNextEntry(new ZipEntry(entryName));
                try (InputStream fis = Files.newInputStream(file.toPath(), StandardOpenOption.READ)) {
                    copy(fis, zos);
                }
                zos.closeEntry();
                return true;
            } catch (IOException e) {
                LOG.warning("[Export] Attempt " + attempt + "/3 failed for " + file + ": " + e.getMessage());
                if (attempt == 3) {
                    LOG.warning("[Export] Skipping file after 3 attempts: " + entryName);
                    return false;
                }
                try { Thread.sleep(200 * attempt); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        return false;
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
            this.overwrite = exists ? null : true;
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
     */
    public static void importFromZip(File zipFile, List<ImportEntry> resolvedEntries,
                                     ProgressCallback callback) throws IOException {
        if (callback == null) callback = (p, m) -> {};

        callback.onProgress(0, "Import wird vorbereitet…");

        File settingsFolder = SettingsHelper.getSettingsFolder();
        File luceneDir = new File(settingsFolder, "db/rag/lexical");
        File archiveDir = new File(System.getProperty("user.home"),
                ".mainframemate" + File.separator + "archive");

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

                if (CHUNKS_FILE.equals(name)) {
                    callback.onProgress(10, "Lucene-Index importieren…");
                    importLuceneChunks(zis);
                    continue;
                }

                if ("archive-db.sql".equals(name)) {
                    callback.onProgress(30, "Archiv-Datenbank importieren…");
                    importH2Dump(zis);
                    continue;
                }

                if (MANIFEST.equals(name) || ze.isDirectory()) continue;

                if (skipPaths.contains(name)) {
                    count++;
                    continue;
                }

                File target = resolveTarget(name, settingsFolder, luceneDir, archiveDir);
                if (target == null) {
                    count++;
                    continue;
                }

                target.getParentFile().mkdirs();

                try (FileOutputStream fos = new FileOutputStream(target)) {
                    copy(zis, fos);
                }

                count++;
                int pct = 40 + Math.min(55, (int) (55.0 * count / Math.max(total, 1)));
                callback.onProgress(pct, "Datei: " + name);
            }
        }

        callback.onProgress(100, "Import abgeschlossen.");
    }

    // ── Lucene import via service ────────────────────────────

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

            if (!batch.isEmpty()) {
                rag.importChunks(batch);
            }

            LOG.info("[Import] Imported " + total + " chunks into Lucene index");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Import] Failed to import Lucene chunks", e);
        }
    }

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

    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();

        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;

        if (json.charAt(start) == 'n') return null;

        if (json.charAt(start) != '"') return null;
        start++;

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

    // ── H2 import ────────────────────────────────────────────

    private static void importH2Dump(InputStream sqlStream) throws IOException {
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

