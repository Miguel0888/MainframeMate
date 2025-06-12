package de.bund.zrb.ui.commands;

import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.ConnectionTabImpl;
import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.api.MenuCommand;

import javax.swing.*;
import java.io.IOException;

public class ConnectMenuCommand implements MenuCommand {

    private final JFrame parent;
    private final TabbedPaneManager tabManager;

    public ConnectMenuCommand(JFrame parent, TabbedPaneManager tabManager) {
        this.parent = parent;
        this.tabManager = tabManager;
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
        FtpManager ftpManager = new FtpManager();
        Settings settings = SettingsHelper.load();

        try {
            ftpManager.connect(settings.host, settings.user);
            tabManager.addTab(new ConnectionTabImpl(ftpManager, tabManager));
        } catch (IOException ex) {
            String msg = ex.getMessage();
            if ("Kein Passwort verfügbar".equals(msg)) {
                // Benutzer hat den Login abgebrochen → nichts tun
                return;
            }

            JOptionPane.showMessageDialog(parent,
                    "Verbindung fehlgeschlagen:\n" + msg,
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

}
