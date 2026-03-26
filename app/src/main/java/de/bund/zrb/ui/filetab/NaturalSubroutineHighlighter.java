package de.bund.zrb.ui.filetab;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * Highlights Natural DEFINE SUBROUTINE … END-SUBROUTINE blocks with a pale
 * pink/red background that spans the full editor width (not just to the end
 * of text on each line).
 * <p>
 * Usage:
 * <pre>
 *   NaturalSubroutineHighlighter.apply(textArea);
 *   // On text change:
 *   NaturalSubroutineHighlighter.apply(textArea);
 * </pre>
 */
public final class NaturalSubroutineHighlighter {

    /** Pale red-pink background for subroutine blocks. */
    private static final Color SUBROUTINE_BG = new Color(255, 230, 230);

    private NaturalSubroutineHighlighter() {}

    /**
     * Scan the text area for DEFINE SUBROUTINE … END-SUBROUTINE blocks and
     * apply full-width background highlights. Previous subroutine highlights
     * are removed first.
     */
    public static void apply(RSyntaxTextArea area) {
        if (area == null) return;

        clearHighlights(area);

        String text = area.getText();
        if (text == null || text.isEmpty()) return;

        String[] lines = text.split("\n", -1);
        boolean inSubroutine = false;
        int subStart = -1; // first line of block

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim().toUpperCase();

            if (!inSubroutine && trimmed.startsWith("DEFINE SUBROUTINE")) {
                inSubroutine = true;
                subStart = i;
            }

            if (inSubroutine && trimmed.startsWith("END-SUBROUTINE")) {
                // highlight from subStart to i (inclusive)
                highlightRange(area, subStart, i);
                inSubroutine = false;
                subStart = -1;
            }
        }

        // Unclosed subroutine — highlight to end of file
        if (inSubroutine) {
            highlightRange(area, subStart, lines.length - 1);
        }
    }

    /**
     * Remove all subroutine highlights from the text area.
     */
    public static void clearHighlights(RSyntaxTextArea area) {
        Highlighter h = area.getHighlighter();
        Highlighter.Highlight[] highlights = h.getHighlights();
        for (Highlighter.Highlight hl : highlights) {
            if (hl.getPainter() instanceof SubroutinePainter) {
                h.removeHighlight(hl);
            }
        }
    }

    // ───────────────────────────── Internal ─────────────────────────────

    private static void highlightRange(RSyntaxTextArea area, int firstLine, int lastLine) {
        try {
            int startOff = area.getLineStartOffset(firstLine);
            int endOff = area.getLineEndOffset(lastLine);
            area.getHighlighter().addHighlight(startOff, endOff, new SubroutinePainter());
        } catch (BadLocationException ignored) {
            // line index out of range – skip
        }
    }

    /**
     * Custom painter that draws a full-width background band for the
     * highlighted region.  The default Swing highlight painter only paints
     * up to the end of the text on each line.  This painter instead fills
     * the entire component width for every line in the range.
     */
    static final class SubroutinePainter implements Highlighter.HighlightPainter {

        @Override
        public void paint(Graphics g, int offs0, int offs1, Shape bounds, JTextComponent c) {
            try {
                Rectangle alloc = (bounds instanceof Rectangle)
                        ? (Rectangle) bounds
                        : bounds.getBounds();

                int startLine = c.getDocument().getDefaultRootElement()
                        .getElementIndex(offs0);
                int endLine = c.getDocument().getDefaultRootElement()
                        .getElementIndex(offs1);

                // Use the full visible width of the component (not just text width)
                int fullWidth = Math.max(c.getWidth(), alloc.width);

                g.setColor(SUBROUTINE_BG);

                for (int line = startLine; line <= endLine; line++) {
                    Rectangle lineRect = getLineRect(c, line);
                    if (lineRect != null) {
                        g.fillRect(0, lineRect.y, fullWidth, lineRect.height);
                    }
                }
            } catch (Exception ignored) {
                // safety net – never break painting
            }
        }

        private Rectangle getLineRect(JTextComponent c, int line) {
            try {
                int lineStart = c.getDocument().getDefaultRootElement()
                        .getElement(line).getStartOffset();
                Rectangle r = c.modelToView(lineStart);
                if (r == null) return null;
                // determine line height from font metrics as fallback
                int lineHeight = c.getFontMetrics(c.getFont()).getHeight();
                return new Rectangle(0, r.y, c.getWidth(), Math.max(r.height, lineHeight));
            } catch (Exception e) {
                return null;
            }
        }
    }
}

