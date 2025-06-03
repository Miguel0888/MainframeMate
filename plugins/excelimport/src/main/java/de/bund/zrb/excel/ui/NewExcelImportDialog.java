package de.bund.zrb.excel.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.bund.zrb.excel.model.ExcelMapping;
import de.bund.zrb.excel.model.ExcelMappingEntry;
import de.bund.zrb.excel.repo.TemplateRepository;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.api.SentenceTypeRegistry;
import de.zrb.bund.newApi.sentence.SentenceField;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class NewExcelImportDialog extends JDialog {

    private final File mappingsFile;

    private final MainframeContext context;
    private final JComboBox<String> templateBox = new JComboBox<>();
    private final JComboBox<String> sentenceTypeBox = new JComboBox<>();
    private final JTable mappingTable;
    private final DefaultTableModel tableModel;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, List<String>> sentenceFields = new HashMap<>();
    private final TemplateRepository templateRepo;
    private Map<String, ExcelMapping> mappings;

    private String lastSavedTemplateName;

    private final List<String> availableExcelColumns;

    public NewExcelImportDialog(MainframeContext context, List<String> availableExcelColumns) {
        super(context.getMainFrame(), "Neue Excel-Mapping-Vorlage", true);
        this.context = context;
        this.availableExcelColumns = availableExcelColumns != null ? availableExcelColumns : Collections.emptyList();
        this.templateRepo = new TemplateRepository(context.getSettingsFolder());
        mappingsFile = new File(context.getSettingsFolder(), "import.json");
        setSize(750, 500);
        setLocationRelativeTo(context.getMainFrame());
        setLayout(new BorderLayout());

        // Table
        String[] columns = {"Herkunft", "Wert", "Feldname"};
        tableModel = new DefaultTableModel(columns, 0);
        mappingTable = new JTable(tableModel);

        // Editor für Herkunft
        JComboBox<String> sourceBox = new JComboBox<>(new String[]{"Excel", "Fixwert", "Ausdruck"});
        mappingTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(sourceBox));

        // Editor für Wert (Excel-Spalten)
        JComboBox<String> excelColumnBox = new JComboBox<>(availableExcelColumns.toArray(new String[0]));
        excelColumnBox.setEditable(true);
        mappingTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(excelColumnBox));

        // Editor für Feldnamen (aus Satzart)
        updateFieldComboBoxes(); // setzt Column 2 korrekt

        // Top Panel
        JPanel topPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));


        topPanel.add(new JLabel("Vorlagen-Name:"));

        // Wrapper mit ComboBox + Button-Leiste
        JPanel templatePanel = new JPanel(new BorderLayout(5, 0));
        templateBox.setEditable(true);
        templateBox.addActionListener(e -> {
            String name = (String) templateBox.getEditor().getItem();
            if (name != null && mappings.containsKey(name)) {
                loadMappingToTable(mappings.get(name));
            }
        });
        templatePanel.add(templateBox, BorderLayout.CENTER);

        // ➕ Neu-Button
        JButton newBtn = new JButton("➕");
        newBtn.setMargin(new Insets(2, 4, 2, 4));
        newBtn.addActionListener(e -> {
            templateBox.setSelectedItem(""); // leeren Namen setzen
            tableModel.setRowCount(0); // Tabelle leeren
        });

        // 🗑 Löschen-Button
        JButton deleteBtn = new JButton("❌");
        deleteBtn.setMargin(new Insets(2, 4, 2, 4));
        deleteBtn.addActionListener(e -> {
            String name = (String) templateBox.getEditor().getItem();
            if (name != null && templateRepo.getTemplate(name) != null) {
                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        "Vorlage „" + name + "“ wirklich löschen?",
                        "Bestätigung",
                        JOptionPane.YES_NO_OPTION
                );
                if (confirm == JOptionPane.YES_OPTION) {
                    templateRepo.deleteTemplate(name);
                    updateTemplateBox();
                    templateBox.setSelectedItem("");
                    tableModel.setRowCount(0);
                }
            }
        });


        JPanel buttonRow = new JPanel(new GridLayout(1, 2, 2, 0));
        buttonRow.add(newBtn);
        buttonRow.add(deleteBtn);
        templatePanel.add(buttonRow, BorderLayout.EAST);

        topPanel.add(templatePanel);


        topPanel.add(new JLabel("Ziel-Satzart:"));
        topPanel.add(sentenceTypeBox);

        loadSentenceTypes();
        sentenceTypeBox.addActionListener(e -> updateFieldComboBoxes());

        JScrollPane tableScroll = new JScrollPane(mappingTable);

        // Button Panel
        JPanel buttonPanel = new JPanel(new BorderLayout());
        JButton addRowBtn = new JButton("➕ Feld hinzufügen");
        JButton removeRowBtn = new JButton("🗑 Entfernen");
        JButton exportBtn = new JButton("💾 Exportieren...");
        JButton persistBtn = new JButton("✅ Speichern");
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            saveMappingToDisk();
            String selected = ((String) templateBox.getEditor().getItem()).trim();
            if (selected != null && !selected.isEmpty()) {
                lastSavedTemplateName = selected;
            }
            dispose();
        });
        persistBtn.addActionListener(e -> { // ToDo: improve dirty fix
            suppressEventAndSave();
        });

        JButton cancelBtn = new JButton("Abbrechen");

        addRowBtn.addActionListener(e -> addRow());
        removeRowBtn.addActionListener(e -> {
            int row = mappingTable.getSelectedRow();
            if (row >= 0) tableModel.removeRow(row);
        });
        cancelBtn.addActionListener(e -> dispose());
        exportBtn.addActionListener(e -> saveMapping());

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftButtons.add(addRowBtn);
        leftButtons.add(removeRowBtn);
        leftButtons.add(exportBtn);
        leftButtons.add(persistBtn);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightButtons.add(okButton);
        rightButtons.add(cancelBtn);

        buttonPanel.add(leftButtons, BorderLayout.WEST);
        buttonPanel.add(rightButtons, BorderLayout.EAST);

        // Add to dialog
        add(topPanel, BorderLayout.NORTH);
        add(tableScroll, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        loadMappingsFromDisk();
        updateTemplateBox();
    }

    private void suppressEventAndSave() {
        // Listener temporär deaktivieren
        ActionListener[] listeners = templateBox.getActionListeners();
        for (ActionListener l : listeners) {
            templateBox.removeActionListener(l);
        }

        saveMappingToDisk();

        // Listener wieder aktivieren
        for (ActionListener l : listeners) {
            templateBox.addActionListener(l);
        }
    }

    private void saveAllMappings() {
        try (FileWriter writer = new FileWriter(mappingsFile)) {
            gson.toJson(mappings, writer);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Fehler beim Speichern:\n" + ex.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadMappingsFromDisk() {
        if (!mappingsFile.exists()) {
            mappings = new LinkedHashMap<>();
            return;
        }
        try (FileReader reader = new FileReader(mappingsFile)) {
            java.lang.reflect.Type type = new TypeToken<Map<String, ExcelMapping>>() {}.getType();
            mappings = gson.fromJson(reader, type);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Fehler beim Laden der Mappings:\n" + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
            mappings = new LinkedHashMap<>();
        }
    }

    private void updateTemplateBox() {
        String selected = (String) templateBox.getEditor().getItem();

        templateBox.removeAllItems();
        for (String name : templateRepo.getTemplateNames()) {
            templateBox.addItem(name);
        }

        if (selected != null && templateRepo.getTemplateNames().contains(selected)) {
            templateBox.setSelectedItem(selected);
        } else if (!templateRepo.getTemplateNames().isEmpty()) {
            templateBox.setSelectedItem(templateRepo.getTemplateNames().iterator().next());
        } else {
            templateBox.setSelectedItem(null);
        }
    }


    private void loadMappingToTable(ExcelMapping mapping) {
        sentenceTypeBox.setSelectedItem(mapping.getSentenceType());
        tableModel.setRowCount(0);
        for (ExcelMappingEntry entry : mapping.getEntries()) {
            String source, value;

            if (entry.getExcelColumn() != null) {
                source = "Excel";
                value = entry.getExcelColumn();
            } else if (entry.getFixedValue() != null) {
                source = "Fixwert";
                value = entry.getFixedValue();
            } else {
                source = "Ausdruck";
                value = entry.getExpression();
            }

            tableModel.addRow(new Object[]{source, value, entry.getFieldName()});
        }
    }


    private void loadSentenceTypes() {
        SentenceTypeRegistry registry = context.getSentenceTypeRegistry();
        registry.getSentenceTypeSpec().getDefinitions()
                .forEach((name, def) -> {
                    sentenceTypeBox.addItem(name);
                    List<String> fields = new ArrayList<>();
                    if (def.getFields() != null) {
                        for (SentenceField field : def.getFields()) {
                            fields.add(field.getName());
                        }
                    }
                    sentenceFields.put(name, fields);
                });
    }

    private void updateFieldComboBoxes() {
        String selectedType = (String) sentenceTypeBox.getSelectedItem();
        List<String> fields = sentenceFields.getOrDefault(selectedType, Collections.emptyList());

        for (int row = 0; row < tableModel.getRowCount(); row++) {
            JComboBox<String> fieldCombo = new JComboBox<>(fields.toArray(new String[0]));
            fieldCombo.setEditable(true);
            mappingTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(fieldCombo));
        }
    }

    private void addRow() {
        tableModel.addRow(new Object[]{"Excel", "", ""});
    }

    private void saveMapping() {
        String name = ((String) templateBox.getEditor().getItem()).trim();
        String sentenceType = (String) sentenceTypeBox.getSelectedItem();

        if (name.isEmpty() || sentenceType == null || sentenceType.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bitte Namen und Satzart angeben.", "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }

        ExcelMapping mapping = new ExcelMapping();
        mapping.setName(name);
        mapping.setSentenceType(sentenceType);

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            ExcelMappingEntry entry = new ExcelMappingEntry();
            String source = getString(i, 0);
            String value = getString(i, 1);
            String targetField = getString(i, 2);

            switch (source.toLowerCase()) {
                case "excel":
                    entry.setExcelColumn(value);
                    break;
                case "fixwert":
                    entry.setFixedValue(value);
                    break;
                case "ausdruck":
                    entry.setExpression(value);
                    break;
            }

            if (targetField != null && !targetField.isEmpty()) {
                entry.setFieldName(targetField);
                mapping.addEntry(entry);
            }
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Speichern unter...");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON-Dateien", "json"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (FileWriter writer = new FileWriter(chooser.getSelectedFile())) {
                mappings = new LinkedHashMap<>();
                mappings.put(mapping.getName(), mapping);
                gson.toJson(mappings, writer);
                JOptionPane.showMessageDialog(this, "Mapping erfolgreich gespeichert.", "Erfolg", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Fehler beim Speichern:\n" + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void saveMappingToDisk() {
        // Übernehme ggf. Editierungen
        if (mappingTable.isEditing()) {
            mappingTable.getCellEditor().stopCellEditing();
        }
        String name = ((String) templateBox.getEditor().getItem()).trim();
        lastSavedTemplateName = name;
        String sentenceType = (String) sentenceTypeBox.getSelectedItem();

        if (name.isEmpty() || sentenceType == null) {
            JOptionPane.showMessageDialog(this, "Name und Satzart angeben.", "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }

        ExcelMapping mapping = new ExcelMapping();
        mapping.setName(name);
        mapping.setSentenceType(sentenceType);

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            ExcelMappingEntry entry = new ExcelMappingEntry();
            String source = getString(i, 0);
            String value = getString(i, 1);
            String targetField = getString(i, 2);

            switch (source.toLowerCase()) {
                case "excel": entry.setExcelColumn(value); break;
                case "fixwert": entry.setFixedValue(value); break;
                case "ausdruck": entry.setExpression(value); break;
            }

            entry.setFieldName(targetField);
            mapping.addEntry(entry);
        }

        templateRepo.saveTemplate(name, mapping);
        updateTemplateBox();
    }

    private String getString(int row, int col) {
        Object val = tableModel.getValueAt(row, col);
        return val != null ? val.toString().trim() : "";
    }

    public String getLastSavedTemplateName() {
        return lastSavedTemplateName;
    }


}
