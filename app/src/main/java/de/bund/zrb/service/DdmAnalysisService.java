package de.bund.zrb.service;

import de.bund.zrb.jcl.model.JclElement;
import de.bund.zrb.jcl.model.JclElementType;
import de.bund.zrb.jcl.model.JclOutlineModel;
import de.bund.zrb.jcl.parser.DdmParser;
import de.bund.zrb.jcl.parser.DdmParser.DdmDefinition;
import de.bund.zrb.jcl.parser.DdmParser.DdmField;
import de.bund.zrb.service.NaturalDependencyService.Dependency;
import de.bund.zrb.service.NaturalDependencyService.DependencyKind;
import de.bund.zrb.service.NaturalDependencyService.DependencyResult;
import de.bund.zrb.service.NaturalDependencyGraph.CallerInfo;

import java.util.*;
import java.util.logging.Logger;

/**
 * Central service for DDM (Data Definition Module) analysis.
 * <p>
 * Provides three views analogous to NaturalONE's panel structure:
 * <ul>
 *   <li><b>Outline</b>: Field structure of the DDM (header + fields grouped by level/PE/MU)</li>
 *   <li><b>Dependencies</b>: Programs that reference this DDM (reverse lookup via VIEW/DB_ACCESS)</li>
 *   <li><b>Hierarchy</b>: DDM → programs using it → other DDMs those programs reference (chain)</li>
 * </ul>
 * <p>
 * Uses the existing {@link DdmParser} for parsing and {@link NaturalDependencyGraph} for
 * reverse dependency lookups. The graph must have been previously built (e.g. by
 * NdvConnectionTab when the user browses a library).
 * <p>
 * Thread-safe singleton — parse operations are stateless; graph lookups are delegated
 * to {@link NaturalAnalysisService}.
 */
public class DdmAnalysisService {

    private static final Logger LOG = Logger.getLogger(DdmAnalysisService.class.getName());

    private static volatile DdmAnalysisService instance;

    private final DdmParser parser = new DdmParser();

    public static synchronized DdmAnalysisService getInstance() {
        if (instance == null) {
            instance = new DdmAnalysisService();
        }
        return instance;
    }

    private DdmAnalysisService() {
    }

    // ═══════════════════════════════════════════════════════════
    //  Detection
    // ═══════════════════════════════════════════════════════════

    /**
     * Detect whether the given content is a DDM definition.
     *
     * @param content      the source text
     * @param sentenceType sentence type hint (nullable), e.g. from dropdown
     * @return true if the content looks like a DDM
     */
    public boolean isDdmSource(String content, String sentenceType) {
        if (sentenceType != null) {
            String upper = sentenceType.toUpperCase();
            if (upper.contains("DDM") || upper.contains("NSD")) return true;
        }
        return DdmParser.isDdmContent(content);
    }

    /**
     * Detect DDM by file extension.
     */
    public boolean isDdmFile(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase();
        return lower.endsWith(".nsd");
    }

    // ═══════════════════════════════════════════════════════════
    //  Parsing
    // ═══════════════════════════════════════════════════════════

    /**
     * Parse DDM source text into a structured definition.
     *
     * @param source       the DDM listing text
     * @param fallbackName DDM name to use if not detected from source
     * @return parsed DDM definition, or null if invalid
     */
    public DdmDefinition parse(String source, String fallbackName) {
        return parser.parse(source, fallbackName);
    }

    // ═══════════════════════════════════════════════════════════
    //  Outline (for RightDrawer JclOutlinePanel)
    // ═══════════════════════════════════════════════════════════

    /**
     * Build a {@link JclOutlineModel} from DDM source for the outline panel.
     * <p>
     * The outline shows:
     * <ul>
     *   <li>DDM header (name, DB, file, default sequence)</li>
     *   <li>Fields grouped by PE/MU groups with level hierarchy</li>
     *   <li>Descriptors and superdescriptors marked with key icons</li>
     * </ul>
     *
     * @param source     DDM source text
     * @param sourceName display name
     * @return outline model (never null; may be empty)
     */
    public JclOutlineModel buildOutline(String source, String sourceName) {
        JclOutlineModel model = new JclOutlineModel();
        model.setLanguage(JclOutlineModel.Language.DDM);
        model.setSourceName(sourceName);

        if (source == null || source.isEmpty()) return model;

        String fallbackName = extractDdmName(sourceName);
        DdmDefinition ddm = parser.parse(source, fallbackName);
        if (ddm == null) return model;

        String[] lines = source.split("\\r?\\n");
        model.setTotalLines(lines.length);

        // ── Header element ──
        String headerText = ddm.getName() + "  (DB:" + ddm.getDbId()
                + " FILE:" + ddm.getFileNumber() + ")";
        if (ddm.getDefaultSequence() != null && !ddm.getDefaultSequence().isEmpty()) {
            headerText += "  SEQ:" + ddm.getDefaultSequence();
        }
        JclElement headerElem = new JclElement(JclElementType.DDM_HEADER, headerText, 1,
                "DB: " + ddm.getDbId() + " FILE: " + ddm.getFileNumber() + " - " + ddm.getName());
        headerElem.addParameter("DDM", ddm.getName());
        headerElem.addParameter("DB", String.valueOf(ddm.getDbId()));
        headerElem.addParameter("FILE", String.valueOf(ddm.getFileNumber()));
        if (ddm.getDefaultSequence() != null && !ddm.getDefaultSequence().isEmpty()) {
            headerElem.addParameter("DEFAULT_SEQ", ddm.getDefaultSequence());
        }
        model.addElement(headerElem);

        // ── Field elements ──
        // Track the current group for nesting
        JclElement currentGroup = null;
        int searchFrom = findFieldStartLine(lines); // 0-based index to start searching

        for (int i = 0; i < ddm.getFields().size(); i++) {
            DdmField field = ddm.getFields().get(i);
            int lineIndex = findFieldLine(lines, field, searchFrom);
            int lineNum = lineIndex + 1; // convert to 1-based
            searchFrom = lineIndex + 1;  // next field must be after this one

            JclElementType type;
            if (field.isSuperdescriptor()) {
                type = JclElementType.DDM_SUPERDESCRIPTOR;
            } else if (field.isDescriptor()) {
                type = JclElementType.DDM_DESCRIPTOR;
            } else if (field.isPeriodicGroup() || field.isMultipleValue()) {
                type = JclElementType.DDM_GROUP;
            } else if (field.isGroup()) {
                type = JclElementType.DDM_GROUP;
            } else {
                type = JclElementType.DDM_FIELD;
            }

            // Build display name: "AB PERSONNEL-ID  A8"
            String displayName = field.getShortName() + " " + field.getLongName();
            if (field.getFormat() != null && !field.getFormat().isEmpty()) {
                displayName += "  " + field.getFormatSpec();
            }

            JclElement elem = new JclElement(type, displayName, lineNum,
                    buildFieldRawText(field, ddm.getDefaultSequence()));

            elem.addParameter("SHORT_NAME", field.getShortName());
            elem.addParameter("LONG_NAME", field.getLongName());
            elem.addParameter("LEVEL", String.valueOf(field.getLevel()));
            if (field.getFormat() != null && !field.getFormat().isEmpty()) {
                elem.addParameter("FORMAT", field.getFormatSpec());
            }
            if (field.isDescriptor() || field.isSuperdescriptor()) {
                elem.addParameter("KEY", field.getKeyLabel(ddm.getDefaultSequence()));
            }
            String keyLabel = field.getKeyLabel(ddm.getDefaultSequence());
            if (!keyLabel.isEmpty()) {
                elem.addParameter("KEY_TYPE", keyLabel);
            }

            // Nesting: Level 1 fields are top-level, Level 2+ are children of groups
            if (field.getLevel() == 1 || field.getLevel() == 0) {
                model.addElement(elem);
                if (field.isPeriodicGroup() || field.isMultipleValue() || field.isGroup()) {
                    currentGroup = elem;
                } else {
                    currentGroup = null;
                }
            } else if (currentGroup != null) {
                currentGroup.addChild(elem);
            } else {
                model.addElement(elem);
            }
        }

        return model;
    }

    /**
     * Find the 0-based line index where field definitions start (after the separator line).
     */
    private int findFieldStartLine(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("-") && line.contains("-") && line.length() > 5) {
                return i + 1; // fields start right after the separator
            }
        }
        return 3; // default: after header
    }

    /**
     * Find the 0-based line index where a specific field appears in the source.
     * Searches forward from startIndex for a line containing the field's long name
     * (and optionally short name). Falls back to startIndex if not found.
     */
    private int findFieldLine(String[] lines, DdmField field, int startIndex) {
        String longName = field.getLongName();
        String shortName = field.getShortName();

        // Primary: match long name (unique within a DDM, e.g. "PERSONNEL-ID")
        if (longName != null && !longName.isEmpty()) {
            for (int i = startIndex; i < lines.length; i++) {
                if (lines[i].contains(longName)) {
                    return i;
                }
            }
        }

        // Fallback: match short name (2-char code like "AA", "AB")
        if (shortName != null && !shortName.isEmpty()) {
            for (int i = startIndex; i < lines.length; i++) {
                if (lines[i].contains(shortName)) {
                    return i;
                }
            }
        }

        return startIndex; // ultimate fallback
    }

    private String buildFieldRawText(DdmField field, String defaultSeq) {
        StringBuilder sb = new StringBuilder();
        sb.append(field.getShortName()).append(" ").append(field.getLongName());
        if (field.getFormat() != null && !field.getFormat().isEmpty()) {
            sb.append("  ").append(field.getFormatSpec());
        }
        String key = field.getKeyLabel(defaultSeq);
        if (!key.isEmpty()) {
            sb.append("  [").append(key).append("]");
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════
    //  Dependencies (for LeftDrawer top: programs using this DDM)
    // ═══════════════════════════════════════════════════════════

    /**
     * Result of DDM dependency analysis — who uses this DDM.
     */
    public static class DdmDependencyResult {
        private final String ddmName;
        private final Map<String, List<DdmUser>> usersByKind;
        private final int totalCount;

        public DdmDependencyResult(String ddmName, Map<String, List<DdmUser>> usersByKind, int totalCount) {
            this.ddmName = ddmName;
            this.usersByKind = usersByKind;
            this.totalCount = totalCount;
        }

        public String getDdmName() { return ddmName; }
        public Map<String, List<DdmUser>> getUsersByKind() { return usersByKind; }
        public int getTotalCount() { return totalCount; }
        public boolean isEmpty() { return totalCount == 0; }
    }

    /**
     * A program that uses a DDM.
     */
    public static class DdmUser {
        private final String programName;
        private final String referenceKind; // "VIEW", "READ", "FIND", etc.
        private final int lineNumber;

        public DdmUser(String programName, String referenceKind, int lineNumber) {
            this.programName = programName;
            this.referenceKind = referenceKind;
            this.lineNumber = lineNumber;
        }

        public String getProgramName() { return programName; }
        public String getReferenceKind() { return referenceKind; }
        public int getLineNumber() { return lineNumber; }

        public String getDisplayText() {
            return programName + "  (" + referenceKind + ", Zeile " + lineNumber + ")";
        }

        @Override
        public String toString() { return getDisplayText(); }
    }

    /**
     * Find all Natural programs that reference the given DDM.
     * <p>
     * Scans the {@link NaturalDependencyGraph} for all programs with
     * {@link DependencyKind#VIEW} or {@link DependencyKind#DB_ACCESS} references
     * matching the DDM name.
     *
     * @param ddmName  the DDM name (uppercase)
     * @param library  the library to search in (for graph lookup)
     * @return dependency result (never null; may be empty)
     */
    public DdmDependencyResult findDdmUsers(String ddmName, String library) {
        Map<String, List<DdmUser>> sections = new LinkedHashMap<String, List<DdmUser>>();
        int totalCount = 0;

        if (ddmName == null || library == null) {
            return new DdmDependencyResult(ddmName, sections, 0);
        }

        String ddmKey = ddmName.toUpperCase();
        NaturalAnalysisService analysisService = NaturalAnalysisService.getInstance();
        NaturalDependencyGraph graph = analysisService.getGraph(library);

        if (graph == null || !graph.isBuilt()) {
            return new DdmDependencyResult(ddmName, sections, 0);
        }

        // Scan all programs in the graph for VIEW/DB_ACCESS references to this DDM
        List<DdmUser> viewUsers = new ArrayList<DdmUser>();
        List<DdmUser> dbAccessUsers = new ArrayList<DdmUser>();

        for (String sourceName : graph.getKnownSources()) {
            DependencyResult result = graph.getActiveXRefs(sourceName);
            if (result == null) continue;

            for (Dependency dep : result.getAllDependencies()) {
                if (!dep.getTargetName().equalsIgnoreCase(ddmKey)) continue;

                if (dep.getKind() == DependencyKind.VIEW) {
                    viewUsers.add(new DdmUser(sourceName, "VIEW", dep.getLineNumber()));
                } else if (dep.getKind() == DependencyKind.DB_ACCESS) {
                    String opKind = dep.getDetail() != null ? dep.getDetail() : "DB";
                    dbAccessUsers.add(new DdmUser(sourceName, opKind, dep.getLineNumber()));
                }
            }
        }

        if (!viewUsers.isEmpty()) {
            sections.put("📊 VIEW-Referenzen", viewUsers);
            totalCount += viewUsers.size();
        }
        if (!dbAccessUsers.isEmpty()) {
            sections.put("🗄 DB-Zugriffe (READ/FIND/…)", dbAccessUsers);
            totalCount += dbAccessUsers.size();
        }

        return new DdmDependencyResult(ddmName, sections, totalCount);
    }

    /**
     * Find DDM users across all known libraries.
     */
    public DdmDependencyResult findDdmUsersAllLibraries(String ddmName) {
        Map<String, List<DdmUser>> allSections = new LinkedHashMap<String, List<DdmUser>>();
        int totalCount = 0;

        NaturalAnalysisService analysisService = NaturalAnalysisService.getInstance();
        for (String lib : analysisService.getKnownLibraries()) {
            DdmDependencyResult libResult = findDdmUsers(ddmName, lib);
            if (!libResult.isEmpty()) {
                for (Map.Entry<String, List<DdmUser>> entry : libResult.getUsersByKind().entrySet()) {
                    String sectionKey = entry.getKey() + " [" + lib + "]";
                    allSections.put(sectionKey, entry.getValue());
                    totalCount += entry.getValue().size();
                }
            }
        }

        return new DdmDependencyResult(ddmName, allSections, totalCount);
    }

    // ═══════════════════════════════════════════════════════════
    //  Hierarchy (for LeftDrawer bottom: callers + related DDMs)
    // ═══════════════════════════════════════════════════════════

    /**
     * Node in the DDM usage hierarchy.
     */
    public static class DdmHierarchyNode {
        private final String name;
        private final String nodeType; // "DDM", "PROGRAM"
        private final String detail;   // e.g. "VIEW", "READ", "CALLNAT", or null for root
        private final List<DdmHierarchyNode> children = new ArrayList<DdmHierarchyNode>();
        private final boolean recursive;

        public DdmHierarchyNode(String name, String nodeType, String detail, boolean recursive) {
            this.name = name;
            this.nodeType = nodeType;
            this.detail = detail;
            this.recursive = recursive;
        }

        public String getName() { return name; }
        public String getNodeType() { return nodeType; }
        public String getDetail() { return detail; }
        public List<DdmHierarchyNode> getChildren() { return children; }
        public boolean isRecursive() { return recursive; }
        public void addChild(DdmHierarchyNode child) { children.add(child); }

        public String getDisplayText() {
            StringBuilder sb = new StringBuilder();
            sb.append("DDM".equals(nodeType) ? "🗃 " : "📝 ");
            sb.append(name);
            if (detail != null && !detail.isEmpty()) {
                sb.append("  [").append(detail).append("]");
            }
            if (recursive) {
                sb.append("  🔄");
            }
            return sb.toString();
        }

        @Override
        public String toString() { return getDisplayText(); }
    }

    /**
     * Result of DDM hierarchy analysis — contains both callers and related DDMs.
     */
    public static class DdmHierarchyResult {
        private final DdmHierarchyNode callersRoot;   // ⬅ Programs using this DDM → who calls them
        private final DdmHierarchyNode relatedDdmsRoot; // ➡ Other DDMs referenced by the same programs

        public DdmHierarchyResult(DdmHierarchyNode callersRoot, DdmHierarchyNode relatedDdmsRoot) {
            this.callersRoot = callersRoot;
            this.relatedDdmsRoot = relatedDdmsRoot;
        }

        public DdmHierarchyNode getCallersRoot() { return callersRoot; }
        public DdmHierarchyNode getRelatedDdmsRoot() { return relatedDdmsRoot; }
    }

    /**
     * Build the full DDM hierarchy with two branches:
     * <ul>
     *   <li><b>Callers</b> (⬅ Aufgerufen von): Programs that reference this DDM via VIEW/DB_ACCESS,
     *       and for each program, who calls that program (via passive XRefs / call hierarchy).</li>
     *   <li><b>Related DDMs</b> (➡ Verwandte DDMs): Other DDMs that are referenced by the same
     *       programs that use this DDM.</li>
     * </ul>
     *
     * @param ddmName  the DDM name
     * @param library  the library to search in
     * @param maxDepth max recursion depth (recommended: 3)
     * @return hierarchy result with both callers and related DDMs (never null)
     */
    public DdmHierarchyResult buildDdmHierarchy(String ddmName, String library, int maxDepth) {
        DdmHierarchyNode callersRoot = new DdmHierarchyNode(ddmName, "DDM", null, false);
        DdmHierarchyNode relatedRoot = new DdmHierarchyNode(ddmName, "DDM", null, false);

        if (library == null || ddmName == null) {
            return new DdmHierarchyResult(callersRoot, relatedRoot);
        }

        NaturalAnalysisService analysisService = NaturalAnalysisService.getInstance();
        NaturalDependencyGraph graph = analysisService.getGraph(library);
        if (graph == null || !graph.isBuilt()) {
            return new DdmHierarchyResult(callersRoot, relatedRoot);
        }

        String ddmKey = ddmName.toUpperCase();

        // ── Collect programs that reference this DDM ──
        // Map: programName → referenceKind (VIEW, READ, FIND, etc.)
        Map<String, String> ddmUsers = new LinkedHashMap<String, String>();
        for (String sourceName : graph.getKnownSources()) {
            DependencyResult result = graph.getActiveXRefs(sourceName);
            if (result == null) continue;

            for (Dependency dep : result.getAllDependencies()) {
                if (dep.getTargetName().equalsIgnoreCase(ddmKey)
                        && (dep.getKind() == DependencyKind.VIEW
                        || dep.getKind() == DependencyKind.DB_ACCESS)) {
                    if (!ddmUsers.containsKey(sourceName)) {
                        String refKind = dep.getKind() == DependencyKind.VIEW ? "VIEW" :
                                (dep.getDetail() != null ? dep.getDetail() : "DB");
                        ddmUsers.put(sourceName, refKind);
                    }
                }
            }
        }

        // ── Branch 1: Callers — who uses this DDM → who calls those programs ──
        for (Map.Entry<String, String> entry : ddmUsers.entrySet()) {
            String progName = entry.getKey();
            String refKind = entry.getValue();

            DdmHierarchyNode progNode = new DdmHierarchyNode(progName, "PROGRAM", refKind, false);
            callersRoot.addChild(progNode);

            // Add callers of this program (passive XRefs) recursively
            if (maxDepth > 1) {
                Set<String> visited = new HashSet<String>();
                visited.add(progName.toUpperCase());
                addProgramCallers(progNode, progName, graph, maxDepth - 1, 0, visited, library);
            }
        }

        // ── Branch 2: Related DDMs — other DDMs referenced by the same programs ──
        Set<String> relatedDdmsFound = new LinkedHashSet<String>();
        for (String progName : ddmUsers.keySet()) {
            DependencyResult result = graph.getActiveXRefs(progName);
            if (result == null) continue;

            for (Dependency dep : result.getAllDependencies()) {
                if (dep.getKind() == DependencyKind.VIEW || dep.getKind() == DependencyKind.DB_ACCESS) {
                    String targetDdm = dep.getTargetName().toUpperCase();
                    if (!targetDdm.equals(ddmKey) && !relatedDdmsFound.contains(targetDdm)) {
                        relatedDdmsFound.add(targetDdm);

                        String refKind = dep.getKind() == DependencyKind.VIEW ? "VIEW" :
                                (dep.getDetail() != null ? dep.getDetail() : "DB");
                        DdmHierarchyNode ddmChild = new DdmHierarchyNode(
                                targetDdm, "DDM", refKind + " via " + progName, false);

                        // Show which programs connect to this related DDM
                        for (Map.Entry<String, String> user : ddmUsers.entrySet()) {
                            DependencyResult userResult = graph.getActiveXRefs(user.getKey());
                            if (userResult == null) continue;
                            for (Dependency userDep : userResult.getAllDependencies()) {
                                if (userDep.getTargetName().equalsIgnoreCase(targetDdm)
                                        && (userDep.getKind() == DependencyKind.VIEW
                                        || userDep.getKind() == DependencyKind.DB_ACCESS)) {
                                    String userRef = userDep.getKind() == DependencyKind.VIEW ? "VIEW" :
                                            (userDep.getDetail() != null ? userDep.getDetail() : "DB");
                                    ddmChild.addChild(new DdmHierarchyNode(
                                            user.getKey(), "PROGRAM", userRef, false));
                                    break; // one entry per program
                                }
                            }
                        }

                        relatedRoot.addChild(ddmChild);
                    }
                }
            }
        }

        return new DdmHierarchyResult(callersRoot, relatedRoot);
    }

    /**
     * Recursively add callers of a program (who calls this program) from passive XRefs.
     */
    private void addProgramCallers(DdmHierarchyNode progNode, String programName,
                                   NaturalDependencyGraph graph, int maxDepth, int depth,
                                   Set<String> visited, String library) {
        if (depth >= maxDepth) return;

        List<CallerInfo> callers = graph.getPassiveXRefs(programName);
        for (CallerInfo caller : callers) {
            boolean recursive = visited.contains(caller.getCallerName().toUpperCase());
            String kindLabel = caller.getReferenceKind() != null
                    ? caller.getReferenceKind().name() : "CALL";
            DdmHierarchyNode callerNode = new DdmHierarchyNode(
                    caller.getCallerName(), "PROGRAM", kindLabel, recursive);
            progNode.addChild(callerNode);

            if (!recursive && depth + 1 < maxDepth) {
                visited.add(caller.getCallerName().toUpperCase());
                addProgramCallers(callerNode, caller.getCallerName(), graph,
                        maxDepth, depth + 1, visited, library);
                visited.remove(caller.getCallerName().toUpperCase());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  AI context building
    // ═══════════════════════════════════════════════════════════

    /**
     * Build a human-readable summary of a DDM for AI context (RAG).
     */
    public String buildAiDdmSummary(String source, String ddmName, String library) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DDM ANALYSIS: ").append(ddmName != null ? ddmName.toUpperCase() : "UNKNOWN").append(" ===\n");

        DdmDefinition ddm = parser.parse(source, ddmName);
        if (ddm == null) {
            sb.append("Could not parse DDM source.\n");
            return sb.toString();
        }

        // Header
        sb.append("Database: ").append(ddm.getDbId())
          .append(", File: ").append(ddm.getFileNumber()).append("\n");
        if (ddm.getDefaultSequence() != null && !ddm.getDefaultSequence().isEmpty()) {
            sb.append("Default Sequence: ").append(ddm.getDefaultSequence()).append("\n");
        }
        sb.append("Fields: ").append(ddm.getFields().size()).append("\n\n");

        // Field listing
        sb.append("--- Fields ---\n");
        for (DdmField field : ddm.getFields()) {
            sb.append("  ").append(field.getShortName()).append("  ")
              .append(field.getLongName());
            if (field.getFormat() != null && !field.getFormat().isEmpty()) {
                sb.append("  ").append(field.getFormatSpec());
            }
            String key = field.getKeyLabel(ddm.getDefaultSequence());
            if (!key.isEmpty()) {
                sb.append("  [").append(key).append("]");
            }
            if (field.isPeriodicGroup()) sb.append("  (PE)");
            if (field.isMultipleValue()) sb.append("  (MU)");
            sb.append("\n");
        }

        // Programs using this DDM
        if (library != null) {
            DdmDependencyResult users = findDdmUsers(ddmName, library);
            if (!users.isEmpty()) {
                sb.append("\n--- Programs using this DDM ---\n");
                for (Map.Entry<String, List<DdmUser>> entry : users.getUsersByKind().entrySet()) {
                    sb.append(entry.getKey()).append(":\n");
                    for (DdmUser user : entry.getValue()) {
                        sb.append("  • ").append(user.getDisplayText()).append("\n");
                    }
                }
            }
        }

        sb.append("=== END DDM ANALYSIS ===\n");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════
    //  Path utilities
    // ═══════════════════════════════════════════════════════════

    /**
     * Extract DDM name from a path like "MYLIB/MYDDM.NSD" → "MYDDM".
     */
    public String extractDdmName(String path) {
        if (path == null) return null;
        int slash = path.lastIndexOf('/');
        String filename = (slash >= 0) ? path.substring(slash + 1) : path;
        int dot = filename.lastIndexOf('.');
        return (dot > 0) ? filename.substring(0, dot).toUpperCase() : filename.toUpperCase();
    }

    /**
     * Extract library from a path like "MYLIB/MYDDM.NSD" → "MYLIB".
     */
    public String extractLibrary(String path) {
        if (path == null) return null;
        int slash = path.indexOf('/');
        if (slash > 0 && slash < path.length() - 1) {
            return path.substring(0, slash);
        }
        return null;
    }
}

