package de.bund.zrb.ndv.renumber;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Add missing behavioral tests that are not covered by RenumberSourceTest.
 */
class RenumberSourceTest3 {

    private static IInsertLabels labels(boolean insert, String format, boolean newLine) {
        return new IInsertLabels() {
            @Override public boolean isInsertLabels() { return insert; }
            @Override public String getLabelFormat()  { return format; }
            @Override public boolean isCreateNewLine() { return newLine; }
        };
    }

    private static List<StringBuffer> sbList(String... lines) {
        List<StringBuffer> list = new ArrayList<StringBuffer>();
        for (int i = 0; i < lines.length; i++) {
            list.add(new StringBuffer(lines[i]));
        }
        return list;
    }

    @Nested
    @DisplayName("addLineNumbers - real behavior (no null labelPrefix shortcuts)")
    class AddLineNumbersRealBehavior {

        @Test
        @DisplayName("step=0 -> step=1 and numbering is correct (non-null labelPrefix)")
        void stepZeroBecomesOne_real() {
            String[] source = {"A", "B"};
            StringBuffer[] out = RenumberSource.addLineNumbers(source, 0, "§", false, false, false);

            assertEquals("0001 A", out[0].toString());
            assertEquals("0002 B", out[1].toString());
        }

        @Test
        @DisplayName("step reduction when overflow (1001 lines, step=10 -> step=5)")
        void stepReduction_real() {
            String[] source = new String[1001];
            Arrays.fill(source, "X");

            StringBuffer[] out = RenumberSource.addLineNumbers(source, 10, "§", false, false, false);

            assertTrue(out[0].toString().startsWith("0005 "), "Expect step=5 for first line");
            assertTrue(out[1].toString().startsWith("0010 "), "Expect step=5 for second line");
            assertTrue(out[2].toString().startsWith("0015 "), "Expect step=5 for third line");

            assertTrue(out[1000].toString().startsWith("5005 "), "Expect last line number to be 5005");
        }

        @Test
        @DisplayName("label in parens is NOT replaced if there is whitespace after '('")
        void labelRefNotReplacedWithWhitespace() {
            String[] source = {"!1. WRITE X", "IF ( !1.) THEN"};
            StringBuffer[] out = RenumberSource.addLineNumbers(source, 10, "!", false, false, false);

            assertEquals("0010 WRITE X", out[0].toString());
            assertEquals("0020 IF ( !1.) THEN", out[1].toString());
        }
    }

    @Nested
    @DisplayName("removeLineNumbers - extra edge cases")
    class RemoveLineNumbersExtraEdgeCases {

        @Test
        @DisplayName("rewrite references with slash and comma endings")
        void rewriteRefSlashAndComma() {
            List<StringBuffer> input = sbList("0010 IF (0010/", "0020 IF (0010,");
            String[] out = RenumberSource.removeLineNumbers(input, true, false, 5, 10, null);

            assertEquals("IF (0001/", out[0]);
            assertEquals("IF (0001,", out[1]);
        }

        @Test
        @DisplayName("mutate input buffers (side effect is preserved)")
        void mutatesInputBuffers() {
            List<StringBuffer> input = sbList("0010 WRITE X", "0020 END");

            RenumberSource.removeLineNumbers(input, false, false, 5, 10, null);

            assertEquals("WRITE X", input.get(0).toString());
            assertEquals("END", input.get(1).toString());
        }

        @Test
        @DisplayName("createNewLine=true applies offset for numeric rewrite in comment context")
        void createNewLine_offsetInNumericRewrite() {
            List<StringBuffer> input = sbList(
                    "0010 WRITE X",
                    "0020 IF (0010)",
                    "0030 * COMMENT (0010)"
            );

            IInsertLabels il = labels(true, "L%d.", true);
            String[] out = RenumberSource.removeLineNumbers(input, true, false, 5, 10, il);

            assertTrue(Arrays.asList(out).toString().contains("* COMMENT (0002)"),
                    "Expect numeric reference to include offset after inserting label line. Output: " + Arrays.toString(out));
        }
    }
}