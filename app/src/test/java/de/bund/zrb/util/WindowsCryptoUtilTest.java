package de.bund.zrb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the pure-Java AES-256-GCM password protection in {@link WindowsCryptoUtil}.
 */
class WindowsCryptoUtilTest {

    @Test
    void encryptAndDecryptRoundTrip() {
        String secret = "myS3cur€Pässw0rd!";
        String encrypted = WindowsCryptoUtil.encrypt(secret);
        assertNotNull(encrypted);
        assertNotEquals(secret, encrypted, "encrypted text must differ from plaintext");

        String decrypted = WindowsCryptoUtil.decrypt(encrypted);
        assertEquals(secret, decrypted);
    }

    @Test
    void encryptProducesDifferentCiphertextEachTime() {
        String secret = "sameInput";
        String enc1 = WindowsCryptoUtil.encrypt(secret);
        String enc2 = WindowsCryptoUtil.encrypt(secret);

        // Each call uses a random IV, so ciphertexts must differ
        assertNotEquals(enc1, enc2, "two encryptions of the same plaintext must produce different ciphertext (random IV)");

        // Both must still decrypt to the same value
        assertEquals(secret, WindowsCryptoUtil.decrypt(enc1));
        assertEquals(secret, WindowsCryptoUtil.decrypt(enc2));
    }

    @Test
    void decryptWithTamperedDataThrows() {
        String encrypted = WindowsCryptoUtil.encrypt("test");
        // Flip a character in the middle of the Base64 string
        char[] chars = encrypted.toCharArray();
        int mid = chars.length / 2;
        chars[mid] = (chars[mid] == 'A') ? 'B' : 'A';
        String tampered = new String(chars);

        assertThrows(IllegalStateException.class, () -> WindowsCryptoUtil.decrypt(tampered));
    }

    @Test
    void decryptWithTooShortPayloadThrows() {
        // A very short Base64 payload (less than 13 bytes decoded) should fail clearly
        String tooShort = java.util.Base64.getEncoder().encodeToString(new byte[5]);
        assertThrows(IllegalStateException.class, () -> WindowsCryptoUtil.decrypt(tooShort));
    }

    @Test
    void emptyStringEncryptDecrypt() {
        String encrypted = WindowsCryptoUtil.encrypt("");
        String decrypted = WindowsCryptoUtil.decrypt(encrypted);
        assertEquals("", decrypted);
    }

    @Test
    void unicodeEncryptDecrypt() {
        String unicode = "日本語パスワード 🔐 Ñoño";
        String encrypted = WindowsCryptoUtil.encrypt(unicode);
        assertEquals(unicode, WindowsCryptoUtil.decrypt(encrypted));
    }

    @Test
    void longPasswordEncryptDecrypt() {
        // Test with a very long password (1000 characters)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append((char) ('A' + (i % 26)));
        }
        String longPw = sb.toString();
        String encrypted = WindowsCryptoUtil.encrypt(longPw);
        assertEquals(longPw, WindowsCryptoUtil.decrypt(encrypted));
    }

    @Test
    void pipeDelimitedCredentialRoundTrip() {
        // This is the format used by WikiSettingsPanel: "user|password"
        String credentials = "myUser|myP@ssw0rd!";
        String encrypted = WindowsCryptoUtil.encrypt(credentials);
        String decrypted = WindowsCryptoUtil.decrypt(encrypted);
        assertEquals(credentials, decrypted);

        int sep = decrypted.indexOf('|');
        assertEquals("myUser", decrypted.substring(0, sep));
        assertEquals("myP@ssw0rd!", decrypted.substring(sep + 1));
    }
}

