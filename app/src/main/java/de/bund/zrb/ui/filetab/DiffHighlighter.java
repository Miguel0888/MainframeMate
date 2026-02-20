package de.bund.zrb.ui.filetab;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Highlights differences between two RSyntaxTextArea instances (editor vs. compare/history).
 * <p>
 * Colors:
 * - GREEN  = line added in editor (not in compare)
 * - RED    = line deleted from editor (only in compare)
 * - GRAY   = line changed (in both, but different content)
 */
public final class DiffHighlighter {

    // Soft colors for better readability
    private static final Color COLOR_ADDED   = new Color(200, 255, 200);  // green – new in editor
    private static final Color COLOR_DELETED = new Color(255, 210, 210);  // red   – removed from editor
    private static final Color COLOR_CHANGED = new Color(220, 220, 220);  // gray  – modified

    private static final String DIFF_TAG = "DiffHighlight";

    private DiffHighlighter() {}

    /**
     * Compute and apply diff highlighting to both text areas.
     *
     * @param editorArea   the current editor (top)
     * @param compareArea  the compare/history version (bottom)
     */
    public static void applyDiff(RSyntaxTextArea editorArea, RSyntaxTextArea compareArea) {
        clearDiffHighlights(editorArea);
        clearDiffHighlights(compareArea);

        String[] editorLines = splitLines(editorArea.getText());
        String[] compareLines = splitLines(compareArea.getText());

        // Compute LCS-based diff
        List<DiffEntry> diff = computeDiff(compareLines, editorLines);

        for (DiffEntry entry : diff) {
            switch (entry.type) {
                case ADDED:
                    // Line exists in editor but not in compare → green in editor
                    highlightLine(editorArea, entry.editorLine, COLOR_ADDED);
                    break;
                case DELETED:
                    // Line exists in compare but not in editor → red in compare
                    highlightLine(compareArea, entry.compareLine, COLOR_DELETED);
                    break;
                case CHANGED:
                    // Line exists in both but differs → gray in both
                    highlightLine(editorArea, entry.editorLine, COLOR_CHANGED);
                    highlightLine(compareArea, entry.compareLine, COLOR_CHANGED);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Remove all diff highlights from a text area.
     */
    public static void clearDiffHighlights(RSyntaxTextArea area) {
        Highlighter h = area.getHighlighter();
        Highlighter.Highlight[] highlights = h.getHighlights();
        for (Highlighter.Highlight hl : highlights) {
            if (hl.getPainter() instanceof DiffPainter) {
                h.removeHighlight(hl);
            }
        }
    }

    // ==================== Internal ====================

    private static void highlightLine(RSyntaxTextArea area, int lineIndex, Color color) {
        try {
            int startOffset = area.getLineStartOffset(lineIndex);
            int endOffset = area.getLineEndOffset(lineIndex);
            area.getHighlighter().addHighlight(startOffset, endOffset, new DiffPainter(color));
        } catch (BadLocationException ignored) {
            // line index out of range – skip silently
        }
    }

    private static String[] splitLines(String text) {
        if (text == null || text.isEmpty()) return new String[0];
        return text.split("\n", -1);
    }

    // ==================== LCS-based Diff ====================

    /**
     * Compute a minimal diff between oldLines (compare) and newLines (editor)
     * using the Longest Common Subsequence (LCS) algorithm.
     */
    private static List<DiffEntry> computeDiff(String[] oldLines, String[] newLines) {
        int oldLen = oldLines.length;
        int newLen = newLines.length;

        // Build LCS table
        int[][] lcs = new int[oldLen + 1][newLen + 1];
        for (int i = 1; i <= oldLen; i++) {
            for (int j = 1; j <= newLen; j++) {
                if (oldLines[i - 1].equals(newLines[j - 1])) {
                    lcs[i][j] = lcs[i - 1][j - 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i - 1][j], lcs[i][j - 1]);
                }
            }
        }

        // Backtrack to find diffs
        List<DiffEntry> result = new ArrayList<>();
        int i = oldLen;
        int j = newLen;

        // Collect raw operations in reverse, then reverse at the end
        List<DiffEntry> reversed = new ArrayList<>();

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines[i - 1].equals(newLines[j - 1])) {
                // equal – no highlight
                i--;
                j--;
            } else if (j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j])) {
                // added in editor
                reversed.add(DiffEntry.added(j - 1));
                j--;
            } else if (i > 0) {
                // deleted from editor
                reversed.add(DiffEntry.deleted(i - 1));
                i--;
            }
        }

        Collections.reverse(reversed);

        // Post-process: pair adjacent delete+add into "changed"
        for (int k = 0; k < reversed.size(); k++) {
            DiffEntry e = reversed.get(k);
            if (e.type == DiffType.DELETED && k + 1 < reversed.size()) {
                DiffEntry next = reversed.get(k + 1);
                if (next.type == DiffType.ADDED) {
                    result.add(DiffEntry.changed(e.compareLine, next.editorLine));
                    k++; // skip next
                    continue;
                }
            }
            result.add(e);
        }

        return result;
    }

    // ==================== Data classes ====================

    private enum DiffType {
        ADDED, DELETED, CHANGED
    }

    private static final class DiffEntry {
        final DiffType type;
        final int compareLine; // line index in compare area (-1 if n/a)
        final int editorLine;  // line index in editor area (-1 if n/a)

        DiffEntry(DiffType type, int compareLine, int editorLine) {
            this.type = type;
            this.compareLine = compareLine;
            this.editorLine = editorLine;
        }

        static DiffEntry added(int editorLine) {
            return new DiffEntry(DiffType.ADDED, -1, editorLine);
        }

        static DiffEntry deleted(int compareLine) {
            return new DiffEntry(DiffType.DELETED, compareLine, -1);
        }

        static DiffEntry changed(int compareLine, int editorLine) {
            return new DiffEntry(DiffType.CHANGED, compareLine, editorLine);
        }
    }

    /**
     * Custom painter that tags highlights so they can be identified and removed.
     */
    private static final class DiffPainter extends DefaultHighlighter.DefaultHighlightPainter {
        DiffPainter(Color color) {
            super(color);
        }
    }
}

