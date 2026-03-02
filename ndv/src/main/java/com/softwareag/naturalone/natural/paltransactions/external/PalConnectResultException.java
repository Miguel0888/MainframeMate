package com.softwareag.naturalone.natural.paltransactions.external;

/**
 * Verbindungsfehler-Ausnahme bei PAL-Connect.
 * Wird geworfen, wenn die Anmeldung am NDV-Server fehlschlägt (z.B. falsches Passwort).
 */
public class PalConnectResultException extends PalResultException {
    public static final int PASSWORD_INVALID = 1;
    public static final int PASSWORD_EXPIRED = 2;
    public static final int PASSWORD_NEW_CHANGE = 3;
    public static final int PASSWORD_NEW_INVALID = 4;
    public static final int PASSWORD_NEW_WRONG_LENGTH = 5;

    private final int secErrorKind;

    public PalConnectResultException(int errorNumber, String shortText, int errorKind, int secErrorKind) {
        super(errorNumber, errorKind, shortText);
        this.secErrorKind = secErrorKind;
    }

    public int getSecErrorKind() {
        return secErrorKind;
    }

    public boolean isPasswordInvalid() {
        return secErrorKind == PASSWORD_INVALID;
    }

    public boolean isPasswordExpired() {
        return secErrorKind == PASSWORD_EXPIRED;
    }

    public boolean isNewPasswordNotPermitted() {
        return secErrorKind == PASSWORD_NEW_CHANGE;
    }

    public boolean isNewPasswordInvalid() {
        return secErrorKind == PASSWORD_NEW_INVALID;
    }

    public boolean isNewPasswordWrongLength() {
        return secErrorKind == PASSWORD_NEW_WRONG_LENGTH;
    }

    public boolean isChangePassword() {
        return secErrorKind == PASSWORD_EXPIRED
            || secErrorKind == PASSWORD_NEW_CHANGE
            || secErrorKind == PASSWORD_NEW_WRONG_LENGTH;
    }
}
