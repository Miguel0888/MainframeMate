package de.bund.zrb.winproxy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads values from the Windows Registry via {@code reg.exe}.
 * <p>
 * This approach works on all Windows machines regardless of PowerShell
 * Constrained Language Mode (CLM) restrictions — {@code reg.exe} is a
 * native Windows tool that is never blocked by policy.
 * <p>
 * <b>Registry search order:</b> Group Policy keys are checked first,
 * because GPO settings always take precedence over user-level settings
 * on hardened enterprise machines.
 * <ol>
 *   <li>{@code HKCU\Software\Policies\...\Internet Settings} (User GPO)</li>
 *   <li>{@code HKLM\Software\Policies\...\Internet Settings} (Machine GPO)</li>
 *   <li>{@code HKCU\Software\Microsoft\...\Internet Settings} (User settings)</li>
 *   <li>{@code HKLM\Software\Microsoft\...\Internet Settings} (Machine settings)</li>
 * </ol>
 */
final class RegistryReader {

    private static final Logger LOG = Logger.getLogger(RegistryReader.class.getName());
    private static final int TIMEOUT_SECONDS = 5;

    /** Registry key for connection-level settings (contains DefaultConnectionSettings binary blob). */
    static final String CONNECTIONS_KEY =
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\\Connections";

    /** Bit flag in {@code DefaultConnectionSettings} byte 8: "Automatically detect settings" (WPAD). */
    static final int FLAG_AUTO_DETECT = 0x08;

    /** Bit flag in {@code DefaultConnectionSettings} byte 8: "Use automatic configuration script" (PAC). */
    static final int FLAG_AUTO_CONFIG = 0x04;

    /** Bit flag in {@code DefaultConnectionSettings} byte 8: "Use a proxy server". */
    static final int FLAG_PROXY = 0x02;

    /**
     * All registry keys to search for Internet/proxy settings, in priority order.
     * Group Policy keys are first because GPO always overrides user-level settings.
     */
    static final String[] SETTINGS_KEYS = {
            "HKCU\\Software\\Policies\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
            "HKLM\\Software\\Policies\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
            "HKLM\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
    };

    private RegistryReader() {}

    /**
     * Queries a single REG_SZ or REG_DWORD value from the Windows Registry.
     *
     * @param key       full registry key path (e.g. {@code "HKCU\\Software\\..."})
     * @param valueName name of the value to query
     * @return the value string, or {@code null} if not found or on error
     */
    static String queryValue(String key, String valueName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query", key, "/v", valueName);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // Parse the reg.exe output — each matching line looks like:
            //     AutoConfigURL    REG_SZ    http://wpad.corp.local/wpad.dat
            // Columns are separated by 2+ spaces
            for (String line : out.toString().split("\\r?\\n")) {
                String trimmed = line.trim();
                if (trimmed.contains(valueName)) {
                    String[] parts = trimmed.split("\\s{2,}");
                    if (parts.length >= 3) {
                        return parts[parts.length - 1].trim();
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "[WinProxy] reg query failed for " + valueName, e);
        }
        return null;
    }

    /**
     * Searches all registry hives (Policy + user + machine) for a value, returning the
     * first non-empty match. This is the correct way to detect enterprise proxy settings,
     * because Group Policy stores them under {@code Software\Policies\...} rather than
     * the normal {@code Software\Microsoft\...} path.
     *
     * @param valueName name of the value to query (e.g. {@code "AutoConfigURL"})
     * @return the first non-empty value found, or {@code null}
     */
    static String queryValueFromAllHives(String valueName) {
        for (String key : SETTINGS_KEYS) {
            String value = queryValue(key, valueName);
            if (value != null && !value.trim().isEmpty()) {
                LOG.fine("[WinProxy] Found " + valueName + " in " + key + " = " + value);
                return value.trim();
            }
        }
        return null;
    }

    /**
     * Reads the connection flags byte from the {@code DefaultConnectionSettings} binary value.
     * <p>
     * The flags byte is at offset 8 in the binary blob and contains:
     * <ul>
     *   <li>{@code 0x01} — Direct connection</li>
     *   <li>{@code 0x02} — Use proxy server</li>
     *   <li>{@code 0x04} — Use automatic configuration script (explicit PAC URL)</li>
     *   <li>{@code 0x08} — Automatically detect settings (WPAD via DHCP/DNS)</li>
     * </ul>
     *
     * @return the flags byte (0–255), or {@code -1} if unable to read
     */
    static int queryConnectionFlags() {
        String hex = readConnectionSettingsHex();
        if (hex == null || hex.length() < 18) return -1;
        try {
            return Integer.parseInt(hex.substring(16, 18), 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Parses the PAC auto-config URL embedded in the {@code DefaultConnectionSettings} blob.
     * <p>
     * The binary structure after the flags byte is:
     * <pre>
     * Offset   Size   Description
     * 0        4      Version (DWORD LE)
     * 4        4      Counter (DWORD LE)
     * 8        1      Flags (bit 0x04 = PAC URL present)
     * 9        3      Padding
     * 12       4      Proxy server string length (DWORD LE, N)
     * 16       N      Proxy server string (ASCII)
     * 16+N     4      Proxy override string length (DWORD LE, M)
     * 20+N     M      Proxy override string (ASCII)
     * 20+N+M   4      PAC URL string length (DWORD LE, P)
     * 24+N+M   P      PAC URL string (ASCII)
     * </pre>
     *
     * @return the embedded PAC URL, or {@code null} if not present or unparseable
     */
    static String queryAutoConfigUrlFromBlob() {
        String hex = readConnectionSettingsHex();
        if (hex == null) return null;

        try {
            // Check if 0x04 flag (auto-config script) is set
            if (hex.length() < 18) return null;
            int flags = Integer.parseInt(hex.substring(16, 18), 16);
            if ((flags & FLAG_AUTO_CONFIG) == 0) {
                return null; // no PAC URL embedded
            }

            // Parse: skip to byte 12 (proxy server length)
            int pos = 24; // byte 12 = hex pos 24
            if (hex.length() < pos + 8) return null;

            // Proxy server string length (4 bytes LE)
            int proxyLen = readDwordLE(hex, pos);
            pos += 8 + (proxyLen * 2); // skip length field + proxy string

            // Proxy override string length (4 bytes LE)
            if (hex.length() < pos + 8) return null;
            int overrideLen = readDwordLE(hex, pos);
            pos += 8 + (overrideLen * 2); // skip length field + override string

            // PAC URL string length (4 bytes LE)
            if (hex.length() < pos + 8) return null;
            int pacLen = readDwordLE(hex, pos);
            pos += 8; // skip length field

            if (pacLen <= 0 || pacLen > 4096) return null;
            if (hex.length() < pos + (pacLen * 2)) return null;

            // Decode PAC URL bytes
            StringBuilder pacUrl = new StringBuilder(pacLen);
            for (int i = 0; i < pacLen; i++) {
                int byteVal = Integer.parseInt(hex.substring(pos + (i * 2), pos + (i * 2) + 2), 16);
                pacUrl.append((char) byteVal);
            }

            String result = pacUrl.toString().trim();
            if (result.isEmpty()) return null;
            LOG.fine("[WinProxy] Found PAC URL embedded in DefaultConnectionSettings: " + result);
            return result;
        } catch (Exception e) {
            LOG.log(Level.FINE, "[WinProxy] Failed to parse PAC URL from DefaultConnectionSettings blob", e);
            return null;
        }
    }

    /**
     * Reads a little-endian DWORD (4 bytes) from the hex string at the given hex-character position.
     */
    private static int readDwordLE(String hex, int hexPos) {
        if (hex.length() < hexPos + 8) return 0;
        // Bytes in LE order: b0, b1, b2, b3
        int b0 = Integer.parseInt(hex.substring(hexPos, hexPos + 2), 16);
        int b1 = Integer.parseInt(hex.substring(hexPos + 2, hexPos + 4), 16);
        int b2 = Integer.parseInt(hex.substring(hexPos + 4, hexPos + 6), 16);
        int b3 = Integer.parseInt(hex.substring(hexPos + 6, hexPos + 8), 16);
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    /**
     * Reads the raw hex string of {@code DefaultConnectionSettings} from the registry.
     * Tries both the user-level and policy connection settings.
     *
     * @return hex string (uppercase, no separators), or {@code null}
     */
    private static String readConnectionSettingsHex() {
        // Try user-level first (most common location for connection blob)
        String[] connectionKeys = {
                CONNECTIONS_KEY,
                "HKLM\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\\Connections",
        };

        for (String connKey : connectionKeys) {
            String hex = readBlobHex(connKey, "DefaultConnectionSettings");
            if (hex != null && hex.length() >= 18) {
                return hex;
            }
        }
        return null;
    }

    /**
     * Reads a REG_BINARY value from the registry and returns it as a clean hex string.
     */
    private static String readBlobHex(String key, String valueName) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "reg", "query", key, "/v", valueName);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            for (String line : out.toString().split("\\r?\\n")) {
                String trimmed = line.trim();
                if (trimmed.contains(valueName) && trimmed.contains("REG_BINARY")) {
                    String[] parts = trimmed.split("\\s{2,}");
                    if (parts.length >= 3) {
                        return parts[parts.length - 1].trim().replaceAll("[^0-9A-Fa-f]", "").toUpperCase();
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "[WinProxy] Failed to read " + valueName + " from " + key, e);
        }
        return null;
    }
}
