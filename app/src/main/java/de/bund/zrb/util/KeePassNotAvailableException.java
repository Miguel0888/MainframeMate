package de.bund.zrb.util;

/**
 * Thrown when KeePass / KPScript is not available or cannot be executed.
 * <p>
 * The user should be advised to check the KeePass configuration in the
 * settings (Einstellungen → Allgemein → Sicherheit) or switch to a
 * different password method.
 */
public class KeePassNotAvailableException extends RuntimeException {

    public KeePassNotAvailableException(String detail) {
        super("KeePass-Zugriff fehlgeschlagen: " + detail + "\n\n"
                + "Bitte prüfen Sie in den Einstellungen (Allgemein → Sicherheit):\n"
                + "• Pfad zu KPScript.exe\n"
                + "• Pfad zur .kdbx-Datenbank\n"
                + "• Eintragstitel\n\n"
                + "Alternativ können Sie auf eine andere Passwort-Methode wechseln.");
    }

    public KeePassNotAvailableException(String detail, Throwable cause) {
        super("KeePass-Zugriff fehlgeschlagen: " + detail + "\n\n"
                + "Bitte prüfen Sie in den Einstellungen (Allgemein → Sicherheit):\n"
                + "• Pfad zu KPScript.exe\n"
                + "• Pfad zur .kdbx-Datenbank\n"
                + "• Eintragstitel\n\n"
                + "Alternativ können Sie auf eine andere Passwort-Methode wechseln.",
                cause);
    }
}

