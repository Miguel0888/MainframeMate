package de.bund.zrb.files.auth;

import java.util.Optional;

public interface CredentialsProvider {

    Optional<Credentials> resolve(ConnectionId connectionId);

    /**
     * Backwards compatible convenience overload.
     */
    default Optional<Credentials> resolve(String host, String username) {
        return resolve(new ConnectionId("ftp", host, username));
    }
}
