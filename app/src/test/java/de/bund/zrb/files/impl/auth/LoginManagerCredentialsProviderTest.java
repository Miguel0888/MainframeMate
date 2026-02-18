package de.bund.zrb.files.impl.auth;
import de.bund.zrb.files.auth.ConnectionId;
import de.bund.zrb.login.LoginManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
class LoginManagerCredentialsProviderTest {
    @Test
    void resolveReturnsEmptyWhenMissingHostOrUser() {
        LoginManagerCredentialsProvider provider = new LoginManagerCredentialsProvider(LoginManager.getInstance());
        assertTrue(!provider.resolve(new ConnectionId("ftp", "", "u")).isPresent());
        assertTrue(!provider.resolve(new ConnectionId("ftp", "h", "")).isPresent());
    }
}