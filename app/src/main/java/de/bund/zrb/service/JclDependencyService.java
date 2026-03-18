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
        /** Natural program extracted from PARM/ZPARM STACK=(LOGON lib;prog) */
        NATURAL_PROGRAM("🌿 Natural-Programme", "NAT"),
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
        private final String naturalLibrary;  // only for NATURAL_PROGRAM: the STEPLIB library
        private final String naturalProgram;  // only for NATURAL_PROGRAM: the program name

        public JclDependency(JclDependencyKind kind, String targetName, int lineNumber,
                             String sourceLine, String detail) {
            this(kind, targetName, lineNumber, sourceLine, detail, null, null);
        }

        public JclDependency(JclDependencyKind kind, String targetName, int lineNumber,
                             String sourceLine, String detail,
                             String naturalLibrary, String naturalProgram) {
            this.kind = kind;
            this.targetName = targetName;
            this.lineNumber = lineNumber;
            this.sourceLine = sourceLine;
            this.detail = detail;
            this.naturalLibrary = naturalLibrary;
            this.naturalProgram = naturalProgram;
        }

        public JclDependencyKind getKind() { return kind; }
        public String getTargetName() { return targetName; }
        public int getLineNumber() { return lineNumber; }
        public String getSourceLine() { return sourceLine; }
        public String getDetail() { return detail; }
        public String getNaturalLibrary() { return naturalLibrary; }
        public String getNaturalProgram() { return naturalProgram; }

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
    //  Call Hierarchy model
    // ═══════════════════════════════════════════════════════════

    /**
     * A node in the JCL call hierarchy tree.
     * Represents the hierarchical execution flow: JOB → EXEC (PGM/PROC) → DD (DSN).
     */
    public static class JclCallNode {
        private final String displayText;
        private final String icon;
        private final int lineNumber;
        private final List<JclCallNode> children;
        /** If non-null, this node represents a Natural program entry; value is "LIB;PROG". */
        private String naturalRef;

        public JclCallNode(String displayText, String icon, int lineNumber) {
            this.displayText = displayText;
            this.icon = icon;
            this.lineNumber = lineNumber;
            this.children = new ArrayList<JclCallNode>();
        }

        public String getDisplayText() { return displayText; }
        public String getIcon() { return icon; }
        public int getLineNumber() { return lineNumber; }
        public List<JclCallNode> getChildren() { return children; }
        public String getNaturalRef() { return naturalRef; }
        public void setNaturalRef(String naturalRef) { this.naturalRef = naturalRef; }

        public void addChild(JclCallNode child) {
            children.add(child);
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
     * Build a call hierarchy tree from JCL content.
     * <p>
     * The hierarchy reflects the JCL execution flow:
     * <pre>
     *   📋 JOBNAME (JOB)
     *     ▶ STEP01 → PGM=IDCAMS  [Zeile 5]
     *       📄 SYSIN → DSN=...   [Zeile 6]
     *       📄 SYSPRINT → SYSOUT=A  [Zeile 7]
     *     ▶ STEP02 → PROC=MYPROC  [Zeile 10]
     *       📄 INPUT → DSN=...   [Zeile 11]
     * </pre>
     *
     * @param jclContent  the JCL source text
     * @param sourceName  name of the source (for display)
     * @return list of root-level call hierarchy nodes (typically JOBs, or EXEC steps if no JOB found)
     */
    public List<JclCallNode> buildCallHierarchy(String jclContent, String sourceName) {
        if (jclContent == null || jclContent.isEmpty()) {
            return Collections.emptyList();
        }

        JclOutlineModel model = parser.parse(jclContent, sourceName);
        List<JclCallNode> roots = new ArrayList<JclCallNode>();

        // Collect JOBs as root nodes
        List<JclElement> jobs = model.getJobs();
        if (!jobs.isEmpty()) {
            for (JclElement job : jobs) {
                JclCallNode jobNode = buildJobNode(job);
                roots.add(jobNode);
            }
        }

        // If no JOB found, collect EXEC steps as roots (e.g. in-stream PROCs or standalone steps)
        if (roots.isEmpty()) {
            for (JclElement elem : model.getElements()) {
                if (elem.getType() == JclElementType.EXEC) {
                    roots.add(buildExecNode(elem));
                } else if (elem.getType() == JclElementType.PROC) {
                    JclCallNode procNode = new JclCallNode(
                            "📦 PROC " + safeLabel(elem.getName()) + "  [Zeile " + elem.getLineNumber() + "]",
                            "📦", elem.getLineNumber());
                    // Add children of the PROC (EXEC steps inside the PROC)
                    for (JclElement child : elem.getChildren()) {
                        if (child.getType() == JclElementType.EXEC) {
                            procNode.addChild(buildExecNode(child));
                        }
                    }
                    roots.add(procNode);
                }
            }
        }

        // If still nothing, add INCLUDE/JCLLIB as informational nodes
        if (roots.isEmpty()) {
            for (JclElement elem : model.getElements()) {
                if (elem.getType() == JclElementType.INCLUDE) {
                    String member = param(elem, "MEMBER", null);
                    roots.add(new JclCallNode(
                            "📎 INCLUDE " + safeLabel(member) + "  [Zeile " + elem.getLineNumber() + "]",
                            "📎", elem.getLineNumber()));
                } else if (elem.getType() == JclElementType.JCLLIB) {
                    String order = param(elem, "ORDER", null);
                    roots.add(new JclCallNode(
                            "📚 JCLLIB " + safeLabel(order) + "  [Zeile " + elem.getLineNumber() + "]",
                            "📚", elem.getLineNumber()));
                }
            }
        }

        return roots;
    }

    /**
     * Build a call hierarchy node for a JOB element with its child EXEC steps.
     */
    private JclCallNode buildJobNode(JclElement job) {
        String jobName = safeLabel(job.getName());
        JclCallNode jobNode = new JclCallNode(
                "📋 " + jobName + "  [Zeile " + job.getLineNumber() + "]",
                "📋", job.getLineNumber());

        for (JclElement child : job.getChildren()) {
            if (child.getType() == JclElementType.EXEC) {
                jobNode.addChild(buildExecNode(child));
            }
        }

        return jobNode;
    }

    /**
     * Build a call hierarchy node for an EXEC step with its child DD statements.
     */
    private JclCallNode buildExecNode(JclElement exec) {
        String stepLabel = buildExecLabel(exec);
        JclCallNode execNode = new JclCallNode(stepLabel, "▶", exec.getLineNumber());

        // Check for Natural program in PARM/ZPARM
        String rawText = exec.getRawText();
        String parm = param(exec, "PARM", null);
        String zparm = param(exec, "ZPARM", null);
        String[] sources = { parm, zparm, rawText };
        for (String src : sources) {
            if (src == null || src.isEmpty()) continue;
            java.util.regex.Matcher matcher = STACK_LOGON_PATTERN.matcher(src);
            if (matcher.find()) {
                String lib = matcher.group(1).toUpperCase();
                String prog = matcher.group(2).toUpperCase();
                JclCallNode natNode = new JclCallNode(
                        "🌿 " + prog + " (Lib: " + lib + ")  [Zeile " + exec.getLineNumber() + "]",
                        "🌿", exec.getLineNumber());
                natNode.setNaturalRef(lib + ";" + prog);
                execNode.addChild(natNode);
                break;
            }
        }

        for (JclElement child : exec.getChildren()) {
            if (child.getType() == JclElementType.DD) {
                execNode.addChild(buildDdNode(child));
            }
        }

        return execNode;
    }

    /**
     * Build a call hierarchy node for a DD statement.
     */
    private JclCallNode buildDdNode(JclElement dd) {
        String ddName = safeLabel(dd.getName());
        StringBuilder sb = new StringBuilder();
        sb.append("📄 ").append(ddName);

        String dsn = param(dd, "DSN", null);
        if (dsn == null) dsn = param(dd, "DSNAME", null);
        if (dsn != null && !dsn.isEmpty()) {
            sb.append(" → ").append(dsn);
        } else {
            // Check for SYSOUT
            String sysout = param(dd, "SYSOUT", null);
            if (sysout != null) {
                sb.append(" → SYSOUT=").append(sysout);
            }
        }

        String disp = param(dd, "DISP", null);
        if (disp != null) {
            sb.append("  [").append(disp).append("]");
        }

        sb.append("  [Zeile ").append(dd.getLineNumber()).append("]");

        return new JclCallNode(sb.toString(), "📄", dd.getLineNumber());
    }

    /**
     * Build a display label for an EXEC step.
     */
    private String buildExecLabel(JclElement exec) {
        StringBuilder sb = new StringBuilder();
        sb.append("▶ ");
        String stepName = exec.getName();
        if (stepName != null && !stepName.isEmpty()) {
            sb.append(stepName);
        } else {
            sb.append("(unnamed)");
        }

        String pgm = param(exec, "PGM", null);
        if (pgm != null && !pgm.isEmpty()) {
            sb.append(" → PGM=").append(pgm.toUpperCase());
        } else {
            String proc = param(exec, "PROC", null);
            if (proc != null && !proc.isEmpty()) {
                sb.append(" → PROC=").append(proc.toUpperCase());
            }
        }

        sb.append("  [Zeile ").append(exec.getLineNumber()).append("]");
        return sb.toString();
    }

    private static String safeLabel(String s) {
        return (s != null && !s.isEmpty()) ? s : "(unnamed)";
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
                // Extract Natural programs from PARM/ZPARM: STACK=(LOGON library;program)
                extractNaturalFromParm(elem, deps);
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

        // Note: no recursive child processing needed here because model.getElements()
        // already contains ALL elements (JOB, EXEC, DD, etc.) in a flat list.
        // The parser adds children to parent elements for structural queries (getJobs(), etc.)
        // but also adds them to the flat list, so iterating getElements() covers everything.
    }

    /**
     * Regex pattern to extract Natural programs from PARM/ZPARM parameters.
     * Matches STACK=(LOGON library;program) — with optional whitespace.
     * Group 1 = library, Group 2 = program.
     */
    private static final java.util.regex.Pattern STACK_LOGON_PATTERN =
            java.util.regex.Pattern.compile(
                    "STACK\\s*=\\s*\\(\\s*LOGON\\s+([A-Za-z0-9_-]+)\\s*;\\s*([A-Za-z0-9_-]+)",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Extract Natural program references from PARM/ZPARM of an EXEC element.
     * <p>
     * Natural steps typically look like:
     * <pre>
     *   //NATURAL1 EXEC NAT1,ZPARM='MADIO=0,MAXCL=0,STACK=(LOGON ABAK-M;ZDALXX0P)'
     * </pre>
     * or via PARM= with symbolic substitution containing STACK=(LOGON ...).
     * We scan the raw text of the element for the pattern.
     */
    private void extractNaturalFromParm(JclElement elem, List<JclDependency> deps) {
        // First try structured parameters
        String parm = param(elem, "PARM", null);
        String zparm = param(elem, "ZPARM", null);
        // Also scan full raw text to catch multi-line continuations and substitutions
        String rawText = elem.getRawText();

        String[] sources = { parm, zparm, rawText };
        for (String src : sources) {
            if (src == null || src.isEmpty()) continue;
            java.util.regex.Matcher matcher = STACK_LOGON_PATTERN.matcher(src);
            while (matcher.find()) {
                String library = matcher.group(1).toUpperCase();
                String program = matcher.group(2).toUpperCase();
                String stepName = elem.getName();
                String detail = (stepName != null && !stepName.isEmpty())
                        ? "Step: " + stepName + ", Lib: " + library
                        : "Lib: " + library;
                deps.add(new JclDependency(JclDependencyKind.NATURAL_PROGRAM,
                        program, elem.getLineNumber(), elem.getRawText(), detail,
                        library, program));
                return; // one Natural ref per EXEC step is typical
            }
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

