package de.bund.zrb.ui.mermaid;

import de.bund.zrb.jcl.model.JclElement;
import de.bund.zrb.jcl.model.JclElementType;
import de.bund.zrb.jcl.model.JclOutlineModel;

import java.util.*;

/**
 * Converts a parsed {@link JclOutlineModel} (JCL / COBOL / Natural) into a
 * Mermaid flowchart diagram.
 * <p>
 * The generated diagram shows the structural flow:
 * <ul>
 *   <li><b>JCL</b>: JOB → EXEC steps (with PGM/PROC) → DD datasets</li>
 *   <li><b>COBOL</b>: Divisions → Sections → Paragraphs, plus PERFORM/CALL edges</li>
 *   <li><b>Natural</b>: Program structure with subroutines, CALLNAT/FETCH edges, DB ops</li>
 * </ul>
 */
public final class OutlineToMermaidConverter {

    private OutlineToMermaidConverter() {}

    /**
     * Convert an outline model to Mermaid flowchart code.
     *
     * @param model the parsed outline
     * @return Mermaid source code, or {@code null} if the model is empty
     */
    public static String convert(JclOutlineModel model) {
        if (model == null || model.isEmpty()) return null;

        switch (model.getLanguage()) {
            case JCL:     return convertJcl(model);
            case COBOL:   return convertCobol(model);
            case NATURAL: return convertNatural(model);
            default:      return convertJcl(model); // fallback
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
}

