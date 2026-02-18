package de.bund.zrb.files.impl.auth;

import de.bund.zrb.files.auth.ConnectionId;
import de.bund.zrb.files.auth.Credentials;
import de.bund.zrb.files.auth.CredentialsProvider;

import java.util.Optional;

/**
 * Adapter that resolves credentials via a non-interactive PasswordLookup function.
 *
 * This provider is strictly non-interactive: it only reads cached/stored credentials.
 */
public class LoginManagerCredentialsProvider implements CredentialsProvider {

    public interface PasswordLookup {
        String getCachedPassword(String host, String username);
    }

    private final PasswordLookup passwordLookup;

    public LoginManagerCredentialsProvider(PasswordLookup passwordLookup) {
        this.passwordLookup = java.util.Objects.requireNonNull(passwordLookup, "passwordLookup");
    }

    @Override
    public Optional<Credentials> resolve(ConnectionId connectionId) {
        if (connectionId == null) {
            return Optional.empty();
        }

        String host = connectionId.getHost();
        String user = connectionId.getUsername();
        if (host == null || host.trim().isEmpty() || user == null || user.trim().isEmpty()) {
            return Optional.empty();
        }

        String password = passwordLookup.getCachedPassword(host, user);
        if (password == null) {
            return Optional.empty();
        }

        return Optional.of(new Credentials(host, user, password));
    }
}
