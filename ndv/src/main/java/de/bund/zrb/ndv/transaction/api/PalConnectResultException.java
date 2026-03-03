package de.bund.zrb.ndv.transaction.api;

/**
 * Verbindungsfehler-Ausnahme bei PAL-Connect.
 * Wird geworfen, wenn die Anmeldung am NDV-Server fehlschlägt.
 */
public class PalConnectResultException extends PalResultException {

    static final long serialVersionUID = -123456789L;

    public static final int PASSWORD_INVALID = 1;
    public static final int PASSWORD_EXPIRED = 2;
    public static final int PASSWORD_NEW_CHANGE = 3;
    public static final int PASSWORD_NEW_INVALID = 4;
    public static final int PASSWORD_NEW_WRONG_LENGTH = 5;

    private int secErrorKind;

    public PalConnectResultException(int errorNumber, String shortText, int errorKind, int secErrorKind) {
        super(errorNumber, errorKind, shortText);
        this.secErrorKind = secErrorKind;
    }

    public final boolean isPasswordInvalid() {
        return getSecErrorKind() == PASSWORD_INVALID;
    }

    public final boolean isPasswordExpired() {
        return getSecErrorKind() == PASSWORD_EXPIRED;
    }

    public final boolean isNewPasswordNotPermitted() {
        return getSecErrorKind() == PASSWORD_NEW_CHANGE;
    }

    public final boolean isNewPasswordInvalid() {
        return getSecErrorKind() == PASSWORD_NEW_INVALID;
    }

    public final boolean isNewPasswordWrongLength() {
        return getSecErrorKind() == PASSWORD_NEW_WRONG_LENGTH;
    }

    public final int getSecErrorKind() {
        return this.secErrorKind;
    }

    public boolean isChangePassword() {
        return isNewPasswordWrongLength() || isPasswordExpired()
                || isNewPasswordNotPermitted() || isNewPasswordWrongLength();
    }
}
