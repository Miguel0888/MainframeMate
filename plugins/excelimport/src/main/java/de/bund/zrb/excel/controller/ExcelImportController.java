package de.bund.zrb.excel.controller;

import de.bund.zrb.excel.model.ExcelImportConfig;
import de.bund.zrb.excel.plugin.ExcelImport;
import de.bund.zrb.excel.service.Converter;
import de.bund.zrb.excel.service.ExcelParser;
import de.bund.zrb.excel.ui.ExcelImportUiDialog;
import de.bund.zrb.excel.ui.ExcelImportUiPanel;
import de.bund.zrb.excel.model.ExcelMapping;
import de.bund.zrb.excel.repo.TemplateRepository;
import de.zrb.bund.api.ExpressionRegistry;
import de.zrb.bund.api.TabAdapter;
import de.zrb.bund.newApi.sentence.FieldCoordinate;
import de.zrb.bund.newApi.sentence.FieldMap;
import de.zrb.bund.newApi.sentence.SentenceDefinition;
import de.zrb.bund.newApi.sentence.SentenceField;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.function.Function;

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

        FieldMap felder = satzart.getFields();
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

            ExpressionRegistry registry = plugin.getContext().getExpressionRegistry();
            for (int i = 0; i < rowCount; i++) {
                Function<String, String> valueProvider = createValueResolver(
                        registry, excelData, mapping, i
                );

                String record = converter.generateRecordLines(
                        felder,
                        schemaLines,
                        mapping,
                        valueProvider
                );

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

    private static Function<String, String> createValueResolver(
            ExpressionRegistry registry,
            Map<String, List<String>> excelData,
            ExcelMapping mapping,
            int rowIndex) {

        return fieldName -> mapping.getEntries().stream()
                .filter(e -> fieldName.equals(e.getFieldName()))
                .findFirst()
                .map(e -> {
                    if (e.isFixed()) {
                        return e.getFixedValue();
                    } else if (e.isDynamic()) {
                        try {
                            return registry.evaluate(e.getExpression());
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    } else if (e.isFromColumn()) {
                        List<String> column = excelData.get(e.getExcelColumn());
                        return (column != null && rowIndex < column.size()) ? column.get(rowIndex) : "";
                    }
                    return "";
                })
                .orElse("");
    }

    public static String importFromConfig(ExcelImport plugin, ExcelImportConfig config) throws Exception {
        if (!config.getFile().exists()) {
            throw new FileNotFoundException("Datei nicht gefunden: " + config.getFile());
        }

        TemplateRepository repo = plugin.getTemplateRepository();
        ExcelMapping mapping = repo.getTemplate(config.getTemplateName());
        if (mapping == null) {
            throw new IllegalArgumentException("Satzarten-Mapping nicht gefunden: " + config.getTemplateName());
        }

        SentenceDefinition satzart = plugin.getContext().getSentenceTypeRegistry()
                .getSentenceTypeSpec()
                .getDefinitions()
                .get(mapping.getSentenceType());

        if (satzart == null) {
            throw new IllegalArgumentException("Satzart nicht bekannt: " + mapping.getSentenceType());
        }

        Map<String, List<String>> excelData = ExcelParser.readExcelAsTable(
                config.getFile(),
                true,
                config.isHasHeader() ? config.getHeaderRowIndex() : -1,
                true,   // stopOnEmpty
                false   // requireAllEmpty
        );

        FieldMap felder = satzart.getFields();
        int schemaLines = satzart.getRowCount() != null ? satzart.getRowCount() : 1;

        Converter converter = Converter.getInstance();
        ExpressionRegistry registry = plugin.getContext().getExpressionRegistry();
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < excelData.values().stream().mapToInt(List::size).max().orElse(0); i++) {
            Function<String, String> resolver = createValueResolver(registry, excelData, mapping, i);
            result.append(converter.generateRecordLines(felder, schemaLines, mapping, resolver)).append("\n");
        }

        return result.toString().trim();
    }

}
