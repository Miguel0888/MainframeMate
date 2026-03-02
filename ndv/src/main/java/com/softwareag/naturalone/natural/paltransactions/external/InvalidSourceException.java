package com.softwareag.naturalone.natural.paltransactions.external;

/**
 * Ausnahme für ungültige Quellcode-Daten.
 */
public class InvalidSourceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}

