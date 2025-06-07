package de.bund.zrb.excel.mcp;

import com.google.gson.JsonObject;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ImportExcelTool implements McpTool {

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<>();
        properties.put("file", new ToolSpec.Property("string", "Pfad zur Excel-Datei"));
        properties.put("satzart", new ToolSpec.Property("string", "Satzartenschlüssel (z. B. \"100\")"));
        properties.put("hasHeader", new ToolSpec.Property("boolean", "Ob Spaltennamen in einer Kopfzeile vorhanden sind"));
        properties.put("headerRowIndex", new ToolSpec.Property("integer", "Index der Kopfzeile (beginnend bei 0)"));
        properties.put("append", new ToolSpec.Property("boolean", "Ob an eine bestehende Datei angehängt wird"));
        properties.put("trennzeile", new ToolSpec.Property("string", "Text der Trennzeile (optional)"));

        List<String> required = Arrays.asList("file", "satzart", "hasHeader", "headerRowIndex", "append");

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
    public JsonObject execute(JsonObject input) {
        // Business-Logik wie gehabt
        return new JsonObject(); // Dummy // TODO
    }
}
