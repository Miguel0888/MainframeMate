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
            RegexFoldParser parser = new RegexFoldParser(regex, null);
            area.getFoldManager().setFolds(parser.getFolds(area));
            area.repaint();
            return true;
        } catch (Exception e) {
            // Invalid regex or folding error
            return false;
        }
    }
}
