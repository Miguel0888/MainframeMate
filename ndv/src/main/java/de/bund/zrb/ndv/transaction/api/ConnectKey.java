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

    /**
     * Optional: explicit client codepage to report to the NDV server.
     * Must be a Single Byte Character Set (SBCS) name, e.g. "ISO-8859-1".
     * If not set, {@code Charset.defaultCharset().name()} is used as fallback.
     */
    public static final String CLIENT_CP = "client.codepage";

    private ConnectKey() {
    }
}

