package de.bund.zrb.util;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central facade for password encryption/decryption.
 * <p>
 * Delegates to the provider selected in
 * {@link Settings#passwordMethod} (Einstellungen → Allgemein → Sicherheit):
 * <ul>
 *   <li>{@link PasswordMethod#WINDOWS_DPAPI} — Windows DPAPI via JNA (default)</li>
 *   <li>{@link PasswordMethod#POWERSHELL_DPAPI} — Windows DPAPI via PowerShell (no JNA)</li>
 *   <li>{@link PasswordMethod#JAVA_AES} — pure-Java AES-256-GCM with file-based key</li>
 * </ul>
 * <p>
 * When the DPAPI provider fails because JNA is blocked by the OS, a
 * {@link JnaBlockedException} is thrown that tells the user to switch to
 * the Java AES variant in the settings.
 */
public class WindowsCryptoUtil {

    private static final Logger LOG = Logger.getLogger(WindowsCryptoUtil.class.getName());

    private WindowsCryptoUtil() { /* utility */ }

    /**
     * Encrypt a plaintext password using the currently configured method.
     *
     * @param plainText the secret to protect
     * @return opaque Base64-encoded ciphertext
     * @throws JnaBlockedException if DPAPI is selected but JNA is blocked
     * @throws IllegalStateException on any other encryption failure
     */
    public static String encrypt(String plainText) {
        PasswordMethod method = getMethod();
        LOG.fine("Encrypting with method: " + method);

        switch (method) {
            case WINDOWS_DPAPI:
                return DpapiCryptoProvider.encrypt(plainText);
            case POWERSHELL_DPAPI:
                return PowerShellCryptoProvider.encrypt(plainText);
            case JAVA_AES:
                return AesCryptoProvider.encrypt(plainText);
            default:
                throw new IllegalStateException("Unknown password method: " + method);
        }
    }

    /**
     * Decrypt a ciphertext produced by {@link #encrypt(String)}.
     *
     * @param base64 the encoded ciphertext
     * @return the original plaintext
     * @throws JnaBlockedException if DPAPI is selected but JNA is blocked
     * @throws PowerShellBlockedException if PowerShell DPAPI is selected but PS is blocked
     * @throws IllegalStateException on any other decryption failure
     */
    public static String decrypt(String base64) {
        PasswordMethod method = getMethod();
        LOG.fine("Decrypting with method: " + method);

        switch (method) {
            case WINDOWS_DPAPI:
                return DpapiCryptoProvider.decrypt(base64);
            case POWERSHELL_DPAPI:
                return PowerShellCryptoProvider.decrypt(base64);
            case JAVA_AES:
                return AesCryptoProvider.decrypt(base64);
            default:
                throw new IllegalStateException("Unknown password method: " + method);
        }
    }

    /**
     * Reads the configured password method from settings.
     * Falls back to {@link PasswordMethod#WINDOWS_DPAPI} if not set or invalid.
     */
    private static PasswordMethod getMethod() {
        try {
            Settings settings = SettingsHelper.load();
            if (settings.passwordMethod != null && !settings.passwordMethod.isEmpty()) {
                return PasswordMethod.valueOf(settings.passwordMethod);
            }
        } catch (IllegalArgumentException e) {
            LOG.log(Level.WARNING, "Invalid passwordMethod in settings — falling back to WINDOWS_DPAPI", e);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not read passwordMethod from settings — falling back to WINDOWS_DPAPI", e);
        }
        return PasswordMethod.WINDOWS_DPAPI;
    }
}
