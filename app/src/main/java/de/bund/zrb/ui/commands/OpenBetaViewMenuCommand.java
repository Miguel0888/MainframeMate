package de.bund.zrb.ui.commands;

import de.bund.zrb.betaview.*;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.login.LoginManager;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.api.ShortcutMenuCommand;
import de.zrb.bund.newApi.ui.FtpTab;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

/**
 * Opens a new BetaView ConnectionTab.
 * Uses the shared server credentials from Settings + LoginManager (same as FTP/NDV).
 */
public class OpenBetaViewMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;
    private final TabbedPaneManager tabManager;

    public OpenBetaViewMenuCommand(JFrame parent, TabbedPaneManager tabManager) {
        this.parent = parent;
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "connection.betaview";
    }

    @Override
    public String getLabel() {
        return "BetaView";
    }

    @Override
    public void perform() {
        Settings settings = SettingsHelper.load();
        String betaviewUrl = settings.betaviewUrl != null ? settings.betaviewUrl.trim() : "";

        // ── 1) Prompt for URL if not configured ─────────────────────────
        if (betaviewUrl.isEmpty()) {
            betaviewUrl = promptForUrl();
            if (betaviewUrl == null || betaviewUrl.trim().isEmpty()) {
                return; // User cancelled
            }
            betaviewUrl = betaviewUrl.trim();
        }

        // ── 2) Auto-normalise: strip to scheme+host+port if user pasted a full URL ──
        betaviewUrl = normaliseAndPersist(betaviewUrl, settings);
        if (betaviewUrl == null) {
            return; // User cancelled after seeing correction dialog
        }

        // ── 3) Determine credentials (shared with FTP/NDV or separate) ──
        String host;
        String user;

        if (settings.betaviewUseSharedCredentials) {
            host = settings.host;
            user = settings.user;
        } else {
            host = settings.betaviewHost;
            user = settings.betaviewUser;
        }

        if (host == null || host.trim().isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "Kein Server konfiguriert.\nBitte unter Einstellungen den Host angeben.",
                    "Kein Server", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (user == null || user.trim().isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "Kein Benutzer konfiguriert.\nBitte unter Einstellungen den Benutzer angeben.",
                    "Kein Benutzer", JOptionPane.WARNING_MESSAGE);
            return;
        }

        host = host.trim();
        user = user.trim();

        // ── 4) Get password via LoginManager (cached, stored, or interactive prompt) ──
        String password;
        if (!settings.betaviewUseSharedCredentials
                && settings.betaviewEncryptedPassword != null
                && !settings.betaviewEncryptedPassword.isEmpty()) {
            // Try stored encrypted password first for separate BetaView credentials
            try {
                password = de.bund.zrb.util.WindowsCryptoUtil.decrypt(settings.betaviewEncryptedPassword);
            } catch (de.bund.zrb.util.JnaBlockedException e) {
                throw e; // must not be swallowed — user needs to switch password method
            } catch (Exception ignore) {
                password = LoginManager.getInstance().getPassword(host, user);
            }
        } else {
            password = LoginManager.getInstance().getPassword(host, user);
        }

        if (password == null || password.isEmpty()) {
            return; // User cancelled the password dialog
        }

        final String fUrl = betaviewUrl;
        final String fHost = host;
        final String fUser = user;
        final String fPassword = password;

        // Build defaults from settings
        final BetaViewAppProperties defaults = new BetaViewAppProperties(
                betaviewUrl, user, "",
                settings.betaviewFavoriteId,
                settings.betaviewLocale,
                settings.betaviewExtension,
                settings.betaviewForm,
                "",      // report
                "",      // jobName
                settings.betaviewDaysBack
        );

        // ── 5) Connect in background ────────────────────────────────────
        parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new SwingWorker<BetaViewConnectionTab, Void>() {
            @Override
            protected BetaViewConnectionTab doInBackground() throws Exception {
                URL baseUrl = new URL(fUrl);

                // Use factory to connect (keeps package-private classes encapsulated)
                BetaViewFactory.ConnectionResult connResult = BetaViewFactory.connect(baseUrl, fUser, fPassword);

                // Mark session as active (for ApplicationLocker)
                LoginManager.getInstance().onLoginSuccess(fHost, fUser);

                String displayName = fUser + "@" + baseUrl.getHost();

                return BetaViewFactory.createConnectionTab(baseUrl, connResult, displayName, defaults);
            }

            @Override
            protected void done() {
                parent.setCursor(Cursor.getDefaultCursor());
                try {
                    BetaViewConnectionTab connTab = get();

                    // Create document tab manager that opens documents as separate FtpTabs
                    BetaViewDocumentTabManager docManager = connTab.createAndWireDocumentTabManager(
                            new BetaViewDocumentTabManager.TabHost() {
                                @Override
                                public void addTab(FtpTab tab) {
                                    tabManager.addTab(tab);
                                }
                                @Override
                                public void removeTab(FtpTab tab) {
                                    // TabbedPaneManager handles close via onClose()
                                }
                            }
                    );

                    // Add connection tab to TabbedPaneManager
                    tabManager.addTab(connTab);

                    // Fetch and open server-side tabs (lazy)
                    docManager.fetchAndOpenServerTabs();

                } catch (Exception e) {
                    String msg = e.getMessage();
                    if (e.getCause() != null) {
                        msg = e.getCause().getMessage();
                    }

                    // On auth failure, clear the cached password
                    if (msg != null && (msg.contains("Login") || msg.contains("login")
                            || msg.contains("401") || msg.contains("403"))) {
                        LoginManager.getInstance().invalidatePassword(fHost, fUser);
                    }

                    JOptionPane.showMessageDialog(parent,
                            "BetaView-Verbindung fehlgeschlagen:\n" + msg,
                            "Verbindungsfehler", JOptionPane.ERROR_MESSAGE);
                    System.err.println("[OpenBetaViewMenuCommand] Connection failed: " + msg);
                }
            }
        }.execute();
    }

    // ── URL prompt ──────────────────────────────────────────────────────

    private String promptForUrl() {
        return (String) JOptionPane.showInputDialog(
                parent,
                "BetaView-URL ist noch nicht konfiguriert.\n"
                        + "Bitte die Base-URL des BetaView-Servers eingeben\n"
                        + "(z.B. https://betaview.example.com):",
                "BetaView-URL eingeben",
                JOptionPane.QUESTION_MESSAGE,
                null, null, "https://");
    }

    // ── URL normalisation ───────────────────────────────────────────────

    private String normaliseAndPersist(String raw, Settings settings) {
        String normalised = normaliseUrl(raw);

        boolean changed = !normalised.equals(raw);

        if (changed) {
            int choice = JOptionPane.showConfirmDialog(parent,
                    "Die eingegebene URL wurde automatisch korrigiert:\n\n"
                            + "Eingabe:   " + raw + "\n"
                            + "Korrigiert: " + normalised + "\n\n"
                            + "Falls die automatische Korrektur falsch war,\n"
                            + "können Sie die URL unter Einstellungen → BetaView anpassen.\n\n"
                            + "Mit der korrigierten URL fortfahren?",
                    "URL korrigiert",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.INFORMATION_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return null;
            }
        }

        // Persist (always, so first-time prompt is saved too)
        if (!normalised.equals(settings.betaviewUrl)) {
            settings.betaviewUrl = normalised;
            SettingsHelper.save(settings);
        }

        return normalised;
    }

    static String normaliseUrl(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";

        // Ensure scheme
        if (!s.contains("://")) {
            s = "https://" + s;
        }

        try {
            URL url = new URL(s);
            String scheme = url.getProtocol();
            String host = url.getHost();
            int port = url.getPort();

            String path = url.getPath();
            if (path == null) path = "";

            // Extract first path segment if present (e.g. "/betaview" from "/betaview/login.action")
            String contextPath = "";
            if (!path.isEmpty() && !path.equals("/")) {
                String stripped = path.startsWith("/") ? path.substring(1) : path;
                int slash = stripped.indexOf('/');
                if (slash >= 0) {
                    contextPath = "/" + stripped.substring(0, slash);
                } else {
                    contextPath = "/" + stripped;
                }
            }

            StringBuilder result = new StringBuilder();
            result.append(scheme).append("://").append(host);
            if (port > 0 && port != 80 && port != 443) {
                result.append(':').append(port);
            }
            result.append(contextPath);
            if (!result.toString().endsWith("/")) {
                result.append('/');
            }
            return result.toString();
        } catch (Exception e) {
            if (!s.endsWith("/")) s += "/";
            return s;
        }
    }
}
