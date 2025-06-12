package de.bund.zrb.mcp;

import com.google.gson.JsonObject;
import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.ToolSpec;

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

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(properties, Collections.singletonList("file"));

        Map<String, Object> example = new LinkedHashMap<>();
        example.put("file", "MY.USER.FILE.JCL");

        return new ToolSpec(
                "open_file",
                "Öffnet eine Datei als neuen Tab im Editor.",
                inputSchema,
                example
        );
    }

    @Override
    public JsonObject execute(JsonObject input) {
        JsonObject result = new JsonObject();

        try {
            String file = input.get("file").getAsString();

            String sentenceTyoe = "";
            context.openFileOrDirectory(file);

            result.addProperty("status", "success");
            result.addProperty("openedFile", file);
        } catch (Exception e) {
            result.addProperty("status", "error");
            result.addProperty("message", "Fehler beim Öffnen der Datei: " + e.getMessage());
        }

        return result;
    }
}
