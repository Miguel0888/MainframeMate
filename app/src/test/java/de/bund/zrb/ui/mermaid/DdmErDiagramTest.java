package de.bund.zrb.ui.mermaid;

import de.bund.zrb.jcl.parser.DdmParser;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DDM → ER diagram conversion in {@link OutlineToMermaidConverter}.
 */
class DdmErDiagramTest {

    private static final String EMPLOYEES_DDM =
            "DB: 010 FILE: 001  - EMPLOYEES                   DEFAULT SEQUENCE: AA\n" +
            "\n" +
            "  T L  DB ---- NAME -------------------  F  LENG  S D REMARK\n" +
            "  - --  -- -------------------------------- ----- - -  ------\n" +
            "  * 1  AA PERSONNEL-ID                   A     8\n" +
            "  * 1  AB FIRST-NAME                     A    20\n" +
            "  * 1  AC NAME                            A    20\n" +
            "  * 1  AG BIRTH                           D     6\n" +
            "  * 1  AK SALARY                          P     9\n";

    private static final String VEHICLES_DDM =
            "DB: 020 FILE: 005  - VEHICLES                    DEFAULT SEQUENCE: AA\n" +
            "\n" +
            "  T L  DB ---- NAME -------------------  F  LENG  S D REMARK\n" +
            "  - --  -- -------------------------------- ----- - -  ------\n" +
            "  * 1  AA REG-NUM                         A    10\n" +
            "  * 1  AB MAKE                             A    20\n";

    @Test
    void singleDdmErDiagram() {
        DdmParser parser = new DdmParser();
        DdmParser.DdmDefinition def = parser.parse(EMPLOYEES_DDM, "EMPLOYEES");
        assertNotNull(def);

        String mermaid = OutlineToMermaidConverter.convertDdmToErDiagram(def);
        assertNotNull(mermaid, "ER diagram should be generated");
        assertTrue(mermaid.startsWith("erDiagram"), "Should start with erDiagram keyword");

        // Check entity name
        assertTrue(mermaid.contains("EMPLOYEES"), "Should contain entity name EMPLOYEES");

        // Check attributes
        assertTrue(mermaid.contains("PERSONNEL_ID"), "Should contain PERSONNEL-ID as attribute");
        assertTrue(mermaid.contains("FIRST_NAME"), "Should contain FIRST-NAME attribute");
        assertTrue(mermaid.contains("SALARY"), "Should contain SALARY attribute");

        // Check PK marking
        assertTrue(mermaid.contains("PK"), "Default sequence field AA should be marked PK");

        // Check format specs
        assertTrue(mermaid.contains("A8"), "Alpha 8 format spec");
        assertTrue(mermaid.contains("D6"), "Date 6 format spec");
        assertTrue(mermaid.contains("P9"), "Packed 9 format spec");
    }

    @Test
    void multiDdmErDiagram() {
        DdmParser parser = new DdmParser();
        DdmParser.DdmDefinition emp = parser.parse(EMPLOYEES_DDM, "EMPLOYEES");
        DdmParser.DdmDefinition veh = parser.parse(VEHICLES_DDM, "VEHICLES");
        assertNotNull(emp);
        assertNotNull(veh);

        List<DdmParser.DdmDefinition> defs = Arrays.asList(emp, veh);
        String mermaid = OutlineToMermaidConverter.convertDdmDefsToErDiagram(defs, null);

        assertNotNull(mermaid, "Multi-DDM ER diagram should be generated");
        assertTrue(mermaid.contains("EMPLOYEES"), "Should contain EMPLOYEES entity");
        assertTrue(mermaid.contains("VEHICLES"), "Should contain VEHICLES entity");
    }

    @Test
    void nullDdmReturnsNull() {
        assertNull(OutlineToMermaidConverter.convertDdmToErDiagram(null));
    }
}

