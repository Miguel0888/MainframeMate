package de.bund.zrb.net;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * End-to-end encryption for Ollama proxy communication.
 * <p>
 * Uses AES-256-GCM with PBKDF2 key derivation — the same wire format
 * as the Node.js proxy ({@code proxy_ollama.js}).
 * <p>
 * Wire format (binary):
 * <pre>
 *   [version 1B] [salt 32B] [iv 12B] [authTag 16B] [ciphertext ...]
 * </pre>
 * <p>
 * Security properties:
 * <ul>
 *   <li>Random salt (32 bytes) per message → unique key per message</li>
 *   <li>Random IV (12 bytes) per message → unique nonce per message</li>
 *   <li>GCM authentication tag (16 bytes) prevents tampering</li>
 *   <li>PBKDF2 with 600,000 iterations of SHA-256 (OWASP 2023 recommendation)</li>
 *   <li>Ciphertext indistinguishable from random data</li>
 * </ul>
 */
public final class E2ECrypto {

    private static final Logger LOG = Logger.getLogger(E2ECrypto.class.getName());

    private static final byte E2E_VERSION = 0x01;
    private static final int SALT_LENGTH = 32;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128; // 16 bytes
    private static final int TAG_LENGTH = TAG_BITS / 8;
    private static final int KEY_LENGTH = 256; // AES-256

    private static final int PBKDF2_ITERATIONS = 600_000;
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";

    private static final SecureRandom RANDOM = new SecureRandom();

    private E2ECrypto() { /* utility class */ }

    /**
     * Encrypt plaintext bytes with AES-256-GCM.
     *
     * @param plaintext the data to encrypt
     * @param password  the shared secret (never transmitted over the network)
     * @return the wire-format encrypted payload
     */
    public static byte[] encrypt(byte[] plaintext, String password) throws Exception {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);

        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);

        byte[] key = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BITS, iv));

        byte[] ciphertextWithTag = cipher.doFinal(plaintext);

        // Java's GCM appends the auth tag at the end of the ciphertext.
        // We need to separate them for the wire format.
        int ciphertextLen = ciphertextWithTag.length - TAG_LENGTH;
        byte[] ciphertext = new byte[ciphertextLen];
        byte[] authTag = new byte[TAG_LENGTH];
        System.arraycopy(ciphertextWithTag, 0, ciphertext, 0, ciphertextLen);
        System.arraycopy(ciphertextWithTag, ciphertextLen, authTag, 0, TAG_LENGTH);

        // Pack: [version][salt][iv][authTag][ciphertext]
        ByteBuffer buf = ByteBuffer.allocate(1 + SALT_LENGTH + IV_LENGTH + TAG_LENGTH + ciphertextLen);
        buf.put(E2E_VERSION);
        buf.put(salt);
        buf.put(iv);
        buf.put(authTag);
        buf.put(ciphertext);
        return buf.array();
    }

    /**
     * Decrypt a wire-format payload.
     *
     * @param payload  the encrypted payload
     * @param password the shared secret
     * @return decrypted plaintext bytes
     * @throws Exception on decryption failure (wrong password, tampered data, etc.)
     */
    public static byte[] decrypt(byte[] payload, String password) throws Exception {
        if (payload.length < 1 + SALT_LENGTH + IV_LENGTH + TAG_LENGTH) {
            throw new IllegalArgumentException("E2E payload too short (" + payload.length + " bytes)");
        }

        ByteBuffer buf = ByteBuffer.wrap(payload);

        byte version = buf.get();
        if (version != E2E_VERSION) {
            throw new IllegalArgumentException("Unsupported E2E version: " + version);
        }

        byte[] salt = new byte[SALT_LENGTH];
        buf.get(salt);

        byte[] iv = new byte[IV_LENGTH];
        buf.get(iv);

        byte[] authTag = new byte[TAG_LENGTH];
        buf.get(authTag);

        byte[] ciphertext = new byte[buf.remaining()];
        buf.get(ciphertext);

        byte[] key = deriveKey(password, salt);

        // Java GCM expects [ciphertext + authTag] concatenated
        byte[] ciphertextWithTag = new byte[ciphertext.length + TAG_LENGTH];
        System.arraycopy(ciphertext, 0, ciphertextWithTag, 0, ciphertext.length);
        System.arraycopy(authTag, 0, ciphertextWithTag, ciphertext.length, TAG_LENGTH);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BITS, iv));

        return cipher.doFinal(ciphertextWithTag);
    }

    /**
     * Derive a 256-bit AES key from password + salt using PBKDF2-HMAC-SHA256.
     */
    private static byte[] deriveKey(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * Encode binary data to Base64 string (for debug/logging).
     */
    public static String toBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Decode Base64 string to binary data.
     */
    public static byte[] fromBase64(String base64) {
        return Base64.getDecoder().decode(base64);
    }
}

