package de.bund.zrb.jcl.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Natural DDM (Data Definition Module) source listings.
 * <p>
 * DDMs describe ADABAS file structures downloaded from NDV.
 * The typical source format is:
 * <pre>
 *   DB: 010 FILE: 001  - EMPLOYEES           DEFAULT SEQUENCE: AA
 *
 *   T L  DB ---- NAME -------------------  F  LENG  S D REMARK
 *   - --  -- -------------------------------- - ----- - -  ------
 *   * 1  AA PERSONNEL-ID                   A     8
 *   * 1  AB FIRST-NAME                     A    20
 *   M 1  AE PHONE                          A    15
 *   P 1  AF INCOME                         (PE group)
 *   * 2  AG CURR-CODE                      A     3
 * </pre>
 * <p>
 * This parser is lenient and handles variations in column alignment.
 */
public class DdmParser {

    // ── Header patterns ──
    // "DB: 010 FILE: 001  - EMPLOYEES   DEFAULT SEQUENCE: AA"
    private static final Pattern HEADER_DB_FILE = Pattern.compile(
            "(?:DB|DATABASE)[:\\s]+(\\d+)\\s+(?:FILE|FNR|FILE NO\\.?)[:\\s]+(\\d+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern HEADER_DDM_NAME = Pattern.compile(
            "(?:DDM|FILE)[:\\s]*\\s+([A-Z][A-Z0-9_-]+)",
            Pattern.CASE_INSENSITIVE);

    // Alternative: name appears after single dash (not multi-dash): "- EMPLOYEES"
    private static final Pattern HEADER_DASH_NAME = Pattern.compile(
            "(?<![\\-])\\-\\s+([A-Z][A-Z0-9_-]{2,})", Pattern.CASE_INSENSITIVE);

    private static final Pattern DEFAULT_SEQ = Pattern.compile(
            "DEFAULT\\s+SEQ(?:UENCE)?[:\\s]+([A-Z]{2})",
            Pattern.CASE_INSENSITIVE);

    // ── Field line pattern ──
    // T  L  DB  NAME                         F  LENG  [S] [D]
    // The type indicator: * (regular), M (MU), P (PE), S (super), D (descriptor), H (hyper), etc.
    // Lenient: allows any single char or blank for the type column
    private static final Pattern FIELD_LINE = Pattern.compile(
            "^\\s*([*MPSDHCN ])\\s+(\\d{1,2})\\s+([A-Z][A-Z0-9])\\s+" +
                    "([A-Z][A-Z0-9_.#@$-]+(?:\\s+[A-Z][A-Z0-9_.#@$-]+)*)\\s+" +
                    "([ANPBDTLFUW])\\s+(\\d+)(?:\\s+(\\d+))?",
            Pattern.CASE_INSENSITIVE);

    // PE / MU group header (no format/length): "P 1 AF INCOME"
    private static final Pattern GROUP_LINE = Pattern.compile(
            "^\\s*([PM])\\s+(\\d{1,2})\\s+([A-Z][A-Z0-9])\\s+" +
                    "([A-Z][A-Z0-9_.#@$-]+(?:\\s+[A-Z][A-Z0-9_.#@$-]+)*)",
            Pattern.CASE_INSENSITIVE);

    // Descriptor-only line (super/sub/hyper): "D    AJ CITY      S=AC(1:10)"
    private static final Pattern DESCRIPTOR_LINE = Pattern.compile(
            "^\\s*([SDH])\\s+([A-Z][A-Z0-9])\\s+" +
                    "([A-Z][A-Z0-9_.#@$-]+(?:\\s+[A-Z][A-Z0-9_.#@$-]+)*)\\s*" +
                    "(?:(.+))?",
            Pattern.CASE_INSENSITIVE);

    // ═══════════════════════════════════════════════════════════
    //  Model classes
    // ═══════════════════════════════════════════════════════════

    /**
     * Parsed DDM definition.
     */
    public static class DdmDefinition {
        private final String name;
        private final int dbId;
        private final int fileNumber;
        private final String defaultSequence;
        private final List<DdmField> fields;

        public DdmDefinition(String name, int dbId, int fileNumber,
                             String defaultSequence, List<DdmField> fields) {
            this.name = name;
            this.dbId = dbId;
            this.fileNumber = fileNumber;
            this.defaultSequence = defaultSequence;
            this.fields = Collections.unmodifiableList(fields);
        }

        public String getName() { return name; }
        public int getDbId() { return dbId; }
        public int getFileNumber() { return fileNumber; }
        public String getDefaultSequence() { return defaultSequence; }
        public List<DdmField> getFields() { return fields; }

        /** Get only top-level fields (level 1). */
        public List<DdmField> getTopLevelFields() {
            List<DdmField> result = new ArrayList<DdmField>();
            for (DdmField f : fields) {
                if (f.getLevel() == 1 || f.getLevel() == 0) {
                    result.add(f);
                }
            }
            return result;
        }
    }

    /**
     * A single field in a DDM definition.
     */
    public static class DdmField {
        private final String shortName;   // 2-char ADABAS name (AA, AB, ...)
        private final String longName;    // descriptive name (PERSONNEL-ID)
        private final int level;          // hierarchy level (1-7)
        private final char typeIndicator; // *, M (MU), P (PE), S, D, H
        private final String format;      // A, N, P, B, D, T, L, F, U, W
        private final int length;
        private final int decimals;       // for numeric fields
        private final boolean descriptor; // is a descriptor?
        private final boolean superdescriptor;
        private final String remark;

        public DdmField(String shortName, String longName, int level,
                        char typeIndicator, String format, int length, int decimals,
                        boolean descriptor, boolean superdescriptor, String remark) {
            this.shortName = shortName;
            this.longName = longName;
            this.level = level;
            this.typeIndicator = typeIndicator;
            this.format = format;
            this.length = length;
            this.decimals = decimals;
            this.descriptor = descriptor;
            this.superdescriptor = superdescriptor;
            this.remark = remark;
        }

        public String getShortName() { return shortName; }
        public String getLongName() { return longName; }
        public int getLevel() { return level; }
        public char getTypeIndicator() { return typeIndicator; }
        public String getFormat() { return format; }
        public int getLength() { return length; }
        public int getDecimals() { return decimals; }
        public boolean isDescriptor() { return descriptor; }
        public boolean isSuperdescriptor() { return superdescriptor; }
        public String getRemark() { return remark; }

        /** Is this a periodic group (PE)? */
        public boolean isPeriodicGroup() { return typeIndicator == 'P'; }
        /** Is this a multiple-value field (MU)? */
        public boolean isMultipleValue() { return typeIndicator == 'M'; }
        /** Is this a group field (no format, just level/name)? */
        public boolean isGroup() { return format == null || format.isEmpty(); }

        /**
         * Mermaid-compatible format string for ER attribute type.
         * E.g. "A20" for Alpha 20, "P9" for Packed 9, "D" for Date.
         */
        public String getFormatSpec() {
            if (format == null || format.isEmpty()) return "GRP";
            StringBuilder sb = new StringBuilder(format);
            if (length > 0) sb.append(length);
            if (decimals > 0) sb.append(".").append(decimals);
            return sb.toString();
        }

        /**
         * Constraint / key label for Mermaid ER.
         * PK for default sequence field, FK for descriptors.
         */
        public String getKeyLabel(String defaultSequence) {
            if (shortName != null && shortName.equalsIgnoreCase(defaultSequence)) return "PK";
            if (descriptor || superdescriptor) return "FK";
            return "";
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Parsing
    // ═══════════════════════════════════════════════════════════

    /**
     * Parse DDM source text into a structured definition.
     *
     * @param source the DDM listing text (from NDV readSource)
     * @param fallbackName DDM name to use if not detected from source
     * @return parsed DDM definition, or null if source is not a valid DDM
     */
    public DdmDefinition parse(String source, String fallbackName) {
        if (source == null || source.trim().isEmpty()) return null;

        String[] lines = source.split("\\r?\\n");

        // ── Phase 1: Extract header information ──
        int dbId = 0;
        int fileNumber = 0;
        String ddmName = fallbackName != null ? fallbackName : "UNKNOWN";
        String defaultSequence = "";

        boolean headerDone = false;
        int fieldStartLine = 0;
        boolean nameFound = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            // Try to extract DB/FILE number
            Matcher m = HEADER_DB_FILE.matcher(line);
            if (m.find()) {
                dbId = Integer.parseInt(m.group(1));
                fileNumber = Integer.parseInt(m.group(2));
            }

            // Try to extract DDM name (only if not yet found)
            if (!nameFound) {
                m = HEADER_DDM_NAME.matcher(line);
                if (m.find()) {
                    ddmName = m.group(1).toUpperCase();
                    nameFound = true;
                } else {
                    m = HEADER_DASH_NAME.matcher(line);
                    if (m.find()) {
                        ddmName = m.group(1).toUpperCase();
                        nameFound = true;
                    }
                }
            }

            // Default sequence
            m = DEFAULT_SEQ.matcher(line);
            if (m.find()) {
                defaultSequence = m.group(1).toUpperCase();
            }

            // Detect field listing start (header separator line with dashes)
            if (line.matches("^\\s*-\\s+-.*") || line.contains("---- NAME")) {
                fieldStartLine = i + 1;
                // Skip the separator line after column headers
                if (fieldStartLine < lines.length
                        && lines[fieldStartLine].trim().matches("^[-\\s]+$")) {
                    fieldStartLine++;
                }
                headerDone = true;
                break;
            }

            // If we've processed a few lines and see field-like content, switch to parsing
            if (i > 5 && !headerDone) {
                Matcher fm = FIELD_LINE.matcher(line);
                if (fm.find()) {
                    fieldStartLine = i;
                    headerDone = true;
                    break;
                }
            }
        }

        // If no explicit header found, try parsing from line 0
        if (!headerDone) {
            fieldStartLine = 0;
        }

        // ── Phase 2: Parse field definitions ──
        List<DdmField> fields = new ArrayList<DdmField>();

        for (int i = fieldStartLine; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty()) continue;
            // Skip comment/separator lines
            if (line.trim().startsWith("-") || line.trim().startsWith("=")) continue;
            // Skip column header lines
            if (line.contains("---- NAME") || line.contains("DB Name") || line.contains("LENG")) {
                continue;
            }

            DdmField field = parseFieldLine(line);
            if (field != null) {
                fields.add(field);
            }
        }

        if (fields.isEmpty()) {
            // Fallback: try to parse the whole thing as field lines
            for (String line : lines) {
                DdmField field = parseFieldLine(line);
                if (field != null) {
                    fields.add(field);
                }
            }
        }

        if (fields.isEmpty()) return null;

        return new DdmDefinition(ddmName, dbId, fileNumber, defaultSequence, fields);
    }

    /**
     * Try to parse a single line as a DDM field definition.
     */
    private DdmField parseFieldLine(String line) {
        if (line == null || line.trim().isEmpty()) return null;

        // Try regular field line: T L DB NAME F LENG [DECIMALS]
        Matcher m = FIELD_LINE.matcher(line);
        if (m.find()) {
            char typeInd = m.group(1).trim().isEmpty() ? '*' : m.group(1).trim().charAt(0);
            int level = Integer.parseInt(m.group(2));
            String shortName = m.group(3).toUpperCase();
            String longName = m.group(4).trim().replaceAll("\\s+", "-").toUpperCase();
            String format = m.group(5).toUpperCase();
            int length = Integer.parseInt(m.group(6));
            int decimals = m.group(7) != null ? Integer.parseInt(m.group(7)) : 0;

            // Check for D (descriptor) and S (superdescriptor) flags in remaining text
            String rest = line.substring(m.end()).trim();
            boolean desc = rest.contains("D") || typeInd == 'D';
            boolean superDesc = rest.contains("S") || typeInd == 'S';

            return new DdmField(shortName, longName, level, typeInd,
                    format, length, decimals, desc, superDesc, rest);
        }

        // Try PE/MU group line (no format): P L DB NAME
        m = GROUP_LINE.matcher(line);
        if (m.find()) {
            char typeInd = m.group(1).trim().charAt(0);
            int level = Integer.parseInt(m.group(2));
            String shortName = m.group(3).toUpperCase();
            String longName = m.group(4).trim().replaceAll("\\s+", "-").toUpperCase();
            return new DdmField(shortName, longName, level, typeInd,
                    "", 0, 0, false, false, "");
        }

        // Try descriptor-only line: D/S/H  DB  NAME  [definition]
        m = DESCRIPTOR_LINE.matcher(line);
        if (m.find()) {
            char typeInd = m.group(1).trim().charAt(0);
            String shortName = m.group(2).toUpperCase();
            String longName = m.group(3).trim().replaceAll("\\s+", "-").toUpperCase();
            String def = m.group(4) != null ? m.group(4).trim() : "";
            boolean superDesc = typeInd == 'S' || def.contains("S=");
            return new DdmField(shortName, longName, 0, typeInd,
                    "", 0, 0, true, superDesc, def);
        }

        return null;
    }

    /**
     * Check if the given source text looks like a DDM definition.
     * Used for auto-detection when the file extension is ambiguous.
     */
    public static boolean isDdmContent(String content) {
        if (content == null || content.isEmpty()) return false;
        String upper = content.toUpperCase();
        // Check for typical DDM header patterns
        if (upper.contains("DEFAULT SEQUENCE") || upper.contains("DEFAULT SEQ")) return true;
        if (upper.contains("DB:") && upper.contains("FILE:")) return true;
        if (upper.contains("DATABASE:") && upper.contains("FILE:")) return true;
        // Check for DDM field listing header
        if (upper.contains("---- NAME") && upper.contains("LENG")) return true;
        if (upper.contains("DB NAME") && upper.contains("F LEN")) return true;
        return false;
    }
}

