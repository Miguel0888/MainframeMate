package de.bund.zrb.excel.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.bund.zrb.excel.controller.ExcelImportController;
import de.bund.zrb.excel.model.ExcelImportConfig;
import de.bund.zrb.excel.model.ExcelMapping;
import de.bund.zrb.excel.plugin.ExcelImport;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.*;
import java.util.function.Function;

public class ImportExcelTool implements McpTool {

    private final ExcelImport plugin;

    public ImportExcelTool(ExcelImport plugin) {
        this.plugin = plugin;
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
        properties.put("trennzeile", new ToolSpec.Property("string", "Text der Trennzeile (optional)"));

        List<String> required = Arrays.asList("file", "satzart");  // defaults: "hasHeader" = true, "headerRowIndex" = 0, "append" = false

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(properties, required);

        Map<String, Object> example = new LinkedHashMap<>();
        example.put("file", "C:/daten/bestand_mai.xlsx");
        example.put("satzart", "100");
        example.put("hasHeader", true);
        example.put("headerRowIndex", 0);
        example.put("append", false);
        example.put("trennzeile", "");

        return new ToolSpec("import_excel", "Importiert eine Excel-Datei als Satzart in das System.", inputSchema, example);
    }

    @Override
    public JsonObject execute(JsonObject input, String resultVar) {
        JsonObject response = new JsonObject();
        try {
            ExcelImportConfig config = new ExcelImportConfig(new Gson().fromJson(input, Map.class), getSpec().getInputSchema(), getStringExcelMappingFunction());

            boolean stopOnEmptyRequiredCheck = Boolean.parseBoolean(plugin.getSettings().getOrDefault("stopOnEmptyRequired", "true"));
            boolean requireAllFieldsEmptyCheck = Boolean.parseBoolean(plugin.getSettings().getOrDefault("requireAllFieldsEmpty", "false"));

            String result = ExcelImportController.importFromConfig(plugin, config, requireAllFieldsEmptyCheck, stopOnEmptyRequiredCheck);
            // ToDo: Set env from result

            if (resultVar != null && !resultVar.trim().isEmpty()) {
                plugin.getContext().getVariableRegistry().set(resultVar, result); // 🔧 setze Variable
            }

            response.addProperty("status", "success");
            response.addProperty("content", ""); // ToDo: May be implemted, but nor required
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", e.getMessage());
        }
        return response;
    }

    private Function<String, ExcelMapping> getStringExcelMappingFunction() {
        return (s) -> plugin.getTemplateRepository().getTemplateFor(s);
    }

}
