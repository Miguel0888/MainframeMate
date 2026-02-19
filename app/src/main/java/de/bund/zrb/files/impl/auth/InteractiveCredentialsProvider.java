package de.bund.zrb.files.impl.auth;

import de.bund.zrb.files.auth.ConnectionId;
import de.bund.zrb.files.auth.Credentials;
import de.bund.zrb.files.auth.CredentialsProvider;

import java.util.Optional;

/**
 * Interactive credentials provider that can prompt the user for password.
 *
 * This provider first tries to get cached credentials, and if not available,
 * calls the interactive password lookup which may show a dialog.
 *
 * The interactive lookup is performed on the EDT if needed.
 */
public class InteractiveCredentialsProvider implements CredentialsProvider {

    /**
     * Interactive password lookup that may show a dialog to the user.
     * This should call LoginManager.getPassword() which handles UI dialogs.
     */
    public interface InteractivePasswordLookup {
        /**
         * Get password for the given host/username.
         * May show a dialog if password is not cached.
         *
         * @param host the host
         * @param username the username
         * @return the password, or null if user cancelled
         */
        String getPassword(String host, String username);
    }

    private final InteractivePasswordLookup passwordLookup;

    public InteractiveCredentialsProvider(InteractivePasswordLookup passwordLookup) {
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

        // Use interactive password lookup (may show dialog)
        String password = passwordLookup.getPassword(host, user);
        if (password == null) {
            return Optional.empty();
        }

        return Optional.of(new Credentials(host, user, password));
    }
}

