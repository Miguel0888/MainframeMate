package de.bund.zrb.ui.mermaid;

import de.bund.zrb.jcl.model.JclElement;
import de.bund.zrb.jcl.model.JclElementType;
import de.bund.zrb.jcl.model.JclOutlineModel;
import de.bund.zrb.service.codeanalytics.CallTreeNode;

import java.util.*;

/**
 * Converts a parsed {@link JclOutlineModel} (JCL / COBOL / Natural) into
 * Mermaid diagram code in various diagram types.
 * <p>
 * Supported diagram types:
 * <ul>
 *   <li><b>STRUCTURE</b> — flowchart showing structural hierarchy</li>
 *   <li><b>FLOWCHART</b> — flowchart focusing on control flow: branches, loops, and external calls</li>
 *   <li><b>SEQUENCE</b> — sequence diagram showing execution/call flow</li>
 *   <li><b>MINDMAP</b> — mind-map for quick overview of code structure</li>
 * </ul>
 */
public final class OutlineToMermaidConverter {

    private OutlineToMermaidConverter() {}

    // IBM Blue styling for system function nodes in Mermaid diagrams
    private static final String SYSFUNC_STYLE = "fill:#0530AD,color:#fff,stroke:#002D9C,stroke-width:2px";

    /**
     * Lazily-loaded set of known system function names (uppercase).
     * Loaded from the user-configurable system_functions.json.
     */
    private static Set<String> loadSystemFunctionNames() {
        try {
            java.util.Map<String, ?> lookup =
                    de.bund.zrb.helper.SystemFunctionSettingsHelper.buildLookup();
            return lookup.keySet(); // already uppercase
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    /**
     * Diagram types that can be generated from an outline model.
     */
    public enum DiagramType {
        /** Flowchart showing structural hierarchy (JOB→STEP→DD, Divisions, Subroutines). */
        STRUCTURE("\uD83C\uDFD7", "Struktur"),   // 🏗
        /** Flowchart focusing on control flow: branches, loops, and external calls. */
        FLOWCHART("\uD83D\uDD00", "Ablauf"),       // 🔀
        /** Sequence diagram showing execution / call flow. */
        SEQUENCE("\u21C4", "Sequenz"),              // ⇄
        /** Mind-map for quick bird's-eye overview. */
        MINDMAP("\uD83E\uDDE0", "Mindmap");        // 🧠

        private final String icon;
        private final String label;

        DiagramType(String icon, String label) {
            this.icon = icon;
            this.label = label;
        }

        public String getIcon() { return icon; }
        public String getLabel() { return label; }
    }

    /**
     * Convert an outline model to Mermaid code using the default STRUCTURE type.
     */
    public static String convert(JclOutlineModel model) {
        return convert(model, DiagramType.STRUCTURE);
    }

    /**
     * Convert an outline model to Mermaid code for the given diagram type.
     *
     * @param model the parsed outline
     * @param type  desired diagram type
     * @return Mermaid source code, or {@code null} if the model is empty
     */
    public static String convert(JclOutlineModel model, DiagramType type) {
        return convert(model, type, null);
    }

    /**
     * Convert an outline model to Mermaid code for the given diagram type,
     * optionally with a recursive call tree for MINDMAP enrichment.
     *
     * @param model    the parsed outline
     * @param type     desired diagram type
     * @param callTree recursive call tree (from CodeAnalyticsService), or null
     * @return Mermaid source code, or {@code null} if the model is empty
     */
    public static String convert(JclOutlineModel model, DiagramType type, CallTreeNode callTree) {
        if (model == null || model.isEmpty()) return null;
        if (type == null) type = DiagramType.STRUCTURE;

        Set<String> sysFuncs = loadSystemFunctionNames();

        switch (type) {
            case FLOWCHART: return convertFlowchart(model, sysFuncs);
            case SEQUENCE:  return convertSequence(model, sysFuncs);
            case MINDMAP:   return convertMindmap(model, callTree, sysFuncs);
            case STRUCTURE:
            default:        return convertStructure(model, sysFuncs);
        }
    }

    /** Check if a name is a known system function. */
    private static boolean isSysFunc(String name, Set<String> sysFuncs) {
        return name != null && sysFuncs.contains(name.toUpperCase());
    }

    /** Append a Mermaid style directive for system function nodes (IBM Blue). */
    private static void styleSysFunc(StringBuilder sb, String nodeId) {
        sb.append("    style ").append(nodeId).append(" ").append(SYSFUNC_STYLE).append("\n");
    }

    // ═══════════════════════════════════════════════════════════
    //  STRUCTURE (existing flowchart)
    // ═══════════════════════════════════════════════════════════

    private static String convertStructure(JclOutlineModel model, Set<String> sysFuncs) {
        switch (model.getLanguage()) {
            case JCL:     return convertJcl(model, sysFuncs);
            case COBOL:   return convertCobol(model, sysFuncs);
            case NATURAL: return convertNatural(model, sysFuncs);
            default:      return convertJcl(model, sysFuncs);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  JCL
    // ══════════════════════════════════════════════════════════

    private static String convertJcl(JclOutlineModel model, Set<String> sysFuncs) {
        StringBuilder sb = new StringBuilder("flowchart TD\n");
        Set<String> usedIds = new HashSet<String>();

        List<JclElement> jobs = model.getJobs();
        List<JclElement> steps = model.getSteps();

        // Group elements by parent (JOB → children)
        if (jobs.isEmpty() && steps.isEmpty()) {
            // Flat list: just show all elements sequentially
            return convertFlatList(model, "JCL");
        }

        String prevStepId = null;

        // If there's a JOB card, start with it
        for (JclElement job : jobs) {
            String jobId = safeId("JOB_" + job.getName(), usedIds);
            sb.append("    ").append(jobId).append("([\"").append(esc(job.getName())).append("\"])\n");
            prevStepId = jobId;
        }

        // EXEC steps
        for (JclElement step : steps) {
            String pgm = step.getParameter("PGM");
            String proc = step.getParameter("PROC");
            String label = step.getName() != null ? step.getName() : "(unnamed)";
            String detail = pgm != null ? "PGM=" + pgm : (proc != null ? "PROC=" + proc : "");

            String stepId = safeId("STEP_" + label, usedIds);
            sb.append("    ").append(stepId).append("[\"").append(esc(label));
            if (!detail.isEmpty()) {
                sb.append("\\n").append(esc(detail));
            }
            sb.append("\"]\n");

            // Style system function steps with IBM Blue
            if (isSysFunc(pgm, sysFuncs)) {
                styleSysFunc(sb, stepId);
            }

            if (prevStepId != null) {
                sb.append("    ").append(prevStepId).append(" --> ").append(stepId).append("\n");
            }

            // DD statements as children of step
            for (JclElement child : step.getChildren()) {
                if (child.getType() == JclElementType.DD) {
                    String dsn = child.getParameter("DSN");
                    String ddLabel = child.getName() != null ? child.getName() : "DD";
                    String ddId = safeId("DD_" + stepId + "_" + ddLabel, usedIds);

                    sb.append("    ").append(ddId).append("[(\"").append(esc(ddLabel));
                    if (dsn != null) {
                        sb.append("\\n").append(esc(truncate(dsn, 25)));
                    }
                    sb.append("\")]\n");
                    sb.append("    ").append(stepId).append(" --> ").append(ddId).append("\n");
                }
            }

            prevStepId = stepId;
        }

        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════
    //  COBOL
    // ══════════════════════════════════════════════════════════

    private static String convertCobol(JclOutlineModel model, Set<String> sysFuncs) {
        StringBuilder sb = new StringBuilder("flowchart TD\n");
        Set<String> usedIds = new HashSet<String>();

        // Program ID
        List<JclElement> all = model.getElements();
        String programId = null;
        for (JclElement e : all) {
            if (e.getType() == JclElementType.PROGRAM_ID) {
                programId = e.getName();
                break;
            }
        }

        String rootId = safeId("PROG", usedIds);
        sb.append("    ").append(rootId).append("([\"").append(esc(programId != null ? programId : "PROGRAM"))
                .append("\"])\n");

        // Divisions as main blocks
        for (JclElement div : model.getDivisions()) {
            String divId = safeId("DIV_" + div.getName(), usedIds);
            sb.append("    ").append(divId).append("[\"").append(esc(div.getName())).append("\"]\n");
            sb.append("    ").append(rootId).append(" --> ").append(divId).append("\n");
        }

        // Sections and Paragraphs in Procedure Division
        List<JclElement> paragraphs = model.getParagraphs();
        String prevParaId = null;
        for (JclElement para : paragraphs) {
            String paraId = safeId("PARA_" + para.getName(), usedIds);
            sb.append("    ").append(paraId).append("[\"").append(esc(para.getName())).append("\"]\n");

            if (prevParaId != null) {
                sb.append("    ").append(prevParaId).append(" --> ").append(paraId).append("\n");
            } else {
                // Link first paragraph to Procedure Division
                sb.append("    ").append(rootId).append(" --> ").append(paraId).append("\n");
            }
            prevParaId = paraId;
        }

        // PERFORM / CALL edges
        for (JclElement e : all) {
            String target = e.getParameter("TARGET");
            if (target == null) continue;
            String sourceParent = findParentParagraph(e, all);

            if (e.getType() == JclElementType.PERFORM_STMT) {
                String fromId = sourceParent != null ? findIdForName("PARA_" + sourceParent, usedIds) : rootId;
                String toId = findIdForName("PARA_" + target, usedIds);
                if (fromId != null && toId != null) {
                    sb.append("    ").append(fromId).append(" -.->|PERFORM| ").append(toId).append("\n");
                }
            } else if (e.getType() == JclElementType.CALL_STMT) {
                String callId = safeId("CALL_" + target, usedIds);
                sb.append("    ").append(callId).append(">\"").append(esc(target)).append("\"]\n");
                if (isSysFunc(target, sysFuncs)) {
                    styleSysFunc(sb, callId);
                }
                String fromId = sourceParent != null ? findIdForName("PARA_" + sourceParent, usedIds) : rootId;
                if (fromId != null) {
                    sb.append("    ").append(fromId).append(" ==>|CALL| ").append(callId).append("\n");
                }
            }
        }

        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════
    //  Natural
    // ══════════════════════════════════════════════════════════

    private static String convertNatural(JclOutlineModel model, Set<String> sysFuncs) {
        StringBuilder sb = new StringBuilder("flowchart TD\n");
        Set<String> usedIds = new HashSet<String>();
        List<JclElement> all = model.getElements();

        // Program/Subprogram root
        String progName = model.getSourceName() != null ? model.getSourceName() : "PROGRAM";
        for (JclElement e : all) {
            if (e.getType() == JclElementType.NAT_PROGRAM
                    || e.getType() == JclElementType.NAT_SUBPROGRAM
                    || e.getType() == JclElementType.NAT_FUNCTION) {
                if (e.getName() != null) progName = e.getName();
                break;
            }
        }

        String rootId = safeId("PROG", usedIds);
        sb.append("    ").append(rootId).append("([\"").append(esc(progName)).append("\"])\n");

        // DEFINE DATA block
        boolean hasData = false;
        for (JclElement e : all) {
            if (e.getType() == JclElementType.NAT_DEFINE_DATA) {
                hasData = true;
                break;
            }
        }
        if (hasData) {
            String dataId = safeId("DATA", usedIds);
            sb.append("    ").append(dataId).append("[\"DEFINE DATA\"]\n");
            sb.append("    ").append(rootId).append(" --> ").append(dataId).append("\n");

            // LOCAL / PARAMETER / GLOBAL blocks
            for (JclElement e : all) {
                if (e.getType() == JclElementType.NAT_LOCAL
                        || e.getType() == JclElementType.NAT_PARAMETER
                        || e.getType() == JclElementType.NAT_GLOBAL
                        || e.getType() == JclElementType.NAT_INDEPENDENT) {
                    String blockId = safeId("DATA_" + e.getType().getDisplayName(), usedIds);
                    sb.append("    ").append(blockId).append("[\"")
                            .append(esc(e.getType().getDisplayName())).append("\"]\n");
                    sb.append("    ").append(dataId).append(" --> ").append(blockId).append("\n");
                }
            }
        }

        // Inline subroutines
        for (JclElement sub : model.getSubroutines()) {
            String subId = safeId("SUB_" + sub.getName(), usedIds);
            sb.append("    ").append(subId).append("{{\"").append(esc(sub.getName())).append("\"}}\n");
            sb.append("    ").append(rootId).append(" --> ").append(subId).append("\n");
        }

        // External calls (CALLNAT, CALL, FETCH)
        for (JclElement call : model.getNaturalCalls()) {
            String target = call.getParameter("TARGET");
            if (target == null) target = call.getName();
            if (target == null) continue;

            String callId = safeId("EXT_" + target, usedIds);
            String edgeLabel = call.getType().getDisplayName();

            // Only create node if not already created
            if (!usedIds.contains(normalizeId("EXT_" + target))) {
                sb.append("    ").append(callId).append(">\"").append(esc(target)).append("\"]\n");
                if (isSysFunc(target, sysFuncs)) {
                    styleSysFunc(sb, callId);
                }
            }
            sb.append("    ").append(rootId).append(" -.->|").append(edgeLabel).append("| ")
                    .append(callId).append("\n");
        }

        // DB operations
        for (JclElement db : model.getNaturalDbOps()) {
            String file = db.getParameter("FILE");
            if (file == null) file = db.getName();
            if (file == null) continue;

            String dbId = safeId("DB_" + file, usedIds);
            String op = db.getType().getDisplayName();

            if (!usedIds.contains(normalizeId("DB_" + file))) {
                sb.append("    ").append(dbId).append("[(\"").append(esc(file)).append("\")]\n");
            }
            sb.append("    ").append(rootId).append(" ==>|").append(op).append("| ")
                    .append(dbId).append("\n");
        }

        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════
    //  Fallback: flat list
    // ══════════════════════════════════════════════════════════

    private static String convertFlatList(JclOutlineModel model, String header) {
        StringBuilder sb = new StringBuilder("flowchart TD\n");
        Set<String> usedIds = new HashSet<String>();

        String prevId = null;
        for (JclElement e : model.getElements()) {
            String label = e.getName() != null ? e.getName() : e.getType().getDisplayName();
            String id = safeId(e.getType().name() + "_" + label, usedIds);
            sb.append("    ").append(id).append("[\"").append(esc(label)).append("\"]\n");
            if (prevId != null) {
                sb.append("    ").append(prevId).append(" --> ").append(id).append("\n");
            }
            prevId = id;
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════
    //  FLOWCHART (control flow: branches, loops, external calls)
    // ═══════════════════════════════════════════════════════════

    private static String convertFlowchart(JclOutlineModel model, Set<String> sysFuncs) {
        switch (model.getLanguage()) {
            case JCL:     return convertJclFlowchart(model, sysFuncs);
            case COBOL:   return convertCobolFlowchart(model, sysFuncs);
            case NATURAL: return convertNaturalFlowchart(model, sysFuncs);
            default:      return convertJclFlowchart(model, sysFuncs);
        }
    }

    private static String convertJclFlowchart(JclOutlineModel model, Set<String> sysFuncs) {
        StringBuilder sb = new StringBuilder("flowchart TD\n");
        Set<String> usedIds = new HashSet<String>();
        List<JclElement> all = model.getElements();
        List<JclElement> steps = model.getSteps();

        // Start node
        String startId = safeId("START", usedIds);
        sb.append("    ").append(startId).append("([\"START\"])\n");

        String prevId = startId;

        // Check for IF/ELSE/ENDIF conditional blocks
        boolean hasConditionals = false;
        for (JclElement e : all) {
            if (e.getType() == JclElementType.IF) { hasConditionals = true; break; }
        }

        if (hasConditionals) {
            // Build flow with conditionals
            prevId = buildJclConditionalFlow(sb, all, steps, usedIds, startId);
        } else {
            // Linear step flow
            for (JclElement step : steps) {
                String label = step.getName() != null ? step.getName() : "(Step)";
                String pgm = step.getParameter("PGM");
                String proc = step.getParameter("PROC");
                String stepId = safeId("STEP_" + label, usedIds);

                sb.append("    ").append(stepId).append("[\"").append(esc(label));
                if (pgm != null) sb.append("\\nPGM=").append(esc(pgm));
                else if (proc != null) sb.append("\\nPROC=").append(esc(proc));
                sb.append("\"]\n");

                // Style system function steps with IBM Blue
                if (isSysFunc(pgm, sysFuncs)) {
                    styleSysFunc(sb, stepId);
                }

                sb.append("    ").append(prevId).append(" --> ").append(stepId).append("\n");

                // Highlight PROC calls as external (different shape + style)
                if (proc != null) {
                    String extId = safeId("EXT_" + proc, usedIds);
                    sb.append("    ").append(extId).append(">\"").append(esc(proc))
                            .append("\\n(externe Prozedur)\"]\n");
                    sb.append("    ").append(stepId).append(" -.->|PROC| ").append(extId).append("\n");
                    sb.append("    style ").append(extId).append(" fill:#ffe0b2,stroke:#e65100,stroke-width:2px\n");
                }

                prevId = stepId;
            }
        }

        // End node
        String endId = safeId("END", usedIds);
        sb.append("    ").append(endId).append("([\"ENDE\"])\n");
        sb.append("    ").append(prevId).append(" --> ").append(endId).append("\n");

        return sb.toString();
    }

    /**
     * Build JCL flow with IF/ELSE/ENDIF conditional branches.
     * Returns the ID of the last node in the flow.
     */
    private static String buildJclConditionalFlow(StringBuilder sb, List<JclElement> all,
                                                   List<JclElement> steps,
                                                   Set<String> usedIds, String startId) {
        String prevId = startId;
        int stepIdx = 0;

        for (int i = 0; i < all.size(); i++) {
            JclElement e = all.get(i);

            if (e.getType() == JclElementType.IF) {
                // Decision diamond
                String condId = safeId("IF_" + (i + 1), usedIds);
                String condLabel = e.getName() != null ? e.getName() : "Bedingung";
                sb.append("    ").append(condId).append("{\"").append(esc(condLabel)).append("\"}\n");
                sb.append("    ").append(prevId).append(" --> ").append(condId).append("\n");

                // Find matching ELSE/ENDIF
                String trueBranch = condId;
                String falseBranch = null;
                String mergeId = safeId("MERGE_" + (i + 1), usedIds);

                // Steps between IF and ELSE/ENDIF → true branch
                String trueEnd = trueBranch;
                for (int j = i + 1; j < all.size(); j++) {
                    JclElement next = all.get(j);
                    if (next.getType() == JclElementType.ELSE || next.getType() == JclElementType.ENDIF) {
                        if (next.getType() == JclElementType.ELSE) {
                            falseBranch = condId;
                        }
                        break;
                    }
                    if (next.getType() == JclElementType.EXEC && stepIdx < steps.size()) {
                        JclElement step = steps.get(stepIdx++);
                        String label = step.getName() != null ? step.getName() : "(Step)";
                        String stepId = safeId("STEP_" + label, usedIds);
                        sb.append("    ").append(stepId).append("[\"").append(esc(label)).append("\"]\n");
                        sb.append("    ").append(trueEnd).append(" -->|Ja| ").append(stepId).append("\n");
                        trueEnd = stepId;
                    }
                }
                sb.append("    ").append(trueEnd).append(" --> ").append(mergeId).append("\n");

                // ELSE branch
                if (falseBranch != null) {
                    String falseEnd = condId;
                    boolean inElse = false;
                    for (int j = i + 1; j < all.size(); j++) {
                        JclElement next = all.get(j);
                        if (next.getType() == JclElementType.ELSE) { inElse = true; continue; }
                        if (next.getType() == JclElementType.ENDIF) break;
                        if (inElse && next.getType() == JclElementType.EXEC && stepIdx < steps.size()) {
                            JclElement step = steps.get(stepIdx++);
                            String label = step.getName() != null ? step.getName() : "(Step)";
                            String stepId = safeId("STEP_" + label, usedIds);
                            sb.append("    ").append(stepId).append("[\"").append(esc(label)).append("\"]\n");
                            sb.append("    ").append(falseEnd).append(" -->|Nein| ").append(stepId).append("\n");
                            falseEnd = stepId;
                        }
                    }
                    sb.append("    ").append(falseEnd).append(" --> ").append(mergeId).append("\n");
                } else {
                    sb.append("    ").append(condId).append(" -->|Nein| ").append(mergeId).append("\n");
                }

                sb.append("    ").append(mergeId).append("(( ))\n"); // merge point (circle)
                prevId = mergeId;

            } else if (e.getType() == JclElementType.EXEC) {
                // Regular step (not inside IF)
                if (stepIdx < steps.size()) {
                    JclElement step = steps.get(stepIdx++);
                    String label = step.getName() != null ? step.getName() : "(Step)";
                    String stepId = safeId("STEP_" + label, usedIds);
                    sb.append("    ").append(stepId).append("[\"").append(esc(label)).append("\"]\n");
                    sb.append("    ").append(prevId).append(" --> ").append(stepId).append("\n");
                    prevId = stepId;
                }
            }
        }

        return prevId;
    }

    private static String convertCobolFlowchart(JclOutlineModel model, Set<String> sysFuncs) {
        StringBuilder sb = new StringBuilder("flowchart TD\n");
        Set<String> usedIds = new HashSet<String>();
        List<JclElement> all = model.getElements();

        // Program root
        String progName = "PROGRAM";
        for (JclElement e : all) {
            if (e.getType() == JclElementType.PROGRAM_ID && e.getName() != null) {
                progName = e.getName();
                break;
            }
        }
        String startId = safeId("START", usedIds);
        sb.append("    ").append(startId).append("([\"").append(esc(progName)).append("\"])\n");

        // Paragraphs as main flow
        String prevId = startId;
        for (JclElement para : model.getParagraphs()) {
            String paraId = safeId("PARA_" + para.getName(), usedIds);
            sb.append("    ").append(paraId).append("[\"").append(esc(para.getName())).append("\"]\n");
            sb.append("    ").append(prevId).append(" --> ").append(paraId).append("\n");
            prevId = paraId;
        }

        // PERFORM edges (loops / internal calls)
        for (JclElement e : all) {
            if (e.getType() == JclElementType.PERFORM_STMT) {
                String target = e.getParameter("TARGET");
                if (target == null) continue;
                String sourceParent = findParentParagraph(e, all);
                String fromId = sourceParent != null ? findIdForName("PARA_" + sourceParent, usedIds) : startId;
                String toId = findIdForName("PARA_" + target, usedIds);
                if (fromId != null && toId != null) {
                    sb.append("    ").append(fromId).append(" -.->|PERFORM| ").append(toId).append("\n");
                }
            }
        }

        // CALL edges (external programs — highlighted)
        for (JclElement e : all) {
            if (e.getType() == JclElementType.CALL_STMT) {
                String target = e.getParameter("TARGET");
                if (target == null) continue;
                String sourceParent = findParentParagraph(e, all);
                String fromId = sourceParent != null ? findIdForName("PARA_" + sourceParent, usedIds) : startId;

                String extId = safeId("EXT_" + target, usedIds);
                sb.append("    ").append(extId).append(">\"").append(esc(target))
                        .append("\\n(externes Programm)\"]\n");
                // System functions → IBM Blue; others → orange highlight
                if (isSysFunc(target, sysFuncs)) {
                    styleSysFunc(sb, extId);
                } else {
                    sb.append("    style ").append(extId).append(" fill:#ffe0b2,stroke:#e65100,stroke-width:2px\n");
                }
                if (fromId != null) {
                    sb.append("    ").append(fromId).append(" ==>|CALL| ").append(extId).append("\n");
                }
            }
        }

        // End node
        String endId = safeId("END", usedIds);
        sb.append("    ").append(endId).append("([\"STOP RUN\"])\n");
        sb.append("    ").append(prevId).append(" --> ").append(endId).append("\n");

        return sb.toString();
    }

    private static String convertNaturalFlowchart(JclOutlineModel model, Set<String> sysFuncs) {
        StringBuilder sb = new StringBuilder("flowchart TD\n");
        Set<String> usedIds = new HashSet<String>();
        List<JclElement> all = model.getElements();

        // Program name
        String progName = model.getSourceName() != null ? model.getSourceName() : "PROGRAM";
        for (JclElement e : all) {
            if (e.getType() == JclElementType.NAT_PROGRAM
                    || e.getType() == JclElementType.NAT_SUBPROGRAM
                    || e.getType() == JclElementType.NAT_FUNCTION) {
                if (e.getName() != null) progName = e.getName();
                break;
            }
        }
        String startId = safeId("START", usedIds);
        sb.append("    ").append(startId).append("([\"").append(esc(progName)).append("\"])\n");

        String prevId = startId;

        // Walk through elements and build control flow
        for (JclElement e : all) {
            JclElementType t = e.getType();

            // ── Branches (IF, DECIDE) ──
            if (t == JclElementType.NAT_IF_BLOCK || t == JclElementType.NAT_DECIDE) {
                String condLabel = e.getName() != null ? e.getName() : t.getDisplayName();
                String condId = safeId("COND_" + condLabel, usedIds);
                sb.append("    ").append(condId).append("{\"").append(esc(condLabel)).append("\"}\n");
                sb.append("    ").append(prevId).append(" --> ").append(condId).append("\n");
                prevId = condId;
            }
            // ── Loops (FOR, REPEAT, READ, FIND, HISTOGRAM) ──
            else if (t == JclElementType.NAT_FOR || t == JclElementType.NAT_REPEAT) {
                String loopLabel = e.getName() != null ? e.getName() : t.getDisplayName();
                String loopId = safeId("LOOP_" + loopLabel, usedIds);
                sb.append("    ").append(loopId).append("{{\"").append(esc(loopLabel)).append("\"}}\n");
                sb.append("    ").append(prevId).append(" --> ").append(loopId).append("\n");
                // Self-loop arrow to indicate repetition
                sb.append("    ").append(loopId).append(" -.-> ").append(loopId).append("\n");
                prevId = loopId;
            }
            // ── DB loops (READ/FIND/HISTOGRAM — highlighted as data access loops) ──
            else if (t == JclElementType.NAT_READ || t == JclElementType.NAT_FIND
                    || t == JclElementType.NAT_HISTOGRAM) {
                String file = e.getParameter("FILE");
                String loopLabel = t.getDisplayName() + (file != null ? " " + file : "");
                String loopId = safeId("DBLOOP_" + loopLabel, usedIds);
                sb.append("    ").append(loopId).append("[(\"").append(esc(loopLabel)).append("\")]\n");
                sb.append("    ").append(prevId).append(" --> ").append(loopId).append("\n");
                sb.append("    style ").append(loopId).append(" fill:#e3f2fd,stroke:#1565c0,stroke-width:2px\n");
                prevId = loopId;
            }
            // ── External calls (CALLNAT, CALL, FETCH — prominently highlighted) ──
            else if (t == JclElementType.NAT_CALLNAT || t == JclElementType.NAT_CALL
                    || t == JclElementType.NAT_FETCH) {
                String target = e.getParameter("TARGET");
                if (target == null) target = e.getName();
                if (target == null) continue;
                String extId = safeId("EXT_" + target, usedIds);
                sb.append("    ").append(extId).append(">\"").append(esc(target))
                        .append("\\n(").append(esc(t.getDisplayName())).append(")\"]\n");
                sb.append("    ").append(prevId).append(" ==>|").append(t.getDisplayName())
                        .append("| ").append(extId).append("\n");
                // System functions → IBM Blue; others → orange highlight
                if (isSysFunc(target, sysFuncs)) {
                    styleSysFunc(sb, extId);
                } else {
                    sb.append("    style ").append(extId).append(" fill:#ffe0b2,stroke:#e65100,stroke-width:2px\n");
                }
                // Don't change prevId — external call returns and flow continues
            }
            // ── Inline PERFORM (internal subroutine calls) ──
            else if (t == JclElementType.NAT_PERFORM) {
                String target = e.getParameter("TARGET");
                if (target == null) target = e.getName();
                if (target == null) continue;
                String perfTarget = findIdForName("SUB_" + target, usedIds);
                if (perfTarget == null) {
                    perfTarget = safeId("SUB_" + target, usedIds);
                    sb.append("    ").append(perfTarget).append("{{\"").append(esc(target)).append("\"}}\n");
                }
                sb.append("    ").append(prevId).append(" -.->|PERFORM| ").append(perfTarget).append("\n");
            }
            // ── Inline subroutine definitions ──
            else if (t == JclElementType.NAT_INLINE_SUBROUTINE || t == JclElementType.NAT_SUBROUTINE) {
                String subId = safeId("SUB_" + e.getName(), usedIds);
                sb.append("    ").append(subId).append("{{\"").append(esc(e.getName())).append("\"}}\n");
                sb.append("    ").append(prevId).append(" --> ").append(subId).append("\n");
                prevId = subId;
            }
            // ── Error handling ──
            else if (t == JclElementType.NAT_ON_ERROR) {
                String errId = safeId("ONERR", usedIds);
                sb.append("    ").append(errId).append("{\"ON ERROR\"}\n");
                sb.append("    ").append(prevId).append(" --> ").append(errId).append("\n");
                sb.append("    style ").append(errId).append(" fill:#ffcdd2,stroke:#c62828,stroke-width:2px\n");
                prevId = errId;
            }
        }

        // End node
        String endId = safeId("END", usedIds);
        sb.append("    ").append(endId).append("([\"END\"])\n");
        sb.append("    ").append(prevId).append(" --> ").append(endId).append("\n");

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════
    //  SEQUENCE diagram
    // ═══════════════════════════════════════════════════════════

    private static String convertSequence(JclOutlineModel model, Set<String> sysFuncs) {
        switch (model.getLanguage()) {
            case JCL:     return convertJclSequence(model, sysFuncs);
            case COBOL:   return convertCobolSequence(model, sysFuncs);
            case NATURAL: return convertNaturalSequence(model, sysFuncs);
            default:      return convertJclSequence(model, sysFuncs);
        }
    }

    private static String convertJclSequence(JclOutlineModel model, Set<String> sysFuncs) {
        StringBuilder sb = new StringBuilder("sequenceDiagram\n");
        List<JclElement> jobs = model.getJobs();
        List<JclElement> steps = model.getSteps();

        // Participant: JOB
        String jobName = "JOB";
        if (!jobs.isEmpty()) {
            jobName = jobs.get(0).getName() != null ? jobs.get(0).getName() : "JOB";
            sb.append("    participant ").append(safeParticipant(jobName)).append("\n");
        }

        // Participant for each EXEC step
        Set<String> declared = new LinkedHashSet<String>();
        for (JclElement step : steps) {
            String name = step.getName() != null ? step.getName() : "STEP";
            String safe = safeParticipant(name);
            if (declared.add(safe)) {
                sb.append("    participant ").append(safe);
                String pgm = step.getParameter("PGM");
                if (pgm != null) {
                    sb.append(" as ").append(safe).append("[PGM=").append(pgm).append("]");
                }
                sb.append("\n");
            }
        }

        // Arrows: JOB → each step in order
        String prev = safeParticipant(jobName);
        for (JclElement step : steps) {
            String name = step.getName() != null ? step.getName() : "STEP";
            String safe = safeParticipant(name);
            String pgm = step.getParameter("PGM");
            String proc = step.getParameter("PROC");
            String detail = pgm != null ? "EXEC PGM=" + pgm : (proc != null ? "EXEC PROC=" + proc : "EXEC");
            sb.append("    ").append(prev).append("->>").append(safe).append(": ").append(detail).append("\n");

            // System function note
            if (isSysFunc(pgm, sysFuncs)) {
                sb.append("    Note right of ").append(safe).append(": \uD83D\uDCD6 Systemfunktion\n");
            }

            // DD statements as notes
            List<String> dds = new ArrayList<String>();
            for (JclElement child : step.getChildren()) {
                if (child.getType() == JclElementType.DD && child.getName() != null) {
                    dds.add(child.getName());
                }
            }
            if (!dds.isEmpty()) {
                sb.append("    Note right of ").append(safe).append(": DD: ")
                        .append(truncate(join(dds, ", "), 30)).append("\n");
            }
            prev = safe;
        }

        return sb.toString();
    }

    private static String convertCobolSequence(JclOutlineModel model, Set<String> sysFuncs) {
        StringBuilder sb = new StringBuilder("sequenceDiagram\n");
        List<JclElement> all = model.getElements();

        // Find program ID
        String progName = "PROGRAM";
        for (JclElement e : all) {
            if (e.getType() == JclElementType.PROGRAM_ID && e.getName() != null) {
                progName = e.getName();
                break;
            }
        }
        sb.append("    participant ").append(safeParticipant(progName)).append("\n");

        // Declare paragraphs as participants
        List<JclElement> paras = model.getParagraphs();
        Set<String> declared = new LinkedHashSet<String>();
        for (JclElement p : paras) {
            String safe = safeParticipant(p.getName());
            if (declared.add(safe)) {
                sb.append("    participant ").append(safe).append("\n");
            }
        }

        // Main flow: program → first paragraph → next → ...
        String prev = safeParticipant(progName);
        for (JclElement p : paras) {
            String safe = safeParticipant(p.getName());
            sb.append("    ").append(prev).append("->>").append(safe).append(": ").append("Abschnitt").append("\n");
            prev = safe;
        }

        // PERFORM / CALL as dashed arrows
        for (JclElement e : all) {
            String target = e.getParameter("TARGET");
            if (target == null) continue;
            String sourceParent = findParentParagraph(e, all);
            String from = sourceParent != null ? safeParticipant(sourceParent) : safeParticipant(progName);

            if (e.getType() == JclElementType.PERFORM_STMT) {
                String to = safeParticipant(target);
                sb.append("    ").append(from).append("-->>").append(to).append(": PERFORM\n");
            } else if (e.getType() == JclElementType.CALL_STMT) {
                String to = safeParticipant(target);
                if (declared.add(to)) {
                    sb.append("    participant ").append(to).append("\n");
                }
                sb.append("    ").append(from).append("->>").append(to).append(": CALL\n");
            }
        }

        return sb.toString();
    }

    private static String convertNaturalSequence(JclOutlineModel model, Set<String> sysFuncs) {
        StringBuilder sb = new StringBuilder("sequenceDiagram\n");
        List<JclElement> all = model.getElements();

        // Program name
        String progName = model.getSourceName() != null ? model.getSourceName() : "PROGRAM";
        for (JclElement e : all) {
            if (e.getType() == JclElementType.NAT_PROGRAM
                    || e.getType() == JclElementType.NAT_SUBPROGRAM
                    || e.getType() == JclElementType.NAT_FUNCTION) {
                if (e.getName() != null) progName = e.getName();
                break;
            }
        }
        sb.append("    participant ").append(safeParticipant(progName)).append("\n");

        Set<String> declared = new LinkedHashSet<String>();

        // Subroutines as participants
        for (JclElement sub : model.getSubroutines()) {
            String safe = safeParticipant(sub.getName());
            if (declared.add(safe)) {
                sb.append("    participant ").append(safe).append("\n");
            }
        }

        // External calls
        for (JclElement call : model.getNaturalCalls()) {
            String target = call.getParameter("TARGET");
            if (target == null) target = call.getName();
            if (target == null) continue;
            String safe = safeParticipant(target);
            if (declared.add(safe)) {
                sb.append("    participant ").append(safe).append("\n");
            }
            String label = call.getType().getDisplayName();
            sb.append("    ").append(safeParticipant(progName))
                    .append("->>").append(safe).append(": ").append(label).append("\n");
        }

        // DB operations
        for (JclElement db : model.getNaturalDbOps()) {
            String file = db.getParameter("FILE");
            if (file == null) file = db.getName();
            if (file == null) continue;
            String safe = safeParticipant("DB_" + file);
            if (declared.add(safe)) {
                sb.append("    participant ").append(safe).append(" as ").append(file).append("\n");
            }
            String op = db.getType().getDisplayName();
            sb.append("    ").append(safeParticipant(progName))
                    .append("->>").append(safe).append(": ").append(op).append("\n");
        }

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════
    //  MINDMAP
    // ═══════════════════════════════════════════════════════════

    private static String convertMindmap(JclOutlineModel model, CallTreeNode callTree, Set<String> sysFuncs) {
        switch (model.getLanguage()) {
            case JCL:     return convertJclMindmap(model, callTree, sysFuncs);
            case COBOL:   return convertCobolMindmap(model, callTree, sysFuncs);
            case NATURAL: return convertNaturalMindmap(model, callTree, sysFuncs);
            default:      return convertJclMindmap(model, callTree, sysFuncs);
        }
    }

    private static String convertJclMindmap(JclOutlineModel model, CallTreeNode callTree, Set<String> sysFuncs) {
        StringBuilder sb = new StringBuilder("mindmap\n");
        List<JclElement> jobs = model.getJobs();

        // Root node = file / job name
        String rootName = "JCL";
        if (!jobs.isEmpty() && jobs.get(0).getName() != null) {
            rootName = jobs.get(0).getName();
        }
        sb.append("  root((").append(escMm(rootName)).append("))\n");

        if (callTree != null && !callTree.getChildren().isEmpty()) {
            // Each external call is a direct branch of root, recursively resolved
            appendCallTreeChildren(sb, callTree, 4, sysFuncs);
        } else {
            // Fallback: external targets from steps (PGM/PROC)
            Set<String> seen = new LinkedHashSet<String>();
            for (JclElement step : model.getSteps()) {
                String pgm = step.getParameter("PGM");
                String proc = step.getParameter("PROC");
                String target = pgm != null ? pgm : proc;
                if (target != null && seen.add(target.toUpperCase())) {
                    String lbl = isSysFunc(target, sysFuncs) ? "\uD83C\uDFE2 " + escMm(target) : escMm(target);
                    sb.append("    ").append(lbl).append("\n");
                }
            }
        }

        return sb.toString();
    }

    private static String convertCobolMindmap(JclOutlineModel model, CallTreeNode callTree, Set<String> sysFuncs) {
        StringBuilder sb = new StringBuilder("mindmap\n");
        List<JclElement> all = model.getElements();

        // Root = program ID
        String progName = "COBOL";
        for (JclElement e : all) {
            if (e.getType() == JclElementType.PROGRAM_ID && e.getName() != null) {
                progName = e.getName();
                break;
            }
        }
        sb.append("  root((").append(escMm(progName)).append("))\n");

        if (callTree != null && !callTree.getChildren().isEmpty()) {
            appendCallTreeChildren(sb, callTree, 4, sysFuncs);
        } else {
            // Fallback: flat external call targets
            Set<String> seen = new LinkedHashSet<String>();
            for (JclElement e : all) {
                if (e.getType() == JclElementType.CALL_STMT) {
                    String target = e.getParameter("TARGET");
                    if (target != null && seen.add(target.toUpperCase())) {
                        String lbl = isSysFunc(target, sysFuncs) ? "\uD83C\uDFE2 " + escMm(target) : escMm(target);
                        sb.append("    ").append(lbl).append("\n");
                    }
                }
            }
        }

        return sb.toString();
    }

    private static String convertNaturalMindmap(JclOutlineModel model, CallTreeNode callTree, Set<String> sysFuncs) {
        StringBuilder sb = new StringBuilder("mindmap\n");
        List<JclElement> all = model.getElements();

        // Root = program name
        String progName = model.getSourceName() != null ? model.getSourceName() : "Natural";
        for (JclElement e : all) {
            if (e.getType() == JclElementType.NAT_PROGRAM
                    || e.getType() == JclElementType.NAT_SUBPROGRAM
                    || e.getType() == JclElementType.NAT_FUNCTION) {
                if (e.getName() != null) progName = e.getName();
                break;
            }
        }
        sb.append("  root((").append(escMm(progName)).append("))\n");

        if (callTree != null && !callTree.getChildren().isEmpty()) {
            appendCallTreeChildren(sb, callTree, 4, sysFuncs);
        } else {
            // Fallback: flat external call targets
            Set<String> seen = new LinkedHashSet<String>();
            for (JclElement call : model.getNaturalCalls()) {
                String target = call.getParameter("TARGET");
                if (target == null) target = call.getName();
                if (target != null && seen.add(target.toUpperCase())) {
                    String lbl = isSysFunc(target, sysFuncs) ? "\uD83C\uDFE2 " + escMm(target) : escMm(target);
                    sb.append("    ").append(lbl).append("\n");
                }
            }
        }

        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════
    //  Call Tree → Mindmap helper
    // ══════════════════════════════════════════════════════════

    /**
     * Recursively append call tree children as mindmap indentation levels.
     * Each child represents an external call to another file; its children
     * are that file's own external calls — forming a pure call graph.
     * System functions are prefixed with 🏢.
     *
     * @param sb         output builder
     * @param parent     parent call tree node
     * @param baseIndent starting indentation (number of spaces)
     * @param sysFuncs   set of known system function names (uppercase)
     */
    private static void appendCallTreeChildren(StringBuilder sb, CallTreeNode parent,
                                                int baseIndent, Set<String> sysFuncs) {
        int maxIndent = 20;
        String indent = spaces(Math.min(baseIndent, maxIndent));

        for (CallTreeNode child : parent.getChildren()) {
            String name = child.getName();
            String label = escMm(name);
            // Prefix system functions with 🏢
            if (isSysFunc(name, sysFuncs)) {
                label = "\uD83C\uDFE2 " + label;
            }
            if (child.isRecursive()) {
                label = label + " \uD83D\uDD04"; // 🔄
            }
            sb.append(indent).append(label).append("\n");

            // Recurse: show external calls of that file as sub-branches
            if (!child.isRecursive() && !child.getChildren().isEmpty()) {
                appendCallTreeChildren(sb, child, baseIndent + 2, sysFuncs);
            }
        }
    }

    private static String spaces(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(' ');
        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════

    /** Make a Mermaid-safe ID (alphanumeric + underscore). */
    private static String safeId(String base, Set<String> usedIds) {
        String normalized = normalizeId(base);
        String id = normalized;
        int counter = 2;
        while (usedIds.contains(id)) {
            id = normalized + "_" + counter++;
        }
        usedIds.add(id);
        return id;
    }

    private static String normalizeId(String s) {
        if (s == null) return "X";
        return s.replaceAll("[^a-zA-Z0-9_]", "_").replaceAll("_+", "_");
    }

    /** Lookup an already-created ID by prefix match. */
    private static String findIdForName(String prefix, Set<String> usedIds) {
        String normalized = normalizeId(prefix);
        if (usedIds.contains(normalized)) return normalized;
        // Try numbered variants
        for (int i = 2; i < 100; i++) {
            String cand = normalized + "_" + i;
            if (usedIds.contains(cand)) return cand;
        }
        return null;
    }

    /** Find the enclosing paragraph name for a PERFORM/CALL element. */
    private static String findParentParagraph(JclElement target, List<JclElement> all) {
        int targetLine = target.getLineNumber();
        String best = null;
        int bestLine = -1;
        for (JclElement e : all) {
            if (e.getType() == JclElementType.PARAGRAPH
                    && e.getLineNumber() <= targetLine
                    && e.getLineNumber() > bestLine) {
                best = e.getName();
                bestLine = e.getLineNumber();
            }
        }
        return best;
    }

    /** Escape text for Mermaid labels. */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\"", "#quot;").replace("\n", "\\n");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }

    /** Make a sequence-diagram-safe participant name (no spaces, no special chars). */
    private static String safeParticipant(String name) {
        if (name == null) return "X";
        return name.replaceAll("[^a-zA-Z0-9_]", "_").replaceAll("_+", "_");
    }

    /** Escape text for Mermaid mindmap nodes (no parentheses or special chars). */
    private static String escMm(String s) {
        if (s == null) return "?";
        return s.replace("(", "").replace(")", "").replace("[", "").replace("]", "")
                .replace("{", "").replace("}", "").replace("\"", "").replace("\n", " ");
    }

    /** Join a list of strings with a separator (Java 8 compatible). */
    private static String join(List<String> items, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(items.get(i));
        }
        return sb.toString();
    }
}
