package de.bund.zrb.sharepoint;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.util.CredentialStore;
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
 * Handles WebDAV authentication for SharePoint UNC paths.
 * <p>
 * <b>Authentication strategy (in order):</b>
 * <ol>
 *   <li><b>File-system probe</b> — {@code new File(uncPath).exists()}.
 *       Works when the OS already has a valid Kerberos/NTLM session.</li>
 *   <li><b>Windows Integrated Auth (Kerberos/NTLM SSO)</b> —
 *       {@code net use \\host@SSL\DavWWWRoot} <em>without</em> explicit
 *       credentials.  The Windows WebClient service sends the logged-in
 *       user's ticket automatically when the SharePoint server is in
 *       the Intranet/Trusted Sites zone.</li>
 *   <li><b>Stored credentials</b> — user + encrypted password from
 *       {@link Settings#sharepointUser} /
 *       {@link Settings#sharepointEncryptedPassword}.</li>
 *   <li><b>Login dialog</b> — prompts the user for credentials if
 *       everything above fails.</li>
 * </ol>
 * In most corporate environments steps 1 or 2 succeed immediately because
 * the user is logged in with their AD account and SharePoint is configured
 * for Windows Integrated Authentication.  Explicit credentials are only
 * needed when SSO is not available (e.g. cross-domain access or SharePoint
 * Online with Basic Auth fallback).
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

        // ── 1. Already authenticated this session? ───────────────
        if (authenticatedHosts.contains(host)) return true;

        // ── 2. File-system probe (OS may have cached credentials) ─
        java.io.File probe = new java.io.File(uncPath);
        if (probe.exists()) {
            LOG.info("[SP-Auth] File-system probe succeeded (OS has active session)");
            authenticatedHosts.add(host);
            return true;
        }

        // ── 3. Try Windows Integrated Auth (Kerberos/NTLM SSO) ──
        //    net use WITHOUT explicit credentials — Windows sends the
        //    current user's Kerberos ticket / NTLM hash automatically.
        if (netUseSso(uncPath)) {
            LOG.info("[SP-Auth] SSO (Windows Integrated Auth) succeeded for " + host);
            authenticatedHosts.add(host);
            return true;
        }

        // ── 4. Try stored credentials (per-site from passwordEntries) ─
        Settings settings = SettingsHelper.load();
        String user = null;
        String password = null;

        // Look up site-specific credentials from SP password entries
        String siteId = findSiteIdForUncPath(settings, uncPath);
        if (siteId != null) {
            try {
                String[] cred = CredentialStore.resolveIncludingEmpty("pwd:" + siteId);
                if (cred[0] != null && !cred[0].isEmpty()) {
                    user = cred[0];
                    password = cred[1];
                }
            } catch (Exception e) {
                LOG.log(Level.FINE, "[SP-Auth] Failed to resolve site credentials for " + siteId, e);
            }
        }

        // Fallback: legacy global SharePoint credentials
        if ((user == null || user.isEmpty()) && settings.sharepointUser != null && !settings.sharepointUser.isEmpty()) {
            user = settings.sharepointUser;
            password = decryptPassword(settings.sharepointEncryptedPassword);
        }

        if (user != null && !user.trim().isEmpty() && password != null && !password.isEmpty()) {
            if (netUse(uncPath, user, password)) {
                LOG.info("[SP-Auth] Stored credentials succeeded for " + host);
                authenticatedHosts.add(host);
                return true;
            }
        }

        // ── 5. Prompt user ───────────────────────────────────────
        String[] creds = showLoginDialog(parent, host, user);
        if (creds == null) return false; // cancelled
        user = creds[0];
        password = creds[1];
        boolean wantsSave = Boolean.parseBoolean(creds.length > 2 ? creds[2] : "false");

        if (netUse(uncPath, user, password)) {
            authenticatedHosts.add(host);
            if (wantsSave) saveCredentialsForSite(uncPath, user, password);
            return true;
        }

        // ── 6. Second prompt (wrong password?) ───────────────────
        creds = showLoginDialog(parent, host, user);
        if (creds == null) return false;
        user = creds[0];
        password = creds[1];
        wantsSave = Boolean.parseBoolean(creds.length > 2 ? creds[2] : "false");

        if (netUse(uncPath, user, password)) {
            authenticatedHosts.add(host);
            if (wantsSave) saveCredentialsForSite(uncPath, user, password);
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
     * @return {@code {user, password, "true"/"false"}} or {@code null} if cancelled.
     *         The third element indicates whether the user wants to save the credentials.
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
                + "<small>Die automatische Anmeldung (Windows SSO) war nicht erfolgreich.<br>"
                + "Geben Sie Ihre Zugangsdaten f\u00fcr <b>" + escapeHtml(host) + "</b> ein.<br>"
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
        JCheckBox saveBox = new JCheckBox("Zugangsdaten speichern (unter Passw\u00f6rter)", true);
        panel.add(saveBox, gbc);

        // Focus password if user is prefilled
        if (prefillUser != null && !prefillUser.isEmpty()) {
            SwingUtilities.invokeLater(passField::requestFocusInWindow);
        } else {
            SwingUtilities.invokeLater(userField::requestFocusInWindow);
        }

        int result = JOptionPane.showConfirmDialog(parent, panel,
                "SharePoint \u2014 Anmeldung erforderlich",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return null;

        String user = userField.getText().trim();
        String password = new String(passField.getPassword());
        if (user.isEmpty()) return null;

        return new String[]{user, password, String.valueOf(saveBox.isSelected())};
    }

    // ═══════════════════════════════════════════════════════════
    //  net use commands
    // ═══════════════════════════════════════════════════════════

    /**
     * Try {@code net use} <b>without</b> explicit credentials —
     * Windows Integrated Authentication (Kerberos/NTLM SSO).
     * The WebClient service sends the current user's ticket automatically.
     */
    public static boolean netUseSso(String uncPath) {
        String sharePath = extractShareRoot(uncPath);
        if (sharePath == null) sharePath = uncPath;

        try {
            ProcessBuilder pb = new ProcessBuilder("net", "use", sharePath);
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
                LOG.info("[SP-Auth] SSO net use succeeded for " + sharePath);
                return true;
            } else {
                LOG.fine("[SP-Auth] SSO net use not successful (exit=" + exitCode + "): "
                        + output.toString().trim());
                return false;
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "[SP-Auth] SSO net use error for " + sharePath, e);
            return false;
        }
    }

    /**
     * Run {@code net use} with explicit credentials.
     */
    public static boolean netUse(String uncPath, String user, String password) {
        String sharePath = extractShareRoot(uncPath);
        if (sharePath == null) sharePath = uncPath;

        try {
            // First disconnect any stale connection (ignore errors)
            runCommand(new String[]{"net", "use", sharePath, "/delete", "/y"});

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
            runCommand(new String[]{"net", "use", sharePath, "/delete", "/y"});
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

    // ═══════════════════════════════════════════════════════════
    //  Private helpers
    // ═══════════════════════════════════════════════════════════

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

    /**
     * Save credentials for a specific UNC path by looking up the matching
     * SP password entry and storing via CredentialStore.
     */
    private static void saveCredentialsForSite(String uncPath, String user, String password) {
        try {
            Settings settings = SettingsHelper.load();
            String siteId = findSiteIdForUncPath(settings, uncPath);
            if (siteId != null) {
                CredentialStore.store("pwd:" + siteId, user, password);
                LOG.fine("[SP-Auth] Site credentials saved for " + siteId + " user: " + user);
            } else {
                // Fallback: save as global credentials
                saveCredentials(user, password);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[SP-Auth] Failed to save site credentials", e);
            saveCredentials(user, password);
        }
    }

    /**
     * Find the password entry ID for a given UNC path by matching URLs
     * in category "SP" password entries.
     */
    private static String findSiteIdForUncPath(Settings settings, String uncPath) {
        if (uncPath == null || settings == null) return null;

        for (Settings.PasswordEntryMeta meta : settings.passwordEntries) {
            if (!"SP".equals(meta.category)) continue;
            if (meta.url == null || meta.url.isEmpty()) continue;

            String entryUnc = SharePointPathUtil.toUncPath(meta.url);
            if (uncPath.startsWith(entryUnc) || entryUnc.startsWith(uncPath)) {
                return meta.id;
            }
        }
        return null;
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

    private static void runCommand(String[] cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), Charset.defaultCharset()));
            while (reader.readLine() != null) { /* drain */ }
            proc.waitFor();
        } catch (Exception e) {
            LOG.log(Level.FINE, "[SP-Auth] runCommand error", e);
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
