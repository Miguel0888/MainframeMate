package de.bund.zrb.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the password encryption facade {@link WindowsCryptoUtil}
 * and both underlying providers ({@link AesCryptoProvider}, {@link DpapiCryptoProvider}).
 */
class WindowsCryptoUtilTest {

    private String originalMethod;

    @BeforeEach
    void saveOriginalSetting() {
        de.bund.zrb.model.Settings s = de.bund.zrb.helper.SettingsHelper.load();
        originalMethod = s.passwordMethod;
    }

    @AfterEach
    void restoreOriginalSetting() {
        de.bund.zrb.model.Settings s = de.bund.zrb.helper.SettingsHelper.load();
        s.passwordMethod = originalMethod;
        de.bund.zrb.helper.SettingsHelper.save(s);
    }

    private void setMethod(PasswordMethod method) {
        de.bund.zrb.model.Settings s = de.bund.zrb.helper.SettingsHelper.load();
        s.passwordMethod = method.name();
        de.bund.zrb.helper.SettingsHelper.save(s);
    }

    // ── AES provider tests ──────────────────────────────────────────

    @Test
    void aes_encryptAndDecryptRoundTrip() {
        setMethod(PasswordMethod.JAVA_AES);
        String secret = "myS3cur€Pässw0rd!";
        String encrypted = WindowsCryptoUtil.encrypt(secret);
        assertNotNull(encrypted);
        assertNotEquals(secret, encrypted);
        assertEquals(secret, WindowsCryptoUtil.decrypt(encrypted));
    }

    @Test
    void aes_encryptProducesDifferentCiphertextEachTime() {
        setMethod(PasswordMethod.JAVA_AES);
        String secret = "sameInput";
        String enc1 = WindowsCryptoUtil.encrypt(secret);
        String enc2 = WindowsCryptoUtil.encrypt(secret);
        assertNotEquals(enc1, enc2, "random IV must produce different ciphertext");
        assertEquals(secret, WindowsCryptoUtil.decrypt(enc1));
        assertEquals(secret, WindowsCryptoUtil.decrypt(enc2));
    }

    @Test
    void aes_decryptWithTamperedDataThrows() {
        setMethod(PasswordMethod.JAVA_AES);
        String encrypted = WindowsCryptoUtil.encrypt("test");
        char[] chars = encrypted.toCharArray();
        int mid = chars.length / 2;
        chars[mid] = (chars[mid] == 'A') ? 'B' : 'A';
        String tampered = new String(chars);
        assertThrows(IllegalStateException.class, () -> WindowsCryptoUtil.decrypt(tampered));
    }

    @Test
    void aes_decryptWithTooShortPayloadThrows() {
        setMethod(PasswordMethod.JAVA_AES);
        String tooShort = java.util.Base64.getEncoder().encodeToString(new byte[5]);
        assertThrows(IllegalStateException.class, () -> WindowsCryptoUtil.decrypt(tooShort));
    }

    @Test
    void aes_emptyStringRoundTrip() {
        setMethod(PasswordMethod.JAVA_AES);
        assertEquals("", WindowsCryptoUtil.decrypt(WindowsCryptoUtil.encrypt("")));
    }

    @Test
    void aes_unicodeRoundTrip() {
        setMethod(PasswordMethod.JAVA_AES);
        String unicode = "日本語パスワード 🔐 Ñoño";
        assertEquals(unicode, WindowsCryptoUtil.decrypt(WindowsCryptoUtil.encrypt(unicode)));
    }

    @Test
    void aes_longPasswordRoundTrip() {
        setMethod(PasswordMethod.JAVA_AES);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) sb.append((char) ('A' + (i % 26)));
        String longPw = sb.toString();
        assertEquals(longPw, WindowsCryptoUtil.decrypt(WindowsCryptoUtil.encrypt(longPw)));
    }

    @Test
    void aes_pipeDelimitedCredentialRoundTrip() {
        setMethod(PasswordMethod.JAVA_AES);
        String credentials = "myUser|myP@ssw0rd!";
        String decrypted = WindowsCryptoUtil.decrypt(WindowsCryptoUtil.encrypt(credentials));
        assertEquals(credentials, decrypted);
        int sep = decrypted.indexOf('|');
        assertEquals("myUser", decrypted.substring(0, sep));
        assertEquals("myP@ssw0rd!", decrypted.substring(sep + 1));
    }

    // ── DPAPI provider tests ────────────────────────────────────────

    @Test
    void dpapi_encryptAndDecryptRoundTrip() {
        setMethod(PasswordMethod.WINDOWS_DPAPI);
        try {
            String secret = "dpapiTest123!";
            String encrypted = WindowsCryptoUtil.encrypt(secret);
            assertNotNull(encrypted);
            assertNotEquals(secret, encrypted);
            assertEquals(secret, WindowsCryptoUtil.decrypt(encrypted));
        } catch (JnaBlockedException e) {
            // JNA blocked on this machine — that's the exact case we handle
            assertTrue(e.getMessage().contains("Einstellungen"),
                    "JnaBlockedException should advise switching in settings");
        }
    }

    @Test
    void dpapi_jnaBlockedExceptionHasHelpfulMessage() {
        JnaBlockedException ex = new JnaBlockedException(new UnsatisfiedLinkError("test"));
        assertTrue(ex.getMessage().contains("Einstellungen"));
        assertTrue(ex.getMessage().contains(PasswordMethod.JAVA_AES.getDisplayName()));
    }

    // ── Cross-method test ───────────────────────────────────────────

    @Test
    void methodSwitchInvalidatesOldPasswords() {
        // Encrypt with AES
        setMethod(PasswordMethod.JAVA_AES);
        String encrypted = WindowsCryptoUtil.encrypt("secret");

        // Switching to DPAPI and trying to decrypt AES ciphertext should fail
        setMethod(PasswordMethod.WINDOWS_DPAPI);
        try {
            WindowsCryptoUtil.decrypt(encrypted);
            // If DPAPI is available, it should still fail because the data was AES-encrypted
            fail("Decrypting AES ciphertext with DPAPI should fail");
        } catch (JnaBlockedException e) {
            // JNA blocked — expected on hardened systems
        } catch (IllegalStateException e) {
            // Expected — wrong format
            assertTrue(true);
        }
    }

    // ── Direct provider tests ───────────────────────────────────────

    @Test
    void aesCryptoProvider_directRoundTrip() {
        String secret = "directAesTest";
        String encrypted = AesCryptoProvider.encrypt(secret);
        assertEquals(secret, AesCryptoProvider.decrypt(encrypted));
    }

    @Test
    void dpapiCryptoProvider_throwsJnaBlockedOnFailure() {
        // If JNA works, this is a round-trip test
        // If JNA is blocked, we verify the exception type
        try {
            String encrypted = DpapiCryptoProvider.encrypt("test");
            assertEquals("test", DpapiCryptoProvider.decrypt(encrypted));
        } catch (JnaBlockedException e) {
            assertNotNull(e.getCause());
        }
    }
}
