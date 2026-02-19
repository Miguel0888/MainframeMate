package de.bund.zrb.files.api;

public enum FileServiceErrorCode {
    NOT_FOUND,
    PERMISSION_DENIED,
    AUTH_FAILED,
    AUTH_CANCELLED,  // Benutzer hat die Passwort-Eingabe abgebrochen
    CONFLICT,
    IO_ERROR,
    UNKNOWN
}

