package de.bund.zrb.ui.dto;

public class ToolbarButtonConfig {
    public String id;
    public String icon;

    public ToolbarButtonConfig() {
        // für Gson
    }

    public ToolbarButtonConfig(String id, String icon) {
        this.id = id;
        this.icon = icon;
    }
}
