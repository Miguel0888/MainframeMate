package de.bund.zrb.service;

import de.bund.zrb.jcl.model.JclElement;
import de.bund.zrb.jcl.model.JclElementType;
import de.bund.zrb.jcl.model.JclOutlineModel;
import de.bund.zrb.jcl.parser.DdmParser.DdmDefinition;
import de.bund.zrb.service.DdmAnalysisService.DdmDependencyResult;
import de.bund.zrb.service.DdmAnalysisService.DdmHierarchyNode;
import de.bund.zrb.service.DdmAnalysisService.DdmUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DdmAnalysisService} — DDM outline, dependencies, hierarchy.
 */
class DdmAnalysisServiceTest {

    private DdmAnalysisService service;

    // ── Sample DDM source ──

    private static final String EMPLOYEES_DDM =
            "DB: 010 FILE: 001  - EMPLOYEES           DEFAULT SEQUENCE: AA\n" +
            "\n" +
            "T L  DB ---- NAME -------------------  F  LENG  S D REMARK\n" +
            "- --  -- -------------------------------- - ----- - -  ------\n" +
            "  1  AA PERSONNEL-ID                   A     8\n" +
            "  1  AB FIRST-NAME                     A    20\n" +
            "  1  AC NAME                           A    20\n" +
            "M 1  AE PHONE                          A    15\n" +
            "P 1  AF INCOME                         \n" +
            "  2  AG CURR-CODE                      A     3\n" +
            "  2  AH SALARY                         P     9\n" +
            "D    AJ CITY                           \n" +
            "S    AK FULL-NAME                      S=AB(1:10),AC(1:10)\n";

    private static final String VEHICLES_DDM =
            "DB: 010 FILE: 002  - VEHICLES            DEFAULT SEQUENCE: BA\n" +
            "\n" +
            "T L  DB ---- NAME -------------------  F  LENG  S D REMARK\n" +
            "- --  -- -------------------------------- - ----- - -  ------\n" +
            "  1  BA VEHICLE-ID                     A    10\n" +
            "  1  BB MAKE                           A    20\n" +
            "  1  BC MODEL                          A    30\n";

    // ── Sample Natural sources that reference DDMs ──

    private static final String PROG_WITH_EMPLOYEES =
            "** EMPL-REPORT\n" +
            "DEFINE DATA\n" +
            "LOCAL USING L-DATA\n" +
            "LOCAL\n" +
            "1 EMPLOYEES-VIEW VIEW OF EMPLOYEES\n" +
            "  2 PERSONNEL-ID\n" +
            "  2 FIRST-NAME\n" +
            "  2 NAME\n" +
            "END-DEFINE\n" +
            "READ EMPLOYEES BY PERSONNEL-ID\n" +
            "  DISPLAY EMPLOYEES-VIEW.NAME\n" +
            "END-READ\n" +
            "END\n";

    private static final String PROG_WITH_BOTH =
            "** COMBINED-REPORT\n" +
            "DEFINE DATA\n" +
            "LOCAL\n" +
            "1 EMP-VIEW VIEW OF EMPLOYEES\n" +
            "  2 PERSONNEL-ID\n" +
            "1 VEH-VIEW VIEW OF VEHICLES\n" +
            "  2 VEHICLE-ID\n" +
            "END-DEFINE\n" +
            "READ EMPLOYEES BY PERSONNEL-ID\n" +
            "  FIND VEHICLES WITH VEHICLE-ID = *ISN\n" +
            "  END-FIND\n" +
            "END-READ\n" +
            "END\n";

    @BeforeEach
    void setUp() {
        service = DdmAnalysisService.getInstance();
        // Clean up test library graph
        NaturalAnalysisService.getInstance().removeGraph("TESTLIB");
    }

    // ═══════════════════════════════════════════════════════════
    //  Detection
    // ═══════════════════════════════════════════════════════════

    @Test
    void isDdmSourceByContent() {
        assertTrue(service.isDdmSource(EMPLOYEES_DDM, null));
    }

    @Test
    void isDdmSourceBySentenceType() {
        assertTrue(service.isDdmSource("anything", "DDM"));
        assertTrue(service.isDdmSource("anything", "NSD"));
    }

    @Test
    void isDdmSourceFalseForNatural() {
        assertFalse(service.isDdmSource(PROG_WITH_EMPLOYEES, null));
    }

    @Test
    void isDdmFileByExtension() {
        assertTrue(service.isDdmFile("MYLIB/EMPLOYEES.NSD"));
        assertTrue(service.isDdmFile("EMPLOYEES.nsd"));
        assertFalse(service.isDdmFile("PROG.NSP"));
        assertFalse(service.isDdmFile(null));
    }

    // ═══════════════════════════════════════════════════════════
    //  Parsing
    // ═══════════════════════════════════════════════════════════

    @Test
    void parseDdm() {
        DdmDefinition ddm = service.parse(EMPLOYEES_DDM, "EMPLOYEES");
        assertNotNull(ddm);
        assertEquals("EMPLOYEES", ddm.getName());
        assertEquals(10, ddm.getDbId());
        assertEquals(1, ddm.getFileNumber());
        assertEquals("AA", ddm.getDefaultSequence());
        assertFalse(ddm.getFields().isEmpty());
    }

    @Test
    void parseNullReturnsNull() {
        assertNull(service.parse(null, "X"));
        assertNull(service.parse("", "X"));
    }

    // ═══════════════════════════════════════════════════════════
    //  Outline
    // ═══════════════════════════════════════════════════════════

    @Test
    void buildOutlineHasLanguageDdm() {
        JclOutlineModel model = service.buildOutline(EMPLOYEES_DDM, "EMPLOYEES.NSD");
        assertNotNull(model);
        assertEquals(JclOutlineModel.Language.DDM, model.getLanguage());
    }

    @Test
    void buildOutlineHasHeaderElement() {
        JclOutlineModel model = service.buildOutline(EMPLOYEES_DDM, "EMPLOYEES.NSD");
        assertFalse(model.isEmpty());

        JclElement first = model.getElements().get(0);
        assertEquals(JclElementType.DDM_HEADER, first.getType());
        assertTrue(first.getName().contains("EMPLOYEES"));
        assertEquals("10", first.getParameter("DB"));
        assertEquals("1", first.getParameter("FILE"));
    }

    @Test
    void buildOutlineHasFieldElements() {
        JclOutlineModel model = service.buildOutline(EMPLOYEES_DDM, "EMPLOYEES.NSD");

        // Count field elements (excluding header)
        int fieldCount = 0;
        for (JclElement elem : model.getElements()) {
            if (elem.getType() != JclElementType.DDM_HEADER) {
                fieldCount++;
                // Check children of groups
                fieldCount += countDdmChildren(elem);
            }
        }
        assertTrue(fieldCount > 0, "Should have field elements");
    }

    @Test
    void buildOutlineGroupsNestedFields() {
        JclOutlineModel model = service.buildOutline(EMPLOYEES_DDM, "EMPLOYEES.NSD");

        // Find PE group (AF INCOME)
        JclElement peGroup = null;
        for (JclElement elem : model.getElements()) {
            if (elem.getType() == JclElementType.DDM_GROUP
                    && elem.getName().contains("INCOME")) {
                peGroup = elem;
                break;
            }
        }

        assertNotNull(peGroup, "Should find PE group INCOME");
        assertTrue(peGroup.hasChildren(), "PE group should have child fields");
    }

    @Test
    void buildOutlineHasDescriptors() {
        JclOutlineModel model = service.buildOutline(EMPLOYEES_DDM, "EMPLOYEES.NSD");

        boolean hasDescriptor = false;
        boolean hasSuperDescriptor = false;
        for (JclElement elem : model.getElements()) {
            if (elem.getType() == JclElementType.DDM_DESCRIPTOR) hasDescriptor = true;
            if (elem.getType() == JclElementType.DDM_SUPERDESCRIPTOR) hasSuperDescriptor = true;
        }

        assertTrue(hasDescriptor, "Should have descriptor element (AJ CITY)");
        assertTrue(hasSuperDescriptor, "Should have superdescriptor element (AK FULL-NAME)");
    }

    @Test
    void buildOutlineEmptySourceReturnsEmptyModel() {
        JclOutlineModel model = service.buildOutline("", "EMPTY.NSD");
        assertNotNull(model);
        assertTrue(model.isEmpty());
        assertEquals(JclOutlineModel.Language.DDM, model.getLanguage());
    }

    @Test
    void buildOutlineFieldsHaveKeyType() {
        JclOutlineModel model = service.buildOutline(EMPLOYEES_DDM, "EMPLOYEES.NSD");

        // AA should be PK (default sequence)
        boolean foundPk = false;
        for (JclElement elem : model.getElements()) {
            if ("PK".equals(elem.getParameter("KEY_TYPE"))) {
                foundPk = true;
                assertTrue(elem.getName().contains("AA") || elem.getName().contains("PERSONNEL-ID"));
            }
        }
        assertTrue(foundPk, "Should find PK field for default sequence AA");
    }

    // ═══════════════════════════════════════════════════════════
    //  Dependencies (programs using DDM)
    // ═══════════════════════════════════════════════════════════

    @Test
    void findDdmUsersWithGraph() {
        // Build a graph with programs that reference EMPLOYEES DDM
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("EMPL-REPORT", PROG_WITH_EMPLOYEES);
        sources.put("COMBINED-REPORT", PROG_WITH_BOTH);
        NaturalAnalysisService.getInstance().buildGraph("TESTLIB", sources);

        DdmDependencyResult result = service.findDdmUsers("EMPLOYEES", "TESTLIB");
        assertFalse(result.isEmpty(), "Should find programs using EMPLOYEES DDM");
        assertTrue(result.getTotalCount() >= 2,
                "At least 2 references expected (VIEW + READ or multiple programs)");

        // Check that both programs are found
        Set<String> foundPrograms = new HashSet<String>();
        for (List<DdmUser> users : result.getUsersByKind().values()) {
            for (DdmUser user : users) {
                foundPrograms.add(user.getProgramName());
            }
        }
        assertTrue(foundPrograms.contains("EMPL-REPORT"), "Should find EMPL-REPORT");
        assertTrue(foundPrograms.contains("COMBINED-REPORT"), "Should find COMBINED-REPORT");

        NaturalAnalysisService.getInstance().removeGraph("TESTLIB");
    }

    @Test
    void findDdmUsersNoGraph() {
        DdmDependencyResult result = service.findDdmUsers("EMPLOYEES", "NONEXIST");
        assertTrue(result.isEmpty());
    }

    @Test
    void findDdmUsersNullParams() {
        DdmDependencyResult result = service.findDdmUsers(null, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void findDdmUsersVehiclesOnlyInCombined() {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("EMPL-REPORT", PROG_WITH_EMPLOYEES);
        sources.put("COMBINED-REPORT", PROG_WITH_BOTH);
        NaturalAnalysisService.getInstance().buildGraph("TESTLIB", sources);

        DdmDependencyResult result = service.findDdmUsers("VEHICLES", "TESTLIB");
        assertFalse(result.isEmpty());

        Set<String> foundPrograms = new HashSet<String>();
        for (List<DdmUser> users : result.getUsersByKind().values()) {
            for (DdmUser user : users) {
                foundPrograms.add(user.getProgramName());
            }
        }
        assertTrue(foundPrograms.contains("COMBINED-REPORT"), "COMBINED-REPORT uses VEHICLES");
        assertFalse(foundPrograms.contains("EMPL-REPORT"), "EMPL-REPORT does NOT use VEHICLES");

        NaturalAnalysisService.getInstance().removeGraph("TESTLIB");
    }

    // ═══════════════════════════════════════════════════════════
    //  Hierarchy (DDM → programs → related DDMs)
    // ═══════════════════════════════════════════════════════════

    @Test
    void buildDdmHierarchy() {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("EMPL-REPORT", PROG_WITH_EMPLOYEES);
        sources.put("COMBINED-REPORT", PROG_WITH_BOTH);
        NaturalAnalysisService.getInstance().buildGraph("TESTLIB", sources);

        DdmHierarchyNode root = service.buildDdmHierarchy("EMPLOYEES", "TESTLIB", 3);
        assertNotNull(root);
        assertEquals("EMPLOYEES", root.getName());
        assertEquals("DDM", root.getNodeType());
        assertFalse(root.getChildren().isEmpty(), "Should have program children");

        // Check that programs are in the hierarchy
        Set<String> progNames = new HashSet<String>();
        for (DdmHierarchyNode child : root.getChildren()) {
            assertEquals("PROGRAM", child.getNodeType());
            progNames.add(child.getName());
        }
        assertTrue(progNames.contains("EMPL-REPORT"));
        assertTrue(progNames.contains("COMBINED-REPORT"));

        NaturalAnalysisService.getInstance().removeGraph("TESTLIB");
    }

    @Test
    void buildDdmHierarchyShowsRelatedDdms() {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("EMPL-REPORT", PROG_WITH_EMPLOYEES);
        sources.put("COMBINED-REPORT", PROG_WITH_BOTH);
        NaturalAnalysisService.getInstance().buildGraph("TESTLIB", sources);

        DdmHierarchyNode root = service.buildDdmHierarchy("EMPLOYEES", "TESTLIB", 3);

        // Find COMBINED-REPORT node
        DdmHierarchyNode combinedNode = null;
        for (DdmHierarchyNode child : root.getChildren()) {
            if ("COMBINED-REPORT".equals(child.getName())) {
                combinedNode = child;
                break;
            }
        }
        assertNotNull(combinedNode, "Should find COMBINED-REPORT");

        // COMBINED-REPORT should have child DDMs: EMPLOYEES (recursive) and VEHICLES
        Set<String> relatedDdms = new HashSet<String>();
        for (DdmHierarchyNode ddmChild : combinedNode.getChildren()) {
            if ("DDM".equals(ddmChild.getNodeType())) {
                relatedDdms.add(ddmChild.getName());
            }
        }
        assertTrue(relatedDdms.contains("VEHICLES"), "COMBINED-REPORT references VEHICLES DDM");
        assertTrue(relatedDdms.contains("EMPLOYEES"), "COMBINED-REPORT references EMPLOYEES DDM (recursive)");

        NaturalAnalysisService.getInstance().removeGraph("TESTLIB");
    }

    @Test
    void buildDdmHierarchyNoGraph() {
        DdmHierarchyNode root = service.buildDdmHierarchy("EMPLOYEES", "NONEXIST", 3);
        assertNotNull(root);
        assertTrue(root.getChildren().isEmpty());
    }

    @Test
    void buildDdmHierarchyNullParams() {
        DdmHierarchyNode root = service.buildDdmHierarchy(null, null, 3);
        assertNotNull(root);
        assertTrue(root.getChildren().isEmpty());
    }

    // ═══════════════════════════════════════════════════════════
    //  AI context
    // ═══════════════════════════════════════════════════════════

    @Test
    void buildAiDdmSummaryContainsFields() {
        String summary = service.buildAiDdmSummary(EMPLOYEES_DDM, "EMPLOYEES", null);
        assertNotNull(summary);
        assertTrue(summary.contains("EMPLOYEES"));
        assertTrue(summary.contains("PERSONNEL-ID"));
        assertTrue(summary.contains("FIRST-NAME"));
        assertTrue(summary.contains("Database: 10"));
    }

    @Test
    void buildAiDdmSummaryWithLibraryShowsUsers() {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("EMPL-REPORT", PROG_WITH_EMPLOYEES);
        NaturalAnalysisService.getInstance().buildGraph("TESTLIB", sources);

        String summary = service.buildAiDdmSummary(EMPLOYEES_DDM, "EMPLOYEES", "TESTLIB");
        assertTrue(summary.contains("EMPL-REPORT"), "Summary should mention program using this DDM");

        NaturalAnalysisService.getInstance().removeGraph("TESTLIB");
    }

    // ═══════════════════════════════════════════════════════════
    //  Path utilities
    // ═══════════════════════════════════════════════════════════

    @Test
    void extractDdmName() {
        assertEquals("EMPLOYEES", service.extractDdmName("MYLIB/EMPLOYEES.NSD"));
        assertEquals("EMPLOYEES", service.extractDdmName("EMPLOYEES.NSD"));
        assertEquals("EMPLOYEES", service.extractDdmName("EMPLOYEES"));
        assertNull(service.extractDdmName(null));
    }

    @Test
    void extractLibrary() {
        assertEquals("MYLIB", service.extractLibrary("MYLIB/EMPLOYEES.NSD"));
        assertNull(service.extractLibrary("EMPLOYEES.NSD"));
        assertNull(service.extractLibrary(null));
    }

    // ── Helper ──

    private int countDdmChildren(JclElement elem) {
        int count = 0;
        for (JclElement child : elem.getChildren()) {
            count++;
            count += countDdmChildren(child);
        }
        return count;
    }
}

