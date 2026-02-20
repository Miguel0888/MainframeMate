package de.bund.zrb.ui.commands;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.login.LoginManager;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ndv.NdvService;
import de.bund.zrb.ndv.NdvException;
import de.bund.zrb.ui.NdvConnectionTab;
import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;
import java.awt.*;

/**
 * Menu command "NDV-Verbindung..." to connect to a Natural Development Server.
 * Uses the shared server credentials from Settings + LoginManager (same as FTP).
 */
public class ConnectNdvMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;
    private final TabbedPaneManager tabManager;

    public ConnectNdvMenuCommand(JFrame parent, TabbedPaneManager tabManager) {
        this.parent = parent;
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "file.ndv";
    }

    @Override
    public String getLabel() {
        return "NDV-Verbindung...";
    }

    @Override
    public void perform() {
        Settings settings = SettingsHelper.load();

        String host = settings.host;
        String user = settings.user;

        // Validate host/user from settings
        if (host == null || host.trim().isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "Kein Server konfiguriert.\nBitte unter Einstellungen → Server den Host angeben.",
                    "Kein Server", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (user == null || user.trim().isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "Kein Benutzer konfiguriert.\nBitte unter Einstellungen → Server den Benutzer angeben.",
                    "Kein Benutzer", JOptionPane.WARNING_MESSAGE);
            return;
        }

        host = host.trim();
        user = user.trim();
        int port = settings.ndvPort;
        String defaultLibrary = settings.ndvDefaultLibrary != null ? settings.ndvDefaultLibrary.trim() : "";

        // Get password via LoginManager (cached, stored, or interactive prompt)
        String password = LoginManager.getInstance().getPassword(host, user);
        if (password == null || password.isEmpty()) {
            // User cancelled the password dialog
            return;
        }

        final String fHost = host;
        final String fUser = user;
        final int fPort = port;
        final String fPassword = password;
        final String fLibrary = defaultLibrary;

        // Connect in background
        parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<NdvConnectionTab, Void> worker = new SwingWorker<NdvConnectionTab, Void>() {
            @Override
            protected NdvConnectionTab doInBackground() throws Exception {
                NdvService service = new NdvService();
                service.connect(fHost, fPort, fUser, fPassword);

                // Mark session as active (for ApplicationLocker)
                LoginManager.getInstance().onLoginSuccess(fHost, fUser);

                // Logon to default library if specified
                if (!fLibrary.isEmpty()) {
                    try {
                        service.logon(fLibrary.toUpperCase());
                    } catch (NdvException e) {
                        System.err.println("[ConnectNdvMenuCommand] Default library logon warning: " + e.getMessage());
                    }
                }

                return new NdvConnectionTab(tabManager, service);
            }

            @Override
            protected void done() {
                parent.setCursor(Cursor.getDefaultCursor());
                try {
                    NdvConnectionTab tab = get();
                    tabManager.addTab(tab);
                } catch (Exception e) {
                    String msg = e.getMessage();
                    if (e.getCause() != null) {
                        msg = e.getCause().getMessage();
                    }

                    // On auth failure, clear the cached password
                    if (msg != null && (msg.contains("Login") || msg.contains("login")
                            || msg.contains("NAT0873") || msg.contains("NAT7734"))) {
                        LoginManager.getInstance().invalidatePassword(fHost, fUser);
                    }

                    JOptionPane.showMessageDialog(parent,
                            "NDV-Verbindung fehlgeschlagen:\n" + msg,
                            "Verbindungsfehler", JOptionPane.ERROR_MESSAGE);
                    System.err.println("[ConnectNdvMenuCommand] Connection failed: " + msg);
                }
            }
        };
        worker.execute();
    }
}

