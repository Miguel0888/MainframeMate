package de.bund.zrb.service;

import de.bund.zrb.jcl.model.JclOutlineModel;
import de.bund.zrb.service.NaturalDependencyService.Dependency;
import de.bund.zrb.service.NaturalDependencyService.DependencyKind;
import de.bund.zrb.service.NaturalDependencyService.DependencyResult;
import de.bund.zrb.service.NaturalDependencyGraph.CallHierarchyNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link NaturalAnalysisService} — the central Natural analysis service.
 */
class NaturalAnalysisServiceTest {

    private NaturalAnalysisService service;

    // ── Sample sources ──

    private static final String MAIN_PROG =
            "** MAINPROG - main program\n"
            + "DEFINE DATA\n"
            + "LOCAL USING L-COMMON\n"
            + "END-DEFINE\n"
            + "CALLNAT 'SUB-VALIDATE' #PARAM\n"
            + "CALLNAT 'SUB-PROCESS' #RESULT\n"
            + "FETCH RETURN 'MENU'\n"
            + "INCLUDE CCSTANDARD\n"
            + "END\n";

    private static final String SUB_VALIDATE =
            "** SUB-VALIDATE\n"
            + "DEFINE DATA\n"
            + "PARAMETER USING P-VALIDATE\n"
            + "LOCAL USING L-COMMON\n"
            + "END-DEFINE\n"
            + "CALLNAT 'SUB-HELPER' #X\n"
            + "END\n";

    private static final String SUB_PROCESS =
            "** SUB-PROCESS\n"
            + "DEFINE DATA\n"
            + "PARAMETER USING P-PROCESS\n"
            + "END-DEFINE\n"
            + "CALLNAT 'SUB-VALIDATE' #Y\n"
            + "CALLNAT 'SUB-HELPER' #Z\n"
            + "END\n";

    @BeforeEach
    void setUp() {
        service = NaturalAnalysisService.getInstance();
        // Clean up any leftover state from previous tests
        service.removeGraph("TESTLIB");
    }

    // ═══════════════════════════════════════════════════════════
    //  Parse
    // ═══════════════════════════════════════════════════════════

    @Test
    void parseReturnsOutlineModel() {
        JclOutlineModel model = service.parse(MAIN_PROG, "MAINPROG");
        assertNotNull(model);
        assertEquals("MAINPROG", model.getSourceName());
        assertFalse(model.getElements().isEmpty());
    }

    @Test
    void parseEmptyReturnsEmptyModel() {
        JclOutlineModel model = service.parse("", "EMPTY");
        assertNotNull(model);
        assertTrue(model.getElements().isEmpty());
    }

    @Test
    void parseNullReturnsEmptyModel() {
        JclOutlineModel model = service.parse(null, "NULL");
        assertNotNull(model);
        assertTrue(model.getElements().isEmpty());
    }

    // ═══════════════════════════════════════════════════════════
    //  Dependency analysis
    // ═══════════════════════════════════════════════════════════

    @Test
    void analyzeDependenciesFindsCallnat() {
        DependencyResult result = service.analyzeDependencies(MAIN_PROG, "MAINPROG");
        assertNotNull(result);
        assertFalse(result.isEmpty());

        Map<DependencyKind, List<Dependency>> grouped = result.getGrouped();
        assertTrue(grouped.containsKey(DependencyKind.CALLNAT));
        assertEquals(2, grouped.get(DependencyKind.CALLNAT).size());
    }

    @Test
    void analyzeDependenciesFindsFetch() {
        DependencyResult result = service.analyzeDependencies(MAIN_PROG, "MAINPROG");
        assertTrue(result.getGrouped().containsKey(DependencyKind.FETCH));
    }

    @Test
    void analyzeDependenciesFindsInclude() {
        DependencyResult result = service.analyzeDependencies(MAIN_PROG, "MAINPROG");
        assertTrue(result.getGrouped().containsKey(DependencyKind.INCLUDE));
    }

    @Test
    void analyzeDependenciesFindsUsing() {
        DependencyResult result = service.analyzeDependencies(MAIN_PROG, "MAINPROG");
        assertTrue(result.getGrouped().containsKey(DependencyKind.USING));
    }

    @Test
    void externalCallTargets() {
        DependencyResult result = service.analyzeDependencies(MAIN_PROG, "MAINPROG");
        List<String> targets = result.getExternalCallTargets();
        assertTrue(targets.contains("SUB-VALIDATE"));
        assertTrue(targets.contains("SUB-PROCESS"));
        assertTrue(targets.contains("MENU"));
    }

    // ═══════════════════════════════════════════════════════════
    //  Graph management
    // ═══════════════════════════════════════════════════════════

    @Test
    void buildGraphAndRetrieve() {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("MAINPROG", MAIN_PROG);
        sources.put("SUB-VALIDATE", SUB_VALIDATE);
        sources.put("SUB-PROCESS", SUB_PROCESS);

        NaturalDependencyGraph graph = service.buildGraph("TESTLIB", sources);
        assertNotNull(graph);
        assertTrue(graph.isBuilt());
        assertEquals(3, graph.getKnownSources().size());

        // Retrieve should return the same graph
        NaturalDependencyGraph retrieved = service.getGraph("TESTLIB");
        assertNotNull(retrieved);
        assertSame(graph, retrieved);

        // Cleanup
        service.removeGraph("TESTLIB");
    }

    @Test
    void passiveXRefs() {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("MAINPROG", MAIN_PROG);
        sources.put("SUB-VALIDATE", SUB_VALIDATE);
        sources.put("SUB-PROCESS", SUB_PROCESS);

        service.buildGraph("TESTLIB", sources);

        // SUB-VALIDATE is called by MAINPROG and SUB-PROCESS
        List<NaturalDependencyGraph.CallerInfo> callers =
                service.getPassiveXRefs("TESTLIB", "SUB-VALIDATE");
        assertTrue(callers.size() >= 2, "Expected at least 2 callers of SUB-VALIDATE");

        service.removeGraph("TESTLIB");
    }

    @Test
    void callHierarchyCallees() {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("MAINPROG", MAIN_PROG);
        sources.put("SUB-VALIDATE", SUB_VALIDATE);
        sources.put("SUB-PROCESS", SUB_PROCESS);

        service.buildGraph("TESTLIB", sources);

        CallHierarchyNode root = service.getCallHierarchy("TESTLIB", "MAINPROG", true, 3);
        assertNotNull(root);
        assertFalse(root.getChildren().isEmpty(), "MAINPROG should have callees");

        service.removeGraph("TESTLIB");
    }

    @Test
    void callHierarchyCallers() {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("MAINPROG", MAIN_PROG);
        sources.put("SUB-VALIDATE", SUB_VALIDATE);
        sources.put("SUB-PROCESS", SUB_PROCESS);

        service.buildGraph("TESTLIB", sources);

        CallHierarchyNode root = service.getCallHierarchy("TESTLIB", "SUB-HELPER", false, 3);
        assertNotNull(root);
        assertFalse(root.getChildren().isEmpty(), "SUB-HELPER should have callers");

        service.removeGraph("TESTLIB");
    }

    // ═══════════════════════════════════════════════════════════
    //  AI context building
    // ═══════════════════════════════════════════════════════════

    @Test
    void buildAiDependencySummaryContainsCriticalInfo() {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("MAINPROG", MAIN_PROG);
        sources.put("SUB-VALIDATE", SUB_VALIDATE);
        service.buildGraph("TESTLIB", sources);

        String summary = service.buildAiDependencySummary(MAIN_PROG, "MAINPROG", "TESTLIB");
        assertNotNull(summary);
        assertTrue(summary.contains("MAINPROG"));
        assertTrue(summary.contains("SUB-VALIDATE"));
        assertTrue(summary.contains("CALLNAT"));
        assertTrue(summary.contains("FETCH"));
        assertTrue(summary.contains("INCLUDE"));
        assertTrue(summary.contains("TESTLIB"));

        service.removeGraph("TESTLIB");
    }

    @Test
    void buildAiDependencySummaryWithoutGraph() {
        String summary = service.buildAiDependencySummary(MAIN_PROG, "MAINPROG", null);
        assertNotNull(summary);
        assertTrue(summary.contains("SUB-VALIDATE"));
        assertTrue(summary.contains("CALLNAT"));
        // No passive XRefs section without graph
        assertFalse(summary.contains("Passive XRefs"));
    }

    @Test
    void buildAiCallChainSummary() {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("MAINPROG", MAIN_PROG);
        sources.put("SUB-VALIDATE", SUB_VALIDATE);
        sources.put("SUB-PROCESS", SUB_PROCESS);
        service.buildGraph("TESTLIB", sources);

        String chain = service.buildAiCallChainSummary("TESTLIB", "MAINPROG");
        assertNotNull(chain);
        assertTrue(chain.contains("CALL CHAIN"));
        assertTrue(chain.contains("SUB-VALIDATE"));

        service.removeGraph("TESTLIB");
    }

    @Test
    void buildAiCallChainSummaryNoGraph() {
        String chain = service.buildAiCallChainSummary("NONEXIST", "PROG");
        assertNotNull(chain);
        assertTrue(chain.isEmpty());
    }

    // ═══════════════════════════════════════════════════════════
    //  Heuristic detection
    // ═══════════════════════════════════════════════════════════

    @Test
    void isNaturalSourceBySentenceType() {
        assertTrue(service.isNaturalSource("anything", "Natural"));
        assertTrue(service.isNaturalSource("anything", "NATURAL_PROGRAM"));
        assertTrue(service.isNaturalSource("anything", "Copycode"));
        assertTrue(service.isNaturalSource("anything", "Subprogram"));
        assertTrue(service.isNaturalSource("anything", "Subroutine"));
        assertTrue(service.isNaturalSource("anything", "Helproutine"));
    }

    @Test
    void isNaturalSourceByHeuristic() {
        assertTrue(service.isNaturalSource(MAIN_PROG, null));
    }

    @Test
    void isNaturalSourceFalseForJcl() {
        assertFalse(service.isNaturalSource("//JOBNAME JOB\n//STEP1 EXEC PGM=IEFBR14", null));
    }

    @Test
    void isNaturalSourceFalseForNull() {
        assertFalse(service.isNaturalSource(null, null));
    }

    // ═══════════════════════════════════════════════════════════
    //  File extension detection (.NSC copycodes, etc.)
    // ═══════════════════════════════════════════════════════════

    @Test
    void isNaturalFileByExtension() {
        assertTrue(service.isNaturalFile("MYLIB/COPYDATA.NSC"));
        assertTrue(service.isNaturalFile("PROG.NSP"));
        assertTrue(service.isNaturalFile("SUB.NSN"));
        assertTrue(service.isNaturalFile("ROUTINE.NSS"));
        assertTrue(service.isNaturalFile("HELP.NSH"));
        assertTrue(service.isNaturalFile("MAP.NSM"));
        assertTrue(service.isNaturalFile("GDA.NSG"));
        assertTrue(service.isNaturalFile("LDA.NSL"));
        assertTrue(service.isNaturalFile("PDA.NSA"));
        assertTrue(service.isNaturalFile("CLASS.NS4"));
        assertTrue(service.isNaturalFile("FUNC.NS7"));
        assertFalse(service.isNaturalFile("DDM.NSD"), "NSD is DDM, not Natural");
        assertFalse(service.isNaturalFile("DATA.TXT"));
        assertFalse(service.isNaturalFile(null));
    }

    @Test
    void isNaturalSourceByPathEvenWithMinimalContent() {
        // Copycode with just variable definitions — too few Natural keywords for heuristic alone
        String copycode = "* L-COMMON - common local data\n"
                + "1 #EMPLOYEE-NAME   (A20)\n"
                + "1 #EMPLOYEE-ID     (A8)\n"
                + "1 #DEPARTMENT      (A10)\n";

        // Without path: fails heuristic (no Natural keywords at all)
        assertFalse(service.isNaturalSource(copycode, null, null));

        // With .NSC path: detected as Natural
        assertTrue(service.isNaturalSource(copycode, null, "MYLIB/L-COMMON.NSC"));
    }

    // ═══════════════════════════════════════════════════════════
    //  Path utilities
    // ═══════════════════════════════════════════════════════════

    @Test
    void extractObjectName() {
        assertEquals("MYPROG", service.extractObjectName("MYLIB/MYPROG.NSP"));
        assertEquals("MYPROG", service.extractObjectName("MYPROG.NSP"));
        assertEquals("MYPROG", service.extractObjectName("MYPROG"));
        assertNull(service.extractObjectName(null));
    }

    @Test
    void extractLibrary() {
        assertEquals("MYLIB", service.extractLibrary("MYLIB/MYPROG.NSP"));
        assertNull(service.extractLibrary("MYPROG.NSP"));
        assertNull(service.extractLibrary(null));
    }
}

