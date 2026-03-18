package de.bund.zrb.util;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central credential store backed by {@link Settings#componentCredentials}.
 * <p>
 * Every component in MainframeMate can store its own credentials here
 * using a namespaced key, for example:
 * <ul>
 *   <li>{@code "wiki:wikipedia_de"} — Wiki site credentials</li>
 *   <li>{@code "betaview"} — BetaView credentials</li>
 *   <li>{@code "ftp:myhost.example.com"} — FTP host credentials</li>
 * </ul>
 * <p>
 * Values are encrypted via {@link WindowsCryptoUtil} as {@code "user|password"}.
 * The store persists immediately to {@code settings.json}.
 *
 * @see Settings#componentCredentials
 */
public final class CredentialStore {

    private static final Logger LOG = Logger.getLogger(CredentialStore.class.getName());

    /** Separator between username and password inside the encrypted value. */
    static final char SEPARATOR = '|';

    private CredentialStore() { /* utility */ }

    // ── Read ────────────────────────────────────────────────────────────────

    /**
     * Resolve stored credentials for the given component key.
     *
     * @param componentKey namespaced key, e.g. {@code "wiki:wikipedia_de"}
     * @return {@code String[]{user, password}} or {@code null} if no credentials are stored
     * @throws JnaBlockedException          if DPAPI/JNA is blocked
     * @throws PowerShellBlockedException   if PowerShell DPAPI is blocked
     * @throws KeePassNotAvailableException if KeePass is misconfigured
     */
    public static String[] resolve(String componentKey) {
        Settings settings = SettingsHelper.load();
        String encrypted = settings.componentCredentials.get(componentKey);
        if (encrypted == null || encrypted.isEmpty()) return null;

        try {
            String decrypted = WindowsCryptoUtil.decrypt(encrypted);
            int sep = decrypted.indexOf(SEPARATOR);
            if (sep >= 0) {
                String user = decrypted.substring(0, sep);
                String pass = decrypted.substring(sep + 1);
                if (!user.isEmpty()) {
                    return new String[]{user, pass};
                }
            }
            LOG.warning("Credential for '" + componentKey + "' has unexpected format (no separator).");
        } catch (JnaBlockedException e) {
            throw e;
        } catch (PowerShellBlockedException e) {
            throw e;
        } catch (KeePassNotAvailableException e) {
            throw e;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to decrypt credential for '" + componentKey + "'", e);
        }
        return null;
    }

    // ── Write ───────────────────────────────────────────────────────────────

    /**
     * Store (or update) credentials for the given component key.
     * Encrypts and persists immediately to {@code settings.json}.
     *
     * @param componentKey namespaced key, e.g. {@code "wiki:wikipedia_de"}
     * @param user         username
     * @param password     password (plaintext)
     * @throws JnaBlockedException          if DPAPI/JNA is blocked
     * @throws PowerShellBlockedException   if PowerShell DPAPI is blocked
     * @throws KeePassNotAvailableException if KeePass is misconfigured
     */
    public static void store(String componentKey, String user, String password) {
        String encrypted = WindowsCryptoUtil.encrypt(user + SEPARATOR + password);
        Settings settings = SettingsHelper.load();
        settings.componentCredentials.put(componentKey, encrypted);
        SettingsHelper.save(settings);
        LOG.fine("Stored credential for '" + componentKey + "' user='" + user + "'");
    }

    // ── Delete ──────────────────────────────────────────────────────────────

    /**
     * Remove stored credentials for the given component key.
     *
     * @param componentKey namespaced key
     */
    public static void remove(String componentKey) {
        Settings settings = SettingsHelper.load();
        if (settings.componentCredentials.remove(componentKey) != null) {
            SettingsHelper.save(settings);
            LOG.fine("Removed credential for '" + componentKey + "'");
        }
    }

    // ── List ────────────────────────────────────────────────────────────────

    /**
     * Return an unmodifiable snapshot of all stored component credentials.
     * Keys are component identifiers, values are encrypted strings.
     */
    public static Map<String, String> getAll() {
        Settings settings = SettingsHelper.load();
        return Collections.unmodifiableMap(
                new LinkedHashMap<String, String>(settings.componentCredentials));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Build a wiki-specific credential key.
     *
     * @param siteId the wiki site id (e.g. {@code "wikipedia_de"})
     * @return {@code "wiki:<siteId>"}
     */
    public static String wikiKey(String siteId) {
        return "wiki:" + siteId;
    }

    /**
     * Extract a human-readable component label from a credential key.
     * <p>Examples:
     * <ul>
     *   <li>{@code "wiki:wikipedia_de"} → {@code "Wiki"}</li>
     *   <li>{@code "betaview"} → {@code "BetaView"}</li>
     *   <li>{@code "ftp:myhost"} → {@code "FTP"}</li>
     * </ul>
     */
    public static String componentLabel(String key) {
        if (key == null) return "";
        int colon = key.indexOf(':');
        String prefix = colon > 0 ? key.substring(0, colon) : key;
        switch (prefix.toLowerCase()) {
            case "wiki":     return "Wiki";
            case "betaview": return "BetaView";
            case "ftp":      return "FTP";
            case "ndv":      return "NDV";
            default:         return prefix;
        }
    }

    /**
     * Extract the identifier part (after the colon) from a credential key.
     * Returns the full key if no colon is present.
     */
    public static String componentId(String key) {
        if (key == null) return "";
        int colon = key.indexOf(':');
        return colon > 0 ? key.substring(colon + 1) : key;
    }

    /**
     * Try to extract the username from a stored credential without
     * throwing on crypto failures (returns {@code null} on error).
     * <p>
     * When the password method is {@link PasswordMethod#KEEPASS},
     * {@link WindowsCryptoUtil#decrypt} does not actually decrypt the
     * stored value — it fetches the main password from KeePass, which
     * would trigger the full KeePass/RPC flow (including pairing).
     * We must not let that happen for a simple username lookup.
     */
    public static String resolveUserNameQuietly(String componentKey) {
        try {
            Settings settings = SettingsHelper.load();
            if ("KEEPASS".equalsIgnoreCase(settings.passwordMethod)) {
                return null;
            }
            String[] cred = resolve(componentKey);
            return cred != null ? cred[0] : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ── KeePass integration ─────────────────────────────────────────────────

    /**
     * List all entries from the KeePass database via PowerShell + KeePass.exe.
     * Only works when the password method is {@link PasswordMethod#KEEPASS}.
     *
     * @return formatted entry list output
     * @throws KeePassNotAvailableException if KeePass is misconfigured or the call fails
     */
    public static String listKeePassEntries() {
        return KeePassProvider.listEntries();
    }

    /**
     * Create a new entry in KeePass.
     * Uses RPC or PowerShell depending on the configured access method.
     */
    public static void addKeePassEntry(String title, String userName, String password, String url) {
        Settings settings = SettingsHelper.load();
        if ("RPC".equalsIgnoreCase(settings.keepassAccessMethod)) {
            KeePassProvider.rpcAddEntry(title, userName, password, url);
        } else {
            KeePassProvider.psAddEntry(title, userName, password);
        }
    }

    /**
     * Update an existing entry in KeePass.
     * Uses RPC or PowerShell depending on the configured access method.
     */
    public static void updateKeePassEntry(String title, String userName, String password) {
        Settings settings = SettingsHelper.load();
        if ("RPC".equalsIgnoreCase(settings.keepassAccessMethod)) {
            KeePassProvider.rpcUpdateEntry(title, userName, password);
        } else {
            KeePassProvider.psUpdateEntry(title, userName, password);
        }
    }

    /**
     * Remove an entry from KeePass.
     * Uses RPC (by uniqueID) or PowerShell (by title) depending on the configured access method.
     *
     * @param title    entry title (used for PowerShell mode)
     * @param uniqueID entry unique ID (used for RPC mode, may be {@code null})
     */
    public static void removeKeePassEntry(String title, String uniqueID) {
        Settings settings = SettingsHelper.load();
        if ("RPC".equalsIgnoreCase(settings.keepassAccessMethod)) {
            if (uniqueID == null || uniqueID.isEmpty()) {
                throw new KeePassNotAvailableException(
                        "Zum Löschen via RPC wird die uniqueID benötigt.");
            }
            KeePassProvider.rpcRemoveEntry(uniqueID);
        } else {
            KeePassProvider.psRemoveEntry(title);
        }
    }
}

