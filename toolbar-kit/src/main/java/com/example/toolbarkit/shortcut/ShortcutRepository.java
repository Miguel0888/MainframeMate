package com.example.toolbarkit.shortcut;

import java.util.List;
import java.util.Map;

/**
 * Load and save shortcut mappings (command id -> keystroke strings).
 */
public interface ShortcutRepository {

    Map<String, List<String>> load();

    void save(Map<String, List<String>> shortcuts);
}
