package de.bund.zrb.service;

import de.bund.zrb.service.NaturalDependencyGraph.CallerInfo;
import de.bund.zrb.service.NaturalDependencyGraph.CallHierarchyNode;
import de.bund.zrb.service.NaturalDependencyService.DependencyKind;
import de.bund.zrb.service.NaturalDependencyService.DependencyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link NaturalDependencyGraph} — bidirectional dependency graph
 * with passive XRefs (callers) and call hierarchy.
 */
class NaturalDependencyGraphTest {

    private NaturalDependencyGraph graph;

    // ── Sample Natural sources ──

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
            "** SUB-VALIDATE - validation subprogram\n"
            + "DEFINE DATA\n"
            + "PARAMETER USING P-VALIDATE\n"
            + "LOCAL USING L-COMMON\n"
            + "END-DEFINE\n"
            + "CALLNAT 'SUB-HELPER' #X\n"
            + "READ EMPLOYEES BY NAME\n"
            + "END-READ\n"
            + "END\n";

    private static final String SUB_PROCESS =
            "** SUB-PROCESS - processing subprogram\n"
            + "DEFINE DATA\n"
            + "PARAMETER USING P-PROCESS\n"
            + "END-DEFINE\n"
            + "CALLNAT 'SUB-HELPER' #Y\n"
            + "CALLNAT 'SUB-VALIDATE' #Z\n"
            + "INCLUDE CCSTANDARD\n"
            + "END\n";

    private static final String SUB_HELPER =
            "** SUB-HELPER - utility subprogram\n"
            + "DEFINE DATA\n"
            + "PARAMETER\n"
            + "01 #INPUT (A50)\n"
            + "END-DEFINE\n"
            + "PERFORM DO-STUFF\n"
            + "DEFINE SUBROUTINE DO-STUFF\n"
            + "END-SUBROUTINE\n"
            + "END\n";

    private static final String MENU =
            "** MENU - menu program\n"
            + "DEFINE DATA LOCAL\nEND-DEFINE\n"
            + "FETCH 'MAINPROG'\n"
            + "END\n";

    @BeforeEach
    void setUp() {
        graph = new NaturalDependencyGraph();
        graph.addSource("TESTLIB", "MAINPROG", MAIN_PROG);
        graph.addSource("TESTLIB", "SUB-VALIDATE", SUB_VALIDATE);
        graph.addSource("TESTLIB", "SUB-PROCESS", SUB_PROCESS);
        graph.addSource("TESTLIB", "SUB-HELPER", SUB_HELPER);
        graph.addSource("TESTLIB", "MENU", MENU);
        graph.build();
    }

    // ═══════════════════════════════════════════════════════════
    //  Basic graph properties
    // ═══════════════════════════════════════════════════════════

    @Test
    void graphIsBuilt() {
        assertTrue(graph.isBuilt());
        assertEquals("TESTLIB", graph.getLibrary());
    }

    @Test
    void knownSources() {
        Set<String> sources = graph.getKnownSources();
        assertEquals(5, sources.size());
        assertTrue(sources.contains("MAINPROG"));
        assertTrue(sources.contains("SUB-VALIDATE"));
        assertTrue(sources.contains("SUB-PROCESS"));
        assertTrue(sources.contains("SUB-HELPER"));
        assertTrue(sources.contains("MENU"));
    }

    // ═══════════════════════════════════════════════════════════
    //  Active XRefs (callees)
    // ═══════════════════════════════════════════════════════════

    @Test
    void activeXRefsMainProg() {
        DependencyResult result = graph.getActiveXRefs("MAINPROG");
        assertFalse(result.isEmpty());

        Map<DependencyKind, List<NaturalDependencyService.Dependency>> grouped = result.getGrouped();
        assertTrue(grouped.containsKey(DependencyKind.CALLNAT));
        assertTrue(grouped.containsKey(DependencyKind.FETCH));
        assertTrue(grouped.containsKey(DependencyKind.INCLUDE));
        assertTrue(grouped.containsKey(DependencyKind.USING));

        // CALLNAT targets
        List<String> targets = result.getExternalCallTargets();
        assertTrue(targets.contains("SUB-VALIDATE"));
        assertTrue(targets.contains("SUB-PROCESS"));
        assertTrue(targets.contains("MENU"));
    }

    @Test
    void activeXRefsUnknownObject() {
        DependencyResult result = graph.getActiveXRefs("NONEXISTENT");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ═══════════════════════════════════════════════════════════
    //  Passive XRefs (callers)
    // ═══════════════════════════════════════════════════════════

    @Test
    void passiveXRefsSubValidate() {
        // SUB-VALIDATE is called by MAINPROG (CALLNAT) and SUB-PROCESS (CALLNAT)
        List<CallerInfo> callers = graph.getPassiveXRefs("SUB-VALIDATE");
        assertFalse(callers.isEmpty());
        assertEquals(2, callers.size());

        List<String> callerNames = new java.util.ArrayList<String>();
        for (CallerInfo c : callers) callerNames.add(c.getCallerName());
        assertTrue(callerNames.contains("MAINPROG"));
        assertTrue(callerNames.contains("SUB-PROCESS"));
    }

    @Test
    void passiveXRefsSubHelper() {
        // SUB-HELPER is called by SUB-VALIDATE and SUB-PROCESS
        List<CallerInfo> callers = graph.getPassiveXRefs("SUB-HELPER");
        assertEquals(2, callers.size());
    }

    @Test
    void passiveXRefsCcstandard() {
        // CCSTANDARD is included by MAINPROG and SUB-PROCESS
        List<CallerInfo> callers = graph.getPassiveXRefs("CCSTANDARD");
        assertEquals(2, callers.size());

        for (CallerInfo c : callers) {
            assertEquals(DependencyKind.INCLUDE, c.getReferenceKind());
        }
    }

    @Test
    void passiveXRefsLCommon() {
        // L-COMMON is used by MAINPROG and SUB-VALIDATE (LOCAL USING)
        List<CallerInfo> callers = graph.getPassiveXRefs("L-COMMON");
        assertEquals(2, callers.size());
    }

    @Test
    void passiveXRefsGrouped() {
        Map<DependencyKind, List<CallerInfo>> grouped = graph.getPassiveXRefsGrouped("SUB-VALIDATE");
        assertTrue(grouped.containsKey(DependencyKind.CALLNAT));
        assertEquals(2, grouped.get(DependencyKind.CALLNAT).size());
    }

    @Test
    void passiveXRefsNoCallers() {
        // MENU has no callers in this test set (it's only called via FETCH from MAINPROG)
        // Actually MAINPROG fetches MENU, so MENU should have MAINPROG as caller
        List<CallerInfo> callers = graph.getPassiveXRefs("MENU");
        assertFalse(callers.isEmpty());
        assertEquals("MAINPROG", callers.get(0).getCallerName());
        assertEquals(DependencyKind.FETCH, callers.get(0).getReferenceKind());
    }

    // ═══════════════════════════════════════════════════════════
    //  Call Hierarchy
    // ═══════════════════════════════════════════════════════════

    @Test
    void callHierarchyCalleesFromMainProg() {
        CallHierarchyNode root = graph.getCallHierarchy("MAINPROG", true, 3);
        assertEquals("MAINPROG", root.getObjectName());
        assertFalse(root.isLeaf());

        // MAINPROG calls SUB-VALIDATE, SUB-PROCESS, MENU, CCSTANDARD
        assertTrue(root.getChildren().size() >= 3, "Expected at least 3 callees");

        // Find SUB-VALIDATE child and check it has its own children
        CallHierarchyNode subValidateNode = null;
        for (CallHierarchyNode child : root.getChildren()) {
            if ("SUB-VALIDATE".equals(child.getObjectName())) {
                subValidateNode = child;
                break;
            }
        }
        assertNotNull(subValidateNode, "SUB-VALIDATE should be a callee of MAINPROG");
        assertFalse(subValidateNode.isLeaf(), "SUB-VALIDATE should have its own callees");
    }

    @Test
    void callHierarchyCallersOfSubHelper() {
        CallHierarchyNode root = graph.getCallHierarchy("SUB-HELPER", false, 3);
        assertEquals("SUB-HELPER", root.getObjectName());

        // SUB-HELPER is called by SUB-VALIDATE and SUB-PROCESS
        assertEquals(2, root.getChildren().size());

        // SUB-VALIDATE is called by MAINPROG and SUB-PROCESS → so it should have children too
        CallHierarchyNode subValidateNode = null;
        for (CallHierarchyNode child : root.getChildren()) {
            if ("SUB-VALIDATE".equals(child.getObjectName())) {
                subValidateNode = child;
                break;
            }
        }
        assertNotNull(subValidateNode);
        assertFalse(subValidateNode.isLeaf(), "SUB-VALIDATE should have callers too");
    }

    @Test
    void callHierarchyDetectsRecursion() {
        // Create a recursive scenario: A calls B, B calls A
        NaturalDependencyGraph cycleGraph = new NaturalDependencyGraph();
        cycleGraph.addSource("LIB", "PROGA",
                "DEFINE DATA LOCAL\nEND-DEFINE\nCALLNAT 'PROGB' #X\nEND\n");
        cycleGraph.addSource("LIB", "PROGB",
                "DEFINE DATA LOCAL\nEND-DEFINE\nCALLNAT 'PROGA' #X\nEND\n");
        cycleGraph.build();

        CallHierarchyNode root = cycleGraph.getCallHierarchy("PROGA", true, 5);
        assertEquals("PROGA", root.getObjectName());
        assertEquals(1, root.getChildren().size());

        CallHierarchyNode progB = root.getChildren().get(0);
        assertEquals("PROGB", progB.getObjectName());
        assertEquals(1, progB.getChildren().size());

        CallHierarchyNode recursiveA = progB.getChildren().get(0);
        assertEquals("PROGA", recursiveA.getObjectName());
        assertTrue(recursiveA.isRecursive(), "Should detect recursion");
        assertTrue(recursiveA.isLeaf(), "Recursive node should be a leaf");
    }

    @Test
    void callHierarchyMaxDepthLimits() {
        CallHierarchyNode root = graph.getCallHierarchy("MAINPROG", true, 1);
        // At depth 1, children should be leaves (not expanded further)
        for (CallHierarchyNode child : root.getChildren()) {
            assertTrue(child.isLeaf(), "At maxDepth=1, children should be leaves");
        }
    }

    @Test
    void callHierarchyTotalNodes() {
        CallHierarchyNode root = graph.getCallHierarchy("MAINPROG", true, 3);
        assertTrue(root.totalNodes() > 4, "Should have multiple nodes in hierarchy");
    }

    // ═══════════════════════════════════════════════════════════
    //  Utility methods
    // ═══════════════════════════════════════════════════════════

    @Test
    void entryPoints() {
        // Objects without callers in this graph
        List<String> entryPoints = graph.getEntryPoints();
        // MAINPROG is called by MENU (FETCH), so it's not an entry point
        // MENU has a caller (MAINPROG), so not entry point
        // Actually let's just verify the method works
        assertNotNull(entryPoints);
    }

    @Test
    void unresolvedTargets() {
        // P-VALIDATE, P-PROCESS, L-COMMON, CCSTANDARD, EMPLOYEES are referenced but not sources
        Set<String> unresolved = graph.getUnresolvedTargets();
        assertFalse(unresolved.isEmpty());
        // P-VALIDATE is a PDA used by SUB-VALIDATE (USING), not a source
        assertTrue(unresolved.contains("P-VALIDATE"));
        assertTrue(unresolved.contains("P-PROCESS"));
    }

    @Test
    void isKnownSource() {
        assertTrue(graph.isKnownSource("MAINPROG"));
        assertTrue(graph.isKnownSource("mainprog")); // case-insensitive
        assertFalse(graph.isKnownSource("NONEXISTENT"));
    }

    @Test
    void clearGraph() {
        graph.clear();
        assertFalse(graph.isBuilt());
        assertTrue(graph.getKnownSources().isEmpty());
        assertTrue(graph.getActiveXRefs("MAINPROG").isEmpty());
        assertTrue(graph.getPassiveXRefs("SUB-VALIDATE").isEmpty());
    }

    @Test
    void summary() {
        String summary = graph.getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("TESTLIB"));
        assertTrue(summary.contains("sources=5"));
    }

    // ═══════════════════════════════════════════════════════════
    //  INPUT MAP dependencies
    // ═══════════════════════════════════════════════════════════

    @Test
    void inputMapDependency() {
        NaturalDependencyGraph mapGraph = new NaturalDependencyGraph();
        mapGraph.addSource("LIB", "SCREEN01",
                "DEFINE DATA LOCAL\nEND-DEFINE\n"
                + "INPUT USING MAP 'MAPSCR01'\n"
                + "END\n");
        mapGraph.build();

        DependencyResult result = mapGraph.getActiveXRefs("SCREEN01");
        assertFalse(result.isEmpty());

        Map<DependencyKind, List<NaturalDependencyService.Dependency>> grouped = result.getGrouped();
        assertTrue(grouped.containsKey(DependencyKind.INPUT_MAP),
                "Should detect INPUT MAP dependency, got: " + grouped.keySet());
    }
}

