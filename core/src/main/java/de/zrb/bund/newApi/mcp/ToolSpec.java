package de.zrb.bund.newApi.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolSpec {

    private final String name;
    private final String description;
    private final InputSchema input_schema;
    private final Map<String, Object> example_input; // optional, nullable

    public ToolSpec(String name, String description, InputSchema inputSchema, Map<String, Object> exampleInput) {
        this.name = name;
        this.description = description;
        this.input_schema = inputSchema;
        this.example_input = exampleInput; // null erlaubt
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public InputSchema getInputSchema() {
        return input_schema;
    }

    public Map<String, Object> getExampleInput() {
        return example_input;
    }

    /**
     * Serialisiert dieses ToolSpec-Objekt als kompaktes JSON – z. B. für die Weitergabe an das Modell.
     */
    public String toJson() {
        Gson gson = new GsonBuilder()
                .serializeNulls() // explizit deaktivierbar, falls trennzeile = null gebraucht wird
                .setPrettyPrinting()
                .create();
        return gson.toJson(this);
    }

    public static class InputSchema {
        private final String type = "object";
        private final Map<String, Property> properties;
        private final List<String> required;

        public InputSchema(Map<String, Property> properties, List<String> required) {
            this.properties = properties;
            this.required = required;
        }

        public String getType() {
            return type;
        }

        public Map<String, Property> getProperties() {
            return properties;
        }

        public List<String> getRequired() {
            return required;
        }
    }

    public static class Property {
        private final String type;
        private final String description;

        public Property(String type, String description) {
            this.type = type;
            this.description = description;
        }

        public String getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }
    }
}
