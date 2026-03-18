package de.bund.zrb.util;

/**
 * Thrown when the KeePass integration (PowerShell + KeePassLib.dll) is not available.
 * <p>
 * The user should be advised to check the KeePass configuration in the
 * settings (Einstellungen → Allgemein → Sicherheit) or switch to a
 * different password method.
 */
public class KeePassNotAvailableException extends RuntimeException {

    public KeePassNotAvailableException(String detail) {
        super("KeePass-Zugriff fehlgeschlagen: " + detail + "\n\n"
                + "Bitte prüfen Sie in den Einstellungen (Allgemein → Sicherheit):\n"
                + "• KeePass-Installationsverzeichnis (enthält KeePassLib.dll)\n"
                + "• Pfad zur .kdbx-Datenbank\n"
                + "• Eintragstitel\n\n"
                + "Alternativ können Sie auf eine andere Passwort-Methode wechseln.");
    }

    public KeePassNotAvailableException(String detail, Throwable cause) {
        super("KeePass-Zugriff fehlgeschlagen: " + detail + "\n\n"
                + "Bitte prüfen Sie in den Einstellungen (Allgemein → Sicherheit):\n"
                + "• KeePass-Installationsverzeichnis (enthält KeePassLib.dll)\n"
                + "• Pfad zur .kdbx-Datenbank\n"
                + "• Eintragstitel\n\n"
                + "Alternativ können Sie auf eine andere Passwort-Methode wechseln.",
                cause);
    }
}
