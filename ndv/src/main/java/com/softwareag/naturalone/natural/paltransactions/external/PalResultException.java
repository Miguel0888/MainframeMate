package com.softwareag.naturalone.natural.paltransactions.external;

/**
 * Ergebnisausnahme bei PAL-Transaktionen.
 * Wird vom Server zurückgegeben, wenn eine Transaktion fehlschlägt (z.B. Objekt nicht gefunden).
 */
public class PalResultException extends Exception {
    public static final int NATERROR = 1;
    public static final int SYSTEMERROR = 2;
    public static final int WARNING = 3;
    public static final int FATALERROR = 4;
    public static final int NATSECERROR = 9;

    private int errorNumber;
    private int errorKind;
    private String shortText;
    private String[] longText;

    public PalResultException() {
        super();
    }

    public PalResultException(String message) {
        super(message);
    }

    public PalResultException(String message, Throwable cause) {
        super(message, cause);
    }

    public PalResultException(int errorNumber, int errorKind, String shortText) {
        super(shortText);
        this.errorNumber = errorNumber;
        this.errorKind = errorKind;
        this.shortText = shortText;
    }

    public int getErrorNumber() {
        return errorNumber;
    }

    public int getErrorKind() {
        return errorKind;
    }

    public void setErrorKind(int errorKind) {
        this.errorKind = errorKind;
    }

    public String getShortText() {
        return shortText;
    }

    public String[] getLongText() {
        return longText;
    }

    public void setLongText(String[] longText) {
        this.longText = longText;
    }

    public boolean isNatError() {
        return errorKind == NATERROR;
    }

    public boolean isSystemError() {
        return errorKind == SYSTEMERROR;
    }

    public boolean isWarning() {
        return errorKind == WARNING;
    }

    public boolean isFatalError() {
        return errorKind == FATALERROR;
    }

    public boolean isNatSecError() {
        return errorKind == NATSECERROR;
    }
}

