package com.softwareag.naturalone.natural.paltransactions.external;

/**
 * Verbindungsfehler-Ausnahme bei PAL-Connect.
 * Wird geworfen, wenn die Anmeldung am NDV-Server fehlschlägt (z.B. falsches Passwort).
 */
public class PalConnectResultException extends PalResultException {

    public PalConnectResultException() {
        super();
    }

    public PalConnectResultException(String message) {
        super(message);
    }

    public PalConnectResultException(String message, Throwable cause) {
        super(message, cause);
    }

    public PalConnectResultException(int resultCode, String message) {
        super(resultCode, message);
    }
}

