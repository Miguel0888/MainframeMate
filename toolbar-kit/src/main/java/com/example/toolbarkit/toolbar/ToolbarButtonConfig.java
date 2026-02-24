package com.example.toolbarkit.toolbar;

/**
 * Persist one toolbar button configuration.
 */
public class ToolbarButtonConfig {

    public String id;
    public String iconText;
    public String backgroundHex;
    public Integer order;

    public ToolbarButtonConfig() {
        // Keep for JSON libraries.
    }

    public ToolbarButtonConfig(String id, String iconText) {
        this.id = id;
        this.iconText = iconText;
    }
}
