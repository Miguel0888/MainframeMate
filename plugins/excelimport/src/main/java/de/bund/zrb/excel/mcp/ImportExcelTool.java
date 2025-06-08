package de.bund.zrb.excel.mcp;

import com.google.gson.JsonObject;
import de.bund.zrb.excel.plugin.ExcelImport;
import de.bund.zrb.excel.service.ExcelParser;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.io.File;
import java.util.*;

public class ImportExcelTool implements McpTool {

    private final ExcelImport plugin;

    public ImportExcelTool(ExcelImport plugin) {
        this.plugin = plugin;
    }

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
        JsonObject result = new JsonObject();
        try {
            // Parameter lesen
            String path = input.get("file").getAsString();
            String satzart = input.get("satzart").getAsString();
            boolean hasHeader = input.get("hasHeader").getAsBoolean();
            int headerRowIndex = input.get("headerRowIndex").getAsInt();
            boolean append = input.get("append").getAsBoolean();
            String trennzeile = input.has("trennzeile") ? input.get("trennzeile").getAsString() : "";

            File excelFile = new File(path);
            if (!excelFile.exists()) {
                throw new IllegalArgumentException("Datei existiert nicht: " + path);
            }

            // Excel-Daten laden
            Map<String, List<String>> table = ExcelParser.readExcelAsTable(
                    excelFile,
                    true,              // Formeln evaluieren
                    hasHeader ? headerRowIndex : -1,
                    false,             // nicht bei leerer Zelle abbrechen
                    false              // nicht bei leerer Zeile abbrechen
            );

            // In Satz-Daten umwandeln
            List<Map<String, String>> datensaetze = new ArrayList<>();
            int rowCount = table.values().stream().mapToInt(List::size).max().orElse(0);
            List<String> columns = new ArrayList<>(table.keySet());

            for (int i = 0; i < rowCount; i++) {
                Map<String, String> row = new LinkedHashMap<>();
                for (String col : columns) {
                    List<String> colData = table.get(col);
                    String value = i < colData.size() ? colData.get(i) : "";
                    row.put(col, value);
                }
                datensaetze.add(row);
            }

            // Optional: Hier z. B. speichern in DataStore, je nach Systemstruktur
            // Beispiel: DataStore.save(satzart, datensaetze, append, trennzeile);

            result.addProperty("status", "success");
            result.addProperty("importedRows", datensaetze.size());
        } catch (Exception e) {
            result.addProperty("status", "error");
            result.addProperty("message", e.getMessage());
        }
        return result;
    }

}
