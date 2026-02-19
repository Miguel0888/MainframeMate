package de.bund.zrb.files.auth;

/**
 * Exception die geworfen wird, wenn der Benutzer die Passwort-Eingabe abgebrochen hat.
 * Diese Exception sollte nicht als Authentifizierungsfehler behandelt werden,
 * sondern als expliziter Abbruch durch den Benutzer.
 */
public class AuthCancelledException extends RuntimeException {

    public AuthCancelledException() {
        super("Authentifizierung vom Benutzer abgebrochen");
    }

    public AuthCancelledException(String message) {
        super(message);
    }
}

