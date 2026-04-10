package de.bund.zrb.ui.mermaid;

import de.bund.zrb.jcl.model.JclElement;
import de.bund.zrb.jcl.model.JclElementType;
import de.bund.zrb.jcl.model.JclOutlineModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link OutlineToMermaidConverter}.
 */
class OutlineToMermaidConverterTest {

    @Test
    void nullModel_returnsNull() {
        assertNull(OutlineToMermaidConverter.convert(null));
    }

    @Test
    void emptyModel_returnsNull() {
        JclOutlineModel model = new JclOutlineModel();
        model.setLanguage(JclOutlineModel.Language.JCL);
        assertNull(OutlineToMermaidConverter.convert(model));
    }

    @Test
    void jclWithJobAndSteps_generatesFlowchart() {
        JclOutlineModel model = new JclOutlineModel();
        model.setLanguage(JclOutlineModel.Language.JCL);

        JclElement job = new JclElement(JclElementType.JOB, "MYJOB", 1, "//MYJOB JOB ...");
        model.addElement(job);

        JclElement step1 = new JclElement(JclElementType.EXEC, "STEP1", 2, "//STEP1 EXEC PGM=IEFBR14");
        step1.addParameter("PGM", "IEFBR14");
        model.addElement(step1);

        JclElement dd = new JclElement(JclElementType.DD, "SYSOUT", 3, "//SYSOUT DD SYSOUT=*");
        step1.addChild(dd);

        JclElement step2 = new JclElement(JclElementType.EXEC, "STEP2", 4, "//STEP2 EXEC PGM=IEBGENER");
        step2.addParameter("PGM", "IEBGENER");
        model.addElement(step2);

        String result = OutlineToMermaidConverter.convert(model);
        assertNotNull(result);
        assertTrue(result.startsWith("flowchart TD"));
        assertTrue(result.contains("MYJOB"));
        assertTrue(result.contains("STEP1"));
        assertTrue(result.contains("PGM=IEFBR14"));
        assertTrue(result.contains("STEP2"));
        assertTrue(result.contains("PGM=IEBGENER"));
        assertTrue(result.contains("SYSOUT"));
        // Ensure arrows exist
        assertTrue(result.contains("-->"));
    }

    @Test
    void cobolWithDivisionsAndParagraphs_generatesFlowchart() {
        JclOutlineModel model = new JclOutlineModel();
        model.setLanguage(JclOutlineModel.Language.COBOL);

        model.addElement(new JclElement(JclElementType.PROGRAM_ID, "TESTPROG", 1, "PROGRAM-ID. TESTPROG."));
        model.addElement(new JclElement(JclElementType.DIVISION, "DATA DIVISION", 3, "DATA DIVISION."));
        model.addElement(new JclElement(JclElementType.DIVISION, "PROCEDURE DIVISION", 10, "PROCEDURE DIVISION."));
        model.addElement(new JclElement(JclElementType.PARAGRAPH, "MAIN-LOGIC", 12, "MAIN-LOGIC."));
        model.addElement(new JclElement(JclElementType.PARAGRAPH, "INIT-ROUTINE", 20, "INIT-ROUTINE."));

        JclElement perform = new JclElement(JclElementType.PERFORM_STMT, "PERFORM", 15, "PERFORM INIT-ROUTINE");
        perform.addParameter("TARGET", "INIT-ROUTINE");
        model.addElement(perform);

        String result = OutlineToMermaidConverter.convert(model);
        assertNotNull(result);
        assertTrue(result.startsWith("flowchart TD"));
        assertTrue(result.contains("TESTPROG"));
        assertTrue(result.contains("DATA DIVISION"));
        assertTrue(result.contains("MAIN-LOGIC"));
        assertTrue(result.contains("INIT-ROUTINE"));
        assertTrue(result.contains("PERFORM"));
    }

    @Test
    void naturalWithCallnatAndDb_generatesFlowchart() {
        JclOutlineModel model = new JclOutlineModel();
        model.setLanguage(JclOutlineModel.Language.NATURAL);
        model.setSourceName("TESTPGM");

        model.addElement(new JclElement(JclElementType.NAT_PROGRAM, "TESTPGM", 1, "DEFINE DATA"));
        model.addElement(new JclElement(JclElementType.NAT_DEFINE_DATA, "DEFINE DATA", 2, "DEFINE DATA"));
        model.addElement(new JclElement(JclElementType.NAT_LOCAL, "LOCAL", 3, "LOCAL"));

        JclElement callnat = new JclElement(JclElementType.NAT_CALLNAT, "EXTPROG", 10, "CALLNAT 'EXTPROG'");
        callnat.addParameter("TARGET", "EXTPROG");
        model.addElement(callnat);

        JclElement read = new JclElement(JclElementType.NAT_READ, "READ", 15, "READ EMPLOYEES");
        read.addParameter("FILE", "EMPLOYEES");
        model.addElement(read);

        String result = OutlineToMermaidConverter.convert(model);
        assertNotNull(result);
        assertTrue(result.startsWith("flowchart TD"));
        assertTrue(result.contains("TESTPGM"));
        assertTrue(result.contains("DEFINE DATA"));
        assertTrue(result.contains("LOCAL"));
        assertTrue(result.contains("EXTPROG"));
        assertTrue(result.contains("CALLNAT"));
        assertTrue(result.contains("EMPLOYEES"));
        assertTrue(result.contains("READ"));
    }

    @Test
    void jclDDStatementsShownAsChildren() {
        JclOutlineModel model = new JclOutlineModel();
        model.setLanguage(JclOutlineModel.Language.JCL);

        JclElement step = new JclElement(JclElementType.EXEC, "SORT", 1, "//SORT EXEC PGM=SORT");
        step.addParameter("PGM", "SORT");
        model.addElement(step);

        JclElement dd1 = new JclElement(JclElementType.DD, "SORTIN", 2, "//SORTIN DD DSN=MY.INPUT");
        dd1.addParameter("DSN", "MY.INPUT.DATASET");
        step.addChild(dd1);

        JclElement dd2 = new JclElement(JclElementType.DD, "SORTOUT", 3, "//SORTOUT DD DSN=MY.OUTPUT");
        dd2.addParameter("DSN", "MY.OUTPUT.DATASET");
        step.addChild(dd2);

        String result = OutlineToMermaidConverter.convert(model);
        assertNotNull(result);
        assertTrue(result.contains("SORTIN"));
        assertTrue(result.contains("SORTOUT"));
        assertTrue(result.contains("MY.INPUT.DATASET"));
        assertTrue(result.contains("MY.OUTPUT.DATASET"));
    }

    // ═══════════════════════════════════════════════════════════
    //  Collapsed Flowchart — heuristic-based collapsing tests
    // ═══════════════════════════════════════════════════════════

    @Test
    void collapsedFlowchart_preservesStructuralOrder() {
        // IF → FOR → IF (must appear in this order, not as "2 Verzweigungen, 1 Schleife")
        JclOutlineModel model = new JclOutlineModel();
        model.setLanguage(JclOutlineModel.Language.NATURAL);
        model.setSourceName("ORDERPGM");

        model.addElement(new JclElement(JclElementType.NAT_PROGRAM, "ORDERPGM", 1, "DEFINE DATA"));
        model.addElement(new JclElement(JclElementType.NAT_IF_BLOCK, "A > 0", 10, "IF A > 0"));
        model.addElement(new JclElement(JclElementType.NAT_FOR, "#I", 20, "FOR #I = 1 TO 10"));
        model.addElement(new JclElement(JclElementType.NAT_IF_BLOCK, "B = 'X'", 30, "IF B = 'X'"));
        model.addElement(new JclElement(JclElementType.NAT_END, "END", 99, "END"));

        String result = OutlineToMermaidConverter.convert(model,
                OutlineToMermaidConverter.DiagramType.FLOWCHART, null, null, true);

        assertNotNull(result);
        assertTrue(result.startsWith("flowchart TD"), "Must produce flowchart TD");
        // The three structural elements must appear in document order
        int posIf1 = result.indexOf("A > 0");
        int posFor = result.indexOf("#I");
        int posIf2 = result.indexOf("B = ");
        assertTrue(posIf1 >= 0, "First IF must be present");
        assertTrue(posFor >= 0, "FOR must be present");
        assertTrue(posIf2 >= 0, "Second IF must be present");
        assertTrue(posIf1 < posFor, "IF(A>0) must come before FOR(#I)");
        assertTrue(posFor < posIf2, "FOR(#I) must come before IF(B='X')");
        // Must NOT contain the old count-based "Verzweigungen" label
        assertFalse(result.contains("Verzweigungen"));
        assertFalse(result.contains("Schleifen"));
    }

    @Test
    void collapsedFlowchart_collapsesFrequentPerforms() {
        // PERFORM XML-ZEILE called 5 times → should be collapsed, not shown 5 times
        JclOutlineModel model = new JclOutlineModel();
        model.setLanguage(JclOutlineModel.Language.NATURAL);
        model.setSourceName("XMLTEST");

        model.addElement(new JclElement(JclElementType.NAT_PROGRAM, "XMLTEST", 1, "DEFINE DATA"));
        model.addElement(new JclElement(JclElementType.NAT_IF_BLOCK, "#FLAG = TRUE", 10, "IF #FLAG = TRUE"));

        // Add 5 PERFORM XML-ZEILE calls (frequent!)
        for (int i = 0; i < 5; i++) {
            JclElement perf = new JclElement(JclElementType.NAT_PERFORM,
                    "XML-ZEILE", 20 + i, "PERFORM XML-ZEILE");
            perf.addParameter("TARGET", "XML-ZEILE");
            model.addElement(perf);
        }

        model.addElement(new JclElement(JclElementType.NAT_FOR, "#J", 30, "FOR #J = 1 TO 5"));
        model.addElement(new JclElement(JclElementType.NAT_END, "END", 99, "END"));

        String result = OutlineToMermaidConverter.convert(model,
                OutlineToMermaidConverter.DiagramType.FLOWCHART, null, null, true);

        assertNotNull(result);
        // "XML-ZEILE" should appear in a collapsed block annotation, not as 5 separate nodes
        assertTrue(result.contains("XML-ZEILE"), "Frequent target must be mentioned in collapsed block");
        // The collapsed block should show the multiplier
        assertTrue(result.contains("\u00D75") || result.contains("XML_ZEILE"),
                "Collapsed block should indicate call count");
        // Should contain the "📦" summary block indicator
        assertTrue(result.contains("\uD83D\uDCE6") || result.contains("Anweisung"),
                "Must contain summary block");
        // IF and FOR must still be present as structural nodes
        assertTrue(result.contains("#FLAG"));
        assertTrue(result.contains("#J"));
    }

    @Test
    void collapsedFlowchart_showsInfrequentPerformsAsSideBranches() {
        // PERFORM INIT called once → should be shown as separate PERFORM node
        JclOutlineModel model = new JclOutlineModel();
        model.setLanguage(JclOutlineModel.Language.NATURAL);
        model.setSourceName("INITPGM");

        model.addElement(new JclElement(JclElementType.NAT_PROGRAM, "INITPGM", 1, "DEFINE DATA"));

        JclElement perf = new JclElement(JclElementType.NAT_PERFORM,
                "INIT-ROUTINE", 10, "PERFORM INIT-ROUTINE");
        perf.addParameter("TARGET", "INIT-ROUTINE");
        model.addElement(perf);

        model.addElement(new JclElement(JclElementType.NAT_IF_BLOCK, "X = 1", 20, "IF X = 1"));
        model.addElement(new JclElement(JclElementType.NAT_END, "END", 99, "END"));

        String result = OutlineToMermaidConverter.convert(model,
                OutlineToMermaidConverter.DiagramType.FLOWCHART, null, null, true);

        assertNotNull(result);
        // Infrequent PERFORM should be shown (not collapsed)
        assertTrue(result.contains("INIT-ROUTINE"), "Infrequent PERFORM target must be visible");
        assertTrue(result.contains("PERFORM"), "PERFORM edge label must be present");
    }

    @Test
    void collapsedFlowchart_externalCallsAsImportantSideBranches() {
        JclOutlineModel model = new JclOutlineModel();
        model.setLanguage(JclOutlineModel.Language.NATURAL);
        model.setSourceName("CALLPGM");

        model.addElement(new JclElement(JclElementType.NAT_PROGRAM, "CALLPGM", 1, "DEFINE DATA"));
        model.addElement(new JclElement(JclElementType.NAT_IF_BLOCK, "MODE = 'A'", 10, "IF MODE = 'A'"));

        JclElement call = new JclElement(JclElementType.NAT_CALLNAT, "EXTMOD", 15, "CALLNAT 'EXTMOD'");
        call.addParameter("TARGET", "EXTMOD");
        model.addElement(call);

        model.addElement(new JclElement(JclElementType.NAT_FOR, "#K", 20, "FOR #K = 1 TO 3"));
        model.addElement(new JclElement(JclElementType.NAT_END, "END", 99, "END"));

        String result = OutlineToMermaidConverter.convert(model,
                OutlineToMermaidConverter.DiagramType.FLOWCHART, null, null, true);

        assertNotNull(result);
        assertTrue(result.contains("EXTMOD"), "External call target must be visible");
        assertTrue(result.contains("CALLNAT"), "CALLNAT label must be present");
        assertTrue(result.contains("==>"), "External call must use bold arrow");
    }

    @Test
    void collapsedFlowchart_dbOperationsPreserved() {
        JclOutlineModel model = new JclOutlineModel();
        model.setLanguage(JclOutlineModel.Language.NATURAL);
        model.setSourceName("DBPGM");

        model.addElement(new JclElement(JclElementType.NAT_PROGRAM, "DBPGM", 1, "DEFINE DATA"));

        JclElement read = new JclElement(JclElementType.NAT_READ, "", 10, "READ EMPLOYEES");
        read.addParameter("FILE", "EMPLOYEES");
        model.addElement(read);

        JclElement find = new JclElement(JclElementType.NAT_FIND, "", 20, "FIND DEPARTMENTS");
        find.addParameter("FILE", "DEPARTMENTS");
        model.addElement(find);

        model.addElement(new JclElement(JclElementType.NAT_END, "END", 99, "END"));

        String result = OutlineToMermaidConverter.convert(model,
                OutlineToMermaidConverter.DiagramType.FLOWCHART, null, null, true);

        assertNotNull(result);
        assertTrue(result.contains("EMPLOYEES"), "READ file must be present");
        assertTrue(result.contains("DEPARTMENTS"), "FIND file must be present");
        // DB operations should appear as cylinder nodes
        assertTrue(result.contains("[(\""), "DB operations should use cylinder shape");
    }

    @Test
    void collapsedFlowchart_aiSummaryHintPresent() {
        // Frequent PERFORMs → collapsed block should have AI-SUMMARY-HINT comment
        JclOutlineModel model = new JclOutlineModel();
        model.setLanguage(JclOutlineModel.Language.NATURAL);
        model.setSourceName("AIPGM");

        model.addElement(new JclElement(JclElementType.NAT_PROGRAM, "AIPGM", 1, "DEFINE DATA"));
        model.addElement(new JclElement(JclElementType.NAT_IF_BLOCK, "X > 0", 10, "IF X > 0"));

        // 3 frequent PERFORMs (threshold = 3)
        for (int i = 0; i < 3; i++) {
            JclElement perf = new JclElement(JclElementType.NAT_PERFORM,
                    "BUILD-XML", 20 + i, "PERFORM BUILD-XML");
            perf.addParameter("TARGET", "BUILD-XML");
            model.addElement(perf);
        }

        model.addElement(new JclElement(JclElementType.NAT_END, "END", 99, "END"));

        String result = OutlineToMermaidConverter.convert(model,
                OutlineToMermaidConverter.DiagramType.FLOWCHART, null, null, true);

        assertNotNull(result);
        assertTrue(result.contains("AI-SUMMARY-HINT"),
                "Collapsed block must contain AI summarisation hint");
        assertTrue(result.contains("PERFORM BUILD-XML"),
                "AI hint must contain raw text of collapsed elements");
    }

    @Test
    void collapsedFlowchart_dataDefinitionsSkipped() {
        // Data definitions should not appear in the collapsed flowchart
        JclOutlineModel model = new JclOutlineModel();
        model.setLanguage(JclOutlineModel.Language.NATURAL);
        model.setSourceName("DATAPGM");

        model.addElement(new JclElement(JclElementType.NAT_PROGRAM, "DATAPGM", 1, "DEFINE DATA"));
        model.addElement(new JclElement(JclElementType.NAT_DEFINE_DATA, "DEFINE DATA", 2, "DEFINE DATA"));
        model.addElement(new JclElement(JclElementType.NAT_LOCAL, "LOCAL", 3, "LOCAL"));
        model.addElement(new JclElement(JclElementType.NAT_DATA_VAR, "#COUNTER", 4, "01 #COUNTER (N5)"));
        model.addElement(new JclElement(JclElementType.NAT_DATA_VAR, "#NAME", 5, "01 #NAME (A30)"));
        model.addElement(new JclElement(JclElementType.NAT_IF_BLOCK, "#COUNTER > 0", 10, "IF #COUNTER > 0"));
        model.addElement(new JclElement(JclElementType.NAT_END, "END", 99, "END"));

        String result = OutlineToMermaidConverter.convert(model,
                OutlineToMermaidConverter.DiagramType.FLOWCHART, null, null, true);

        assertNotNull(result);
        // Data definitions must NOT appear
        assertFalse(result.contains("DEFINE DATA"), "DEFINE DATA must be skipped");
        assertFalse(result.contains("LOCAL"), "LOCAL must be skipped");
        assertFalse(result.contains("#COUNTER (N5)"), "Variable definition must be skipped");
        // But the IF must be present
        assertTrue(result.contains("#COUNTER > 0"), "IF condition must be present");
    }

    @Test
    void collapsedFlowchart_mixedDetailAndStructuralElements() {
        // Simulates: IF → (freq PERFORM ×3, INCLUDE) → FOR → (freq PERFORM ×2) → END
        JclOutlineModel model = new JclOutlineModel();
        model.setLanguage(JclOutlineModel.Language.NATURAL);
        model.setSourceName("MIXPGM");

        model.addElement(new JclElement(JclElementType.NAT_PROGRAM, "MIXPGM", 1, "DEFINE DATA"));
        model.addElement(new JclElement(JclElementType.NAT_IF_BLOCK, "MODE = 'X'", 10, "IF MODE = 'X'"));

        // 3 PERFORMs to frequent target + 1 INCLUDE (all detail)
        for (int i = 0; i < 3; i++) {
            JclElement perf = new JclElement(JclElementType.NAT_PERFORM,
                    "WRITE-LINE", 20 + i, "PERFORM WRITE-LINE");
            perf.addParameter("TARGET", "WRITE-LINE");
            model.addElement(perf);
        }
        JclElement inc = new JclElement(JclElementType.NAT_INCLUDE, "CCUTIL", 25, "INCLUDE CCUTIL");
        inc.addParameter("COPYCODE", "CCUTIL");
        model.addElement(inc);

        model.addElement(new JclElement(JclElementType.NAT_FOR, "#N", 30, "FOR #N = 1 TO 20"));

        // 2 more PERFORMs in the FOR block
        for (int i = 0; i < 2; i++) {
            JclElement perf = new JclElement(JclElementType.NAT_PERFORM,
                    "WRITE-LINE", 40 + i, "PERFORM WRITE-LINE");
            perf.addParameter("TARGET", "WRITE-LINE");
            model.addElement(perf);
        }

        model.addElement(new JclElement(JclElementType.NAT_END, "END", 99, "END"));

        String result = OutlineToMermaidConverter.convert(model,
                OutlineToMermaidConverter.DiagramType.FLOWCHART, null, null, true);

        assertNotNull(result);

        // IF and FOR must be present as structural nodes
        assertTrue(result.contains("MODE = "), "IF condition must be present");
        assertTrue(result.contains("#N"), "FOR variable must be present");

        // There should be two collapsed blocks (one after IF, one after FOR)
        // Count occurrences of the summary block marker
        int blockCount = countOccurrences(result, "Anweisung");
        assertTrue(blockCount >= 2, "Should have at least 2 collapsed detail blocks, got " + blockCount);

        // Dashed border style for collapsed blocks
        assertTrue(result.contains("stroke-dasharray"), "Collapsed blocks should have dashed border");
    }

    @Test
    void collapsedFlowchart_emptyProgram_producesStartEnd() {
        JclOutlineModel model = new JclOutlineModel();
        model.setLanguage(JclOutlineModel.Language.NATURAL);
        model.setSourceName("EMPTY");

        model.addElement(new JclElement(JclElementType.NAT_PROGRAM, "EMPTY", 1, "DEFINE DATA"));
        model.addElement(new JclElement(JclElementType.NAT_END, "END", 2, "END"));

        String result = OutlineToMermaidConverter.convert(model,
                OutlineToMermaidConverter.DiagramType.FLOWCHART, null, null, true);

        assertNotNull(result);
        assertTrue(result.contains("EMPTY"), "Program name must be present");
        assertTrue(result.contains("END"), "END node must be present");
        assertTrue(result.contains("-->"), "Must have arrow between START and END");
    }

    @Test
    void collapsedFlowchart_jclStepsPreservedInOrder() {
        JclOutlineModel model = new JclOutlineModel();
        model.setLanguage(JclOutlineModel.Language.JCL);

        model.addElement(new JclElement(JclElementType.JOB, "MYJOB", 1, "//MYJOB JOB"));
        JclElement s1 = new JclElement(JclElementType.EXEC, "STEP1", 2, "//STEP1 EXEC PGM=SORT");
        s1.addParameter("PGM", "SORT");
        model.addElement(s1);
        JclElement s2 = new JclElement(JclElementType.EXEC, "STEP2", 3, "//STEP2 EXEC PGM=COPY");
        s2.addParameter("PGM", "COPY");
        model.addElement(s2);
        JclElement s3 = new JclElement(JclElementType.EXEC, "STEP3", 4, "//STEP3 EXEC PGM=PRINT");
        s3.addParameter("PGM", "PRINT");
        model.addElement(s3);

        String result = OutlineToMermaidConverter.convert(model,
                OutlineToMermaidConverter.DiagramType.FLOWCHART, null, null, true);

        assertNotNull(result);
        int posStep1 = result.indexOf("STEP1");
        int posStep2 = result.indexOf("STEP2");
        int posStep3 = result.indexOf("STEP3");
        assertTrue(posStep1 >= 0 && posStep2 >= 0 && posStep3 >= 0, "All steps must be present");
        assertTrue(posStep1 < posStep2, "STEP1 before STEP2");
        assertTrue(posStep2 < posStep3, "STEP2 before STEP3");
    }

    @Test
    void collapsedFlowchart_frequencyLegendPresent() {
        // When frequent targets exist, a Mermaid comment listing them should appear
        JclOutlineModel model = new JclOutlineModel();
        model.setLanguage(JclOutlineModel.Language.NATURAL);
        model.setSourceName("FREQPGM");

        model.addElement(new JclElement(JclElementType.NAT_PROGRAM, "FREQPGM", 1, "DEFINE DATA"));
        for (int i = 0; i < 4; i++) {
            JclElement perf = new JclElement(JclElementType.NAT_PERFORM,
                    "COMMON-SUB", 10 + i, "PERFORM COMMON-SUB");
            perf.addParameter("TARGET", "COMMON-SUB");
            model.addElement(perf);
        }
        model.addElement(new JclElement(JclElementType.NAT_END, "END", 99, "END"));

        String result = OutlineToMermaidConverter.convert(model,
                OutlineToMermaidConverter.DiagramType.FLOWCHART, null, null, true);

        assertNotNull(result);
        // Frequency legend as Mermaid comment
        assertTrue(result.contains("COMMON_SUB") || result.contains("COMMON-SUB"),
                "Frequent target must appear in legend");
        assertTrue(result.contains("%%"), "Legend must be a Mermaid comment");
    }

    @Test
    void collapsedFlowchart_onErrorPreserved() {
        JclOutlineModel model = new JclOutlineModel();
        model.setLanguage(JclOutlineModel.Language.NATURAL);
        model.setSourceName("ERRPGM");

        model.addElement(new JclElement(JclElementType.NAT_PROGRAM, "ERRPGM", 1, "DEFINE DATA"));
        model.addElement(new JclElement(JclElementType.NAT_ON_ERROR, "ON ERROR", 10, "ON ERROR"));
        model.addElement(new JclElement(JclElementType.NAT_END, "END", 99, "END"));

        String result = OutlineToMermaidConverter.convert(model,
                OutlineToMermaidConverter.DiagramType.FLOWCHART, null, null, true);

        assertNotNull(result);
        assertTrue(result.contains("ON ERROR"), "ON ERROR must be preserved");
        assertTrue(result.contains("ffcdd2"), "ON ERROR should have red styling");
    }

    /** Count how often a substring appears in a string. */
    private static int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) >= 0) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}

