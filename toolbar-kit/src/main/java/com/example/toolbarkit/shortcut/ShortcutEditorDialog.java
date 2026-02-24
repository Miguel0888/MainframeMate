package com.example.toolbarkit.shortcut;

import com.example.toolbarkit.command.ToolbarCommand;
import com.example.toolbarkit.command.ToolbarCommandRegistry;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Edit command shortcuts in a modal dialog.
 */
public class ShortcutEditorDialog {

    private final Window parent;
    private final ToolbarCommandRegistry registry;
    private final ShortcutService shortcutService;

    public ShortcutEditorDialog(Window parent, ToolbarCommandRegistry registry, ShortcutService shortcutService) {
        if (registry == null) throw new IllegalArgumentException("registry must not be null");
        if (shortcutService == null) throw new IllegalArgumentException("shortcutService must not be null");
        this.parent = parent;
        this.registry = registry;
        this.shortcutService = shortcutService;
    }

    public void showAndPersist() {
        List<ToolbarCommand> all = new ArrayList<ToolbarCommand>(registry.getAll());
        Map<ToolbarCommand, KeyStrokeField> fields = new LinkedHashMap<ToolbarCommand, KeyStrokeField>();

        JPanel commandPanel = new JPanel(new GridLayout(0, 1));

        for (ToolbarCommand cmd : all) {
            JPanel line = new JPanel(new BorderLayout(4, 0));

            JLabel label = new JLabel(cmd.getLabel());
            KeyStroke initial = firstKeyStroke(cmd);
            KeyStrokeField field = new KeyStrokeField(initial);
            fields.put(cmd, field);

            field.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            field.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusGained(java.awt.event.FocusEvent e) {
                    field.setBorder(BorderFactory.createLineBorder(Color.ORANGE, 2));
                }

                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    field.setBorder(BorderFactory.createLineBorder(Color.GRAY));
                }
            });

            JButton clearButton = new JButton("❌");
            clearButton.setMargin(new Insets(0, 4, 0, 4));
            clearButton.setPreferredSize(new Dimension(28, 24));
            clearButton.setFocusable(false);
            clearButton.setToolTipText("Shortcut entfernen");
            clearButton.addActionListener(e -> field.clear());

            JPanel fieldPanel = new JPanel(new BorderLayout(2, 0));
            fieldPanel.add(field, BorderLayout.CENTER);
            fieldPanel.add(clearButton, BorderLayout.EAST);

            line.add(label, BorderLayout.CENTER);
            line.add(fieldPanel, BorderLayout.EAST);
            commandPanel.add(line);
        }

        JPanel fullPanel = new JPanel(new BorderLayout(8, 8));
        fullPanel.add(new JScrollPane(commandPanel), BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(parent, fullPanel,
                "Tastenkombinationen bearbeiten", JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            for (Map.Entry<ToolbarCommand, KeyStrokeField> entry : fields.entrySet()) {
                ToolbarCommand cmd = entry.getKey();
                KeyStrokeField field = entry.getValue();
                KeyStroke ks = field.getKeyStroke();

                if (ks == null || field.getText().trim().isEmpty()) {
                    cmd.setShortcuts(java.util.Collections.emptyList());
                } else {
                    cmd.setShortcuts(java.util.Collections.singletonList(ks.toString()));
                }
            }

            shortcutService.saveFromCommands();

            if (parent instanceof RootPaneContainer) {
                shortcutService.installInto(((RootPaneContainer) parent).getRootPane());
            }
        }
    }

    private KeyStroke firstKeyStroke(ToolbarCommand cmd) {
        if (cmd.getShortcuts() == null || cmd.getShortcuts().isEmpty()) {
            return null;
        }
        return KeyStroke.getKeyStroke(cmd.getShortcuts().get(0));
    }
}
