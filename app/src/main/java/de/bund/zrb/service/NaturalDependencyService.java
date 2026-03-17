package de.bund.zrb.service;

import de.bund.zrb.jcl.model.JclElement;
import de.bund.zrb.jcl.model.JclElementType;
import de.bund.zrb.jcl.model.JclOutlineModel;
import de.bund.zrb.jcl.parser.NaturalParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central service for extracting dependencies from Natural source code.
 * <p>
 * Parses Natural source using {@link NaturalParser} and extracts all external references
 * (CALLNAT, FETCH, PERFORM, INCLUDE, USING data areas, VIEW/DDM references, DB operations).
 * <p>
 * This is analogous to NaturalONE's NaturalBuilder which builds a dependency graph by parsing
 * all source objects. NaturalONE's Dependencies view shows "Active XRefs" (callees — what this
 * program calls) and "Passive XRefs" (callers — who calls this program). We implement the
 * "Active XRefs" part here by parsing a single source; passive XRefs require scanning all
 * sources in a library (future enhancement).
 * <p>
 * The service is stateless and thread-safe — create one instance and share it.
 */
public class NaturalDependencyService {

    private final NaturalParser parser = new NaturalParser();

    // ═══════════════════════════════════════════════════════════
    //  Dependency model
    // ═══════════════════════════════════════════════════════════

    /**
     * Classification of a Natural dependency reference.
     */
    public enum DependencyKind {
        /** CALLNAT → external subprogram call */
        CALLNAT("📞 CALLNAT", "CALLNAT"),
        /** FETCH / FETCH RETURN → program invocation */
        FETCH("📞 FETCH", "FETCH"),
        /** CALL → 3GL call */
        CALL("📞 CALL (3GL)", "CALL"),
        /** PERFORM → inline or external subroutine call */
        PERFORM("🔄 PERFORM", "PERFORM"),
        /** INCLUDE → copycode inclusion */
        INCLUDE("📎 INCLUDE (Copycode)", "INCLUDE"),
        /** LOCAL USING / PARAMETER USING / GLOBAL USING → data area reference */
        USING("📦 USING (Data Area)", "USING"),
        /** INPUT MAP / WRITE MAP → map reference */
        INPUT_MAP("🖥 MAP (Input/Write)", "INPUT_MAP"),
        /** VIEW OF → DDM reference */
        VIEW("📊 VIEW (DDM)", "VIEW"),
        /** READ / FIND / HISTOGRAM / STORE / UPDATE / DELETE / GET → database access */
        DB_ACCESS("🗄 DB-Zugriff", "DB_ACCESS");

        private final String displayLabel;
        private final String code;

        DependencyKind(String displayLabel, String code) {
            this.displayLabel = displayLabel;
            this.code = code;
        }

        public String getDisplayLabel() { return displayLabel; }
        public String getCode() { return code; }
    }

    /**
     * A single dependency extracted from Natural source code.
     */
    public static class Dependency {
        private final DependencyKind kind;
        private final String targetName;
        private final int lineNumber;
        private final String sourceLine;
        private final String detail; // e.g. scope (LOCAL/PARAMETER/GLOBAL) or DB operation type

        public Dependency(DependencyKind kind, String targetName, int lineNumber,
                          String sourceLine, String detail) {
            this.kind = kind;
            this.targetName = targetName;
            this.lineNumber = lineNumber;
            this.sourceLine = sourceLine;
            this.detail = detail;
        }

        public DependencyKind getKind() { return kind; }
        public String getTargetName() { return targetName; }
        public int getLineNumber() { return lineNumber; }
        public String getSourceLine() { return sourceLine; }
        public String getDetail() { return detail; }

        /**
         * Display label for tree nodes, e.g. "CALLNAT MYSUBPROG  [Zeile 42]"
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
     * Result of dependency analysis: all dependencies grouped by kind.
     */
    public static class DependencyResult {
        private final String sourceName;
        private final List<Dependency> allDependencies;
        private final Map<DependencyKind, List<Dependency>> grouped;

        public DependencyResult(String sourceName, List<Dependency> allDependencies) {
            this.sourceName = sourceName;
            this.allDependencies = Collections.unmodifiableList(allDependencies);
            // Group by kind, preserving insertion order
            Map<DependencyKind, List<Dependency>> map = new LinkedHashMap<DependencyKind, List<Dependency>>();
            for (Dependency dep : allDependencies) {
                List<Dependency> list = map.get(dep.getKind());
                if (list == null) {
                    list = new ArrayList<Dependency>();
                    map.put(dep.getKind(), list);
                }
                list.add(dep);
            }
            this.grouped = Collections.unmodifiableMap(map);
        }

        public String getSourceName() { return sourceName; }
        public List<Dependency> getAllDependencies() { return allDependencies; }
        public Map<DependencyKind, List<Dependency>> getGrouped() { return grouped; }
        public boolean isEmpty() { return allDependencies.isEmpty(); }
        public int getTotalCount() { return allDependencies.size(); }

        /**
         * Get unique target names for external calls (CALLNAT + FETCH + CALL).
         * Useful for resolving passive XRefs or loading related sources.
         */
        public List<String> getExternalCallTargets() {
            List<String> targets = new ArrayList<String>();
            for (Dependency dep : allDependencies) {
                if (dep.getKind() == DependencyKind.CALLNAT
                        || dep.getKind() == DependencyKind.FETCH
                        || dep.getKind() == DependencyKind.CALL) {
                    String name = dep.getTargetName().toUpperCase();
                    if (!targets.contains(name)) {
                        targets.add(name);
                    }
                }
            }
            return targets;
        }

        /**
         * Get unique data area names referenced via USING.
         */
        public List<String> getDataAreaTargets() {
            List<String> targets = new ArrayList<String>();
            for (Dependency dep : allDependencies) {
                if (dep.getKind() == DependencyKind.USING) {
                    String name = dep.getTargetName().toUpperCase();
                    if (!targets.contains(name)) {
                        targets.add(name);
                    }
                }
            }
            return targets;
        }

        /**
         * Get unique copycode names referenced via INCLUDE.
         */
        public List<String> getCopycodeTargets() {
            List<String> targets = new ArrayList<String>();
            for (Dependency dep : allDependencies) {
                if (dep.getKind() == DependencyKind.INCLUDE) {
                    String name = dep.getTargetName().toUpperCase();
                    if (!targets.contains(name)) {
                        targets.add(name);
                    }
                }
            }
            return targets;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Analysis
    // ═══════════════════════════════════════════════════════════

    /**
     * Analyze Natural source code and extract all dependencies.
     *
     * @param sourceCode  the Natural source text
     * @param sourceName  name of the source object (for display)
     * @return dependency analysis result, never null
     */
    public DependencyResult analyze(String sourceCode, String sourceName) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            return new DependencyResult(sourceName, Collections.<Dependency>emptyList());
        }

        JclOutlineModel model = parser.parse(sourceCode, sourceName);
        List<Dependency> deps = new ArrayList<Dependency>();

        for (JclElement elem : model.getElements()) {
            Dependency dep = toDependency(elem);
            if (dep != null) {
                deps.add(dep);
            }
        }

        return new DependencyResult(sourceName, deps);
    }

    /**
     * Convert a JclElement to a Dependency if it represents an external reference.
     */
    private Dependency toDependency(JclElement elem) {
        JclElementType type = elem.getType();

        switch (type) {
            case NAT_CALLNAT:
                return new Dependency(DependencyKind.CALLNAT,
                        param(elem, "TARGET", elem.getName()),
                        elem.getLineNumber(), elem.getRawText(), null);

            case NAT_FETCH:
                return new Dependency(DependencyKind.FETCH,
                        param(elem, "TARGET", elem.getName()),
                        elem.getLineNumber(), elem.getRawText(), null);

            case NAT_CALL:
                return new Dependency(DependencyKind.CALL,
                        param(elem, "TARGET", elem.getName()),
                        elem.getLineNumber(), elem.getRawText(), null);

            case NAT_PERFORM:
                return new Dependency(DependencyKind.PERFORM,
                        param(elem, "TARGET", elem.getName()),
                        elem.getLineNumber(), elem.getRawText(), null);

            case NAT_INCLUDE:
                return new Dependency(DependencyKind.INCLUDE,
                        param(elem, "COPYCODE", elem.getName()),
                        elem.getLineNumber(), elem.getRawText(), null);

            case NAT_LOCAL:
            case NAT_PARAMETER:
            case NAT_GLOBAL:
            case NAT_INDEPENDENT: {
                String using = param(elem, "USING", null);
                if (using != null && !using.isEmpty()) {
                    String scope = type.getDisplayName().toUpperCase();
                    return new Dependency(DependencyKind.USING,
                            using, elem.getLineNumber(), elem.getRawText(), scope);
                }
                return null;
            }

            case NAT_DATA_VIEW: {
                String ddm = param(elem, "OF", null);
                if (ddm != null && !ddm.isEmpty()) {
                    return new Dependency(DependencyKind.VIEW,
                            ddm, elem.getLineNumber(), elem.getRawText(), null);
                }
                return null;
            }

            case NAT_INPUT:
            case NAT_WRITE: {
                String map = param(elem, "MAP", null);
                if (map != null && !map.isEmpty()) {
                    String stmtType = type == JclElementType.NAT_INPUT ? "INPUT" : "WRITE";
                    return new Dependency(DependencyKind.INPUT_MAP,
                            map, elem.getLineNumber(), elem.getRawText(), stmtType);
                }
                return null;
            }

            case NAT_READ:
            case NAT_FIND:
            case NAT_HISTOGRAM:
            case NAT_STORE:
            case NAT_UPDATE:
            case NAT_DELETE:
            case NAT_GET: {
                String file = param(elem, "FILE", null);
                if (file != null && !file.isEmpty()) {
                    String opType = type.getDisplayName().toUpperCase();
                    return new Dependency(DependencyKind.DB_ACCESS,
                            file, elem.getLineNumber(), elem.getRawText(), opType);
                }
                return null;
            }

            default:
                return null;
        }
    }

    private static String param(JclElement elem, String key, String fallback) {
        String val = elem.getParameter(key);
        if (val != null && !val.isEmpty()) {
            // Strip surrounding quotes if present (e.g. 'MYSUBPROG')
            if (val.length() >= 2
                    && ((val.charAt(0) == '\'' && val.charAt(val.length() - 1) == '\'')
                    || (val.charAt(0) == '"' && val.charAt(val.length() - 1) == '"'))) {
                val = val.substring(1, val.length() - 1);
            }
            return val;
        }
        return fallback != null ? fallback : "";
    }
}

