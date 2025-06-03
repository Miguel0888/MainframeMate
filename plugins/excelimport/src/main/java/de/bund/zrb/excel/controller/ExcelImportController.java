package de.bund.zrb.excel.controller;

import de.bund.zrb.excel.ExcelImportParser;
import de.bund.zrb.excel.dialogs.ExcelImportUiDialog;
import de.bund.zrb.excel.dialogs.ExcelImportUiPanel;
import de.bund.zrb.excel.model.ExcelMapping;
import de.bund.zrb.excel.model.ExcelMappingEntry;
import de.bund.zrb.excel.repo.TemplateRepository;
import de.zrb.bund.api.MainframeContext;
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

    public static void handleImport(MainframeContext context) {
        ExcelImportUiDialog dialog = new ExcelImportUiDialog(context);
        dialog.setVisible(true);
        ExcelImportUiPanel ui = dialog.getUiPanel();

        File excelFile = ui.getExcelFile();
        if (excelFile == null || !excelFile.exists()) {
            showError(context.getMainFrame(), "Excel-Datei wurde nicht gefunden.");
            return;
        }

        String templateName = ui.getSelectedTemplateName();
        if (templateName == null || templateName.trim().isEmpty()) {
            showError(context.getMainFrame(), "Bitte wähle eine Mapping-Vorlage aus.");
            return;
        }

        TemplateRepository repo = new TemplateRepository(context.getSettingsFolder());
        ExcelMapping mapping = repo.getTemplate(templateName);
        if (mapping == null) {
            showError(context.getMainFrame(), "Mapping-Vorlage nicht gefunden: " + templateName);
            return;
        }

        String satzartName = mapping.getSentenceType();
        if (satzartName == null || satzartName.trim().isEmpty()) {
            showError(context.getMainFrame(), "Keine Satzart im Mapping definiert.");
            return;
        }

        SentenceDefinition satzart = context.getSentenceTypeRegistry()
                .getSentenceTypeSpec()
                .getDefinitions()
                .get(satzartName);

        if (satzart == null || satzart.getFields() == null || satzart.getFields().isEmpty()) {
            showError(context.getMainFrame(), "Satzart oder Felder nicht gefunden: " + satzartName);
            return;
        }

        List<SentenceField> felder = satzart.getFields();

        try {
            Map<String, List<String>> excelData = ExcelImportParser.readExcelAsTable(
                    excelFile,
                    ui.isHeaderEnabled(),
                    ui.getHeaderRowIndex()
            );

            String result = formatFixedWidthBySatzart(excelData, felder, mapping);
            if (result == null || result.trim().isEmpty()) {
                showError(context.getMainFrame(), "Kein Inhalt erzeugt – bitte Mapping und Datei prüfen.");
                return;
            }

            if (ui.shouldAppend()) {
                Optional<TabAdapter> tab = context.getSelectedTab();
                String existing = tab.map(TabAdapter::getContent).orElse("");
                String trenn = ui.getTrennzeile();
                result = existing + (trenn == null ? "" : trenn + "\n") + result;
            }

            context.openFileTab(result, satzartName);

            Map<String, String> settings = getPluginSettings();
            if (Boolean.parseBoolean(settings.getOrDefault("showConfirmation", "true"))) {
                JOptionPane.showMessageDialog(context.getMainFrame(),
                        "Die Datei wurde mit dem importierten Excel-Inhalt aktualisiert.",
                        "Import abgeschlossen", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (InvalidFormatException e) {
            showError(context.getMainFrame(), "Ungültiges Excel-Format.");
        } catch (IOException e) {
            showError(context.getMainFrame(), "Fehler beim Lesen:\n" + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            showError(context.getMainFrame(), "Unerwarteter Fehler:\n" + e.getMessage());
        }
    }

    private static void showError(Component parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "Fehler", JOptionPane.ERROR_MESSAGE);
    }

    private static Map<String, String> getPluginSettings() {
        return Collections.emptyMap(); // oder über Plugin-Konfiguration
    }

    private static String formatFixedWidthBySatzart(Map<String, List<String>> table,
                                                    List<SentenceField> felder,
                                                    ExcelMapping mapping) {
        // Erzeuge jede Zeile aus dem Mapping
        StringBuilder builder = new StringBuilder();
        int rowCount = table.values().stream().findFirst().map(List::size).orElse(0);

        for (int i = 0; i < rowCount; i++) {
            StringBuilder row = new StringBuilder();

            for (ExcelMappingEntry entry : mapping.getEntries()) {
                String fieldName = entry.getFieldName();
                String value = "";

                if (entry.getExcelColumn() != null) {
                    List<String> col = table.get(entry.getExcelColumn());
                    if (col != null && i < col.size()) {
                        value = col.get(i);
                    }
                } else if (entry.getFixedValue() != null) {
                    value = entry.getFixedValue();
                } else if (entry.getExpression() != null) {
                    // TODO: Ausdruck evaluieren
                    value = entry.getExpression();
                }

                // Padding oder Zuschnitt je nach Satzart-Definition
                row.append(value);
                row.append(" "); // Trennung (anpassen!)
            }

            builder.append(row.toString().trim()).append("\n");
        }

        return builder.toString();
    }
}
