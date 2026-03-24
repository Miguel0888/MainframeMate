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
 */
final class RegistryReader {

    private static final Logger LOG = Logger.getLogger(RegistryReader.class.getName());
    private static final int TIMEOUT_SECONDS = 5;

    /** Registry key for connection-level settings (contains DefaultConnectionSettings binary blob). */
    static final String CONNECTIONS_KEY =
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\\Connections";

    /** Bit flag in {@code DefaultConnectionSettings} byte 8: "Automatically detect settings" (WPAD). */
    static final int FLAG_AUTO_DETECT = 0x08;

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
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "reg", "query", CONNECTIONS_KEY, "/v", "DefaultConnectionSettings");
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
                if (trimmed.contains("DefaultConnectionSettings") && trimmed.contains("REG_BINARY")) {
                    String[] parts = trimmed.split("\\s{2,}");
                    if (parts.length >= 3) {
                        String hex = parts[parts.length - 1].trim().replaceAll("[^0-9A-Fa-f]", "");
                        // Byte at index 8 (0-based) = hex characters at positions 16–17
                        if (hex.length() >= 18) {
                            return Integer.parseInt(hex.substring(16, 18), 16);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "[WinProxy] Failed to read connection flags", e);
        }
        return -1;
    }
}
