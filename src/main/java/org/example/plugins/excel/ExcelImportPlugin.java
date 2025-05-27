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

            // 🆕 Satzart und Felder extrahieren
            Map<String, Object> satzart = getSatzartDefinition(satzartenMap, satzartName);
            if (satzart == null) return;

            List<Map<String, Object>> felder = getFeldDefinitionen(satzart, satzartName);
            if (felder == null) return;

            // 🆕 Übergib felder explizit
            String formatted = formatFixedWidthBySatzart(table, felder, satzartName);

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

            insertTextIntoEditor(formatted, felder);

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

    private String formatFixedWidthBySatzart(Map<String, List<String>> table, List<Map<String, Object>> felder, String satzartName) {
        if (table.isEmpty() || felder == null || felder.isEmpty()) {
            showError(mainFrame, "Keine Daten oder Felddefinitionen verfügbar.");
            return null;
        }

        Map<String, String> pluginSettings = getPluginSettings();
        boolean stopOnEmptyRequired = Boolean.parseBoolean(pluginSettings.getOrDefault("stopOnEmptyRequired", "true"));
        boolean requireAllFieldsEmpty = Boolean.parseBoolean(pluginSettings.getOrDefault("requireAllFieldsEmpty", "false"));

        int rowCount = calculateMaxRows(table);
        StringBuilder result = new StringBuilder();

        boolean usedTableColumnAtLeastOnce = false;

        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            boolean usedColumnThisRow = false;
            boolean foundEmptyRequiredField = false;

            Map<Integer, char[]> rowLines = new TreeMap<>();

            for (Map<String, Object> feld : felder) {
                if (!isFeldValid(feld, satzartName)) continue;

                int row = getIntValue(feld.get("row"));
                if (row < 1) row = 1;

                char[] line = rowLines.computeIfAbsent(row, r -> createBlankLine(256));
                int start = getIntValue(feld.get(KEY_POS)) - 1;
                int len = getIntValue(feld.get(KEY_LEN));
                String padded;

                if (feld.containsKey("value")) {
                    // Konstante Werte
                    String fixed = String.valueOf(feld.get("value"));
                    padded = padRight(fixed, len);
                } else {
                    String name = (String) feld.get(KEY_NAME);
                    List<String> column = findColumnByName(table, name);
                    if (column == null) continue;

                    String value = rowIndex < column.size() ? column.get(rowIndex) : "";
                    if (value == null || value.trim().isEmpty()) {
                        if (!requireAllFieldsEmpty) {
                            foundEmptyRequiredField = true;
                            break; // reicht schon
                        }
                    } else {
                        usedColumnThisRow = true;
                        if (requireAllFieldsEmpty) foundEmptyRequiredField = false;
                    }

                    padded = padRight(value, len);
                }

                insertIntoLine(line, start, padded);
            }

            if (foundEmptyRequiredField) break; // abbrechen, wenn relevante Felder leer

            if (!usedColumnThisRow && usedTableColumnAtLeastOnce) {
                // Nur Konstante verwendet und bereits einmal geschrieben
                break;
            }

            usedTableColumnAtLeastOnce |= usedColumnThisRow;

            for (char[] line : rowLines.values()) {
                result.append(new String(line)).append("\n");
            }
        }

        return result.toString();
    }


    private List<String> buildMultiLineRecord(
            Map<String, List<String>> table,
            List<Map<String, Object>> felder,
            int rowIndex,
            String satzartName) {

        Map<Integer, char[]> rowLines = new TreeMap<>();

        for (Map<String, Object> feld : felder) {
            if (!isFeldValid(feld, satzartName)) continue;

            int row = getIntValue(feld.get("row"));
            if (row < 1) row = 1; // default row

            char[] line = rowLines.computeIfAbsent(row, r -> createBlankLine(256)); // max Länge schätzen
            int start = getIntValue(feld.get(KEY_POS)) - 1;
            int len = getIntValue(feld.get(KEY_LEN));
            String padded;

            if (feld.containsKey("value")) {
                String fixed = String.valueOf(feld.get("value"));
                padded = padRight(fixed, len);
            } else {
                String name = (String) feld.get(KEY_NAME);
                List<String> column = findColumnByName(table, name);
                if (column == null) {
                    System.err.println("⚠️ Spalte \"" + name + "\" nicht in Tabelle enthalten.");
                    continue;
                }
                String value = rowIndex < column.size() ? column.get(rowIndex) : "";
                padded = padRight(value, len);
            }

            insertIntoLine(line, start, padded);
        }

        // Reihenfolge sicherstellen (1, 2, 3...)
        List<String> resultLines = new ArrayList<>();
        for (char[] line : rowLines.values()) {
            resultLines.add(new String(line));
        }

        return resultLines;
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

    private void insertTextIntoEditor(String text, List<Map<String, Object>> feldDefinitionen) {
        Optional<FileTab> optionalTab = mainFrame.getTabManager().getSelectedFileTab();

        FileTab fileTab = optionalTab.orElseGet(() -> createNewFileTab(null));

        fileTab.setStructuredContent(text, feldDefinitionen, getMaxRowNumber(feldDefinitionen));
        fileTab.markAsChanged();
    }

    private FileTab createNewFileTab(String content) {
        mainFrame.getTabManager().openFileTab(content);

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

    private int getMaxRowNumber(List<Map<String, Object>> felder) {
        return felder.stream()
                .map(f -> f.containsKey("row") ? ((Number) f.get("row")).intValue() : 1)
                .max(Integer::compareTo)
                .orElse(1);
    }

}
