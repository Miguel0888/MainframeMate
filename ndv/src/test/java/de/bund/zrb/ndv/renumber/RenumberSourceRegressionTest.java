package de.bund.zrb.ndv.renumber;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressionstests für Grenzfälle der Label-Erkennung und Referenzauflösung.
 */
@DisplayName("RenumberSource – Regressionstests")
class RenumberSourceRegressionTest {

    @Nested
    @DisplayName("Phase-1-Label-Erkennung: Prefix und Zahlenanteil")
    class Phase1LabelDetection {

        @Test
        @DisplayName("Einzel-Zeichen-Prefix '!' mit '!1.' → Label-Mode aktiv")
        void singleCharPrefix_activatesLabelMode() {
            String[] source = {"!1. CODE"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0010 CODE", result[0].toString());
        }

        @Test
        @DisplayName("Zwei-Zeichen-Prefix '##' mit '##1.' → kein Label-Mode, da kein reiner Zahlenanteil")
        void multiCharHashPrefix_noLabelMode() {
            String[] source = {"##1. CODE"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "##", false, false, false);
            assertEquals("0010 ##1. CODE", result[0].toString());
        }

        @Test
        @DisplayName("Zwei-Zeichen-Prefix 'AB' mit 'AB1.' → kein Label-Mode, da kein reiner Zahlenanteil")
        void multiCharAlphaPrefix_noLabelMode() {
            String[] source = {"AB1. CODE"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "AB", false, false, false);
            assertEquals("0010 AB1. CODE", result[0].toString());
        }

        @Test
        @DisplayName("'!.' ohne Zeichen zwischen Prefix und Punkt → kein Label-Mode")
        void nothingBetweenPrefixAndDot_noLabelMode() {
            String[] source = {"!. CODE"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0010 !. CODE", result[0].toString());
        }
    }

    @Nested
    @DisplayName("Phase-2b-Label-Referenzen: Klammer und Terminator bleiben erhalten")
    class Phase2bLabelRefReplace {

        @Test
        @DisplayName("Label-Ref '(!1.)' → Klammer und ')' bleiben, Zahl wird eingesetzt")
        void labelRef_parenTerminator() {
            String[] source = {"!1. CODE", "IF (!1.) THEN"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0020 IF (0010) THEN", result[1].toString());
        }

        @Test
        @DisplayName("Label-Ref '(!1./' → Klammer und '/' bleiben, Zahl wird eingesetzt")
        void labelRef_slashTerminator() {
            String[] source = {"!1. CODE", "GOTO (!1./ REST"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0020 GOTO (0010/ REST", result[1].toString());
        }

        @Test
        @DisplayName("Label-Ref '(!1.,' → Klammer und ',' bleiben, Zahl wird eingesetzt")
        void labelRef_commaTerminator() {
            String[] source = {"!1. CODE", "IF (!1., OTHER"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0020 IF (0010, OTHER", result[1].toString());
        }
    }

    @Nested
    @DisplayName("removeLineNumbers: Label-Erkennung und Wiederverwendung")
    class RemoveLineNumbersLabels {

        @Test
        @DisplayName("Existierendes Label auf Zielzeile wird erkannt und als Referenz verwendet")
        void existingLabelOnTargetLine_usedAsReference() {
            IInsertLabels ctrl = makeCtrl(true, "L%d.", false);
            List<StringBuffer> source = new ArrayList<>();
            source.add(new StringBuffer("0010 L1. CODE"));
            source.add(new StringBuffer("0020 GOTO (0010) END"));
            String[] result = RenumberSource.removeLineNumbers(source, true, false, 5, 10, ctrl);
            assertEquals("GOTO (L1.) END", result[1]);
        }

        @Test
        @DisplayName("Mehrere Referenzen auf dieselbe Zeile → dasselbe Label wird wiederverwendet")
        void multipleRefsToSameLine_sameLabelReused() {
            IInsertLabels ctrl = makeCtrl(true, "L%d.", false);
            List<StringBuffer> source = new ArrayList<>();
            source.add(new StringBuffer("0010 CODE"));
            source.add(new StringBuffer("0020 IF (0010) OR (0010) END"));
            String[] result = RenumberSource.removeLineNumbers(source, true, false, 5, 10, ctrl);
            String line = result[1];
            int first  = line.indexOf('(');
            int second = line.indexOf('(', first + 1);
            String label1 = line.substring(first + 1, line.indexOf(')', first));
            String label2 = line.substring(second + 1, line.indexOf(')', second));
            assertEquals(label1, label2);
        }
    }

    private static IInsertLabels makeCtrl(boolean insert, String fmt, boolean newLine) {
        return new IInsertLabels() {
            public boolean isInsertLabels()  { return insert;  }
            public String  getLabelFormat()  { return fmt;     }
            public boolean isCreateNewLine() { return newLine; }
        };
    }
}
