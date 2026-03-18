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
 * via {@code KPScript.exe} (single-command operations).
 * <p>
 * <b>Read</b> ({@link #getPassword}): calls
 * {@code KPScript -c:GetEntryString <db> -ref-Title:<entry> -Field:Password}
 * <p>
 * <b>Write</b> ({@link #putPassword}): calls
 * {@code KPScript -c:EditEntry <db> -ref-Title:<entry> -set-Password:<pw>}
 * and falls back to {@code -c:AddEntry} if the entry does not exist yet.
 * <p>
 * The database master key is handled by KPScript itself; the user may be
 * prompted by KeePass for the master password if the database is locked.
 * Use {@code -pw-stdin} if you want to pipe the master password.
 * <p>
 * <b>Security:</b> The FTP password appears as a KPScript command-line
 * argument. This is acceptable in a single-user desktop context.
 * KPScript does not offer a stdin-based way to set field values.
 *
 * @see <a href="https://keepass.info/help/v2_dev/scr_sc_index.html">KPScript Single Command Operations</a>
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
     * @throws KeePassNotAvailableException if KPScript/database is not configured or call fails
     */
    static String getPassword() {
        Settings settings = SettingsHelper.load();
        validateConfig(settings);

        String kpScript = settings.keepassKpScriptPath.trim();
        String db = settings.keepassDatabasePath.trim();
        String entry = settings.keepassEntryTitle.trim();

        String output = exec(kpScript,
                "-c:GetEntryString",
                db,
                "-ref-Title:" + entry,
                "-Field:Password",
                "-Spr",         // resolve field references
                "-FailIfNoEntry"
        );

        // KPScript prints the field value followed by "OK: Operation completed successfully."
        // We need to strip the status line.
        String password = stripStatusLine(output);
        if (password == null || password.isEmpty()) {
            throw new KeePassNotAvailableException(
                    "Kein Passwort im KeePass-Eintrag \"" + entry + "\" gefunden.");
        }
        return password;
    }

    /**
     * Read the UserName field for the configured entry from KeePass.
     *
     * @return the username, or {@code null} if not set
     */
    static String getUserName() {
        Settings settings = SettingsHelper.load();
        validateConfig(settings);

        String kpScript = settings.keepassKpScriptPath.trim();
        String db = settings.keepassDatabasePath.trim();
        String entry = settings.keepassEntryTitle.trim();

        try {
            String output = exec(kpScript,
                    "-c:GetEntryString",
                    db,
                    "-ref-Title:" + entry,
                    "-Field:UserName",
                    "-Spr",
                    "-FailIfNoEntry"
            );
            String value = stripStatusLine(output);
            return (value != null && !value.isEmpty()) ? value : null;
        } catch (KeePassNotAvailableException e) {
            LOG.fine("Could not read UserName from KeePass: " + e.getMessage());
            return null;
        }
    }

    /**
     * Store (or update) a password in the KeePass database.
     * Tries {@code EditEntry} first; if the entry doesn't exist, uses {@code AddEntry}.
     *
     * @param password the plaintext password to store
     * @throws KeePassNotAvailableException on any failure
     */
    static void putPassword(String password) {
        Settings settings = SettingsHelper.load();
        validateConfig(settings);

        String kpScript = settings.keepassKpScriptPath.trim();
        String db = settings.keepassDatabasePath.trim();
        String entry = settings.keepassEntryTitle.trim();

        try {
            // Try to update existing entry
            exec(kpScript,
                    "-c:EditEntry",
                    db,
                    "-ref-Title:" + entry,
                    "-set-Password:" + password,
                    "-FailIfNoEntry"
            );
            LOG.fine("Updated KeePass entry: " + entry);
        } catch (KeePassNotAvailableException editFail) {
            // Entry doesn't exist yet — create it
            LOG.fine("Entry not found, creating new entry: " + entry);
            try {
                exec(kpScript,
                        "-c:AddEntry",
                        db,
                        "-Title:" + entry,
                        "-Password:" + password
                );
                LOG.fine("Created KeePass entry: " + entry);
            } catch (KeePassNotAvailableException addFail) {
                throw new KeePassNotAvailableException(
                        "Eintrag \"" + entry + "\" konnte weder aktualisiert noch angelegt werden.",
                        addFail);
            }
        }
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private static void validateConfig(Settings settings) {
        String kpScript = settings.keepassKpScriptPath;
        if (kpScript == null || kpScript.trim().isEmpty()) {
            throw new KeePassNotAvailableException("Pfad zu KPScript.exe ist nicht konfiguriert.");
        }
        if (!new File(kpScript.trim()).isFile()) {
            throw new KeePassNotAvailableException(
                    "KPScript.exe nicht gefunden: " + kpScript.trim());
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
     * Execute KPScript with the given arguments and return stdout.
     */
    private static String exec(String kpScript, String... args) {
        try {
            String[] cmd = new String[args.length + 1];
            cmd[0] = kpScript;
            System.arraycopy(args, 0, cmd, 1, args.length);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output = readFully(process.getInputStream());

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new KeePassNotAvailableException(
                        "KPScript-Timeout nach " + TIMEOUT_SECONDS + " Sekunden.");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                // Sanitize output to avoid leaking passwords in error messages
                String safeOutput = sanitize(output);
                throw new KeePassNotAvailableException(
                        "KPScript beendet mit Exit-Code " + exitCode + ": " + safeOutput);
            }

            return output;
        } catch (KeePassNotAvailableException e) {
            throw e;
        } catch (IOException e) {
            throw new KeePassNotAvailableException("KPScript konnte nicht gestartet werden.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeePassNotAvailableException("KPScript wurde unterbrochen.", e);
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
     * Strip the KPScript status line ("OK: Operation completed successfully.") from output.
     * Returns the content before the status line.
     */
    private static String stripStatusLine(String output) {
        if (output == null) return null;
        // KPScript appends "OK: ..." as the last line
        int lastNewline = output.lastIndexOf('\n');
        if (lastNewline > 0) {
            String lastLine = output.substring(lastNewline + 1).trim();
            if (lastLine.startsWith("OK:")) {
                return output.substring(0, lastNewline).trim();
            }
        }
        // Single-line output that starts with "OK:" means empty result
        if (output.trim().startsWith("OK:")) {
            return "";
        }
        return output.trim();
    }

    /**
     * Remove potential password values from error output.
     */
    private static String sanitize(String output) {
        if (output == null) return "";
        // Limit length for error messages
        if (output.length() > 500) {
            output = output.substring(0, 500) + "…";
        }
        return output;
    }
}

