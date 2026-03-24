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
 *   <li>{@link PasswordMethod#KEEPASS} — KeePass as external secret store (default)</li>
 *   <li>{@link PasswordMethod#WINDOWS_DPAPI} — Windows DPAPI via JNA</li>
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

    /**
     * Marker stored in {@code settings.encryptedPassword} when KeePass is the
     * active method. The actual password lives in the KeePass database.
     */
    static final String KEEPASS_MARKER = "keepass:";

    private WindowsCryptoUtil() { /* utility */ }

    /**
     * Encrypt a plaintext password using the currently configured method.
     * <p>
     * For {@link PasswordMethod#KEEPASS}, the password is stored in KeePass
     * and a marker string {@code "keepass:"} is returned instead of ciphertext.
     *
     * @param plainText the secret to protect
     * @return opaque Base64-encoded ciphertext (or marker for KeePass)
     * @throws JnaBlockedException if DPAPI is selected but JNA is blocked
     * @throws KeePassNotAvailableException if KeePass is selected but PowerShell/KeePassLib fails
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
            case KEEPASS:
                KeePassProvider.putPassword(plainText);
                return KEEPASS_MARKER;
            default:
                throw new IllegalStateException("Unknown password method: " + method);
        }
    }

    /**
     * Decrypt a ciphertext produced by {@link #encrypt(String)}.
     * <p>
     * For {@link PasswordMethod#KEEPASS}, the password is read from KeePass
     * (the {@code base64} parameter is ignored — it is just a marker).
     *
     * @param base64 the encoded ciphertext (or KeePass marker)
     * @return the original plaintext
     * @throws JnaBlockedException if DPAPI is selected but JNA is blocked
     * @throws PowerShellBlockedException if PowerShell DPAPI is selected but PS is blocked
     * @throws KeePassNotAvailableException if KeePass is selected but PowerShell/KeePassLib fails
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
            case KEEPASS:
                return KeePassProvider.getPassword();
            default:
                throw new IllegalStateException("Unknown password method: " + method);
        }
    }

    /**
     * Reads the configured password method from settings.
     * Falls back to {@link PasswordMethod#KEEPASS} if not set or invalid.
     */
    private static PasswordMethod getMethod() {
        try {
            Settings settings = SettingsHelper.load();
            if (settings.passwordMethod != null && !settings.passwordMethod.isEmpty()) {
                return PasswordMethod.valueOf(settings.passwordMethod);
            }
        } catch (IllegalArgumentException e) {
            LOG.log(Level.WARNING, "Invalid passwordMethod in settings — falling back to KEEPASS", e);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not read passwordMethod from settings — falling back to KEEPASS", e);
        }
        return PasswordMethod.KEEPASS;
    }
}
