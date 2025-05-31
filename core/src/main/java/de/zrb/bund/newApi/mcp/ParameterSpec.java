package de.zrb.bund.newApi.mcp;

public class ParameterSpec {

    public enum ParamType {
        STRING, INTEGER, BOOLEAN, OBJECT
    }

    private final String name;
    private final String description;
    private final ParamType type;
    private final boolean optional;

    public ParameterSpec(String name, String description, ParamType type, boolean optional) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.optional = optional;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description + (optional ? " (optional)" : "");
    }

    public ParamType getType() {
        return type;
    }

    public boolean isOptional() {
        return optional;
    }
}
