package de.bund.zrb.excel.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.bund.zrb.excel.controller.ExcelImportController;
import de.bund.zrb.excel.model.ExcelImportConfig;
import de.bund.zrb.excel.model.ExcelMapping;
import de.bund.zrb.excel.plugin.ExcelImport;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolConfig;
import de.zrb.bund.newApi.mcp.ToolConfigReader;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.*;
import java.util.function.Function;

public class ImportExcelTool implements McpTool {

    private final ExcelImport plugin;

    public ImportExcelTool(ExcelImport plugin) {
        this.plugin = plugin;
    }

    @Override
    public ToolConfig getDefaultConfig() {
        return new ExcelImportToolConfig();
    }

    @Override
    public Class<? extends ToolConfig> getConfigClass() {
        return ExcelImportToolConfig.class;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<>();
        properties.put("file", new ToolSpec.Property("string", "Pfad zur Excel-Datei"));
        properties.put("destination", new ToolSpec.Property("string", "Ausgaberecord auf dem Großrechner in einer Form wie H.X.YY")); // empty tab if absent / null
        properties.put("satzart", new ToolSpec.Property("string", "Satzartenschlüssel (z. B. \"100\")"));
        properties.put("hasHeader", new ToolSpec.Property("boolean", "Ob Spaltennamen in einer Kopfzeile vorhanden sind"));
        properties.put("headerRowIndex", new ToolSpec.Property("integer", "Index der Kopfzeile (beginnend bei 0)"));
        properties.put("append", new ToolSpec.Property("boolean", "Ob an eine bestehende Datei angehängt wird"));
        properties.put("separator", new ToolSpec.Property("string", "Text der Trennzeile (optional)"));
        properties.put("search", new ToolSpec.Property("string", "Suchausdruck innerhalb des Ziels hervorheben"));
        properties.put("toCompare", new ToolSpec.Property("boolean", "Vergleich mit dem alten Inhalt öffnen"));

        List<String> required = Arrays.asList("file", "satzart");  // defaults: "hasHeader" = true, "headerRowIndex" = 0, "append" = false

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(properties, required);

        Map<String, Object> example = new LinkedHashMap<>();
        example.put("file", "C:/daten/bestand_mai.xlsx");
        example.put("satzart", "100");
        example.put("hasHeader", true);
        example.put("headerRowIndex", 0);
        example.put("append", false);
        example.put("separator", "");
        example.put("search", ".*ABC.*");
        example.put("toCompare", true);

        return new ToolSpec("import_excel", "Importiert eine Excel-Datei als Satzart in das System.", inputSchema, example);
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        JsonObject response = new JsonObject();
        String result = null;
        try {
            ExcelImportConfig config = new ExcelImportConfig(new Gson().fromJson(input, Map.class), getSpec().getInputSchema(), getStringExcelMappingFunction());

            // Load tool config from ToolConfig system (Settings >> Tool Config)
            ExcelImportToolConfig toolConfig = getResolvedConfig();
            boolean stopOnEmptyRequiredCheck = toolConfig.isStopOnEmptyRequired();
            boolean requireAllFieldsEmptyCheck = toolConfig.isRequireAllFieldsEmpty();

            result = ExcelImportController.importFromConfig(plugin, config, stopOnEmptyRequiredCheck, requireAllFieldsEmptyCheck);

            response.addProperty("status", "success");
            response.addProperty("content", ""); // ToDo: May be implemented, but not required
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", e.getMessage());
        }

        return new McpToolResponse(response, resultVar, result);
    }

    /**
     * Loads the resolved ExcelImportToolConfig.
     * Prefers the persisted tool-configs.json (editable via Settings >> Tool Config),
     * falls back to legacy plugin settings.
     */
    private ExcelImportToolConfig getResolvedConfig() {
        // 1) Try tool-configs.json (written by Settings >> Tool Config)
        ExcelImportToolConfig fromFile = ToolConfigReader.getConfig("import_excel", ExcelImportToolConfig.class);
        if (fromFile != null) {
            return fromFile;
        }

        // 2) Fallback: legacy plugin settings
        Map<String, String> settings = plugin.getSettings();
        if (settings != null && !settings.isEmpty()) {
            ExcelImportToolConfig config = new ExcelImportToolConfig();
            config.setLastJsonPath(settings.getOrDefault("lastJsonPath", ""));
            config.setLastExcelPath(settings.getOrDefault("lastExcelPath", ""));
            config.setSeparator(settings.getOrDefault("separator", ""));
            config.setShowConfirmation(Boolean.parseBoolean(settings.getOrDefault("showConfirmation", "true")));
            config.setAutoOpen(Boolean.parseBoolean(settings.getOrDefault("autoOpen", "true")));
            config.setStopOnEmptyRequired(Boolean.parseBoolean(settings.getOrDefault("stopOnEmptyRequired", "true")));
            config.setRequireAllFieldsEmpty(Boolean.parseBoolean(settings.getOrDefault("requireAllFieldsEmpty", "false")));
            return config;
        }

        return new ExcelImportToolConfig();
    }

    private Function<String, ExcelMapping> getStringExcelMappingFunction() {
        return (s) -> plugin.getTemplateRepository().getTemplateFor(s);
    }

}
