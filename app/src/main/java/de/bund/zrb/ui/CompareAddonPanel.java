package de.bund.zrb.ui;

import de.bund.zrb.helper.SettingsHelper;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;

/**
 * Panel zum Vergleich eines zusätzlichen Dateiinhalts.
 * Besteht aus einer Statuszeile und einem Read-only-Editor.
 */
public class CompareAddonPanel extends JPanel {

    private final RSyntaxTextArea compareArea = new RSyntaxTextArea();
    private final JButton closeButton = new JButton("\u274C");
    private final JCheckBox appendCheckBox = new JCheckBox("Anhängen");

    private boolean appendSelected = false;

    public CompareAddonPanel(String filePath, String content) {
        super(new BorderLayout());

        compareArea.setText(content);
        compareArea.setEditable(false);
        compareArea.setLineWrap(false);
        compareArea.setCodeFoldingEnabled(true);
        compareArea.setAntiAliasingEnabled(true);

        initEditorSettings(compareArea);

        RTextScrollPane scrollPane = new RTextScrollPane(compareArea);
        scrollPane.setFoldIndicatorEnabled(true);
        scrollPane.setLineNumbersEnabled(true);

        JLabel label = new JLabel("Vergleich mit: " + filePath);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));

        closeButton.setToolTipText("Vergleich schließen");
        closeButton.setForeground(Color.RED);
        closeButton.setFont(closeButton.getFont().deriveFont(Font.BOLD, 14f));
        closeButton.setPreferredSize(new Dimension(28, 28));
        closeButton.setFocusPainted(false);
        closeButton.setBorderPainted(true);
        closeButton.setContentAreaFilled(true);

        appendCheckBox.setToolTipText("Wenn aktiv, wird neuer Inhalt an den Vergleich angehängt.");
        appendCheckBox.setSelected(appendSelected);
        appendCheckBox.addItemListener(e -> appendSelected = e.getStateChange() == ItemEvent.SELECTED);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.add(label, BorderLayout.WEST);

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        rightControls.add(appendCheckBox);
        rightControls.add(closeButton);
        topBar.add(rightControls, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    public RSyntaxTextArea getCompareArea() {
        return compareArea;
    }

    public boolean isAppendSelected() {
        return appendSelected;
    }

    public void setAppendSelected(boolean value) {
        appendSelected = value;
        appendCheckBox.setSelected(value);
    }

    public void setCloseAction(Runnable action) {
        closeButton.addActionListener(e -> action.run());
    }

    private void initEditorSettings(RSyntaxTextArea editor) {
        Font editorFont = new Font(SettingsHelper.load().editorFont, Font.PLAIN, SettingsHelper.load().editorFontSize);
        editor.setFont(editorFont);
        editor.setSyntaxEditingStyle("text/plain");
        editor.setTabSize(4);
        editor.setHighlightCurrentLine(true);
        editor.setAntiAliasingEnabled(true);
        editor.setCodeFoldingEnabled(true);
        editor.setMarginLineEnabled(true);
        editor.setMarginLinePosition(SettingsHelper.load().marginColumn);
        editor.setMarginLineColor(Color.RED);
        editor.setPaintTabLines(true);
    }
}
