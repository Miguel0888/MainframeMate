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
 * Pure-Java password encryption/decryption using AES-256-GCM with a file-based
 * master key stored in {@code ~/.mainframemate/.master.key}.
 * <p>
 * No native dependencies — works on any platform with Java 8+.
 */
final class AesCryptoProvider {

    private static final Logger LOG = Logger.getLogger(AesCryptoProvider.class.getName());

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

    private AesCryptoProvider() { /* utility */ }

    // ── public API ───────────────────────────────────────────────────

    static String encrypt(String plainText) {
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
            throw new IllegalStateException("AES password encryption failed", e);
        }
    }

    static String decrypt(String base64) {
        try {
            byte[] data = Base64.getDecoder().decode(base64);
            if (data.length < IV_SIZE_BYTES + 1) {
                throw new IllegalStateException("Encrypted payload too short — "
                        + "possibly encrypted with a different method. "
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
            throw new IllegalStateException("AES password decryption failed — "
                    + "the stored password may have been encrypted with a different key "
                    + "or method. Please re-enter your password.", e);
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

            LOG.info("Generated new AES master key: " + keyFile.getAbsolutePath());
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

