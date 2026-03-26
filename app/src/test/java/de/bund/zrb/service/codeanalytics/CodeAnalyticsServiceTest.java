package de.bund.zrb.service.codeanalytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CodeAnalyticsService} and language-specific extractors.
 */
class CodeAnalyticsServiceTest {

    private CodeAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = CodeAnalyticsService.getInstance();
    }

    // ═══════════════════════════════════════════════════════════
    //  Language detection
    // ═══════════════════════════════════════════════════════════

    @Test
    void detectNaturalSource() {
        String source = "DEFINE DATA\nLOCAL\n1 #VAR (A10)\nEND-DEFINE\nCALLNAT 'MYSUB' #VAR\nEND\n";
        assertEquals(SourceLanguage.NATURAL, service.detectLanguage(source));
    }

    @Test
    void detectCobolSource() {
        String source = "       IDENTIFICATION DIVISION.\n       PROGRAM-ID. TESTPROG.\n       PROCEDURE DIVISION.\n";
        assertEquals(SourceLanguage.COBOL, service.detectLanguage(source));
    }

    @Test
    void detectJclSource() {
        String source = "//MYJOB   JOB (ACCT),'TEST',CLASS=A\n//STEP1   EXEC PGM=IDCAMS\n//SYSIN   DD *\n";
        assertEquals(SourceLanguage.JCL, service.detectLanguage(source));
    }

    @Test
    void detectUnknownForEmpty() {
        assertEquals(SourceLanguage.UNKNOWN, service.detectLanguage(""));
        assertEquals(SourceLanguage.UNKNOWN, service.detectLanguage(null));
    }

    // ═══════════════════════════════════════════════════════════
    //  Natural external calls
    // ═══════════════════════════════════════════════════════════

    @Test
    void naturalCallnatExtracted() {
        String source = "DEFINE DATA\nLOCAL\nEND-DEFINE\nCALLNAT 'SUBPROG1' #PARAM\nCALLNAT 'SUBPROG2'\nEND\n";
        List<ExternalCall> calls = service.extractExternalCalls(source, "MAIN", SourceLanguage.NATURAL);
        assertEquals(2, calls.size());
        assertEquals("SUBPROG1", calls.get(0).getTargetName());
        assertEquals("CALLNAT", calls.get(0).getCallType());
        assertEquals("SUBPROG2", calls.get(1).getTargetName());
    }

    @Test
    void naturalFetchExtracted() {
        String source = "DEFINE DATA LOCAL\nEND-DEFINE\nFETCH RETURN 'MYPROG'\nEND\n";
        List<ExternalCall> calls = service.extractExternalCalls(source, "MAIN", SourceLanguage.NATURAL);
        assertEquals(1, calls.size());
        assertEquals("MYPROG", calls.get(0).getTargetName());
        assertEquals("FETCH", calls.get(0).getCallType());
    }

    @Test
    void naturalPerformNotExtracted() {
        // PERFORM calls inline subroutines — should NOT be external
        String source = "DEFINE DATA LOCAL\nEND-DEFINE\nPERFORM CALC-TOTAL\n"
                + "DEFINE SUBROUTINE CALC-TOTAL\nEND-SUBROUTINE\nEND\n";
        List<ExternalCall> calls = service.extractExternalCalls(source, "MAIN", SourceLanguage.NATURAL);
        assertTrue(calls.isEmpty(), "PERFORM should not be treated as external call");
    }

    @Test
    void naturalDuplicateCallsDeduped() {
        String source = "DEFINE DATA LOCAL\nEND-DEFINE\n"
                + "CALLNAT 'SUBPROG1' #A\nCALLNAT 'SUBPROG1' #B\nEND\n";
        List<ExternalCall> calls = service.extractExternalCalls(source, "MAIN", SourceLanguage.NATURAL);
        assertEquals(1, calls.size(), "Duplicate CALLNAT targets should be deduped");
    }

    // ═══════════════════════════════════════════════════════════
    //  COBOL external calls
    // ═══════════════════════════════════════════════════════════

    @Test
    void cobolCallExtracted() {
        String source = "       IDENTIFICATION DIVISION.\n       PROGRAM-ID. TESTPROG.\n"
                + "       PROCEDURE DIVISION.\n       MAIN-PARA.\n"
                + "           CALL 'SUBPROG1'\n           CALL 'SUBPROG2'\n"
                + "           STOP RUN.\n";
        List<ExternalCall> calls = service.extractExternalCalls(source, "TESTPROG", SourceLanguage.COBOL);
        assertEquals(2, calls.size());
        assertEquals("SUBPROG1", calls.get(0).getTargetName());
        assertEquals("CALL", calls.get(0).getCallType());
    }

    @Test
    void cobolPerformNotExtracted() {
        String source = "       IDENTIFICATION DIVISION.\n       PROGRAM-ID. TESTPROG.\n"
                + "       PROCEDURE DIVISION.\n       MAIN-PARA.\n"
                + "           PERFORM SUB-PARA.\n       SUB-PARA.\n"
                + "           DISPLAY 'HELLO'.\n";
        List<ExternalCall> calls = service.extractExternalCalls(source, "TESTPROG", SourceLanguage.COBOL);
        assertTrue(calls.isEmpty(), "PERFORM should not be treated as external call in COBOL");
    }

    // ═══════════════════════════════════════════════════════════
    //  JCL external calls
    // ═══════════════════════════════════════════════════════════

    @Test
    void jclExecPgmExtracted() {
        String source = "//MYJOB   JOB (ACCT),'TEST',CLASS=A\n"
                + "//STEP1   EXEC PGM=IDCAMS\n"
                + "//STEP2   EXEC PGM=IEFBR14\n";
        List<ExternalCall> calls = service.extractExternalCalls(source, "MYJOB", SourceLanguage.JCL);
        assertTrue(calls.size() >= 2);
        boolean foundIdcams = false, foundIefbr14 = false;
        for (ExternalCall call : calls) {
            if ("IDCAMS".equals(call.getTargetName())) foundIdcams = true;
            if ("IEFBR14".equals(call.getTargetName())) foundIefbr14 = true;
        }
        assertTrue(foundIdcams, "IDCAMS should be extracted");
        assertTrue(foundIefbr14, "IEFBR14 should be extracted");
    }

    // ═══════════════════════════════════════════════════════════
    //  Call tree
    // ═══════════════════════════════════════════════════════════

    @Test
    void buildCallTreeWithoutResolver() {
        String source = "DEFINE DATA LOCAL\nEND-DEFINE\n"
                + "CALLNAT 'SUB1'\nCALLNAT 'SUB2'\nEND\n";
        CallTreeNode tree = service.buildCallTree(source, "MAIN", SourceLanguage.NATURAL, 2, null);
        assertNotNull(tree);
        assertEquals("MAIN", tree.getName());
        assertEquals(2, tree.getChildren().size());
        assertEquals("SUB1", tree.getChildren().get(0).getName());
        assertEquals("SUB2", tree.getChildren().get(1).getName());
        // Without resolver, children should have no children
        assertTrue(tree.getChildren().get(0).isLeaf());
    }

    @Test
    void buildCallTreeWithResolver() {
        String mainSource = "DEFINE DATA LOCAL\nEND-DEFINE\nCALLNAT 'SUB1'\nEND\n";
        String sub1Source = "DEFINE DATA LOCAL\nEND-DEFINE\nCALLNAT 'SUB2'\nEND\n";

        SourceResolver resolver = new SourceResolver() {
            @Override
            public String resolve(String targetName) {
                if ("SUB1".equalsIgnoreCase(targetName)) return sub1Source;
                return null;
            }
        };

        CallTreeNode tree = service.buildCallTree(mainSource, "MAIN", SourceLanguage.NATURAL, 3, resolver);
        assertNotNull(tree);
        assertEquals(1, tree.getChildren().size());

        CallTreeNode sub1Node = tree.getChildren().get(0);
        assertEquals("SUB1", sub1Node.getName());
        assertFalse(sub1Node.isLeaf(), "SUB1 should have children (SUB2)");
        assertEquals(1, sub1Node.getChildren().size());
        assertEquals("SUB2", sub1Node.getChildren().get(0).getName());
    }

    @Test
    void buildCallTreeDetectsRecursion() {
        String mainSource = "DEFINE DATA LOCAL\nEND-DEFINE\nCALLNAT 'SUB1'\nEND\n";
        String sub1Source = "DEFINE DATA LOCAL\nEND-DEFINE\nCALLNAT 'MAIN'\nEND\n";

        SourceResolver resolver = new SourceResolver() {
            @Override
            public String resolve(String targetName) {
                if ("SUB1".equalsIgnoreCase(targetName)) return sub1Source;
                return null;
            }
        };

        CallTreeNode tree = service.buildCallTree(mainSource, "MAIN", SourceLanguage.NATURAL, 5, resolver);
        assertNotNull(tree);
        CallTreeNode sub1Node = tree.getChildren().get(0);
        assertEquals("SUB1", sub1Node.getName());

        // SUB1 calls MAIN → recursive
        if (!sub1Node.isLeaf()) {
            CallTreeNode recursiveMain = sub1Node.getChildren().get(0);
            assertEquals("MAIN", recursiveMain.getName());
            assertTrue(recursiveMain.isRecursive(), "MAIN should be flagged as recursive");
        }
    }

    @Test
    void buildCallTreeRespectsMaxDepth() {
        String mainSource = "DEFINE DATA LOCAL\nEND-DEFINE\nCALLNAT 'A'\nEND\n";
        String aSource = "DEFINE DATA LOCAL\nEND-DEFINE\nCALLNAT 'B'\nEND\n";
        String bSource = "DEFINE DATA LOCAL\nEND-DEFINE\nCALLNAT 'C'\nEND\n";

        SourceResolver resolver = new SourceResolver() {
            @Override
            public String resolve(String targetName) {
                if ("A".equalsIgnoreCase(targetName)) return aSource;
                if ("B".equalsIgnoreCase(targetName)) return bSource;
                return null;
            }
        };

        // Depth 1: only direct calls
        CallTreeNode tree1 = service.buildCallTree(mainSource, "MAIN", SourceLanguage.NATURAL, 1, resolver);
        assertEquals(1, tree1.getChildren().size());
        assertTrue(tree1.getChildren().get(0).isLeaf(), "At depth 1, children should not be expanded");

        // Depth 2: A → B but not C
        CallTreeNode tree2 = service.buildCallTree(mainSource, "MAIN", SourceLanguage.NATURAL, 2, resolver);
        assertFalse(tree2.getChildren().get(0).isLeaf());
        CallTreeNode aNode = tree2.getChildren().get(0);
        assertEquals("B", aNode.getChildren().get(0).getName());
        assertTrue(aNode.getChildren().get(0).isLeaf(), "At depth 2, B should not have children");
    }

    @Test
    void autoDetectLanguageForCalls() {
        String source = "DEFINE DATA LOCAL\nEND-DEFINE\nCALLNAT 'MYSUB'\nEND\n";
        List<ExternalCall> calls = service.extractExternalCalls(source, "MAIN", SourceLanguage.UNKNOWN);
        assertEquals(1, calls.size());
        assertEquals("MYSUB", calls.get(0).getTargetName());
    }
}

