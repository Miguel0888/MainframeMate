package de.bund.zrb.mcp;

import com.google.gson.JsonObject;
import de.bund.zrb.ui.VirtualResource;
import de.bund.zrb.ui.VirtualResourceResolver;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
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
        properties.put("satzart", new ToolSpec.Property("string", "Optionaler Satzartenschlüssel"));
        properties.put("search", new ToolSpec.Property("string", "Optionaler Suchausdruck"));
        properties.put("toCompare", new ToolSpec.Property("boolean", "Ob im Vergleichsmodus geöffnet werden soll"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(properties, Collections.singletonList("file"));

        Map<String, Object> example = new LinkedHashMap<>();
        example.put("file", "MY.USER.FILE.JCL");
        example.put("satzart", "100");
        example.put("search", "BEGIN");
        example.put("toCompare", true);

        return new ToolSpec(
                "open_file",
                "Öffnet eine Datei als neuen Tab im Editor.",
                inputSchema,
                example
        );
    }


    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        JsonObject response = new JsonObject();

        try {
            if (input == null || !input.has("file") || input.get("file").isJsonNull()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Pflichtfeld fehlt: file");
                return new McpToolResponse(response, resultVar, null);
            }

            String file = input.get("file").getAsString();

            // Pipeline endet aktuell am Resolver (kein Tab wird geoeffnet)
            VirtualResourceResolver resolver = new VirtualResourceResolver();
            VirtualResource resource = resolver.resolve(file);

            System.out.println("[TOOL open_file] resolved: " + resource.getResolvedPath() + " kind=" + resource.getKind());

            response.addProperty("status", "success");
            response.addProperty("openedFile", file);
            response.addProperty("resolvedPath", resource.getResolvedPath());
            response.addProperty("kind", resource.getKind().name());
            response.addProperty("local", resource.isLocal());

            return new McpToolResponse(response, resultVar, null);
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            return new McpToolResponse(response, resultVar, null);
        }
    }

}
