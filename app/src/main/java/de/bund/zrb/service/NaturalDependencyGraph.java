package de.bund.zrb.service;

import de.bund.zrb.service.NaturalDependencyService.Dependency;
import de.bund.zrb.service.NaturalDependencyService.DependencyKind;
import de.bund.zrb.service.NaturalDependencyService.DependencyResult;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Library-wide dependency graph that provides both Active XRefs (callees — what a
 * program calls) and Passive XRefs (callers — who calls this program).
 * <p>
 * This is analogous to NaturalONE's {@code NaturalBuilder} which parses all workspace
 * sources to build a bidirectional dependency graph. NaturalONE does this entirely
 * client-side — the NDV server only provides source code download.
 * <p>
 * Usage:
 * <pre>
 *   NaturalDependencyGraph graph = new NaturalDependencyGraph();
 *   graph.addSource("LIBNAME", "PROG1", sourceCode1);
 *   graph.addSource("LIBNAME", "PROG2", sourceCode2);
 *   ...
 *   graph.build();  // computes passive XRefs
 *
 *   // Active XRefs (what PROG1 calls)
 *   DependencyResult active = graph.getActiveXRefs("PROG1");
 *
 *   // Passive XRefs (who calls PROG1)
 *   List&lt;CallerInfo&gt; passive = graph.getPassiveXRefs("PROG1");
 *
 *   // Full call hierarchy (recursive)
 *   CallHierarchyNode hierarchy = graph.getCallHierarchy("PROG1", true, 5);
 * </pre>
 * <p>
 * Thread-safe for reads after {@link #build()} is called. The build phase itself
 * should be called from a single thread (e.g. a SwingWorker).
 */
public class NaturalDependencyGraph {

    private static final Logger LOG = Logger.getLogger(NaturalDependencyGraph.class.getName());

    private final NaturalDependencyService service = new NaturalDependencyService();

    /** Library name this graph belongs to (for display/navigation). */
    private volatile String library;

    /** Active XRefs per source object name (uppercase). */
    private final ConcurrentHashMap<String, DependencyResult> activeXRefs =
            new ConcurrentHashMap<String, DependencyResult>();

    /** Passive XRefs per target name (uppercase): who calls this target. */
    private final ConcurrentHashMap<String, List<CallerInfo>> passiveXRefs =
            new ConcurrentHashMap<String, List<CallerInfo>>();

    /** All known source names in this graph (uppercase). */
    private final Set<String> knownSources = Collections.synchronizedSet(new LinkedHashSet<String>());

    /** Whether the graph has been built (passive XRefs computed). */
    private volatile boolean built = false;

    // ═══════════════════════════════════════════════════════════
    //  Model classes
    // ═══════════════════════════════════════════════════════════

    /**
     * Information about a caller referencing a target object.
     */
    public static class CallerInfo {
        private final String callerName;
        private final DependencyKind referenceKind;
        private final int lineNumber;

        public CallerInfo(String callerName, DependencyKind referenceKind, int lineNumber) {
            this.callerName = callerName;
            this.referenceKind = referenceKind;
            this.lineNumber = lineNumber;
        }

        public String getCallerName() { return callerName; }
        public DependencyKind getReferenceKind() { return referenceKind; }
        public int getLineNumber() { return lineNumber; }

        public String getDisplayText() {
            return callerName + "  (" + referenceKind.getCode() + ", Zeile " + lineNumber + ")";
        }

        @Override
        public String toString() { return getDisplayText(); }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CallerInfo)) return false;
            CallerInfo that = (CallerInfo) o;
            return callerName.equals(that.callerName) && referenceKind == that.referenceKind;
        }

        @Override
        public int hashCode() {
            return 31 * callerName.hashCode() + referenceKind.hashCode();
        }
    }

    /**
     * Node in a call hierarchy tree (used for both callers and callees hierarchy).
     */
    public static class CallHierarchyNode {
        private final String objectName;
        private final DependencyKind referenceKind; // null for root
        private final int lineNumber; // 0 for root
        private final List<CallHierarchyNode> children = new ArrayList<CallHierarchyNode>();
        private final boolean isRecursive; // true if this node creates a cycle

        public CallHierarchyNode(String objectName, DependencyKind referenceKind,
                                 int lineNumber, boolean isRecursive) {
            this.objectName = objectName;
            this.referenceKind = referenceKind;
            this.lineNumber = lineNumber;
            this.isRecursive = isRecursive;
        }

        public String getObjectName() { return objectName; }
        public DependencyKind getReferenceKind() { return referenceKind; }
        public int getLineNumber() { return lineNumber; }
        public List<CallHierarchyNode> getChildren() { return children; }
        public boolean isRecursive() { return isRecursive; }
        public boolean isLeaf() { return children.isEmpty(); }

        public void addChild(CallHierarchyNode child) {
            children.add(child);
        }

        public String getDisplayText() {
            StringBuilder sb = new StringBuilder(objectName);
            if (referenceKind != null) {
                sb.append("  [").append(referenceKind.getCode()).append("]");
            }
            if (lineNumber > 0) {
                sb.append("  Zeile ").append(lineNumber);
            }
            if (isRecursive) {
                sb.append("  🔄 (rekursiv)");
            }
            return sb.toString();
        }

        @Override
        public String toString() { return getDisplayText(); }

        /**
         * Total number of nodes in this subtree (including this node).
         */
        public int totalNodes() {
            int count = 1;
            for (CallHierarchyNode child : children) {
                count += child.totalNodes();
            }
            return count;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Building the graph
    // ═══════════════════════════════════════════════════════════

    /**
     * Set the library name for this graph.
     */
    public void setLibrary(String library) {
        this.library = library;
    }

    public String getLibrary() {
        return library;
    }

    /**
     * Add pre-analyzed dependencies from cache (e.g. restored from Lucene index).
     * Use this instead of {@link #addSource} when source code is not available.
     * Call {@link #build()} after all cached deps are added to compute passive XRefs.
     *
     * @param objectName object name (e.g. "MYPROG")
     * @param dependencies pre-analyzed dependency list
     */
    public void addCachedDependencies(String objectName, List<Dependency> dependencies) {
        String key = objectName.toUpperCase();
        knownSources.add(key);
        activeXRefs.put(key, new DependencyResult(objectName, dependencies));
    }

    /**
     * Add a source object to the graph. Parses immediately for active XRefs.
     * Call {@link #build()} after adding all sources to compute passive XRefs.
     *
     * @param library    library name (for context)
     * @param objectName object name (e.g. "MYPROG")
     * @param sourceCode source code text
     */
    public void addSource(String library, String objectName, String sourceCode) {
        if (this.library == null) {
            this.library = library;
        }
        String key = objectName.toUpperCase();
        knownSources.add(key);

        try {
            DependencyResult result = service.analyze(sourceCode, objectName);
            activeXRefs.put(key, result);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to analyze source: " + objectName, e);
        }
    }

    /**
     * Build passive XRefs (callers) from all analyzed sources.
     * Must be called after all sources have been added via {@link #addSource}.
     */
    public void build() {
        passiveXRefs.clear();

        for (Map.Entry<String, DependencyResult> entry : activeXRefs.entrySet()) {
            String callerName = entry.getKey();
            DependencyResult result = entry.getValue();

            for (Dependency dep : result.getAllDependencies()) {
                String targetKey = dep.getTargetName().toUpperCase();

                // Only track external dependencies (not DB access / VIEW for callers)
                DependencyKind kind = dep.getKind();
                if (kind == DependencyKind.CALLNAT || kind == DependencyKind.FETCH
                        || kind == DependencyKind.CALL || kind == DependencyKind.PERFORM
                        || kind == DependencyKind.INCLUDE || kind == DependencyKind.USING) {

                    CallerInfo callerInfo = new CallerInfo(callerName, kind, dep.getLineNumber());

                    List<CallerInfo> callers = passiveXRefs.get(targetKey);
                    if (callers == null) {
                        callers = Collections.synchronizedList(new ArrayList<CallerInfo>());
                        List<CallerInfo> existing = passiveXRefs.putIfAbsent(targetKey, callers);
                        if (existing != null) {
                            callers = existing;
                        }
                    }
                    if (!callers.contains(callerInfo)) {
                        callers.add(callerInfo);
                    }
                }
            }
        }

        built = true;
        LOG.info("[DependencyGraph] Built graph for library '" + library
                + "': " + knownSources.size() + " sources, "
                + activeXRefs.size() + " analyzed, "
                + passiveXRefs.size() + " targets with callers");
    }

    // ═══════════════════════════════════════════════════════════
    //  Queries
    // ═══════════════════════════════════════════════════════════

    /**
     * Get active XRefs (callees) for a source object.
     *
     * @param objectName name of the source object
     * @return dependency result (never null; may be empty)
     */
    public DependencyResult getActiveXRefs(String objectName) {
        DependencyResult result = activeXRefs.get(objectName.toUpperCase());
        if (result == null) {
            return new DependencyResult(objectName, Collections.<Dependency>emptyList());
        }
        return result;
    }

    /**
     * Get passive XRefs (callers) for a target object.
     *
     * @param objectName name of the target object
     * @return list of callers (never null; may be empty)
     */
    public List<CallerInfo> getPassiveXRefs(String objectName) {
        List<CallerInfo> callers = passiveXRefs.get(objectName.toUpperCase());
        if (callers == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<CallerInfo>(callers));
    }

    /**
     * Get passive XRefs grouped by reference kind.
     */
    public Map<DependencyKind, List<CallerInfo>> getPassiveXRefsGrouped(String objectName) {
        List<CallerInfo> callers = getPassiveXRefs(objectName);
        Map<DependencyKind, List<CallerInfo>> grouped = new LinkedHashMap<DependencyKind, List<CallerInfo>>();
        for (CallerInfo caller : callers) {
            List<CallerInfo> list = grouped.get(caller.getReferenceKind());
            if (list == null) {
                list = new ArrayList<CallerInfo>();
                grouped.put(caller.getReferenceKind(), list);
            }
            list.add(caller);
        }
        return grouped;
    }

    /**
     * Build a call hierarchy tree starting from the given object.
     *
     * @param objectName starting object
     * @param callees    true = show callees (what this calls), false = show callers (who calls this)
     * @param maxDepth   maximum recursion depth (to prevent infinite loops)
     * @return root node of the hierarchy tree
     */
    public CallHierarchyNode getCallHierarchy(String objectName, boolean callees, int maxDepth) {
        CallHierarchyNode root = new CallHierarchyNode(objectName.toUpperCase(), null, 0, false);
        Set<String> visited = new HashSet<String>();
        visited.add(objectName.toUpperCase());
        buildHierarchy(root, callees, maxDepth, 0, visited);
        return root;
    }

    private void buildHierarchy(CallHierarchyNode node, boolean callees,
                                int maxDepth, int currentDepth, Set<String> visited) {
        if (currentDepth >= maxDepth) return;

        if (callees) {
            // Active XRefs: what does this object call?
            DependencyResult result = activeXRefs.get(node.getObjectName());
            if (result == null) return;

            for (Dependency dep : result.getAllDependencies()) {
                DependencyKind kind = dep.getKind();
                // Only follow program-level calls for hierarchy
                if (kind == DependencyKind.CALLNAT || kind == DependencyKind.FETCH
                        || kind == DependencyKind.CALL || kind == DependencyKind.PERFORM
                        || kind == DependencyKind.INCLUDE) {

                    String target = dep.getTargetName().toUpperCase();
                    boolean recursive = visited.contains(target);
                    CallHierarchyNode child = new CallHierarchyNode(
                            target, kind, dep.getLineNumber(), recursive);
                    node.addChild(child);

                    if (!recursive) {
                        visited.add(target);
                        buildHierarchy(child, true, maxDepth, currentDepth + 1, visited);
                        visited.remove(target);
                    }
                }
            }
        } else {
            // Passive XRefs: who calls this object?
            List<CallerInfo> callers = passiveXRefs.get(node.getObjectName());
            if (callers == null) return;

            for (CallerInfo caller : callers) {
                boolean recursive = visited.contains(caller.getCallerName());
                CallHierarchyNode child = new CallHierarchyNode(
                        caller.getCallerName(), caller.getReferenceKind(),
                        caller.getLineNumber(), recursive);
                node.addChild(child);

                if (!recursive) {
                    visited.add(caller.getCallerName());
                    buildHierarchy(child, false, maxDepth, currentDepth + 1, visited);
                    visited.remove(caller.getCallerName());
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Utilities
    // ═══════════════════════════════════════════════════════════

    /**
     * Check whether a target object is known (has source in this graph).
     */
    public boolean isKnownSource(String objectName) {
        return knownSources.contains(objectName.toUpperCase());
    }

    /**
     * Get all known source object names.
     */
    public Set<String> getKnownSources() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(knownSources));
    }

    /**
     * Get all objects that have callers (useful for finding entry points — objects without callers).
     */
    public List<String> getEntryPoints() {
        List<String> entryPoints = new ArrayList<String>();
        for (String source : knownSources) {
            if (!passiveXRefs.containsKey(source) || passiveXRefs.get(source).isEmpty()) {
                entryPoints.add(source);
            }
        }
        return entryPoints;
    }

    /**
     * Get all unresolved references (targets that aren't in the graph).
     */
    public Set<String> getUnresolvedTargets() {
        Set<String> unresolved = new LinkedHashSet<String>();
        for (DependencyResult result : activeXRefs.values()) {
            for (Dependency dep : result.getAllDependencies()) {
                DependencyKind kind = dep.getKind();
                // Only check program-level references
                if (kind == DependencyKind.CALLNAT || kind == DependencyKind.FETCH
                        || kind == DependencyKind.CALL || kind == DependencyKind.INCLUDE
                        || kind == DependencyKind.USING) {
                    String target = dep.getTargetName().toUpperCase();
                    if (!knownSources.contains(target)) {
                        unresolved.add(target);
                    }
                }
            }
        }
        return unresolved;
    }

    /**
     * @return true if {@link #build()} has been called
     */
    public boolean isBuilt() {
        return built;
    }

    /**
     * Clear the entire graph (for rebuilding).
     */
    public void clear() {
        activeXRefs.clear();
        passiveXRefs.clear();
        knownSources.clear();
        built = false;
    }

    /**
     * Get a summary string for logging/debugging.
     */
    public String getSummary() {
        int totalActive = 0;
        for (DependencyResult r : activeXRefs.values()) {
            totalActive += r.getTotalCount();
        }
        int totalPassive = 0;
        for (List<CallerInfo> callers : passiveXRefs.values()) {
            totalPassive += callers.size();
        }
        return String.format("DependencyGraph[library=%s, sources=%d, activeRefs=%d, passiveTargets=%d, callerRefs=%d, unresolved=%d]",
                library, knownSources.size(), totalActive, passiveXRefs.size(), totalPassive,
                built ? getUnresolvedTargets().size() : -1);
    }
}

