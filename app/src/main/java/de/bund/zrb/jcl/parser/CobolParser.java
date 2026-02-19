package de.bund.zrb.jcl.parser;

import de.bund.zrb.jcl.model.JclElement;
import de.bund.zrb.jcl.model.JclElementType;
import de.bund.zrb.jcl.model.JclOutlineModel;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for COBOL source code that extracts structural elements for outline view.
 *
 * COBOL column layout (fixed format):
 *   Col  1-6:  sequence number (ignored)
 *   Col  7:    indicator (* = comment, - = continuation, D = debug)
 *   Col  8-11: Area A (divisions, sections, paragraphs, 01/77 levels)
 *   Col 12-72: Area B (statements, subordinate data items)
 *   Col 73-80: identification (ignored)
 *
 * This parser extracts:
 *   - IDENTIFICATION / ENVIRONMENT / DATA / PROCEDURE DIVISIONs
 *   - SECTIONs (WORKING-STORAGE, LINKAGE, FILE, etc.)
 *   - Paragraphs (names in Area A followed by period)
 *   - Data items (level numbers 01, 77, 88 and subordinate levels)
 *   - COPY statements
 *   - CALL / PERFORM statements
 *   - PROGRAM-ID
 */
public class CobolParser {

    // Division pattern: e.g. "IDENTIFICATION DIVISION."
    private static final Pattern DIVISION_PATTERN = Pattern.compile(
            "^\\s{0,6}.?\\s*(IDENTIFICATION|ENVIRONMENT|DATA|PROCEDURE)\\s+DIVISION[\\s.]",
            Pattern.CASE_INSENSITIVE);

    // Section pattern: e.g. "WORKING-STORAGE SECTION." or "INPUT-OUTPUT SECTION."
    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "^\\s{0,6}.?\\s*([A-Z][A-Z0-9-]*)\\s+SECTION\\s*\\.",
            Pattern.CASE_INSENSITIVE);

    // PROGRAM-ID: e.g. "PROGRAM-ID. MYPROG."
    private static final Pattern PROGRAM_ID_PATTERN = Pattern.compile(
            "PROGRAM-ID\\.?\\s+([A-Z][A-Z0-9-]*)",
            Pattern.CASE_INSENSITIVE);

    // Data item level: starts with level number (01-49, 66, 77, 88)
    // e.g. "       01  WS-COUNTER       PIC 9(4)  VALUE 0."
    private static final Pattern DATA_ITEM_PATTERN = Pattern.compile(
            "^\\s{0,6}.?\\s{0,4}(\\d{2})\\s+([A-Z][A-Z0-9-]*(?:\\s+(?:FILLER))?)" +
                    "(?:\\s+PIC(?:TURE)?\\s+([^\\s.]+))?(?:.*VALUE\\s+([^.]+))?",
            Pattern.CASE_INSENSITIVE);

    // FD (File Description): "FD  filename"
    private static final Pattern FD_PATTERN = Pattern.compile(
            "^\\s{0,6}.?\\s*FD\\s+([A-Z][A-Z0-9-]*)",
            Pattern.CASE_INSENSITIVE);

    // COPY statement: "COPY copybook."
    private static final Pattern COPY_PATTERN = Pattern.compile(
            "\\bCOPY\\s+([A-Z][A-Z0-9-]*)",
            Pattern.CASE_INSENSITIVE);

    // CALL statement: "CALL 'PROGRAM'" or "CALL WS-PGM-NAME"
    private static final Pattern CALL_PATTERN = Pattern.compile(
            "\\bCALL\\s+['\"]?([A-Z][A-Z0-9-]*)['\"]?",
            Pattern.CASE_INSENSITIVE);

    // PERFORM statement: "PERFORM paragraph-name" (not inline PERFORM ... END-PERFORM)
    private static final Pattern PERFORM_PATTERN = Pattern.compile(
            "\\bPERFORM\\s+([A-Z][A-Z0-9-]+)(?:\\s+(?:THRU|THROUGH)\\s+([A-Z][A-Z0-9-]+))?",
            Pattern.CASE_INSENSITIVE);

    // Paragraph name: a word starting in Area A (col 8-11), followed by a period
    // Must NOT be a known keyword
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile(
            "^\\s{0,6}.?\\s{0,3}([A-Z][A-Z0-9-]*)\\s*\\.",
            Pattern.CASE_INSENSITIVE);

    private static final String[] NON_PARAGRAPH_KEYWORDS = {
            "IDENTIFICATION", "ENVIRONMENT", "DATA", "PROCEDURE",
            "WORKING-STORAGE", "LOCAL-STORAGE", "LINKAGE", "FILE",
            "INPUT-OUTPUT", "CONFIGURATION", "SCREEN", "REPORT",
            "FD", "SD", "COPY", "REPLACE", "EJECT", "SKIP1", "SKIP2", "SKIP3",
            "PROGRAM-ID", "AUTHOR", "DATE-WRITTEN", "DATE-COMPILED",
            "SECURITY", "INSTALLATION", "SOURCE-COMPUTER", "OBJECT-COMPUTER",
            "SPECIAL-NAMES", "REPOSITORY", "SELECT", "USE"
    };

    /**
     * Parse COBOL content and return outline model.
     */
    public JclOutlineModel parse(String content, String sourceName) {
        JclOutlineModel model = new JclOutlineModel();
        model.setSourceName(sourceName);
        model.setLanguage(JclOutlineModel.Language.COBOL);

        if (content == null || content.isEmpty()) {
            return model;
        }

        String[] lines = content.split("\\r?\\n");
        model.setTotalLines(lines.length);

        JclElement currentDivision = null;
        JclElement currentSection = null;
        JclElement currentLevel01 = null;
        boolean inProcedure = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;

            // Skip empty lines
            if (line.trim().isEmpty()) continue;

            // Skip comment lines (indicator = '*' in column 7)
            if (line.length() >= 7 && line.charAt(6) == '*') continue;

            // Get the "effective" content (strip sequence numbers and ident area)
            String effective = getEffectiveContent(line);
            if (effective.trim().isEmpty()) continue;

            Matcher m;

            // ── PROGRAM-ID ──────────────────────────────────────────
            m = PROGRAM_ID_PATTERN.matcher(effective);
            if (m.find()) {
                JclElement pgmId = new JclElement(JclElementType.PROGRAM_ID,
                        m.group(1), lineNum, line.trim());
                model.addElement(pgmId);
                continue;
            }

            // ── DIVISION ────────────────────────────────────────────
            m = DIVISION_PATTERN.matcher(line);
            if (m.find()) {
                String divName = m.group(1).toUpperCase() + " DIVISION";
                JclElementType divType = JclElementType.DIVISION;
                if ("PROCEDURE".equalsIgnoreCase(m.group(1))) {
                    divType = JclElementType.PROCEDURE_DIVISION;
                    inProcedure = true;
                }
                JclElement div = new JclElement(divType, divName, lineNum, line.trim());
                model.addElement(div);
                currentDivision = div;
                currentSection = null;
                currentLevel01 = null;
                continue;
            }

            // ── SECTION ─────────────────────────────────────────────
            m = SECTION_PATTERN.matcher(line);
            if (m.find()) {
                String secName = m.group(1).toUpperCase();
                JclElementType secType = JclElementType.SECTION;
                if ("WORKING-STORAGE".equals(secName)) {
                    secType = JclElementType.WORKING_STORAGE;
                } else if ("LINKAGE".equals(secName)) {
                    secType = JclElementType.LINKAGE_SECTION;
                } else if ("FILE".equals(secName)) {
                    secType = JclElementType.FILE_SECTION;
                } else if ("SCREEN".equals(secName)) {
                    secType = JclElementType.SCREEN_SECTION;
                }

                JclElement sec = new JclElement(secType, secName + " SECTION", lineNum, line.trim());
                model.addElement(sec);
                if (currentDivision != null) {
                    currentDivision.addChild(sec);
                }
                currentSection = sec;
                currentLevel01 = null;
                continue;
            }

            // ── FD ──────────────────────────────────────────────────
            m = FD_PATTERN.matcher(line);
            if (m.find()) {
                JclElement fd = new JclElement(JclElementType.FILE_DESCRIPTOR,
                        m.group(1), lineNum, line.trim());
                model.addElement(fd);
                if (currentSection != null) {
                    currentSection.addChild(fd);
                }
                continue;
            }

            // ── COPY ────────────────────────────────────────────────
            m = COPY_PATTERN.matcher(effective);
            if (m.find()) {
                JclElement copy = new JclElement(JclElementType.COPY_STMT,
                        m.group(1), lineNum, line.trim());
                copy.addParameter("COPYBOOK", m.group(1));
                model.addElement(copy);
                continue;
            }

            // ── Data items (only in DATA DIVISION or before PROCEDURE) ──
            if (!inProcedure) {
                m = DATA_ITEM_PATTERN.matcher(line);
                if (m.find()) {
                    int level = Integer.parseInt(m.group(1));
                    String itemName = m.group(2).trim();
                    String pic = m.group(3);
                    String value = m.group(4);

                    JclElementType itemType;
                    if (level == 1) {
                        itemType = JclElementType.LEVEL_01;
                    } else if (level == 77) {
                        itemType = JclElementType.LEVEL_77;
                    } else if (level == 88) {
                        itemType = JclElementType.LEVEL_88;
                    } else {
                        itemType = JclElementType.DATA_ITEM;
                    }

                    JclElement item = new JclElement(itemType, itemName, lineNum, line.trim());
                    if (pic != null) item.addParameter("PIC", pic.trim());
                    if (value != null) item.addParameter("VALUE", value.trim());
                    item.addParameter("LEVEL", String.valueOf(level));

                    // Only add 01, 77, 88 levels and immediate children to outline
                    // (otherwise outline is too noisy)
                    if (level == 1 || level == 77) {
                        model.addElement(item);
                        if (currentSection != null) {
                            currentSection.addChild(item);
                        }
                        currentLevel01 = item;
                    } else if (level == 88) {
                        model.addElement(item);
                        if (currentLevel01 != null) {
                            currentLevel01.addChild(item);
                        }
                    } else if (level >= 2 && level <= 5) {
                        // Show top-level group items as children of 01
                        model.addElement(item);
                        if (currentLevel01 != null) {
                            currentLevel01.addChild(item);
                        }
                    }
                    // Deeper levels (05+) are omitted for a clean outline
                    continue;
                }
            }

            // ── CALL (only in PROCEDURE DIVISION) ───────────────────
            if (inProcedure) {
                m = CALL_PATTERN.matcher(effective);
                if (m.find()) {
                    JclElement call = new JclElement(JclElementType.CALL_STMT,
                            m.group(1), lineNum, line.trim());
                    call.addParameter("TARGET", m.group(1));
                    model.addElement(call);
                    continue;
                }

                // ── PERFORM ─────────────────────────────────────────
                m = PERFORM_PATTERN.matcher(effective);
                if (m.find()) {
                    String target = m.group(1);
                    // Skip inline PERFORM (PERFORM VARYING, PERFORM UNTIL, etc.)
                    if (!isPerformKeyword(target)) {
                        JclElement perform = new JclElement(JclElementType.PERFORM_STMT,
                                target, lineNum, line.trim());
                        perform.addParameter("TARGET", target);
                        if (m.group(2) != null) {
                            perform.addParameter("THRU", m.group(2));
                        }
                        model.addElement(perform);
                        continue;
                    }
                }

                // ── Paragraph name (in PROCEDURE DIVISION) ─────────
                m = PARAGRAPH_PATTERN.matcher(line);
                if (m.find()) {
                    String paraName = m.group(1);
                    if (!isNonParagraphKeyword(paraName) && isInAreaA(line, paraName)) {
                        JclElement para = new JclElement(JclElementType.PARAGRAPH,
                                paraName, lineNum, line.trim());
                        model.addElement(para);
                        if (currentSection != null) {
                            currentSection.addChild(para);
                        } else if (currentDivision != null) {
                            currentDivision.addChild(para);
                        }
                    }
                }
            }
        }

        return model;
    }

    /**
     * Get effective content from a COBOL line, stripping sequence numbers (col 1-6)
     * and identification area (col 73-80).
     */
    private String getEffectiveContent(String line) {
        if (line.length() <= 7) return "";
        int end = Math.min(line.length(), 72);
        return line.substring(7, end);
    }

    /**
     * Check if name appears in Area A (columns 8-11, i.e. index 7-10).
     */
    private boolean isInAreaA(String line, String name) {
        if (line.length() < 8) return false;
        // Area A is columns 8-11 (index 7-10)
        // The name should start at or near column 8
        int nameStart = line.indexOf(name);
        return nameStart >= 6 && nameStart <= 11;
    }

    private boolean isNonParagraphKeyword(String word) {
        String upper = word.toUpperCase();
        for (String kw : NON_PARAGRAPH_KEYWORDS) {
            if (kw.equals(upper)) return true;
        }
        return false;
    }

    private boolean isPerformKeyword(String word) {
        String upper = word.toUpperCase();
        return "VARYING".equals(upper) || "UNTIL".equals(upper)
                || "TIMES".equals(upper) || "WITH".equals(upper)
                || "TEST".equals(upper);
    }
}

