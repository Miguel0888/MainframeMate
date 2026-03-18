package de.bund.zrb.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Password encryption/decryption via Windows DPAPI (CryptProtectData / CryptUnprotectData).
 * <p>
 * All JNA calls are wrapped in try-catch for {@link NoClassDefFoundError},
 * {@link UnsatisfiedLinkError} and {@link ExceptionInInitializerError} so that
 * callers receive a clear {@link JnaBlockedException} when the native library is
 * blocked by Windows security policies.
 */
final class DpapiCryptoProvider {

    private DpapiCryptoProvider() { /* utility */ }

    /**
     * Encrypt using Windows DPAPI via JNA.
     *
     * @throws JnaBlockedException if JNA native loading is blocked
     * @throws IllegalStateException on any other encryption failure
     */
    static String encrypt(String plainText) {
        try {
            return doEncrypt(plainText);
        } catch (NoClassDefFoundError | UnsatisfiedLinkError | ExceptionInInitializerError e) {
            throw new JnaBlockedException(e);
        }
    }

    /**
     * Decrypt using Windows DPAPI via JNA.
     *
     * @throws JnaBlockedException if JNA native loading is blocked
     * @throws IllegalStateException on any other decryption failure
     */
    static String decrypt(String base64) {
        try {
            return doDecrypt(base64);
        } catch (NoClassDefFoundError | UnsatisfiedLinkError | ExceptionInInitializerError e) {
            throw new JnaBlockedException(e);
        }
    }

    // ── actual JNA calls (isolated so the class-loading errors are catchable) ──

    private static String doEncrypt(String plainText) {
        byte[] data = plainText.getBytes(StandardCharsets.UTF_8);
        com.sun.jna.platform.win32.WinCrypt.DATA_BLOB dataIn =
                new com.sun.jna.platform.win32.WinCrypt.DATA_BLOB(data);
        com.sun.jna.platform.win32.WinCrypt.DATA_BLOB dataOut =
                new com.sun.jna.platform.win32.WinCrypt.DATA_BLOB();

        boolean success = com.sun.jna.platform.win32.Crypt32.INSTANCE
                .CryptProtectData(dataIn, null, null, null, null, 0, dataOut);
        if (!success) {
            throw new IllegalStateException("DPAPI CryptProtectData failed");
        }
        return Base64.getEncoder().encodeToString(dataOut.getData());
    }

    private static String doDecrypt(String base64) {
        byte[] encrypted = Base64.getDecoder().decode(base64);
        com.sun.jna.platform.win32.WinCrypt.DATA_BLOB dataIn =
                new com.sun.jna.platform.win32.WinCrypt.DATA_BLOB(encrypted);
        com.sun.jna.platform.win32.WinCrypt.DATA_BLOB dataOut =
                new com.sun.jna.platform.win32.WinCrypt.DATA_BLOB();

        boolean success = com.sun.jna.platform.win32.Crypt32.INSTANCE
                .CryptUnprotectData(dataIn, null, null, null, null, 0, dataOut);
        if (!success) {
            throw new IllegalStateException("DPAPI CryptUnprotectData failed");
        }
        return new String(dataOut.getData(), StandardCharsets.UTF_8);
    }
}

