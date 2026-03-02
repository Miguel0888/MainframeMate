package com.softwareag.naturalone.natural.paltransactions.external;

/**
 * Ungültige Quelltext-Ausnahme.
 * Wird geworfen, wenn der hochzuladende Quelltext ungültig ist.
 */
public class InvalidSourceException extends PalResultException {

    public InvalidSourceException() {
        super();
    }

    public InvalidSourceException(String message) {
        super(message);
    }

    public InvalidSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}

