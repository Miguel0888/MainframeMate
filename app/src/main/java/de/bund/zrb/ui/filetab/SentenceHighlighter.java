package de.bund.zrb.ui.filetab;

import de.zrb.bund.newApi.sentence.FieldCoordinate;
import de.zrb.bund.newApi.sentence.FieldMap;
import de.zrb.bund.newApi.sentence.SentenceField;
import de.bund.zrb.helper.SettingsHelper;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.text.Highlighter;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.util.Map;

public class SentenceHighlighter {

    public void highlightFields(RSyntaxTextArea area, FieldMap fields, int schemaLines) {
        Highlighter highlighter = area.getHighlighter();
        highlighter.removeAllHighlights();

        String[] lines = area.getText().split("\n");

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            int schemaRow = lineIndex % schemaLines;
            int lineOffset = getLineStartOffset(lines, lineIndex);
            String line = lines[lineIndex];

            for (Map.Entry<FieldCoordinate, SentenceField> entry : fields.entrySet()) {
                FieldCoordinate coord = entry.getKey();
                SentenceField field = entry.getValue();

                if (coord.getRow() - 1 != schemaRow) continue;

                int start = coord.getPosition() - 1;
                int len = field.getLength() != null ? field.getLength() : 0;
                int end = Math.min(line.length(), start + len);

                try {
                    highlighter.addHighlight(lineOffset + start, lineOffset + end,
                            new DefaultHighlighter.DefaultHighlightPainter(getColorFor(field)));
                } catch (BadLocationException ignored) {}
            }
        }
    }

    private int getLineStartOffset(String[] lines, int index) {
        int offset = 0;
        for (int i = 0; i < index; i++) {
            offset += lines[i].length() + 1;
        }
        return offset;
    }

    private Color getColorFor(SentenceField field) {
        String name = field.getName();
        String override = SettingsHelper.load().fieldColorOverrides.get(name.toUpperCase());
        try {
            if (override != null) return Color.decode(override);
            if (field.getColor() != null && !field.getColor().isEmpty()) return Color.decode(field.getColor());
        } catch (NumberFormatException ignored) {}

        int hash = Math.abs(name.hashCode());
        float hue = (hash % 360) / 360f;
        return Color.getHSBColor(hue, 0.5f, 0.85f);
    }

    public void clearHighlights(RSyntaxTextArea textArea) {
        Highlighter highlighter = textArea.getHighlighter();
        if (highlighter != null) {
            highlighter.removeAllHighlights();
        }
    }

}
