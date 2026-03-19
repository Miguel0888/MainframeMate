package de.bund.zrb.sharepoint;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.util.WindowsCryptoUtil;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles WebDAV authentication for SharePoint UNC paths using {@code net use}.
 * <p>
 * Windows's WebClient service requires credentials for HTTP-authenticated
 * SharePoint sites. This class runs {@code net use \\host@SSL\DavWWWRoot ...}
 * to establish the authenticated connection before any file-system access.
 * <p>
 * Credentials are stored in {@link Settings#sharepointUser} /
 * {@link Settings#sharepointEncryptedPassword} (encrypted via
 * {@link WindowsCryptoUtil}).
 */
public final class SharePointAuthenticator {

    private static final Logger LOG = Logger.getLogger(SharePointAuthenticator.class.getName());

    /** Hosts that have been successfully authenticated in this session. */
    private static final Set<String> authenticatedHosts = new HashSet<String>();

    private SharePointAuthenticator() {}

    /**
     * Ensure the WebDAV host for the given UNC path is authenticated.
     * Returns {@code true} if the connection is ready, {@code false} if
     * the user cancelled the credential dialog.
     *
     * @param uncPath  e.g. {@code \\host@SSL\DavWWWRoot\sites\MySite}
     * @param parent   parent component for dialogs (may be {@code null})
     */
    public static boolean ensureAuthenticated(String uncPath, Component parent) {
        String host = extractHost(uncPath);
        if (host == null || host.isEmpty()) return true; // not a UNC path

        // Already authenticated this session?
        if (authenticatedHosts.contains(host)) return true;

        // Try access without explicit auth first (may have OS-cached credentials)
        java.io.File probe = new java.io.File(uncPath);
        if (probe.exists()) {
            authenticatedHosts.add(host);
            return true;
        }

        // Load stored credentials
        Settings settings = SettingsHelper.load();
        String user = settings.sharepointUser;
        String password = decryptPassword(settings.sharepointEncryptedPassword);

        // If no stored credentials, prompt the user
        if (user == null || user.trim().isEmpty() || password == null || password.isEmpty()) {
            String[] creds = showLoginDialog(parent, host, user);
            if (creds == null) return false; // cancelled
            user = creds[0];
            password = creds[1];
        }

        // Try net use
        boolean success = netUse(uncPath, user, password);

        if (!success) {
            // Credentials may be wrong — prompt again
            String[] creds = showLoginDialog(parent, host, user);
            if (creds == null) return false;
            user = creds[0];
            password = creds[1];
            success = netUse(uncPath, user, password);
        }

        if (success) {
            authenticatedHosts.add(host);
            // Persist credentials
            saveCredentials(user, password);
            return true;
        }

        JOptionPane.showMessageDialog(parent,
                "Verbindung zu SharePoint konnte nicht hergestellt werden.\n\n"
                        + "Mögliche Ursachen:\n"
                        + "• Benutzername oder Passwort falsch\n"
                        + "• Der WebClient-Dienst ist nicht gestartet (net start WebClient)\n"
                        + "• Netzwerk nicht erreichbar\n"
                        + "• SSL-Zertifikat nicht vertrauenswürdig",
                "SharePoint-Verbindung", JOptionPane.ERROR_MESSAGE);
        return false;
    }

    /**
     * Show a login dialog for SharePoint WebDAV credentials.
     *
     * @return {@code {user, password}} or {@code null} if cancelled
     */
    public static String[] showLoginDialog(Component parent, String host, String prefillUser) {
        JTextField userField = new JTextField(prefillUser != null ? prefillUser : "", 25);
        JPasswordField passField = new JPasswordField(25);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(new JLabel("<html><b>SharePoint-Anmeldung</b><br>"
                + "<small>Geben Sie Ihre Zugangsdaten für <b>" + escapeHtml(host) + "</b> ein.<br>"
                + "Format: DOMAIN\\Benutzer oder benutzer@domain.de</small></html>"), gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 1; gbc.gridx = 0;
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Benutzer:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(userField, gbc);

        gbc.gridy = 2; gbc.gridx = 0;
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Passwort:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(passField, gbc);

        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 2;
        JCheckBox saveBox = new JCheckBox("Zugangsdaten speichern", true);
        panel.add(saveBox, gbc);

        // Focus password if user is prefilled
        if (prefillUser != null && !prefillUser.isEmpty()) {
            SwingUtilities.invokeLater(passField::requestFocusInWindow);
        } else {
            SwingUtilities.invokeLater(userField::requestFocusInWindow);
        }

        int result = JOptionPane.showConfirmDialog(parent, panel,
                "SharePoint — Anmeldung erforderlich",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return null;

        String user = userField.getText().trim();
        String password = new String(passField.getPassword());
        if (user.isEmpty()) return null;

        // Optionally persist
        if (saveBox.isSelected()) {
            saveCredentials(user, password);
        }

        return new String[]{user, password};
    }

    /**
     * Run {@code net use} to authenticate a WebDAV UNC path.
     */
    public static boolean netUse(String uncPath, String user, String password) {
        // Extract \\host@SSL\DavWWWRoot (the root share)
        String sharePath = extractShareRoot(uncPath);
        if (sharePath == null) sharePath = uncPath;

        try {
            // First disconnect any stale connection (ignore errors)
            runCommand(new String[]{"net", "use", sharePath, "/delete", "/y"}, false);

            // Now connect with credentials
            ProcessBuilder pb = new ProcessBuilder(
                    "net", "use", sharePath,
                    "/user:" + user, password);
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), Charset.defaultCharset()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = proc.waitFor();
            if (exitCode == 0) {
                LOG.info("[SP-Auth] net use succeeded for " + sharePath);
                return true;
            } else {
                LOG.warning("[SP-Auth] net use failed (exit=" + exitCode + "): " + output);
                return false;
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[SP-Auth] net use error for " + sharePath, e);
            return false;
        }
    }

    /**
     * Disconnect a previously authenticated WebDAV share.
     */
    public static void disconnect(String uncPath) {
        String sharePath = extractShareRoot(uncPath);
        if (sharePath == null) return;
        try {
            runCommand(new String[]{"net", "use", sharePath, "/delete", "/y"}, false);
            String host = extractHost(uncPath);
            if (host != null) authenticatedHosts.remove(host);
            LOG.info("[SP-Auth] Disconnected: " + sharePath);
        } catch (Exception e) {
            LOG.log(Level.FINE, "[SP-Auth] Disconnect error", e);
        }
    }

    /**
     * Reset session cache (e.g. after settings change).
     */
    public static void resetSession() {
        authenticatedHosts.clear();
    }

    // ── Private helpers ─────────────────────────────────────────────

    private static void saveCredentials(String user, String password) {
        try {
            Settings settings = SettingsHelper.load();
            settings.sharepointUser = user;
            settings.sharepointEncryptedPassword = WindowsCryptoUtil.encrypt(password);
            SettingsHelper.save(settings);
            LOG.fine("[SP-Auth] Credentials saved for user: " + user);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[SP-Auth] Failed to save credentials", e);
        }
    }

    private static String decryptPassword(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) return null;
        try {
            return WindowsCryptoUtil.decrypt(encrypted);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[SP-Auth] Failed to decrypt password", e);
            return null;
        }
    }

    /**
     * Extract the host part from a UNC path.
     * {@code \\host@SSL\DavWWWRoot\...} → {@code host@SSL}
     */
    static String extractHost(String uncPath) {
        if (uncPath == null || !uncPath.startsWith("\\\\")) return null;
        String rest = uncPath.substring(2);
        int sep = rest.indexOf('\\');
        return sep > 0 ? rest.substring(0, sep) : rest;
    }

    /**
     * Extract the share root: {@code \\host@SSL\DavWWWRoot\sites\x\docs}
     * → {@code \\host@SSL\DavWWWRoot}
     */
    static String extractShareRoot(String uncPath) {
        if (uncPath == null || !uncPath.startsWith("\\\\")) return null;
        String rest = uncPath.substring(2);
        int firstSlash = rest.indexOf('\\');
        if (firstSlash < 0) return uncPath;
        int secondSlash = rest.indexOf('\\', firstSlash + 1);
        if (secondSlash < 0) return uncPath;
        return "\\\\" + rest.substring(0, secondSlash);
    }

    private static void runCommand(String[] cmd, boolean throwOnError) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            // Drain output
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), Charset.defaultCharset()));
            while (reader.readLine() != null) { /* drain */ }
            proc.waitFor();
        } catch (Exception e) {
            if (throwOnError) throw new RuntimeException(e);
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

