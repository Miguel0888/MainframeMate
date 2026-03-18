package de.bund.zrb.util;

/**
 * Thrown when JNA native library loading is blocked by the operating system
 * (e.g. Windows Smart App Control, AppLocker, or WDAC).
 * <p>
 * The user should be advised to switch the password method to
 * {@link PasswordMethod#JAVA_AES} in the settings
 * (Einstellungen → Allgemein → Passwort-Verschlüsselung).
 */
public class JnaBlockedException extends RuntimeException {

    private static final String MESSAGE =
            "JNA konnte nicht geladen werden — die native Bibliothek wird vom Betriebssystem blockiert.\n\n"
            + "Bitte wechseln Sie in den Einstellungen (Allgemein → Sicherheit → Passwort-Verschlüsselung) "
            + "auf \"" + PasswordMethod.JAVA_AES.getDisplayName() + "\".";

    public JnaBlockedException(Throwable cause) {
        super(MESSAGE, cause);
    }
}

