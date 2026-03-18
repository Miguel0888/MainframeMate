package de.bund.zrb.util;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;

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
 * No separate {@code KPScript.exe} or {@code KeePassLib.dll} is required —
 * the KeePassLib namespace is compiled directly into {@code KeePass.exe}.
 * PowerShell loads it via {@code Add-Type -Path KeePass.exe}.
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
        validateConfig(settings);
        String script = buildGetFieldScript(settings, "Password");
        String result = execPowerShell(script).trim();
        if (result.isEmpty()) {
            throw new KeePassNotAvailableException(
                    "Kein Passwort im KeePass-Eintrag \"" + settings.keepassEntryTitle.trim() + "\" gefunden.");
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
        validateConfig(settings);

        String dllPath = resolveDllPath(settings);
        String dbPath = settings.keepassDatabasePath.trim();
        String entry = settings.keepassEntryTitle.trim();

        // PowerShell script: find entry → update or create, then save
        String script =
                "Add-Type -Path '" + esc(dllPath) + "'\n"
                + "$io = New-Object KeePassLib.Serialization.IOConnectionInfo\n"
                + "$io.Path = '" + esc(dbPath) + "'\n"
                + "$key = New-Object KeePassLib.Keys.CompositeKey\n"
                + "$key.AddUserKey((New-Object KeePassLib.Keys.KcpUserAccount))\n"
                + "$db = New-Object KeePassLib.PwDatabase\n"
                + "$db.Open($io, $key, (New-Object KeePassLib.Interfaces.NullStatusLogger))\n"
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
        validateConfig(settings);

        String dllPath = resolveDllPath(settings);
        String dbPath = settings.keepassDatabasePath.trim();

        String script =
                "Add-Type -Path '" + esc(dllPath) + "'\n"
                + "$io = New-Object KeePassLib.Serialization.IOConnectionInfo\n"
                + "$io.Path = '" + esc(dbPath) + "'\n"
                + "$key = New-Object KeePassLib.Keys.CompositeKey\n"
                + "$key.AddUserKey((New-Object KeePassLib.Keys.KcpUserAccount))\n"
                + "$db = New-Object KeePassLib.PwDatabase\n"
                + "$db.Open($io, $key, (New-Object KeePassLib.Interfaces.NullStatusLogger))\n"
                + "foreach ($e in $db.RootGroup.GetEntries($true)) {\n"
                + "  Write-Output (\"Title: \" + $e.Strings.ReadSafe('Title'))\n"
                + "  Write-Output (\"UserName: \" + $e.Strings.ReadSafe('UserName'))\n"
                + "  Write-Output (\"Password: \" + $e.Strings.ReadSafe('Password'))\n"
                + "  Write-Output (\"URL: \" + $e.Strings.ReadSafe('URL'))\n"
                + "  Write-Output ''\n"
                + "}\n"
                + "$db.Close()\n"
                + "Write-Output 'OK: Operation completed successfully.'";

        return execPowerShell(script);
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private static void validateConfig(Settings settings) {
        String installPath = settings.keepassInstallPath;
        if (installPath == null || installPath.trim().isEmpty()) {
            throw new KeePassNotAvailableException(
                    "KeePass-Installationsverzeichnis ist nicht konfiguriert.\n"
                    + "Bitte unter Einstellungen \u2192 Allgemein \u2192 Sicherheit den Pfad angeben.");
        }
        String exePath = resolveDllPath(settings);
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
        String dllPath = resolveDllPath(settings);
        String dbPath = settings.keepassDatabasePath.trim();
        String entry = settings.keepassEntryTitle.trim();

        return "Add-Type -Path '" + esc(dllPath) + "'\n"
                + "$io = New-Object KeePassLib.Serialization.IOConnectionInfo\n"
                + "$io.Path = '" + esc(dbPath) + "'\n"
                + "$key = New-Object KeePassLib.Keys.CompositeKey\n"
                + "$key.AddUserKey((New-Object KeePassLib.Keys.KcpUserAccount))\n"
                + "$db = New-Object KeePassLib.PwDatabase\n"
                + "$db.Open($io, $key, (New-Object KeePassLib.Interfaces.NullStatusLogger))\n"
                + "foreach ($e in $db.RootGroup.GetEntries($true)) {\n"
                + "  if ($e.Strings.ReadSafe('Title') -eq '" + esc(entry) + "') {\n"
                + "    Write-Output $e.Strings.ReadSafe('" + fieldName + "')\n"
                + "    break\n"
                + "  }\n"
                + "}\n"
                + "$db.Close()";
    }

    /**
     * Resolve the path to KeePass.exe (which contains the KeePassLib namespace)
     * from the installation directory.
     */
    private static String resolveDllPath(Settings settings) {
        String installDir = settings.keepassInstallPath.trim();
        // Handle both directory and direct KeePass.exe path
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
}

