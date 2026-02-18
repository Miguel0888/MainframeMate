package de.bund.zrb.ui.commands;

import de.bund.zrb.ui.MainFrame;
import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;

public class ConnectMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;

    public ConnectMenuCommand(JFrame parent, TabbedPaneManager tabManager) {
        this.parent = parent;
    }

    @Override
    public String getId() {
        return "file.connect";
    }

    @Override
    public String getLabel() {
        return "Neue Verbindung...";
    }

    @Override
    public void perform() {
        // Open default FTP root via VirtualResourceOpener path
        if (parent instanceof MainFrame) {
            ((MainFrame) parent).openFileOrDirectory("/");
            return;
        }

        JOptionPane.showMessageDialog(parent,
                "Konnte Verbindung nicht öffnen (MainFrame fehlt).",
                "Fehler", JOptionPane.ERROR_MESSAGE);
    }
}
