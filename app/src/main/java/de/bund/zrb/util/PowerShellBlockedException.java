package de.bund.zrb.util;

/**
 * Thrown when PowerShell cannot be started or is blocked by the operating system
 * (e.g. Execution Policy, AppLocker, Constrained Language Mode).
 * <p>
 * The user should be advised to switch the password method to
 * {@link PasswordMethod#JAVA_AES} or {@link PasswordMethod#WINDOWS_DPAPI}
 * in the settings (Einstellungen → Allgemein → Passwort-Verschlüsselung).
 */
public class PowerShellBlockedException extends RuntimeException {

    private static final String MESSAGE =
            "PowerShell konnte nicht gestartet werden — der Aufruf wird vom Betriebssystem blockiert.\n\n"
            + "Bitte wechseln Sie in den Einstellungen (Allgemein → Sicherheit → Passwort-Verschlüsselung) "
            + "auf \"" + PasswordMethod.JAVA_AES.getDisplayName() + "\" oder \""
            + PasswordMethod.WINDOWS_DPAPI.getDisplayName() + "\".";

    public PowerShellBlockedException(Throwable cause) {
        super(MESSAGE, cause);
    }
}

