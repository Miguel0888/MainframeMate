package de.bund.zrb.excel.dialogs;

import de.bund.zrb.excel.repo.TemplateRepository;
import de.zrb.bund.api.MainframeContext;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Map;

public class ExcelImportUiPanel extends JPanel {

    private static final String PLUGIN_KEY = "excelImporter";
    private static final String KEY_EXCEL = "lastExcelPath";
    private static final String KEY_HEADER_ENABLED = "headerEnabled";
    private static final String KEY_HEADER_ROW = "headerRow";
    private static final String KEY_APPEND = "append";
    private static final String KEY_TRENNZEILE = "trennzeile";

    private File selectedExcelFile;

    private final JLabel excelFileLabel = new JLabel("<keine Datei ausgewählt>");
    private final JButton excelFileButton = new JButton("...");

    private final JCheckBox headerCheckbox = new JCheckBox("Spaltennamen in Zeile:");
    private final JSpinner headerRowSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));

    private final JComboBox<String> templateDropdown = new JComboBox<>();
    private final JButton templateEditButton = new JButton("...");

    private final JCheckBox appendCheckbox = new JCheckBox("An bestehende Datei anhängen");
    private final JTextField trennzeileField = new JTextField();
    private final TemplateRepository templateRepo;

    public ExcelImportUiPanel(MainframeContext context) {
        this.templateRepo = new TemplateRepository(context.getSettingsFolder());

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
        excelFileButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                selectedExcelFile = chooser.getSelectedFile();
                excelFileLabel.setText(selectedExcelFile.getAbsolutePath());
            }
        });
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

            updateTemplateDropdown();

            String lastSaved = dialog.getLastSavedTemplateName();
            if (lastSaved != null && !lastSaved.trim().isEmpty()) {
                templateDropdown.setSelectedItem(lastSaved);
            }
        });

        updateTemplateDropdown();
        loadSettingsFromContext(context);
    }

    private void updateTemplateDropdown() {
        templateDropdown.removeAllItems();
        for (String name : templateRepo.getTemplateNames()) {
            templateDropdown.addItem(name);
        }
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

    public void loadSettingsFromContext(MainframeContext context) {
        Map<String, String> settings = context.loadPluginSettings(PLUGIN_KEY);

        // Excel-Datei
        String excelPath = settings.get(KEY_EXCEL);
        if (excelPath != null && !excelPath.isEmpty()) {
            File file = new File(excelPath);
            if (file.exists()) {
                selectedExcelFile = file;
                excelFileLabel.setText(file.getAbsolutePath());
            }
        }

        // Header-Zeile
        headerCheckbox.setSelected(Boolean.parseBoolean(settings.getOrDefault(KEY_HEADER_ENABLED, "true")));
        headerRowSpinner.setEnabled(headerCheckbox.isSelected());

        try {
            int headerRow = Integer.parseInt(settings.getOrDefault(KEY_HEADER_ROW, "1"));
            headerRowSpinner.setValue(Math.max(1, headerRow));
        } catch (NumberFormatException e) {
            headerRowSpinner.setValue(1);
        }

        // Anfügen
        appendCheckbox.setSelected(Boolean.parseBoolean(settings.getOrDefault(KEY_APPEND, "false")));
        trennzeileField.setEnabled(appendCheckbox.isSelected());

        // Trennzeile
        String trennzeile = settings.get(KEY_TRENNZEILE);
        if (trennzeile != null) {
            trennzeileField.setText(trennzeile);
        }
    }

    public void saveSettingsToContext(MainframeContext context) {
        Map<String, String> settings = context.loadPluginSettings(PLUGIN_KEY);

        if (selectedExcelFile != null) {
            settings.put(KEY_EXCEL, selectedExcelFile.getAbsolutePath());
        }

        settings.put(KEY_HEADER_ENABLED, Boolean.toString(headerCheckbox.isSelected()));
        settings.put(KEY_HEADER_ROW, String.valueOf(headerRowSpinner.getValue()));
        settings.put(KEY_APPEND, Boolean.toString(appendCheckbox.isSelected()));
        settings.put(KEY_TRENNZEILE, trennzeileField.getText());

        context.savePluginSettings(PLUGIN_KEY, settings);
    }

}
