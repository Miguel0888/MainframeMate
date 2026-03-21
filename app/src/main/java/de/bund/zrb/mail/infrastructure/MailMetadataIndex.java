package de.bund.zrb.mail.infrastructure;

import de.bund.zrb.helper.SettingsHelper;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dedicated Lucene index for mail metadata — optimised for sorted listing and
 * fast faceted queries over 70k+ mails without loading them all into memory.
 * <p>
 * Stored fields per document:
 * <ul>
 *   <li>{@code itemPath}  — bookmark key: {@code mailboxPath#folderPath#nodeId} (unique)</li>
 *   <li>{@code mailboxPath} — path to OST/PST file</li>
 *   <li>{@code folderPath}  — folder inside the store</li>
 *   <li>{@code nodeId}      — PST descriptor node ID (stored as string for retrieval)</li>
 *   <li>{@code subject}     — mail subject (full-text searchable)</li>
 *   <li>{@code sender}      — sender address/name (full-text searchable)</li>
 *   <li>{@code recipients}  — comma-separated recipients (full-text searchable)</li>
 *   <li>{@code deliveryTime} — epoch millis (LongPoint for range + NumericDocValuesField for sort)</li>
 *   <li>{@code messageClass} — IPM.Note / IPM.Appointment / etc.</li>
 *   <li>{@code hasAttachments} — "1" or "0"</li>
 *   <li>{@code size}          — approximate size in bytes</li>
 * </ul>
 * <p>
 * Index location: {@code ~/.mainframemate/db/mail-metadata/}
 */
public class MailMetadataIndex implements Closeable {

    private static final Logger LOG = Logger.getLogger(MailMetadataIndex.class.getName());

    // ── Field names ──
    static final String F_ITEM_PATH      = "itemPath";
    static final String F_MAILBOX_PATH   = "mailboxPath";
    static final String F_FOLDER_PATH    = "folderPath";
    static final String F_NODE_ID        = "nodeId";
    static final String F_SUBJECT        = "subject";
    static final String F_SENDER         = "sender";
    static final String F_RECIPIENTS     = "recipients";
    static final String F_DELIVERY_TIME  = "deliveryTime";
    static final String F_MESSAGE_CLASS  = "messageClass";
    static final String F_HAS_ATTACHMENTS = "hasAttachments";
    static final String F_SIZE           = "size";

    // Singleton
    private static volatile MailMetadataIndex INSTANCE;

    private final Directory directory;
    private final Analyzer analyzer;
    private IndexWriter writer;
    private DirectoryReader reader;
    private IndexSearcher searcher;
    private boolean available;

    // ═══════════════════════════════════════════════════════════════
    //  Singleton access
    // ═══════════════════════════════════════════════════════════════

    public static MailMetadataIndex getInstance() {
        if (INSTANCE == null) {
            synchronized (MailMetadataIndex.class) {
                if (INSTANCE == null) {
                    INSTANCE = createPersistent();
                }
            }
        }
        return INSTANCE;
    }

    private static MailMetadataIndex createPersistent() {
        try {
            File settingsFolder = SettingsHelper.getSettingsFolder();
            Path indexPath = new File(settingsFolder, "db/mail-metadata").toPath();
            return new MailMetadataIndex(indexPath);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to create mail metadata index", e);
            // Return an unavailable stub so callers don't NPE
            return new MailMetadataIndex();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Construction
    // ═══════════════════════════════════════════════════════════════

    /** Persistent index on disk. */
    MailMetadataIndex(Path indexPath) throws IOException {
        indexPath.toFile().mkdirs();
        this.directory = FSDirectory.open(indexPath);
        this.analyzer = createAnalyzer();
        initWriter();
    }

    /** Unavailable fallback. */
    private MailMetadataIndex() {
        this.directory = null;
        this.analyzer = null;
        this.available = false;
    }

    private void initWriter() throws IOException {
        IndexWriterConfig cfg = new IndexWriterConfig(analyzer);
        cfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        cfg.setRAMBufferSizeMB(8.0);
        this.writer = new IndexWriter(directory, cfg);
        this.writer.commit();
        this.available = true;
        LOG.info("[MailMetadataIndex] Initialised — docs: " + writer.getDocStats().numDocs);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Indexing
    // ═══════════════════════════════════════════════════════════════

    /**
     * Index (or update) a single mail's metadata.
     */
    public synchronized void index(MailMetadataEntry entry) {
        if (!available || entry == null) return;
        try {
            Document doc = toDocument(entry);
            writer.updateDocument(new Term(F_ITEM_PATH, entry.itemPath), doc);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[MailMetadataIndex] index failed: " + entry.itemPath, e);
        }
    }

    /**
     * Batch-index a list of entries, then commit once.
     */
    public synchronized void indexBatch(List<MailMetadataEntry> entries) {
        if (!available || entries == null || entries.isEmpty()) return;
        try {
            for (MailMetadataEntry entry : entries) {
                Document doc = toDocument(entry);
                writer.updateDocument(new Term(F_ITEM_PATH, entry.itemPath), doc);
            }
            writer.commit();
            refreshReader();
            LOG.info("[MailMetadataIndex] Batch indexed " + entries.size() + " mails");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[MailMetadataIndex] batch index failed", e);
        }
    }

    /**
     * Commit pending writes and refresh the reader.
     */
    public synchronized void flush() {
        if (!available) return;
        try {
            writer.commit();
            refreshReader();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[MailMetadataIndex] flush failed", e);
        }
    }

    /**
     * Remove a single mail by item path.
     */
    public synchronized void remove(String itemPath) {
        if (!available || itemPath == null) return;
        try {
            writer.deleteDocuments(new Term(F_ITEM_PATH, itemPath));
            writer.commit();
            refreshReader();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[MailMetadataIndex] remove failed: " + itemPath, e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Queries
    // ═══════════════════════════════════════════════════════════════

    /**
     * List mails in a specific folder, sorted by delivery time.
     *
     * @param mailboxPath   OST/PST path
     * @param folderPath    folder inside the store
     * @param ascending     true = oldest first, false = newest first
     * @param offset        paging offset
     * @param limit         page size
     * @return ordered list of metadata entries
     */
    public synchronized List<MailMetadataEntry> listByFolder(
            String mailboxPath, String folderPath,
            boolean ascending, int offset, int limit) {

        if (!available) return Collections.emptyList();
        try {
            refreshReader();
            if (searcher == null) return Collections.emptyList();

            BooleanQuery.Builder qb = new BooleanQuery.Builder();
            qb.add(new TermQuery(new Term(F_MAILBOX_PATH, mailboxPath)), BooleanClause.Occur.MUST);
            qb.add(new TermQuery(new Term(F_FOLDER_PATH, folderPath)), BooleanClause.Occur.MUST);
            Query query = qb.build();

            Sort sort = new Sort(new SortField(F_DELIVERY_TIME, SortField.Type.LONG, !ascending));
            int total = offset + limit;
            TopDocs topDocs = searcher.search(query, total, sort);

            return extractEntries(topDocs, offset);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[MailMetadataIndex] listByFolder failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * Count mails in a specific folder.
     */
    public synchronized int countByFolder(String mailboxPath, String folderPath) {
        if (!available) return 0;
        try {
            refreshReader();
            if (searcher == null) return 0;

            BooleanQuery.Builder qb = new BooleanQuery.Builder();
            qb.add(new TermQuery(new Term(F_MAILBOX_PATH, mailboxPath)), BooleanClause.Occur.MUST);
            qb.add(new TermQuery(new Term(F_FOLDER_PATH, folderPath)), BooleanClause.Occur.MUST);

            return searcher.count(qb.build());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[MailMetadataIndex] countByFolder failed", e);
            return 0;
        }
    }

    /**
     * Full-text search over subject, sender, and recipients — sorted by delivery time.
     *
     * @param queryText   user query (supports simple terms)
     * @param ascending   sort direction
     * @param maxResults  max hits
     * @return matching entries ordered by delivery time
     */
    public synchronized List<MailMetadataEntry> search(
            String queryText, boolean ascending, int maxResults) {

        if (!available || queryText == null || queryText.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            refreshReader();
            if (searcher == null) return Collections.emptyList();

            // Build a BooleanQuery with SHOULD across text fields
            BooleanQuery.Builder qb = new BooleanQuery.Builder();
            String[] terms = queryText.trim().toLowerCase().split("\\s+");
            for (String term : terms) {
                BooleanQuery.Builder termQ = new BooleanQuery.Builder();
                termQ.add(new WildcardQuery(new Term(F_SUBJECT, "*" + term + "*")), BooleanClause.Occur.SHOULD);
                termQ.add(new WildcardQuery(new Term(F_SENDER, "*" + term + "*")), BooleanClause.Occur.SHOULD);
                termQ.add(new WildcardQuery(new Term(F_RECIPIENTS, "*" + term + "*")), BooleanClause.Occur.SHOULD);
                qb.add(termQ.build(), BooleanClause.Occur.MUST);
            }

            Sort sort = new Sort(new SortField(F_DELIVERY_TIME, SortField.Type.LONG, !ascending));
            TopDocs topDocs = searcher.search(qb.build(), maxResults, sort);
            return extractEntries(topDocs, 0);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[MailMetadataIndex] search failed: " + queryText, e);
            return Collections.emptyList();
        }
    }

    /**
     * Full-text search scoped to a specific folder — sorted by delivery time.
     */
    public synchronized List<MailMetadataEntry> searchInFolder(
            String mailboxPath, String folderPath,
            String queryText, boolean ascending, int maxResults) {

        if (!available || queryText == null || queryText.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            refreshReader();
            if (searcher == null) return Collections.emptyList();

            BooleanQuery.Builder qb = new BooleanQuery.Builder();
            qb.add(new TermQuery(new Term(F_MAILBOX_PATH, mailboxPath)), BooleanClause.Occur.MUST);
            qb.add(new TermQuery(new Term(F_FOLDER_PATH, folderPath)), BooleanClause.Occur.MUST);

            String[] terms = queryText.trim().toLowerCase().split("\\s+");
            for (String term : terms) {
                BooleanQuery.Builder termQ = new BooleanQuery.Builder();
                termQ.add(new WildcardQuery(new Term(F_SUBJECT, "*" + term + "*")), BooleanClause.Occur.SHOULD);
                termQ.add(new WildcardQuery(new Term(F_SENDER, "*" + term + "*")), BooleanClause.Occur.SHOULD);
                termQ.add(new WildcardQuery(new Term(F_RECIPIENTS, "*" + term + "*")), BooleanClause.Occur.SHOULD);
                qb.add(termQ.build(), BooleanClause.Occur.MUST);
            }

            Sort sort = new Sort(new SortField(F_DELIVERY_TIME, SortField.Type.LONG, !ascending));
            TopDocs topDocs = searcher.search(qb.build(), maxResults, sort);
            return extractEntries(topDocs, 0);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[MailMetadataIndex] searchInFolder failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * Check if a mail with the given item path is already indexed.
     */
    public synchronized boolean contains(String itemPath) {
        if (!available || itemPath == null) return false;
        try {
            refreshReader();
            if (searcher == null) return false;
            TopDocs td = searcher.search(new TermQuery(new Term(F_ITEM_PATH, itemPath)), 1);
            return td.totalHits.value > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Total number of indexed mails.
     */
    public synchronized int size() {
        if (!available) return 0;
        try {
            refreshReader();
            return reader != null ? reader.numDocs() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  MailMetadataEntry — lightweight value object
    // ═══════════════════════════════════════════════════════════════

    /**
     * A lightweight metadata record for one mail — all fields from the index.
     */
    public static class MailMetadataEntry {
        public final String itemPath;
        public final String mailboxPath;
        public final String folderPath;
        public final long nodeId;
        public final String subject;
        public final String sender;
        public final String recipients;
        public final long deliveryTimeMillis;
        public final String messageClass;
        public final boolean hasAttachments;
        public final long size;

        public MailMetadataEntry(String itemPath, String mailboxPath, String folderPath, long nodeId,
                                 String subject, String sender, String recipients,
                                 long deliveryTimeMillis, String messageClass,
                                 boolean hasAttachments, long size) {
            this.itemPath = itemPath;
            this.mailboxPath = mailboxPath;
            this.folderPath = folderPath;
            this.nodeId = nodeId;
            this.subject = subject;
            this.sender = sender;
            this.recipients = recipients;
            this.deliveryTimeMillis = deliveryTimeMillis;
            this.messageClass = messageClass;
            this.hasAttachments = hasAttachments;
            this.size = size;
        }

        /** Build a bookmark URI: {@code mail://mailboxPath#folderPath#nodeId} */
        public String toBookmarkUri() {
            return "mail://" + mailboxPath + "#" + folderPath + "#" + nodeId;
        }

        /** Delivery time as Date (may be null if 0). */
        public Date getDeliveryDate() {
            return deliveryTimeMillis > 0 ? new Date(deliveryTimeMillis) : null;
        }

        /**
         * Returns a display string similar to {@link de.bund.zrb.mail.model.MailMessageHeader#toString()}.
         */
        public String toDisplayString() {
            String icon = getTypeIcon();
            String subj = subject != null && !subject.isEmpty() ? subject : "(kein Betreff)";
            String dateStr = deliveryTimeMillis > 0
                    ? String.format("%1$td.%1$tm.%1$tY %1$tH:%1$tM", new Date(deliveryTimeMillis))
                    : "";
            String from = sender != null ? sender : "";
            return icon + " " + dateStr + "  " + from + " – " + subj;
        }

        private String getTypeIcon() {
            if (messageClass == null) return "✉";
            String mc = messageClass.toUpperCase();
            if (mc.startsWith("IPM.APPOINTMENT")) return "📅";
            if (mc.startsWith("IPM.CONTACT")) return "👤";
            if (mc.startsWith("IPM.TASK")) return "✅";
            if (mc.startsWith("IPM.STICKYNOTE")) return "📝";
            if (mc.startsWith("REPORT.")) return "📨";
            if (hasAttachments) return "📎";
            return "✉";
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Internal helpers
    // ═══════════════════════════════════════════════════════════════

    private Document toDocument(MailMetadataEntry e) {
        Document doc = new Document();
        // Unique key (exact match)
        doc.add(new StringField(F_ITEM_PATH, e.itemPath, Field.Store.YES));
        // Filter fields (exact match)
        doc.add(new StringField(F_MAILBOX_PATH, e.mailboxPath, Field.Store.YES));
        doc.add(new StringField(F_FOLDER_PATH, e.folderPath, Field.Store.YES));
        doc.add(new StringField(F_NODE_ID, String.valueOf(e.nodeId), Field.Store.YES));

        // Text-searchable fields (lowercased via analyzer)
        doc.add(new TextField(F_SUBJECT, safe(e.subject), Field.Store.YES));
        doc.add(new TextField(F_SENDER, safe(e.sender), Field.Store.YES));
        doc.add(new TextField(F_RECIPIENTS, safe(e.recipients), Field.Store.YES));

        // Date: LongPoint for range queries + NumericDocValuesField for sorting + StoredField for retrieval
        long millis = e.deliveryTimeMillis;
        doc.add(new LongPoint(F_DELIVERY_TIME, millis));
        doc.add(new NumericDocValuesField(F_DELIVERY_TIME, millis));
        doc.add(new StoredField(F_DELIVERY_TIME, millis));

        // Additional metadata
        doc.add(new StringField(F_MESSAGE_CLASS, safe(e.messageClass), Field.Store.YES));
        doc.add(new StringField(F_HAS_ATTACHMENTS, e.hasAttachments ? "1" : "0", Field.Store.YES));
        doc.add(new StoredField(F_SIZE, e.size));

        return doc;
    }

    private MailMetadataEntry fromDocument(Document doc) {
        String itemPath = doc.get(F_ITEM_PATH);
        String mailboxPath = doc.get(F_MAILBOX_PATH);
        String folderPath = doc.get(F_FOLDER_PATH);
        long nodeId = parseLong(doc.get(F_NODE_ID), 0);
        String subject = doc.get(F_SUBJECT);
        String sender = doc.get(F_SENDER);
        String recipients = doc.get(F_RECIPIENTS);

        // Retrieve stored long value for delivery time
        IndexableField dtField = doc.getField(F_DELIVERY_TIME);
        long deliveryTime = dtField != null && dtField.numericValue() != null
                ? dtField.numericValue().longValue() : 0;

        String messageClass = doc.get(F_MESSAGE_CLASS);
        boolean hasAtt = "1".equals(doc.get(F_HAS_ATTACHMENTS));

        IndexableField sizeField = doc.getField(F_SIZE);
        long size = sizeField != null && sizeField.numericValue() != null
                ? sizeField.numericValue().longValue() : 0;

        return new MailMetadataEntry(itemPath, mailboxPath, folderPath, nodeId,
                subject, sender, recipients, deliveryTime, messageClass, hasAtt, size);
    }

    private List<MailMetadataEntry> extractEntries(TopDocs topDocs, int offset) throws IOException {
        List<MailMetadataEntry> results = new ArrayList<>();
        ScoreDoc[] hits = topDocs.scoreDocs;
        for (int i = offset; i < hits.length; i++) {
            Document doc = searcher.doc(hits[i].doc);
            results.add(fromDocument(doc));
        }
        return results;
    }

    private synchronized void refreshReader() throws IOException {
        if (writer == null) return;
        if (reader == null) {
            reader = DirectoryReader.open(writer);
            searcher = new IndexSearcher(reader);
        } else {
            DirectoryReader newReader = DirectoryReader.openIfChanged(reader, writer);
            if (newReader != null) {
                reader.close();
                reader = newReader;
                searcher = new IndexSearcher(reader);
            }
        }
    }

    private static Analyzer createAnalyzer() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                StandardTokenizer tokenizer = new StandardTokenizer();
                int flags = WordDelimiterGraphFilter.GENERATE_WORD_PARTS
                        | WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS
                        | WordDelimiterGraphFilter.SPLIT_ON_CASE_CHANGE
                        | WordDelimiterGraphFilter.SPLIT_ON_NUMERICS
                        | WordDelimiterGraphFilter.PRESERVE_ORIGINAL;
                TokenStream stream = new WordDelimiterGraphFilter(tokenizer, flags, null);
                stream = new LowerCaseFilter(stream);
                stream = new org.apache.lucene.analysis.core.FlattenGraphFilter(stream);
                return new TokenStreamComponents(tokenizer, stream);
            }
        };
    }

    @Override
    public synchronized void close() {
        try {
            if (reader != null) { reader.close(); reader = null; }
            if (writer != null) { writer.close(); writer = null; }
            if (directory != null) { directory.close(); }
            available = false;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[MailMetadataIndex] close failed", e);
        }
    }

    private static String safe(String s) { return s != null ? s : ""; }

    private static long parseLong(String s, long defaultValue) {
        if (s == null || s.isEmpty()) return defaultValue;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

