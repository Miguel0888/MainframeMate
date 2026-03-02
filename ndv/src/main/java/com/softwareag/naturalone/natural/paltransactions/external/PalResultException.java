package com.softwareag.naturalone.natural.paltransactions.external;

/**
 * Ergebnisausnahme bei PAL-Transaktionen.
 * Wird vom Server zurückgegeben, wenn eine Transaktion fehlschlägt (z.B. Objekt nicht gefunden).
 */
public class PalResultException extends RuntimeException {

    private int resultCode;

    public PalResultException() {
        super();
    }

    public PalResultException(String message) {
        super(message);
    }

    public PalResultException(String message, Throwable cause) {
        super(message, cause);
    }

    public PalResultException(int resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    public int getResultCode() {
        return resultCode;
    }
}

