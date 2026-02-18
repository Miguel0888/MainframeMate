package de.bund.zrb.excel.controller;

import de.bund.zrb.excel.model.ExcelImportConfig;
import de.bund.zrb.excel.model.ExcelMapping;
import de.bund.zrb.excel.plugin.ExcelImport;
import de.bund.zrb.excel.repo.TemplateRepository;
import de.bund.zrb.excel.service.Converter;
import de.bund.zrb.excel.service.ExcelParser;
import de.bund.zrb.excel.ui.ExcelImportUiDialog;
import de.bund.zrb.excel.ui.ExcelImportUiPanel;
import de.zrb.bund.api.ExpressionRegistry;
import de.zrb.bund.newApi.sentence.FieldMap;
import de.zrb.bund.newApi.sentence.SentenceDefinition;
import de.zrb.bund.newApi.ui.FileTab;
import de.zrb.bund.newApi.ui.FtpTab;
import de.zrb.bund.api.UiExpressionParser;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.List;
import java.util.function.Function;

public class ExcelImportController {

    public static void handleImport(ExcelImport plugin) {
        ExcelImportUiDialog dialog = new ExcelImportUiDialog(plugin.getContext());
        dialog.setVisible(true);
        if( !dialog.isConfirmed()) {
            return;
        }
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

        Map<String, Object> importParameters = new HashMap<>();
        importParameters.put("file", excelFile.getAbsolutePath());
        importParameters.put("template", templateName);
        importParameters.put("hasHeader", ui.isHeaderEnabled());
        importParameters.put("headerRowIndex", ui.getHeaderRowIndex());
        importParameters.put("append", ui.shouldAppend());
        importParameters.put("noDuplicates", ui.shouldSkipDuplicates());
        importParameters.put("trennzeile", ui.getTrennzeile());

        ExcelImportConfig config = new ExcelImportConfig(
                importParameters,
                null,
                s -> plugin.getTemplateRepository().getTemplateFor(s)
        );

        boolean stopOnEmptyRequiredCheck = Boolean.parseBoolean(plugin.getSettings().getOrDefault("stopOnEmptyRequired", "true"));
        boolean requireAllFieldsEmptyCheck = Boolean.parseBoolean(plugin.getSettings().getOrDefault("requireAllFieldsEmpty", "false"));

        try {
            importFromConfig(plugin, config, stopOnEmptyRequiredCheck, requireAllFieldsEmptyCheck);
        } catch (Exception e) {
            e.printStackTrace();
            showError(plugin.getMainFrame(), "Fehler beim Import:\n" + e.getMessage());
        }
    }

    public static @Nullable String importFromConfig(ExcelImport plugin,
                                                    ExcelImportConfig config,
                                                    boolean stopOnEmptyRequired,
                                                    boolean requireAllFieldsEmpty) throws Exception {

        if (!config.getFile().exists()) {
            throw new FileNotFoundException("Datei nicht gefunden: " + config.getFile());
        }

        TemplateRepository repo = plugin.getTemplateRepository();
        ExcelMapping mapping = repo.getTemplate(config.getTemplateName());
        if (mapping == null) {
            throw new IllegalArgumentException("Mapping-Vorlage nicht gefunden: " + config.getTemplateName());
        }

        String satzartName = mapping.getSentenceType();
        if (satzartName == null || satzartName.trim().isEmpty()) {
            throw new IllegalArgumentException("Keine Satzart im Mapping definiert.");
        }

        SentenceDefinition satzart = plugin.getContext().getSentenceTypeRegistry()
                .getSentenceTypeSpec()
                .getDefinitions()
                .get(satzartName);

        if (satzart == null || satzart.getFields() == null || satzart.getFields().isEmpty()) {
            throw new IllegalArgumentException("Satzart oder Felder nicht gefunden: " + satzartName);
        }

        Map<String, List<String>> excelData = ExcelParser.readExcelAsTable(
                config.getFile(),
                config.isHasHeader(),
                config.isHasHeader() ? config.getHeaderRowIndex() : -1,
                stopOnEmptyRequired,
                requireAllFieldsEmpty
        );

        FieldMap felder = satzart.getFields();
        int schemaLines = satzart.getRowCount() != null ? satzart.getRowCount() : 1;

        StringBuilder builder = new StringBuilder();
        Converter converter = Converter.getInstance();
        ExpressionRegistry registry = plugin.getContext().getExpressionRegistry();

        int rowCount = excelData.values().stream()
                .findFirst()
                .map(List::size)
                .orElse(0);

        String previousRecord = null;
        for (int i = 0; i < rowCount; i++) {
            Function<String, String> valueProvider = createValueResolver(registry, excelData, mapping, i);

            String record = converter.generateRecordLines(
                    felder,
                    schemaLines,
                    mapping,
                    valueProvider
            );

            if (config.isNoDuplicates() && Objects.equals(previousRecord, record)) {
                continue;
            }

            builder.append(record).append("\n");
            previousRecord = record;
        }

        String result = builder.toString().trim();
        if (result.isEmpty()) {
            throw new IllegalStateException("Kein Inhalt erzeugt – bitte Mapping und Datei prüfen.");
        }

        if (config.isAppend()) {
            String trenn = config.getSeparator();
            // Optional: Trennzeile oder Zeilenumbruch dazwischen
            if (!trenn.endsWith("\n") && !result.startsWith("\n")) {
                trenn += "\n";
            }
            result =  trenn + result;
        }

        if(config.getDestination() != null && !config.getDestination().trim().isEmpty()) {
            FtpTab ftpTab = plugin.getContext().openFileOrDirectory(config.getDestination(), satzartName, config.getSearchPattern(), config.getToCompare());
            if(ftpTab == null) {
                showError(plugin.getMainFrame(), "Zielpfad konnte nicht geöffnet werden: " + config.getDestination());
                return null;
            }
            if(ftpTab instanceof FileTab) { // ToDo: Besser: Fähigkeit explizit machen, z. B. durch: interface ContentWritable
                ((FileTab) ftpTab).setAppend(config.isAppend());
                ((FileTab) ftpTab).setContent(result, satzartName);
            } else {
                showError(plugin.getMainFrame(), "Zielpfad ist kein gültiger Tab: " + config.getDestination());
                return null;
            }
        }
        else
        {
            plugin.getContext().createFile(result, satzartName); // ToDo: try to open path first
        }

        if (Boolean.parseBoolean(plugin.getSettings().getOrDefault("showConfirmation", "true"))) {
            JOptionPane.showMessageDialog(plugin.getMainFrame(),
                    "Die Datei wurde mit dem importierten Excel-Inhalt aktualisiert.",
                    "Import abgeschlossen", JOptionPane.INFORMATION_MESSAGE);
        }
        return result;
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
                    try {
                        UiExpressionParser.Expression expr = UiExpressionParser.parse(e.getExpression(), registry.getKeys());

                        switch (expr.getKind()) {
                            case LITERAL:
                                return expr.getLiteralOrColumn();

                            case COLUMN:
                                List<String> column = excelData.get(expr.getLiteralOrColumn());
                                return (column != null && rowIndex < column.size()) ? column.get(rowIndex) : "";

                            case FUNCTION:
                                List<String> resolvedArgs = new ArrayList<>();
                                for (String arg : expr.getArguments()) {
                                    if (arg.startsWith("\"") && arg.endsWith("\"")) {
                                        resolvedArgs.add(arg.substring(1, arg.length() - 1)); // Literal
                                    } else {
                                        List<String> col = excelData.get(arg);
                                        String val = (col != null && rowIndex < col.size()) ? col.get(rowIndex) : "";
                                        resolvedArgs.add(val);
                                    }
                                }
                                return registry.evaluate(expr.getFunctionName(), resolvedArgs);

                            default:
                                return "";
                        }

                    } catch (Exception ex) {
                        throw new RuntimeException("Fehler beim Verarbeiten des Ausdrucks: " + e.getExpression(), ex);
                    }
                })
                .orElse("");
    }
}
