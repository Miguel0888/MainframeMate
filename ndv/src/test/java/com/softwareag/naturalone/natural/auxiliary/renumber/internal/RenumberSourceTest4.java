package com.softwareag.naturalone.natural.auxiliary.renumber.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RenumberSourceTest4 — zusätzliche Edge-Cases und Grenzwert-Tests.
 * Deckt extreme Eingaben, Grenzwerte, ungewöhnliche Zeichenkombinationen,
 * leere Strings, Sonderfälle bei Labels, Kommentaren, Quotes und
 * Kombinationen davon ab.
 */
class RenumberSourceTest4 {

    // ═══════════════════════════════════════════════════════════════════
    //  Hilfsmethoden
    // ═══════════════════════════════════════════════════════════════════

    private static IInsertLabels labels(boolean insert, String format, boolean newLine) {
        return new IInsertLabels() {
            @Override public boolean isInsertLabels() { return insert; }
            @Override public String getLabelFormat()  { return format; }
            @Override public boolean isCreateNewLine() { return newLine; }
        };
    }

    private static List<StringBuffer> sbList(String... lines) {
        List<StringBuffer> list = new ArrayList<>();
        for (String l : lines) {
            list.add(new StringBuffer(l));
        }
        return list;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  isLineReference — extreme Edge-Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("isLineReference – Extreme Edge-Cases")
    class IsLineReferenceExtremeTest {

        @Test
        @DisplayName("pos=0, leerer String → false")
        void emptyString() {
            assertFalse(RenumberSource.isLineReference(0, ""));
        }

        @Test
        @DisplayName("pos negativ → StringIndexOutOfBoundsException")
        void negativePos() {
            // pos=-1, pos+6=5 <= length=6 → substring(-1,5) → SIOOBE
            try {
                RenumberSource.isLineReference(-1, "(0010)");
                fail("Expected StringIndexOutOfBoundsException");
            } catch (StringIndexOutOfBoundsException expected) {
                // korrekt
            }
        }

        @Test
        @DisplayName("pos genau am Ende des Strings → false")
        void posAtEnd() {
            assertFalse(RenumberSource.isLineReference(6, "(0010)"));
        }

        @Test
        @DisplayName("pos=0 mit genau 6 Zeichen → true")
        void exactlySixChars() {
            assertTrue(RenumberSource.isLineReference(0, "(0010)"));
        }

        @Test
        @DisplayName("pos=0, 7+ Zeichen, gültige Referenz → true")
        void sevenCharsValid() {
            assertTrue(RenumberSource.isLineReference(0, "(0010)X"));
        }

        @Test
        @DisplayName("Referenz mit 0000 → true")
        void refZeros() {
            assertTrue(RenumberSource.isLineReference(0, "(0000)"));
        }

        @Test
        @DisplayName("Referenz mit 9999 → true")
        void refMax() {
            assertTrue(RenumberSource.isLineReference(0, "(9999)"));
        }

        @Test
        @DisplayName("Referenz mit nur Neunen als Slash → true")
        void refMaxSlash() {
            assertTrue(RenumberSource.isLineReference(0, "(9999/"));
        }

        @Test
        @DisplayName("Referenz mit nur Neunen als Komma → true")
        void refMaxComma() {
            assertTrue(RenumberSource.isLineReference(0, "(9999,"));
        }

        @Test
        @DisplayName("Klammer gefolgt von Buchstaben statt Ziffern → false")
        void lettersInsteadOfDigits() {
            assertFalse(RenumberSource.isLineReference(0, "(abcd)"));
        }

        @Test
        @DisplayName("Gemischte Ziffern und Buchstaben → false")
        void mixedDigitsLetters() {
            assertFalse(RenumberSource.isLineReference(0, "(01A2)"));
        }

        @Test
        @DisplayName("Nur 3 Ziffern in Klammer → false")
        void threeDigits() {
            assertFalse(RenumberSource.isLineReference(0, "(012)X"));
        }

        @Test
        @DisplayName("5 Ziffern in Klammer → false (Pattern erwartet genau 4)")
        void fiveDigits() {
            // "(01234)" hat 7 Zeichen, pos=0, substring(0,6)="(01234" → Pattern \([0-9]{4}[\)/,]
            // 5. Zeichen ist '4' (Ziffer), kein )/,  → false
            assertFalse(RenumberSource.isLineReference(0, "(01234)"));
        }

        @Test
        @DisplayName("Doppel-Klammer: ((0010) → pos=0 liefert false, pos=1 liefert true")
        void doubleParen() {
            assertFalse(RenumberSource.isLineReference(0, "((0010)"));
            assertTrue(RenumberSource.isLineReference(1, "((0010)"));
        }

        @Test
        @DisplayName("pos sehr groß → false")
        void posVeryLarge() {
            assertFalse(RenumberSource.isLineReference(1000, "(0010)"));
        }

        @Test
        @DisplayName("null String → NPE")
        void nullString() {
            assertThrows(NullPointerException.class, () ->
                    RenumberSource.isLineReference(0, null));
        }

        @Test
        @DisplayName("Leerzeichen in Referenz → false")
        void spacesInRef() {
            assertFalse(RenumberSource.isLineReference(0, "( 010)"));
        }

        @Test
        @DisplayName("Tab in Referenz → false")
        void tabInRef() {
            assertFalse(RenumberSource.isLineReference(0, "(\t010)"));
        }

        @Test
        @DisplayName("Unicode-Ziffern → false (Pattern erwartet ASCII)")
        void unicodeDigits() {
            // ١٢٣٤ sind arabische Ziffern, nicht [0-9]
            assertFalse(RenumberSource.isLineReference(0, "(\u0661\u0662\u0663\u0664)"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  isLineNumberReference — extreme Edge-Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("isLineNumberReference – Extreme Edge-Cases")
    class IsLineNumberReferenceExtremeTest {

        @Test
        @DisplayName("Leerer String mit pos=-1 → false")
        void emptyStringPosMinusOne() {
            assertFalse(RenumberSource.isLineNumberReference(-1, "", false, false, false));
        }

        @Test
        @DisplayName("pos=0, 6 Zeichen genau, keine Kommentar/Quote → true")
        void exactMinimalInput() {
            assertTrue(RenumberSource.isLineNumberReference(0, "(0010)", false, false, false));
        }

        @Test
        @DisplayName("'*/' am Offset 0, insertLabelsMode=true → false")
        void commentStarSlashInsertLabels() {
            String line = "*/ text (0010)";
            assertFalse(RenumberSource.isLineNumberReference(8, line, true, false, false));
        }

        @Test
        @DisplayName("Verschachtelte Quotes: '\"(0010)\"' → innerhalb single-quote → renConst entscheidet")
        void nestedQuotes() {
            // Ab Pos 0: single-quote offen, dann double-quote, dann (0010)
            String line = "'\"(0010)\"'";
            // Single-Quote ist offen → in String → renConst=false → false
            assertFalse(RenumberSource.isLineNumberReference(2, line, false, false, false));
            // renConst=true → true
            assertTrue(RenumberSource.isLineNumberReference(2, line, false, false, true));
        }

        @Test
        @DisplayName("Ungerade Anzahl Single-Quotes → in String")
        void oddSingleQuotes() {
            String line = "A'B'C'(0010)";
            // 3 Single-Quotes vor ( → nach Toggle: offen-geschlossen-offen → in String
            assertFalse(RenumberSource.isLineNumberReference(6, line, false, false, false));
        }

        @Test
        @DisplayName("Gerade Anzahl Single-Quotes → nicht in String")
        void evenSingleQuotes() {
            String line = "A'B'(0010)";
            // 2 Single-Quotes vor ( → offen-geschlossen → nicht in String
            assertTrue(RenumberSource.isLineNumberReference(4, line, false, false, false));
        }

        @Test
        @DisplayName("Ungerade Anzahl Double-Quotes → in String")
        void oddDoubleQuotes() {
            String line = "A\"B\"C\"(0010)";
            assertFalse(RenumberSource.isLineNumberReference(6, line, false, false, false));
        }

        @Test
        @DisplayName("Gerade Anzahl Double-Quotes → nicht in String")
        void evenDoubleQuotes() {
            String line = "A\"B\"(0010)";
            assertTrue(RenumberSource.isLineNumberReference(4, line, false, false, false));
        }

        @Test
        @DisplayName("/* direkt am Anfang der Zeile → Block-Kommentar")
        void blockCommentAtStart() {
            String line = "/*(0010)";
            // Pos=0 char='/', charAt(1)='*' → Block-Kommentar → return !insertLabelsMode
            assertTrue(RenumberSource.isLineNumberReference(2, line, false, false, false));
            assertFalse(RenumberSource.isLineNumberReference(2, line, true, false, false));
        }

        @Test
        @DisplayName("/ ohne * danach → kein Block-Kommentar")
        void slashWithoutStar() {
            String line = "/X(0010)";
            assertTrue(RenumberSource.isLineNumberReference(2, line, false, false, false));
        }

        @Test
        @DisplayName("'* ' am Index 5 mit hasLineNumberPrefix=true → Kommentar")
        void starSpaceAtOffset5() {
            String line = "XXXX * (0010)";
            // hasLineNumberPrefix=true → offset=5, indexOf("* ")==5 → Kommentar
            assertTrue(RenumberSource.isLineNumberReference(7, line, false, true, false));
            assertFalse(RenumberSource.isLineNumberReference(7, line, true, true, false));
        }

        @Test
        @DisplayName("'**' am Index 5 mit hasLineNumberPrefix=true → Kommentar")
        void doubleStarAtOffset5() {
            String line = "XXXX **(0010)";
            assertTrue(RenumberSource.isLineNumberReference(7, line, false, true, false));
            assertFalse(RenumberSource.isLineNumberReference(7, line, true, true, false));
        }

        @Test
        @DisplayName("'*/' am Index 5 mit hasLineNumberPrefix=true → Kommentar")
        void starSlashAtOffset5() {
            String line = "XXXX */(0010)";
            assertTrue(RenumberSource.isLineNumberReference(7, line, false, true, false));
            assertFalse(RenumberSource.isLineNumberReference(7, line, true, true, false));
        }

        @Test
        @DisplayName("Kommentar-Marker nicht am erwarteten Offset → kein Kommentar")
        void commentMarkerAtWrongOffset() {
            // hasLineNumberPrefix=false → offset=0, aber "* " steht bei Index 5 → kein Treffer
            String line = "XXXX * (0010)";
            assertTrue(RenumberSource.isLineNumberReference(7, line, false, false, false));
        }

        @Test
        @DisplayName("pos=0, String ist genau '(0010)' → alle Flags false → true")
        void minimalValidRef() {
            assertTrue(RenumberSource.isLineNumberReference(0, "(0010)", false, false, false));
        }

        @Test
        @DisplayName("Referenz mit Komma-Ende → true")
        void refWithComma() {
            assertTrue(RenumberSource.isLineNumberReference(0, "(0010,REST", false, false, false));
        }

        @Test
        @DisplayName("Referenz mit Slash-Ende → true")
        void refWithSlash() {
            assertTrue(RenumberSource.isLineNumberReference(0, "(0010/REST", false, false, false));
        }

        @Test
        @DisplayName("Beide Quote-Typen gemischt vor Referenz")
        void mixedQuotes() {
            // 'A"B' → single-open, A, double-toggle, B, single-close → single geschlossen, double offen
            String line = "'A\"B'(0010)";
            // Bei pos=5: single-quotes: pos0=open, pos4=close → geschlossen
            // double-quotes: pos2=open → offen → in double-string
            assertFalse(RenumberSource.isLineNumberReference(5, line, false, false, false));
        }

        @Test
        @DisplayName("Block-Kommentar /* innerhalb Single-Quotes → KEIN Block-Kommentar")
        void blockCommentInsideSingleQuotes() {
            String line = "'/*'(0010)";
            // pos0=single-open, pos1=/ (in string → no block comment check), pos2=*, pos3=single-close
            // Bei der Schleife: pos0 single-open → var7=true, pos1 '/' → var7=true → skip block comment
            assertTrue(RenumberSource.isLineNumberReference(4, line, false, false, false));
        }

        @Test
        @DisplayName("Block-Kommentar /* innerhalb Double-Quotes → KEIN Block-Kommentar")
        void blockCommentInsideDoubleQuotes() {
            String line = "\"/*\"(0010)";
            assertTrue(RenumberSource.isLineNumberReference(4, line, false, false, false));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  addLineNumbers — extreme Edge-Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("addLineNumbers – Extreme Edge-Cases")
    class AddLineNumbersExtremeTest {

        @Test
        @DisplayName("Leeres Array mit non-null labelPrefix → leeres Ergebnis")
        void emptyArrayNonNullPrefix() {
            StringBuffer[] result = RenumberSource.addLineNumbers(new String[0], 10, "!", false, false, false);
            assertEquals(0, result.length);
        }

        @Test
        @DisplayName("Einzelne leere Zeile")
        void singleEmptyLine() {
            StringBuffer[] result = RenumberSource.addLineNumbers(new String[]{""}, 10, "!", false, false, false);
            assertEquals("0010 ", result[0].toString());
        }

        @Test
        @DisplayName("step=1 mit non-null labelPrefix")
        void stepOneNonNull() {
            String[] source = {"A", "B", "C"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 1, "§", false, false, false);
            assertEquals("0001 A", result[0].toString());
            assertEquals("0002 B", result[1].toString());
            assertEquals("0003 C", result[2].toString());
        }

        @Test
        @DisplayName("step negativ → keine Reduktion, negative Zeilennummern")
        void stepNegative() {
            String[] source = {"A"};
            // step=-5: step!=0, step*1=-5 < 9999 → kein Overflow
            // lineNo = (0+1)*(-5) = -5 → String.format("%04d", -5) → Verhalten prüfen
            StringBuffer[] result = RenumberSource.addLineNumbers(source, -5, "§", false, false, false);
            assertNotNull(result[0]);
        }

        @Test
        @DisplayName("step=Integer.MAX_VALUE → Overflow bei step*length")
        void stepMaxValue() {
            String[] source = {"A", "B"};
            // step*2 → Integer-Overflow → Verhalten prüfen
            StringBuffer[] result = RenumberSource.addLineNumbers(source, Integer.MAX_VALUE, "§", false, false, false);
            assertNotNull(result);
        }

        @Test
        @DisplayName("Genau 9999 Zeilen mit step=1")
        void exactly9999Lines() {
            String[] source = new String[9999];
            Arrays.fill(source, "X");
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 1, "§", false, false, false);
            assertEquals("9999 X", result[9998].toString());
        }

        @Test
        @DisplayName("10000 Zeilen mit step=1 → letzte Zeile > 9999")
        void over9999Lines() {
            String[] source = new String[10000];
            Arrays.fill(source, "X");
            // step=1, 1*10000=10000 > 9999 → Schleife: 10→5→2→1; 9999/10000=0 < 1 → step bleibt 1?
            // Eigentlich: var1 > 1 Bedingung → Schleife endet bei var1=1
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 1, "§", false, false, false);
            assertNotNull(result);
        }

        @Test
        @DisplayName("openSystemsServer + updateRefs gemeinsam")
        void openSystemsAndUpdateRefs() {
            String[] source = {"IF (0001)", "END"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "§", true, true, false);
            assertTrue(result[0].toString().contains("(0010)"));
            assertTrue(result[0].toString().endsWith(" "));
        }

        @Test
        @DisplayName("updateRefs mit renConst=true in Quotes")
        void updateRefsRenConstTrue() {
            String[] source = {"'(0001)'", "END"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "§", true, false, true);
            // ref=1, i+1=1, ref<=1 → replace: 1*10=10 → (0010)
            assertTrue(result[0].toString().contains("(0010)"));
        }

        @Test
        @DisplayName("updateRefs mit renConst=false in Quotes → keine Änderung")
        void updateRefsRenConstFalseInQuotes() {
            String[] source = {"'(0001)'", "END"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "§", true, false, false);
            // In Quotes + renConst=false → isLineNumberReference=false → keine Änderung
            assertTrue(result[0].toString().contains("(0001)"));
        }

        @Test
        @DisplayName("Leerer labelPrefix-String → indexOf('') ist immer 0")
        void emptyLabelPrefix() {
            String[] source = {"A", "B"};
            // indexOf("") == 0 immer → wird als Label-Position 0 erkannt
            // Punkt-Suche: indexOf(".", 1) → kein Punkt → kein Label-Mode
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "", false, false, false);
            assertNotNull(result);
        }

        @Test
        @DisplayName("Zeile besteht nur aus labelPrefix → kein Punkt, kein Label-Mode")
        void lineIsJustPrefix() {
            String[] source = {"!", "END"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            // "!" hat kein "." → kein Label-Mode → normaler Modus
            assertEquals("0010 !", result[0].toString());
        }

        @Test
        @DisplayName("Label-Mode: Label-Definition ohne Leerzeichen nach Punkt")
        void labelNoSpaceAfterDot() {
            String[] source = {"!1.CODE", "IF (!1.) THEN"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0010 CODE", result[0].toString());
            assertEquals("0020 IF (0010) THEN", result[1].toString());
        }

        @Test
        @DisplayName("Label-Mode: labelPrefix mehrzeitig '##', Zahl direkt am Punkt → kein Label-Mode wg. leerem parseInt")
        void multiCharPrefix() {
            // "##1. CODE": indexOf("##")=0, indexOf(".",3)=3, substring(3,3)="" → parseInt("") → NFE → kein labelMode
            String[] source = {"##1. CODE", "IF (##1.) THEN"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "##", false, false, false);
            // Normaler Modus, da Label-Erkennung fehlschlägt
            assertEquals("0010 ##1. CODE", result[0].toString());
        }

        @Test
        @DisplayName("Label-Mode: gleicher Labelname definiert auf zwei Zeilen → erster gewinnt")
        void duplicateLabelDefinition() {
            String[] source = {"!1. FIRST", "!1. SECOND"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            // Zweites !1. wird nicht erneut ins labelMap aufgenommen (containsKey check)
            assertNotNull(result);
            assertEquals("0010 FIRST", result[0].toString());
        }

        @Test
        @DisplayName("Label-Mode: Referenz auf Label, das in einer späteren Zeile definiert ist")
        void forwardLabelRef() {
            String[] source = {"IF (!2.) THEN", "!2. CODE"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            // !2. erst in Zeile 1 definiert, Zeile 0 referenziert es → nicht im Map → bleibt
            assertEquals("0010 IF (!2.) THEN", result[0].toString());
        }

        @Test
        @DisplayName("Label-Mode: Zeile nur aus Whitespace")
        void whitespaceOnlyLine() {
            String[] source = {"!1. CODE", "   ", "END"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0020    ", result[1].toString());
        }

        @Test
        @DisplayName("Label-Mode: Sehr langer Label-Name")
        void veryLongLabelName() {
            String[] source = {"!123456789. CODE"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0010 CODE", result[0].toString());
        }

        @Test
        @DisplayName("Normaler Modus: Zeile enthält '(' aber kein gültiges Ref-Format")
        void parenButNoValidRef() {
            String[] source = {"IF (ABC)", "END"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "§", true, false, false);
            assertEquals("0010 IF (ABC)", result[0].toString());
        }

        @Test
        @DisplayName("Normaler Modus: Zeile enthält mehrere '(' → alle werden geprüft")
        void multipleParens() {
            String[] source = {"X (0001)(0001)", "END"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "§", true, false, false);
            // Beide refs: ref=1, i+1=1, 1<=1 → 1*10=10
            assertTrue(result[0].toString().contains("(0010)"));
        }

        @Test
        @DisplayName("Normaler Modus: Ref am Ende der Zeile ohne schließende Klammer aber mit Komma")
        void refWithCommaAtEnd() {
            String[] source = {"X (0001,", "END"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "§", true, false, false);
            assertTrue(result[0].toString().contains("(0010,"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  removeLineNumbers — extreme Edge-Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("removeLineNumbers – Extreme Edge-Cases")
    class RemoveLineNumbersExtremeTest {

        @Test
        @DisplayName("Leere Liste → leeres Array")
        void emptyList() {
            String[] result = RenumberSource.removeLineNumbers(new ArrayList<>(), true, true, 5, 10,
                    labels(true, "L%d.", true));
            assertEquals(0, result.length);
        }

        @Test
        @DisplayName("Zeile mit weniger als 4 Zeichen → bleibt unverändert (length < 4)")
        void shortLineLessThan4() {
            List<StringBuffer> input = sbList("AB");
            String[] result = RenumberSource.removeLineNumbers(input, false, false, 5, 10, null);
            // length=2, weder >4 noch ==4 → wird einfach als toString() hinzugefügt
            assertEquals("AB", result[0]);
        }

        @Test
        @DisplayName("Zeile mit genau 3 Zeichen")
        void lineExactly3Chars() {
            List<StringBuffer> input = sbList("ABC");
            String[] result = RenumberSource.removeLineNumbers(input, false, false, 5, 10, null);
            assertEquals("ABC", result[0]);
        }

        @Test
        @DisplayName("Zeile mit genau 5 Zeichen → delete(0, prefixLength=5)")
        void lineExactly5Chars() {
            List<StringBuffer> input = sbList("0010X");
            String[] result = RenumberSource.removeLineNumbers(input, false, false, 5, 10, null);
            assertEquals("", result[0]);
        }

        @Test
        @DisplayName("prefixLength=4 → nur 4 Zeichen entfernt")
        void prefixLength4() {
            List<StringBuffer> input = sbList("0010 CODE");
            String[] result = RenumberSource.removeLineNumbers(input, false, false, 4, 10, null);
            assertEquals(" CODE", result[0]);
        }

        @Test
        @DisplayName("prefixLength=0 → nichts entfernt")
        void prefixLength0() {
            List<StringBuffer> input = sbList("0010 CODE");
            String[] result = RenumberSource.removeLineNumbers(input, false, false, 0, 10, null);
            assertEquals("0010 CODE", result[0]);
        }

        @Test
        @DisplayName("prefixLength größer als Zeilenlänge → StringIndexOutOfBoundsException oder leerer String")
        void prefixLengthGreaterThanLine() {
            List<StringBuffer> input = sbList("0010 X");
            // delete(0, 20) auf einem 6-Zeichen-Buffer
            String[] result = RenumberSource.removeLineNumbers(input, false, false, 20, 10, null);
            assertEquals("", result[0]);
        }

        @Test
        @DisplayName("Referenz auf sich selbst + Labels inline → Label wird auf eigene Zeile gesetzt")
        void selfRefLabelsInline() {
            List<StringBuffer> input = sbList("0010 IF (0010)");
            IInsertLabels il = labels(true, "L%d.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            // Zeile 0 referenziert sich selbst → Label erzeugt
            assertTrue(result[0].contains("L1."));
        }

        @Test
        @DisplayName("Referenz auf sich selbst + Labels newLine")
        void selfRefLabelsNewLine() {
            List<StringBuffer> input = sbList("0010 IF (0010)");
            IInsertLabels il = labels(true, "L%d.", true);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            assertTrue(result.length >= 2);
        }

        @Test
        @DisplayName("Viele Referenzen auf verschiedene Zeilen → mehrere Labels")
        void manyDifferentRefs() {
            List<StringBuffer> input = sbList(
                    "0010 A",
                    "0020 B",
                    "0030 C",
                    "0040 IF (0010)(0020)(0030)"
            );
            IInsertLabels il = labels(true, "L%d.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            assertTrue(result[0].contains("L"));
            assertTrue(result[1].contains("L"));
            assertTrue(result[2].contains("L"));
        }

        @Test
        @DisplayName("step=1 → var19 % var4 == 0 Pfad")
        void stepOne() {
            List<StringBuffer> input = sbList("0001 IF (0099)");
            // ref=99, currentLineNo=1 → 99 > 1 → else-Zweig: (99 <= 0 || 99 % 1 == 0) && 99 > 0 → dead code
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 1, null);
            assertEquals("IF (0099)", result[0]);
        }

        @Test
        @DisplayName("step=0 → Division durch Null in totem Code-Pfad (var19 % var4)")
        void stepZero() {
            // step=0: var19 % 0 → ArithmeticException, aber nur wenn der Pfad erreicht wird
            // Der Pfad wird erreicht wenn var19 > var18 UND var19 > 0
            List<StringBuffer> input = sbList("0010 IF (0020)");
            // ref=20, currentLineNo=10 → 20 > 10 → else-Pfad: (20 <= 0 || 20 % 0 == 0) → 20%0 = ArithmeticException
            assertThrows(ArithmeticException.class, () ->
                    RenumberSource.removeLineNumbers(input, true, false, 5, 0, null));
        }

        @Test
        @DisplayName("Referenz die genau auf currentLineNo zeigt (Gleichheit)")
        void refEqualsCurrentLineNo() {
            List<StringBuffer> input = sbList("0010 IF (0010)");
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, null);
            assertEquals("IF (0001)", result[0]);
        }

        @Test
        @DisplayName("Kommentar-Zeile mit Referenz + updateRefs")
        void commentLineWithRef() {
            List<StringBuffer> input = sbList("0010 CODE", "0020 * (0010)");
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, null);
            // "* " am Offset 5 → Kommentar, insertLabelsMode=false → !false → true → wird umgeschrieben
            assertEquals("* (0001)", result[1]);
        }

        @Test
        @DisplayName("Block-Kommentar /* in Zeile + updateRefs")
        void blockCommentInLineWithRef() {
            List<StringBuffer> input = sbList("0010 CODE", "0020 /* (0010)");
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, null);
            // "/* " → Block-Kommentar erkannt → !insertLabelsMode → true → wird als Ref erkannt
            assertEquals("/* (0001)", result[1]);
        }

        @Test
        @DisplayName("Referenz in Double-Quotes + renConst=true → wird umgeschrieben")
        void refInDoubleQuotesRenConstTrue() {
            List<StringBuffer> input = sbList("0010 WRITE \"(0010)\"");
            String[] result = RenumberSource.removeLineNumbers(input, true, true, 5, 10, null);
            assertEquals("WRITE \"(0001)\"", result[0]);
        }

        @Test
        @DisplayName("Referenz in Double-Quotes + renConst=false → bleibt")
        void refInDoubleQuotesRenConstFalse() {
            List<StringBuffer> input = sbList("0010 WRITE \"(0010)\"");
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, null);
            assertEquals("WRITE \"(0010)\"", result[0]);
        }

        @Test
        @DisplayName("Labels newLine: Mehrere Referenzen auf gleiche Zeile → nur ein Label-Zeile")
        void labelsNewLineMultipleRefsSameLine() {
            List<StringBuffer> input = sbList("0010 CODE", "0020 IF (0010)", "0030 GOTO (0010)");
            IInsertLabels il = labels(true, "L%d.", true);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            // Nur 1 Label-Zeile für 0010 → 4 Zeilen gesamt
            assertEquals(4, result.length);
        }

        @Test
        @DisplayName("Labels mit Sonderzeichen im Format")
        void labelsSpecialFormat() {
            List<StringBuffer> input = sbList("0010 CODE", "0020 IF (0010)");
            IInsertLabels il = labels(true, "LABEL_%d_END.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            assertTrue(result[0].contains("LABEL_1_END."));
            assertTrue(result[1].contains("(LABEL_1_END.)"));
        }

        @Test
        @DisplayName("Existierendes Label auf Zielzeile → getExistingLabel findet es")
        void existingLabelReuse() {
            List<StringBuffer> input = sbList("0010 MyLabel1.CODE", "0020 IF (0010)");
            IInsertLabels il = labels(true, "L%d.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            assertTrue(result[1].contains("(MyLabel1.)"));
        }

        @Test
        @DisplayName("getExistingLabel: Zeile beginnt mit Punkt → kein Label")
        void existingLabelStartsWithDot() {
            List<StringBuffer> input = sbList("0010 .CODE", "0020 IF (0010)");
            IInsertLabels il = labels(true, "L%d.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            // getExistingLabel(".CODE") → Pattern "^[a-zA-Z]*[0-9]*[.]" → "." matches (0 letters, 0 digits, dot)
            assertNotNull(result);
        }

        @Test
        @DisplayName("getExistingLabel: Nur Buchstaben mit Punkt → Label erkannt")
        void existingLabelLettersOnly() {
            List<StringBuffer> input = sbList("0010 ABC.CODE", "0020 IF (0010)");
            IInsertLabels il = labels(true, "L%d.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            assertTrue(result[1].contains("(ABC.)"));
        }

        @Test
        @DisplayName("getExistingLabel: Nur Ziffern mit Punkt → Label erkannt")
        void existingLabelDigitsOnly() {
            List<StringBuffer> input = sbList("0010 123.CODE", "0020 IF (0010)");
            IInsertLabels il = labels(true, "L%d.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            // Pattern: ^[a-zA-Z]*[0-9]*[.] → "123." → 0 Buchstaben, "123", "." → match
            assertTrue(result[1].contains("(123.)"));
        }

        @Test
        @DisplayName("IInsertLabels: isInsertLabels=true aber getLabelFormat=null → NPE bei String.format")
        void labelFormatNull() {
            List<StringBuffer> input = sbList("0010 CODE", "0020 IF (0010)");
            IInsertLabels il = labels(true, null, false);
            assertThrows(NullPointerException.class, () ->
                    RenumberSource.removeLineNumbers(input, true, false, 5, 10, il));
        }

        @Test
        @DisplayName("Leere Zeile in der Liste (leerer StringBuffer)")
        void emptyLineInList() {
            List<StringBuffer> input = sbList("0010 CODE", "", "0030 END");
            String[] result = RenumberSource.removeLineNumbers(input, false, false, 5, 10, null);
            assertEquals("CODE", result[0]);
            assertEquals("", result[1]);
            assertEquals("END", result[2]);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  updateLineReferences — extreme Edge-Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateLineReferences – Extreme Edge-Cases")
    class UpdateLineReferencesExtremeTest {

        @Test
        @DisplayName("Leeres Array → leeres Array zurück")
        void emptyArray() {
            String[] result = RenumberSource.updateLineReferences(new String[0], 5, false);
            assertEquals(0, result.length);
        }

        @Test
        @DisplayName("Zeile ohne Klammer → unverändert")
        void noParens() {
            String[] source = {"WRITE X"};
            String[] result = RenumberSource.updateLineReferences(source, 5, false);
            assertEquals("WRITE X", result[0]);
        }

        @Test
        @DisplayName("delta=0 → ref bleibt gleich (ref+0=ref, ref<=i+1 → replace mit gleichem Wert)")
        void deltaZero() {
            String[] source = {"IF (0001)"};
            String[] result = RenumberSource.updateLineReferences(source, 0, false);
            // ref=1+0=1, 1>0 && 1<=1 → replace mit 0001
            assertTrue(result[0].contains("(0001)"));
        }

        @Test
        @DisplayName("delta sehr groß positiv → ref > i+1 → nicht ersetzt")
        void deltaVeryLargePositive() {
            String[] source = {"IF (0001)"};
            String[] result = RenumberSource.updateLineReferences(source, 9999, false);
            // ref=1+9999=10000, 10000<=1? nein → nicht ersetzt
            assertTrue(result[0].contains("(0001)"));
        }

        @Test
        @DisplayName("delta sehr negativ → ref <= 0 → nicht ersetzt")
        void deltaVeryNegative() {
            String[] source = {"IF (0001) "};
            String[] result = RenumberSource.updateLineReferences(source, -9999, false);
            assertTrue(result[0].contains("(0001)"));
        }

        @Test
        @DisplayName("Referenz genau am Anfang der Zeile")
        void refAtLineStart() {
            String[] source = {"X", "(0001)REST"};
            String[] result = RenumberSource.updateLineReferences(source, 1, false);
            // Zeile 1 (i=1): ref=1+1=2, 2<=2 → replace
            assertTrue(result[1].contains("(0002)"));
        }

        @Test
        @DisplayName("Mehrere Referenzen, nur erste gültig")
        void multipleRefsFirstValid() {
            String[] source = {"X", "IF (0001)(ABCD)"};
            String[] result = RenumberSource.updateLineReferences(source, 1, false);
            assertTrue(result[1].contains("(0002)"));
            assertTrue(result[1].contains("(ABCD)"));
        }

        @Test
        @DisplayName("Zeile ist nur eine öffnende Klammer")
        void lineIsJustOpenParen() {
            String[] source = {"("};
            // indexOf("(")=0, isLineReference(0, "(") → pos+6=6 > length=1 → false → kein Treffer
            String[] result = RenumberSource.updateLineReferences(source, 1, false);
            assertEquals("(", result[0]);
        }

        @Test
        @DisplayName("Zeile enthält viele Klammern ohne gültiges Ref-Format")
        void manyInvalidParens() {
            String[] source = {"(((("};
            String[] result = RenumberSource.updateLineReferences(source, 1, false);
            assertEquals("((((", result[0]);
        }

        @Test
        @DisplayName("Rückgabe-Array ist dasselbe Objekt wie Eingabe")
        void returnSameArray() {
            String[] source = {"WRITE X"};
            String[] result = RenumberSource.updateLineReferences(source, 1, false);
            assertSame(source, result);
        }

        @Test
        @DisplayName("Mutiert das Eingabe-Array (Seiteneffekt)")
        void mutatesInputArray() {
            String[] source = {"X", "IF (0001)"};
            RenumberSource.updateLineReferences(source, 1, false);
            // Zeile 1 wurde mutiert
            assertTrue(source[1].contains("(0002)"));
        }

        @Test
        @DisplayName("Referenz mit Komma-Ende in updateLineReferences")
        void refWithComma() {
            String[] source = {"X", "IF (0001,REST"};
            String[] result = RenumberSource.updateLineReferences(source, 1, false);
            assertTrue(result[1].contains("(0002,"));
        }

        @Test
        @DisplayName("Referenz mit Slash-Ende in updateLineReferences")
        void refWithSlash() {
            String[] source = {"X", "IF (0001/REST"};
            String[] result = RenumberSource.updateLineReferences(source, 1, false);
            assertTrue(result[1].contains("(0002/"));
        }

        @Test
        @DisplayName("Block-Kommentar vor Referenz → Kommentar erkannt")
        void blockCommentBeforeRef() {
            String[] source = {"/* (0001)"};
            String[] result = RenumberSource.updateLineReferences(source, 1, false);
            // Block-Kommentar → !insertLabelsMode=!false=true → Reference erkannt
            // Aber ref=1+1=2, 2<=1? nein → nicht ersetzt
            assertTrue(result[0].contains("(0001)"));
        }

        @Test
        @DisplayName("Einzelne Zeile mit gültiger Ref die genau auf sich selbst zeigt")
        void singleLineSelfRef() {
            String[] source = {"IF (0001)"};
            String[] result = RenumberSource.updateLineReferences(source, 0, false);
            // ref=1+0=1, 1>0 && 1<=1 → replace
            assertTrue(result[0].contains("(0001)"));
        }

        @Test
        @DisplayName("Null-Element im Array → NPE")
        void nullElementInArray() {
            String[] source = {null};
            assertThrows(NullPointerException.class, () ->
                    RenumberSource.updateLineReferences(source, 1, false));
        }

        @Test
        @DisplayName("Leerer String als Zeile → keine Klammer gefunden")
        void emptyStringLine() {
            String[] source = {""};
            String[] result = RenumberSource.updateLineReferences(source, 1, false);
            assertEquals("", result[0]);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  getExistingLabel (indirekt über removeLineNumbers)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getExistingLabel – indirekte Tests über removeLineNumbers")
    class GetExistingLabelIndirectTest {

        @Test
        @DisplayName("Zielzeile hat kein Label → neues Label wird generiert")
        void noExistingLabel() {
            List<StringBuffer> input = sbList("0010 CODE", "0020 IF (0010)");
            IInsertLabels il = labels(true, "L%d.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            assertTrue(result[1].contains("(L1.)"));
        }

        @Test
        @DisplayName("Zielzeile beginnt (nach trim) mit Ziffer+Punkt → Label erkannt")
        void labelStartsWithDigit() {
            List<StringBuffer> input = sbList("0010 9.REST", "0020 IF (0010)");
            IInsertLabels il = labels(true, "L%d.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            // getExistingLabel("9.REST") → "9." → match
            assertTrue(result[1].contains("(9.)"));
        }

        @Test
        @DisplayName("Zielzeile: Label mit Groß- und Kleinbuchstaben")
        void labelMixedCase() {
            List<StringBuffer> input = sbList("0010 AbCd1.REST", "0020 IF (0010)");
            IInsertLabels il = labels(true, "L%d.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            assertTrue(result[1].contains("(AbCd1.)"));
        }

        @Test
        @DisplayName("Zielzeile: Kein Punkt → kein Label")
        void noDotNoLabel() {
            List<StringBuffer> input = sbList("0010 CODE_WITHOUT_DOT", "0020 IF (0010)");
            IInsertLabels il = labels(true, "L%d.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            assertTrue(result[1].contains("(L1.)"));
        }

        @Test
        @DisplayName("Zielzeile: Punkt nicht am Anfang sondern mitten im Text")
        void dotInMiddle() {
            List<StringBuffer> input = sbList("0010 CODE.REST", "0020 IF (0010)");
            IInsertLabels il = labels(true, "L%d.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            // "CODE.REST" → getExistingLabel → Pattern ^[a-zA-Z]*[0-9]*[.] → "CODE." matches
            assertTrue(result[1].contains("(CODE.)"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  searchStringInSource (indirekt über removeLineNumbers Label-Kollision)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("searchStringInSource – indirekte Label-Kollisions-Tests")
    class SearchStringInSourceIndirectTest {

        @Test
        @DisplayName("Mehrfache Label-Kollision: getExistingLabel findet erstes Label → wird wiederverwendet")
        void multipleCollisions() {
            // Zielzeile trim(): "L1. L2. L3." → getExistingLabel → Pattern ^[a-zA-Z]*[0-9]*[.] → "L1." matches
            // → existierendes Label "L1." wird wiederverwendet
            List<StringBuffer> input = sbList("0010 L1. L2. L3.", "0020 IF (0010)");
            IInsertLabels il = labels(true, "L%d.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            assertTrue(result[1].contains("(L1.)"), "Expected L1. reuse but got: " + result[1]);
        }

        @Test
        @DisplayName("Label existiert als Teilstring → getExistingLabel findet trotzdem Label am Anfang")
        void labelAsSubstring() {
            // getExistingLabel("XL1.Y") → Pattern ^[a-zA-Z]*[0-9]*[.] → "XL1." matches → reuse
            List<StringBuffer> input = sbList("0010 XL1.Y", "0020 IF (0010)");
            IInsertLabels il = labels(true, "L%d.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            assertTrue(result[1].contains("(XL1.)"), "Expected XL1. but got: " + result[1]);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Kombinations-Tests
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Kombinations- und Integrations-EdgeCases")
    class CombinationTest {

        @Test
        @DisplayName("addLineNumbers + removeLineNumbers Roundtrip mit non-null prefix")
        void roundtripNonNullPrefix() {
            String[] original = {"WRITE X", "END"};
            StringBuffer[] numbered = RenumberSource.addLineNumbers(original, 10, "§", false, false, false);

            List<StringBuffer> asList = new ArrayList<>();
            for (StringBuffer sb : numbered) {
                asList.add(sb);
            }

            String[] restored = RenumberSource.removeLineNumbers(asList, false, false, 5, 10, null);
            assertArrayEquals(original, restored);
        }

        @Test
        @DisplayName("addLineNumbers + removeLineNumbers Roundtrip mit updateRefs")
        void roundtripWithUpdateRefs() {
            String[] original = {"IF (0001)", "END"};
            StringBuffer[] numbered = RenumberSource.addLineNumbers(original, 10, "§", true, false, false);

            List<StringBuffer> asList = new ArrayList<>();
            for (StringBuffer sb : numbered) {
                asList.add(sb);
            }

            String[] restored = RenumberSource.removeLineNumbers(asList, true, false, 5, 10, null);
            assertEquals("IF (0001)", restored[0]);
        }

        @Test
        @DisplayName("addLineNumbers + removeLineNumbers Roundtrip mit Labels")
        void roundtripWithLabels() {
            String[] source = {"!1. CODE", "IF (!1.) THEN"};
            StringBuffer[] numbered = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);

            List<StringBuffer> asList = new ArrayList<>();
            for (StringBuffer sb : numbered) {
                asList.add(sb);
            }

            String[] restored = RenumberSource.removeLineNumbers(asList, false, false, 5, 10, null);
            assertEquals("CODE", restored[0]);
            assertEquals("IF (0010) THEN", restored[1]);
        }

        @Test
        @DisplayName("updateLineReferences auf Ergebnis von addLineNumbers")
        void updateAfterAdd() {
            String[] source = {"CODE", "IF (0001)"};
            StringBuffer[] numbered = RenumberSource.addLineNumbers(source, 10, "§", true, false, false);

            String[] lines = new String[numbered.length];
            for (int i = 0; i < numbered.length; i++) {
                lines[i] = numbered[i].toString();
            }

            // Shift alle Refs um +10
            String[] updated = RenumberSource.updateLineReferences(lines, 10, false);
            // ref war 0010, +10=0020, aber 20 <= i+1=2? nein → nicht ersetzt
            assertTrue(updated[1].contains("(0010)"));
        }

        @Test
        @DisplayName("Sehr lange Zeile (1000+ Zeichen)")
        void veryLongLine() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 200; i++) {
                sb.append("XXXXX");
            }
            sb.append("(0001)");
            String[] source = {"CODE", sb.toString()};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "§", true, false, false);
            assertNotNull(result);
            // Ref am Ende: ref=1, i+1=2, 1<=2 → replace: 1*10=10
            assertTrue(result[1].toString().contains("(0010)"));
        }

        @Test
        @DisplayName("Nur Whitespace-Zeilen")
        void allWhitespaceLines() {
            String[] source = {"   ", "\t\t", "  \t "};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "§", false, false, false);
            assertEquals("0010    ", result[0].toString());
            assertEquals("0020 \t\t", result[1].toString());
        }

        @Test
        @DisplayName("Spezialzeichen in Zeilen: Umlaute, Sonderzeichen")
        void specialCharsInLines() {
            String[] source = {"WRITE 'Ä Ö Ü ß'", "END"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "§", false, false, false);
            assertEquals("0010 WRITE 'Ä Ö Ü ß'", result[0].toString());
        }

        @Test
        @DisplayName("Referenz (0000) in updateLineReferences")
        void refZeroInUpdate() {
            String[] source = {"IF (0000)"};
            String[] result = RenumberSource.updateLineReferences(source, 1, false);
            // ref=0+1=1, 1>0 && 1<=1 → replace → (0001)
            assertTrue(result[0].contains("(0001)"));
        }

        @Test
        @DisplayName("Referenz (9999) in updateLineReferences mit negativem delta")
        void refMaxInUpdate() {
            String[] source = new String[9999];
            Arrays.fill(source, "X");
            source[9998] = "IF (9999)";
            String[] result = RenumberSource.updateLineReferences(source, -1, false);
            // ref=9999+(-1)=9998, 9998>0 && 9998<=9999 → replace → (9998)
            assertTrue(result[9998].contains("(9998)"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Pattern/Regex Static-State-Tests
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Statische Pattern-Initialisierung")
    class StaticPatternTest {

        @Test
        @DisplayName("isLineReference: Mehrfachaufruf → Pattern wird wiederverwendet (kein Fehler)")
        void patternReuse() {
            for (int i = 0; i < 100; i++) {
                assertTrue(RenumberSource.isLineReference(0, "(0010)"));
            }
        }

        @Test
        @DisplayName("getExistingLabel: Mehrfachaufruf indirekt → Pattern wird wiederverwendet")
        void existingLabelPatternReuse() {
            for (int i = 0; i < 50; i++) {
                List<StringBuffer> input = sbList("0010 L1.CODE", "0020 IF (0010)");
                IInsertLabels il = labels(true, "L%d.", false);
                String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
                assertTrue(result[1].contains("(L1.)"));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  addLineNumbers Label-Mode: Whitespace-Backtrack-Bug (Zeile 108)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("addLineNumbers Label-Mode – Whitespace-Backtrack (Zeile 108 Bug)")
    class LabelModeWhitespaceBacktrackTest {

        @Test
        @DisplayName("Label-Prefix nach Leerzeichen → Whitespace-Backtrack-Schleife (&&-Bug)")
        void prefixAfterSpaces() {
            // Zeile 108: var19.charAt(var28) == ' ' && var19.charAt(var28) == '\t'
            // Das ist IMMER false → Schleife terminiert sofort → var28 bleibt bei var22-1
            // → var28 != -1 → var27 bleibt false → kein Label erkannt
            String[] source = {"!1. FIRST", "  !1. SECOND"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            // "  !1." → charAt(1)!='(' → else-Zweig → Backtrack-Schleife terminiert sofort
            // → var28=1, != -1 → kein Label → var20 bleibt 0
            assertNotNull(result);
        }

        @Test
        @DisplayName("Label-Prefix nach Tab → Whitespace-Backtrack-Schleife (&&-Bug)")
        void prefixAfterTab() {
            String[] source = {"!1. FIRST", "\t!1. SECOND"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertNotNull(result);
        }

        @Test
        @DisplayName("Label-Prefix nach Tab+Space → Whitespace-Backtrack-Schleife (&&-Bug)")
        void prefixAfterTabAndSpace() {
            String[] source = {"!1. FIRST", "\t !1. SECOND"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertNotNull(result);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  removeLineNumbers: Toter Code (Zeile 191-193)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("removeLineNumbers – Toter Code Pfad (var19 > var18)")
    class DeadCodePathTest {

        @Test
        @DisplayName("Vorwärts-Ref: ref > currentLineNo → else-Pfad erreicht, aber dead code")
        void forwardRefHitsDeadCode() {
            // var19=20 > var18=10 → else-Zweig: (20 <= 0 || 20 % 10 == 0) && 20 > 0 → true
            // var10000 = 20/10 = 2 → aber Variable wird nie verwendet
            List<StringBuffer> input = sbList("0010 IF (0020)", "0020 END");
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, null);
            // Ref bleibt unverändert (Vorwärts-Ref)
            assertEquals("IF (0020)", result[0]);
        }

        @Test
        @DisplayName("Vorwärts-Ref: ref nicht durch step teilbar → nichts passiert")
        void forwardRefNotDivisibleByStep() {
            List<StringBuffer> input = sbList("0010 IF (0015)", "0020 END");
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, null);
            assertEquals("IF (0015)", result[0]);
        }

        @Test
        @DisplayName("ref=0: (var19 <= 0 || ...) → true, aber var19 > 0 → false → nichts passiert")
        void refZeroDeadCode() {
            List<StringBuffer> input = sbList("0010 IF (0000)");
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, null);
            assertEquals("IF (0000)", result[0]);
        }
    }
}

