package de.bund.zrb.ndv.transaction.api;

/**
 * Schlüsselkonstanten für die Verbindungsparameter-Map,
 * die an {@link IPalTransactions#connect(java.util.Map)} übergeben wird.
 */
public final class ConnectKey {

    public static final String HOST = "host";
    public static final String PORT = "port";
    public static final String USERID = "user id";
    public static final String PASSWORD = "password";
    public static final String NEW_PASSWORD = "new password";
    public static final String PARM = "session parameters";

    private ConnectKey() {
    }
}

