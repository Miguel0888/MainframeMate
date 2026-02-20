package de.bund.zrb.tools;

/** Derive fallback access type for tools without explicit policy. */
public final class ToolAccessTypeDefaults {

    private ToolAccessTypeDefaults() {
    }

    public static ToolAccessType resolveDefault(String toolName) {
        if (toolName == null) {
            return ToolAccessType.READ;
        }
        String n = toolName.toLowerCase();
        if (n.contains("write") || n.contains("set") || n.contains("save") || n.contains("delete")
                || n.contains("update") || n.contains("create") || n.contains("import") || n.contains("open")
                || n.contains("websearch")) {
            return ToolAccessType.WRITE;
        }
        return ToolAccessType.READ;
    }
}
