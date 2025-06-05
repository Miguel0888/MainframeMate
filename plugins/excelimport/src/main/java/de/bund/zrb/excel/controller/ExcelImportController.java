package de.bund.zrb.excel.controller;

import de.bund.zrb.excel.plugin.ExcelImport;
import de.bund.zrb.excel.service.Converter;
import de.bund.zrb.excel.service.ExcelParser;
import de.bund.zrb.excel.ui.ExcelImportUiDialog;
import de.bund.zrb.excel.ui.ExcelImportUiPanel;
import de.bund.zrb.excel.model.ExcelMapping;
import de.bund.zrb.excel.repo.TemplateRepository;
import de.zrb.bund.api.TabAdapter;
import de.zrb.bund.newApi.sentence.SentenceDefinition;
import de.zrb.bund.newApi.sentence.SentenceField;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class ExcelImportController {

    public static void handleImport(ExcelImport plugin) {
        ExcelImportUiDialog dialog = new ExcelImportUiDialog(plugin.getContext());
        dialog.setVisible(true);
        ExcelImportUiPanel ui = dialog.getUiPanel();

        File excelFile = ui.getExcelFile();
        if (excelFile == null || !excelFile.exists()) {
            showError(plugin.getMainFrame(), "Excel-Datei wurde nicht gefunden.");
            return;
        }

        String templateName = ui.getSelectedTemplateName();
        if (templateName == null || templateName.trim().isEmpty()) {
            showError(plugin.getMainFrame(), "Bitte wähle eine Mapping-Vorlage aus.");
            return;
        }

        TemplateRepository repo = plugin.getTemplateRepository();
        ExcelMapping mapping = repo.getTemplate(templateName);
        if (mapping == null) {
            showError(plugin.getMainFrame(), "Mapping-Vorlage nicht gefunden: " + templateName);
            return;
        }

        String satzartName = mapping.getSentenceType();
        if (satzartName == null || satzartName.trim().isEmpty()) {
            showError(plugin.getMainFrame(), "Keine Satzart im Mapping definiert.");
            return;
        }

        SentenceDefinition satzart = plugin.getContext().getSentenceTypeRegistry()
                .getSentenceTypeSpec()
                .getDefinitions()
                .get(satzartName);

        if (satzart == null || satzart.getFields() == null || satzart.getFields().isEmpty()) {
            showError(plugin.getMainFrame(), "Satzart oder Felder nicht gefunden: " + satzartName);
            return;
        }

        Map<Integer, SentenceField> felder = satzart.getFields();
        int schemaLines = satzart.getRowCount() != null ? satzart.getRowCount() : 1;

        boolean stopOnEmptyRequiredCheck = Boolean.parseBoolean(plugin.getSettings().getOrDefault("stopOnEmptyRequired", "true"));
        boolean requireAllFieldsEmptyCheck = Boolean.parseBoolean(plugin.getSettings().getOrDefault("requireAllFieldsEmpty", "false"));

        try {
            Map<String, List<String>> excelData = ExcelParser.readExcelAsTable(
                    excelFile,
                    ui.isHeaderEnabled(),
                    ui.getHeaderRowIndex(),
                    stopOnEmptyRequiredCheck,
                    requireAllFieldsEmptyCheck
            );

            int rowCount = excelData.values().stream()
                    .findFirst()
                    .map(List::size)
                    .orElse(0);

            StringBuilder builder = new StringBuilder();
            Converter converter = Converter.getInstance();

            for (int i = 0; i < rowCount; i++) {
                int finalI = i;

                String record = converter.generateRecordLines(
                        felder,
                        schemaLines,
                        mapping,
                        columnName -> {
                            List<String> column = excelData.get(columnName);
                            return (column != null && finalI < column.size()) ? column.get(finalI) : "";
                        });

                builder.append(record).append("\n");
            }

            String result = builder.toString();
            if (result.trim().isEmpty()) {
                showError(plugin.getMainFrame(), "Kein Inhalt erzeugt – bitte Mapping und Datei prüfen.");
                return;
            }

            if (ui.shouldAppend()) {
                Optional<TabAdapter> tab = plugin.getContext().getSelectedTab();
                String existing = tab.map(TabAdapter::getContent).orElse("");
                String trenn = ui.getTrennzeile();
                result = existing + (trenn == null ? "" : trenn + "\n") + result;
            }

            plugin.getContext().openFileTab(result, satzartName);

            Map<String, String> settings = plugin.getSettings();
            if (Boolean.parseBoolean(settings.getOrDefault("showConfirmation", "true"))) {
                JOptionPane.showMessageDialog(plugin.getMainFrame(),
                        "Die Datei wurde mit dem importierten Excel-Inhalt aktualisiert.",
                        "Import abgeschlossen", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (InvalidFormatException e) {
            showError(plugin.getMainFrame(), "Ungültiges Excel-Format.");
        } catch (IOException e) {
            showError(plugin.getMainFrame(), "Fehler beim Lesen:\n" + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            showError(plugin.getMainFrame(), "Unerwarteter Fehler:\n" + e.getMessage());
        }
    }

    private static void showError(Component parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "Fehler", JOptionPane.ERROR_MESSAGE);
    }
}
