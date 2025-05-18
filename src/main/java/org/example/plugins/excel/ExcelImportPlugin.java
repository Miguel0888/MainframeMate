package org.example.plugins.excel;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.example.ftp.FtpFileBuffer;
import org.example.ftp.FtpManager;
import org.example.model.Settings;
import org.example.plugins.MainframeMatePlugin;
import org.example.ui.FileTab;
import org.example.ui.MainFrame;
import org.example.util.SettingsManager;

import javax.swing.*;
import java.awt.Component;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ExcelImportPlugin implements MainframeMatePlugin {

    private static final String KEY_NAME = "name";
    private static final String KEY_POS = "pos";
    private static final String KEY_LEN = "len";
    private static final String KEY_FIELDS = "felder";


    private static final String PLUGIN_KEY = "excelImporter";

    MainFrame mainFrame;

    private Map<String, String> getPluginSettings() {
        Settings settings = SettingsManager.load();
        return settings.pluginSettings.computeIfAbsent(PLUGIN_KEY, k -> new LinkedHashMap<>());
    }

    private void savePluginSettings(Map<String, String> newValues) {
        Settings settings = SettingsManager.load();
        settings.pluginSettings.put(PLUGIN_KEY, new LinkedHashMap<>(newValues));
        SettingsManager.save(settings);
    }

    @Override
    public String getPluginName() {
        return "Excel-Importer";
    }

    @Override
    public void initialize(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        JMenu pluginMenu = mainFrame.getOrCreatePluginMenu();
        JMenuItem importItem = new JMenuItem("Excel-Import");

        importItem.addActionListener(e -> handleImport());

        pluginMenu.add(importItem);
    }

    private void handleImport() {
        ExcelImportDialog dialog = new ExcelImportDialog(mainFrame);
        dialog.setVisible(true);
        if (!dialog.isConfirmed()) return;

        File excelFile = dialog.getExcelFile();
        if (excelFile == null || !excelFile.exists()) {
            showError(mainFrame, "Excel-Datei wurde nicht gefunden.");
            return;
        }

        String satzartName = dialog.getSelectedSatzart();
        if (satzartName == null || satzartName.trim().isEmpty()) {
            showError(mainFrame, "Bitte wähle eine Satzart aus.");
            return;
        }

        Map<String, Object> satzartenMap = dialog.getSatzartenMap();
        if (satzartenMap == null || satzartenMap.isEmpty()) {
            showError(mainFrame, "Es wurde kein gültiges Satzarten-Layout geladen.");
            return;
        }

        try {
            Map<String, List<String>> table = ExcelImportParser.readExcelAsTable(
                    excelFile,
                    dialog.isHeaderEnabled(),
                    dialog.getHeaderRowIndex()
            );

            String formatted = formatFixedWidthBySatzart(table, satzartName, satzartenMap);

            if (formatted == null || formatted.trim().isEmpty()) {
                showError(mainFrame, "Kein Inhalt wurde erzeugt – bitte prüfe die Felddefinition oder die Excel-Datei.");
                return;
            }

            // 🔁 An bestehende Datei anhängen?
            if (dialog.shouldAppend()) {
                Optional<FileTab> optionalTab = mainFrame.getTabManager().getSelectedFileTab();
                String existing = optionalTab.map(FileTab::getContent).orElse("");
                String trennzeile = dialog.getTrennzeile();

                String separator = (trennzeile != null && !trennzeile.trim().isEmpty())
                        ? trennzeile + "\n"
                        : "";

                formatted = existing + separator + formatted;
            }

            insertTextIntoEditor(formatted);
            Map<String, String> settings = getPluginSettings();
            boolean showConfirmation = Boolean.parseBoolean(settings.getOrDefault("showConfirmation", "true"));

            if (showConfirmation) {
                JOptionPane.showMessageDialog(mainFrame,
                        "Die Datei wurde mit dem importierten Excel-Inhalt aktualisiert.",
                        "Import abgeschlossen", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (InvalidFormatException ex) {
            showError(mainFrame, "Die gewählte Datei ist kein gültiges Excel-Dokument.");
        } catch (IOException ex) {
            showError(mainFrame, "Fehler beim Lesen der Datei:\n" + ex.getMessage());
        } catch (Exception ex) {
            showError(mainFrame, "Unerwarteter Fehler:\n" + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private String formatFixedWidthBySatzart(Map<String, List<String>> table, String satzartName, Map<String, Object> satzartenMap) {
        if (table.isEmpty() || satzartenMap == null) {
            showError(mainFrame, "Keine Daten oder Satzart verfügbar.");
            return null;
        }

        Map<String, Object> satzart = getSatzartDefinition(satzartenMap, satzartName);
        if (satzart == null) return null;

        List<Map<String, Object>> felder = getFeldDefinitionen(satzart, satzartName);
        if (felder == null) return null;

        int rowCount = calculateMaxRows(table);
        int recordLength = calculateRecordLength(felder);

        StringBuilder result = new StringBuilder();

        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            char[] line = createBlankLine(recordLength);
            fillLineWithRowData(line, rowIndex, felder, table, satzartName);
            result.append(new String(line)).append("\n");
        }

        return result.toString();
    }

    private void fillLineWithRowData(char[] line, int rowIndex, List<Map<String, Object>> felder,
                                     Map<String, List<String>> table, String satzartName) {
        for (Map<String, Object> feld : felder) {
            if (!isFeldValid(feld, satzartName)) return;

            String name = (String) feld.get(KEY_NAME);
            int start = getIntValue(feld.get(KEY_POS)) - 1;
            int len = getIntValue(feld.get(KEY_LEN));

            // 🆕 Konstante prüfen
            if (feld.containsKey("value")) {
                String fixed = String.valueOf(feld.get("value"));
                String padded = padRight(fixed, len);
                insertIntoLine(line, start, padded);
                continue; // Excel-Wert überspringen
            }

            // 📥 Excel-Wert verarbeiten
            List<String> column = findColumnByName(table, name);
            if (column == null) {
                System.err.println("⚠️ Spalte \"" + name + "\" nicht in Tabelle enthalten.");
                continue;
            }

            String value = rowIndex < column.size() ? column.get(rowIndex) : "";
            String padded = padRight(value, len);
            insertIntoLine(line, start, padded);
        }
    }


    private List<String> findColumnByName(Map<String, List<String>> table, String fieldName) {
        for (Map.Entry<String, List<String>> entry : table.entrySet()) {
            String key = entry.getKey().trim();
            if (key.equalsIgnoreCase(fieldName.trim())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private int getIntValue(Object obj) {
        return (obj instanceof Number) ? ((Number) obj).intValue() : 0;
    }

    private Map<String, Object> getSatzartDefinition(Map<String, Object> map, String name) {
        Object def = map.get(name);
        if (!(def instanceof Map)) {
            showError(mainFrame, "Satzart \"" + name + "\" nicht gefunden oder ungültig.");
            return null;
        }
        return (Map<String, Object>) def;
    }

    private List<Map<String, Object>> getFeldDefinitionen(Map<String, Object> satzart, String satzartName) {
        Object list = satzart.get(KEY_FIELDS);
        if (!(list instanceof List)) {
            showError(mainFrame, "Satzart \"" + satzartName + "\" enthält keine Felddefinitionen.");
            return null;
        }
        return (List<Map<String, Object>>) list;
    }

    private int calculateMaxRows(Map<String, List<String>> table) {
        return table.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);
    }

    private int calculateRecordLength(List<Map<String, Object>> felder) {
        return felder.stream()
                .filter(f -> f.get(KEY_POS) instanceof Number && f.get(KEY_LEN) instanceof Number)
                .mapToInt(f -> ((Number) f.get(KEY_POS)).intValue() + ((Number) f.get(KEY_LEN)).intValue() - 1)
                .max()
                .orElse(0);
    }

    private char[] createBlankLine(int length) {
        char[] line = new char[length];
        Arrays.fill(line, ' ');
        return line;
    }

    private void insertIntoLine(char[] line, int start, String content) {
        for (int i = 0; i < content.length() && (start + i) < line.length; i++) {
            line[start + i] = content.charAt(i);
        }
    }

    private boolean isFeldValid(Map<String, Object> feld, String satzartName) {
        Object pos = feld.get(KEY_POS);
        Object len = feld.get(KEY_LEN);
        Object name = feld.get(KEY_NAME);

        if (!(pos instanceof Number) || !(len instanceof Number)) {
            String feldName = name != null ? name.toString() : "<unbenannt>";
            showError(mainFrame,
                    "Die Satzart \"" + satzartName + "\" enthält ein ungültiges Feld:\n" +
                            "Feld \"" + feldName + "\" hat keine gültige Position (\"" + KEY_POS + "\") oder Länge (\"" + KEY_LEN + "\").");
            return false;
        }
        return true;
    }

    private String padRight(String input, int length) {
        if (input == null) input = "";
        if (input.length() >= length) return input.substring(0, length);
        return input + repeatSpace(length - input.length());
    }

    private String repeatSpace(int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, ' ');
        return new String(chars);
    }

    private void insertTextIntoEditor(String text) {
        Optional<FileTab> optionalTab = mainFrame.getTabManager().getSelectedFileTab();
        FileTab fileTab;

        if (optionalTab.isPresent()) {
            fileTab = optionalTab.get();
        } else {
            fileTab = createNewFileTab(text);
        }

        fileTab.setContent(text);
        fileTab.markAsChanged();
    }

    private FileTab createNewFileTab(String content) {
        org.apache.commons.net.ftp.FTPFile dummyMeta = new org.apache.commons.net.ftp.FTPFile();
        dummyMeta.setName("import");
        dummyMeta.setSize(content.getBytes(StandardCharsets.UTF_8).length);

        FtpFileBuffer buffer = new FtpFileBuffer("import", dummyMeta);
        try {
            buffer.loadContent(
                    new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                    null
            );
        } catch (IOException ex) {
            showError(mainFrame, "Fehler beim Erstellen des neuen Datei-Tabs:\n" + ex.getMessage());
            throw new RuntimeException(ex);
        }

        FtpManager dummyManager = new FtpManager();
        mainFrame.getTabManager().openFileTab(dummyManager, buffer);

        return mainFrame.getTabManager().getSelectedFileTab()
                .orElseThrow(() -> new IllegalStateException("Datei-Tab konnte nicht erzeugt werden"));
    }

    private void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Fehler", JOptionPane.ERROR_MESSAGE);
    }



    private File chooseFile(Component parent, String title, String fileDesc, String... extensions) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(fileDesc, extensions));
        int result = chooser.showOpenDialog(parent);
        if (result == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;
    }

    @Override
    public Optional<JMenuItem> getSettingsMenuItem(MainFrame mainFrame) {
        JMenuItem item = new JMenuItem("Excel-Importer...");
        item.addActionListener(e -> {
            ExcelImportSettingsDialog dialog = new ExcelImportSettingsDialog(mainFrame);
            dialog.setVisible(true);
        });
        return Optional.of(item);
    }


}
