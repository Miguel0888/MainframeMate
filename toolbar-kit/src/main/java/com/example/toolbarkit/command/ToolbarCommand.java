package com.example.toolbarkit.command;

import java.util.Collections;
import java.util.List;

/**
 * Represent a user-invokable command that can be exposed in menus, toolbars and shortcuts.
 */
public interface ToolbarCommand {

    String getId();

    String getLabel();

    void perform();

    default List<String> getShortcuts() {
        return Collections.emptyList();
    }

    default void setShortcuts(List<String> shortcuts) {
        // Override to support shortcut editing.
    }
}
