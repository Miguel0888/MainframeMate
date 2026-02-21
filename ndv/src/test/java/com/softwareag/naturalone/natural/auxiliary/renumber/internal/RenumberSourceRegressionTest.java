package com.softwareag.naturalone.natural.auxiliary.renumber.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressionstests für Verhaltensdivergenz zwischen alter Neuimplementierung
 * und dem tatsächlichen Originalcode (identifiziert durch Backcheck).
 *
 * Jeder Test ist direkt auf einen konkreten Unterschied zurückführbar.
 */
@DisplayName("RenumberSource – Regressionstests (Originalcode-Backcheck)")
class RenumberSourceRegressionTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Diff 1: Phase-1-Label-Erkennung — indexOf(".", prefixPos+1) mit Guard
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Diff 1 – Phase-1-Label-Erkennung: indexOf('.', prefixPos+1) mit Guard prefixPos+1 < dotPos")
    class Phase1LabelDetection {

        @Test
        @DisplayName("Einzel-Zeichen-Prefix '!': Dot direkt nach Prefix-Zeichen → labelMode aktiv")
        void singleCharPrefix_dotAfterOneChar_isLabelMode() {
            // "!1." → prefixPos=0, Dot ab 1 → dotPos=2, guard 1<2 ✓, substring(1,2)="1" → OK
            String[] source = {"!1. CODE"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0010 CODE", result[0].toString(),
                    "Einzel-Char-Prefix mit Zahl+Punkt am Anfang muss Label-Mode aktivieren");
        }

        @Test
        @DisplayName("Zwei-Zeichen-Prefix '##': Zahl zwischen Prefix-Zeichen 1 und Dot → kein labelMode (NFE durch '#1')")
        void multiCharPrefix_secondCharIsHashNotDigit_noLabelMode() {
            // "##1." → prefixPos=0, Dot ab 1 → dotPos=3, guard 1<3 ✓, substring(1,3)="#1" → NFE → kein labelMode
            String[] source = {"##1. CODE"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "##", false, false, false);
            assertEquals("0010 ##1. CODE", result[0].toString(),
                    "Zwei-Char-Prefix '##' darf KEINEN Label-Mode aktivieren (NFE wegen '#1')");
        }

        @Test
        @DisplayName("Guard prefixPos+1 < dotPos: '!.' (kein Zeichen zwischen Prefix und Dot) → kein labelMode")
        void guard_nothingBetweenPrefixAndDot_noLabelMode() {
            // "!." → prefixPos=0, Dot ab 1 → dotPos=1, guard 0+1 < 1 ist FALSE → kein labelMode
            String[] source = {"!. CODE"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0010 !. CODE", result[0].toString(),
                    "Kein Zeichen zwischen Prefix und Dot darf keinen Label-Mode aktivieren");
        }

        @Test
        @DisplayName("Guard: nicht-numerischer Anteil zwischen Prefix und Dot → kein labelMode")
        void guard_nonNumericBetweenPrefixAndDot_noLabelMode() {
            // "!abc." → substring(1, 4)="abc" → NFE → kein labelMode
            String[] source = {"!abc. CODE"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0010 !abc. CODE", result[0].toString(),
                    "Nicht-numerischer Anteil zwischen Prefix und Dot darf keinen Label-Mode aktivieren");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Diff 2: Phase-2b-Label-Ref — Replace ersetzt nur prefix..dot+1,
    //         die öffnende Klammer '(' und der Terminator bleiben stehen
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Diff 2 – Phase-2b-Label-Ref: Replace nur prefix..dot+1, Klammer und Terminator bleiben")
    class Phase2bLabelRefReplace {

        @Test
        @DisplayName("Label-Ref '(!1.)': Klammer und ')' bleiben, nur '!1.' → '0010'")
        void labelRef_parenAndTerminatorPreserved() {
            String[] source = {"!1. CODE", "IF (!1.) THEN"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            // '(' bleibt, ')' bleibt, nur "!1." wird zu "0010"
            assertEquals("0020 IF (0010) THEN", result[1].toString());
        }

        @Test
        @DisplayName("Label-Ref '(!1./': Klammer und '/' bleiben, nur '!1.' → '0010'")
        void labelRef_slashTerminatorPreserved() {
            String[] source = {"!1. CODE", "GOTO (!1./ REST"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0020 GOTO (0010/ REST", result[1].toString());
        }

        @Test
        @DisplayName("Label-Ref '(!1.,': Klammer und ',' bleiben, nur '!1.' → '0010'")
        void labelRef_commaTerminatorPreserved() {
            String[] source = {"!1. CODE", "IF (!1., OTHER"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0020 IF (0010, OTHER", result[1].toString());
        }

        @Test
        @DisplayName("Mehrstelliges Label '!12.': Replace liefert exakt '(0010)' — keine Verschiebung")
        void labelRef_multiDigitLabelReplaced() {
            String[] source = {"!12. CODE", "IF (!12.) THEN"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0010 CODE", result[0].toString());
            assertEquals("0020 IF (0010) THEN", result[1].toString());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Diff 4: removeLineNumbers — getExistingLabel bekommt substring(4).trim()
    //         statt substring(5).trim()
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Diff 4 – removeLineNumbers: getExistingLabel mit substring(4).trim()")
    class RemoveLineNumbersExistingLabel {

        @Test
        @DisplayName("Zeile '0010 L1.CODE': substring(4).trim()='L1.CODE' → existingLabel='L1.' wird erkannt")
        void existingLabel_foundViaSubstring4() {
            // Ziel: Zeile 0010 hat ein bestehendes Label "L1."
            // Eine Referenz darauf soll durch "L1." ersetzt werden
            IInsertLabels ctrl = makeInsertLabels(true, "GEN%d.", false);
            List<StringBuffer> source = new ArrayList<>();
            source.add(new StringBuffer("0010 L1.CODE"));
            source.add(new StringBuffer("0020 GOTO (0010) END"));

            String[] result = RenumberSource.removeLineNumbers(source, true, false, 5, 10, ctrl);

            // "L1." muss erkannt werden und als Label in der Referenz erscheinen
            assertEquals("GOTO (L1.) END", result[1],
                    "Existierendes Label 'L1.' muss aus substring(4).trim() erkannt werden");
        }

        @Test
        @DisplayName("Zeile '0010 L1. CODE': trim() entfernt führendes Leerzeichen korrekt")
        void existingLabel_spaceAfterLinenoTrimmed() {
            IInsertLabels ctrl = makeInsertLabels(true, "GEN%d.", false);
            List<StringBuffer> source = new ArrayList<>();
            source.add(new StringBuffer("0010 L1. CODE"));
            source.add(new StringBuffer("0020 GOTO (0010) END"));

            String[] result = RenumberSource.removeLineNumbers(source, true, false, 5, 10, ctrl);

            assertEquals("GOTO (L1.) END", result[1],
                    "trim() nach substring(4) muss das Leerzeichen nach der Zeilennummer entfernen");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Diff 5: removeLineNumbers — Label-Lookup nach Replace mit aktuellem
    //         Puffer-Inhalt (nicht dem alten refKey)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Diff 5 – removeLineNumbers: mehrere Referenzen auf dieselbe Zeile → gleiches Label")
    class RemoveLineNumbersLabelReuse {

        @Test
        @DisplayName("Zwei Referenzen auf dieselbe Zielzeile → dasselbe Label wird wiederverwendet")
        void twoRefsToSameLine_sameLabelUsed() {
            IInsertLabels ctrl = makeInsertLabels(true, "L%d.", false);
            List<StringBuffer> source = new ArrayList<>();
            source.add(new StringBuffer("0010 CODE"));
            source.add(new StringBuffer("0020 IF (0010) OR (0010) END"));

            String[] result = RenumberSource.removeLineNumbers(source, true, false, 5, 10, ctrl);

            String line = result[1];
            // Beide Referenzen müssen durch dasselbe Label ersetzt werden
            // (nicht zwei verschiedene Labels erzeugen)
            int first  = line.indexOf('(');
            int second = line.indexOf('(', first + 1);
            assertTrue(first != -1 && second != -1, "Zwei Klammern erwartet");
            String label1 = line.substring(first + 1, line.indexOf(')', first));
            String label2 = line.substring(second + 1, line.indexOf(')', second));
            assertEquals(label1, label2,
                    "Beide Referenzen auf dieselbe Zielzeile müssen dasselbe Label erhalten");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hilfsmethoden
    // ─────────────────────────────────────────────────────────────────────────

    private static IInsertLabels makeInsertLabels(boolean insert, String format, boolean newLine) {
        return new IInsertLabels() {
            @Override public boolean isInsertLabels()  { return insert;  }
            @Override public String  getLabelFormat()  { return format;  }
            @Override public boolean isCreateNewLine() { return newLine; }
        };
    }
}

