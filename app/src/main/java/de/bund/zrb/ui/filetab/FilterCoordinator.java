package de.bund.zrb.ui.filetab;

import de.bund.zrb.ui.util.RegexFoldParser;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;

public class FilterCoordinator {

    private final RSyntaxTextArea editorArea;
    private final RSyntaxTextArea originalArea;
    private final JTextField grepField;
    private final boolean soundEnabled;

    public FilterCoordinator(RSyntaxTextArea editorArea,
                             RSyntaxTextArea originalArea,
                             JTextField grepField,
                             boolean soundEnabled) {
        this.editorArea = editorArea;
        this.originalArea = originalArea;
        this.grepField = grepField;
        this.soundEnabled = soundEnabled;

        grepField.setToolTipText("Regulärer Ausdruck für Zeilenfilterung");
        grepField.setToolTipText(
                "<html>" +
                        "Regulärer Ausdruck für die Zeilenfilterung<br>" +
                        "<br>" +
                        "<b>Beispiele:</b><br>" +
                        "&bull; <code>abc</code> – enthält 'abc'<br>" +
                        "&bull; <code>^abc</code> – beginnt mit 'abc'<br>" +
                        "&bull; <code>abc$</code> – endet mit 'abc'<br>" +
                        "&bull; <code>.*test.*</code> – enthält 'test' (beliebiger Kontext)<br>" +
                        "&bull; <code>\\d+</code> – enthält eine oder mehrere Ziffern<br>" +
                        "&bull; <code>[A-Z]{3}</code> – genau drei Großbuchstaben<br>" +
                        "<br>" +
                        "Hinweis: Die Suche ist <b>nicht</b> groß-/kleinschreibungssensitiv.<br>" +
                        "Alle Zeilen, die nicht passen, werden gefaltet." +
                        "</html>"
        );
    }

    public void applyFilter() {
        String input = grepField.getText();
        boolean editorSuccess = applyFilterToArea(editorArea, input);
        boolean originalSuccess = applyFilterToArea(originalArea, input);

        // Visual feedback bei Fehler
        if (!editorSuccess || !originalSuccess) {
            grepField.setBackground(new Color(255, 200, 200));
            if (soundEnabled) {
                Toolkit.getDefaultToolkit().beep();
            }
        } else {
            grepField.setBackground(UIManager.getColor("TextField.background"));
        }
    }

    private boolean applyFilterToArea(RSyntaxTextArea area, String regex) {
        try {
            RegexFoldParser parser = new RegexFoldParser(regex, hasMatch -> showSearchFeedback(grepField, hasMatch));
            area.getFoldManager().setFolds(parser.getFolds(area));
            updateArea(area);
            return true;
        } catch (Exception e) {
            // Invalid regex or folding error
            return false;
        }
    }

    private void updateArea( RSyntaxTextArea area) {
        area.revalidate();
        area.repaint();
        area.getParent().revalidate(); // Wichtig: das ist der Viewport!
        area.getParent().repaint();
    }

    private void showSearchFeedback(JTextField field, boolean success) {
        if (success || field.getText().isEmpty()) {
            SwingUtilities.invokeLater(() -> field.setBackground(UIManager.getColor("TextField.background")));
        } else {
            SwingUtilities.invokeLater(() -> field.setBackground(new Color(255, 200, 200)));
            if (soundEnabled) {
                Toolkit.getDefaultToolkit().beep();
            }
        }
    }
}
