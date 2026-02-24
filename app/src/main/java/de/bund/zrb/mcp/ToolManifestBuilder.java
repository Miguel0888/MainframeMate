package de.bund.zrb.mcp;

import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Erzeugt einen KI-kompatiblen Manifest-Text, der alle verfügbaren Tools beschreibt.
 * Wird z. B. als System-Prompt genutzt, um dem Modell mitzuteilen, welche Werkzeuge
 * es eigenständig verwenden darf.
 */
public class ToolManifestBuilder {

    /**
     * Erstellt einen Manifest-Text im gewünschten Format, inklusive aller Tools als JSON.
     *
     * @param tools Liste der verfügbaren Tool-Spezifikationen
     * @return Systemprompt-kompatibler Text
     */
    public static String buildManifest(List<ToolSpec> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("Du darfst bei Bedarf die folgenden Tools verwenden.\n")
                .append("Gib die Werkzeugverwendung im folgenden Format aus:\n\n")
                .append("{ \"tool_name\": \"<name>\", \"tool_input\": { ... }, \"tool_call_id\": \"call-<id>\" }\n\n")
                .append("Hier ist die Beschreibung der verfügbaren Tools:\n\n");

        for (ToolSpec tool : tools) {
            sb.append(tool.toJson()).append("\n\n");
        }

        return sb.toString().trim();
    }

    /**
     * Gibt den Manifest-Teil nur als JSON-Array aller Tool-Spezifikationen zurück.
     * Nützlich, falls du Tools separat übergeben willst.
     *
     * @param tools Liste der Tool-Spezifikationen
     * @return JSON-Array als String
     */
    public static String buildJsonArray(List<ToolSpec> tools) {
        return tools.stream()
                .map(ToolSpec::toJson)
                .collect(Collectors.joining(",\n", "[\n", "\n]"));
    }

    /**
     * Konvertiert Tool-Spezifikationen ins native Ollama/OpenAI Tool-Format für /api/chat.
     * <pre>
     * {
     *   "type": "function",
     *   "function": {
     *     "name": "...",
     *     "description": "...",
     *     "parameters": {
     *       "type": "object",
     *       "properties": { ... },
     *       "required": [ ... ]
     *     }
     *   }
     * }
     * </pre>
     *
     * @param tools Liste der Tool-Spezifikationen
     * @return Liste von Maps im Ollama-nativen Format, serialisierbar via Gson
     */
    public static List<Map<String, Object>> buildOllamaTools(List<ToolSpec> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (tools == null) return result;

        for (ToolSpec tool : tools) {
            Map<String, Object> toolDef = new LinkedHashMap<>();
            toolDef.put("type", "function");

            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription() != null ? tool.getDescription() : "");

            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("type", "object");

            ToolSpec.InputSchema schema = tool.getInputSchema();
            if (schema != null) {
                Map<String, Object> properties = new LinkedHashMap<>();
                if (schema.getProperties() != null) {
                    for (Map.Entry<String, ToolSpec.Property> entry : schema.getProperties().entrySet()) {
                        Map<String, String> propDef = new LinkedHashMap<>();
                        propDef.put("type", entry.getValue().getType() != null ? entry.getValue().getType() : "string");
                        if (entry.getValue().getDescription() != null && !entry.getValue().getDescription().isEmpty()) {
                            propDef.put("description", entry.getValue().getDescription());
                        }
                        properties.put(entry.getKey(), propDef);
                    }
                }
                parameters.put("properties", properties);

                if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
                    parameters.put("required", schema.getRequired());
                }
            } else {
                parameters.put("properties", new LinkedHashMap<>());
            }

            function.put("parameters", parameters);
            toolDef.put("function", function);
            result.add(toolDef);
        }

        return result;
    }
}
