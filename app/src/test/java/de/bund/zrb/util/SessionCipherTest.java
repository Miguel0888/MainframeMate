package de.bund.zrb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionCipherTest {

    @Test
    void encryptDecryptRoundTrip() {
        String plain = "TopSecret123!";
        String encrypted = SessionCipher.encrypt(plain);
        assertNotNull(encrypted);
        assertNotEquals(plain, encrypted);
        assertEquals(plain, SessionCipher.decrypt(encrypted));
    }

    @Test
    void differentCiphertextEachTime() {
        String plain = "password";
        String enc1 = SessionCipher.encrypt(plain);
        String enc2 = SessionCipher.encrypt(plain);
        assertNotEquals(enc1, enc2, "Each encryption must produce unique ciphertext (random IV)");
        assertEquals(plain, SessionCipher.decrypt(enc1));
        assertEquals(plain, SessionCipher.decrypt(enc2));
    }

    @Test
    void emptyStringRoundTrip() {
        assertEquals("", SessionCipher.decrypt(SessionCipher.encrypt("")));
    }

    @Test
    void unicodeRoundTrip() {
        String unicode = "Pässwörd™ \u00e9\u00e8\u00ea 中文";
        assertEquals(unicode, SessionCipher.decrypt(SessionCipher.encrypt(unicode)));
    }

    @Test
    void encryptRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> SessionCipher.encrypt(null));
    }

    @Test
    void decryptRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> SessionCipher.decrypt(null));
    }

    @Test
    void decryptRejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> SessionCipher.decrypt(""));
    }

    @Test
    void decryptRejectsTampered() {
        String encrypted = SessionCipher.encrypt("test");
        // Flip a character in the middle
        char[] chars = encrypted.toCharArray();
        chars[chars.length / 2] ^= 1;
        String tampered = new String(chars);
        assertThrows(IllegalStateException.class, () -> SessionCipher.decrypt(tampered));
    }
}

