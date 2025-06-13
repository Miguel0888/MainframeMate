package de.bund.zrb.mcp;

import com.google.gson.JsonObject;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.ToolSpec;
import de.zrb.bund.newApi.ui.FtpTab;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class OpenFileTool implements McpTool {

    private final MainframeContext context;

    public OpenFileTool(MainframeContext context) {
        this.context = context;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<>();
        properties.put("file", new ToolSpec.Property("string", "Pfad zur Datei auf dem Host"));
        properties.put("satzart", new ToolSpec.Property("string", "Optionaler Satzartenschlüssel"));
        properties.put("suchmuster", new ToolSpec.Property("string", "Optionaler Suchausdruck"));
        properties.put("toCompare", new ToolSpec.Property("boolean", "Ob im Vergleichsmodus geöffnet werden soll"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(properties, Collections.singletonList("file"));

        Map<String, Object> example = new LinkedHashMap<>();
        example.put("file", "MY.USER.FILE.JCL");
        example.put("satzart", "100");
        example.put("suchmuster", "BEGIN");
        example.put("toCompare", true);

        return new ToolSpec(
                "open_file",
                "Öffnet eine Datei als neuen Tab im Editor.",
                inputSchema,
                example
        );
    }


    @Override
    public JsonObject execute(JsonObject input, String resultVar) {
        JsonObject response = new JsonObject();

        try {
            String file = input.get("file").getAsString();
            String sentenceType = input.has("satzart") && !input.get("satzart").isJsonNull()
                    ? input.get("satzart").getAsString()
                    : null;

            String searchPattern = input.has("suchmuster") && !input.get("suchmuster").isJsonNull()
                    ? input.get("suchmuster").getAsString()
                    : null;

            Boolean toCompare = input.has("toCompare") && !input.get("toCompare").isJsonNull()
                    ? input.get("toCompare").getAsBoolean()
                    : null;

            FtpTab ftpTab = context.openFileOrDirectory(file, sentenceType, searchPattern, toCompare);
            String result = ftpTab.getContent();

            if (resultVar != null && !resultVar.trim().isEmpty()) {
                context.getVariableRegistry().set(resultVar, result); // 🔧 setze Variable
            }

            response.addProperty("status", "success");
            response.addProperty("openedFile", file);
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Fehler beim Öffnen der Datei: " + e.getMessage());
        }

        return response;
    }

}
