package de.bund.zrb.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Password encryption/decryption via Windows DPAPI — called through
 * {@code powershell.exe} instead of JNA.
 * <p>
 * <b>How it works:</b>
 * <ul>
 *   <li>{@code ConvertTo-SecureString -AsPlainText} + {@code ConvertFrom-SecureString}
 *       encrypts using DPAPI (bound to the current Windows user).</li>
 *   <li>{@code ConvertTo-SecureString} + {@code Marshal} decrypts back to plaintext.</li>
 * </ul>
 * <p>
 * <b>Security:</b>
 * <ul>
 *   <li>Passwords are passed via <b>STDIN only</b> — never on the command line.</li>
 *   <li>{@code -NoProfile -NonInteractive} prevents profile scripts from interfering.</li>
 *   <li>The PowerShell script is passed as {@code -EncodedCommand} (Base64-encoded UTF-16LE)
 *       to avoid quoting/escaping issues.</li>
 * </ul>
 *
 * @see <a href="https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.security/convertfrom-securestring">
 *      ConvertFrom-SecureString</a>
 */
final class PowerShellCryptoProvider {

    private static final Logger LOG = Logger.getLogger(PowerShellCryptoProvider.class.getName());

    /** Timeout for PowerShell process (seconds). */
    private static final int TIMEOUT_SECONDS = 10;

    // ── PowerShell scripts (passed via STDIN→ReadLine, result on STDOUT) ─────

    /**
     * Reads one line of plaintext from STDIN, encrypts it with DPAPI via
     * SecureString, writes the encrypted hex blob to STDOUT.
     */
    private static final String ENCRYPT_SCRIPT =
            "$ErrorActionPreference='Stop'\n"
            + "$plain = [Console]::In.ReadLine()\n"
            + "$ss = ConvertTo-SecureString $plain -AsPlainText -Force\n"
            + "$encrypted = ConvertFrom-SecureString $ss\n"
            + "Write-Output $encrypted\n";

    /**
     * Reads one line of encrypted hex blob from STDIN, decrypts it with DPAPI
     * via SecureString + Marshal, writes plaintext to STDOUT.
     */
    private static final String DECRYPT_SCRIPT =
            "$ErrorActionPreference='Stop'\n"
            + "$encrypted = [Console]::In.ReadLine()\n"
            + "$ss = ConvertTo-SecureString $encrypted\n"
            + "$bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($ss)\n"
            + "try {\n"
            + "  $plain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)\n"
            + "  Write-Output $plain\n"
            + "} finally {\n"
            + "  [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)\n"
            + "}\n";

    private PowerShellCryptoProvider() { /* utility */ }

    // ── public API ───────────────────────────────────────────────────

    /**
     * Encrypt using Windows DPAPI via PowerShell.
     *
     * @throws PowerShellBlockedException if PowerShell cannot be started or is blocked
     * @throws IllegalStateException on any other failure
     */
    static String encrypt(String plainText) {
        return runPowerShell(ENCRYPT_SCRIPT, plainText, "encrypt");
    }

    /**
     * Decrypt using Windows DPAPI via PowerShell.
     *
     * @throws PowerShellBlockedException if PowerShell cannot be started or is blocked
     * @throws IllegalStateException on any other failure
     */
    static String decrypt(String encrypted) {
        return runPowerShell(DECRYPT_SCRIPT, encrypted, "decrypt");
    }

    // ── process handling ─────────────────────────────────────────────

    private static String runPowerShell(String script, String stdinLine, String operation) {
        String encodedCommand = encodeCommand(script);
        ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-EncodedCommand", encodedCommand
        );
        pb.redirectErrorStream(false);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new PowerShellBlockedException(e);
        }

        try {
            // Write the sensitive data to STDIN (never on the command line!)
            OutputStream stdin = process.getOutputStream();
            stdin.write(stdinLine.getBytes(StandardCharsets.UTF_8));
            stdin.write('\n');
            stdin.flush();
            stdin.close();

            // Wait for completion
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException(
                        "PowerShell " + operation + " timed out after " + TIMEOUT_SECONDS + "s");
            }

            // Read STDOUT
            String stdout = readStream(process.getInputStream()).trim();

            // Read STDERR for diagnostics
            String stderr = readStream(process.getErrorStream()).trim();

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                // Sanitize: never include the plaintext in error messages
                String safeError = stderr.isEmpty() ? ("exit code " + exitCode) : sanitize(stderr);
                LOG.warning("[PS/DPAPI] " + operation + " failed: " + safeError);
                throw new IllegalStateException(
                        "PowerShell DPAPI " + operation + " failed: " + safeError);
            }

            if (stdout.isEmpty()) {
                throw new IllegalStateException(
                        "PowerShell DPAPI " + operation + " returned empty output");
            }

            return stdout;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PowerShell " + operation + " interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("PowerShell " + operation + " I/O error", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("PowerShell " + operation + " failed", e);
        } finally {
            process.destroyForcibly();
        }
    }

    /**
     * Encode a PowerShell script as Base64 UTF-16LE for {@code -EncodedCommand}.
     */
    private static String encodeCommand(String script) {
        byte[] utf16le = script.getBytes(Charset.forName("UTF-16LE"));
        return Base64.getEncoder().encodeToString(utf16le);
    }

    private static String readStream(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[1024];
        int n;
        while ((n = is.read(tmp)) != -1) {
            buf.write(tmp, 0, n);
        }
        return buf.toString("UTF-8");
    }

    /**
     * Strip potential plaintext leaks from error messages.
     * Keeps only the first line and truncates at 200 chars.
     */
    private static String sanitize(String stderr) {
        String firstLine = stderr.contains("\n") ? stderr.substring(0, stderr.indexOf('\n')) : stderr;
        if (firstLine.length() > 200) {
            firstLine = firstLine.substring(0, 200) + "…";
        }
        return firstLine;
    }
}

