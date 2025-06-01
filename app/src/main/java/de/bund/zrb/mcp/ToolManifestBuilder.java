package de.bund.zrb.mcp;

import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.List;
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
}
