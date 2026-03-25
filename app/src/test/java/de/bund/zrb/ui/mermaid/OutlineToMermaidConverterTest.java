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
}

