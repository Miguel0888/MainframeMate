package de.bund.zrb.ui.commands;

import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;

public class ShowAboutDialogMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;

    public ShowAboutDialogMenuCommand(JFrame parent) {
        this.parent = parent;
    }

    @Override
    public String getId() {
        return "help.about";
    }

    @Override
    public String getLabel() {
        return "ℹ Über MainframeMate";
    }

    @Override
    public void perform() {
        JOptionPane.showMessageDialog(parent,
                "MainframeMate\nVersion 1.3.0\n© 2025 GZD",
                "Über", JOptionPane.INFORMATION_MESSAGE);
    }
}
