package de.bund.zrb.util;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads and writes passwords from/to a KeePass {@code .kdbx} database
 * via <b>PowerShell</b> and the {@code KeePass.exe} assembly from KeePass 2.x.
 * <p>
 * No separate {@code KPScript.exe} is required — the KeePassLib namespace is
 * compiled directly into {@code KeePass.exe}.  PowerShell loads it via
 * {@code [Reflection.Assembly]::LoadFrom('KeePass.exe')} (not {@code Add-Type},
 * which rejects {@code .exe} extensions).
 * <p>
 * The database is opened with the <b>Windows User Account</b> composite key
 * ({@code KcpUserAccount}), which uses DPAPI to derive the key from the
 * currently logged-in Windows user.  If the database requires a different
 * master key (password, key file), the operation will fail with a clear error.
 */
final class KeePassProvider {

    private static final Logger LOG = Logger.getLogger(KeePassProvider.class.getName());
    private static final int TIMEOUT_SECONDS = 30;

    private KeePassProvider() {}

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Read the password for the configured entry from KeePass.
     *
     * @return the plaintext password
     * @throws KeePassNotAvailableException if config is invalid or call fails
     */
    static String getPassword() {
        Settings settings = SettingsHelper.load();
        if ("RPC".equalsIgnoreCase(settings.keepassAccessMethod)) {
            String pw = rpcGetField(settings, "password");
            if (pw != null && !pw.isEmpty()) return pw;
            // Entry not found via RPC — prompt user and create via AddLogin
            return rpcPromptAndCreate(settings);
        }
        validateConfig(settings);
        String script = buildGetFieldScript(settings, "Password");
        String result = execPowerShell(script).trim();
        if (result.isEmpty()) {
            // Entry doesn't exist → ask user for password and create it
            return promptAndCreateEntry(settings);
        }
        return result;
    }

    /**
     * Read the UserName field for the configured entry from KeePass.
     *
     * @return the username, or {@code null} if not set
     */
    static String getUserName() {
        Settings settings = SettingsHelper.load();
        if ("RPC".equalsIgnoreCase(settings.keepassAccessMethod)) {
            try {
                String result = rpcGetField(settings, "username");
                return (result != null && !result.isEmpty()) ? result : null;
            } catch (KeePassNotAvailableException e) {
                LOG.fine("Could not read UserName via RPC: " + e.getMessage());
                return null;
            }
        }
        validateConfig(settings);
        try {
            String result = execPowerShell(buildGetFieldScript(settings, "UserName")).trim();
            return result.isEmpty() ? null : result;
        } catch (KeePassNotAvailableException e) {
            LOG.fine("Could not read UserName from KeePass: " + e.getMessage());
            return null;
        }
    }

    /**
     * Store (or update) a password in the KeePass database.
     * If the entry does not exist, it is created.
     *
     * @param password the plaintext password to store
     * @throws KeePassNotAvailableException on any failure
     */
    static void putPassword(String password) {
        Settings settings = SettingsHelper.load();
        if ("RPC".equalsIgnoreCase(settings.keepassAccessMethod)) {
            rpcUpdatePassword(settings, password);
            return;
        }
        validateConfig(settings);

        String dbPath = settings.keepassDatabasePath.trim();
        String entry = settings.keepassEntryTitle.trim();

        String script = preamble(settings)
                + "$found = $false\n"
                + "foreach ($e in $db.RootGroup.GetEntries($true)) {\n"
                + "  if ($e.Strings.ReadSafe('Title') -eq '" + esc(entry) + "') {\n"
                + "    $e.Strings.Set('Password', (New-Object KeePassLib.Security.ProtectedString($true, '" + esc(password) + "')))\n"
                + "    $found = $true; break\n"
                + "  }\n"
                + "}\n"
                + "if (-not $found) {\n"
                + "  $ne = New-Object KeePassLib.PwEntry($db.RootGroup, $true, $true)\n"
                + "  $ne.Strings.Set('Title', (New-Object KeePassLib.Security.ProtectedString($true, '" + esc(entry) + "')))\n"
                + "  $ne.Strings.Set('Password', (New-Object KeePassLib.Security.ProtectedString($true, '" + esc(password) + "')))\n"
                + "  $db.RootGroup.AddEntry($ne, $true)\n"
                + "}\n"
                + "$db.Save((New-Object KeePassLib.Interfaces.NullStatusLogger))\n"
                + "$db.Close()\n"
                + "Write-Output 'OK'";

        String result = execPowerShell(script).trim();
        if (!result.contains("OK")) {
            throw new KeePassNotAvailableException(
                    "KeePass-Eintrag konnte nicht geschrieben werden: " + sanitize(result));
        }
        LOG.fine("KeePass entry updated: " + entry);
    }

    /**
     * List all entries from the KeePass database.
     * Returns a formatted string with entry details.
     *
     * @return formatted entry list
     * @throws KeePassNotAvailableException on any failure
     */
    static String listEntries() {
        Settings settings = SettingsHelper.load();
        if ("RPC".equalsIgnoreCase(settings.keepassAccessMethod)) {
            return rpcListEntries(settings);
        }
        validateConfig(settings);

        String script = preamble(settings)
                + "foreach ($e in $db.RootGroup.GetEntries($true)) {\n"
                + "  Write-Output (\"Title: \" + $e.Strings.ReadSafe('Title'))\n"
                + "  Write-Output (\"UserName: \" + $e.Strings.ReadSafe('UserName'))\n"
                + "  Write-Output (\"Password: \" + $e.Strings.ReadSafe('Password'))\n"
                + "  Write-Output (\"URL: \" + $e.Strings.ReadSafe('URL'))\n"
                + "  Write-Output (\"UniqueID: \" + $e.Uuid.ToHexString())\n"
                + "  Write-Output (\"MM_Category: \" + $e.Strings.ReadSafe('MM_Category'))\n"
                + "  Write-Output (\"MM_DisplayName: \" + $e.Strings.ReadSafe('MM_DisplayName'))\n"
                + "  Write-Output (\"MM_RequiresLogin: \" + $e.Strings.ReadSafe('MM_RequiresLogin'))\n"
                + "  Write-Output (\"MM_UseProxy: \" + $e.Strings.ReadSafe('MM_UseProxy'))\n"
                + "  Write-Output (\"MM_AutoIndex: \" + $e.Strings.ReadSafe('MM_AutoIndex'))\n"
                + "  Write-Output (\"MM_SavePassword: \" + $e.Strings.ReadSafe('MM_SavePassword'))\n"
                + "  Write-Output (\"MM_SessionCache: \" + $e.Strings.ReadSafe('MM_SessionCache'))\n"
                + "  Write-Output (\"MM_CertAlias: \" + $e.Strings.ReadSafe('MM_CertAlias'))\n"
                + "  Write-Output ''\n"
                + "}\n"
                + "$db.Close()\n"
                + "Write-Output 'OK: Operation completed successfully.'";

        return execPowerShell(script);
    }

    // ── Internals ───────────────────────────────────────────────────────────

    /**
     * Common PowerShell preamble: load KeePass.exe assembly, open database.
     * Returns a script prefix that leaves {@code $db} open and ready to use.
     */
    private static String preamble(Settings settings) {
        String exePath = resolveExePath(settings);
        String dbPath = settings.keepassDatabasePath.trim();

        return "[Reflection.Assembly]::LoadFrom('" + esc(exePath) + "') | Out-Null\n"
                + "$io = New-Object KeePassLib.Serialization.IOConnectionInfo\n"
                + "$io.Path = '" + esc(dbPath) + "'\n"
                + "$key = New-Object KeePassLib.Keys.CompositeKey\n"
                + "$key.AddUserKey((New-Object KeePassLib.Keys.KcpUserAccount))\n"
                + "$db = New-Object KeePassLib.PwDatabase\n"
                + "$db.Open($io, $key, (New-Object KeePassLib.Interfaces.NullStatusLogger))\n";
    }

    private static void validateConfig(Settings settings) {
        String installPath = settings.keepassInstallPath;
        if (installPath == null || installPath.trim().isEmpty()) {
            throw new KeePassNotAvailableException(
                    "KeePass-Installationsverzeichnis ist nicht konfiguriert.\n"
                    + "Bitte unter Einstellungen \u2192 Allgemein \u2192 Sicherheit den Pfad angeben.");
        }
        String exePath = resolveExePath(settings);
        if (!new File(exePath).isFile()) {
            throw new KeePassNotAvailableException(
                    "KeePass.exe nicht gefunden: " + exePath + "\n"
                    + "Bitte pr\u00fcfen Sie, ob KeePass 2.x im angegebenen Verzeichnis installiert ist.");
        }
        String db = settings.keepassDatabasePath;
        if (db == null || db.trim().isEmpty()) {
            throw new KeePassNotAvailableException("Pfad zur .kdbx-Datenbank ist nicht konfiguriert.");
        }
        if (!new File(db.trim()).isFile()) {
            throw new KeePassNotAvailableException(
                    "KeePass-Datenbank nicht gefunden: " + db.trim());
        }
        String entry = settings.keepassEntryTitle;
        if (entry == null || entry.trim().isEmpty()) {
            throw new KeePassNotAvailableException("KeePass-Eintragstitel ist nicht konfiguriert.");
        }
    }

    /**
     * Build a PowerShell script that reads a single field from a KeePass entry.
     */
    private static String buildGetFieldScript(Settings settings, String fieldName) {
        String entry = settings.keepassEntryTitle.trim();

        return preamble(settings)
                + "foreach ($e in $db.RootGroup.GetEntries($true)) {\n"
                + "  if ($e.Strings.ReadSafe('Title') -eq '" + esc(entry) + "') {\n"
                + "    Write-Output $e.Strings.ReadSafe('" + fieldName + "')\n"
                + "    break\n"
                + "  }\n"
                + "}\n"
                + "$db.Close()";
    }

    /**
     * Resolve the path to KeePass.exe from the installation directory.
     */
    private static String resolveExePath(Settings settings) {
        String installDir = settings.keepassInstallPath.trim();
        File dir = new File(installDir);
        if (dir.isFile()) {
            return dir.getAbsolutePath(); // user pointed directly to KeePass.exe
        }
        return new File(dir, "KeePass.exe").getAbsolutePath();
    }

    /**
     * Execute a PowerShell script and return stdout.
     */
    private static String execPowerShell(String script) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy", "Bypass",
                    "-Command", script
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output = readFully(process.getInputStream());

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new KeePassNotAvailableException(
                        "PowerShell-Timeout nach " + TIMEOUT_SECONDS + " Sekunden.\n"
                        + "M\u00f6glicherweise wartet KeePass auf die Eingabe des Master-Passworts.");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new KeePassNotAvailableException(
                        "PowerShell beendet mit Exit-Code " + exitCode + ":\n" + sanitize(output));
            }

            return output;
        } catch (KeePassNotAvailableException e) {
            throw e;
        } catch (IOException e) {
            throw new KeePassNotAvailableException("PowerShell konnte nicht gestartet werden.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeePassNotAvailableException("PowerShell wurde unterbrochen.", e);
        }
    }

    private static String readFully(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = in.read(tmp)) != -1) {
            buf.write(tmp, 0, n);
        }
        return buf.toString("UTF-8").trim();
    }

    /**
     * Escape single quotes for PowerShell string literals.
     * PowerShell escapes {@code '} as {@code ''} inside single-quoted strings.
     */
    private static String esc(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    /**
     * Limit error output length to avoid leaking sensitive data.
     */
    private static String sanitize(String output) {
        if (output == null) return "";
        if (output.length() > 500) {
            output = output.substring(0, 500) + "\u2026";
        }
        return output;
    }

    // ── Entry creation with user prompt ───────────────────────────────────

    /**
     * Prompts the user for username + password via a Swing dialog and creates
     * the KeePass entry via PowerShell.  Called when the configured entry does not exist yet.
     *
     * @param settings current settings
     * @return the password the user entered
     * @throws KeePassNotAvailableException if the user cancels or creation fails
     */
    private static String promptAndCreateEntry(Settings settings) {
        String entryTitle = settings.keepassEntryTitle.trim();

        JTextField userField = new JTextField(20);
        JPasswordField passField = new JPasswordField(20);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        String info = "<html><body style='width:320px'>"
                  + "Der KeePass-Eintrag <b>\"" + entryTitle + "\"</b> existiert noch nicht.<br>"
                  + "Bitte geben Sie Benutzername und Passwort ein — der Eintrag wird automatisch angelegt."
                  + "</body></html>";
        panel.add(new JLabel(info), gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Benutzername:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(userField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Passwort:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(passField, gbc);

        int result = JOptionPane.showConfirmDialog(null, panel,
                "KeePass-Eintrag anlegen: " + entryTitle,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            throw new KeePassNotAvailableException(
                    "Kein Passwort eingegeben — KeePass-Eintrag \"" + entryTitle + "\" wurde nicht angelegt.");
        }

        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());

        if (pass.isEmpty()) {
            throw new KeePassNotAvailableException(
                    "Leeres Passwort — KeePass-Eintrag \"" + entryTitle + "\" wurde nicht angelegt.");
        }


        // PowerShell mode: create the entry with both username and password
        createEntry(settings, entryTitle, user, pass, "", null, null, false, false, false, "");
        LOG.info("[KeePass] Entry \"" + entryTitle + "\" created for user \"" + user + "\"");
        return pass;
    }

    /**
     * Create a new KeePass entry via PowerShell with MM_* Advanced String Fields.
     */
    private static void createEntry(Settings settings, String title, String userName, String password,
                                    String url, String displayName, String category,
                                    boolean requiresLogin, boolean useProxy, boolean autoIndex,
                                    String certAlias) {
        String urlLine = "";
        if (url != null && !url.isEmpty()) {
            urlLine = "$ne.Strings.Set('URL', (New-Object KeePassLib.Security.ProtectedString($false, '" + esc(url) + "')))\n";
        }
        String mmLines = buildMmCreateScript(displayName, category, requiresLogin, useProxy, autoIndex, certAlias);

        String script = preamble(settings)
                + "$ne = New-Object KeePassLib.PwEntry($db.RootGroup, $true, $true)\n"
                + "$ne.Strings.Set('Title', (New-Object KeePassLib.Security.ProtectedString($true, '" + esc(title) + "')))\n"
                + "$ne.Strings.Set('UserName', (New-Object KeePassLib.Security.ProtectedString($false, '" + esc(userName) + "')))\n"
                + "$ne.Strings.Set('Password', (New-Object KeePassLib.Security.ProtectedString($true, '" + esc(password) + "')))\n"
                + urlLine
                + mmLines
                + "$db.RootGroup.AddEntry($ne, $true)\n"
                + "$db.Save((New-Object KeePassLib.Interfaces.NullStatusLogger))\n"
                + "$db.Close()\n"
                + "Write-Output 'OK'";

        String out = execPowerShell(script).trim();
        if (!out.contains("OK")) {
            throw new KeePassNotAvailableException(
                    "KeePass-Eintrag \"" + title + "\" konnte nicht angelegt werden: " + sanitize(out));
        }
    }

    /**
     * Build PowerShell snippet to SET MM_* Advanced String Fields on an <b>existing</b> entry {@code $e}.
     */
    private static String buildMmUpdateScript(String displayName, String category,
                                              boolean requiresLogin, boolean useProxy, boolean autoIndex,
                                              String certAlias) {
        StringBuilder sb = new StringBuilder();
        sb.append("    $e.Strings.Set('MM_DisplayName', (New-Object KeePassLib.Security.ProtectedString($false, '")
                .append(esc(displayName != null ? displayName : "")).append("')))\n");
        sb.append("    $e.Strings.Set('MM_Category', (New-Object KeePassLib.Security.ProtectedString($false, '")
                .append(esc(category != null ? category : "")).append("')))\n");
        sb.append("    $e.Strings.Set('MM_RequiresLogin', (New-Object KeePassLib.Security.ProtectedString($false, '")
                .append(requiresLogin).append("')))\n");
        sb.append("    $e.Strings.Set('MM_UseProxy', (New-Object KeePassLib.Security.ProtectedString($false, '")
                .append(useProxy).append("')))\n");
        sb.append("    $e.Strings.Set('MM_AutoIndex', (New-Object KeePassLib.Security.ProtectedString($false, '")
                .append(autoIndex).append("')))\n");
        sb.append("    $e.Strings.Set('MM_CertAlias', (New-Object KeePassLib.Security.ProtectedString($false, '")
                .append(esc(certAlias != null ? certAlias : "")).append("')))\n");
        return sb.toString();
    }

    /**
     * Build PowerShell snippet to SET MM_* Advanced String Fields on a <b>new</b> entry {@code $ne}.
     */
    private static String buildMmCreateScript(String displayName, String category,
                                              boolean requiresLogin, boolean useProxy, boolean autoIndex,
                                              String certAlias) {
        StringBuilder sb = new StringBuilder();
        sb.append("  $ne.Strings.Set('MM_DisplayName', (New-Object KeePassLib.Security.ProtectedString($false, '")
                .append(esc(displayName != null ? displayName : "")).append("')))\n");
        sb.append("  $ne.Strings.Set('MM_Category', (New-Object KeePassLib.Security.ProtectedString($false, '")
                .append(esc(category != null ? category : "")).append("')))\n");
        sb.append("  $ne.Strings.Set('MM_RequiresLogin', (New-Object KeePassLib.Security.ProtectedString($false, '")
                .append(requiresLogin).append("')))\n");
        sb.append("  $ne.Strings.Set('MM_UseProxy', (New-Object KeePassLib.Security.ProtectedString($false, '")
                .append(useProxy).append("')))\n");
        sb.append("  $ne.Strings.Set('MM_AutoIndex', (New-Object KeePassLib.Security.ProtectedString($false, '")
                .append(autoIndex).append("')))\n");
        sb.append("  $ne.Strings.Set('MM_CertAlias', (New-Object KeePassLib.Security.ProtectedString($false, '")
                .append(esc(certAlias != null ? certAlias : "")).append("')))\n");
        return sb.toString();
    }

    // ── KeePassRPC helpers ──────────────────────────────────────────────

    /**
     * Prompt the user for username + password and create a new entry in KeePass via RPC.
     * Called when {@code getPassword()} finds no matching entry.
     *
     * @return the password the user entered
     */
    private static String rpcPromptAndCreate(Settings settings) {
        String entryTitle = settings.keepassEntryTitle.trim();

        JTextField userField = new JTextField(20);
        JPasswordField passField = new JPasswordField(20);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        String info = "<html><body style='width:320px'>"
                + "Der KeePass-Eintrag <b>\"" + entryTitle + "\"</b> existiert noch nicht.<br>"
                + "Bitte geben Sie Benutzername und Passwort ein — der Eintrag wird automatisch in KeePass angelegt."
                + "</body></html>";
        panel.add(new JLabel(info), gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Benutzername:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(userField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Passwort:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(passField, gbc);

        int result = JOptionPane.showConfirmDialog(null, panel,
                "KeePass-Eintrag anlegen: " + entryTitle,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            throw new KeePassNotAvailableException(
                    "Kein Passwort eingegeben — KeePass-Eintrag \"" + entryTitle + "\" wurde nicht angelegt.");
        }

        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());

        if (pass.isEmpty()) {
            throw new KeePassNotAvailableException(
                    "Leeres Passwort — KeePass-Eintrag \"" + entryTitle + "\" wurde nicht angelegt.");
        }

        // Create entry in KeePass via RPC AddLogin
        validateRpcConfig(settings);
        KeePassRpcClient client = new KeePassRpcClient(
                settings.getEffectiveRpcHost(), settings.keepassRpcPort,
                "MainframeMate", settings.keepassRpcKey.trim(),
                settings.getEffectiveRpcOrigin());
        try {
            client.connect();
            client.addLogin(entryTitle, user, pass, "");
        } finally {
            client.close();
        }

        LOG.info("[KeePass] Entry \"" + entryTitle + "\" created via RPC for user \"" + user + "\"");
        return pass;
    }

    /**
     * Update (or create) a password in KeePass via RPC.
     */
    private static void rpcUpdatePassword(Settings settings, String password) {
        validateRpcConfig(settings);
        String entryTitle = settings.keepassEntryTitle.trim();

        // First try to get the existing username so we can preserve it
        String existingUser = null;
        KeePassRpcClient client = new KeePassRpcClient(
                settings.getEffectiveRpcHost(), settings.keepassRpcPort,
                "MainframeMate", settings.keepassRpcKey.trim(),
                settings.getEffectiveRpcOrigin());
        try {
            client.connect();
            existingUser = client.getUserName(entryTitle);
            client.updateLogin(entryTitle, existingUser != null ? existingUser : "", password);
        } finally {
            client.close();
        }
        LOG.info("[KeePass] Entry \"" + entryTitle + "\" updated via RPC");
    }

    private static void validateRpcConfig(Settings settings) {
        if (settings.keepassRpcKey == null || settings.keepassRpcKey.trim().isEmpty()) {
            // No SRP key configured — show the pairing dialog
            if (javax.swing.SwingUtilities.isEventDispatchThread()) {
                String key = KeePassRpcPairingDialog.showAndPair();
                if (key != null && !key.trim().isEmpty()) {
                    // Reload settings to pick up the newly saved key
                    Settings fresh = SettingsHelper.load();
                    settings.keepassRpcKey = fresh.keepassRpcKey;
                } else {
                    throw new KeePassNotAvailableException(
                            "KeePassRPC-Pairing wurde abgebrochen.\n"
                          + "Bitte starten Sie den Vorgang erneut oder konfigurieren Sie den "
                          + "SRP-Schlüssel manuell unter Einstellungen \u2192 Allgemein \u2192 Sicherheit.");
                }
            } else {
                // Not on EDT — try to show the dialog on the EDT and wait for the result
                final java.util.concurrent.atomic.AtomicReference<String> result =
                        new java.util.concurrent.atomic.AtomicReference<String>(null);
                try {
                    javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
                        @Override
                        public void run() {
                            result.set(KeePassRpcPairingDialog.showAndPair());
                        }
                    });
                } catch (Exception e) {
                    throw new KeePassNotAvailableException(
                            "KeePassRPC-Pairing konnte nicht gestartet werden: " + e.getMessage());
                }
                if (result.get() != null && !result.get().trim().isEmpty()) {
                    Settings fresh = SettingsHelper.load();
                    settings.keepassRpcKey = fresh.keepassRpcKey;
                } else {
                    throw new KeePassNotAvailableException(
                            "KeePassRPC-Pairing wurde abgebrochen.\n"
                          + "Bitte starten Sie den Vorgang erneut oder konfigurieren Sie den "
                          + "SRP-Schlüssel manuell unter Einstellungen \u2192 Allgemein \u2192 Sicherheit.");
                }
            }
        }
        String entry = settings.keepassEntryTitle;
        if (entry == null || entry.trim().isEmpty()) {
            throw new KeePassNotAvailableException("KeePass-Eintragstitel ist nicht konfiguriert.");
        }
    }

    private static String rpcGetField(Settings settings, String field) {
        validateRpcConfig(settings);
        KeePassRpcClient client = new KeePassRpcClient(
                settings.getEffectiveRpcHost(), settings.keepassRpcPort,
                "MainframeMate", settings.keepassRpcKey.trim(),
                settings.getEffectiveRpcOrigin());
        try {
            client.connect();
            String entry = settings.keepassEntryTitle.trim();
            if ("password".equalsIgnoreCase(field)) {
                return client.getPassword(entry); // may return null
            } else {
                return client.getUserName(entry);
            }
        } finally {
            client.close();
        }
    }

    private static String rpcListEntries(Settings settings) {
        validateRpcConfig(settings);
        KeePassRpcClient client = new KeePassRpcClient(
                settings.getEffectiveRpcHost(), settings.keepassRpcPort,
                "MainframeMate", settings.keepassRpcKey.trim(),
                settings.getEffectiveRpcOrigin());
        try {
            client.connect();
            return client.listEntries();
        } finally {
            client.close();
        }
    }

    // ── CRUD via RPC (called from ShowPasswordsMenuCommand) ──────────────

    /**
     * Resolve user + password for an arbitrary entry by title via RPC.
     *
     * @param entryTitle the KeePass entry title (e.g. "wikipedia_de")
     * @return {@code String[]{user, password}} or {@code null} if not found
     */
    static String[] rpcGetCredentialsByTitle(String entryTitle) {
        Settings settings = SettingsHelper.load();
        validateRpcConfig(settings);
        KeePassRpcClient client = new KeePassRpcClient(
                settings.getEffectiveRpcHost(), settings.keepassRpcPort,
                "MainframeMate", settings.keepassRpcKey.trim(),
                settings.getEffectiveRpcOrigin());
        try {
            client.connect();
            String user = client.getUserName(entryTitle);
            String pass = client.getPassword(entryTitle);
            if (user == null && pass == null) return null;
            return new String[]{user != null ? user : "", pass != null ? pass : ""};
        } finally {
            client.close();
        }
    }

    /**
     * Resolve user + password for an arbitrary entry by title via PowerShell.
     *
     * @param entryTitle the KeePass entry title
     * @return {@code String[]{user, password}} or {@code null} if not found
     */
    static String[] psGetCredentialsByTitle(String entryTitle) {
        Settings settings = SettingsHelper.load();
        validateConfig(settings);
        String script = preamble(settings)
                + "$found = $false\n"
                + "foreach ($e in $db.RootGroup.GetEntries($true)) {\n"
                + "  if ($e.Strings.ReadSafe('Title') -eq '" + esc(entryTitle) + "') {\n"
                + "    Write-Output ('USER:' + $e.Strings.ReadSafe('UserName'))\n"
                + "    Write-Output ('PASS:' + $e.Strings.ReadSafe('Password'))\n"
                + "    $found = $true; break\n"
                + "  }\n"
                + "}\n"
                + "if (-not $found) { Write-Output 'NOT_FOUND' }\n"
                + "$db.Close()";
        String result = execPowerShell(script).trim();
        if (result.contains("NOT_FOUND")) return null;
        String user = "";
        String pass = "";
        for (String line : result.split("\\n")) {
            line = line.trim();
            if (line.startsWith("USER:")) user = line.substring(5);
            else if (line.startsWith("PASS:")) pass = line.substring(5);
        }
        return new String[]{user, pass};
    }

    /**
     * Create a new entry in KeePass via RPC.
     */
    static void rpcAddEntry(String title, String userName, String password, String url,
                            String displayName, String category,
                            boolean requiresLogin, boolean useProxy, boolean autoIndex,
                            String certAlias) {
        Settings settings = SettingsHelper.load();
        validateRpcConfig(settings);
        KeePassRpcClient client = new KeePassRpcClient(
                settings.getEffectiveRpcHost(), settings.keepassRpcPort,
                "MainframeMate", settings.keepassRpcKey.trim(),
                settings.getEffectiveRpcOrigin());
        try {
            client.connect();
            client.addLogin(title, userName, password, url, displayName, category,
                    requiresLogin, useProxy, autoIndex, certAlias);
        } finally {
            client.close();
        }
    }

    /**
     * Update an existing entry in KeePass via RPC.
     */
    static void rpcUpdateEntry(String title, String userName, String password, String url,
                               String displayName, String category,
                               boolean requiresLogin, boolean useProxy, boolean autoIndex,
                               String certAlias) {
        Settings settings = SettingsHelper.load();
        validateRpcConfig(settings);
        KeePassRpcClient client = new KeePassRpcClient(
                settings.getEffectiveRpcHost(), settings.keepassRpcPort,
                "MainframeMate", settings.keepassRpcKey.trim(),
                settings.getEffectiveRpcOrigin());
        try {
            client.connect();
            client.updateLogin(title, userName, password, url, displayName, category,
                    requiresLogin, useProxy, autoIndex, certAlias);
        } finally {
            client.close();
        }
    }

    /**
     * Remove an entry from KeePass via RPC.
     * If {@code uniqueID} is empty/null, the entry is looked up by title first.
     *
     * @param title    entry title (used for lookup when uniqueID is missing)
     * @param uniqueID entry unique ID (may be {@code null} or empty)
     */
    static void rpcRemoveEntry(String title, String uniqueID) {
        Settings settings = SettingsHelper.load();
        validateRpcConfig(settings);
        KeePassRpcClient client = new KeePassRpcClient(
                settings.getEffectiveRpcHost(), settings.keepassRpcPort,
                "MainframeMate", settings.keepassRpcKey.trim(),
                settings.getEffectiveRpcOrigin());
        try {
            client.connect();
            String resolvedId = (uniqueID != null) ? uniqueID.trim() : "";
            if (resolvedId.isEmpty() && title != null && !title.trim().isEmpty()) {
                String looked = client.findUniqueIdByTitle(title.trim());
                if (looked != null) {
                    resolvedId = looked;
                }
            }
            if (resolvedId.isEmpty()) {
                throw new KeePassNotAvailableException(
                        "Eintrag \"" + title + "\" konnte in KeePass nicht gefunden werden (uniqueID leer).");
            }
            client.removeEntry(resolvedId);
        } finally {
            client.close();
        }
    }

    /**
     * Create a new entry in KeePass via PowerShell.
     */
    static void psAddEntry(String title, String userName, String password, String url,
                           String displayName, String category,
                           boolean requiresLogin, boolean useProxy, boolean autoIndex,
                           String certAlias) {
        Settings settings = SettingsHelper.load();
        validateConfig(settings);
        createEntry(settings, title, userName, password, url,
                displayName, category, requiresLogin, useProxy, autoIndex, certAlias);
    }

    /**
     * Update an existing entry in KeePass via PowerShell.
     * If the entry does not exist, it is created.
     */
    static void psUpdateEntry(String title, String userName, String password, String url,
                              String displayName, String category,
                              boolean requiresLogin, boolean useProxy, boolean autoIndex,
                              String certAlias) {
        Settings settings = SettingsHelper.load();
        validateConfig(settings);

        // Build inline PS snippets for MM_* Advanced String Fields
        String mmUpdate = buildMmUpdateScript(displayName, category, requiresLogin, useProxy, autoIndex, certAlias);
        String mmCreate = buildMmCreateScript(displayName, category, requiresLogin, useProxy, autoIndex, certAlias);

        String urlLine = (url != null && !url.isEmpty())
                ? "    $e.Strings.Set('URL', (New-Object KeePassLib.Security.ProtectedString($false, '" + esc(url) + "')))\n"
                : "";
        String urlCreateLine = (url != null && !url.isEmpty())
                ? "  $ne.Strings.Set('URL', (New-Object KeePassLib.Security.ProtectedString($false, '" + esc(url) + "')))\n"
                : "";

        String script = preamble(settings)
                + "$found = $false\n"
                + "foreach ($e in $db.RootGroup.GetEntries($true)) {\n"
                + "  if ($e.Strings.ReadSafe('Title') -eq '" + esc(title) + "') {\n"
                + "    $e.Strings.Set('UserName', (New-Object KeePassLib.Security.ProtectedString($false, '" + esc(userName) + "')))\n"
                + "    $e.Strings.Set('Password', (New-Object KeePassLib.Security.ProtectedString($true, '" + esc(password) + "')))\n"
                + urlLine
                + mmUpdate
                + "    $found = $true; break\n"
                + "  }\n"
                + "}\n"
                + "if (-not $found) {\n"
                + "  $ne = New-Object KeePassLib.PwEntry($db.RootGroup, $true, $true)\n"
                + "  $ne.Strings.Set('Title', (New-Object KeePassLib.Security.ProtectedString($true, '" + esc(title) + "')))\n"
                + "  $ne.Strings.Set('UserName', (New-Object KeePassLib.Security.ProtectedString($false, '" + esc(userName) + "')))\n"
                + "  $ne.Strings.Set('Password', (New-Object KeePassLib.Security.ProtectedString($true, '" + esc(password) + "')))\n"
                + urlCreateLine
                + mmCreate
                + "  $db.RootGroup.AddEntry($ne, $true)\n"
                + "}\n"
                + "$db.Save((New-Object KeePassLib.Interfaces.NullStatusLogger))\n"
                + "$db.Close()\n"
                + "Write-Output 'OK'";

        String result = execPowerShell(script).trim();
        if (!result.contains("OK")) {
            throw new KeePassNotAvailableException(
                    "KeePass-Eintrag konnte nicht aktualisiert werden: " + sanitize(result));
        }
    }

    /**
     * Delete an entry from KeePass via PowerShell.
     */
    static void psRemoveEntry(String title) {
        Settings settings = SettingsHelper.load();
        validateConfig(settings);

        String script = preamble(settings)
                + "$found = $false\n"
                + "foreach ($e in $db.RootGroup.GetEntries($true)) {\n"
                + "  if ($e.Strings.ReadSafe('Title') -eq '" + esc(title) + "') {\n"
                + "    $e.ParentGroup.Entries.Remove($e) | Out-Null\n"
                + "    $found = $true; break\n"
                + "  }\n"
                + "}\n"
                + "if ($found) {\n"
                + "  $db.Save((New-Object KeePassLib.Interfaces.NullStatusLogger))\n"
                + "  Write-Output 'OK'\n"
                + "} else {\n"
                + "  Write-Output 'NOT_FOUND'\n"
                + "}\n"
                + "$db.Close()";

        String result = execPowerShell(script).trim();
        if (result.contains("NOT_FOUND")) {
            throw new KeePassNotAvailableException(
                    "KeePass-Eintrag \"" + title + "\" wurde nicht gefunden.");
        }
        if (!result.contains("OK")) {
            throw new KeePassNotAvailableException(
                    "KeePass-Eintrag konnte nicht gelöscht werden: " + sanitize(result));
        }
    }
}

