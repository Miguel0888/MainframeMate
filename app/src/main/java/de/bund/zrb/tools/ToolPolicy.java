package de.bund.zrb.tools;

/** Persisted user policy for a tool. */
public class ToolPolicy {

    private String toolName;
    private boolean enabled;
    private boolean askBeforeUse;
    private ToolAccessType accessType;

    public ToolPolicy() {
        // for JSON
    }

    public ToolPolicy(String toolName, boolean enabled, boolean askBeforeUse, ToolAccessType accessType) {
        this.toolName = toolName;
        this.enabled = enabled;
        this.askBeforeUse = askBeforeUse;
        this.accessType = accessType;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAskBeforeUse() {
        return askBeforeUse;
    }

    public void setAskBeforeUse(boolean askBeforeUse) {
        this.askBeforeUse = askBeforeUse;
    }

    public ToolAccessType getAccessType() {
        return accessType;
    }

    public void setAccessType(ToolAccessType accessType) {
        this.accessType = accessType;
    }
}
