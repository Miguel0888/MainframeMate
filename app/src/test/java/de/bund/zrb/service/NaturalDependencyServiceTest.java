package de.bund.zrb.service;

import de.bund.zrb.service.NaturalDependencyService.Dependency;
import de.bund.zrb.service.NaturalDependencyService.DependencyKind;
import de.bund.zrb.service.NaturalDependencyService.DependencyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link NaturalDependencyService}.
 */
class NaturalDependencyServiceTest {

    private NaturalDependencyService service;

    @BeforeEach
    void setUp() {
        service = new NaturalDependencyService();
    }

    @Test
    void analyzeEmptySource() {
        DependencyResult result = service.analyze("", "EMPTY");
        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalCount());
    }

    @Test
    void analyzeNullSource() {
        DependencyResult result = service.analyze(null, "NULL");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void analyzeCallnat() {
        String source = "DEFINE DATA\n"
                + "LOCAL\n"
                + "END-DEFINE\n"
                + "CALLNAT 'MYSUBPROG' #PARAM1\n"
                + "END\n";

        DependencyResult result = service.analyze(source, "TESTPROG");
        assertFalse(result.isEmpty());

        Map<DependencyKind, List<Dependency>> grouped = result.getGrouped();
        assertTrue(grouped.containsKey(DependencyKind.CALLNAT));
        assertEquals(1, grouped.get(DependencyKind.CALLNAT).size());
        assertEquals("MYSUBPROG", grouped.get(DependencyKind.CALLNAT).get(0).getTargetName());
    }

    @Test
    void analyzeFetch() {
        String source = "DEFINE DATA LOCAL\nEND-DEFINE\n"
                + "FETCH RETURN 'OTHERPROG'\n"
                + "END\n";

        DependencyResult result = service.analyze(source, "TESTPROG");
        assertFalse(result.isEmpty());

        Map<DependencyKind, List<Dependency>> grouped = result.getGrouped();
        assertTrue(grouped.containsKey(DependencyKind.FETCH));
        assertEquals("OTHERPROG", grouped.get(DependencyKind.FETCH).get(0).getTargetName());
    }

    @Test
    void analyzePerform() {
        String source = "DEFINE DATA LOCAL\nEND-DEFINE\n"
                + "PERFORM CALC-TOTAL\n"
                + "DEFINE SUBROUTINE CALC-TOTAL\n"
                + "END-SUBROUTINE\n"
                + "END\n";

        DependencyResult result = service.analyze(source, "TESTPROG");

        Map<DependencyKind, List<Dependency>> grouped = result.getGrouped();
        assertTrue(grouped.containsKey(DependencyKind.PERFORM));
        assertEquals("CALC-TOTAL", grouped.get(DependencyKind.PERFORM).get(0).getTargetName());
    }

    @Test
    void analyzeInclude() {
        String source = "DEFINE DATA LOCAL\nEND-DEFINE\n"
                + "INCLUDE CCOPY01\n"
                + "END\n";

        DependencyResult result = service.analyze(source, "TESTPROG");

        Map<DependencyKind, List<Dependency>> grouped = result.getGrouped();
        assertTrue(grouped.containsKey(DependencyKind.INCLUDE));
        assertEquals("CCOPY01", grouped.get(DependencyKind.INCLUDE).get(0).getTargetName());
    }

    @Test
    void analyzeUsing() {
        String source = "DEFINE DATA\n"
                + "LOCAL USING MYLDA\n"
                + "PARAMETER USING MYPDA\n"
                + "END-DEFINE\n"
                + "END\n";

        DependencyResult result = service.analyze(source, "TESTPROG");

        Map<DependencyKind, List<Dependency>> grouped = result.getGrouped();
        assertTrue(grouped.containsKey(DependencyKind.USING));
        List<Dependency> usings = grouped.get(DependencyKind.USING);
        assertEquals(2, usings.size());
        assertEquals("MYLDA", usings.get(0).getTargetName());
        assertEquals("MYPDA", usings.get(1).getTargetName());
    }

    @Test
    void analyzeDbAccess() {
        String source = "DEFINE DATA LOCAL\nEND-DEFINE\n"
                + "READ EMPLOYEES BY NAME\n"
                + "FIND VEHICLES WITH OWNER = #OWNER\n"
                + "END\n";

        DependencyResult result = service.analyze(source, "TESTPROG");

        Map<DependencyKind, List<Dependency>> grouped = result.getGrouped();
        assertTrue(grouped.containsKey(DependencyKind.DB_ACCESS));
        List<Dependency> dbOps = grouped.get(DependencyKind.DB_ACCESS);
        assertEquals(2, dbOps.size());
    }

    @Test
    void analyzeComplexProgram() {
        String source = "** TESTPROG - Complex program with multiple dependencies\n"
                + "DEFINE DATA\n"
                + "LOCAL USING L-COMMON\n"
                + "PARAMETER USING P-PARAMS\n"
                + "LOCAL\n"
                + "01 #I (N3)\n"
                + "01 #RESULT (A100)\n"
                + "END-DEFINE\n"
                + "CALLNAT 'SUB-VALIDATE' #I\n"
                + "CALLNAT 'SUB-PROCESS' #RESULT\n"
                + "PERFORM CHECK-STATUS\n"
                + "INCLUDE CCSTANDARD\n"
                + "READ EMPLOYEES BY NAME\n"
                + "  CALLNAT 'SUB-FORMAT' #RESULT\n"
                + "END-READ\n"
                + "FETCH RETURN 'MAINMENU'\n"
                + "DEFINE SUBROUTINE CHECK-STATUS\n"
                + "END-SUBROUTINE\n"
                + "END\n";

        DependencyResult result = service.analyze(source, "TESTPROG");

        // Verify total count
        assertTrue(result.getTotalCount() >= 8, "Expected at least 8 dependencies, got " + result.getTotalCount());

        // Verify all kinds are present
        Map<DependencyKind, List<Dependency>> grouped = result.getGrouped();
        assertTrue(grouped.containsKey(DependencyKind.CALLNAT), "Should have CALLNAT dependencies");
        assertTrue(grouped.containsKey(DependencyKind.FETCH), "Should have FETCH dependencies");
        assertTrue(grouped.containsKey(DependencyKind.PERFORM), "Should have PERFORM dependencies");
        assertTrue(grouped.containsKey(DependencyKind.INCLUDE), "Should have INCLUDE dependencies");
        assertTrue(grouped.containsKey(DependencyKind.USING), "Should have USING dependencies");
        assertTrue(grouped.containsKey(DependencyKind.DB_ACCESS), "Should have DB_ACCESS dependencies");

        // Verify CALLNAT count (3 calls)
        assertEquals(3, grouped.get(DependencyKind.CALLNAT).size());

        // Verify unique external call targets
        List<String> extTargets = result.getExternalCallTargets();
        assertTrue(extTargets.contains("SUB-VALIDATE"));
        assertTrue(extTargets.contains("SUB-PROCESS"));
        assertTrue(extTargets.contains("SUB-FORMAT"));
        assertTrue(extTargets.contains("MAINMENU"));

        // Verify data area targets
        List<String> dataTargets = result.getDataAreaTargets();
        assertTrue(dataTargets.contains("L-COMMON"));
        assertTrue(dataTargets.contains("P-PARAMS"));

        // Verify copycode targets
        List<String> copycodes = result.getCopycodeTargets();
        assertTrue(copycodes.contains("CCSTANDARD"));
    }

    @Test
    void dependencyDisplayText() {
        Dependency dep = new Dependency(DependencyKind.CALLNAT, "MYSUBPROG", 42, "CALLNAT 'MYSUBPROG'", null);
        String text = dep.getDisplayText();
        assertTrue(text.contains("MYSUBPROG"));
        assertTrue(text.contains("42"));
    }

    @Test
    void dependencyDisplayTextWithDetail() {
        Dependency dep = new Dependency(DependencyKind.USING, "MYLDA", 5, "LOCAL USING MYLDA", "LOCAL");
        String text = dep.getDisplayText();
        assertTrue(text.contains("MYLDA"));
        assertTrue(text.contains("LOCAL"));
        assertTrue(text.contains("5"));
    }
}

