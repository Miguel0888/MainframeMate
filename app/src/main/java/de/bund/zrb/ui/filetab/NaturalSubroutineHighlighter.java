package de.bund.zrb.ui.filetab;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * Highlights structural Natural code blocks with distinct pale background colors
 * that span the full editor width:
 * <ul>
 *   <li><b>DEFINE SUBROUTINE … END-SUBROUTINE</b> — pale pink/red</li>
 *   <li><b>DEFINE DATA … END-DEFINE</b> — pale blue</li>
 *   <li><b>ON ERROR … END-ERROR</b> — pale yellow/amber</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 *   NaturalSubroutineHighlighter.apply(textArea);
 * </pre>
 */
public final class NaturalSubroutineHighlighter {

    /** Pale red-pink background for subroutine blocks. */
    private static final Color SUBROUTINE_BG = new Color(255, 230, 230);

    /** Pale blue background for DEFINE DATA blocks. */
    private static final Color DEFINE_DATA_BG = new Color(230, 240, 255);

    /** Pale yellow/amber background for ON ERROR blocks. */
    private static final Color ON_ERROR_BG = new Color(255, 248, 220);

    private NaturalSubroutineHighlighter() {}

    /**
     * Scan the text area for structural Natural blocks and apply full-width
     * background highlights.  Previous block highlights are removed first.
     */
    public static void apply(RSyntaxTextArea area) {
        if (area == null) return;

        clearHighlights(area);

        String text = area.getText();
        if (text == null || text.isEmpty()) return;

        String[] lines = text.split("\n", -1);

        // Track three independent block types (they don't nest with each other)
        boolean inSubroutine = false;
        int subStart = -1;

        boolean inDefineData = false;
        int dataStart = -1;

        boolean inOnError = false;
        int errorStart = -1;

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim().toUpperCase();

            // ── DEFINE SUBROUTINE … END-SUBROUTINE ──────────────────
            if (!inSubroutine && trimmed.startsWith("DEFINE SUBROUTINE")) {
                inSubroutine = true;
                subStart = i;
            }
            if (inSubroutine && trimmed.startsWith("END-SUBROUTINE")) {
                highlightRange(area, subStart, i, SUBROUTINE_BG);
                inSubroutine = false;
                subStart = -1;
            }

            // ── DEFINE DATA … END-DEFINE ────────────────────────────
            if (!inDefineData && trimmed.startsWith("DEFINE DATA")) {
                inDefineData = true;
                dataStart = i;
            }
            if (inDefineData && trimmed.startsWith("END-DEFINE")) {
                highlightRange(area, dataStart, i, DEFINE_DATA_BG);
                inDefineData = false;
                dataStart = -1;
            }

            // ── ON ERROR … END-ERROR ────────────────────────────────
            if (!inOnError && trimmed.startsWith("ON ERROR")) {
                inOnError = true;
                errorStart = i;
            }
            if (inOnError && trimmed.startsWith("END-ERROR")) {
                highlightRange(area, errorStart, i, ON_ERROR_BG);
                inOnError = false;
                errorStart = -1;
            }
        }

        // Unclosed blocks — highlight to end of file
        if (inSubroutine) {
            highlightRange(area, subStart, lines.length - 1, SUBROUTINE_BG);
        }
        if (inDefineData) {
            highlightRange(area, dataStart, lines.length - 1, DEFINE_DATA_BG);
        }
        if (inOnError) {
            highlightRange(area, errorStart, lines.length - 1, ON_ERROR_BG);
        }
    }

    /**
     * Remove all Natural block highlights from the text area.
     */
    public static void clearHighlights(RSyntaxTextArea area) {
        Highlighter h = area.getHighlighter();
        Highlighter.Highlight[] highlights = h.getHighlights();
        for (Highlighter.Highlight hl : highlights) {
            if (hl.getPainter() instanceof BlockPainter) {
                h.removeHighlight(hl);
            }
        }
    }

    // ───────────────────────────── Internal ─────────────────────────────

    private static void highlightRange(RSyntaxTextArea area, int firstLine, int lastLine, Color color) {
        try {
            int startOff = area.getLineStartOffset(firstLine);
            int endOff = area.getLineEndOffset(lastLine);
            area.getHighlighter().addHighlight(startOff, endOff, new BlockPainter(color));
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
    static final class BlockPainter implements Highlighter.HighlightPainter {

        private final Color color;

        BlockPainter(Color color) {
            this.color = color;
        }

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

                g.setColor(color);

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
