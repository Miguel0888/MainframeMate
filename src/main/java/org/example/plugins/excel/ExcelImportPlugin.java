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

        File excelFile = dialog.getExcelFile().orElse(null);
        if (excelFile == null || !excelFile.exists()) {
            showError(mainFrame, "Excel-Datei wurde nicht gefunden.");
            return;
        }

        try {
//            Map<String, List<String>> table = ExcelImportParser.readExcelAsTable(excelFile, true);
            Map<String, List<String>> table = ExcelImportParser.readExcelAsTable(excelFile, true, 0);
            String formatted = formatWithEmojis(table);
            insertTextIntoEditor(formatted);

            JOptionPane.showMessageDialog(mainFrame,
                    "Die Datei wurde mit dem importierten Excel-Inhalt aktualisiert.",
                    "Import abgeschlossen", JOptionPane.INFORMATION_MESSAGE);

        } catch (InvalidFormatException ex) {
            showError(mainFrame, "Die gewählte Datei ist kein gültiges Excel-Dokument.");
        } catch (IOException ex) {
            showError(mainFrame, "Fehler beim Lesen der Datei:\n" + ex.getMessage());
        }
    }

    private String formatWithEmojis(Map<String, List<String>> table) {
        if (table.isEmpty()) {
            showError(mainFrame, "Die Excel-Tabelle enthält keine Daten.");
            return null;
        }

        StringBuilder sb = new StringBuilder();
        List<String> headers = new ArrayList<>(table.keySet());

        int rowCount = table.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);

        for (int i = 0; i < rowCount; i++) {
            for (int c = 0; c < headers.size(); c++) {
                List<String> column = table.get(headers.get(c));
                String value = i < column.size() ? column.get(i) : "";
                sb.append(value);
                if (c < headers.size() - 1) sb.append(" 😎 ");
            }
            sb.append("\n");
        }

        return sb.toString();
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
        dummyMeta.setName("import.csv");
        dummyMeta.setSize(content.getBytes(StandardCharsets.UTF_8).length);

        FtpFileBuffer buffer = new FtpFileBuffer("/import.csv", dummyMeta);
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
