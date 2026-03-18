package de.bund.zrb.util;

/**
 * Strategy for encrypting/decrypting stored passwords.
 */
public enum PasswordMethod {

    /**
     * Windows DPAPI via JNA (CryptProtectData / CryptUnprotectData).
     * <p>
     * Passwords are bound to the current Windows user account.
     * <b>May be blocked</b> on hardened Windows 11 systems (Smart App Control /
     * AppLocker / WDAC) because JNA extracts {@code jnidispatch.dll} into a
     * temp directory.
     */
    WINDOWS_DPAPI("Windows DPAPI (JNA)"),

    /**
     * Windows DPAPI via PowerShell ({@code ConvertFrom-SecureString} /
     * {@code ConvertTo-SecureString}).
     * <p>
     * Passwords are bound to the current Windows user account — same security
     * model as {@link #WINDOWS_DPAPI}, but without JNA. Requires that
     * {@code powershell.exe} is available and not blocked by policy.
     */
    POWERSHELL_DPAPI("Windows DPAPI (PowerShell)"),

    /**
     * Pure-Java AES-256-GCM with a file-based master key
     * ({@code ~/.mainframemate/.master.key}).
     * <p>
     * No native dependencies. Works on any OS with Java 8+.
     */
    JAVA_AES("Java AES-256 (plattformunabhängig)"),

    /**
     * KeePass as external secret store via PowerShell + KeePass.exe.
     * <p>
     * Passwords are read from / written to a KeePass {@code .kdbx} database
     * using PowerShell and the {@code KeePass.exe} assembly from the KeePass 2.x
     * installation (KeePassLib is compiled into KeePass.exe). The database is unlocked via the Windows User Account key.
     * The application does not store any encrypted password — it delegates
     * storage entirely to KeePass.
     */
    KEEPASS("KeePass (externer Passwort-Store)");

    private final String displayName;

    PasswordMethod(String displayName) {
        this.displayName = displayName;
    }

    /** Human-readable label for settings UI. */
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

