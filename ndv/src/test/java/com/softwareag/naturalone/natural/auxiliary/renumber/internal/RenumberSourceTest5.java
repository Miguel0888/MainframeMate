package com.softwareag.naturalone.natural.auxiliary.renumber.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cover previously missed (red) branches/lines in RenumberSource.
 */
class RenumberSourceTest5 {

    @Nested
    @DisplayName("addLineNumbers - cover normal mode updateRefs and trailing space")
    class AddLineNumbersNormalModeCoverage {

        @Test
        @DisplayName("updateRefs=true: cover backward/self ref replacement and forward ref non-replacement")
        void updateRefs_coverReplaceAndNoReplace() {
            // Use a labelPrefix that does not trigger label-mode.
            String labelPrefix = "§";

            // Line 1 has a forward reference (0002) -> must NOT be replaced (because 2 > currentLineIndex+1).
            // Line 2 has backward (0001) and self (0002) references -> both must be replaced.
            String[] source = {
                    "IF (0002) THEN",
                    "IF (0001) AND (0002) THEN"
            };

            StringBuffer[] out = RenumberSource.addLineNumbers(source, 10, labelPrefix, true, false, false);

            assertEquals("0010 IF (0002) THEN", out[0].toString(), "Forward reference must stay unchanged");
            assertEquals("0020 IF (0010) AND (0020) THEN", out[1].toString(), "Backward/self references must be renumbered");
        }

        @Test
        @DisplayName("openSystemsServer=true in normal mode: append trailing space")
        void openSystemsServer_appendsTrailingBlank_normalMode() {
            String[] source = {"WRITE X"};
            StringBuffer[] out = RenumberSource.addLineNumbers(source, 10, "§", false, true, false);

            assertEquals("0010 WRITE X ", out[0].toString(), "Expect trailing space when openSystemsServer=true");
        }

        @Test
        @DisplayName("updateRefs respects renConst: reference inside quotes changes only when renConst=true")
        void updateRefs_respectsRenConst_insideQuotes() {
            String[] source = {
                    "X",
                    "WRITE '(0001)'"
            };

            StringBuffer[] outRenConstFalse = RenumberSource.addLineNumbers(source, 10, "§", true, false, false);
            assertEquals("0020 WRITE '(0001)'", outRenConstFalse[1].toString(), "Expect unchanged reference in quotes when renConst=false");

            StringBuffer[] outRenConstTrue = RenumberSource.addLineNumbers(source, 10, "§", true, false, true);
            assertEquals("0020 WRITE '(0010)'", outRenConstTrue[1].toString(), "Expect changed reference in quotes when renConst=true");
        }
    }

    @Nested
    @DisplayName("addLineNumbers - cover label mode edge branches (non-red but often partial)")
    class AddLineNumbersLabelModeEdges {

        @Test
        @DisplayName("label definition without space after '.' must not skip first content char")
        void labelMode_noSpaceAfterDot_doesNotSkipChar() {
            // Ensure label-mode is active.
            String[] source = {"!1.CODE"};
            StringBuffer[] out = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);

            // If no space after dot, var20 must point to 'C' and not be incremented.
            assertEquals("0010 CODE", out[0].toString());
        }

        @Test
        @DisplayName("label definition with only trailing space must not throw and must produce only number+space")
        void labelMode_onlyLabelWithTrailingSpace_producesNumberPlusSpace() {
            // "!1. " (note trailing space) avoids StringIndexOutOfBoundsException and makes var20 == length.
            String[] source = {"!1. "};
            StringBuffer[] out = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);

            assertEquals("0010 ", out[0].toString(), "Expect number plus single space when no remaining content exists");
        }

        @Test
        @DisplayName("label reference replacement works only when prefix is directly after '('")
        void labelMode_referenceReplacement_onlyDirectAfterParen() {
            String[] source = {
                    "!1. WRITE X",
                    "IF (!1.) THEN",
                    "IF ( !1.) THEN"
            };

            StringBuffer[] out = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);

            assertEquals("0010 WRITE X", out[0].toString());
            assertEquals("0020 IF (0010) THEN", out[1].toString(), "Expect replacement when prefix is directly after '('");
            assertEquals("0030 IF ( !1.) THEN", out[2].toString(), "Expect no replacement when whitespace exists after '('");
        }
    }

    @Nested
    @DisplayName("updateLineReferences - cover the missing false branch of (var8 <= currentLine)")
    class UpdateLineReferencesCoverage {

        @Test
        @DisplayName("delta shifts reference beyond current line -> do not replace (covers false branch with var8>current)")
        void shiftBeyondCurrentLine_doesNotReplace() {
            // Single line: currentLineIndex+1 = 1, (0001)+1 => 2 -> must not be replaced.
            String[] source = {"IF (0001) THEN"};
            String[] out = RenumberSource.updateLineReferences(source, 1, false);

            assertEquals("IF (0001) THEN", out[0], "Expect unchanged because shifted ref points beyond current line");
        }
    }

    @Nested
    @DisplayName("extra 'nonsense' inputs to pin down behavior")
    class WeirdInputs {

        @Test
        @DisplayName("negative step produces negative line numbers (document current behavior)")
        void add_negativeStep_formatsNegativeNumbers() {
            String[] source = {"A", "B"};
            StringBuffer[] out = RenumberSource.addLineNumbers(source, -10, "§", false, false, false);

            // String.format("%04d", -10) yields "-010" (width includes sign).
            assertTrue(out[0].toString().startsWith("-010 "), "Expect negative formatted line number. Actual: " + out[0]);
        }

        @Test
        @DisplayName("empty labelPrefix triggers label-mode detection in surprising ways (document current behavior)")
        void add_emptyLabelPrefix_isWeirdButDeterministic() {
            // With empty prefix, indexOf("") is always 0 -> label-mode detection can become true if line begins with digits + '.'
            String[] source = {"1. X", "END"};
            StringBuffer[] out = RenumberSource.addLineNumbers(source, 10, "", false, false, false);

            // This test mainly locks the current behavior down (whatever it is).
            assertEquals(2, out.length);
            assertNotNull(out[0]);
            assertNotNull(out[1]);
        }

        @Test
        @DisplayName("label collision with invalid labelFormat can explode (avoid infinite loops by expecting exception)")
        void remove_invalidLabelFormat_throws() {
            // If labelFormat is null, String.format(...) throws NPE when label creation is needed.
            IInsertLabels insertLabels = new IInsertLabels() {
                @Override public boolean isInsertLabels() { return true; }
                @Override public String getLabelFormat() { return null; }
                @Override public boolean isCreateNewLine() { return false; }
            };

            // Create a situation where label creation is required (reference rewrite with labels).
            // Expect NPE from String.format(null,...).
            assertThrows(NullPointerException.class, () ->
                    RenumberSource.removeLineNumbers(
                            Arrays.asList(new StringBuffer("0010 X"), new StringBuffer("0020 IF (0010)")),
                            true, false, 5, 10, insertLabels
                    ));
        }
    }
}
