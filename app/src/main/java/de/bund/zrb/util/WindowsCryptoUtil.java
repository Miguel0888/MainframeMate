package de.bund.zrb.util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * Pure-Java password protection using AES-256-GCM with a file-based master key.
 * <p>
 * Replaces the former JNA/DPAPI-based implementation that was blocked under
 * hardened Windows 11 (Smart App Control / AppLocker / WDAC).
 * <p>
 * Security model:
 * <ul>
 *   <li>A random 256-bit AES master key is generated on first use and stored
 *       in {@code ~/.mainframemate/.master.key}.</li>
 *   <li>Each {@link #encrypt(String)} call generates a fresh random 96-bit IV,
 *       encrypts the plaintext with AES-256-GCM, and returns
 *       {@code Base64(IV ‖ ciphertext ‖ tag)}.</li>
 *   <li>An attacker needs access to <em>both</em> the settings file and the
 *       master key file to recover passwords.</li>
 * </ul>
 * <p>
 * <b>Note:</b> Passwords encrypted with the old DPAPI/JNA scheme cannot be
 * decrypted by this class. Users will need to re-enter their passwords once
 * after the migration.
 */
public class WindowsCryptoUtil {

    private static final Logger LOG = Logger.getLogger(WindowsCryptoUtil.class.getName());

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_SIZE_BITS = 256;
    private static final int IV_SIZE_BYTES = 12;   // 96 bits — recommended for GCM
    private static final int TAG_SIZE_BITS = 128;

    private static final String APP_DIR = ".mainframemate";
    private static final String KEY_FILE = ".master.key";

    /** Lazy-initialized master key (thread-safe via holder idiom). */
    private static final class KeyHolder {
        static final SecretKey MASTER_KEY = loadOrCreateMasterKey();
    }

    // ── public API (unchanged signatures) ────────────────────────────

    /**
     * Encrypt a plaintext string with AES-256-GCM.
     *
     * @param plainText the secret to protect
     * @return Base64-encoded string containing {@code IV ‖ ciphertext ‖ tag}
     * @throws IllegalStateException if encryption fails
     */
    public static String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_SIZE_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, KeyHolder.MASTER_KEY,
                    new GCMParameterSpec(TAG_SIZE_BITS, iv));

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Wire format: IV (12 bytes) ‖ ciphertext+tag
            byte[] result = new byte[IV_SIZE_BYTES + cipherText.length];
            System.arraycopy(iv, 0, result, 0, IV_SIZE_BYTES);
            System.arraycopy(cipherText, 0, result, IV_SIZE_BYTES, cipherText.length);

            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new IllegalStateException("Password encryption failed", e);
        }
    }

    /**
     * Decrypt a Base64-encoded ciphertext that was produced by {@link #encrypt(String)}.
     *
     * @param base64 the encoded ciphertext
     * @return the original plaintext
     * @throws IllegalStateException if decryption fails (wrong key, tampered data, or
     *                               data encrypted with the old DPAPI scheme)
     */
    public static String decrypt(String base64) {
        try {
            byte[] data = Base64.getDecoder().decode(base64);
            if (data.length < IV_SIZE_BYTES + 1) {
                throw new IllegalStateException("Encrypted payload too short — "
                        + "possibly encrypted with the old DPAPI scheme. "
                        + "Please re-enter your password.");
            }

            byte[] iv = new byte[IV_SIZE_BYTES];
            System.arraycopy(data, 0, iv, 0, IV_SIZE_BYTES);

            byte[] cipherText = new byte[data.length - IV_SIZE_BYTES];
            System.arraycopy(data, IV_SIZE_BYTES, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, KeyHolder.MASTER_KEY,
                    new GCMParameterSpec(TAG_SIZE_BITS, iv));

            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Password decryption failed — "
                    + "the stored password may have been encrypted with a different key "
                    + "or the old DPAPI scheme. Please re-enter your password.", e);
        }
    }

    // ── master-key management ────────────────────────────────────────

    private static SecretKey loadOrCreateMasterKey() {
        File keyFile = getMasterKeyFile();
        if (keyFile.exists()) {
            return loadKeyFromFile(keyFile);
        }
        return generateAndStoreKey(keyFile);
    }

    private static SecretKey loadKeyFromFile(File keyFile) {
        try (FileInputStream fis = new FileInputStream(keyFile)) {
            byte[] keyBytes = new byte[KEY_SIZE_BITS / 8];
            int read = fis.read(keyBytes);
            if (read != keyBytes.length) {
                LOG.warning("Master key file has unexpected size (" + read
                        + " bytes). Regenerating key.");
                return generateAndStoreKey(keyFile);
            }
            return new SecretKeySpec(keyBytes, "AES");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read master key from " + keyFile, e);
        }
    }

    private static SecretKey generateAndStoreKey(File keyFile) {
        try {
            // Ensure parent directory exists
            File parentDir = keyFile.getParentFile();
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                throw new IOException("Cannot create directory " + parentDir);
            }

            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(KEY_SIZE_BITS, new SecureRandom());
            SecretKey key = keyGen.generateKey();

            try (FileOutputStream fos = new FileOutputStream(keyFile)) {
                fos.write(key.getEncoded());
            }

            LOG.info("Generated new master key: " + keyFile.getAbsolutePath());
            return key;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot generate/store master key at " + keyFile, e);
        }
    }

    private static File getMasterKeyFile() {
        String home = System.getProperty("user.home");
        return new File(new File(home, APP_DIR), KEY_FILE);
    }
}
