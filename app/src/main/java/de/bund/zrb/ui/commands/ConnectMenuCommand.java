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
        return "FTP-Verbindung...";
    }

    @Override
    public void perform() {
        // Open FTP root via explicit ftp: prefix to avoid confusion with local "/" on Unix
        if (parent instanceof MainFrame) {
            ((MainFrame) parent).openFileOrDirectory("ftp:/");
            return;
        }

        JOptionPane.showMessageDialog(parent,
                "Konnte Verbindung nicht öffnen (MainFrame fehlt).",
                "Fehler", JOptionPane.ERROR_MESSAGE);
    }
}
