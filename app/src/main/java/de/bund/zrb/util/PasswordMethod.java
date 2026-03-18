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
     * Pure-Java AES-256-GCM with a file-based master key
     * ({@code ~/.mainframemate/.master.key}).
     * <p>
     * No native dependencies. Works on any OS with Java 8+.
     */
    JAVA_AES("Java AES-256 (plattformunabhängig)");

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

