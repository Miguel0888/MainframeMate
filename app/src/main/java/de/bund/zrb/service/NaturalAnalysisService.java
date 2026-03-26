package de.bund.zrb.service;

import de.bund.zrb.jcl.model.JclOutlineModel;
import de.bund.zrb.jcl.parser.NaturalParser;
import de.bund.zrb.service.NaturalDependencyService.Dependency;
import de.bund.zrb.service.NaturalDependencyService.DependencyKind;
import de.bund.zrb.service.NaturalDependencyService.DependencyResult;
import de.bund.zrb.service.NaturalDependencyGraph.CallHierarchyNode;
import de.bund.zrb.service.NaturalDependencyGraph.CallerInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central service for all Natural source code analysis.
 * <p>
 * Consolidates {@link NaturalParser}, {@link NaturalDependencyService},
 * {@link NaturalDependencyGraph}, and {@link LuceneDependencyIndex} behind
 * a single API that can be used from:
 * <ul>
 *   <li>UI components (TabbedPaneManager, LeftDrawer)</li>
 *   <li>MCP tools (AI queries)</li>
 *   <li>Chat context builders (RAG)</li>
 *   <li>Background tasks (NdvSourceCacheService)</li>
 * </ul>
 * <p>
 * Thread-safe singleton — parse and dependency operations are stateless;
 * graph management is protected by ConcurrentHashMap.
 */
public class NaturalAnalysisService {

    private static final Logger LOG = Logger.getLogger(NaturalAnalysisService.class.getName());

    private static volatile NaturalAnalysisService instance;

    private final NaturalParser parser = new NaturalParser();
    private final NaturalDependencyService dependencyService = new NaturalDependencyService();

    /** In-memory dependency graphs per library (uppercase key). */
    private final ConcurrentHashMap<String, NaturalDependencyGraph> graphs =
            new ConcurrentHashMap<String, NaturalDependencyGraph>();

    public static synchronized NaturalAnalysisService getInstance() {
        if (instance == null) {
            instance = new NaturalAnalysisService();
        }
        return instance;
    }

    private NaturalAnalysisService() {
    }

    // ═══════════════════════════════════════════════════════════
    //  Parsing
    // ═══════════════════════════════════════════════════════════

    /**
     * Parse Natural source code and return the outline model.
     * Usable for Outline view, dependency extraction, and AI context.
     */
    public JclOutlineModel parse(String sourceCode, String sourceName) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            JclOutlineModel empty = new JclOutlineModel();
            empty.setSourceName(sourceName);
            empty.setLanguage(JclOutlineModel.Language.NATURAL);
            return empty;
        }
        return parser.parse(sourceCode, sourceName);
    }

    // ═══════════════════════════════════════════════════════════
    //  Single-source dependency analysis
    // ═══════════════════════════════════════════════════════════

    /**
     * Analyze a single Natural source and extract all dependencies.
     *
     * @param sourceCode the source text
     * @param sourceName object name (for display)
     * @return dependency result (never null)
     */
    public DependencyResult analyzeDependencies(String sourceCode, String sourceName) {
        return dependencyService.analyze(sourceCode, sourceName);
    }

    // ═══════════════════════════════════════════════════════════
    //  Library-wide dependency graph management
    // ═══════════════════════════════════════════════════════════

    /**
     * Get the dependency graph for a library.
     * Checks in-memory cache first, then Lucene persistent cache.
     *
     * @param library library name
     * @return the graph or null if not available
     */
    public NaturalDependencyGraph getGraph(String library) {
        if (library == null || library.isEmpty()) return null;
        String key = library.toUpperCase();

        // In-memory first
        NaturalDependencyGraph graph = graphs.get(key);
        if (graph != null) return graph;

        // Try Lucene persistent cache
        try {
            graph = LuceneDependencyIndex.getInstance().restoreGraph(key);
            if (graph != null) {
                graphs.put(key, graph);
                return graph;
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to restore graph from Lucene: " + key, e);
        }

        return null;
    }

    /**
     * Register an externally-built graph (e.g. from NdvConnectionTab background build).
     */
    public void registerGraph(String library, NaturalDependencyGraph graph) {
        if (library != null && graph != null) {
            graphs.put(library.toUpperCase(), graph);
        }
    }

    /**
     * Remove a graph from in-memory cache (called when user clears NDV cache).
     */
    public void removeGraph(String library) {
        if (library != null) {
            graphs.remove(library.toUpperCase());
        }
    }

    /**
     * Build (or rebuild) a dependency graph for a library from source code map.
     * Persists the result to Lucene for offline availability and AI search.
     *
     * @param library library name
     * @param sources map of objectName → sourceCode
     * @return the built graph
     */
    public NaturalDependencyGraph buildGraph(String library, Map<String, String> sources) {
        NaturalDependencyGraph graph = new NaturalDependencyGraph();
        graph.setLibrary(library);

        for (Map.Entry<String, String> entry : sources.entrySet()) {
            graph.addSource(library, entry.getKey(), entry.getValue());
        }
        graph.build();

        graphs.put(library.toUpperCase(), graph);

        // Persist to Lucene
        try {
            LuceneDependencyIndex.getInstance().storeGraph(graph);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to persist graph to Lucene: " + library, e);
        }

        return graph;
    }

    // ═══════════════════════════════════════════════════════════
    //  Queries (for UI + MCP tools)
    // ═══════════════════════════════════════════════════════════

    /**
     * Get active XRefs (what this program calls) from the graph.
     */
    public DependencyResult getActiveXRefs(String library, String objectName) {
        NaturalDependencyGraph graph = getGraph(library);
        if (graph == null) return new DependencyResult(objectName, Collections.<Dependency>emptyList());
        return graph.getActiveXRefs(objectName);
    }

    /**
     * Get passive XRefs (who calls this program) from the graph.
     */
    public List<CallerInfo> getPassiveXRefs(String library, String objectName) {
        NaturalDependencyGraph graph = getGraph(library);
        if (graph == null) return Collections.emptyList();
        return graph.getPassiveXRefs(objectName);
    }

    /**
     * Get passive XRefs grouped by dependency kind.
     */
    public Map<DependencyKind, List<CallerInfo>> getPassiveXRefsGrouped(String library, String objectName) {
        NaturalDependencyGraph graph = getGraph(library);
        if (graph == null) return Collections.emptyMap();
        return graph.getPassiveXRefsGrouped(objectName);
    }

    /**
     * Get full call hierarchy (callees or callers) starting from the given object.
     *
     * @param library    library name
     * @param objectName starting object
     * @param callees    true = what this calls, false = who calls this
     * @param maxDepth   max recursion depth
     * @return root node of the hierarchy tree (never null)
     */
    public CallHierarchyNode getCallHierarchy(String library, String objectName,
                                               boolean callees, int maxDepth) {
        NaturalDependencyGraph graph = getGraph(library);
        if (graph == null || !graph.isBuilt()) {
            return new CallHierarchyNode(objectName.toUpperCase(), null, 0, false);
        }
        return graph.getCallHierarchy(objectName, callees, maxDepth);
    }

    // ═══════════════════════════════════════════════════════════
    //  AI context building — human-readable summaries
    // ═══════════════════════════════════════════════════════════

    /**
     * Build a comprehensive AI-readable summary of a Natural program's dependencies.
     * Includes active XRefs, passive XRefs, data areas, maps, DB access, and call chain info.
     * Designed to be injected into LLM context for questions about the program.
     *
     * @param sourceCode the program source code
     * @param objectName the program name
     * @param library    the library name (for graph lookups, may be null)
     * @return human-readable dependency summary
     */
    public String buildAiDependencySummary(String sourceCode, String objectName, String library) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== NATURAL DEPENDENCY ANALYSIS: ").append(objectName.toUpperCase()).append(" ===\n");
        if (library != null) {
            sb.append("Library: ").append(library.toUpperCase()).append("\n");
        }
        sb.append("\n");

        // 1) Parse and analyze the source
        DependencyResult result = analyzeDependencies(sourceCode, objectName);

        if (result.isEmpty()) {
            sb.append("No dependencies found.\n");
            return sb.toString();
        }

        // 2) Active XRefs (what this program calls), grouped by kind
        sb.append("--- Active XRefs (what this program references) ---\n");
        for (Map.Entry<DependencyKind, List<Dependency>> group : result.getGrouped().entrySet()) {
            DependencyKind kind = group.getKey();
            List<Dependency> deps = group.getValue();
            sb.append(kind.getDisplayLabel()).append(" (").append(deps.size()).append("):\n");
            for (Dependency dep : deps) {
                sb.append("  • ").append(dep.getTargetName());
                if (dep.getDetail() != null && !dep.getDetail().isEmpty()) {
                    sb.append(" (").append(dep.getDetail()).append(")");
                }
                sb.append(" [Zeile ").append(dep.getLineNumber()).append("]\n");
            }
        }

        // 3) Passive XRefs from graph (who calls this)
        if (library != null) {
            NaturalDependencyGraph graph = getGraph(library);
            if (graph != null && graph.isBuilt()) {
                Map<DependencyKind, List<CallerInfo>> callerGroups =
                        graph.getPassiveXRefsGrouped(objectName);
                if (!callerGroups.isEmpty()) {
                    sb.append("\n--- Passive XRefs (who calls this program) ---\n");
                    for (Map.Entry<DependencyKind, List<CallerInfo>> cg : callerGroups.entrySet()) {
                        DependencyKind kind = cg.getKey();
                        List<CallerInfo> callers = cg.getValue();
                        sb.append(kind.getCode()).append(" (").append(callers.size()).append("):\n");
                        for (CallerInfo caller : callers) {
                            sb.append("  • ").append(caller.getCallerName())
                              .append(" [Zeile ").append(caller.getLineNumber()).append("]\n");
                        }
                    }
                }

                // 4) Entry point detection
                List<String> entryPoints = graph.getEntryPoints();
                if (entryPoints.contains(objectName.toUpperCase())) {
                    sb.append("\n⚡ This program is an ENTRY POINT (not called by any other program in this library).\n");
                }

                // 5) Unresolved references
                Set<String> unresolved = graph.getUnresolvedTargets();
                List<String> myUnresolved = new ArrayList<String>();
                for (Dependency dep : result.getAllDependencies()) {
                    if (unresolved.contains(dep.getTargetName().toUpperCase())) {
                        myUnresolved.add(dep.getTargetName());
                    }
                }
                if (!myUnresolved.isEmpty()) {
                    sb.append("\n⚠ Unresolved references (target not in library): ");
                    sb.append(myUnresolved).append("\n");
                }
            }
        }

        // 6) Summary stats
        sb.append("\nSummary: ").append(result.getTotalCount()).append(" total dependencies");
        sb.append(", ").append(result.getExternalCallTargets().size()).append(" external calls");
        sb.append(", ").append(result.getDataAreaTargets().size()).append(" data areas");
        sb.append(", ").append(result.getCopycodeTargets().size()).append(" copycodes\n");
        sb.append("=== END DEPENDENCY ANALYSIS ===\n");

        return sb.toString();
    }

    /**
     * Build a short AI-readable call chain summary for a program.
     * Shows 2 levels of callees and callers.
     *
     * @param library    the library name
     * @param objectName the program name
     * @return human-readable call chain summary or empty string if no graph
     */
    public String buildAiCallChainSummary(String library, String objectName) {
        NaturalDependencyGraph graph = getGraph(library);
        if (graph == null || !graph.isBuilt()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== CALL CHAIN: ").append(objectName.toUpperCase()).append(" ===\n");

        // Callees (max depth 3)
        CallHierarchyNode callees = graph.getCallHierarchy(objectName, true, 3);
        if (!callees.getChildren().isEmpty()) {
            sb.append("Calls (outgoing):\n");
            appendHierarchyTree(sb, callees, "  ", true);
        }

        // Callers (max depth 3)
        CallHierarchyNode callers = graph.getCallHierarchy(objectName, false, 3);
        if (!callers.getChildren().isEmpty()) {
            sb.append("Called by (incoming):\n");
            appendHierarchyTree(sb, callers, "  ", true);
        }

        if (callees.getChildren().isEmpty() && callers.getChildren().isEmpty()) {
            sb.append("No call chain data available.\n");
        }

        sb.append("=== END CALL CHAIN ===\n");
        return sb.toString();
    }

    private void appendHierarchyTree(StringBuilder sb, CallHierarchyNode node,
                                     String indent, boolean isRoot) {
        if (!isRoot) {
            sb.append(indent).append("→ ").append(node.getObjectName());
            if (node.getReferenceKind() != null) {
                sb.append(" [").append(node.getReferenceKind().getCode()).append("]");
            }
            if (node.isRecursive()) {
                sb.append(" 🔄 (recursive)");
            }
            sb.append("\n");
        }

        if (!node.isRecursive()) {
            for (CallHierarchyNode child : node.getChildren()) {
                appendHierarchyTree(sb, child, indent + "  ", false);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Heuristic: is this Natural source?
    // ═══════════════════════════════════════════════════════════

    /**
     * Natural source file extensions (uppercase, without dot). NSD excluded (DDM).
     */
    private static final Set<String> NATURAL_EXTENSIONS = new HashSet<String>(Arrays.asList(
            "NSP", "NSN", "NSS", "NSH", "NSC", "NSL", "NSA", "NSG", "NSM",
            "NS3", "NS4", "NS5", "NS6", "NS7", "NS8", "NAT"
    ));

    /**
     * Detect whether a file path is a Natural source by extension.
     *
     * @param path file path (e.g. "MYLIB/PROG.NSC")
     * @return true if the extension is a known Natural source extension
     */
    public boolean isNaturalFile(String path) {
        if (path == null) return false;
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return false;
        String ext = path.substring(dot + 1).toUpperCase();
        return NATURAL_EXTENSIONS.contains(ext);
    }

    /**
     * Detect whether source content is Natural (by sentence type hint, file path, or content heuristic).
     */
    public boolean isNaturalSource(String content, String sentenceType) {
        return isNaturalSource(content, sentenceType, null);
    }

    /**
     * Detect whether source content is Natural (by sentence type hint, file path, or content heuristic).
     *
     * @param content      source text (nullable)
     * @param sentenceType sentence type hint from dropdown (nullable)
     * @param path         file path for extension-based detection (nullable)
     * @return true if the source is Natural
     */
    public boolean isNaturalSource(String content, String sentenceType, String path) {
        if (sentenceType != null) {
            String upper = sentenceType.toUpperCase();
            if (upper.contains("NATURAL") || upper.contains("COPYCODE")
                    || upper.contains("SUBPROGRAM") || upper.contains("SUBROUTINE")
                    || upper.contains("HELPROUTINE")) {
                return true;
            }
        }
        if (isNaturalFile(path)) return true;
        if (content == null) return false;
        String[] lines = content.split("\\r?\\n", 40);
        int hits = 0;
        for (String line : lines) {
            String t = line.trim().toUpperCase();
            if (t.startsWith("DEFINE DATA") || t.startsWith("END-DEFINE")
                    || t.startsWith("CALLNAT ") || t.startsWith("LOCAL USING")
                    || t.startsWith("PARAMETER USING") || t.startsWith("DECIDE ON")
                    || t.startsWith("FETCH RETURN") || t.startsWith("INPUT USING MAP")
                    || t.startsWith("DEFINE SUBROUTINE") || t.startsWith("END-SUBROUTINE")) {
                hits++;
            }
        }
        // For copycodes that may just have variable definitions (1 #VAR (A10) format),
        // relax the threshold if the path suggests Natural
        if (hits >= 1 && path != null && isNaturalFile(path)) return true;
        return hits >= 2;
    }

    /**
     * Extract the object name from a path like "LIBNAME/OBJNAME.NSP" → "OBJNAME".
     */
    public String extractObjectName(String path) {
        if (path == null) return null;
        int slash = path.lastIndexOf('/');
        String filename = (slash >= 0) ? path.substring(slash + 1) : path;
        int dot = filename.lastIndexOf('.');
        return (dot > 0) ? filename.substring(0, dot).toUpperCase() : filename.toUpperCase();
    }

    /**
     * Extract the library name from a path like "LIBNAME/OBJNAME.NSP" → "LIBNAME".
     */
    public String extractLibrary(String path) {
        if (path == null) return null;
        int slash = path.indexOf('/');
        if (slash > 0 && slash < path.length() - 1) {
            return path.substring(0, slash);
        }
        return null;
    }

    /**
     * Get all known libraries (from in-memory cache + Lucene).
     */
    public List<String> getKnownLibraries() {
        Set<String> libs = new LinkedHashSet<String>(graphs.keySet());
        try {
            libs.addAll(LuceneDependencyIndex.getInstance().listCachedLibraries());
        } catch (Exception e) {
            LOG.log(Level.FINE, "Failed to list cached libraries", e);
        }
        return new ArrayList<String>(libs);
    }
}

