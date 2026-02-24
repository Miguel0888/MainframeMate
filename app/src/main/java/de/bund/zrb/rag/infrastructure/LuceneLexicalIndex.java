package de.bund.zrb.rag.infrastructure;

import de.bund.zrb.rag.model.Chunk;
import de.bund.zrb.rag.model.ScoredChunk;
import de.bund.zrb.rag.port.LexicalIndex;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lucene-based lexical (BM25) index for chunk retrieval.
 * Supports both in-memory (ByteBuffersDirectory) and persistent (FSDirectory) storage.
 * Lucene 8.11.x (Java 8 compatible).
 */
public class LuceneLexicalIndex implements LexicalIndex {

    private static final Logger LOG = Logger.getLogger(LuceneLexicalIndex.class.getName());

    private static final String FIELD_CHUNK_ID = "chunkId";
    private static final String FIELD_DOCUMENT_ID = "documentId";
    private static final String FIELD_SOURCE_NAME = "sourceName";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_HEADING = "heading";

    private final Directory directory;
    private final Analyzer analyzer;
    private IndexWriter writer;
    private DirectoryReader reader;
    private IndexSearcher searcher;

    private final Map<String, Chunk> chunkCache = new ConcurrentHashMap<>();
    private boolean available = false;

    /**
     * In-memory index (non-persistent, for tests/backwards compatibility).
     */
    public LuceneLexicalIndex() {
        this.directory = new ByteBuffersDirectory();
        this.analyzer = new StandardAnalyzer();
        initialize(8.0);
    }

    /**
     * Persistent index on disk.
     *
     * @param indexPath path to the directory where the Lucene index is stored
     */
    public LuceneLexicalIndex(Path indexPath) throws IOException {
        indexPath.toFile().mkdirs();
        this.directory = FSDirectory.open(indexPath);
        this.analyzer = new StandardAnalyzer();
        initialize(8.0);
        rebuildCacheFromIndex();
    }

    private synchronized void initialize(double ramBufferMB) {
        try {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            config.setRAMBufferSizeMB(ramBufferMB);
            this.writer = new IndexWriter(directory, config);
            this.writer.commit(); // ensure segments exist for reader
            this.available = true;
            LOG.info("Lucene 8.11 index initialized (ramBuffer=" + ramBufferMB + "MB)");
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to initialize Lucene index", e);
            this.available = false;
        }
    }

    /**
     * Rebuild the in-memory chunk cache from a persisted index.
     */
    private void rebuildCacheFromIndex() {
        try {
            refreshReader();
            if (searcher == null) return;

            int numDocs = searcher.getIndexReader().numDocs();
            if (numDocs == 0) return;

            for (int i = 0; i < searcher.getIndexReader().maxDoc(); i++) {
                try {
                    Document doc = searcher.doc(i);
                    String chunkId = doc.get(FIELD_CHUNK_ID);
                    String docId = doc.get(FIELD_DOCUMENT_ID);
                    String sourceName = doc.get(FIELD_SOURCE_NAME);
                    String text = doc.get(FIELD_TEXT);
                    String heading = doc.get(FIELD_HEADING);

                    if (chunkId != null && docId != null) {
                        Chunk.Builder builder = Chunk.builder()
                                .chunkId(chunkId)
                                .documentId(docId);
                        if (sourceName != null) builder.sourceName(sourceName);
                        if (text != null) builder.text(text);
                        if (heading != null) builder.heading(heading);
                        chunkCache.put(chunkId, builder.build());
                    }
                } catch (Exception e) {
                    // skip deleted or problematic docs
                }
            }
            LOG.info("Rebuilt chunk cache from persisted index: " + chunkCache.size() + " chunks");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to rebuild cache from index", e);
        }
    }

    @Override
    public synchronized void indexChunk(Chunk chunk) {
        if (!available || chunk == null) return;

        try {
            Document doc = createDocument(chunk);
            writer.updateDocument(new Term(FIELD_CHUNK_ID, chunk.getChunkId()), doc);
            chunkCache.put(chunk.getChunkId(), chunk);
            refreshReader();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to index chunk: " + chunk.getChunkId(), e);
        }
    }

    @Override
    public synchronized void indexChunks(List<Chunk> chunks) {
        if (!available || chunks == null || chunks.isEmpty()) return;

        try {
            for (Chunk chunk : chunks) {
                Document doc = createDocument(chunk);
                writer.updateDocument(new Term(FIELD_CHUNK_ID, chunk.getChunkId()), doc);
                chunkCache.put(chunk.getChunkId(), chunk);
            }
            writer.commit();
            refreshReader();
            LOG.info("Indexed " + chunks.size() + " chunks");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to index chunks", e);
        }
    }

    @Override
    public synchronized List<ScoredChunk> search(String query, int topN) {
        if (!available || query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            refreshReader();
            if (searcher == null) {
                return Collections.emptyList();
            }

            Query luceneQuery = buildSmartQuery(query.trim());

            TopDocs topDocs = searcher.search(luceneQuery, topN);
            List<ScoredChunk> results = new ArrayList<>();

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                String chunkId = doc.get(FIELD_CHUNK_ID);
                Chunk chunk = chunkCache.get(chunkId);

                if (chunk != null) {
                    results.add(new ScoredChunk(chunk, scoreDoc.score, ScoredChunk.ScoreSource.LEXICAL));
                }
            }

            return results;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Search failed for query: " + query, e);
            return Collections.emptyList();
        }
    }

    /**
     * Build a smart Lucene query that searches across multiple fields,
     * supports wildcards, and handles short queries gracefully.
     *
     * Strategy:
     * - Short query (1-2 chars): prefix/wildcard search (e.g. "a" → "a*")
     * - Normal query: search text + sourceName + heading with OR
     * - Multi-word: each word searched with OR, boosted if all match
     */
    private Query buildSmartQuery(String queryStr) throws Exception {
        org.apache.lucene.search.BooleanQuery.Builder mainQuery =
                new org.apache.lucene.search.BooleanQuery.Builder();

        String[] words = queryStr.toLowerCase().split("\\s+");

        for (String word : words) {
            org.apache.lucene.search.BooleanQuery.Builder wordQuery =
                    new org.apache.lucene.search.BooleanQuery.Builder();

            if (word.length() <= 2) {
                // Short words: use prefix query (no analyzer, direct match)
                wordQuery.add(new org.apache.lucene.search.PrefixQuery(
                        new Term(FIELD_TEXT, word)), org.apache.lucene.search.BooleanClause.Occur.SHOULD);
                wordQuery.add(new org.apache.lucene.search.PrefixQuery(
                        new Term(FIELD_SOURCE_NAME, word)), org.apache.lucene.search.BooleanClause.Occur.SHOULD);
                wordQuery.add(new org.apache.lucene.search.PrefixQuery(
                        new Term(FIELD_HEADING, word)), org.apache.lucene.search.BooleanClause.Occur.SHOULD);
            } else {
                // Normal words: analyzed term query + wildcard fallback
                // Term query (analyzed – finds exact stems)
                QueryParser textParser = new QueryParser(FIELD_TEXT, analyzer);
                textParser.setDefaultOperator(QueryParser.Operator.OR);
                try {
                    Query textQ = textParser.parse(word);
                    wordQuery.add(textQ, org.apache.lucene.search.BooleanClause.Occur.SHOULD);
                } catch (Exception ignored) {}

                // Source name (file name search)
                QueryParser nameParser = new QueryParser(FIELD_SOURCE_NAME, analyzer);
                try {
                    Query nameQ = nameParser.parse(word);
                    wordQuery.add(nameQ, org.apache.lucene.search.BooleanClause.Occur.SHOULD);
                } catch (Exception ignored) {}

                // Heading
                QueryParser headingParser = new QueryParser(FIELD_HEADING, analyzer);
                try {
                    Query headingQ = headingParser.parse(word);
                    wordQuery.add(headingQ, org.apache.lucene.search.BooleanClause.Occur.SHOULD);
                } catch (Exception ignored) {}

                // Wildcard fallback (for partial matches like "mai" → "mai*")
                wordQuery.add(new org.apache.lucene.search.WildcardQuery(
                        new Term(FIELD_TEXT, word + "*")), org.apache.lucene.search.BooleanClause.Occur.SHOULD);
                wordQuery.add(new org.apache.lucene.search.WildcardQuery(
                        new Term(FIELD_SOURCE_NAME, word + "*")), org.apache.lucene.search.BooleanClause.Occur.SHOULD);
            }

            mainQuery.add(wordQuery.build(), org.apache.lucene.search.BooleanClause.Occur.SHOULD);
        }

        return mainQuery.build();
    }

    @Override
    public synchronized void removeDocument(String documentId) {
        if (!available || documentId == null) return;

        try {
            writer.deleteDocuments(new Term(FIELD_DOCUMENT_ID, documentId));
            writer.commit();

            // Remove from cache
            chunkCache.entrySet().removeIf(e -> documentId.equals(e.getValue().getDocumentId()));
            refreshReader();
            LOG.info("Removed chunks for document: " + documentId);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to remove document: " + documentId, e);
        }
    }

    @Override
    public synchronized void clear() {
        try {
            writer.deleteAll();
            writer.commit();
            chunkCache.clear();
            refreshReader();
            LOG.info("Lucene index cleared");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to clear index", e);
        }
    }

    @Override
    public int size() {
        return chunkCache.size();
    }

    /**
     * List all unique document IDs and their source names in the index.
     * Used for the control panel "show indexed documents" feature.
     *
     * @return map of documentId → sourceName
     */
    public synchronized java.util.Map<String, String> listAllDocuments() {
        java.util.Map<String, String> docs = new java.util.LinkedHashMap<>();
        if (!available) return docs;

        try {
            refreshReader();
            if (searcher == null) return docs;

            for (int i = 0; i < searcher.getIndexReader().maxDoc(); i++) {
                try {
                    Document doc = searcher.doc(i);
                    String docId = doc.get(FIELD_DOCUMENT_ID);
                    String name = doc.get(FIELD_SOURCE_NAME);
                    if (docId != null && !docs.containsKey(docId)) {
                        docs.put(docId, name != null ? name : docId);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to list documents", e);
        }
        return docs;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    private Document createDocument(Chunk chunk) {
        Document doc = new Document();
        doc.add(new StringField(FIELD_CHUNK_ID, chunk.getChunkId(), Field.Store.YES));
        doc.add(new StringField(FIELD_DOCUMENT_ID, chunk.getDocumentId(), Field.Store.YES));

        if (chunk.getSourceName() != null) {
            doc.add(new TextField(FIELD_SOURCE_NAME, chunk.getSourceName(), Field.Store.YES));
        }

        if (chunk.getText() != null) {
            doc.add(new TextField(FIELD_TEXT, chunk.getText(), Field.Store.YES));
        }

        if (chunk.getHeading() != null) {
            doc.add(new TextField(FIELD_HEADING, chunk.getHeading(), Field.Store.YES));
        }

        return doc;
    }

    private void refreshReader() throws IOException {
        if (reader == null) {
            reader = DirectoryReader.open(writer);
            searcher = new IndexSearcher(reader);
        } else {
            DirectoryReader newReader = DirectoryReader.openIfChanged(reader);
            if (newReader != null) {
                reader.close();
                reader = newReader;
                searcher = new IndexSearcher(reader);
            }
        }
    }

    /**
     * Close the index.
     */
    public synchronized void close() {
        try {
            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
            directory.close();
            available = false;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to close index", e);
        }
    }
}

