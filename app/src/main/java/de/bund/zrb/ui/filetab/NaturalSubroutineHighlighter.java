package de.bund.zrb.ui.filetab;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
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
 * Colors are configurable via NDV Settings (Natural-Block-Farben).
 * <p>
 * Usage:
 * <pre>
 *   NaturalSubroutineHighlighter.apply(textArea);
 * </pre>
 */
public final class NaturalSubroutineHighlighter {

    /** Default: pale red-pink background for subroutine blocks. */
    private static final Color DEFAULT_SUBROUTINE_BG = new Color(255, 230, 230);

    /** Default: pale blue background for DEFINE DATA blocks. */
    private static final Color DEFAULT_DEFINE_DATA_BG = new Color(230, 240, 255);

    /** Default: pale yellow/amber background for ON ERROR blocks. */
    private static final Color DEFAULT_ON_ERROR_BG = new Color(255, 248, 220);

    private NaturalSubroutineHighlighter() {}

    /**
     * Load block colors from settings, with fallback to defaults.
     */
    private static Color[] loadColors() {
        try {
            Settings s = SettingsHelper.load();
            return new Color[]{
                    hexToColor(s.naturalColorSubroutine, DEFAULT_SUBROUTINE_BG),
                    hexToColor(s.naturalColorDefineData, DEFAULT_DEFINE_DATA_BG),
                    hexToColor(s.naturalColorOnError, DEFAULT_ON_ERROR_BG)
            };
        } catch (Exception e) {
            return new Color[]{ DEFAULT_SUBROUTINE_BG, DEFAULT_DEFINE_DATA_BG, DEFAULT_ON_ERROR_BG };
        }
    }

    private static Color hexToColor(String hex, Color fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        try {
            return Color.decode(hex);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Scan the text area for structural Natural blocks and apply full-width
     * background highlights.  Previous block highlights are removed first.
     */
    public static void apply(RSyntaxTextArea area) {
        if (area == null) return;

        clearHighlights(area);

        String text = area.getText();
        if (text == null || text.isEmpty()) return;

        Color[] colors = loadColors();
        Color subroutineBg = colors[0];
        Color defineDataBg = colors[1];
        Color onErrorBg    = colors[2];

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
                highlightRange(area, subStart, i, subroutineBg);
                inSubroutine = false;
                subStart = -1;
            }

            // ── DEFINE DATA … END-DEFINE ────────────────────────────
            if (!inDefineData && trimmed.startsWith("DEFINE DATA")) {
                inDefineData = true;
                dataStart = i;
            }
            if (inDefineData && trimmed.startsWith("END-DEFINE")) {
                highlightRange(area, dataStart, i, defineDataBg);
                inDefineData = false;
                dataStart = -1;
            }

            // ── ON ERROR … END-ERROR ────────────────────────────────
            if (!inOnError && trimmed.startsWith("ON ERROR")) {
                inOnError = true;
                errorStart = i;
            }
            if (inOnError && trimmed.startsWith("END-ERROR")) {
                highlightRange(area, errorStart, i, onErrorBg);
                inOnError = false;
                errorStart = -1;
            }
        }

        // Unclosed blocks — highlight to end of file
        if (inSubroutine) {
            highlightRange(area, subStart, lines.length - 1, subroutineBg);
        }
        if (inDefineData) {
            highlightRange(area, dataStart, lines.length - 1, defineDataBg);
        }
        if (inOnError) {
            highlightRange(area, errorStart, lines.length - 1, onErrorBg);
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
