package de.bund.zrb.ui.commands;

import de.bund.zrb.files.auth.AuthCancelledException;
import de.bund.zrb.files.auth.ConnectionId;
import de.bund.zrb.files.auth.Credentials;
import de.bund.zrb.files.impl.auth.InteractiveCredentialsProvider;
import de.bund.zrb.files.impl.ftp.jes.JesFtpService;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.login.LoginManager;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.TabbedPaneManager;
import de.bund.zrb.ui.jes.JesJobsConnectionTab;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;
import java.awt.*;

/**
 * Menu command "JES-Jobs…" – connects via FTP JES and opens a JesJobsConnectionTab.
 */
public class ConnectJesMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;
    private final TabbedPaneManager tabManager;

    public ConnectJesMenuCommand(JFrame parent, TabbedPaneManager tabManager) {
        this.parent = parent;
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "file.jes";
    }

    @Override
    public String getLabel() {
        return "JES-Jobs\u2026";
    }

    @Override
    public void perform() {
        Settings settings = SettingsHelper.load();
        String host = settings.host;
        String user = settings.user;

        if (host == null || host.trim().isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "Kein FTP-Host konfiguriert.\nBitte zuerst unter Einstellungen → FTP-Server einen Host eintragen.",
                    "Kein Host", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Resolve credentials via LoginManager (may show password dialog)
        InteractiveCredentialsProvider credProvider =
                new InteractiveCredentialsProvider(LoginManager.getInstance()::getPassword);

        ConnectionId connId = new ConnectionId("ftp", host, user);
        Credentials credentials;
        try {
            credentials = credProvider.resolve(connId).orElse(null);
        } catch (AuthCancelledException e) {
            return; // user cancelled
        }
        if (credentials == null) return;

        parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        final String fHost = host.trim();
        final String fUser = credentials.getUsername();
        final String fPass = credentials.getPassword();

        new SwingWorker<JesFtpService, Void>() {
            @Override
            protected JesFtpService doInBackground() throws Exception {
                return new JesFtpService(fHost, fUser, fPass);
            }

            @Override
            protected void done() {
                parent.setCursor(Cursor.getDefaultCursor());
                try {
                    JesFtpService service = get();
                    LoginManager.getInstance().setSessionActive(true);
                    JesJobsConnectionTab tab = new JesJobsConnectionTab(service, tabManager);
                    tabManager.addTab(tab);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    JOptionPane.showMessageDialog(parent,
                            "JES-Verbindung fehlgeschlagen:\n" + cause.getMessage(),
                            "Verbindungsfehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}

