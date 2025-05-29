package de.bund.zrb.excel.dialogs;

import com.google.gson.Gson;
import de.zrb.bund.api.MainframeContext;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ExcelImportDialog extends JDialog {
    private final MainframeContext context;
    private static final String KEY_TRENNZEILE = "trennzeile";

    private static final String PLUGIN_KEY = "excelImporter";
    private static final String KEY_EXCEL = "lastExcelPath";
    private static final String KEY_JSON = "lastJsonPath";
    private static final String KEY_AUTO = "autoOpen";

    private JCheckBox headerCheckbox;
    private JSpinner headerRowSpinner;
    private JCheckBox appendCheckbox;
    private JComboBox<String> satzartDropdown;
    private JButton loadJsonButton;
    private JButton importButton;
    private JButton cancelButton;
    private JTextField trennzeileField;

    private boolean confirmed = false;

    private File selectedExcelFile;
    private JLabel excelFileLabel;
    private Map<String, Object> satzartenMap;

    public ExcelImportDialog(MainframeContext context) {
        super(context.getMainFrame(), "Excel-Import", true);
        this.context = context;
        setLayout(new BorderLayout());
        setSize(500, 280);
        setLocationRelativeTo(context.getMainFrame());

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        buildFileSelection(formPanel, gbc);
        buildHeaderControls(formPanel, gbc);
        buildSatzartControls(formPanel, gbc);
        buildAppendControl(formPanel, gbc);

        add(formPanel, BorderLayout.CENTER);
        buildButtons();
        loadInitialSettings();
    }

    private void buildFileSelection(JPanel formPanel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        formPanel.add(new JLabel("Excel-Datei:"), gbc);

        excelFileLabel = new JLabel("<keine Datei ausgewählt>");
        gbc.gridx = 1;
        gbc.weightx = 1;
        formPanel.add(excelFileLabel, gbc);

        JButton fileButton = new JButton("...");
        fileButton.setPreferredSize(new Dimension(30, 25));
        fileButton.setToolTipText("Excel-Datei auswählen");
        fileButton.addActionListener(e -> chooseFile());
        gbc.gridx = 2;
        gbc.weightx = 0;
        formPanel.add(fileButton, gbc);
    }

    private void buildHeaderControls(JPanel formPanel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        headerCheckbox = new JCheckBox("Spaltennamen in Zeile:");
        headerCheckbox.setSelected(true);
        formPanel.add(headerCheckbox, gbc);

        gbc.gridx = 2;
        gbc.gridwidth = 1;
        headerRowSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
        formPanel.add(headerRowSpinner, gbc);

        headerCheckbox.addActionListener(e -> headerRowSpinner.setEnabled(headerCheckbox.isSelected()));
    }

    private void buildSatzartControls(JPanel formPanel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy++;
        formPanel.add(new JLabel("Satzart:"), gbc);

        satzartDropdown = new JComboBox<>();
        gbc.gridx = 1;
        formPanel.add(satzartDropdown, gbc);

        loadJsonButton = new JButton("...");
        loadJsonButton.setPreferredSize(new Dimension(30, 25));
        loadJsonButton.setToolTipText("Satzarten-Layout aus JSON laden");
        loadJsonButton.addActionListener(e -> loadSatzartenJson());
        gbc.gridx = 2;
        formPanel.add(loadJsonButton, gbc);

        satzartDropdown.addActionListener(e -> updateAppendCheckboxFromSelectedSatzart());
    }

    private void buildAppendControl(JPanel formPanel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        appendCheckbox = new JCheckBox("An bestehende Datei anhängen");
        formPanel.add(appendCheckbox, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 1;
        formPanel.add(new JLabel("Trennzeile:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        trennzeileField = new JTextField();
        trennzeileField.setEnabled(false); // initial deaktiviert
        formPanel.add(trennzeileField, gbc);

        appendCheckbox.addActionListener(e ->
                trennzeileField.setEnabled(appendCheckbox.isSelected())
        );
    }

    private void buildButtons() {
        JPanel buttonPanel = new JPanel();
        importButton = new JButton("Importieren");
        cancelButton = new JButton("Abbrechen");

        importButton.addActionListener(e -> {
            confirmed = true;
            savePluginSettings();
            setVisible(false);
        });
        cancelButton.addActionListener(e -> setVisible(false));

        buttonPanel.add(importButton);
        buttonPanel.add(cancelButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedExcelFile = chooser.getSelectedFile();
            excelFileLabel.setText(selectedExcelFile.getAbsolutePath());
        }
    }

    private void loadInitialSettings() {
        Map<String, String> settings = getPluginSettings();

        String excelPath = settings.get(KEY_EXCEL);
        if (excelPath != null && !excelPath.isEmpty()) {
            File f = new File(excelPath);
            if (f.exists()) {
                selectedExcelFile = f;
                excelFileLabel.setText(f.getAbsolutePath());
            }
        }

        String jsonPath = settings.get(KEY_JSON);
        if (jsonPath != null && !jsonPath.isEmpty()) {
            File jsonFile = new File(jsonPath);
            if (jsonFile.exists()) {
                loadSatzartenJson(jsonFile);
            }
        }

        String trennzeile = settings.get(KEY_TRENNZEILE);
        if (trennzeile != null) {
            trennzeileField.setText(trennzeile);
        }
    }

    private void loadSatzartenJson() {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File jsonFile = chooser.getSelectedFile();
            loadSatzartenJson(jsonFile);
        }
    }

    private void loadSatzartenJson(File jsonFile) {
        try (Reader reader = new InputStreamReader(new FileInputStream(jsonFile), StandardCharsets.UTF_8)) {

            com.google.gson.reflect.TypeToken<java.util.Map<String, Object>> typeToken =
                    new com.google.gson.reflect.TypeToken<java.util.Map<String, Object>>() {};
            java.lang.reflect.Type type = typeToken.getType();

            Gson gson = new Gson();
            Object parsed = gson.fromJson(reader, type);

            if (!(parsed instanceof Map)) {
                throw new IllegalArgumentException("Ungültige JSON-Struktur – erwartet wurde ein Objekt mit Satzarten.");
            }

            satzartenMap = (Map<String, Object>) parsed;
            setSatzarten(new ArrayList<>(satzartenMap.keySet()));

            Map<String, String> settings = getPluginSettings();
            settings.put(KEY_JSON, jsonFile.getAbsolutePath());
            context.savePluginSettings(PLUGIN_KEY, settings);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Fehler beim Laden der Satzarten-Datei:\n" + ex.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
            satzartenMap = Collections.emptyMap(); // Fallback vermeiden
        }
    }

    private Map<String, String> getPluginSettings() {
        return context.loadPluginSettings(PLUGIN_KEY);
    }

    private void savePluginSettings() {
        Map<String, String> settings = getPluginSettings();

        if (selectedExcelFile != null) {
            settings.put(KEY_EXCEL, selectedExcelFile.getAbsolutePath());
        }

        if (trennzeileField.isEnabled()) {
            settings.put(KEY_TRENNZEILE, getTrennzeile());
        }

        context.savePluginSettings(PLUGIN_KEY, settings);
    }


    public boolean isConfirmed() {
        return confirmed;
    }

    public File getExcelFile() {
        return selectedExcelFile;
    }

    public boolean isHeaderEnabled() {
        return headerCheckbox.isSelected();
    }

    public int getHeaderRowIndex() {
        return isHeaderEnabled() ? ((int) headerRowSpinner.getValue()) - 1 : -1;
    }

    public boolean shouldAppend() {
        return appendCheckbox.isSelected();
    }

    public String getSelectedSatzart() {
        return (String) satzartDropdown.getSelectedItem();
    }

    public void setSatzarten(List<String> satzartNamen) {
        satzartDropdown.removeAllItems();
        for (String s : satzartNamen) {
            satzartDropdown.addItem(s);
        }
    }

    public Map<String, Object> getSatzartenMap() {
        return satzartenMap != null ? satzartenMap : Collections.emptyMap();
    }

    private void updateAppendCheckboxFromSelectedSatzart() {
        if (satzartenMap == null) return;

        String selected = getSelectedSatzart();
        if (selected == null) return;

        Object satzartObj = satzartenMap.get(selected);
        if (!(satzartObj instanceof Map)) return;

        Map<?, ?> satzart = (Map<?, ?>) satzartObj;
        Object metaObj = satzart.get("meta");
        if (!(metaObj instanceof Map)) {
            appendCheckbox.setSelected(false); // kein Meta: Checkbox deaktivieren
            return;
        }

        Map<?, ?> meta = (Map<?, ?>) metaObj;
        Object appendFlag = meta.get("append");

        if (appendFlag instanceof Boolean) {
            appendCheckbox.setSelected((Boolean) appendFlag);
        } else {
            appendCheckbox.setSelected(false); // kein oder ungültiges Flag
        }

        trennzeileField.setEnabled(appendCheckbox.isSelected());
    }

    public String getTrennzeile() {
        return trennzeileField.getText();
    }
}