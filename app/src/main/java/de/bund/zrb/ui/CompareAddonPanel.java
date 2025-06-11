package de.bund.zrb.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import java.awt.*;

/**
 * Panel zum Vergleich eines zusätzlichen Dateiinhalts.
 * Besteht aus einer Statuszeile und einem Read-only-Editor.
 */
public class CompareAddonPanel extends JPanel {

    private final RSyntaxTextArea compareArea = new RSyntaxTextArea();
    private final JButton closeButton = new JButton("\u274C");

    public CompareAddonPanel(String filePath, String content) {
        super(new BorderLayout());

        compareArea.setText(content);
        compareArea.setEditable(false);
        compareArea.setLineWrap(false);
        compareArea.setCodeFoldingEnabled(true);
        compareArea.setAntiAliasingEnabled(true);

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

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.add(label, BorderLayout.WEST);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        buttonPanel.add(closeButton);
        topBar.add(buttonPanel, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    public RSyntaxTextArea getCompareArea() {
        return compareArea;
    }

    public void setCloseAction(Runnable action) {
        closeButton.addActionListener(e -> action.run());
    }
}