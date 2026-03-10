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
import java.util.List;
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

    // Spool file line: ID STEPNAME PROCSTEP CLASS DDNAME BYTECOUNT
    //   Example:   2   JES2        N/A        A  JESJCL      1234     20
    private static final Pattern SPOOL_LINE = Pattern.compile(
            "^\\s*(\\d+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S)\\s+(\\S+)\\s+(\\d+)\\s+(\\d+)",
            Pattern.CASE_INSENSITIVE);

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
     */
    public List<JesSpoolFile> listSpoolFiles(String jobId) throws IOException {
        ensureConnected();

        FTPFile[] files = ftp.listFiles(jobId);
        List<JesSpoolFile> spoolFiles = new ArrayList<JesSpoolFile>();

        if (files != null) {
            for (FTPFile f : files) {
                String raw = f.getRawListing();
                if (raw == null) continue;
                JesSpoolFile sf = parseSpoolLine(raw.trim());
                if (sf != null) spoolFiles.add(sf);
            }
        }

        LOG.info("[JES] Job " + jobId + " has " + spoolFiles.size() + " spool files.");
        return spoolFiles;
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
     */
    static JesSpoolFile parseSpoolLine(String line) {
        if (line == null || line.isEmpty()) return null;

        // Skip header/separator lines
        String upper = line.toUpperCase().trim();
        if (upper.startsWith("ID") || upper.startsWith("---") || upper.startsWith("JOBNAME")) {
            return null;
        }

        Matcher m = SPOOL_LINE.matcher(line);
        if (m.find()) {
            return new JesSpoolFile(
                    Integer.parseInt(m.group(1)),      // id
                    m.group(5),                         // ddName
                    m.group(2),                         // stepName
                    m.group(3),                         // procStep
                    m.group(4),                         // class
                    parseLongSafe(m.group(6), 0),       // byteCount
                    parseIntSafe(m.group(7), 0)         // recordCount
            );
        }

        LOG.fine("[JES] Unparseable spool line: " + line);
        return null;
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
}

