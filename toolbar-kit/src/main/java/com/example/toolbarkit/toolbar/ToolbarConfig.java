package com.example.toolbarkit.toolbar;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Persist full toolbar configuration.
 */
public class ToolbarConfig {

    public int buttonSizePx;
    public float fontSizeRatio;
    public List<ToolbarButtonConfig> buttons;
    public LinkedHashSet<String> rightSideIds;
    public LinkedHashMap<String, String> groupColors;
    public LinkedHashSet<String> hiddenCommandIds;
}
