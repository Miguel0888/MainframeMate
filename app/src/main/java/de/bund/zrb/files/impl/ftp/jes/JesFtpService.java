package de.bund.zrb.files.impl.ftp.jes;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FTP JES service for listing, querying, downloading and deleting z/OS jobs.
 * <p>
 * All operations assume the connection is in JES mode ({@code SITE FILETYPE=JES}).
 * The service maintains a persistent FTP connection that must be explicitly closed.
 */
public class JesFtpService implements Closeable {

    private static final Logger LOG = Logger.getLogger(JesFtpService.class.getName());

    // Patterns for parsing JES LIST output (JESINTERFACELEVEL 2 format)
    // Example: JOBNAME  JOB12345 OWNER    OUTPUT  A        RC=0000 3 spool files
    private static final Pattern JOB_LINE = Pattern.compile(
            "^(\\S+)\\s+(JOB\\d+|STC\\d+|TSU\\d+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S*)\\s*(?:RC=(\\S+))?.*?(\\d+)\\s+spool",
            Pattern.CASE_INSENSITIVE);

    // Simpler fallback: just jobname + jobid + owner + status
    private static final Pattern JOB_LINE_SIMPLE = Pattern.compile(
            "^(\\S+)\\s+((?:JOB|STC|TSU|J)\\d+)\\s+(\\S+)\\s+(OUTPUT|ACTIVE|INPUT|HELD)",
            Pattern.CASE_INSENSITIVE);

    // Spool file line patterns – z/OS JES2 LIST output varies widely:
    //   Format A:  002 JES2     N/A  H JESJCL      1,234    20
    //   Format B:  002 JES2     N/A  H JESJCL      1234
    //   Format C:  002 JES2     N/A  H JESJCL      1,234
    // The byte-count may contain commas, record count may be absent.
    private static final Pattern SPOOL_FULL = Pattern.compile(
            "^\\s*(\\d+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S)\\s+(\\S+)\\s+([\\d,]+)\\s+(\\d+)");
    private static final Pattern SPOOL_NO_RECORDS = Pattern.compile(
            "^\\s*(\\d+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S)\\s+(\\S+)\\s+([\\d,]+)\\s*$");
    // Minimal: just id + ddname somewhere in the line
    private static final Pattern SPOOL_MINIMAL = Pattern.compile(
            "^\\s*(\\d{1,4})\\s+.*?(\\S+)\\s*$");

    private final FTPClient ftp;
    private final String host;
    private final String user;
    private volatile boolean connected;

    /**
     * Create a connected JES FTP service.
     *
     * @param host     FTP host
     * @param user     FTP user
     * @param password FTP password
     * @throws IOException on connection or login failure
     */
    public JesFtpService(String host, String user, String password) throws IOException {
        this.host = host;
        this.user = user;
        this.ftp = new FTPClient();

        Settings settings = SettingsHelper.load();
        String encoding = settings.encoding != null ? settings.encoding : "UTF-8";
        ftp.setControlEncoding(encoding);

        int connectTimeout = settings.ftpConnectTimeoutMs;
        if (connectTimeout > 0) {
            ftp.setDefaultTimeout(connectTimeout);
            ftp.setConnectTimeout(connectTimeout);
        }

        ftp.connect(host);
        if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
            throw new IOException("FTP-Verbindung zu " + host + " fehlgeschlagen: " + ftp.getReplyString());
        }

        int controlTimeout = settings.ftpControlTimeoutMs;
        ftp.setSoTimeout(controlTimeout);

        if (!ftp.login(user, password)) {
            throw new IOException("FTP-Anmeldung fehlgeschlagen: " + ftp.getReplyString());
        }

        ftp.enterLocalPassiveMode();
        ftp.setFileType(FTP.ASCII_FILE_TYPE);

        // Switch to JES mode
        if (!ftp.sendSiteCommand("FILETYPE=JES")) {
            throw new IOException("Server erlaubt FILETYPE=JES nicht: " + ftp.getReplyString());
        }

        // Try to request structured spool listings with DDNames (z/OS JES FTP standard).
        // Not all servers support this; if rejected we fall back to content-based detection.
        ftp.sendSiteCommand("JESINTERFACELEVEL=2");
        String jilReply = ftp.getReplyString().trim();
        LOG.info("[JES] SITE JESINTERFACELEVEL=2 → " + jilReply);

        connected = true;
        LOG.info("[JES] Connected to " + host + " as " + user + " in JES mode.");
    }

    public String getHost() { return host; }
    public String getUser() { return user; }
    public boolean isConnected() { return connected && ftp.isConnected(); }

    // ═══════════════════════════════════════════════════════════════════
    //  Job listing
    // ═══════════════════════════════════════════════════════════════════

    /**
     * List jobs matching the given filters.
     *
     * @param ownerFilter   owner pattern (e.g. "MYUSER" or "*"), null = current user
     * @param jobNameFilter job name pattern (e.g. "MYJOB*" or "*"), null = "*"
     * @param statusFilter  "ALL", "OUTPUT", "ACTIVE", "INPUT" – null = "ALL"
     */
    public List<JesJob> listJobs(String ownerFilter, String jobNameFilter, String statusFilter)
            throws IOException {
        ensureConnected();

        // Set filters via SITE commands
        sendSite("JESOWNER=" + (ownerFilter != null && !ownerFilter.isEmpty() ? ownerFilter : user));
        sendSite("JESJOBNAME=" + (jobNameFilter != null && !jobNameFilter.isEmpty() ? jobNameFilter : "*"));
        sendSite("JESSTATUS=" + (statusFilter != null && !statusFilter.isEmpty() ? statusFilter : "ALL"));

        // LIST in JES mode returns job entries
        FTPFile[] files = ftp.listFiles();
        String rawReply = ftp.getReplyString();
        LOG.fine("[JES] LIST returned " + (files != null ? files.length : 0) + " entries. Reply: "
                + (rawReply != null ? rawReply.trim() : ""));

        List<JesJob> jobs = new ArrayList<JesJob>();

        if (files != null) {
            for (FTPFile f : files) {
                String raw = f.getRawListing();
                if (raw == null || raw.trim().isEmpty()) continue;

                JesJob job = parseJobLine(raw.trim());
                if (job != null) {
                    jobs.add(job);
                }
            }
        }

        // Fallback: if listFiles() returned nothing, try raw listing via NLST/names
        if (jobs.isEmpty()) {
            String[] names = ftp.listNames();
            if (names != null) {
                LOG.fine("[JES] Fallback: NLST returned " + names.length + " names.");
                for (String name : names) {
                    if (name != null && !name.trim().isEmpty()) {
                        JesJob job = parseJobLine(name.trim());
                        if (job != null) jobs.add(job);
                    }
                }
            }
        }

        LOG.info("[JES] Found " + jobs.size() + " jobs.");
        return jobs;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Spool files for a single job
    // ═══════════════════════════════════════════════════════════════════

    /**
     * List spool files for a specific job.
     * <p>
     * Strategy (multi-level):
     * <ol>
     *   <li>Try {@code LIST jobId} – works with JESINTERFACELEVEL=2</li>
     *   <li>Try probing individual spool files via {@code RETR jobId.n} and
     *       extract DDName from the 125/250 FTP reply string</li>
     *   <li>Fall through to caller which will use content-based section parsing</li>
     * </ol>
     */
    public List<JesSpoolFile> listSpoolFiles(String jobId) throws IOException {
        ensureConnected();

        // ── Strategy 1: LIST jobId (JESINTERFACELEVEL=2) ────────────
        FTPFile[] files = ftp.listFiles(jobId);
        List<JesSpoolFile> spoolFiles = new ArrayList<JesSpoolFile>();

        if (files != null) {
            LOG.info("[JES] LIST " + jobId + " returned " + files.length + " raw entries.");
            for (FTPFile f : files) {
                String raw = f.getRawListing();
                if (raw == null) continue;
                LOG.info("[JES] Spool raw: " + raw);
                JesSpoolFile sf = parseSpoolLine(raw.trim());
                if (sf != null) spoolFiles.add(sf);
            }
        }

        // Fallback 1a: try NLST if listFiles() returned nothing parseable
        if (spoolFiles.isEmpty()) {
            LOG.fine("[JES] Spool listing empty for " + jobId + ", trying NLST fallback…");
            String[] names = ftp.listNames(jobId);
            if (names != null) {
                LOG.fine("[JES] NLST " + jobId + " returned " + names.length + " names.");
                for (String name : names) {
                    if (name != null && !name.trim().isEmpty()) {
                        JesSpoolFile sf = parseSpoolLine(name.trim());
                        if (sf != null) spoolFiles.add(sf);
                    }
                }
            }
        }

        // ── Strategy 2: Probe individual spool files (only in PROBE mode) ─
        Settings probeSettings = SettingsHelper.load();
        String ddNameMode = probeSettings.jesSpoolDdNameMode != null ? probeSettings.jesSpoolDdNameMode : "FAST";

        if (spoolFiles.isEmpty() && "PROBE".equalsIgnoreCase(ddNameMode)) {
            LOG.info("[JES] Trying spool probe for " + jobId + " (PROBE mode)…");
            spoolFiles = probeSpoolFilesWithContentDetection(jobId);
        }

        // In FAST or OFF mode, return empty list – caller (JobDetailTab) will use
        // getAllSpoolContent + parseSpoolSectionsFromOutput for fast loading.

        LOG.info("[JES] Job " + jobId + " has " + spoolFiles.size() + " spool files.");
        return spoolFiles;
    }

    /**
     * Probe spool files by attempting RETR on jobId.1, jobId.2, … and use
     * content-based detection to determine the DDName for each spool file.
     * <p>
     * This is the fallback for servers where JESINTERFACELEVEL=2 is not supported
     * and the RETR reply does not contain DDName information.
     * <p>
     * Stops probing after 3 consecutive misses (no more spool files).
     */
    private List<JesSpoolFile> probeSpoolFilesWithContentDetection(String jobId) {
        List<JesSpoolFile> result = new ArrayList<JesSpoolFile>();
        int consecutiveMisses = 0;
        int maxConsecutiveMisses = 3;
        int maxProbe = 200; // safety limit

        for (int spoolId = 1; spoolId <= maxProbe && consecutiveMisses < maxConsecutiveMisses; spoolId++) {
            try {
                String remoteName = jobId + "." + spoolId;
                ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
                boolean ok = ftp.retrieveFile(remoteName, baos);
                String reply = ftp.getReplyString();

                if (!ok) {
                    consecutiveMisses++;
                    LOG.fine("[JES] Probe " + remoteName + " → not found (" + (reply != null ? reply.trim() : "") + ")");
                    continue;
                }

                consecutiveMisses = 0;
                Settings settings = SettingsHelper.load();
                String enc = settings.encoding != null ? settings.encoding : "UTF-8";
                String content = baos.toString(Charset.forName(enc).name());
                int lineCount = countLines(baos.toByteArray());

                // Try FTP reply first, then content-based detection
                String ddName = extractDdNameFromReply(reply, spoolId);
                if (ddName.startsWith("SPOOL#")) {
                    // Reply didn't help – try content-based detection
                    ddName = detectDdNameFromContent(content, spoolId);
                }

                LOG.info("[JES] Probe " + remoteName + " → DDName=" + ddName
                        + " (" + lineCount + " lines)");

                result.add(new JesSpoolFile(
                        spoolId,
                        ddName,
                        "",   // stepName
                        "",   // procStep
                        "",   // dsClass
                        baos.size(),
                        lineCount
                ));
            } catch (IOException e) {
                consecutiveMisses++;
                LOG.fine("[JES] Probe " + jobId + "." + spoolId + " → IOException: " + e.getMessage());
            }
        }

        return result;
    }

    /**
     * Detect DDName from the content of a spool file by examining the first lines.
     * Uses the same heuristics as {@link #detectDdNameFromSection} but on the actual
     * content string.
     */
    static String detectDdNameFromContent(String content, int spoolId) {
        if (content == null || content.isEmpty()) {
            return "SPOOL#" + spoolId;
        }

        String[] lines = content.split("\\r?\\n");
        int probeEnd = Math.min(lines.length, 30);

        // First pass: use existing line-level detection
        for (int i = 0; i < probeEnd; i++) {
            String candidate = detectDdNameFromLine(lines[i]);
            if (candidate != null) {
                return candidate;
            }
        }

        // Second pass: check for JCL lines
        for (int i = 0; i < probeEnd; i++) {
            if (lines[i] != null && lines[i].trim().startsWith("//")) {
                return "JESJCL";
            }
        }

        // Third pass: check for well-known z/OS message prefixes that indicate JESYSMSG
        for (int i = 0; i < probeEnd; i++) {
            String trimmed = lines[i] != null ? lines[i].trim() : "";
            // JESYSMSG typically contains IEF, IGD, ICH messages and "STMT NO. MESSAGE" header
            if (trimmed.startsWith("STMT NO.") || trimmed.startsWith("STMT  NO.")) {
                return "JESYSMSG";
            }
            if (trimmed.matches("^\\s*\\d+\\s+IEF[A-Z]?\\d+.*") || trimmed.matches("^\\s*\\d+\\s+IGD\\d+.*")) {
                return "JESYSMSG";
            }
            if (trimmed.startsWith("ICH70001I") || trimmed.startsWith("IEF") || trimmed.startsWith("IEFA")) {
                return "JESYSMSG";
            }
        }

        // Fourth pass: check for IDCAMS output → SYSPRINT
        for (int i = 0; i < probeEnd; i++) {
            String trimmed = lines[i] != null ? lines[i].trim() : "";
            if (trimmed.contains("IDCAMS") && trimmed.contains("SYSTEM SERVICES")) {
                return "SYSPRINT";
            }
            if (trimmed.startsWith("IDC0") || trimmed.startsWith("IDC1")) {
                return "SYSPRINT";
            }
        }

        return "SPOOL#" + spoolId;
    }

    /**
     * Extract DDName from a JES FTP RETR reply string.
     * <p>
     * Common formats:
     * <ul>
     *   <li>{@code 125 Sending data set USER.JOBnnnnn.?.DDNAME for job JOBnnnnn DSId n}</li>
     *   <li>{@code 250 Transfer completed for DDNAME}</li>
     *   <li>Various other formats containing the dataset name with DDName as last qualifier</li>
     * </ul>
     */
    static String extractDdNameFromReply(String reply, int spoolId) {
        if (reply == null || reply.isEmpty()) {
            return "SPOOL#" + spoolId;
        }

        // Pattern: "data set XXXX.XXXX.?.DDNAME" – DDName is the last qualifier after "?"
        Matcher dsn = Pattern.compile("data\\s+set\\s+\\S+\\.\\?\\.(\\S+?)(?:\\s|$)", Pattern.CASE_INSENSITIVE).matcher(reply);
        if (dsn.find()) {
            return dsn.group(1).toUpperCase();
        }

        // Pattern: "data set XXXX.XXXX.DDNAME" – DDName is the last qualifier
        Matcher dsn2 = Pattern.compile("data\\s+set\\s+(\\S+?)(?:\\s|$)", Pattern.CASE_INSENSITIVE).matcher(reply);
        if (dsn2.find()) {
            String fullDsn = dsn2.group(1);
            int lastDot = fullDsn.lastIndexOf('.');
            if (lastDot >= 0 && lastDot < fullDsn.length() - 1) {
                return fullDsn.substring(lastDot + 1).toUpperCase();
            }
        }

        // Pattern: look for known DDNames directly in the reply
        String upper = reply.toUpperCase();
        String[] knownDDs = {"JESMSGLG", "JESJCL", "JESYSMSG", "SYSPRINT", "SYSOUT", "SYSTSPRT", "SYSIN"};
        for (String dd : knownDDs) {
            if (upper.contains(dd)) {
                return dd;
            }
        }

        return "SPOOL#" + spoolId;
    }

    private static int countLines(byte[] data) {
        int count = 0;
        for (byte b : data) {
            if (b == '\n') count++;
        }
        return count;
    }

    /**
     * Parse spool DD sections from the full concatenated output text.
     * Looks for JES separator lines like "!! END OF JES SPOOL FILE !!" and
     * DD header lines to extract section boundaries.
     * <p>
     * Returns a list of pseudo-SpoolFiles with id and ddName derived from the content.
     * This is a last-resort fallback when the FTP listing fails.
     */
    public static List<JesSpoolFile> parseSpoolSectionsFromOutput(String fullOutput) {
        List<JesSpoolFile> sections = new ArrayList<JesSpoolFile>();
        if (fullOutput == null || fullOutput.isEmpty()) {
            return sections;
        }

        String[] lines = fullOutput.split("\\r?\\n");
        int sectionStart = findNextNonEmptyLine(lines, 0);
        int sectionId = 1;

        for (int i = sectionStart; i < lines.length; i++) {
            if (!isJesSectionSeparator(lines[i])) {
                continue;
            }

            int sectionEnd = i;
            addSection(sections, lines, sectionStart, sectionEnd, sectionId);
            sectionId++;
            sectionStart = findNextNonEmptyLine(lines, i + 1);
        }

        if (sectionStart >= 0 && sectionStart < lines.length) {
            addSection(sections, lines, sectionStart, lines.length, sectionId);
        }

        return sections;
    }

    /**
     * Try to identify a DD name from a JES output header line.
     */
    private static String detectDdNameFromLine(String line) {
        if (line == null) {
            return null;
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String upper = trimmed.toUpperCase();
        String compact = upper.replaceAll("\\s+", "");

        if (compact.contains("JES2JOBLOG") || compact.contains("JESJOBLOG")) {
            return "JESMSGLG";
        }

        if (compact.contains("JES2JCL") || compact.contains("JESJCL")) {
            return "JESJCL";
        }

        if (compact.contains("SYSTEMMESSAGES")
                || compact.contains("JES2SYSTEMMESSAGES")
                || compact.contains("JESYSMSG")) {
            return "JESYSMSG";
        }

        if (compact.contains("SYSPRINT")) {
            return "SYSPRINT";
        }

        if (compact.contains("SYSOUT")) {
            return "SYSOUT";
        }

        if (compact.contains("SYSTSPRT")) {
            return "SYSTSPRT";
        }

        if (upper.startsWith("//")) {
            return "JESJCL";
        }

        return null;
    }

    private static void addSection(List<JesSpoolFile> sections,
                                   String[] lines,
                                   int sectionStart,
                                   int sectionEnd,
                                   int sectionId) {
        if (sectionStart < 0 || sectionStart >= sectionEnd) {
            return;
        }

        String ddName = detectDdNameFromSection(lines, sectionStart, sectionEnd, sectionId);
        int lineCount = countNonSeparatorLines(lines, sectionStart, sectionEnd);

        sections.add(new JesSpoolFile(
                sectionId,
                ddName,
                "",
                "",
                "",
                0,
                lineCount
        ));
    }

    private static int findNextNonEmptyLine(String[] lines, int startIndex) {
        for (int i = Math.max(0, startIndex); i < lines.length; i++) {
            if (lines[i] != null && !lines[i].trim().isEmpty()) {
                return i;
            }
        }
        return lines.length;
    }

    private static boolean isJesSectionSeparator(String line) {
        if (line == null) {
            return false;
        }

        String trimmed = line.trim();
        return trimmed.contains("END OF JES SPOOL FILE")
                || trimmed.contains("END OF JES2 SPOOL")
                || trimmed.matches("^\\s*!!\\s+END\\s+.*!!\\s*$");
    }

    private static int countNonSeparatorLines(String[] lines, int startInclusive, int endExclusive) {
        int count = 0;
        for (int i = startInclusive; i < endExclusive; i++) {
            if (!isJesSectionSeparator(lines[i])) {
                count++;
            }
        }
        return count;
    }

    private static String detectDdNameFromSection(String[] lines,
                                                  int startInclusive,
                                                  int endExclusive,
                                                  int sectionId) {
        int probeEnd = Math.min(endExclusive, startInclusive + 30);

        for (int i = startInclusive; i < probeEnd; i++) {
            String candidate = detectDdNameFromLine(lines[i]);
            if (candidate != null) {
                return candidate;
            }
        }

        for (int i = startInclusive; i < probeEnd; i++) {
            String rawLine = lines[i];
            if (rawLine == null) {
                continue;
            }

            String trimmed = rawLine.trim();
            if (trimmed.startsWith("//")) {
                return "JESJCL";
            }
        }

        return "SPOOL#" + sectionId;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Spool content retrieval
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get the content of a single spool file.
     *
     * @param jobId       e.g. "JOB12345"
     * @param spoolFileId spool file number (1, 2, 3…)
     * @return the text content
     */
    public String getSpoolContent(String jobId, int spoolFileId) throws IOException {
        ensureConnected();
        String remoteName = jobId + "." + spoolFileId;
        return retrieveAsString(remoteName);
    }

    /**
     * Get ALL spool output for a job concatenated (using the ".x" suffix).
     */
    public String getAllSpoolContent(String jobId) throws IOException {
        ensureConnected();
        return retrieveAsString(jobId + ".x");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Job deletion
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Delete a job's output from the spool.
     */
    public boolean deleteJob(String jobId) throws IOException {
        ensureConnected();
        boolean ok = ftp.deleteFile(jobId);
        String reply = ftp.getReplyString();
        LOG.info("[JES] DELETE " + jobId + " → " + ok + " reply: "
                + (reply != null ? reply.trim() : ""));
        return ok;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void close() {
        connected = false;
        try {
            if (ftp.isConnected()) {
                ftp.logout();
                ftp.disconnect();
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "[JES] Disconnect error", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Internal helpers
    // ═══════════════════════════════════════════════════════════════════

    private void ensureConnected() throws IOException {
        if (!connected || !ftp.isConnected()) {
            throw new IOException("JES-FTP nicht verbunden.");
        }
    }

    private void sendSite(String command) throws IOException {
        ftp.sendSiteCommand(command);
        LOG.fine("[JES] SITE " + command + " → " + ftp.getReplyString().trim());
    }

    private String retrieveAsString(String remoteName) throws IOException {
        Settings settings = SettingsHelper.load();
        String enc = settings.encoding != null ? settings.encoding : "UTF-8";
        ByteArrayOutputStream baos = new ByteArrayOutputStream(32768);
        boolean ok = ftp.retrieveFile(remoteName, baos);
        if (!ok) {
            String reply = ftp.getReplyString();
            throw new IOException("GET " + remoteName + " fehlgeschlagen: "
                    + (reply != null ? reply.trim() : "(keine Antwort)"));
        }
        return baos.toString(Charset.forName(enc).name());
    }

    /**
     * Parse a JES job line from LIST output.
     * Two formats supported: full (JESINTERFACELEVEL 2) and simple.
     */
    static JesJob parseJobLine(String line) {
        if (line == null || line.isEmpty()) return null;

        // Skip header lines
        String upper = line.toUpperCase();
        if (upper.startsWith("JOBNAME") || upper.startsWith("---") || upper.startsWith("OWNER")) {
            return null;
        }

        Matcher m = JOB_LINE.matcher(line);
        if (m.find()) {
            return new JesJob(
                    m.group(2).toUpperCase(),         // jobId
                    m.group(1),                        // jobName
                    m.group(3),                        // owner
                    m.group(4).toUpperCase(),          // status
                    m.group(5),                        // class
                    m.group(6),                        // retCode (may be null)
                    parseIntSafe(m.group(7), 0)        // spoolFileCount
            );
        }

        // Simpler fallback
        Matcher ms = JOB_LINE_SIMPLE.matcher(line);
        if (ms.find()) {
            return new JesJob(
                    ms.group(2).toUpperCase(),
                    ms.group(1),
                    ms.group(3),
                    ms.group(4).toUpperCase(),
                    "",
                    null,
                    0
            );
        }

        LOG.fine("[JES] Unparseable job line: " + line);
        return null;
    }

    /**
     * Parse a spool-file line from LIST JOBxxxxx output.
     * Tries multiple patterns since z/OS JES2 output varies between systems.
     */
    static JesSpoolFile parseSpoolLine(String line) {
        if (line == null || line.isEmpty()) return null;

        // Skip header/separator lines
        String upper = line.toUpperCase().trim();
        if (upper.startsWith("ID") || upper.startsWith("---") || upper.startsWith("JOBNAME")
                || upper.startsWith("BYTE") || upper.isEmpty()) {
            return null;
        }

        // Full format: ID STEP PROC CLASS DDNAME BYTES RECORDS
        Matcher m = SPOOL_FULL.matcher(line);
        if (m.find()) {
            return new JesSpoolFile(
                    Integer.parseInt(m.group(1)),
                    m.group(5),                              // ddName
                    m.group(2),                              // stepName
                    m.group(3),                              // procStep
                    m.group(4),                              // class
                    parseByteCount(m.group(6)),              // byteCount (may have commas)
                    parseIntSafe(m.group(7), 0)              // recordCount
            );
        }

        // No record count: ID STEP PROC CLASS DDNAME BYTES
        Matcher m2 = SPOOL_NO_RECORDS.matcher(line);
        if (m2.find()) {
            return new JesSpoolFile(
                    Integer.parseInt(m2.group(1)),
                    m2.group(5),
                    m2.group(2),
                    m2.group(3),
                    m2.group(4),
                    parseByteCount(m2.group(6)),
                    0
            );
        }

        // Minimal fallback: at least get the spool-file ID
        Matcher m3 = SPOOL_MINIMAL.matcher(line);
        if (m3.find()) {
            // Extract tokens to get what we can
            String[] tokens = line.trim().split("\\s+");
            String ddName = tokens.length >= 5 ? tokens[4] : tokens[tokens.length - 1];
            String step = tokens.length >= 2 ? tokens[1] : "";
            String proc = tokens.length >= 3 ? tokens[2] : "";
            String cls = tokens.length >= 4 ? tokens[3] : "";
            return new JesSpoolFile(
                    Integer.parseInt(m3.group(1)),
                    ddName, step, proc,
                    cls.length() == 1 ? cls : "",
                    0, 0
            );
        }

        LOG.info("[JES] Unparseable spool line: " + line);
        return null;
    }

    /** Parse a byte count that may contain commas (e.g. "1,234" → 1234). */
    private static long parseByteCount(String s) {
        if (s == null) return 0;
        return parseLongSafe(s.replace(",", ""), 0);
    }

    private static int parseIntSafe(String s, int fallback) {
        if (s == null) return fallback;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static long parseLongSafe(String s, long fallback) {
        if (s == null) return fallback;
        try { return Long.parseLong(s.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Parallel spool DDName probing
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Probe DDNames for spool files in parallel using N separate FTP connections.
     * Each connection fetches a subset of spool files and detects DDNames from content.
     * <p>
     * This method is used by:
     * <ul>
     *   <li>PROBE mode: direct parallel loading</li>
     *   <li>FAST mode (with background probe): background refinement</li>
     *   <li>OFF mode: background correction of SPOOL#n names</li>
     * </ul>
     *
     * @param jobId              the job ID (e.g. "J0211636")
     * @param host               FTP host
     * @param user               FTP user
     * @param password           FTP password
     * @param spoolCount         number of spool files to probe (1..spoolCount)
     * @param parallelConnections number of parallel FTP connections (1-10)
     * @return map of spoolId → detected DDName
     */
    public static Map<Integer, String> probeSpoolDdNamesParallel(
            String jobId, String host, String user, String password,
            int spoolCount, int parallelConnections) {

        Map<Integer, String> result = new ConcurrentHashMap<>();
        int threads = Math.max(1, Math.min(parallelConnections, 10));

        if (threads == 1) {
            // Single-threaded: use one connection for all
            probeSingleThread(jobId, host, user, password, spoolCount, result);
            return result;
        }

        // Multi-threaded: distribute spool IDs across N connections
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int threadIndex = t;
            futures.add(executor.submit(() -> {
                FTPClient threadFtp = null;
                try {
                    threadFtp = createJesFtpConnection(host, user, password);
                    Settings settings = SettingsHelper.load();
                    String enc = settings.encoding != null ? settings.encoding : "UTF-8";

                    for (int spoolId = threadIndex + 1; spoolId <= spoolCount; spoolId += threads) {
                        try {
                            String remoteName = jobId + "." + spoolId;
                            ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
                            boolean ok = threadFtp.retrieveFile(remoteName, baos);

                            if (ok) {
                                String content = baos.toString(Charset.forName(enc).name());
                                String reply = threadFtp.getReplyString();
                                String ddName = extractDdNameFromReply(reply, spoolId);
                                if (ddName.startsWith("SPOOL#")) {
                                    ddName = detectDdNameFromContent(content, spoolId);
                                }
                                result.put(spoolId, ddName);
                                LOG.info("[JES] Parallel probe " + remoteName + " → " + ddName
                                        + " (thread " + threadIndex + ")");
                            }
                        } catch (IOException e) {
                            LOG.fine("[JES] Parallel probe " + jobId + "." + spoolId
                                    + " failed: " + e.getMessage());
                        }
                    }
                } catch (IOException e) {
                    LOG.warning("[JES] Failed to create parallel FTP connection (thread "
                            + threadIndex + "): " + e.getMessage());
                } finally {
                    closeQuietly(threadFtp);
                }
            }));
        }

        // Wait for all threads to complete
        for (Future<?> f : futures) {
            try {
                f.get(120, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOG.warning("[JES] Parallel probe thread error: " + e.getMessage());
            }
        }
        executor.shutdown();

        return result;
    }

    /**
     * Single-threaded probe fallback (parallelConnections=1).
     */
    private static void probeSingleThread(String jobId, String host, String user, String password,
                                          int spoolCount, Map<Integer, String> result) {
        FTPClient singleFtp = null;
        try {
            singleFtp = createJesFtpConnection(host, user, password);
            Settings settings = SettingsHelper.load();
            String enc = settings.encoding != null ? settings.encoding : "UTF-8";

            for (int spoolId = 1; spoolId <= spoolCount; spoolId++) {
                try {
                    String remoteName = jobId + "." + spoolId;
                    ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
                    boolean ok = singleFtp.retrieveFile(remoteName, baos);

                    if (ok) {
                        String content = baos.toString(Charset.forName(enc).name());
                        String reply = singleFtp.getReplyString();
                        String ddName = extractDdNameFromReply(reply, spoolId);
                        if (ddName.startsWith("SPOOL#")) {
                            ddName = detectDdNameFromContent(content, spoolId);
                        }
                        result.put(spoolId, ddName);
                        LOG.info("[JES] Probe " + remoteName + " → " + ddName);
                    }
                } catch (IOException e) {
                    LOG.fine("[JES] Probe " + jobId + "." + spoolId + " failed: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            LOG.warning("[JES] Failed to create FTP connection for probe: " + e.getMessage());
        } finally {
            closeQuietly(singleFtp);
        }
    }

    /**
     * Create a new FTP connection in JES mode for parallel probing.
     */
    private static FTPClient createJesFtpConnection(String host, String user, String password)
            throws IOException {
        FTPClient client = new FTPClient();
        Settings settings = SettingsHelper.load();
        String encoding = settings.encoding != null ? settings.encoding : "UTF-8";
        client.setControlEncoding(encoding);

        int connectTimeout = settings.ftpConnectTimeoutMs;
        if (connectTimeout > 0) {
            client.setDefaultTimeout(connectTimeout);
            client.setConnectTimeout(connectTimeout);
        }

        client.connect(host);
        if (!FTPReply.isPositiveCompletion(client.getReplyCode())) {
            throw new IOException("FTP connect failed: " + client.getReplyString());
        }

        client.setSoTimeout(settings.ftpControlTimeoutMs);

        if (!client.login(user, password)) {
            throw new IOException("FTP login failed: " + client.getReplyString());
        }

        client.enterLocalPassiveMode();
        client.setFileType(FTP.ASCII_FILE_TYPE);
        client.sendSiteCommand("FILETYPE=JES");

        return client;
    }

    private static void closeQuietly(FTPClient client) {
        if (client != null) {
            try {
                if (client.isConnected()) {
                    client.logout();
                    client.disconnect();
                }
            } catch (IOException ignored) { }
        }
    }
}

