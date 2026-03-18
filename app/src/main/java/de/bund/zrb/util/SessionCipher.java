package de.bund.zrb.util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Fast, pure-Java AES-256-GCM cipher for in-memory password encryption.
 * <p>
 * Uses a random key generated once per JVM session (never persisted).
 * This avoids any dependency on JNA, PowerShell, or file-based keys
 * while still ensuring passwords are not stored as plaintext in the heap.
 * <p>
 * NOT suitable for persistent storage — use {@link WindowsCryptoUtil} for that.
 */
public final class SessionCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    /** Session-random key — lives only in memory, never written to disk. */
    private static final SecretKey SESSION_KEY = generateSessionKey();

    private static final SecureRandom RANDOM = new SecureRandom();

    private SessionCipher() {}

    private static SecretKey generateSessionKey() {
        try {
            KeyGenerator gen = KeyGenerator.getInstance("AES");
            gen.init(256, new SecureRandom());
            return gen.generateKey();
        } catch (Exception e) {
            throw new IllegalStateException("AES-256 not available", e);
        }
    }

    /**
     * Encrypt a plaintext string for in-memory caching.
     *
     * @return Base64-encoded {@code IV || ciphertext || tag}
     */
    public static String encrypt(String plainText) {
        if (plainText == null) {
            throw new IllegalArgumentException("plainText must not be null");
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, SESSION_KEY, new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] cipherText = cipher.doFinal(plainText.getBytes("UTF-8"));

            byte[] out = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(cipherText, 0, out, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("SessionCipher encrypt failed", e);
        }
    }

    /**
     * Decrypt a string that was encrypted with {@link #encrypt(String)} in this session.
     *
     * @return the original plaintext
     * @throws IllegalStateException if decryption fails (e.g. from a previous session)
     */
    public static String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            throw new IllegalArgumentException("encrypted must not be null or empty");
        }
        try {
            byte[] raw = Base64.getDecoder().decode(encrypted);
            if (raw.length < GCM_IV_BYTES + 1) {
                throw new IllegalStateException("Ciphertext too short");
            }

            byte[] iv = new byte[GCM_IV_BYTES];
            System.arraycopy(raw, 0, iv, 0, GCM_IV_BYTES);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, SESSION_KEY, new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] plainBytes = cipher.doFinal(raw, GCM_IV_BYTES, raw.length - GCM_IV_BYTES);
            return new String(plainBytes, "UTF-8");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("SessionCipher decrypt failed", e);
        }
    }
}

