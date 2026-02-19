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

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lucene-based lexical (BM25) index for chunk retrieval.
 * Uses in-memory storage with Lucene 8.11.x (Java 8 compatible).
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

    public LuceneLexicalIndex() {
        this.directory = new ByteBuffersDirectory();
        this.analyzer = new StandardAnalyzer();
        initialize();
    }

    private synchronized void initialize() {
        try {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            this.writer = new IndexWriter(directory, config);
            this.available = true;
            LOG.info("Lucene 8.11 index initialized");
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to initialize Lucene index", e);
            this.available = false;
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

            QueryParser parser = new QueryParser(FIELD_TEXT, analyzer);
            parser.setDefaultOperator(QueryParser.Operator.OR);

            // Escape special characters and search
            String escapedQuery = QueryParser.escape(query);
            Query luceneQuery = parser.parse(escapedQuery);

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
            doc.add(new TextField(FIELD_TEXT, chunk.getText(), Field.Store.NO));
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

