package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.branding.IconThemeInstaller;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;
import java.awt.*;

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
        // Use the largest available app logo instead of the default info icon
        ImageIcon logoIcon = null;
        Image appIcon = IconThemeInstaller.getAppIcon(1024);
        if (appIcon == null) {
            appIcon = IconThemeInstaller.getAppIcon(256);
        }
        if (appIcon != null) {
            // Scale to a nice display size for the dialog (128px)
            Image scaled = appIcon.getScaledInstance(128, 128, Image.SCALE_SMOOTH);
            logoIcon = new ImageIcon(scaled);
        }

        JOptionPane.showMessageDialog(parent,
                "MainframeMate\nVersion 5.3.0\n© 2026 GZD",
                "Über MainframeMate",
                logoIcon != null ? JOptionPane.PLAIN_MESSAGE : JOptionPane.INFORMATION_MESSAGE,
                logoIcon);
    }
}
