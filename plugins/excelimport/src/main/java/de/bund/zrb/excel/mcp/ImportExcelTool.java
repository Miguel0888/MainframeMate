package de.bund.zrb.excel.mcp;

import com.google.gson.JsonObject;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.ParameterSpec;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.Arrays;

public class ImportExcelTool implements McpTool {

    @Override
    public ToolSpec getSpec() {
        return new ToolSpec(
                "import_excel",
                "Importiert eine Excel-Datei als Satzart in das System.",
                Arrays.asList(
                        new ParameterSpec("file", "Pfad zur Excel-Datei", ParameterSpec.ParamType.STRING, false),
                        new ParameterSpec("satzart", "Satzartenschlüssel (z. B. '100')", ParameterSpec.ParamType.STRING, false),
                        new ParameterSpec("hasHeader", "Gibt an, ob eine Kopfzeile vorhanden ist", ParameterSpec.ParamType.BOOLEAN, false),
                        new ParameterSpec("headerRowIndex", "Nullbasierter Index der Kopfzeile", ParameterSpec.ParamType.INTEGER, false),
                        new ParameterSpec("append", "Gibt an, ob angehängt werden soll", ParameterSpec.ParamType.BOOLEAN, false),
                        new ParameterSpec("trennzeile", "Inhalt der Trennzeile", ParameterSpec.ParamType.STRING, true)
                )
        );
    }

    @Override
    public JsonObject execute(JsonObject input) {
        // Business-Logik wie gehabt
        return new JsonObject(); // Dummy
    }
}
