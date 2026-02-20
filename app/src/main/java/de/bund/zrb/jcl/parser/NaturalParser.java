package de.bund.zrb.jcl.parser;

import de.bund.zrb.jcl.model.JclElement;
import de.bund.zrb.jcl.model.JclElementType;
import de.bund.zrb.jcl.model.JclOutlineModel;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Software AG Natural source code that extracts structural elements for outline view.
 *
 * Natural source is free-format (no column restrictions like COBOL).
 * Comments start with {@code /*} or {@code **} at any position.
 * Statements end with implicit line-end or explicit statement separator.
 *
 * This parser extracts:
 *   - DEFINE DATA (LOCAL / PARAMETER / GLOBAL / INDEPENDENT blocks)
 *   - Variable definitions (level 1-99)
 *   - VIEW definitions
 *   - REDEFINE blocks
 *   - DEFINE SUBROUTINE (inline subroutines)
 *   - PERFORM (calls to inline subroutines)
 *   - CALLNAT / CALL / FETCH (external calls)
 *   - READ / FIND / HISTOGRAM / STORE / UPDATE / DELETE / GET (Adabas DB access)
 *   - DECIDE ON / DECIDE FOR
 *   - IF blocks (top-level only)
 *   - FOR / REPEAT loops
 *   - INPUT / WRITE / DISPLAY / PRINT (I/O)
 *   - INCLUDE (copycode)
 *   - ON ERROR
 *   - END (program end marker)
 */
public class NaturalParser {

    // ── Patterns ────────────────────────────────────────────────────

    // DEFINE DATA
    private static final Pattern DEFINE_DATA = Pattern.compile(
            "^\\s*DEFINE\\s+DATA\\b", Pattern.CASE_INSENSITIVE);

    // Data area scope: LOCAL, PARAMETER, GLOBAL, INDEPENDENT
    private static final Pattern DATA_SCOPE = Pattern.compile(
            "^\\s*(LOCAL|PARAMETER|GLOBAL|INDEPENDENT)\\b(?:\\s+USING\\s+(\\S+))?",
            Pattern.CASE_INSENSITIVE);

    // END-DEFINE
    private static final Pattern END_DEFINE = Pattern.compile(
            "^\\s*END-DEFINE\\b", Pattern.CASE_INSENSITIVE);

    // Variable definition: level name (format) [INIT<...>]
    // e.g.: 01 #COUNTER (N5)    or    02 #NAME  (A30) INIT<''>
    private static final Pattern DATA_VAR = Pattern.compile(
            "^\\s*(\\d{1,2})\\s+([#@$]?[A-Za-z][A-Za-z0-9_.#@$-]*)(?:\\s*\\(([^)]+)\\))?" +
                    "(?:.*INIT\\s*<([^>]*)>)?",
            Pattern.CASE_INSENSITIVE);

    // VIEW definition: 01 viewname VIEW OF ddmname
    private static final Pattern DATA_VIEW = Pattern.compile(
            "^\\s*(\\d{1,2})\\s+([#@$]?[A-Za-z][A-Za-z0-9_.#@$-]*)\\s+VIEW\\s+OF\\s+(\\S+)",
            Pattern.CASE_INSENSITIVE);

    // REDEFINE: REDEFINE #variable
    private static final Pattern REDEFINE = Pattern.compile(
            "^\\s*REDEFINE\\s+([#@$]?[A-Za-z][A-Za-z0-9_.#@$-]*)",
            Pattern.CASE_INSENSITIVE);

    // CONST: e.g. 01 #C1 (A5) CONST<'ABC'>
    private static final Pattern DATA_CONST = Pattern.compile(
            "CONST\\s*<([^>]*)>", Pattern.CASE_INSENSITIVE);

    // DEFINE SUBROUTINE name
    private static final Pattern DEFINE_SUBROUTINE = Pattern.compile(
            "^\\s*DEFINE\\s+SUBROUTINE\\s+([A-Za-z][A-Za-z0-9_.#@$-]*)",
            Pattern.CASE_INSENSITIVE);

    // PERFORM name
    private static final Pattern PERFORM = Pattern.compile(
            "^\\s*PERFORM\\s+([A-Za-z][A-Za-z0-9_.#@$-]*)",
            Pattern.CASE_INSENSITIVE);

    // CALLNAT 'name' / CALLNAT name
    private static final Pattern CALLNAT = Pattern.compile(
            "^\\s*CALLNAT\\s+['\"]?([A-Za-z][A-Za-z0-9_.#@$-]*)['\"]?",
            Pattern.CASE_INSENSITIVE);

    // CALL 'name' / CALL name (3GL call)
    private static final Pattern CALL_3GL = Pattern.compile(
            "^\\s*CALL\\s+['\"]?([A-Za-z][A-Za-z0-9_.#@$-]*)['\"]?",
            Pattern.CASE_INSENSITIVE);

    // FETCH [RETURN] 'name'
    private static final Pattern FETCH = Pattern.compile(
            "^\\s*FETCH\\s+(?:RETURN\\s+)?['\"]?([A-Za-z][A-Za-z0-9_.#@$-]*)['\"]?",
            Pattern.CASE_INSENSITIVE);

    // READ [WORK] FILE name / READ name [WITH ...]
    private static final Pattern READ = Pattern.compile(
            "^\\s*READ\\s+(?:WORK\\s+FILE\\s+)?(\\S+)",
            Pattern.CASE_INSENSITIVE);

    // FIND [(number)] name WITH ...
    private static final Pattern FIND = Pattern.compile(
            "^\\s*FIND\\s+(?:\\(\\d+\\)\\s+)?([A-Za-z][A-Za-z0-9_.#@$-]*)(?:\\s+WITH\\s+(.+))?",
            Pattern.CASE_INSENSITIVE);

    // HISTOGRAM name WITH ...
    private static final Pattern HISTOGRAM = Pattern.compile(
            "^\\s*HISTOGRAM\\s+(?:\\(\\d+\\)\\s+)?([A-Za-z][A-Za-z0-9_.#@$-]*)(?:\\s+WITH\\s+(.+))?",
            Pattern.CASE_INSENSITIVE);

    // STORE name
    private static final Pattern STORE = Pattern.compile(
            "^\\s*STORE\\s+([A-Za-z][A-Za-z0-9_.#@$-]*)",
            Pattern.CASE_INSENSITIVE);

    // UPDATE [SAME RECORD IN] name
    private static final Pattern UPDATE = Pattern.compile(
            "^\\s*UPDATE\\s+(?:SAME\\s+RECORD\\s+IN\\s+)?([A-Za-z][A-Za-z0-9_.#@$-]*)?",
            Pattern.CASE_INSENSITIVE);

    // DELETE [RECORD IN] name
    private static final Pattern DELETE = Pattern.compile(
            "^\\s*DELETE\\s+(?:RECORD\\s+IN\\s+)?([A-Za-z][A-Za-z0-9_.#@$-]*)?",
            Pattern.CASE_INSENSITIVE);

    // GET name [record]
    private static final Pattern GET = Pattern.compile(
            "^\\s*GET\\s+([A-Za-z][A-Za-z0-9_.#@$-]*)",
            Pattern.CASE_INSENSITIVE);

    // DECIDE ON / DECIDE FOR
    private static final Pattern DECIDE = Pattern.compile(
            "^\\s*DECIDE\\s+(ON|FOR)\\s+(?:FIRST\\s+(?:VALUE\\s+)?(?:OF\\s+)?)?(\\S+)?",
            Pattern.CASE_INSENSITIVE);

    // IF condition
    private static final Pattern IF_BLOCK = Pattern.compile(
            "^\\s*IF\\s+(.+)", Pattern.CASE_INSENSITIVE);

    // FOR #var = ... TO ...
    private static final Pattern FOR = Pattern.compile(
            "^\\s*FOR\\s+([#@$]?[A-Za-z][A-Za-z0-9_.#@$-]*)\\s*=?",
            Pattern.CASE_INSENSITIVE);

    // REPEAT
    private static final Pattern REPEAT = Pattern.compile(
            "^\\s*REPEAT\\b", Pattern.CASE_INSENSITIVE);

    // INPUT (USING MAP 'name' | inline)
    private static final Pattern INPUT = Pattern.compile(
            "^\\s*INPUT\\s+(?:USING\\s+MAP\\s+['\"]?([A-Za-z][A-Za-z0-9_.#@$-]*)['\"]?)?",
            Pattern.CASE_INSENSITIVE);

    // WRITE / DISPLAY / PRINT
    private static final Pattern WRITE = Pattern.compile(
            "^\\s*(WRITE|DISPLAY|PRINT)\\b", Pattern.CASE_INSENSITIVE);

    // INCLUDE copycode
    private static final Pattern INCLUDE_CC = Pattern.compile(
            "^\\s*INCLUDE\\s+([A-Za-z][A-Za-z0-9_.#@$-]*)",
            Pattern.CASE_INSENSITIVE);

    // ON ERROR
    private static final Pattern ON_ERROR = Pattern.compile(
            "^\\s*ON\\s+ERROR\\b", Pattern.CASE_INSENSITIVE);

    // END
    private static final Pattern END = Pattern.compile(
            "^\\s*END\\s*$", Pattern.CASE_INSENSITIVE);

    // Comment: line starting with * or /* (after optional leading spaces)
    private static final Pattern COMMENT = Pattern.compile(
            "^\\s*(?:\\*\\*|/\\*)");

    // ── Parse ───────────────────────────────────────────────────────

    /**
     * Parse Natural content and return outline model.
     */
    public JclOutlineModel parse(String content, String sourceName) {
        JclOutlineModel model = new JclOutlineModel();
        model.setSourceName(sourceName);
        model.setLanguage(JclOutlineModel.Language.NATURAL);

        if (content == null || content.isEmpty()) {
            return model;
        }

        String[] lines = content.split("\\r?\\n");
        model.setTotalLines(lines.length);

        JclElement defineDataBlock = null;
        JclElement currentScope = null;
        JclElement currentLevel01 = null;
        boolean inDefineData = false;
        int defineDataDepth = 0; // for nested DEFINE DATA (unlikely but safe)

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;
            String trimmed = line.trim();

            // Skip blank lines
            if (trimmed.isEmpty()) continue;

            // Skip comment lines
            if (COMMENT.matcher(trimmed).find()) continue;

            // Strip inline comments: everything after /*
            int commentIdx = trimmed.indexOf("/*");
            if (commentIdx > 0) {
                trimmed = trimmed.substring(0, commentIdx).trim();
                if (trimmed.isEmpty()) continue;
            }

            Matcher m;

            // ── END-DEFINE ──────────────────────────────────────────
            m = END_DEFINE.matcher(trimmed);
            if (m.find()) {
                if (inDefineData) {
                    inDefineData = false;
                    defineDataBlock = null;
                    currentScope = null;
                    currentLevel01 = null;
                }
                continue;
            }

            // ── DEFINE DATA ─────────────────────────────────────────
            m = DEFINE_DATA.matcher(trimmed);
            if (m.find()) {
                JclElement dd = new JclElement(JclElementType.NAT_DEFINE_DATA,
                        "DEFINE DATA", lineNum, trimmed);
                model.addElement(dd);
                defineDataBlock = dd;
                inDefineData = true;
                continue;
            }

            // Inside DEFINE DATA block
            if (inDefineData) {
                // ── Data scope: LOCAL / PARAMETER / GLOBAL / INDEPENDENT ─
                m = DATA_SCOPE.matcher(trimmed);
                if (m.find()) {
                    String scopeName = m.group(1).toUpperCase();
                    JclElementType scopeType;
                    switch (scopeName) {
                        case "LOCAL":        scopeType = JclElementType.NAT_LOCAL; break;
                        case "PARAMETER":    scopeType = JclElementType.NAT_PARAMETER; break;
                        case "GLOBAL":       scopeType = JclElementType.NAT_GLOBAL; break;
                        case "INDEPENDENT":  scopeType = JclElementType.NAT_INDEPENDENT; break;
                        default:             scopeType = JclElementType.NAT_LOCAL; break;
                    }
                    String using = m.group(2);
                    JclElement scope = new JclElement(scopeType, scopeName, lineNum, trimmed);
                    if (using != null) {
                        scope.addParameter("USING", using);
                    }
                    model.addElement(scope);
                    if (defineDataBlock != null) {
                        defineDataBlock.addChild(scope);
                    }
                    currentScope = scope;
                    currentLevel01 = null;
                    continue;
                }

                // ── VIEW ────────────────────────────────────────────
                m = DATA_VIEW.matcher(trimmed);
                if (m.find()) {
                    JclElement view = new JclElement(JclElementType.NAT_DATA_VIEW,
                            m.group(2), lineNum, trimmed);
                    view.addParameter("LEVEL", m.group(1));
                    view.addParameter("OF", m.group(3));
                    model.addElement(view);
                    if (currentScope != null) currentScope.addChild(view);
                    currentLevel01 = view;
                    continue;
                }

                // ── REDEFINE ────────────────────────────────────────
                m = REDEFINE.matcher(trimmed);
                if (m.find()) {
                    JclElement redef = new JclElement(JclElementType.NAT_DATA_REDEFINE,
                            "REDEFINE " + m.group(1), lineNum, trimmed);
                    redef.addParameter("REDEFINES", m.group(1));
                    model.addElement(redef);
                    if (currentScope != null) currentScope.addChild(redef);
                    continue;
                }

                // ── Variable definition ─────────────────────────────
                m = DATA_VAR.matcher(trimmed);
                if (m.find()) {
                    int level = Integer.parseInt(m.group(1));
                    String varName = m.group(2);
                    String format = m.group(3);
                    String init = m.group(4);

                    // Check for CONST
                    Matcher mc = DATA_CONST.matcher(trimmed);
                    boolean isConst = mc.find();

                    JclElementType varType = isConst
                            ? JclElementType.NAT_DATA_CONST
                            : JclElementType.NAT_DATA_VAR;

                    JclElement var = new JclElement(varType, varName, lineNum, trimmed);
                    var.addParameter("LEVEL", String.valueOf(level));
                    if (format != null) var.addParameter("FORMAT", format.trim());
                    if (init != null) var.addParameter("INIT", init.trim());
                    if (isConst) var.addParameter("CONST", mc.group(1));

                    // Hierarchy: show level 1 as top-level, others nested
                    if (level == 1) {
                        model.addElement(var);
                        if (currentScope != null) currentScope.addChild(var);
                        currentLevel01 = var;
                    } else if (level == 2 && currentLevel01 != null) {
                        model.addElement(var);
                        currentLevel01.addChild(var);
                    }
                    // Deeper levels omitted for clean outline
                    continue;
                }

                // Still in DEFINE DATA but not matched — skip
                continue;
            }

            // ── Outside DEFINE DATA = executable code ───────────────

            // ── DEFINE SUBROUTINE ───────────────────────────────────
            m = DEFINE_SUBROUTINE.matcher(trimmed);
            if (m.find()) {
                JclElement sub = new JclElement(JclElementType.NAT_INLINE_SUBROUTINE,
                        m.group(1), lineNum, trimmed);
                model.addElement(sub);
                continue;
            }

            // ── PERFORM ─────────────────────────────────────────────
            m = PERFORM.matcher(trimmed);
            if (m.find()) {
                JclElement perf = new JclElement(JclElementType.NAT_PERFORM,
                        m.group(1), lineNum, trimmed);
                perf.addParameter("TARGET", m.group(1));
                model.addElement(perf);
                continue;
            }

            // ── CALLNAT ─────────────────────────────────────────────
            m = CALLNAT.matcher(trimmed);
            if (m.find()) {
                JclElement cn = new JclElement(JclElementType.NAT_CALLNAT,
                        m.group(1), lineNum, trimmed);
                cn.addParameter("TARGET", m.group(1));
                model.addElement(cn);
                continue;
            }

            // ── FETCH ───────────────────────────────────────────────
            m = FETCH.matcher(trimmed);
            if (m.find()) {
                JclElement fe = new JclElement(JclElementType.NAT_FETCH,
                        m.group(1), lineNum, trimmed);
                fe.addParameter("TARGET", m.group(1));
                model.addElement(fe);
                continue;
            }

            // ── CALL (3GL) ──────────────────────────────────────────
            m = CALL_3GL.matcher(trimmed);
            if (m.find()) {
                JclElement c3 = new JclElement(JclElementType.NAT_CALL,
                        m.group(1), lineNum, trimmed);
                c3.addParameter("TARGET", m.group(1));
                model.addElement(c3);
                continue;
            }

            // ── READ ────────────────────────────────────────────────
            m = READ.matcher(trimmed);
            if (m.find()) {
                JclElement rd = new JclElement(JclElementType.NAT_READ,
                        "", lineNum, trimmed);
                rd.addParameter("FILE", m.group(1));
                model.addElement(rd);
                continue;
            }

            // ── FIND ────────────────────────────────────────────────
            m = FIND.matcher(trimmed);
            if (m.find()) {
                JclElement fi = new JclElement(JclElementType.NAT_FIND,
                        "", lineNum, trimmed);
                fi.addParameter("FILE", m.group(1));
                if (m.group(2) != null) fi.addParameter("WITH", m.group(2).trim());
                model.addElement(fi);
                continue;
            }

            // ── HISTOGRAM ───────────────────────────────────────────
            m = HISTOGRAM.matcher(trimmed);
            if (m.find()) {
                JclElement hi = new JclElement(JclElementType.NAT_HISTOGRAM,
                        "", lineNum, trimmed);
                hi.addParameter("FILE", m.group(1));
                if (m.group(2) != null) hi.addParameter("WITH", m.group(2).trim());
                model.addElement(hi);
                continue;
            }

            // ── STORE ───────────────────────────────────────────────
            m = STORE.matcher(trimmed);
            if (m.find()) {
                JclElement st = new JclElement(JclElementType.NAT_STORE,
                        "", lineNum, trimmed);
                st.addParameter("FILE", m.group(1));
                model.addElement(st);
                continue;
            }

            // ── UPDATE ──────────────────────────────────────────────
            m = UPDATE.matcher(trimmed);
            if (m.find()) {
                JclElement up = new JclElement(JclElementType.NAT_UPDATE,
                        "", lineNum, trimmed);
                if (m.group(1) != null) up.addParameter("FILE", m.group(1));
                model.addElement(up);
                continue;
            }

            // ── DELETE ──────────────────────────────────────────────
            m = DELETE.matcher(trimmed);
            if (m.find()) {
                JclElement del = new JclElement(JclElementType.NAT_DELETE,
                        "", lineNum, trimmed);
                if (m.group(1) != null) del.addParameter("FILE", m.group(1));
                model.addElement(del);
                continue;
            }

            // ── GET ─────────────────────────────────────────────────
            m = GET.matcher(trimmed);
            if (m.find()) {
                JclElement gt = new JclElement(JclElementType.NAT_GET,
                        "", lineNum, trimmed);
                gt.addParameter("FILE", m.group(1));
                model.addElement(gt);
                continue;
            }

            // ── DECIDE ──────────────────────────────────────────────
            m = DECIDE.matcher(trimmed);
            if (m.find()) {
                JclElement dec = new JclElement(JclElementType.NAT_DECIDE,
                        "DECIDE " + m.group(1).toUpperCase(), lineNum, trimmed);
                dec.addParameter("ON", m.group(1).toUpperCase());
                if (m.group(2) != null) dec.addParameter("EXPR", m.group(2));
                model.addElement(dec);
                continue;
            }

            // ── ON ERROR ────────────────────────────────────────────
            m = ON_ERROR.matcher(trimmed);
            if (m.find()) {
                model.addElement(new JclElement(JclElementType.NAT_ON_ERROR,
                        "ON ERROR", lineNum, trimmed));
                continue;
            }

            // ── INCLUDE ─────────────────────────────────────────────
            m = INCLUDE_CC.matcher(trimmed);
            if (m.find()) {
                JclElement inc = new JclElement(JclElementType.NAT_INCLUDE,
                        m.group(1), lineNum, trimmed);
                inc.addParameter("COPYCODE", m.group(1));
                model.addElement(inc);
                continue;
            }

            // ── INPUT (with MAP) ────────────────────────────────────
            m = INPUT.matcher(trimmed);
            if (m.find()) {
                JclElement inp = new JclElement(JclElementType.NAT_INPUT,
                        "", lineNum, trimmed);
                if (m.group(1) != null) {
                    inp.addParameter("MAP", m.group(1));
                }
                model.addElement(inp);
                continue;
            }

            // ── WRITE / DISPLAY / PRINT (only top-level markers) ────
            // Omitted from outline to avoid noise – these are very frequent.
            // Uncomment below if desired:
            // m = WRITE.matcher(trimmed);
            // if (m.find()) { ... }

            // ── FOR ─────────────────────────────────────────────────
            m = FOR.matcher(trimmed);
            if (m.find()) {
                JclElement forEl = new JclElement(JclElementType.NAT_FOR,
                        m.group(1), lineNum, trimmed);
                model.addElement(forEl);
                continue;
            }

            // ── REPEAT ──────────────────────────────────────────────
            m = REPEAT.matcher(trimmed);
            if (m.find()) {
                model.addElement(new JclElement(JclElementType.NAT_REPEAT,
                        "", lineNum, trimmed));
                continue;
            }

            // ── END (program end) ───────────────────────────────────
            m = END.matcher(trimmed);
            if (m.find()) {
                model.addElement(new JclElement(JclElementType.NAT_END,
                        "END", lineNum, trimmed));
                // Don't continue — END is terminal
            }
        }

        return model;
    }
}

