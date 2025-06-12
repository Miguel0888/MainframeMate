package de.bund.zrb.ui.commands;

import de.bund.zrb.helper.ShortcutManager;
import de.zrb.bund.api.MenuCommand;
import de.zrb.bund.api.SimpleMenuCommand;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

public class ShowShortcutConfigMenuCommand extends SimpleMenuCommand {

    private final JFrame parent;

    public ShowShortcutConfigMenuCommand(JFrame parent) {
        this.parent = parent;
    }

    @Override
    public String getId() {
        return "settings.shortcuts";
    }

    @Override
    public String getLabel() {
        return "Shortcuts...";
    }

    @Override
    public void perform() {
        List<MenuCommand> all = new ArrayList<>(CommandRegistryImpl.getAll());
        Map<MenuCommand, KeyStrokeField> shortcutFields = new LinkedHashMap<>();

        JPanel commandPanel = new JPanel(new GridLayout(0, 1));

        for (MenuCommand cmd : all) {
            JPanel line = new JPanel(new BorderLayout(4, 0));
            KeyStroke initial = ShortcutManager.getKeyStrokeFor(cmd);
            KeyStrokeField field = new KeyStrokeField(initial);

            JLabel label = new JLabel(cmd.getLabel());
            line.add(label, BorderLayout.CENTER);
            line.add(field, BorderLayout.EAST);

            shortcutFields.put(cmd, field);
            commandPanel.add(line);
        }

        JPanel fullPanel = new JPanel(new BorderLayout(8, 8));
        fullPanel.add(new JScrollPane(commandPanel), BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(parent, fullPanel,
                "Tastenkombinationen bearbeiten", JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            for (Map.Entry<MenuCommand, KeyStrokeField> entry : shortcutFields.entrySet()) {
                KeyStroke ks = entry.getValue().getKeyStroke();
                ShortcutManager.assignShortcut(entry.getKey().getId(), ks);
            }
            ShortcutManager.saveShortcuts();
            ShortcutManager.registerGlobalShortcuts(parent.getRootPane()); // 🔥 Damit es SOFORT wirkt (ohne Neustart)
        }
    }

}
