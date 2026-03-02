package com.softwareag.naturalone.natural.paltransactions.external;

/**
 * Kompilierungs-Ergebnis-Ausnahme.
 * Wird geworfen wenn die Kompilierung eines Natural-Objekts auf dem Server fehlschlägt.
 */
public class PalCompileResultException extends PalResultException {

    public PalCompileResultException() {
        super();
    }

    public PalCompileResultException(String message) {
        super(message);
    }

    public PalCompileResultException(String message, Throwable cause) {
        super(message, cause);
    }
}

