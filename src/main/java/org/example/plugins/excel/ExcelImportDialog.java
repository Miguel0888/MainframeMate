package org.example.plugins.excel;

import org.example.model.Settings;
import org.example.util.SettingsManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class ExcelImportDialog extends JDialog {

    private static final String PLUGIN_KEY = "excelImporter";

    private final JTextField excelPathField = new JTextField(30);
    private final JTextField jsonPathField = new JTextField(30);
    private final JCheckBox openTabCheck = new JCheckBox("Datei nach Import automatisch öffnen");

    private boolean confirmed = false;
    private String generatedText = "";

    public ExcelImportDialog(Frame parent) {
        super(parent, "Excel → Satzdatei", true);
        setLayout(new BorderLayout());

        Map<String, String> settings = getPluginSettings();

        excelPathField.setText(settings.getOrDefault("lastExcelPath", ""));
        jsonPathField.setText(settings.getOrDefault("lastJsonPath", ""));
        openTabCheck.setSelected(Boolean.parseBoolean(settings.getOrDefault("autoOpen", "true")));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Excel
        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("Excel-Datei:"), gbc);
        gbc.gridx = 1; form.add(excelPathField, gbc);
        gbc.gridx = 2; form.add(createBrowseButton(excelPathField, "Excel-Datei auswählen", "xlsx", "xls"), gbc);

        // JSON
        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("JSON-Feldlayout:"), gbc);
        gbc.gridx = 1; form.add(jsonPathField, gbc);
        gbc.gridx = 2; form.add(createBrowseButton(jsonPathField, "JSON-Datei auswählen", "json"), gbc);

        // Checkbox
        gbc.gridx = 1; gbc.gridy = 2; gbc.gridwidth = 2;
        form.add(openTabCheck, gbc);

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton run = new JButton("Importieren");
        run.addActionListener(e -> {
            if (!new File(excelPathField.getText()).exists()) {
                JOptionPane.showMessageDialog(this, "Excel-Datei existiert nicht.");
                return;
            }
            if (!new File(jsonPathField.getText()).exists()) {
                JOptionPane.showMessageDialog(this, "JSON-Datei existiert nicht.");
                return;
            }

            savePluginSettings(); // speichert Pfade und Checkbox
            // TODO: Hier später Excel-Parser aufrufen
            generatedText = "[ERFOLGREICH IMPORTIERT]\nExcel: " + excelPathField.getText() +
                    "\nLayout: " + jsonPathField.getText();
            confirmed = true;
            dispose();
        });

        JButton cancel = new JButton("Abbrechen");
        cancel.addActionListener(e -> dispose());

        buttons.add(run);
        buttons.add(cancel);

        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(parent);
    }

    private JButton createBrowseButton(JTextField field, String title, String... extensions) {
        JButton btn = new JButton("Durchsuchen...");
        btn.addActionListener(e -> {
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
        return btn;
    }

    private void savePluginSettings() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("lastExcelPath", excelPathField.getText().trim());
        values.put("lastJsonPath", jsonPathField.getText().trim());
        values.put("autoOpen", String.valueOf(openTabCheck.isSelected()));
        Settings settings = SettingsManager.load();
        settings.pluginSettings.put(PLUGIN_KEY, values);
        SettingsManager.save(settings);
    }

    private Map<String, String> getPluginSettings() {
        return SettingsManager.load().pluginSettings.computeIfAbsent(PLUGIN_KEY, k -> new LinkedHashMap<>());
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getGeneratedText() {
        return generatedText;
    }

    public boolean shouldOpenTab() {
        return openTabCheck.isSelected();
    }

    public Optional<File> getJsonFile() {
        return Optional.ofNullable(jsonPathField.getText()).filter(s -> !s.isEmpty()).map(File::new);
    }

    public Optional<File> getExcelFile() {
        return Optional.ofNullable(excelPathField.getText()).filter(s -> !s.isEmpty()).map(File::new);
    }
}
