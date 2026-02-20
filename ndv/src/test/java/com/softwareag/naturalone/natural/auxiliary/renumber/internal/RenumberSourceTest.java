package com.softwareag.naturalone.natural.auxiliary.renumber.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für RenumberSource — abgeleitet aus der Anforderungsspezifikation.
 * <p>
 * Gliederung:
 * <ol>
 *   <li>§5 isLineReference</li>
 *   <li>§6 isLineNumberReference</li>
 *   <li>§3 addLineNumbers</li>
 *   <li>§4 removeLineNumbers</li>
 *   <li>§7 updateLineReferences</li>
 *   <li>§8 Akzeptanzkriterien (Integrationstests)</li>
 * </ol>
 */
class RenumberSourceTest {

    // ═══════════════════════════════════════════════════════════════════
    //  Hilfs-IInsertLabels-Implementierung für Tests
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

    private static String[] toStrings(StringBuffer[] bufs) {
        String[] result = new String[bufs.length];
        for (int i = 0; i < bufs.length; i++) {
            result[i] = bufs[i].toString();
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  §5  isLineReference
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("§5 isLineReference")
    class IsLineReferenceTest {

        @Test
        @DisplayName("Erkennt gültige Reference mit Closing-Paren")
        void validRefClosingParen() {
            // "(0010)" → 6 Zeichen, Pattern \([0-9]{4}[\)/,]
            assertTrue(RenumberSource.isLineReference(0, "(0010)"));
        }

        @Test
        @DisplayName("Erkennt gültige Reference mit Slash")
        void validRefSlash() {
            assertTrue(RenumberSource.isLineReference(0, "(0010/"));
        }

        @Test
        @DisplayName("Erkennt gültige Reference mit Komma")
        void validRefComma() {
            assertTrue(RenumberSource.isLineReference(0, "(0010,"));
        }

        @Test
        @DisplayName("Erkennt Reference mitten in einer Zeile")
        void validRefMidLine() {
            String line = "IF (0010) THEN";
            assertTrue(RenumberSource.isLineReference(3, line));
        }

        @Test
        @DisplayName("Zu kurz → false")
        void tooShort() {
            assertFalse(RenumberSource.isLineReference(0, "(001)"));
        }

        @Test
        @DisplayName("pos + 6 > length → false")
        void posOutOfBounds() {
            assertFalse(RenumberSource.isLineReference(2, "(0010)"));
        }

        @Test
        @DisplayName("Keine Klammer → false")
        void noBracket() {
            assertFalse(RenumberSource.isLineReference(0, "X0010)"));
        }

        @Test
        @DisplayName("Nicht-numerisch → false")
        void nonNumeric() {
            assertFalse(RenumberSource.isLineReference(0, "(ABCD)"));
        }

        @Test
        @DisplayName("Falsches Endzeichen → false")
        void wrongEndChar() {
            assertFalse(RenumberSource.isLineReference(0, "(0010X"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  §6  isLineNumberReference
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("§6 isLineNumberReference")
    class IsLineNumberReferenceTest {

        @Test
        @DisplayName("pos == -1 → false")
        void posMinusOne() {
            assertFalse(RenumberSource.isLineNumberReference(-1, "(0010)", false, false, false));
        }

        @Test
        @DisplayName("Keine gültige lineRef → false")
        void noValidLineRef() {
            assertFalse(RenumberSource.isLineNumberReference(0, "XXXXXX", false, false, false));
        }

        @Test
        @DisplayName("Normale Reference ohne Kontext → true")
        void normalRefNoContext() {
            assertTrue(RenumberSource.isLineNumberReference(3, "IF (0010) THEN", false, false, false));
        }

        // ── Kommentar-Erkennung ────────────────────────────────────

        @Test
        @DisplayName("'* ' am Offset 0 (kein Prefix), insertLabelsMode=false → true (weil !insertLabelsMode)")
        void commentStarSpaceNoPrefix() {
            // Offset=0, line starts with "* "
            String line = "* text (0010)";
            assertTrue(RenumberSource.isLineNumberReference(7, line, false, false, false));
        }

        @Test
        @DisplayName("'* ' am Offset 0, insertLabelsMode=true → false")
        void commentStarSpaceInsertLabels() {
            String line = "* text (0010)";
            assertFalse(RenumberSource.isLineNumberReference(7, line, true, false, false));
        }

        @Test
        @DisplayName("'* ' am Offset 5 (mit Prefix), insertLabelsMode=false → true")
        void commentStarSpaceWithPrefix() {
            // hasLineNumberPrefix=true → offset=5, prüft ab Index 5
            String line = "0010 * text (0010)";
            assertTrue(RenumberSource.isLineNumberReference(12, line, false, true, false));
        }

        @Test
        @DisplayName("'* ' am Offset 5, insertLabelsMode=true → false")
        void commentStarSpaceWithPrefixInsertLabels() {
            String line = "0010 * text (0010)";
            assertFalse(RenumberSource.isLineNumberReference(12, line, true, true, false));
        }

        @Test
        @DisplayName("'**' am Offset 0, insertLabelsMode=false → true")
        void commentDoubleStarNoPrefix() {
            String line = "** text (0010)";
            assertTrue(RenumberSource.isLineNumberReference(8, line, false, false, false));
        }

        @Test
        @DisplayName("'**' am Offset 0, insertLabelsMode=true → false")
        void commentDoubleStarInsertLabels() {
            String line = "** text (0010)";
            assertFalse(RenumberSource.isLineNumberReference(8, line, true, false, false));
        }

        @Test
        @DisplayName("'*/' am Offset 0, insertLabelsMode=false → true")
        void commentStarSlashNoPrefix() {
            String line = "*/ text (0010)";
            assertTrue(RenumberSource.isLineNumberReference(8, line, false, false, false));
        }

        // ── Block-Kommentar /* ──────────────────────────────────────

        @Test
        @DisplayName("Block-Kommentar /* vor Reference, insertLabelsMode=false → true (!insertLabelsMode)")
        void blockCommentBeforeRef() {
            String line = "code /* (0010)";
            assertTrue(RenumberSource.isLineNumberReference(8, line, false, false, false));
        }

        @Test
        @DisplayName("Block-Kommentar /* vor Reference, insertLabelsMode=true → false")
        void blockCommentBeforeRefInsertLabels() {
            String line = "code /* (0010)";
            assertFalse(RenumberSource.isLineNumberReference(8, line, true, false, false));
        }

        // ── String-Literale (Quotes) ────────────────────────────────

        @Test
        @DisplayName("Reference in Single-Quotes, renConst=false → false")
        void refInSingleQuotesRenConstFalse() {
            String line = "WRITE '(0010)'";
            assertFalse(RenumberSource.isLineNumberReference(7, line, false, false, false));
        }

        @Test
        @DisplayName("Reference in Single-Quotes, renConst=true → true")
        void refInSingleQuotesRenConstTrue() {
            String line = "WRITE '(0010)'";
            assertTrue(RenumberSource.isLineNumberReference(7, line, false, false, true));
        }

        @Test
        @DisplayName("Reference in Double-Quotes, renConst=false → false")
        void refInDoubleQuotesRenConstFalse() {
            String line = "WRITE \"(0010)\"";
            assertFalse(RenumberSource.isLineNumberReference(7, line, false, false, false));
        }

        @Test
        @DisplayName("Reference in Double-Quotes, renConst=true → true")
        void refInDoubleQuotesRenConstTrue() {
            String line = "WRITE \"(0010)\"";
            assertTrue(RenumberSource.isLineNumberReference(7, line, false, false, true));
        }

        @Test
        @DisplayName("Reference NACH geschlossenen Quotes → true (nicht mehr in String)")
        void refAfterClosedQuotes() {
            String line = "'text' (0010)";
            assertTrue(RenumberSource.isLineNumberReference(7, line, false, false, false));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  §3  addLineNumbers
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("§3 addLineNumbers")
    class AddLineNumbersTest {

        // ── 3.1 step-Normalisierung ─────────────────────────────────

        @Test
        @DisplayName("step=0 → wird zu step=1")
        void stepZeroBecomesOne() {
            String[] source = {"A", "B"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 0, null, false, false, false);
            assertEquals("0001 A", result[0].toString());
            assertEquals("0002 B", result[1].toString());
        }

        @Test
        @DisplayName("step*length > 9999 → step wird reduziert")
        void stepReductionWhenOverflow() {
            // 1001 Zeilen * 10 = 10010 > 9999 → step muss angepasst werden
            // 9999/1001 = 9 < 10 → step/2=5; 9999/1001=9 >= 5 → step=5
            String[] source = new String[1001];
            Arrays.fill(source, "X");
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, null, false, false, false);
            assertEquals("0005 X", result[0].toString());
            assertEquals("0010 X", result[1].toString());
        }

        @Test
        @DisplayName("step reduction bis auf 1 bei sehr vielen Zeilen")
        void stepReductionToOne() {
            // 5001 Zeilen: step=10 → 10*5001>9999 → step=10;
            // 9999/5001=1 < 10 → step=5; 9999/5001=1 < 5 → step=2; 9999/5001=1 < 2 → step=1
            String[] source = new String[5001];
            Arrays.fill(source, "X");
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, null, false, false, false);
            assertEquals("0001 X", result[0].toString());
            assertEquals("0002 X", result[1].toString());
        }

        // ── 3.3.1 Normaler Modus ────────────────────────────────────

        @Test
        @DisplayName("Einfache Nummerierung ohne Extras")
        void simpleNumbering() {
            String[] source = {"WRITE 'X'", "END"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, null, false, false, false);
            assertEquals("0010 WRITE 'X'", result[0].toString());
            assertEquals("0020 END", result[1].toString());
        }

        @Test
        @DisplayName("openSystemsServer=true → hängt Leerzeichen hinten an")
        void openSystemsServerTrailingSpace() {
            String[] source = {"WRITE 'X'"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, null, false, true, false);
            assertEquals("0010 WRITE 'X' ", result[0].toString());
        }

        @Test
        @DisplayName("Leeres source → leeres Ergebnis")
        void emptySource() {
            StringBuffer[] result = RenumberSource.addLineNumbers(new String[0], 10, null, false, false, false);
            assertEquals(0, result.length);
        }

        @Test
        @DisplayName("null source → leeres Ergebnis")
        void nullSource() {
            StringBuffer[] result = RenumberSource.addLineNumbers(null, 10, null, false, false, false);
            assertEquals(0, result.length);
        }

        // ── 3.3.1 updateRefs im Normalmodus ─────────────────────────

        @Test
        @DisplayName("updateRefs=true, Self-Ref wird umgerechnet")
        void updateRefsSelfRef() {
            // Zeile 1 (Index 0): ref=1, i+1=1, ref<=i+1 → replace 1*10=0010
            String[] source = {"IF (0001) ", "END-IF"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, null, true, false, false);
            assertTrue(result[0].toString().contains("(0010)"));
        }

        @Test
        @DisplayName("updateRefs=true, Vorwärts-Ref wird NICHT umgerechnet")
        void updateRefsForwardRefIgnored() {
            // Zeile 1 (Index 0): ref=2, i+1=1, ref > i+1 → kein Replace
            String[] source = {"IF (0002) ", "END-IF"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, null, true, false, false);
            assertTrue(result[0].toString().contains("(0002)"));
        }

        @Test
        @DisplayName("updateRefs=true, Rückwärts-Ref wird umgerechnet")
        void updateRefsBackwardRef() {
            // Zeile 2 (Index 1): ref=1, i+1=2, ref<=i+1 → replace 1*10=0010
            String[] source = {"WRITE X", "IF (0001) "};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, null, true, false, false);
            assertTrue(result[1].toString().contains("(0010)"));
        }

        @Test
        @DisplayName("updateRefs=false → References bleiben unverändert")
        void updateRefsFalse() {
            String[] source = {"IF (0001) ", "END-IF"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, null, false, false, false);
            assertTrue(result[0].toString().contains("(0001)"));
        }

        // ── 3.2 + 3.3.2 Label-Mode ─────────────────────────────────

        @Test
        @DisplayName("Label-Mode aktiviert wenn labelPrefix am Zeilenanfang mit numerischem Teil vor '.'")
        void labelModeActivated() {
            String[] source = {"!1. WRITE X", "IF (!1.) THEN"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            // Label-Definition "!1." an Index 0 → labelMap["!1."] = "0010"
            // Zeile 0: Label entfernt → "WRITE X"
            assertEquals("0010 WRITE X", result[0].toString());
            // Zeile 1: Label-Ref (!1.) → ersetzt durch "0010"
            assertEquals("0020 IF (0010) THEN", result[1].toString());
        }

        @Test
        @DisplayName("Label-Mode: Label mit Leerzeichen nach Punkt wird korrekt getrimmt")
        void labelModeSpaceAfterDot() {
            String[] source = {"!5. CODE", "END"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0010 CODE", result[0].toString());
            assertEquals("0020 END", result[1].toString());
        }

        @Test
        @DisplayName("Label-Mode nicht aktiviert wenn labelPrefix nicht am Index 0")
        void labelModeNotActivatedMidLine() {
            // Leerzeichen vor "!" → kein labelMode
            String[] source = {" !1. WRITE X", "END"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            // Normaler Modus
            assertEquals("0010  !1. WRITE X", result[0].toString());
        }

        @Test
        @DisplayName("Label-Mode nicht aktiviert wenn kein Punkt nach Prefix")
        void labelModeNoDot() {
            String[] source = {"!1 WRITE X", "END"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0010 !1 WRITE X", result[0].toString());
        }

        @Test
        @DisplayName("Label-Mode nicht aktiviert wenn Teil zwischen Prefix und Punkt nicht numerisch")
        void labelModeNonNumeric() {
            String[] source = {"!ABC. WRITE X", "END"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0010 !ABC. WRITE X", result[0].toString());
        }

        @Test
        @DisplayName("Label-Mode: Zeile ohne Label-Definition behält Inhalt")
        void labelModeNonLabelLine() {
            String[] source = {"!1. WRITE X", "COMPUTE Y", "IF (!1.) GOTO"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0010 WRITE X", result[0].toString());
            assertEquals("0020 COMPUTE Y", result[1].toString());
            assertEquals("0030 IF (0010) GOTO", result[2].toString());
        }

        @Test
        @DisplayName("Label-Mode: openSystemsServer bei Inhalt")
        void labelModeOpenSystems() {
            String[] source = {"!1. WRITE X"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, true, false);
            assertEquals("0010 WRITE X ", result[0].toString());
        }

        @Test
        @DisplayName("Label-Mode: Zeile nur mit Label-Definition, kein weiterer Inhalt")
        void labelModeOnlyLabel() {
            // "!1." → startIndexAfterLabel=3, line.length()=3, sIdx >= line.length() → kein Inhalt
            String[] source = {"!1."};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            // Mindestens "dddd "
            assertEquals("0010 ", result[0].toString());
        }

        @Test
        @DisplayName("Label-Mode: Label-Ref mit Slash-Ende")
        void labelModeRefSlash() {
            String[] source = {"!1. WRITE X", "IF (!1./ THEN"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0020 IF (0010/ THEN", result[1].toString());
        }

        @Test
        @DisplayName("Label-Mode: Label-Ref mit Komma-Ende")
        void labelModeRefComma() {
            String[] source = {"!1. WRITE X", "IF (!1., THEN"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0020 IF (0010, THEN", result[1].toString());
        }

        @Test
        @DisplayName("Label-Mode: Unbekanntes Label-Ref bleibt unverändert")
        void labelModeUnknownRef() {
            String[] source = {"!1. WRITE X", "IF (!99.) THEN"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            // "!99." ist nicht im labelMap → bleibt unverändert
            assertEquals("0020 IF (!99.) THEN", result[1].toString());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  §4  removeLineNumbers
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("§4 removeLineNumbers")
    class RemoveLineNumbersTest {

        // ── 4.1 Grundverhalten (Prefix entfernen) ───────────────────

        @Test
        @DisplayName("Prefix entfernen (prefixLength=5)")
        void removePrefixLength5() {
            List<StringBuffer> input = sbList("0010 WRITE X", "0020 END");
            String[] result = RenumberSource.removeLineNumbers(input, false, false, 5, 10, null);
            assertEquals("WRITE X", result[0]);
            assertEquals("END", result[1]);
        }

        @Test
        @DisplayName("Zeile mit genau 4 Zeichen → leerer String")
        void lineExactly4Chars() {
            List<StringBuffer> input = sbList("0010");
            String[] result = RenumberSource.removeLineNumbers(input, false, false, 5, 10, null);
            assertEquals("", result[0]);
        }

        @Test
        @DisplayName("Leere Eingabe → leeres Array")
        void emptyInput() {
            String[] result = RenumberSource.removeLineNumbers(new ArrayList<>(), false, false, 5, 10, null);
            assertEquals(0, result.length);
        }

        @Test
        @DisplayName("null Eingabe → leeres Array")
        void nullInput() {
            String[] result = RenumberSource.removeLineNumbers(null, false, false, 5, 10, null);
            assertEquals(0, result.length);
        }

        // ── 4.3 Reference-Umschreiben ───────────────────────────────

        @Test
        @DisplayName("Self-Reference wird umgeschrieben (0010 → 0001)")
        void selfRefRewritten() {
            List<StringBuffer> input = sbList("0010 IF (0010)", "0020 END");
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, null);
            assertEquals("IF (0001)", result[0]);
        }

        @Test
        @DisplayName("Rückwärts-Reference wird umgeschrieben")
        void backwardRefRewritten() {
            List<StringBuffer> input = sbList("0010 WRITE X", "0020 IF (0010)");
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, null);
            assertEquals("IF (0001)", result[1]);
        }

        @Test
        @DisplayName("Vorwärts-Reference wird NICHT umgeschrieben (refLineNo > currentLineNo)")
        void forwardRefNotRewritten() {
            List<StringBuffer> input = sbList("0010 IF (0020)", "0020 END");
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, null);
            assertEquals("IF (0020)", result[0]);
        }

        @Test
        @DisplayName("updateRefs=false → Reference bleibt unverändert")
        void updateRefsFalse() {
            List<StringBuffer> input = sbList("0010 IF (0010)", "0020 END");
            String[] result = RenumberSource.removeLineNumbers(input, false, false, 5, 10, null);
            assertEquals("IF (0010)", result[0]);
        }

        @Test
        @DisplayName("Reference mit refLineNo=0 wird nicht umgeschrieben")
        void refZeroNotRewritten() {
            List<StringBuffer> input = sbList("0010 IF (0000)");
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, null);
            assertEquals("IF (0000)", result[0]);
        }

        // ── 4.3 renConst-Handling ───────────────────────────────────

        @Test
        @DisplayName("Reference in Quotes, renConst=false → nicht umgeschrieben")
        void refInQuotesRenConstFalse() {
            List<StringBuffer> input = sbList("0010 WRITE '(0010)'");
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, null);
            assertEquals("WRITE '(0010)'", result[0]);
        }

        @Test
        @DisplayName("Reference in Quotes, renConst=true → wird umgeschrieben")
        void refInQuotesRenConstTrue() {
            List<StringBuffer> input = sbList("0010 WRITE '(0010)'");
            String[] result = RenumberSource.removeLineNumbers(input, true, true, 5, 10, null);
            assertEquals("WRITE '(0001)'", result[0]);
        }

        // ── 4.2 + 4.4 Label-Modus (inline) ─────────────────────────

        @Test
        @DisplayName("Labels inline: Reference wird durch Label ersetzt")
        void labelsInline() {
            List<StringBuffer> input = sbList("0010 WRITE X", "0020 IF (0010)");
            IInsertLabels il = labels(true, "L%d.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            // Reference (0010) → Zielzeile Index 0, Label z.B. "L1."
            // Zeile 0 bekommt Label: replace(0,4,"L1.") → "L1. WRITE X"
            assertEquals("L1. WRITE X", result[0]);
            // Zeile 1: Reference enthält das Label
            assertEquals("IF (L1.)", result[1]);
        }

        @Test
        @DisplayName("Labels inline: insertLabels=null → kein Label-Modus")
        void labelsNull() {
            List<StringBuffer> input = sbList("0010 IF (0010)", "0020 END");
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, null);
            assertEquals("IF (0001)", result[0]);
        }

        @Test
        @DisplayName("Labels inline: isInsertLabels=false → kein Label-Modus")
        void labelsInsertFalse() {
            List<StringBuffer> input = sbList("0010 IF (0010)", "0020 END");
            IInsertLabels il = labels(false, "L%d.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            assertEquals("IF (0001)", result[0]);
        }

        // ── 4.4 Label-Modus (createNewLine=true) ────────────────────

        @Test
        @DisplayName("Labels als eigene Zeile: Label wird vor Zielzeile eingefügt")
        void labelsNewLine() {
            List<StringBuffer> input = sbList("0010 WRITE X", "0020 IF (0010)");
            IInsertLabels il = labels(true, "L%d.", true);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            // Zielzeile (0010) bekommt Label als eigene Zeile davor
            assertEquals(3, result.length);
            assertEquals("L1.", result[0]);
            assertEquals("WRITE X", result[1]);
            assertEquals("IF (L1.)", result[2]);
        }

        // ── 4.3.4 Label in Kommentar-Kontext nicht erlaubt ──────────

        @Test
        @DisplayName("Label-Ref in Kommentar: isLineNumberReference mit insertLabelsMode=true → false → numerisch")
        void labelRefInComment() {
            // Zeile beginnt (nach Prefix offset=5) mit "* " → Kommentar
            // insertLabelsMode=true → false → numerische Ersetzung
            List<StringBuffer> input = sbList("0010 WRITE X", "0020 * REF (0010)");
            IInsertLabels il = labels(true, "L%d.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            // In Kommentar-Kontext: labelRefIsAllowed=false → numerisch
            // Zeile 0 sollte KEIN Label bekommen (da Label nur über labelRefIsAllowed erzeugt)
            // Zeile 1: numerische Ersetzung → (0001)
            assertEquals("* REF (0001)", result[1]);
        }

        // ── Existing Label on Target Line ───────────────────────────

        @Test
        @DisplayName("Zielzeile hat bereits ein existierendes Label → wird wiederverwendet")
        void existingLabelOnTargetLine() {
            // Zielzeile hat nach den 4 Ziffern " L1.CODE" → getExistingLabel("L1.CODE") → "L1."
            List<StringBuffer> input = sbList("0010 L1.CODE", "0020 IF (0010)");
            IInsertLabels il = labels(true, "L%d.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            // Existing Label "L1." should be reused
            assertTrue(result[1].contains("(L1.)"));
        }

        // ── Label collision: label already exists in source ─────────

        @Test
        @DisplayName("Label-Kollision: nächstes Label wird generiert wenn aktuelles existiert")
        void labelCollision() {
            // "L1." kommt bereits im Source vor → nächstes Label "L2." wird generiert
            List<StringBuffer> input = sbList("0010 WRITE L1.", "0020 IF (0010)");
            IInsertLabels il = labels(true, "L%d.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            // "L1." existiert bereits → sollte "L2." verwenden
            assertTrue(result[1].contains("(L2.)"), "Expected L2. but got: " + result[1]);
        }

        // ── Multiple references to same line ────────────────────────

        @Test
        @DisplayName("Mehrere Referenzen auf gleiche Zeile → gleiches Label")
        void multipleRefsSameLine() {
            List<StringBuffer> input = sbList("0010 WRITE X", "0020 IF (0010)", "0030 GOTO (0010)");
            IInsertLabels il = labels(true, "L%d.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            // Beide Refs auf 0010 → gleiches Label
            String label1 = extractLabelFromRef(result[1]);
            String label2 = extractLabelFromRef(result[2]);
            assertEquals(label1, label2);
        }

        private String extractLabelFromRef(String line) {
            int start = line.indexOf('(');
            int end = line.indexOf(')');
            if (start >= 0 && end > start) {
                return line.substring(start + 1, end);
            }
            return "";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  §7  updateLineReferences
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("§7 updateLineReferences")
    class UpdateLineReferencesTest {

        @Test
        @DisplayName("delta positiv: Reference wird verschoben")
        void deltaPositive() {
            // Zeile 0 (i=0): ref=0001+1=0002, aber ref<=i+1=1? 2>1 → nicht ersetzt
            // Zeile 1 (i=1): ref=0001+1=0002, ref<=i+1=2? ja → ersetzt
            String[] source = {"WRITE X", "IF (0001) "};
            String[] result = RenumberSource.updateLineReferences(source, 1, false);
            // Zeile 1: ref=1+1=2, 2<=2 → replace → (0002)
            assertTrue(result[1].contains("(0002)"));
        }

        @Test
        @DisplayName("delta negativ: ref <= 0 → nicht ersetzt")
        void deltaNegativeRefZero() {
            // ref=0001 + (-1) = 0 → ref <= 0 → nicht ersetzt
            String[] source = {"IF (0001) "};
            String[] result = RenumberSource.updateLineReferences(source, -1, false);
            assertTrue(result[0].contains("(0001)"));
        }

        @Test
        @DisplayName("null source → null")
        void nullSource() {
            assertNull(RenumberSource.updateLineReferences(null, 1, false));
        }

        @Test
        @DisplayName("Reference in Quotes, renConst=false → nicht geändert")
        void refInQuotesRenConstFalse() {
            String[] source = {"'(0001)'"};
            String[] result = RenumberSource.updateLineReferences(source, 1, false);
            assertTrue(result[0].contains("(0001)"));
        }

        @Test
        @DisplayName("Reference in Quotes, renConst=true → wird geändert")
        void refInQuotesRenConstTrue() {
            String[] source = {"X", "'(0001)'"};
            String[] result = RenumberSource.updateLineReferences(source, 1, true);
            // Zeile 1 (i=1): ref=1+1=2, 2<=2 → replace
            assertTrue(result[1].contains("(0002)"), "Expected (0002) but got: " + result[1]);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  §8  Akzeptanzkriterien (Integrationstests)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("§8 Akzeptanzkriterien")
    class AcceptanceCriteriaTest {

        @Test
        @DisplayName("§8.1 Upload (Normal)")
        void acceptance81_uploadNormal() {
            String[] source = {"WRITE 'X'", "END"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, null, false, false, false);
            assertEquals("0010 WRITE 'X'", result[0].toString());
            assertEquals("0020 END", result[1].toString());
        }

        @Test
        @DisplayName("§8.2 Upload (References umrechnen)")
        void acceptance82_uploadReferences() {
            String[] source = {"IF (0001) ", "END-IF"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, null, true, false, false);
            assertTrue(result[0].toString().contains("(0010)"),
                    "Expected (0010) in: " + result[0]);
        }

        @Test
        @DisplayName("§8.3 Download (Prefix entfernen + Reference normalisieren)")
        void acceptance83_downloadPrefixAndRef() {
            List<StringBuffer> input = sbList("0010 IF (0010)", "0020 END");
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, null);
            assertEquals("IF (0001)", result[0]);
            assertEquals("END", result[1]);
        }

        @Test
        @DisplayName("§8.4 Download (Labels inline)")
        void acceptance84_downloadLabelsInline() {
            List<StringBuffer> input = sbList("0010 WRITE X", "0020 IF (0010)");
            IInsertLabels il = labels(true, "L%d.", false);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            assertEquals("L1. WRITE X", result[0]);
            assertEquals("IF (L1.)", result[1]);
        }

        @Test
        @DisplayName("§8.5 Download (Labels als eigene Zeile)")
        void acceptance85_downloadLabelsNewLine() {
            List<StringBuffer> input = sbList("0010 WRITE X", "0020 IF (0010)");
            IInsertLabels il = labels(true, "L%d.", true);
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);
            assertEquals(3, result.length, "Expected 3 lines but got: " + Arrays.toString(result));
            assertEquals("L1.", result[0]);
            assertEquals("WRITE X", result[1]);
            assertEquals("IF (L1.)", result[2]);
        }

        @Test
        @DisplayName("§8.6 renConst=false: Reference in Strings NICHT geändert")
        void acceptance86_renConstFalse() {
            List<StringBuffer> input = sbList("0010 WRITE '(0010)'");
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, null);
            assertEquals("WRITE '(0010)'", result[0]);
        }

        @Test
        @DisplayName("§8.6 renConst=true: Reference in Strings WIRD geändert")
        void acceptance86_renConstTrue() {
            List<StringBuffer> input = sbList("0010 WRITE '(0010)'");
            String[] result = RenumberSource.removeLineNumbers(input, true, true, 5, 10, null);
            assertEquals("WRITE '(0001)'", result[0]);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Zusätzliche Randfall-Tests
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Randfälle")
    class EdgeCaseTests {

        @Test
        @DisplayName("addLineNumbers: step=1 erzeugt korrekte Nummern")
        void stepOne() {
            String[] source = {"A", "B", "C"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 1, null, false, false, false);
            assertEquals("0001 A", result[0].toString());
            assertEquals("0002 B", result[1].toString());
            assertEquals("0003 C", result[2].toString());
        }

        @Test
        @DisplayName("removeLineNumbers: Mehrere References in einer Zeile")
        void multipleRefsInOneLine() {
            List<StringBuffer> input = sbList("0010 X", "0020 Y", "0030 IF (0010)(0020)");
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, null);
            // Ref (0010) → Zeile 0 → (0001), Ref (0020) → Zeile 1 → (0002)
            assertEquals("IF (0001)(0002)", result[2]);
        }

        @Test
        @DisplayName("removeLineNumbers: Reference zeigt auf nicht existierende Zeile → bleibt unverändert")
        void refToNonExistentLine() {
            List<StringBuffer> input = sbList("0010 X", "0020 IF (0005)");
            String[] result = RenumberSource.removeLineNumbers(input, true, false, 5, 10, null);
            // 0005 existiert nicht → Reference bleibt
            assertEquals("IF (0005)", result[1]);
        }

        @Test
        @DisplayName("addLineNumbers: null Zeile im Array wird als leer behandelt")
        void nullLineInArray() {
            String[] source = {null, "END"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, null, false, false, false);
            assertEquals("0010 ", result[0].toString());
            assertEquals("0020 END", result[1].toString());
        }

        @Test
        @DisplayName("isLineNumberReference: hasLineNumberPrefix=true, korrekte Offset-Berechnung")
        void hasLineNumberPrefixOffset() {
            // "0010 * (0010)" → offset=5, ab offset="* (0010)" → starts with "* " → !insertLabelsMode
            String line = "0010 * (0010)";
            assertTrue(RenumberSource.isLineNumberReference(7, line, false, true, false));
            assertFalse(RenumberSource.isLineNumberReference(7, line, true, true, false));
        }

        @Test
        @DisplayName("addLineNumbers: mehrere Labels in Label-Mode")
        void multipleLabelsMode() {
            String[] source = {"!1. FIRST", "MIDDLE", "!2. LAST", "GOTO (!1.) OR (!2.)"};
            StringBuffer[] result = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);
            assertEquals("0010 FIRST", result[0].toString());
            assertEquals("0020 MIDDLE", result[1].toString());
            assertEquals("0030 LAST", result[2].toString());
            assertEquals("0040 GOTO (0010) OR (0030)", result[3].toString());
        }

        @Test
        @DisplayName("Roundtrip: addLineNumbers → removeLineNumbers ergibt Original zurück")
        void roundtrip() {
            String[] original = {"WRITE 'X'", "IF TRUE", "END"};
            StringBuffer[] numbered = RenumberSource.addLineNumbers(original, 10, null, false, false, false);

            List<StringBuffer> asList = new ArrayList<>();
            for (StringBuffer sb : numbered) {
                asList.add(sb);
            }

            String[] restored = RenumberSource.removeLineNumbers(asList, false, false, 5, 10, null);
            assertArrayEquals(original, restored);
        }

        @Test
        @DisplayName("Roundtrip mit References: addLineNumbers → removeLineNumbers")
        void roundtripWithRefs() {
            String[] original = {"IF (0001) ", "END-IF"};
            // Upload: refs umrechnen
            StringBuffer[] numbered = RenumberSource.addLineNumbers(original, 10, null, true, false, false);
            // numbered[0] enthält "(0010)"

            List<StringBuffer> asList = new ArrayList<>();
            for (StringBuffer sb : numbered) {
                asList.add(sb);
            }

            // Download: refs zurückrechnen
            String[] restored = RenumberSource.removeLineNumbers(asList, true, false, 5, 10, null);
            assertEquals("IF (0001) ", restored[0]);
            assertEquals("END-IF", restored[1]);
        }
    }
}

