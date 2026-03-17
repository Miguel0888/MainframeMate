package de.bund.zrb.service;

import de.bund.zrb.service.NaturalDependencyService.Dependency;
import de.bund.zrb.service.NaturalDependencyService.DependencyKind;
import de.bund.zrb.service.NaturalDependencyGraph.CallerInfo;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LuceneDependencyIndex} — Lucene-based caching of dependency graphs.
 */
class LuceneDependencyIndexTest {

    private LuceneDependencyIndex index;

    @BeforeEach
    void setUp() {
        // Use in-memory directory for tests
        index = new LuceneDependencyIndex(new ByteBuffersDirectory());
    }

    @AfterEach
    void tearDown() {
        index.close();
    }

    @Test
    void storeAndRestoreGraph() {
        // Build a simple graph
        NaturalDependencyGraph graph = new NaturalDependencyGraph();
        graph.setLibrary("TESTLIB");
        graph.addCachedDependencies("PROG1", java.util.Arrays.asList(
                new Dependency(DependencyKind.CALLNAT, "SUBPROG1", 10, "CALLNAT SUBPROG1", null),
                new Dependency(DependencyKind.FETCH, "PROG2", 20, "FETCH PROG2", null),
                new Dependency(DependencyKind.USING, "MYDATA", 5, "LOCAL USING MYDATA", "LOCAL")
        ));
        graph.addCachedDependencies("PROG2", java.util.Arrays.asList(
                new Dependency(DependencyKind.CALLNAT, "SUBPROG1", 15, "CALLNAT SUBPROG1", null)
        ));
        graph.build();

        // Store in Lucene
        index.storeGraph(graph);

        // Verify library is cached
        assertTrue(index.hasLibrary("TESTLIB"));
        assertFalse(index.hasLibrary("OTHER"));

        // Restore
        NaturalDependencyGraph restored = index.restoreGraph("TESTLIB");
        assertNotNull(restored);
        assertTrue(restored.isBuilt());
        assertEquals("TESTLIB", restored.getLibrary());

        // Check active XRefs
        assertEquals(3, restored.getActiveXRefs("PROG1").getTotalCount());
        assertEquals(1, restored.getActiveXRefs("PROG2").getTotalCount());

        // Check passive XRefs (SUBPROG1 is called by both PROG1 and PROG2)
        List<CallerInfo> subprog1Callers = restored.getPassiveXRefs("SUBPROG1");
        assertEquals(2, subprog1Callers.size());

        // Check PROG2 is called by PROG1 (via FETCH)
        List<CallerInfo> prog2Callers = restored.getPassiveXRefs("PROG2");
        assertEquals(1, prog2Callers.size());
        assertEquals("PROG1", prog2Callers.get(0).getCallerName());
        assertEquals(DependencyKind.FETCH, prog2Callers.get(0).getReferenceKind());
    }

    @Test
    void findCallersAcrossGraph() {
        NaturalDependencyGraph graph = new NaturalDependencyGraph();
        graph.setLibrary("LIB1");
        graph.addCachedDependencies("CALLER1", java.util.Arrays.asList(
                new Dependency(DependencyKind.CALLNAT, "TARGET", 10, null, null)
        ));
        graph.addCachedDependencies("CALLER2", java.util.Arrays.asList(
                new Dependency(DependencyKind.CALLNAT, "TARGET", 20, null, null),
                new Dependency(DependencyKind.FETCH, "OTHER", 30, null, null)
        ));
        graph.build();
        index.storeGraph(graph);

        // Find callers of TARGET
        List<CallerInfo> callers = index.findCallers("TARGET", "LIB1");
        assertEquals(2, callers.size());

        // Find callers of OTHER
        List<CallerInfo> otherCallers = index.findCallers("OTHER", "LIB1");
        assertEquals(1, otherCallers.size());
        assertEquals("CALLER2", otherCallers.get(0).getCallerName());
    }

    @Test
    void fullTextSearch() {
        NaturalDependencyGraph graph = new NaturalDependencyGraph();
        graph.setLibrary("SEARCHLIB");
        graph.addCachedDependencies("MAINPROG", java.util.Arrays.asList(
                new Dependency(DependencyKind.CALLNAT, "HELPER1", 10, null, null),
                new Dependency(DependencyKind.USING, "DATAAREA", 5, null, "LOCAL")
        ));
        graph.build();
        index.storeGraph(graph);

        // Search for CALLNAT
        List<LuceneDependencyIndex.DependencySearchResult> results = index.search("CALLNAT", 10);
        assertFalse(results.isEmpty());
        assertEquals("SEARCHLIB", results.get(0).getLibrary());

        // Search for specific object
        results = index.search("HELPER1", 10);
        assertFalse(results.isEmpty());
    }

    @Test
    void replaceGraphOnRestore() {
        // Store first version
        NaturalDependencyGraph graph1 = new NaturalDependencyGraph();
        graph1.setLibrary("MYLIB");
        graph1.addCachedDependencies("PROG1", java.util.Arrays.asList(
                new Dependency(DependencyKind.CALLNAT, "OLD_TARGET", 10, null, null)
        ));
        graph1.build();
        index.storeGraph(graph1);

        // Store second version (should replace)
        NaturalDependencyGraph graph2 = new NaturalDependencyGraph();
        graph2.setLibrary("MYLIB");
        graph2.addCachedDependencies("PROG1", java.util.Arrays.asList(
                new Dependency(DependencyKind.CALLNAT, "NEW_TARGET", 10, null, null)
        ));
        graph2.build();
        index.storeGraph(graph2);

        // Restore — should get second version
        NaturalDependencyGraph restored = index.restoreGraph("MYLIB");
        assertNotNull(restored);
        assertEquals(1, restored.getActiveXRefs("PROG1").getTotalCount());
        assertEquals("NEW_TARGET",
                restored.getActiveXRefs("PROG1").getAllDependencies().get(0).getTargetName());
    }

    @Test
    void listCachedLibraries() {
        assertTrue(index.listCachedLibraries().isEmpty());

        NaturalDependencyGraph graph = new NaturalDependencyGraph();
        graph.setLibrary("LIB_A");
        graph.build();
        index.storeGraph(graph);

        NaturalDependencyGraph graph2 = new NaturalDependencyGraph();
        graph2.setLibrary("LIB_B");
        graph2.build();
        index.storeGraph(graph2);

        List<String> libs = index.listCachedLibraries();
        assertEquals(2, libs.size());
        assertTrue(libs.contains("LIB_A"));
        assertTrue(libs.contains("LIB_B"));
    }
}

