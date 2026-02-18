package de.bund.zrb.files.impl.auth;

import de.bund.zrb.files.auth.ConnectionId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginManagerCredentialsProviderTest {

    @Test
    void resolveReturnsEmptyWhenMissingHostOrUser() {
        LoginManagerCredentialsProvider provider = new LoginManagerCredentialsProvider((host, user) -> null);
        assertFalse(provider.resolve(new ConnectionId("ftp", "", "u")).isPresent());
        assertFalse(provider.resolve(new ConnectionId("ftp", "h", "")).isPresent());
    }

    @Test
    void resolveUsesCachedPasswordOnly() {
        LoginManagerCredentialsProvider provider = new LoginManagerCredentialsProvider((host, user) -> "secret");
        assertTrue(provider.resolve(new ConnectionId("ftp", "host", "user")).isPresent());
    }

    @Test
    void requiresPasswordLookup() {
        assertThrows(NullPointerException.class, () -> new LoginManagerCredentialsProvider(null));
    }
}