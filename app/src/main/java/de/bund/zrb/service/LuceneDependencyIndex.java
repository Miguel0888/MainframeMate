package de.bund.zrb.service;

import de.bund.zrb.service.NaturalDependencyService.Dependency;
import de.bund.zrb.service.NaturalDependencyService.DependencyKind;
import de.bund.zrb.service.NaturalDependencyService.DependencyResult;
import de.bund.zrb.service.NaturalDependencyGraph.CallerInfo;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lucene-based persistent cache for Natural dependency graphs.
 * <p>
 * Each dependency edge (caller→callee) is stored as a Lucene document with:
 * <ul>
 *   <li>{@code library} — the Natural library name (StringField, exact match)</li>
 *   <li>{@code caller} — the calling object name (StringField + TextField for search)</li>
 *   <li>{@code callee} — the called/referenced object name (StringField + TextField)</li>
 *   <li>{@code kind} — dependency kind code (CALLNAT, FETCH, USING, etc.)</li>
 *   <li>{@code line} — line number where the reference occurs</li>
 *   <li>{@code detail} — additional info (scope, operation type)</li>
 *   <li>{@code text} — human-readable description for RAG/AI search</li>
 * </ul>
 * <p>
 * Benefits of Lucene storage:
 * <ol>
 *   <li>Persistent — survives app restarts, no re-download needed</li>
 *   <li>Full-text searchable — AI can query "which programs call MYSUBPROG?"</li>
 *   <li>Fast lookups — term queries for exact caller/callee matching</li>
 *   <li>Integrates with existing RAG infrastructure</li>
 * </ol>
 * <p>
 * Index location: {@code ~/.mainframemate/db/dep-graph/}
 * <p>
 * Lucene 8.11.x (Java 8 compatible).
 */
public class LuceneDependencyIndex {

    private static final Logger LOG = Logger.getLogger(LuceneDependencyIndex.class.getName());

    // ── Lucene field names ──
    private static final String F_LIBRARY = "library";
    private static final String F_CALLER = "caller";
    private static final String F_CALLER_SEARCH = "callerSearch";
    private static final String F_CALLEE = "callee";
    private static final String F_CALLEE_SEARCH = "calleeSearch";
    private static final String F_KIND = "kind";
    private static final String F_LINE = "line";
    private static final String F_DETAIL = "detail";
    private static final String F_TEXT = "text";       // Human-readable for RAG
    private static final String F_EDGE_ID = "edgeId";  // Unique: library|caller|callee|kind

    // ── Meta-documents to track which libraries are indexed ──
    private static final String F_DOC_TYPE = "docType";
    private static final String DOC_TYPE_EDGE = "EDGE";
    private static final String DOC_TYPE_META = "META";
    private static final String F_SOURCE_COUNT = "sourceCount";
    private static final String F_EDGE_COUNT = "edgeCount";
    private static final String F_BUILD_TIME = "buildTime";

    private final Directory directory;
    private final Analyzer analyzer;
    private IndexWriter writer;
    private DirectoryReader reader;
    private IndexSearcher searcher;
    private boolean available = false;

    private static LuceneDependencyIndex instance;

    /**
     * Get or create the singleton instance with persistent storage.
     */
    public static synchronized LuceneDependencyIndex getInstance() {
        if (instance == null) {
            instance = new LuceneDependencyIndex();
        }
        return instance;
    }

    /**
     * Create a persistent dependency index under ~/.mainframemate/db/dep-graph/.
     */
    private LuceneDependencyIndex() {
        Path indexPath = Paths.get(System.getProperty("user.home"),
                ".mainframemate", "db", "dep-graph");
        try {
            indexPath.toFile().mkdirs();
            this.directory = FSDirectory.open(indexPath);
            this.analyzer = new StandardAnalyzer();
            initialize();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open dependency index at " + indexPath, e);
        }
    }

    /**
     * In-memory constructor for tests.
     */
    LuceneDependencyIndex(Directory directory) {
        this.directory = directory;
        this.analyzer = new StandardAnalyzer();
        initialize();
    }

    private void initialize() {
        try {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            config.setRAMBufferSizeMB(4.0);
            this.writer = new IndexWriter(directory, config);
            this.writer.commit();
            this.available = true;
            LOG.info("[DepIndex] Lucene dependency index initialized");
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to initialize dependency index", e);
            this.available = false;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Storing a graph
    // ═══════════════════════════════════════════════════════════

    /**
     * Store an entire dependency graph in the index, replacing any previous data
     * for the same library. Also stores a meta-document with build statistics.
     *
     * @param graph the built dependency graph
     */
    public synchronized void storeGraph(NaturalDependencyGraph graph) {
        if (!available || graph == null || !graph.isBuilt()) return;

        String library = graph.getLibrary().toUpperCase();
        LOG.info("[DepIndex] Storing graph for library '" + library + "' ...");

        try {
            // Delete all existing edges for this library
            BooleanQuery deleteQuery = new BooleanQuery.Builder()
                    .add(new TermQuery(new Term(F_LIBRARY, library)), BooleanClause.Occur.MUST)
                    .build();
            writer.deleteDocuments(deleteQuery);

            int edgeCount = 0;

            // Index every dependency edge from active XRefs
            for (String sourceName : graph.getKnownSources()) {
                DependencyResult result = graph.getActiveXRefs(sourceName);
                for (Dependency dep : result.getAllDependencies()) {
                    Document doc = createEdgeDocument(library, sourceName, dep);
                    writer.addDocument(doc);
                    edgeCount++;
                }
            }

            // Store meta-document for this library
            writer.deleteDocuments(new BooleanQuery.Builder()
                    .add(new TermQuery(new Term(F_DOC_TYPE, DOC_TYPE_META)), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term(F_LIBRARY, library)), BooleanClause.Occur.MUST)
                    .build());

            Document meta = new Document();
            meta.add(new StringField(F_DOC_TYPE, DOC_TYPE_META, Field.Store.YES));
            meta.add(new StringField(F_LIBRARY, library, Field.Store.YES));
            meta.add(new StoredField(F_SOURCE_COUNT, graph.getKnownSources().size()));
            meta.add(new StoredField(F_EDGE_COUNT, edgeCount));
            meta.add(new StoredField(F_BUILD_TIME, System.currentTimeMillis()));
            writer.addDocument(meta);

            writer.commit();
            refreshReader();

            LOG.info("[DepIndex] Stored " + edgeCount + " edges for library '" + library
                    + "' (" + graph.getKnownSources().size() + " sources)");

        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to store dependency graph for " + library, e);
        }
    }

    private Document createEdgeDocument(String library, String caller, Dependency dep) {
        Document doc = new Document();

        String callee = dep.getTargetName().toUpperCase();
        String edgeId = library + "|" + caller.toUpperCase() + "|" + callee + "|" + dep.getKind().getCode();

        doc.add(new StringField(F_DOC_TYPE, DOC_TYPE_EDGE, Field.Store.YES));
        doc.add(new StringField(F_EDGE_ID, edgeId, Field.Store.YES));
        doc.add(new StringField(F_LIBRARY, library, Field.Store.YES));
        doc.add(new StringField(F_CALLER, caller.toUpperCase(), Field.Store.YES));
        doc.add(new TextField(F_CALLER_SEARCH, caller.toUpperCase(), Field.Store.NO));
        doc.add(new StringField(F_CALLEE, callee, Field.Store.YES));
        doc.add(new TextField(F_CALLEE_SEARCH, callee, Field.Store.NO));
        doc.add(new StringField(F_KIND, dep.getKind().getCode(), Field.Store.YES));
        doc.add(new StoredField(F_LINE, dep.getLineNumber()));

        if (dep.getDetail() != null) {
            doc.add(new StoredField(F_DETAIL, dep.getDetail()));
        }

        // Human-readable text for RAG/AI search
        String text = caller.toUpperCase() + " " + dep.getKind().getCode() + " " + callee
                + " in Bibliothek " + library;
        if (dep.getDetail() != null) {
            text += " (" + dep.getDetail() + ")";
        }
        doc.add(new TextField(F_TEXT, text, Field.Store.YES));

        return doc;
    }

    // ═══════════════════════════════════════════════════════════
    //  Querying
    // ═══════════════════════════════════════════════════════════

    /**
     * Check whether a library has a cached dependency graph.
     */
    public boolean hasLibrary(String library) {
        if (!available || library == null) return false;
        try {
            ensureSearcher();
            BooleanQuery query = new BooleanQuery.Builder()
                    .add(new TermQuery(new Term(F_DOC_TYPE, DOC_TYPE_META)), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term(F_LIBRARY, library.toUpperCase())), BooleanClause.Occur.MUST)
                    .build();
            TopDocs results = searcher.search(query, 1);
            return results.totalHits.value > 0;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to check library existence: " + library, e);
            return false;
        }
    }

    /**
     * Get the build timestamp for a cached library graph (0 if not found).
     */
    public long getLibraryBuildTime(String library) {
        if (!available || library == null) return 0;
        try {
            ensureSearcher();
            BooleanQuery query = new BooleanQuery.Builder()
                    .add(new TermQuery(new Term(F_DOC_TYPE, DOC_TYPE_META)), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term(F_LIBRARY, library.toUpperCase())), BooleanClause.Occur.MUST)
                    .build();
            TopDocs results = searcher.search(query, 1);
            if (results.totalHits.value > 0) {
                Document doc = searcher.doc(results.scoreDocs[0].doc);
                return doc.getField(F_BUILD_TIME).numericValue().longValue();
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to get build time for " + library, e);
        }
        return 0;
    }

    /**
     * Reconstruct a NaturalDependencyGraph from the cached Lucene data.
     * This avoids re-downloading all sources from the NDV server.
     *
     * @param library the library to restore
     * @return the reconstructed graph, or null if not cached
     */
    public NaturalDependencyGraph restoreGraph(String library) {
        if (!available || library == null) return null;
        String lib = library.toUpperCase();

        if (!hasLibrary(lib)) return null;

        try {
            ensureSearcher();

            // Find all edges for this library
            BooleanQuery query = new BooleanQuery.Builder()
                    .add(new TermQuery(new Term(F_DOC_TYPE, DOC_TYPE_EDGE)), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term(F_LIBRARY, lib)), BooleanClause.Occur.MUST)
                    .build();

            TopDocs results = searcher.search(query, Integer.MAX_VALUE);
            if (results.totalHits.value == 0) return null;

            // Group edges by caller
            Map<String, List<Dependency>> edgesByCaller = new LinkedHashMap<String, List<Dependency>>();

            for (ScoreDoc scoreDoc : results.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                String caller = doc.get(F_CALLER);
                String callee = doc.get(F_CALLEE);
                String kindCode = doc.get(F_KIND);
                int line = doc.getField(F_LINE) != null
                        ? doc.getField(F_LINE).numericValue().intValue() : 0;
                String detail = doc.get(F_DETAIL);

                DependencyKind kind = kindFromCode(kindCode);
                if (kind == null) continue;

                Dependency dep = new Dependency(kind, callee, line, null, detail);

                List<Dependency> callerEdges = edgesByCaller.get(caller);
                if (callerEdges == null) {
                    callerEdges = new ArrayList<Dependency>();
                    edgesByCaller.put(caller, callerEdges);
                }
                callerEdges.add(dep);
            }

            // Build the graph from cached edges
            NaturalDependencyGraph graph = new NaturalDependencyGraph();
            graph.setLibrary(lib);

            // We need to feed sources artificially — the graph normally parses source code.
            // Instead, we directly populate the active XRefs and rebuild passive XRefs.
            for (Map.Entry<String, List<Dependency>> entry : edgesByCaller.entrySet()) {
                graph.addCachedDependencies(entry.getKey(), entry.getValue());
            }

            graph.build();

            LOG.info("[DepIndex] Restored graph for library '" + lib + "' from cache: "
                    + edgesByCaller.size() + " callers, " + results.totalHits.value + " edges");

            return graph;

        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to restore graph for " + library, e);
            return null;
        }
    }

    /**
     * Get all callers of a specific object across all libraries.
     * Useful for AI queries like "who calls MYSUBPROG?".
     *
     * @param objectName the target object name
     * @return list of caller info entries
     */
    public List<CallerInfo> findCallers(String objectName) {
        return findCallers(objectName, null);
    }

    /**
     * Get all callers of a specific object in a specific library.
     *
     * @param objectName the target object name
     * @param library    library to restrict search to (null = all libraries)
     * @return list of caller info entries
     */
    public List<CallerInfo> findCallers(String objectName, String library) {
        if (!available || objectName == null) return Collections.emptyList();

        try {
            ensureSearcher();

            BooleanQuery.Builder qb = new BooleanQuery.Builder()
                    .add(new TermQuery(new Term(F_DOC_TYPE, DOC_TYPE_EDGE)), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term(F_CALLEE, objectName.toUpperCase())), BooleanClause.Occur.MUST);
            if (library != null) {
                qb.add(new TermQuery(new Term(F_LIBRARY, library.toUpperCase())), BooleanClause.Occur.MUST);
            }

            TopDocs results = searcher.search(qb.build(), 1000);
            List<CallerInfo> callers = new ArrayList<CallerInfo>();

            for (ScoreDoc sd : results.scoreDocs) {
                Document doc = searcher.doc(sd.doc);
                String callerName = doc.get(F_CALLER);
                String kindCode = doc.get(F_KIND);
                int line = doc.getField(F_LINE) != null
                        ? doc.getField(F_LINE).numericValue().intValue() : 0;
                DependencyKind kind = kindFromCode(kindCode);
                if (kind != null) {
                    callers.add(new CallerInfo(callerName, kind, line));
                }
            }

            return callers;

        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to find callers for " + objectName, e);
            return Collections.emptyList();
        }
    }

    /**
     * Full-text search in dependency descriptions. For AI/RAG integration.
     * Returns human-readable dependency descriptions matching the query.
     *
     * @param queryText search text (e.g. "CALLNAT MYSUBPROG", "wer ruft PROG1 auf")
     * @param maxResults maximum number of results
     * @return list of matching dependency descriptions
     */
    public List<DependencySearchResult> search(String queryText, int maxResults) {
        if (!available || queryText == null || queryText.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            ensureSearcher();

            org.apache.lucene.queryparser.classic.QueryParser parser =
                    new org.apache.lucene.queryparser.classic.QueryParser(F_TEXT, analyzer);
            parser.setDefaultOperator(org.apache.lucene.queryparser.classic.QueryParser.Operator.OR);

            // Escape special characters in user input
            String escaped = org.apache.lucene.queryparser.classic.QueryParser.escape(queryText.trim());
            Query query = parser.parse(escaped);

            // Wrap with doc-type filter
            BooleanQuery filtered = new BooleanQuery.Builder()
                    .add(new TermQuery(new Term(F_DOC_TYPE, DOC_TYPE_EDGE)), BooleanClause.Occur.MUST)
                    .add(query, BooleanClause.Occur.MUST)
                    .build();

            TopDocs results = searcher.search(filtered, maxResults);
            List<DependencySearchResult> searchResults = new ArrayList<DependencySearchResult>();

            for (ScoreDoc sd : results.scoreDocs) {
                Document doc = searcher.doc(sd.doc);
                searchResults.add(new DependencySearchResult(
                        doc.get(F_LIBRARY),
                        doc.get(F_CALLER),
                        doc.get(F_CALLEE),
                        doc.get(F_KIND),
                        doc.get(F_TEXT),
                        sd.score
                ));
            }

            return searchResults;

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Dependency search failed for: " + queryText, e);
            return Collections.emptyList();
        }
    }

    /**
     * List all cached libraries.
     */
    public List<String> listCachedLibraries() {
        if (!available) return Collections.emptyList();

        try {
            ensureSearcher();
            Query query = new TermQuery(new Term(F_DOC_TYPE, DOC_TYPE_META));
            TopDocs results = searcher.search(query, 1000);

            List<String> libraries = new ArrayList<String>();
            for (ScoreDoc sd : results.scoreDocs) {
                Document doc = searcher.doc(sd.doc);
                libraries.add(doc.get(F_LIBRARY));
            }
            return libraries;

        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to list cached libraries", e);
            return Collections.emptyList();
        }
    }

    /**
     * Remove cached data for a library.
     */
    public synchronized void removeLibrary(String library) {
        if (!available || library == null) return;
        try {
            writer.deleteDocuments(new TermQuery(new Term(F_LIBRARY, library.toUpperCase())));
            writer.commit();
            refreshReader();
            LOG.info("[DepIndex] Removed cached data for library: " + library);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to remove library: " + library, e);
        }
    }

    /**
     * Close the index (call on app shutdown).
     */
    public synchronized void close() {
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            directory.close();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to close dependency index", e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Internal helpers
    // ═══════════════════════════════════════════════════════════

    private void ensureSearcher() throws IOException {
        refreshReader();
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

    private static DependencyKind kindFromCode(String code) {
        if (code == null) return null;
        for (DependencyKind kind : DependencyKind.values()) {
            if (kind.getCode().equals(code)) return kind;
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  Search result model
    // ═══════════════════════════════════════════════════════════

    /**
     * A search result from the dependency index (for AI/RAG).
     */
    public static class DependencySearchResult {
        private final String library;
        private final String caller;
        private final String callee;
        private final String kind;
        private final String text;
        private final float score;

        public DependencySearchResult(String library, String caller, String callee,
                                      String kind, String text, float score) {
            this.library = library;
            this.caller = caller;
            this.callee = callee;
            this.kind = kind;
            this.text = text;
            this.score = score;
        }

        public String getLibrary() { return library; }
        public String getCaller() { return caller; }
        public String getCallee() { return callee; }
        public String getKind() { return kind; }
        public String getText() { return text; }
        public float getScore() { return score; }

        @Override
        public String toString() {
            return text + " [score=" + score + "]";
        }
    }
}

