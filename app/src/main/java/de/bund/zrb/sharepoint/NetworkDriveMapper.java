package de.bund.zrb.sharepoint;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps SharePoint WebDAV UNC paths as Windows network drives ({@code net use}).
 * <p>
 * Uses {@code net use <DRIVE>: <UNC_PATH>} to assign a drive letter.
 * The WebClient service must be running on the machine.
 * <p>
 * Drive letters are picked automatically (Z → A, skipping already used ones).
 */
public final class NetworkDriveMapper {

    private static final Logger LOG = Logger.getLogger(NetworkDriveMapper.class.getName());

    /** Pattern to match "X:" at start of a {@code net use} listing line. */
    private static final Pattern DRIVE_PATTERN = Pattern.compile("^\\s*\\w+\\s+([A-Z]:)\\s+", Pattern.CASE_INSENSITIVE);

    private NetworkDriveMapper() {}

    // ════════════════════════════════════════════════════════════════
    //  Public API
    // ════════════════════════════════════════════════════════════════

    /**
     * Result of a single drive-mapping attempt.
     */
    public static class MappingResult {
        public final String url;
        public final String uncPath;
        public final String driveLetter;
        public final boolean success;
        public final String message;

        MappingResult(String url, String uncPath, String driveLetter, boolean success, String message) {
            this.url = url;
            this.uncPath = uncPath;
            this.driveLetter = driveLetter;
            this.success = success;
            this.message = message;
        }
    }

    /**
     * Map a single SharePoint URL as a network drive.
     *
     * @param spUrl  the SharePoint web URL (https://…)
     * @param label  optional label shown in Explorer (may be {@code null})
     * @return result containing the assigned drive letter or error info
     */
    public static MappingResult mapDrive(String spUrl, String label) {
        if (spUrl == null || spUrl.trim().isEmpty()) {
            return new MappingResult(spUrl, "", "", false, "URL ist leer");
        }

        String uncPath = SharePointPathUtil.toUncPath(spUrl);
        if (uncPath.isEmpty() || uncPath.equals(spUrl)) {
            return new MappingResult(spUrl, uncPath, "", false,
                    "URL konnte nicht in einen UNC-Pfad konvertiert werden: " + spUrl);
        }

        // Check if already mapped
        String existingDrive = findExistingMapping(uncPath);
        if (existingDrive != null) {
            return new MappingResult(spUrl, uncPath, existingDrive, true,
                    "Bereits als " + existingDrive + " verbunden");
        }

        // Find a free drive letter
        String drive = findFreeDriveLetter();
        if (drive == null) {
            return new MappingResult(spUrl, uncPath, "", false,
                    "Kein freier Laufwerksbuchstabe verfügbar");
        }

        // Ensure WebClient service is running
        ensureWebClient();

        // Execute net use
        try {
            List<String> cmd = new ArrayList<String>();
            cmd.add("net");
            cmd.add("use");
            cmd.add(drive);
            cmd.add(uncPath);
            cmd.add("/persistent:yes");

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), Charset.defaultCharset()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            int exitCode = proc.waitFor();

            if (exitCode == 0) {
                LOG.info("[NetDrive] Mapped " + uncPath + " as " + drive);

                // Optionally rename drive label in Explorer via PowerShell
                if (label != null && !label.trim().isEmpty()) {
                    setDriveLabel(drive, label.trim());
                }

                return new MappingResult(spUrl, uncPath, drive, true,
                        "Netzlaufwerk " + drive + " erfolgreich verbunden");
            } else {
                String msg = output.toString().trim();
                LOG.warning("[NetDrive] net use failed (exit=" + exitCode + "): " + msg);
                return new MappingResult(spUrl, uncPath, drive, false,
                        "net use fehlgeschlagen (Code " + exitCode + "): " + msg);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[NetDrive] Error mapping " + uncPath, e);
            return new MappingResult(spUrl, uncPath, drive, false,
                    "Fehler: " + e.getMessage());
        }
    }

    /**
     * Disconnect a mapped network drive.
     *
     * @param driveLetter e.g. "Z:"
     * @return {@code true} if disconnect succeeded
     */
    public static boolean disconnectDrive(String driveLetter) {
        try {
            ProcessBuilder pb = new ProcessBuilder("net", "use", driveLetter, "/delete", "/y");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            int exit = proc.waitFor();
            LOG.info("[NetDrive] Disconnect " + driveLetter + " → exit " + exit);
            return exit == 0;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[NetDrive] Disconnect error for " + driveLetter, e);
            return false;
        }
    }

    /**
     * List currently mapped network drives.
     *
     * @return map of drive letter (e.g. "Z:") → remote UNC path
     */
    public static Map<String, String> listMappedDrives() {
        Map<String, String> result = new LinkedHashMap<String, String>();
        try {
            ProcessBuilder pb = new ProcessBuilder("net", "use");
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), Charset.defaultCharset()));
            String line;
            while ((line = reader.readLine()) != null) {
                // Lines look like: "OK   Z:  \\host@SSL\DavWWWRoot\..."
                Matcher m = DRIVE_PATTERN.matcher(line);
                if (m.find()) {
                    String drive = m.group(1).toUpperCase();
                    String rest = line.substring(m.end()).trim();
                    // The UNC path starts with \\
                    int uncStart = rest.indexOf("\\\\");
                    if (uncStart >= 0) {
                        // UNC path ends at whitespace or end of line
                        int uncEnd = rest.indexOf(' ', uncStart + 2);
                        String unc = uncEnd > 0 ? rest.substring(uncStart, uncEnd) : rest.substring(uncStart);
                        result.put(drive, unc.trim());
                    }
                }
            }
            proc.waitFor();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[NetDrive] Failed to list drives", e);
        }
        return result;
    }

    // ════════════════════════════════════════════════════════════════
    //  Internal helpers
    // ════════════════════════════════════════════════════════════════

    /**
     * Check if the given UNC path is already mapped, and return the drive letter if so.
     */
    private static String findExistingMapping(String uncPath) {
        Map<String, String> mapped = listMappedDrives();
        String normalised = uncPath.toLowerCase().replace("/", "\\");
        for (Map.Entry<String, String> entry : mapped.entrySet()) {
            if (entry.getValue().toLowerCase().replace("/", "\\").equals(normalised)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Find the next free drive letter (Z → D, skipping used ones).
     */
    private static String findFreeDriveLetter() {
        Map<String, String> mapped = listMappedDrives();
        // Also check which drive letters have existing file-system roots
        File[] roots = File.listRoots();
        java.util.Set<String> used = new java.util.HashSet<String>();
        for (Map.Entry<String, String> e : mapped.entrySet()) {
            used.add(e.getKey().toUpperCase());
        }
        if (roots != null) {
            for (File root : roots) {
                String letter = root.getAbsolutePath().substring(0, 2).toUpperCase();
                used.add(letter);
            }
        }

        // Prefer Z → D
        for (char c = 'Z'; c >= 'D'; c--) {
            String candidate = c + ":";
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Ensure the Windows WebClient service is running (required for WebDAV).
     * If not running, attempts to start it via {@code net start webclient}.
     */
    private static void ensureWebClient() {
        try {
            // Quick check: the service sets this registry key, but simpler to just try starting.
            ProcessBuilder pb = new ProcessBuilder("net", "start", "webclient");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            // Drain output
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), Charset.defaultCharset()));
            while (reader.readLine() != null) { /* drain */ }
            int exit = proc.waitFor();
            // exit 0 = started, exit 2 = already running — both OK
            if (exit == 0) {
                LOG.info("[NetDrive] WebClient service started");
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "[NetDrive] Could not start WebClient service", e);
        }
    }

    /**
     * Set a custom label for a drive in Windows Explorer via PowerShell.
     * Uses {@code (New-Object -ComObject Shell.Application).NameSpace('Z:\').Self.Name = 'label'}.
     */
    private static void setDriveLabel(String drive, String label) {
        try {
            String escaped = label.replace("'", "''");
            String script = "(New-Object -ComObject Shell.Application).NameSpace('"
                    + drive + "\\').Self.Name = '" + escaped + "'";
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", script);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), Charset.defaultCharset()));
            while (reader.readLine() != null) { /* drain */ }
            proc.waitFor();
        } catch (Exception e) {
            LOG.log(Level.FINE, "[NetDrive] Could not set drive label for " + drive, e);
        }
    }
}

