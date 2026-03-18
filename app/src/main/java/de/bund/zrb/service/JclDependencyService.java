package de.bund.zrb.service;

import de.bund.zrb.jcl.model.JclElement;
import de.bund.zrb.jcl.model.JclElementType;
import de.bund.zrb.jcl.model.JclOutlineModel;
import de.bund.zrb.jcl.parser.AntlrJclParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for extracting dependencies/relationships from JCL source code.
 * <p>
 * Parses JCL using {@link AntlrJclParser} and extracts all external references:
 * <ul>
 *   <li>Programs (EXEC PGM=xxx) — e.g. IDCAMS, IEFBR14, IKJEFT01, COBOL/Natural programs</li>
 *   <li>Procedures (EXEC PROC=xxx or EXEC xxx) — cataloged procedures</li>
 *   <li>INCLUDE members — included JCL members</li>
 *   <li>JCLLIB ORDER — procedure library search order</li>
 *   <li>Datasets (DD DSN=xxx) — referenced datasets</li>
 * </ul>
 * <p>
 * The service is stateless and thread-safe.
 */
public class JclDependencyService {

    private static final JclDependencyService INSTANCE = new JclDependencyService();

    private final AntlrJclParser parser = new AntlrJclParser();

    public static JclDependencyService getInstance() {
        return INSTANCE;
    }

    // ═══════════════════════════════════════════════════════════
    //  Dependency model
    // ═══════════════════════════════════════════════════════════

    /**
     * Classification of a JCL dependency reference.
     */
    public enum JclDependencyKind {
        /** EXEC PGM=xxx — system utility or application program */
        PROGRAM("▶ Programme (PGM)", "PGM"),
        /** EXEC PROC=xxx or EXEC xxx — cataloged procedure */
        PROCEDURE("📦 Prozeduren (PROC)", "PROC"),
        /** INCLUDE MEMBER=xxx — included JCL member */
        INCLUDE("📎 INCLUDE Member", "INCLUDE"),
        /** JCLLIB ORDER=(dsn,...) — procedure library */
        JCLLIB("📚 JCLLIB", "JCLLIB"),
        /** DD DSN=xxx — referenced datasets */
        DATASET("📄 Datasets (DSN)", "DSN");

        private final String displayLabel;
        private final String code;

        JclDependencyKind(String displayLabel, String code) {
            this.displayLabel = displayLabel;
            this.code = code;
        }

        public String getDisplayLabel() { return displayLabel; }
        public String getCode() { return code; }
    }

    /**
     * A single dependency extracted from JCL source code.
     */
    public static class JclDependency {
        private final JclDependencyKind kind;
        private final String targetName;
        private final int lineNumber;
        private final String sourceLine;
        private final String detail;  // e.g. step name, DD name

        public JclDependency(JclDependencyKind kind, String targetName, int lineNumber,
                             String sourceLine, String detail) {
            this.kind = kind;
            this.targetName = targetName;
            this.lineNumber = lineNumber;
            this.sourceLine = sourceLine;
            this.detail = detail;
        }

        public JclDependencyKind getKind() { return kind; }
        public String getTargetName() { return targetName; }
        public int getLineNumber() { return lineNumber; }
        public String getSourceLine() { return sourceLine; }
        public String getDetail() { return detail; }

        /**
         * Display label for tree nodes, e.g. "IDCAMS  (Step: STEP01)  [Zeile 5]"
         */
        public String getDisplayText() {
            StringBuilder sb = new StringBuilder();
            sb.append(targetName);
            if (detail != null && !detail.isEmpty()) {
                sb.append("  (").append(detail).append(")");
            }
            sb.append("  [Zeile ").append(lineNumber).append("]");
            return sb.toString();
        }

        @Override
        public String toString() {
            return getDisplayText();
        }
    }

    /**
     * Result of JCL dependency analysis: all dependencies grouped by kind.
     */
    public static class JclDependencyResult {
        private final String sourceName;
        private final List<JclDependency> allDependencies;
        private final Map<JclDependencyKind, List<JclDependency>> grouped;

        public JclDependencyResult(String sourceName, List<JclDependency> allDependencies) {
            this.sourceName = sourceName;
            this.allDependencies = Collections.unmodifiableList(allDependencies);
            Map<JclDependencyKind, List<JclDependency>> map =
                    new LinkedHashMap<JclDependencyKind, List<JclDependency>>();
            for (JclDependency dep : allDependencies) {
                List<JclDependency> list = map.get(dep.getKind());
                if (list == null) {
                    list = new ArrayList<JclDependency>();
                    map.put(dep.getKind(), list);
                }
                list.add(dep);
            }
            this.grouped = Collections.unmodifiableMap(map);
        }

        public String getSourceName() { return sourceName; }
        public List<JclDependency> getAllDependencies() { return allDependencies; }
        public Map<JclDependencyKind, List<JclDependency>> getGrouped() { return grouped; }
        public boolean isEmpty() { return allDependencies.isEmpty(); }
        public int getTotalCount() { return allDependencies.size(); }

        /**
         * Get unique program names (PGM=).
         */
        public List<String> getProgramNames() {
            Set<String> names = new LinkedHashSet<String>();
            for (JclDependency dep : allDependencies) {
                if (dep.getKind() == JclDependencyKind.PROGRAM) {
                    names.add(dep.getTargetName().toUpperCase());
                }
            }
            return new ArrayList<String>(names);
        }

        /**
         * Get unique procedure names (PROC=).
         */
        public List<String> getProcedureNames() {
            Set<String> names = new LinkedHashSet<String>();
            for (JclDependency dep : allDependencies) {
                if (dep.getKind() == JclDependencyKind.PROCEDURE) {
                    names.add(dep.getTargetName().toUpperCase());
                }
            }
            return new ArrayList<String>(names);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Analysis
    // ═══════════════════════════════════════════════════════════

    /**
     * Analyze JCL source code and extract all dependencies.
     *
     * @param jclContent  the JCL source text
     * @param sourceName  name of the source (for display)
     * @return dependency analysis result, never null
     */
    public JclDependencyResult analyze(String jclContent, String sourceName) {
        if (jclContent == null || jclContent.isEmpty()) {
            return new JclDependencyResult(sourceName, Collections.<JclDependency>emptyList());
        }

        JclOutlineModel model = parser.parse(jclContent, sourceName);
        List<JclDependency> deps = new ArrayList<JclDependency>();

        // Track unique targets to avoid duplicates in the same category
        // (we still record line numbers, but collapse identical targets)
        for (JclElement elem : model.getElements()) {
            extractDependencies(elem, deps);
        }

        return new JclDependencyResult(sourceName, deps);
    }

    /**
     * Detect whether source content is JCL (by sentence type hint or heuristic).
     */
    public boolean isJclSource(String content, String sentenceType) {
        if (sentenceType != null && sentenceType.toUpperCase().contains("JCL")) {
            return true;
        }
        if (content == null || content.length() < 3) return false;

        String[] lines = content.split("\\r?\\n", 80);
        int jclLineCount = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//")) {
                jclLineCount++;
            } else {
                // JES spool output may prepend line numbers
                String stripped = trimmed.replaceFirst("^\\d+\\s+", "");
                if (stripped.startsWith("//")) {
                    jclLineCount++;
                }
            }
        }
        return jclLineCount >= 2;
    }

    // ═══════════════════════════════════════════════════════════
    //  Element → Dependency extraction
    // ═══════════════════════════════════════════════════════════

    private void extractDependencies(JclElement elem, List<JclDependency> deps) {
        JclElementType type = elem.getType();

        switch (type) {
            case EXEC: {
                String pgm = param(elem, "PGM", null);
                if (pgm != null && !pgm.isEmpty()) {
                    String stepName = elem.getName();
                    String detail = (stepName != null && !stepName.isEmpty())
                            ? "Step: " + stepName : null;
                    deps.add(new JclDependency(JclDependencyKind.PROGRAM,
                            pgm.toUpperCase(), elem.getLineNumber(), elem.getRawText(), detail));
                } else {
                    String proc = param(elem, "PROC", null);
                    if (proc != null && !proc.isEmpty()) {
                        String stepName = elem.getName();
                        String detail = (stepName != null && !stepName.isEmpty())
                                ? "Step: " + stepName : null;
                        deps.add(new JclDependency(JclDependencyKind.PROCEDURE,
                                proc.toUpperCase(), elem.getLineNumber(), elem.getRawText(), detail));
                    }
                }
                break;
            }

            case INCLUDE: {
                String member = param(elem, "MEMBER", null);
                if (member != null && !member.isEmpty()) {
                    deps.add(new JclDependency(JclDependencyKind.INCLUDE,
                            member.toUpperCase(), elem.getLineNumber(), elem.getRawText(), null));
                }
                break;
            }

            case JCLLIB: {
                String order = param(elem, "ORDER", null);
                if (order != null && !order.isEmpty()) {
                    // ORDER can be a comma-separated list in parentheses: ORDER=(DSN1,DSN2)
                    String cleaned = order.replaceAll("[()]", "");
                    String[] dsns = cleaned.split(",");
                    for (String dsn : dsns) {
                        String trimmed = dsn.trim();
                        if (!trimmed.isEmpty()) {
                            deps.add(new JclDependency(JclDependencyKind.JCLLIB,
                                    trimmed.toUpperCase(), elem.getLineNumber(), elem.getRawText(), null));
                        }
                    }
                }
                break;
            }

            case DD: {
                String dsn = param(elem, "DSN", null);
                if (dsn == null) {
                    dsn = param(elem, "DSNAME", null);
                }
                if (dsn != null && !dsn.isEmpty()) {
                    // Skip temporary datasets (&&xxx)
                    if (!dsn.startsWith("&&") && !dsn.startsWith("&")) {
                        String ddName = elem.getName();
                        String detail = (ddName != null && !ddName.isEmpty())
                                ? "DD: " + ddName : null;
                        deps.add(new JclDependency(JclDependencyKind.DATASET,
                                dsn.toUpperCase(), elem.getLineNumber(), elem.getRawText(), detail));
                    }
                }
                break;
            }

            default:
                break;
        }

        // Recursively process children (e.g. DDs under EXEC steps)
        for (JclElement child : elem.getChildren()) {
            extractDependencies(child, deps);
        }
    }

    private static String param(JclElement elem, String key, String fallback) {
        String val = elem.getParameter(key);
        if (val != null && !val.isEmpty()) {
            // Strip surrounding quotes if present
            if (val.length() >= 2
                    && ((val.charAt(0) == '\'' && val.charAt(val.length() - 1) == '\'')
                    || (val.charAt(0) == '"' && val.charAt(val.length() - 1) == '"'))) {
                val = val.substring(1, val.length() - 1);
            }
            return val;
        }
        return fallback;
    }
}

