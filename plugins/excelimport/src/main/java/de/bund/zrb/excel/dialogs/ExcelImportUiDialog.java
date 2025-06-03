package de.bund.zrb.excel.dialogs;

import de.zrb.bund.api.MainframeContext;

import javax.swing.*;
import java.awt.*;

public class ExcelImportUiDialog extends JDialog {

    private final ExcelImportUiPanel uiPanel;

    public ExcelImportUiDialog(MainframeContext context) {
        super(context.getMainFrame(), "Excel-Import", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(600, 300);
        setLocationRelativeTo(context.getMainFrame());

        uiPanel = new ExcelImportUiPanel(context);

        // Buttons unten
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Abbrechen");

        okButton.addActionListener(e -> {
            // Hier ggf. Logik
            setVisible(false);
        });

        cancelButton.addActionListener(e -> {
            setVisible(false);
        });

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        // Layout zusammensetzen
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(uiPanel, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);
    }

    public ExcelImportUiPanel getUiPanel() {
        return uiPanel;
    }
}
