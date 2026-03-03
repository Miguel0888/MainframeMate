package de.bund.zrb.ndv.transaction.api;

/**
 * Ergebnisausnahme bei PAL-Transaktionen.
 * Wird vom Server zurückgegeben, wenn eine Transaktion fehlschlägt.
 */
public class PalResultException extends Exception {

    static final long serialVersionUID = -1234567890L;

    public static final int NOERROR = 0;
    public static final int WARNING = 1;
    public static final int NATERROR = 2;
    public static final int INTERROR = 3;
    public static final int FATALERROR = 4;

    private int errorKind;
    private String shortText;
    private String[] longText;
    private int errorNumber;

    public PalResultException(int errorNumber, int errorKind, String shortText) {
        super(shortText, null);
        this.errorNumber = errorNumber;
        this.errorKind = errorKind;
        this.shortText = shortText;
    }

    public final String[] getLongText() {
        if (this.longText == null) {
            return null;
        }
        return (String[]) this.longText.clone();
    }

    public final String getShortText() {
        return this.shortText;
    }

    public final int getErrorNumber() {
        return this.errorNumber;
    }

    public void setErrorKind(int errorKind) {
        this.errorKind = errorKind;
    }

    public void setLongText(String[] longText) {
        if (longText == null) {
            return;
        }
        this.longText = (String[]) longText.clone();
    }

    public final int getErrorKind() {
        return this.errorKind;
    }

    public final boolean isWarning() {
        return getErrorKind() == WARNING;
    }

    public final void setShortText(String shortText) {
        this.shortText = shortText;
    }
}
