package de.bund.zrb.excel.dialogs;

import de.zrb.bund.api.MainframeContext;

import javax.swing.*;
import java.awt.*;

public class ExcelImportUiPanel extends JPanel {

    private final JLabel excelFileLabel = new JLabel("<keine Datei ausgewählt>");
    private final JButton excelFileButton = new JButton("...");

    private final JCheckBox headerCheckbox = new JCheckBox("Spaltennamen in Zeile:");
    private final JSpinner headerRowSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));

    private final JComboBox<String> templateDropdown = new JComboBox<>();
    private final JButton templateEditButton = new JButton("...");

    private final JCheckBox appendCheckbox = new JCheckBox("An bestehende Datei anhängen");
    private final JTextField trennzeileField = new JTextField();

    public ExcelImportUiPanel(MainframeContext context) {

        setLayout(new BorderLayout());
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Zeile 1: Excel-Datei
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel("Excel-Datei:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        formPanel.add(excelFileLabel, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        excelFileButton.setPreferredSize(new Dimension(30, 25));
        excelFileButton.setToolTipText("Excel-Datei auswählen");
        formPanel.add(excelFileButton, gbc);

        // Zeile 2: Header-Checkbox + Spinner
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        headerCheckbox.setSelected(true);
        formPanel.add(headerCheckbox, gbc);

        gbc.gridx = 2;
        gbc.gridwidth = 1;
        formPanel.add(headerRowSpinner, gbc);

        // Zeile 3: Vorlage + Edit-Button
        gbc.gridx = 0;
        gbc.gridy++;
        formPanel.add(new JLabel("Vorlage:"), gbc);

        gbc.gridx = 1;
        templateDropdown.setEditable(false);
        formPanel.add(templateDropdown, gbc);

        gbc.gridx = 2;
        templateEditButton.setPreferredSize(new Dimension(30, 25));
        templateEditButton.setToolTipText("Vorlagen bearbeiten");
        formPanel.add(templateEditButton, gbc);

        // Zeile 4: Anhängen
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 3;
        formPanel.add(appendCheckbox, gbc);

        // Zeile 5: Trennzeile
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 1;
        formPanel.add(new JLabel("Trennzeile:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        trennzeileField.setEnabled(false);
        formPanel.add(trennzeileField, gbc);

        add(formPanel, BorderLayout.CENTER);

        // Spinner-Abhängigkeit
        headerCheckbox.addActionListener(e ->
                headerRowSpinner.setEnabled(headerCheckbox.isSelected())
        );

        // Trennzeile-Abhängigkeit
        appendCheckbox.addActionListener(e ->
                trennzeileField.setEnabled(appendCheckbox.isSelected())
        );

        templateEditButton.addActionListener(e -> {
            NewExcelImportDialog dialog = new NewExcelImportDialog(context);
            dialog.setModal(true);
            dialog.setVisible(true);

            // Wenn Dialog geschlossen wurde, neue Vorlage übernehmen
            String lastSaved = dialog.getLastSavedTemplateName();
            if (lastSaved != null && !lastSaved.trim().isEmpty()) {
                if (((DefaultComboBoxModel<String>) templateDropdown.getModel()).getIndexOf(lastSaved) == -1) {
                    templateDropdown.addItem(lastSaved);
                }
                templateDropdown.setSelectedItem(lastSaved);
            }
        });

    }

    // Getter für externe Logik
    public JButton getExcelFileButton() {
        return excelFileButton;
    }

    public JLabel getExcelFileLabel() {
        return excelFileLabel;
    }

    public JCheckBox getHeaderCheckbox() {
        return headerCheckbox;
    }

    public JSpinner getHeaderRowSpinner() {
        return headerRowSpinner;
    }

    public JComboBox<String> getTemplateDropdown() {
        return templateDropdown;
    }

    public JButton getTemplateEditButton() {
        return templateEditButton;
    }

    public JCheckBox getAppendCheckbox() {
        return appendCheckbox;
    }

    public JTextField getTrennzeileField() {
        return trennzeileField;
    }
}
