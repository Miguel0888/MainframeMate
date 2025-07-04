package de.bund.zrb.excel.ui;

import de.zrb.bund.api.MainframeContext;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class ExcelImportSettingsDialog extends JDialog {
    private final MainframeContext context;

    private final JTextField jsonPathField = new JTextField(30);
    private final JTextField excelPathField = new JTextField(30);
    private final JTextField trennzeileField = new JTextField(30);
    private final JCheckBox showConfirmationCheck = new JCheckBox("Bestätigungsdialog nach dem Import anzeigen");
    private final JCheckBox autoOpenCheck = new JCheckBox("Datei nach Import automatisch öffnen");
    private final JCheckBox stopOnEmptyRequiredCheck = new JCheckBox("Stoppen, wenn benötigte Spalten leer sind");
    private final JCheckBox requireAllFieldsEmptyCheck = new JCheckBox("Alle statt eine Spalte müssen leer sein");

    private static final String PLUGIN_KEY = "excelImporter";

    public ExcelImportSettingsDialog(MainframeContext context) {
        super(context.getMainFrame(), "Excel-Import Einstellungen", true);
        this.context = context;
        setLayout(new BorderLayout());

        Map<String, String> pluginSettings = getPluginSettings();

        jsonPathField.setText(pluginSettings.getOrDefault("lastJsonPath", ""));
        excelPathField.setText(pluginSettings.getOrDefault("lastExcelPath", ""));
        trennzeileField.setText(pluginSettings.getOrDefault("separator", ""));
        showConfirmationCheck.setSelected(Boolean.parseBoolean(pluginSettings.getOrDefault("showConfirmation", "true")));
        autoOpenCheck.setSelected(Boolean.parseBoolean(pluginSettings.getOrDefault("autoOpen", "true")));
        stopOnEmptyRequiredCheck.setSelected(Boolean.parseBoolean(pluginSettings.getOrDefault("stopOnEmptyRequired", "true")));
        requireAllFieldsEmptyCheck.setSelected(Boolean.parseBoolean(pluginSettings.getOrDefault("requireAllFieldsEmpty", "false")));



        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // JSON-Eingabe
        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("Letzte JSON-Datei:"), gbc);
        gbc.gridx = 1;
        form.add(jsonPathField, gbc);
        gbc.gridx = 2;
        form.add(createBrowseButton(jsonPathField, "JSON-Datei auswählen", "json"), gbc);

        // Excel-Eingabe
        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("Letzte Excel-Datei:"), gbc);
        gbc.gridx = 1;
        form.add(excelPathField, gbc);
        gbc.gridx = 2;
        form.add(createBrowseButton(excelPathField, "Excel-Datei auswählen", "xlsx", "xls"), gbc);

        // Trennzeile
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        form.add(new JLabel("Trennzeile:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2;
        form.add(trennzeileField, gbc);

        // Checkbox for confirmation dialog
        gbc.gridx = 1; gbc.gridy = 3; gbc.gridwidth = 2;
        form.add(showConfirmationCheck, gbc);

        // Checkbox for auto-open
        gbc.gridx = 1; gbc.gridy = 4; gbc.gridwidth = 1;
        form.add(autoOpenCheck, gbc);

        // Weiter Import-Optionen
        gbc.gridx = 1; gbc.gridy++;
        form.add(stopOnEmptyRequiredCheck, gbc);
        gbc.gridx = 1; gbc.gridy++;
        form.add(requireAllFieldsEmptyCheck, gbc);

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton save = new JButton("Speichern");
        save.addActionListener(e -> {
            Map<String, String> updated = new LinkedHashMap<>();
            updated.put("lastJsonPath", jsonPathField.getText().trim());
            updated.put("lastExcelPath", excelPathField.getText().trim());
            updated.put("separator", trennzeileField.getText().trim());
            updated.put("showConfirmation", String.valueOf(showConfirmationCheck.isSelected()));
            updated.put("autoOpen", String.valueOf(autoOpenCheck.isSelected()));
            updated.put("stopOnEmptyRequired", String.valueOf(stopOnEmptyRequiredCheck.isSelected()));
            updated.put("requireAllFieldsEmpty", String.valueOf(requireAllFieldsEmptyCheck.isSelected()));
            savePluginSettings(updated);
            dispose();
        });

        JButton cancel = new JButton("Abbrechen");
        cancel.addActionListener(e -> dispose());

        buttons.add(save);
        buttons.add(cancel);

        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(context.getMainFrame());
    }

    private JButton createBrowseButton(JTextField field, String title, String... extensions) {
        JButton button = new JButton("Durchsuchen...");
        button.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(title);
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(title, extensions));
            if (!field.getText().isEmpty()) {
                chooser.setSelectedFile(new File(field.getText()));
            }
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        return button;
    }

    private Map<String, String> getPluginSettings() {
        return context.loadPluginSettings(PLUGIN_KEY);
    }

    private void savePluginSettings(Map<String, String> updated) {
        context.savePluginSettings(PLUGIN_KEY, updated);
    }

}
