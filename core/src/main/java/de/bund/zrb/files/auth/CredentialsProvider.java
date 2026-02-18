package de.bund.zrb.files.auth;

import java.util.Optional;

public interface CredentialsProvider {

    Optional<Credentials> resolve(String host, String username);
}

