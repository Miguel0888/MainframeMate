package de.zrb.bund.newApi.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

public class ToolSpec {

    private final String name;
    private final String functionDescription;
    private final List<ParameterSpec> parameters;

    public ToolSpec(String name, String functionDescription, List<ParameterSpec> parameters) {
        this.name = name;
        this.functionDescription = functionDescription;
        this.parameters = parameters;
    }

    public String getName() {
        return name;
    }

    public String getFunctionDescription() {
        return functionDescription;
    }

    public List<ParameterSpec> getParameters() {
        return parameters;
    }

    public String toPromptBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append("Tool: ").append(name).append("\n");
        sb.append("Funktion: ").append(functionDescription).append("\n");
        sb.append("Parameter:\n");
        for (ParameterSpec p : parameters) {
            sb.append("  - ").append(p.getName()).append(": ")
                    .append(p.getDescription()).append(" (").append(p.getType()).append(")\n");
        }
        return sb.toString();
    }
}
