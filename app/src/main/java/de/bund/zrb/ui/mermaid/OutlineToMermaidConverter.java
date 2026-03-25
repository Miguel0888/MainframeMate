package de.bund.zrb.ui.mermaid;

import de.bund.zrb.jcl.model.JclElement;
import de.bund.zrb.jcl.model.JclElementType;
import de.bund.zrb.jcl.model.JclOutlineModel;

import java.util.*;

/**
 * Converts a parsed {@link JclOutlineModel} (JCL / COBOL / Natural) into
 * Mermaid diagram code in various diagram types.
 * <p>
 * Supported diagram types:
 * <ul>
 *   <li><b>STRUCTURE</b> — flowchart showing structural hierarchy</li>
 *   <li><b>SEQUENCE</b> — sequence diagram showing execution/call flow</li>
 *   <li><b>MINDMAP</b> — mind-map for quick overview of code structure</li>
 * </ul>
 */
public final class OutlineToMermaidConverter {

    private OutlineToMermaidConverter() {}

    /**
     * Diagram types that can be generated from an outline model.
     */
    public enum DiagramType {
        /** Flowchart showing structural hierarchy (JOB→STEP→DD, Divisions, Subroutines). */
        STRUCTURE("\uD83C\uDFD7", "Struktur"),   // 🏗
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
        if (model == null || model.isEmpty()) return null;
        if (type == null) type = DiagramType.STRUCTURE;

        switch (type) {
            case SEQUENCE: return convertSequence(model);
            case MINDMAP:  return convertMindmap(model);
            case STRUCTURE:
            default:       return convertStructure(model);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  STRUCTURE (existing flowchart)
    // ═══════════════════════════════════════════════════════════

    private static String convertStructure(JclOutlineModel model) {
        switch (model.getLanguage()) {
            case JCL:     return convertJcl(model);
            case COBOL:   return convertCobol(model);
            case NATURAL: return convertNatural(model);
            default:      return convertJcl(model);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  JCL
    // ══════════════════════════════════════════════════════════

    private static String convertJcl(JclOutlineModel model) {
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

    private static String convertCobol(JclOutlineModel model) {
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

    private static String convertNatural(JclOutlineModel model) {
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
    //  SEQUENCE diagram
    // ═══════════════════════════════════════════════════════════

    private static String convertSequence(JclOutlineModel model) {
        switch (model.getLanguage()) {
            case JCL:     return convertJclSequence(model);
            case COBOL:   return convertCobolSequence(model);
            case NATURAL: return convertNaturalSequence(model);
            default:      return convertJclSequence(model);
        }
    }

    private static String convertJclSequence(JclOutlineModel model) {
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

    private static String convertCobolSequence(JclOutlineModel model) {
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

    private static String convertNaturalSequence(JclOutlineModel model) {
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

    private static String convertMindmap(JclOutlineModel model) {
        switch (model.getLanguage()) {
            case JCL:     return convertJclMindmap(model);
            case COBOL:   return convertCobolMindmap(model);
            case NATURAL: return convertNaturalMindmap(model);
            default:      return convertJclMindmap(model);
        }
    }

    private static String convertJclMindmap(JclOutlineModel model) {
        StringBuilder sb = new StringBuilder("mindmap\n");
        List<JclElement> jobs = model.getJobs();
        List<JclElement> steps = model.getSteps();

        // Root node
        String rootName = "JCL";
        if (!jobs.isEmpty() && jobs.get(0).getName() != null) {
            rootName = jobs.get(0).getName();
        }
        sb.append("  root((").append(escMm(rootName)).append("))\n");

        if (steps.isEmpty()) {
            // Flat list
            for (JclElement e : model.getElements()) {
                sb.append("    ").append(escMm(e.getName() != null ? e.getName() : e.getType().getDisplayName())).append("\n");
            }
            return sb.toString();
        }

        // Steps
        for (JclElement step : steps) {
            String label = step.getName() != null ? step.getName() : "(Step)";
            String pgm = step.getParameter("PGM");
            String proc = step.getParameter("PROC");
            sb.append("    ").append(escMm(label)).append("\n");
            if (pgm != null) {
                sb.append("      PGM=").append(escMm(pgm)).append("\n");
            } else if (proc != null) {
                sb.append("      PROC=").append(escMm(proc)).append("\n");
            }
            // DD children
            for (JclElement child : step.getChildren()) {
                if (child.getType() == JclElementType.DD && child.getName() != null) {
                    sb.append("      DD ").append(escMm(child.getName())).append("\n");
                }
            }
        }

        return sb.toString();
    }

    private static String convertCobolMindmap(JclOutlineModel model) {
        StringBuilder sb = new StringBuilder("mindmap\n");
        List<JclElement> all = model.getElements();

        // Root: program ID
        String progName = "COBOL";
        for (JclElement e : all) {
            if (e.getType() == JclElementType.PROGRAM_ID && e.getName() != null) {
                progName = e.getName();
                break;
            }
        }
        sb.append("  root((").append(escMm(progName)).append("))\n");

        // Divisions
        for (JclElement div : model.getDivisions()) {
            sb.append("    ").append(escMm(div.getName() != null ? div.getName() : "DIVISION")).append("\n");
        }

        // Sections
        List<JclElement> sections = model.getSections();
        if (!sections.isEmpty()) {
            sb.append("    Sektionen\n");
            for (JclElement sec : sections) {
                sb.append("      ").append(escMm(sec.getName() != null ? sec.getName() : "SECTION")).append("\n");
            }
        }

        // Paragraphs
        List<JclElement> paras = model.getParagraphs();
        if (!paras.isEmpty()) {
            sb.append("    Paragraphen\n");
            for (JclElement p : paras) {
                sb.append("      ").append(escMm(p.getName())).append("\n");
            }
        }

        // External calls
        boolean hasCall = false;
        for (JclElement e : all) {
            if (e.getType() == JclElementType.CALL_STMT) {
                if (!hasCall) {
                    sb.append("    Externe Aufrufe\n");
                    hasCall = true;
                }
                String target = e.getParameter("TARGET");
                if (target != null) {
                    sb.append("      CALL ").append(escMm(target)).append("\n");
                }
            }
        }

        return sb.toString();
    }

    private static String convertNaturalMindmap(JclOutlineModel model) {
        StringBuilder sb = new StringBuilder("mindmap\n");
        List<JclElement> all = model.getElements();

        // Root: program name
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

        // DEFINE DATA sub-blocks
        boolean hasData = false;
        for (JclElement e : all) {
            if (e.getType() == JclElementType.NAT_DEFINE_DATA) { hasData = true; break; }
        }
        if (hasData) {
            sb.append("    DEFINE DATA\n");
            for (JclElement e : all) {
                if (e.getType() == JclElementType.NAT_LOCAL
                        || e.getType() == JclElementType.NAT_PARAMETER
                        || e.getType() == JclElementType.NAT_GLOBAL
                        || e.getType() == JclElementType.NAT_INDEPENDENT) {
                    sb.append("      ").append(escMm(e.getType().getDisplayName())).append("\n");
                }
            }
        }

        // Subroutines
        List<JclElement> subs = model.getSubroutines();
        if (!subs.isEmpty()) {
            sb.append("    Subroutinen\n");
            for (JclElement sub : subs) {
                sb.append("      ").append(escMm(sub.getName())).append("\n");
            }
        }

        // External calls
        List<JclElement> calls = model.getNaturalCalls();
        if (!calls.isEmpty()) {
            sb.append("    Externe Aufrufe\n");
            for (JclElement call : calls) {
                String target = call.getParameter("TARGET");
                if (target == null) target = call.getName();
                if (target != null) {
                    sb.append("      ").append(escMm(call.getType().getDisplayName()))
                            .append(" ").append(escMm(target)).append("\n");
                }
            }
        }

        // DB operations
        List<JclElement> dbOps = model.getNaturalDbOps();
        if (!dbOps.isEmpty()) {
            sb.append("    DB-Zugriffe\n");
            for (JclElement db : dbOps) {
                String file = db.getParameter("FILE");
                if (file == null) file = db.getName();
                if (file != null) {
                    sb.append("      ").append(escMm(db.getType().getDisplayName()))
                            .append(" ").append(escMm(file)).append("\n");
                }
            }
        }

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
