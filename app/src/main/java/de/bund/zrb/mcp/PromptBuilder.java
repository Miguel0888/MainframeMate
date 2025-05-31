package de.bund.zrb.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.ParameterSpec;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.List;

/**
 * Builder for prompts that describe available tools and their parameters.
 * This class generates a formatted string that can be used to inform AI bots
 * about the tools they can use, including their names, descriptions, and parameters.
 */
public class PromptBuilder {

    public static String buildPromptFor(List<McpTool> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("Dir stehen folgende Werkzeuge zur Verfügung:\n\n");
        for (McpTool tool : tools) {
            ToolSpec spec = tool.getSpec();
            sb.append("Tool: ").append(spec.getName()).append("\n");
            sb.append("Funktion: ").append(spec.getFunctionDescription()).append("\n");
            sb.append("Parameter:\n");
            for (ParameterSpec p : spec.getParameters()) {
                sb.append("  - ").append(p.getName()).append(": ")
                        .append(p.getDescription()).append(" (")
                        .append(p.getType()).append(")\n");
            }
            sb.append("\n");
        }
        sb.append("Wenn du ein Werkzeug verwenden möchtest, gib ein JSON im folgenden Format aus:\n");
        sb.append("{ \"tool_name\": \"<name>\", \"tool_input\": { ... }, \"tool_call_id\": \"<id>\" }");
        return sb.toString();
    }

    /**
     * Converts a McpTool to a JSON representation of its specification for debugging or logging purposes.
     *
     * @param tool The McpTool to convert.
     * @return A JSON string representing the tool's specification.
     */
    public String toJson(McpTool tool) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(tool.getSpec());
        System.out.println(json);
    }
}
