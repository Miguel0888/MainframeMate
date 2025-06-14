package de.bund.zrb.mcp;

import com.google.gson.JsonObject;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;
import de.zrb.bund.newApi.sentence.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class FilterColumnTool implements McpTool {

    private final MainframeContext context;

    public FilterColumnTool(MainframeContext context) {
        this.context = context;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> props = new LinkedHashMap<>();
        props.put("satzart", new ToolSpec.Property("string", "Name der Satzart"));
        props.put("feld", new ToolSpec.Property("string", "Feldname, dessen Werte extrahiert werden sollen"));
        props.put("content", new ToolSpec.Property("string", "Inhalt, der nach der angegebenen Spalte gefiltert werden soll"));

        ToolSpec.InputSchema schema = new ToolSpec.InputSchema(props, Arrays.asList("satzart", "feld"));

        Map<String, Object> example = new LinkedHashMap<>();
        example.put("satzart", "100");
        example.put("feld", "KUNDENNUMMER");

        return new ToolSpec(
                "filter_column",
                "Extrahiert alle Vorkommen eines Felds (mehrzeilig) aus einer Datei anhand der Satzart.",
                schema,
                example
        );
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        String result = null;
        JsonObject response = new JsonObject();

        try {
            String satzartName = input.get("satzart").getAsString();
            String feldName = input.get("feld").getAsString();
            String content = input.get("content").getAsString();

            SentenceDefinition definition = context.getSentenceTypeRegistry()
                    .getSentenceTypeSpec()
                    .getDefinitions()
                    .get(satzartName);

            if (definition == null) {
                throw new IllegalArgumentException("Satzart nicht gefunden: " + satzartName);
            }

            FieldMap fieldMap = definition.getFields();
            int maxRow = Optional.ofNullable(definition.getRowCount()).orElse(0);

            List<Map.Entry<FieldCoordinate, SentenceField>> relevantFields = new ArrayList<>();
            for (Map.Entry<FieldCoordinate, SentenceField> entry : fieldMap.entrySet()) {
                if (feldName.equalsIgnoreCase(entry.getValue().getName())) {
                    relevantFields.add(entry);
                }
            }

            if (relevantFields.isEmpty()) {
                throw new IllegalArgumentException("Feld \"" + feldName + "\" nicht in Satzart \"" + satzartName + "\" gefunden.");
            }

            // Dateiinhalt prüfen
            if (content == null || content.trim().isEmpty()) {
                throw new IllegalStateException("Keine Daten in der Umgebungsvariablen 'content' gefunden.");
            }

            result = getColumnValues(content, maxRow, relevantFields);

            response.addProperty("status", "success");
            response.addProperty("result", result);

        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Fehler beim Extrahieren des Felds: " + e.getMessage());
        }

        return new McpToolResponse(response, resultVar, result);
    }

    @NotNull
    private static String getColumnValues(String content, int recordHeight, List<Map.Entry<FieldCoordinate, SentenceField>> relevantFields) {
        String[] lines = content.split("\\r?\\n");

        List<String> values = new ArrayList<>();

        for (int i = 0; i + recordHeight <= lines.length; i += recordHeight) {
            boolean found = false;

            for (Map.Entry<FieldCoordinate, SentenceField> entry : relevantFields) {
                if (found) break; // Nur den ersten Treffer je Record verwenden

                FieldCoordinate coord = entry.getKey();
                SentenceField field = entry.getValue();

                int rowIndex = i + coord.getRow();
                if (rowIndex >= lines.length) continue;

                String line = lines[rowIndex];
                int start = coord.getPosition();
                int end = Math.min(start + field.getLength(), line.length());

                if (start < line.length()) {
                    String value = line.substring(start, end).trim();
                    values.add(value);
                    found = true;
                }
            }
        }

        return String.join(";", values);
    }
}
