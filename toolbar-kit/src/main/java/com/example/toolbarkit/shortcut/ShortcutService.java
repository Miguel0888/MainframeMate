package com.example.toolbarkit.shortcut;

import com.example.toolbarkit.command.ToolbarCommand;
import com.example.toolbarkit.command.ToolbarCommandRegistry;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Apply shortcuts to commands and install them into a root pane.
 */
public class ShortcutService {

    private final ToolbarCommandRegistry registry;
    private final ShortcutRepository repository;

    public ShortcutService(ToolbarCommandRegistry registry, ShortcutRepository repository) {
        if (registry == null) throw new IllegalArgumentException("registry must not be null");
        if (repository == null) throw new IllegalArgumentException("repository must not be null");
        this.registry = registry;
        this.repository = repository;
    }

    public void loadAndApplyToCommands() {
        Map<String, List<String>> loaded = repository.load();
        for (Map.Entry<String, List<String>> e : loaded.entrySet()) {
            final String id = e.getKey();
            final List<String> shortcuts = e.getValue();
            registry.findById(id).ifPresent(cmd -> cmd.setShortcuts(shortcuts));
        }
    }

    public void saveFromCommands() {
        Map<String, List<String>> current = new LinkedHashMap<String, List<String>>();
        for (ToolbarCommand cmd : registry.getAll()) {
            List<String> shortcuts = cmd.getShortcuts();
            if (shortcuts != null && !shortcuts.isEmpty()) {
                current.put(cmd.getId(), new ArrayList<String>(shortcuts));
            }
        }
        repository.save(current);
    }

    public void installInto(JRootPane rootPane) {
        if (rootPane == null) throw new IllegalArgumentException("rootPane must not be null");

        for (ToolbarCommand cmd : registry.getAll()) {
            List<String> shortcuts = cmd.getShortcuts();
            if (shortcuts == null) shortcuts = Collections.emptyList();

            for (String key : shortcuts) {
                KeyStroke ks = KeyStroke.getKeyStroke(key);
                if (ks == null) continue;

                String actionKey = "shortcut:" + cmd.getId() + ":" + key;
                rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, actionKey);
                rootPane.getActionMap().put(actionKey, new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        cmd.perform();
                    }
                });
            }
        }
    }

    public void clearFrom(JRootPane rootPane) {
        // Keep simple: recreate the root pane or install only once.
        // Implement removal if you need dynamic re-binding.
    }
}
