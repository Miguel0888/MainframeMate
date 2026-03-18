package de.bund.zrb.files.impl.ftp.jes;

import de.bund.zrb.files.auth.ConnectionId;
import de.bund.zrb.files.auth.Credentials;
import de.bund.zrb.files.auth.CredentialsProvider;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Submits JCL content as a job via FTP JES interface on z/OS.
 * <p>
 * Protocol:
 * <ol>
 *   <li>Connect &amp; login using the existing credential infrastructure.</li>
 *   <li>Switch to JES mode: {@code SITE FILETYPE=JES}</li>
 *   <li>Store the JCL content (ASCII transfer) – the server interprets it as a job.</li>
 *   <li>Extract the Job-ID from the FTP reply strings.</li>
 * </ol>
 */
public final class JesFtpJobSubmitter {

    private static final Logger LOG = Logger.getLogger(JesFtpJobSubmitter.class.getName());

    /**
     * Matches z/OS job identifiers like JOB12345, JOB00001, STC00001, TSU00042.
     * z/OS may return 1–8 digit suffixes depending on configuration.
     * Also matches the "J0012345" short form.
     */
    private static final Pattern JOB_ID_PATTERN =
            Pattern.compile("\\b(JOB|STC|TSU|J)\\d{1,8}\\b", Pattern.CASE_INSENSITIVE);

    private JesFtpJobSubmitter() {
        // utility – not instantiated
    }

    /**
     * Submit JCL content via FTP JES.
     *
     * @param connectionId        identifies host + user
     * @param credentialsProvider  resolves the password
     * @param jclContent           the JCL source to submit
     * @return result containing the extracted job-ID
     * @throws JesSubmitException on any failure (wraps root cause)
     */
    public static JobSubmitResult submit(ConnectionId connectionId,
                                         CredentialsProvider credentialsProvider,
                                         String jclContent) throws JesSubmitException {

        if (connectionId == null) {
            throw new JesSubmitException("Keine Verbindungsinformationen vorhanden (ConnectionId ist null).");
        }
        if (jclContent == null || jclContent.trim().isEmpty()) {
            throw new JesSubmitException("JCL-Inhalt ist leer.");
        }

        Settings settings = SettingsHelper.load();
        String host = connectionId.getHost();
        String user = connectionId.getUsername();

        Credentials credentials;
        try {
            credentials = credentialsProvider.resolve(connectionId)
                    .orElseThrow(() -> new JesSubmitException("Keine Anmeldeinformationen verfügbar."));
        } catch (JesSubmitException e) {
            throw e;
        } catch (de.bund.zrb.util.JnaBlockedException e) {
            throw e; // must not be swallowed — user needs to switch password method
        } catch (de.bund.zrb.util.PowerShellBlockedException e) {
            throw e; // must not be swallowed — user needs to switch password method
        } catch (Exception e) {
            throw new JesSubmitException("Fehler beim Auflösen der Anmeldeinformationen: " + e.getMessage(), e);
        }

        FTPClient ftp = new FTPClient();
        try {
            // ── Connect ──────────────────────────────────────────────
            String encoding = settings.encoding != null ? settings.encoding : "UTF-8";
            ftp.setControlEncoding(encoding);

            int connectTimeout = settings.ftpConnectTimeoutMs;
            if (connectTimeout > 0) {
                ftp.setDefaultTimeout(connectTimeout);
                ftp.setConnectTimeout(connectTimeout);
            }

            ftp.connect(host);
            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
                throw new JesSubmitException("FTP-Verbindung fehlgeschlagen: " + ftp.getReplyString());
            }

            int controlTimeout = settings.ftpControlTimeoutMs;
            ftp.setSoTimeout(controlTimeout);

            // ── Login ────────────────────────────────────────────────
            if (!ftp.login(credentials.getUsername(), credentials.getPassword())) {
                throw new JesSubmitException("FTP-Anmeldung fehlgeschlagen: " + ftp.getReplyString());
            }

            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTP.ASCII_FILE_TYPE);

            // ── JES mode ─────────────────────────────────────────────
            if (!ftp.sendSiteCommand("FILETYPE=JES")) {
                String reply = ftp.getReplyString();
                throw new JesSubmitException(
                        "JES-Submit nicht möglich. Server erlaubt FILETYPE=JES nicht oder Berechtigung fehlt.\n"
                                + "FTP-Antwort: " + (reply != null ? reply.trim() : "(keine)"));
            }
            LOG.info("[JES] SITE FILETYPE=JES accepted – reply: " + ftp.getReplyString().trim());

            // ── Upload JCL ───────────────────────────────────────────
            Charset cs = Charset.forName(encoding);
            byte[] jclBytes = jclContent.getBytes(cs);
            InputStream in = new ByteArrayInputStream(jclBytes);

            boolean stored = ftp.storeFile("JCL", in);
            String fullReply = ftp.getReplyString();   // multi-line reply as single string
            String[] replyStrings = ftp.getReplyStrings(); // individual reply lines
            int replyCode = ftp.getReplyCode();

            LOG.info("[JES] storeFile reply (code " + replyCode + "): "
                    + (fullReply != null ? fullReply.trim() : "(null)"));

            if (!stored || !FTPReply.isPositiveCompletion(replyCode)) {
                StringBuilder msg = new StringBuilder("Job-Submit fehlgeschlagen (FTP Code ");
                msg.append(replyCode).append(").");
                if (fullReply != null) {
                    msg.append('\n').append(fullReply.trim());
                }
                throw new JesSubmitException(msg.toString());
            }

            // ── Extract Job-ID ───────────────────────────────────────
            // Try extraction from the full reply string first (most reliable),
            // then fall back to individual reply lines.
            String jobId = extractJobId(fullReply);
            if (jobId == null && replyStrings != null) {
                for (String line : replyStrings) {
                    jobId = extractJobId(line);
                    if (jobId != null) break;
                }
            }
            if (jobId == null) {
                // Fallback – still successful but ID unknown
                LOG.warning("[JES] Could not extract Job-ID from reply: "
                        + (fullReply != null ? fullReply.trim() : "(null)"));
                jobId = "(unbekannt – Reply: "
                        + (fullReply != null ? fullReply.trim().replace("\n", " ") : "leer") + ")";
            }

            // ── Jobname from JCL (first JOB card) ────────────────────
            String jobName = extractJobName(jclContent);

            LOG.info("[JES] Job submitted: " + jobId + " / " + jobName);
            return new JobSubmitResult(jobId, jobName, host, user);

        } catch (JesSubmitException e) {
            throw e;
        } catch (IOException e) {
            throw new JesSubmitException("FTP-Fehler beim Job-Submit: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new JesSubmitException("Unerwarteter Fehler beim Job-Submit: " + e.getMessage(), e);
        } finally {
            try {
                if (ftp.isConnected()) {
                    ftp.logout();
                    ftp.disconnect();
                }
            } catch (IOException ignore) {
                // best effort
            }
        }
    }

    /**
     * Extract a Job-ID from a reply string.
     * Typical z/OS replies:
     * <ul>
     *   <li>{@code 250-It is known to JES as JOB12345}</li>
     *   <li>{@code 250 JOB JOB12345 submitted}</li>
     *   <li>{@code 250-JOB JOB00042 IS SUBMITTED}</li>
     * </ul>
     *
     * @return the Job-ID or null if not found
     */
    private static String extractJobId(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher m = JOB_ID_PATTERN.matcher(text);
        if (m.find()) {
            return m.group().toUpperCase();
        }
        return null;
    }

    /**
     * Try to extract the job name from the first JOB card in the JCL.
     * Typical pattern: {@code //JOBNAME  JOB ...}
     */
    private static String extractJobName(String jcl) {
        if (jcl == null) return null;
        String[] lines = jcl.split("\\r?\\n", 50);
        Pattern jobCard = Pattern.compile("^//([A-Z$#@][A-Z0-9$#@]{0,7})\\s+JOB\\b", Pattern.CASE_INSENSITIVE);
        for (String line : lines) {
            Matcher m = jobCard.matcher(line);
            if (m.find()) {
                return m.group(1).toUpperCase();
            }
        }
        return null;
    }
}

