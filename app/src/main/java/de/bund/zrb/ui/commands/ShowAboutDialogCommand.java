package de.bund.zrb.ui.commands;

import de.zrb.bund.api.Command;

import javax.swing.*;

public class ShowAboutDialogCommand implements Command {

    private final JFrame parent;

    public ShowAboutDialogCommand(JFrame parent) {
        this.parent = parent;
    }

    @Override
    public String getId() {
        return "help.about";
    }

    @Override
    public String getLabel() {
        return "ℹ️ Über MainframeMate";
    }

    @Override
    public void perform() {
        JOptionPane.showMessageDialog(parent,
                "MainframeMate\nVersion 1.1.0\n© 2025 GZD",
                "Über", JOptionPane.INFORMATION_MESSAGE);
    }
}
