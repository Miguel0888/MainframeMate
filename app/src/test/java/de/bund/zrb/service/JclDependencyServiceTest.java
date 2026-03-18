package de.bund.zrb.service;

import de.bund.zrb.service.JclDependencyService.JclCallNode;
import de.bund.zrb.service.JclDependencyService.JclDependency;
import de.bund.zrb.service.JclDependencyService.JclDependencyKind;
import de.bund.zrb.service.JclDependencyService.JclDependencyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JclDependencyService} — especially Natural program extraction
 * from PARM/ZPARM with STACK=(LOGON library;program).
 */
class JclDependencyServiceTest {

    private JclDependencyService service;

    @BeforeEach
    void setUp() {
        service = JclDependencyService.getInstance();
    }

    // ── Basic sanity ──

    @Test
    void analyzeEmptyReturnsEmpty() {
        JclDependencyResult result = service.analyze("", "EMPTY");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void analyzeNullReturnsEmpty() {
        JclDependencyResult result = service.analyze(null, "NULL");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ── Natural program extraction from ZPARM ──

    @Test
    void extractNaturalFromZparm() {
        String jcl =
                "//MYJOB   JOB (ACCT),'NATURAL',CLASS=A\n" +
                "//NATURAL1 EXEC NAT1,ZPARM='MADIO=0,MAXCL=0,STACK=(LOGON ABAK-M;ZDALXX0P)'\n";

        JclDependencyResult result = service.analyze(jcl, "TEST");
        assertFalse(result.isEmpty());

        Map<JclDependencyKind, List<JclDependency>> grouped = result.getGrouped();
        List<JclDependency> natDeps = grouped.get(JclDependencyKind.NATURAL_PROGRAM);
        assertNotNull(natDeps, "Should have NATURAL_PROGRAM dependencies");
        assertEquals(1, natDeps.size(), "Exactly one Natural program per EXEC step");

        JclDependency dep = natDeps.get(0);
        assertEquals("ZDALXX0P", dep.getTargetName());
        assertEquals("ABAK-M", dep.getNaturalLibrary());
        assertEquals("ZDALXX0P", dep.getNaturalProgram());
        assertTrue(dep.getDetail().contains("ABAK-M"));
    }

    @Test
    void extractNaturalFromParm() {
        String jcl =
                "//MYJOB   JOB (ACCT),'NATURAL',CLASS=A\n" +
                "//STEP1   EXEC PGM=NATBATCH,PARM='STACK=(LOGON MYLIB;MYPROG)'\n";

        JclDependencyResult result = service.analyze(jcl, "TEST");

        Map<JclDependencyKind, List<JclDependency>> grouped = result.getGrouped();
        List<JclDependency> natDeps = grouped.get(JclDependencyKind.NATURAL_PROGRAM);
        assertNotNull(natDeps, "Should extract Natural program from PARM");
        assertEquals(1, natDeps.size());
        assertEquals("MYPROG", natDeps.get(0).getTargetName());
        assertEquals("MYLIB", natDeps.get(0).getNaturalLibrary());
    }

    @Test
    void extractNaturalCaseInsensitive() {
        String jcl =
                "//MYJOB   JOB (ACCT),'TEST',CLASS=A\n" +
                "//STEP1   EXEC PGM=NATBATCH,ZPARM='stack=(logon mylib;myprog)'\n";

        JclDependencyResult result = service.analyze(jcl, "TEST");

        List<JclDependency> natDeps = result.getGrouped().get(JclDependencyKind.NATURAL_PROGRAM);
        assertNotNull(natDeps, "Case-insensitive matching should work");
        assertEquals(1, natDeps.size());
        // Should be uppercased
        assertEquals("MYPROG", natDeps.get(0).getTargetName());
        assertEquals("MYLIB", natDeps.get(0).getNaturalLibrary());
    }

    @Test
    void noNaturalWhenNoStack() {
        String jcl =
                "//MYJOB   JOB (ACCT),'TEST',CLASS=A\n" +
                "//STEP1   EXEC PGM=IDCAMS\n" +
                "//SYSIN   DD *\n" +
                "  LISTCAT ENTRIES('MY.DATASET')\n" +
                "/*\n";

        JclDependencyResult result = service.analyze(jcl, "TEST");

        List<JclDependency> natDeps = result.getGrouped().get(JclDependencyKind.NATURAL_PROGRAM);
        assertNull(natDeps, "IDCAMS step should not have Natural dependencies");
    }

    // ── Call hierarchy with Natural nodes ──

    @Test
    void callHierarchyContainsNaturalNode() {
        String jcl =
                "//MYJOB   JOB (ACCT),'NATURAL',CLASS=A\n" +
                "//NATURAL1 EXEC NAT1,ZPARM='MADIO=0,STACK=(LOGON ABAK-M;ZDALXX0P)'\n";

        List<JclCallNode> roots = service.buildCallHierarchy(jcl, "TEST");
        assertFalse(roots.isEmpty());

        // Find the Natural node in the hierarchy
        JclCallNode natNode = findNaturalNode(roots);
        assertNotNull(natNode, "Call hierarchy should contain a Natural program node");
        assertTrue(natNode.getDisplayText().contains("ZDALXX0P"));
        assertNotNull(natNode.getNaturalRef());
        assertTrue(natNode.getNaturalRef().contains("ABAK-M"));
        assertTrue(natNode.getNaturalRef().contains("ZDALXX0P"));
    }

    @Test
    void regularProgramDependency() {
        String jcl =
                "//MYJOB   JOB (ACCT),'TEST',CLASS=A\n" +
                "//STEP1   EXEC PGM=IDCAMS\n";

        JclDependencyResult result = service.analyze(jcl, "TEST");

        List<JclDependency> pgmDeps = result.getGrouped().get(JclDependencyKind.PROGRAM);
        assertNotNull(pgmDeps);
        assertEquals(1, pgmDeps.size(), "PGM should appear exactly once");
        assertEquals("IDCAMS", pgmDeps.get(0).getTargetName());
    }

    // ── Helpers ──

    private JclCallNode findNaturalNode(List<JclCallNode> nodes) {
        for (JclCallNode node : nodes) {
            if (node.getNaturalRef() != null) return node;
            JclCallNode found = findNaturalNode(node.getChildren());
            if (found != null) return found;
        }
        return null;
    }
}

