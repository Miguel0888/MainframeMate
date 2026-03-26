package de.bund.zrb.jcl.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DdmParser} — verifies parsing of Natural DDM source listings.
 */
class DdmParserTest {

    private final DdmParser parser = new DdmParser();

    // ═══════════════════════════════════════════════════════════
    //  Standard DDM listing format
    // ═══════════════════════════════════════════════════════════

    private static final String STANDARD_DDM_SOURCE =
            "DB: 010 FILE: 001  - EMPLOYEES                   DEFAULT SEQUENCE: AA\n" +
            "\n" +
            "  T L  DB ---- NAME -------------------  F  LENG  S D REMARK\n" +
            "  - --  -- -------------------------------- ----- - -  ------\n" +
            "  * 1  AA PERSONNEL-ID                   A     8\n" +
            "  * 1  AB FIRST-NAME                     A    20\n" +
            "  * 1  AC NAME                            A    20\n" +
            "  * 1  AD MIDDLE-I                        A     1\n" +
            "  * 1  AE MAR-STAT                        A     1\n" +
            "  * 1  AF SEX                             A     1\n" +
            "  * 1  AG BIRTH                           D     6\n" +
            "  * 2  AH DEPT                            A     6\n" +
            "  * 2  AI JOB-TITLE                       A    25\n" +
            "  * 1  AJ CURR-CODE                       A     3\n" +
            "  * 1  AK SALARY                          P     9\n" +
            "  * 1  AL BONUS                           P     9\n";

    @Test
    void parseStandardDdm() {
        DdmParser.DdmDefinition def = parser.parse(STANDARD_DDM_SOURCE, "EMPLOYEES");

        assertNotNull(def, "DDM definition should be parsed");
        assertEquals("EMPLOYEES", def.getName());
        assertEquals(10, def.getDbId());
        assertEquals(1, def.getFileNumber());
        assertEquals("AA", def.getDefaultSequence());

        List<DdmParser.DdmField> fields = def.getFields();
        assertFalse(fields.isEmpty(), "Should have fields");

        // First field: AA PERSONNEL-ID A 8
        DdmParser.DdmField first = fields.get(0);
        assertEquals("AA", first.getShortName());
        assertEquals("PERSONNEL-ID", first.getLongName());
        assertEquals(1, first.getLevel());
        assertEquals("A", first.getFormat());
        assertEquals(8, first.getLength());

        // AG BIRTH should be Date format
        DdmParser.DdmField birth = findByShortName(fields, "AG");
        assertNotNull(birth);
        assertEquals("D", birth.getFormat());
        assertEquals(6, birth.getLength());

        // AK SALARY should be Packed
        DdmParser.DdmField salary = findByShortName(fields, "AK");
        assertNotNull(salary);
        assertEquals("P", salary.getFormat());
        assertEquals(9, salary.getLength());
    }

    @Test
    void primaryKeyDetection() {
        DdmParser.DdmDefinition def = parser.parse(STANDARD_DDM_SOURCE, "EMPLOYEES");
        assertNotNull(def);

        DdmParser.DdmField pkField = findByShortName(def.getFields(), "AA");
        assertNotNull(pkField);
        assertEquals("PK", pkField.getKeyLabel("AA"));
        assertEquals("", pkField.getKeyLabel("ZZ")); // not PK if different default seq
    }

    @Test
    void formatSpecGeneration() {
        DdmParser.DdmDefinition def = parser.parse(STANDARD_DDM_SOURCE, "EMPLOYEES");
        assertNotNull(def);

        DdmParser.DdmField alpha = findByShortName(def.getFields(), "AB");
        assertNotNull(alpha);
        assertEquals("A20", alpha.getFormatSpec());

        DdmParser.DdmField date = findByShortName(def.getFields(), "AG");
        assertNotNull(date);
        assertEquals("D6", date.getFormatSpec());
    }

    // ═══════════════════════════════════════════════════════════
    //  DDM with PE/MU groups
    // ═══════════════════════════════════════════════════════════

    private static final String DDM_WITH_GROUPS =
            "DB: 020 FILE: 005  - VEHICLES                    DEFAULT SEQUENCE: AA\n" +
            "\n" +
            "  T L  DB ---- NAME -------------------  F  LENG  S D REMARK\n" +
            "  - --  -- -------------------------------- ----- - -  ------\n" +
            "  * 1  AA REG-NUM                         A    10\n" +
            "  * 1  AB MAKE                             A    20\n" +
            "  M 1  AC PHONE                            A    15\n" +
            "  P 1  AD SERVICE-HISTORY\n" +
            "  * 2  AE SERVICE-DATE                     D     6\n" +
            "  * 2  AF SERVICE-TYPE                     A    10\n";

    @Test
    void parseGroupFields() {
        DdmParser.DdmDefinition def = parser.parse(DDM_WITH_GROUPS, "VEHICLES");
        assertNotNull(def);
        assertEquals("VEHICLES", def.getName());
        assertEquals(20, def.getDbId());

        DdmParser.DdmField mu = findByShortName(def.getFields(), "AC");
        assertNotNull(mu, "MU field should be parsed");
        assertTrue(mu.isMultipleValue());

        DdmParser.DdmField pe = findByShortName(def.getFields(), "AD");
        assertNotNull(pe, "PE group should be parsed");
        assertTrue(pe.isPeriodicGroup());
        assertTrue(pe.isGroup()); // no format
    }

    // ═══════════════════════════════════════════════════════════
    //  Content detection
    // ═══════════════════════════════════════════════════════════

    @Test
    void isDdmContentPositive() {
        assertTrue(DdmParser.isDdmContent(STANDARD_DDM_SOURCE));
        assertTrue(DdmParser.isDdmContent("DB: 001 FILE: 100"));
        assertTrue(DdmParser.isDdmContent("DATABASE: 10 FILE: 5 DEFAULT SEQUENCE: AA"));
    }

    @Test
    void isDdmContentNegative() {
        assertFalse(DdmParser.isDdmContent(null));
        assertFalse(DdmParser.isDdmContent(""));
        assertFalse(DdmParser.isDdmContent("DEFINE DATA LOCAL\n01 #VAR (A10)\nEND-DEFINE"));
    }

    // ═══════════════════════════════════════════════════════════
    //  Null / empty handling
    // ═══════════════════════════════════════════════════════════

    @Test
    void parseNullReturnsNull() {
        assertNull(parser.parse(null, "TEST"));
    }

    @Test
    void parseEmptyReturnsNull() {
        assertNull(parser.parse("", "TEST"));
    }

    @Test
    void parseNonDdmReturnsNull() {
        assertNull(parser.parse("DEFINE DATA LOCAL\n01 #VAR (A10)\nEND-DEFINE", "TEST"));
    }

    // ═══════════════════════════════════════════════════════════
    //  Helper
    // ═══════════════════════════════════════════════════════════

    private static DdmParser.DdmField findByShortName(List<DdmParser.DdmField> fields, String shortName) {
        for (DdmParser.DdmField f : fields) {
            if (shortName.equals(f.getShortName())) return f;
        }
        return null;
    }
}

