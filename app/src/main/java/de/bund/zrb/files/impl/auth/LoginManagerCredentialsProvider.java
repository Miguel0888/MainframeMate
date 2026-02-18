package de.bund.zrb.files.impl.auth;

import de.bund.zrb.files.auth.ConnectionId;
import de.bund.zrb.files.auth.Credentials;
import de.bund.zrb.files.auth.CredentialsProvider;
import de.bund.zrb.login.LoginManager;

import java.util.Optional;

/**
 * Adapter that resolves credentials via the existing LoginManager.
 *
 * NOTE: It may still prompt the user depending on LoginManager configuration.
 * The important part for fileservice-003 is: infrastructure code only talks to this provider.
 */
public class LoginManagerCredentialsProvider implements CredentialsProvider {

    private final LoginManager loginManager;

    public LoginManagerCredentialsProvider(LoginManager loginManager) {
        this.loginManager = loginManager == null ? LoginManager.getInstance() : loginManager;
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

        String password = loginManager.getPassword(host, user);
        if (password == null) {
            return Optional.empty();
        }

        return Optional.of(new Credentials(host, user, password));
    }
}

