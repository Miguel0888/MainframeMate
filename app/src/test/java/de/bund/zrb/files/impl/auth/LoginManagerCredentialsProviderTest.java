package de.bund.zrb.files.impl.auth;

import de.bund.zrb.files.auth.ConnectionId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginManagerCredentialsProviderTest {

    @Test
    void resolveReturnsEmptyWhenMissingHostOrUser() {
        LoginManagerCredentialsProvider provider = new LoginManagerCredentialsProvider((host, user) -> null);
        assertTrue(!provider.resolve(new ConnectionId("ftp", "", "u")).isPresent());
        assertTrue(!provider.resolve(new ConnectionId("ftp", "h", "")).isPresent());
    }

    @Test
    void resolveUsesCachedPasswordOnly() {
        LoginManagerCredentialsProvider provider = new LoginManagerCredentialsProvider((host, user) -> "secret");
        assertTrue(provider.resolve(new ConnectionId("ftp", "host", "user")).isPresent());
    }
}