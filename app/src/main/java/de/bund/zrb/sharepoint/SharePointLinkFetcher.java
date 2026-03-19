package de.bund.zrb.sharepoint;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.util.WindowsCryptoUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches a web page and extracts all hyperlinks.
 * Used by the SharePoint settings panel to discover available links
 * from the configured parent page URL.
 * <p>
 * <b>Authentication strategy (in order):</b>
 * <ol>
 *   <li><b>PowerShell + System.Net.WebClient + UseDefaultCredentials</b> —
 *       true Windows SSO (Kerberos/NTLM via SSPI). Uses the same mechanism
 *       as the browser: the current AD user's ticket is sent automatically,
 *       no password needed.</li>
 *   <li><b>PowerShell + System.Net.WebClient + explicit credentials</b> —
 *       if SSO fails and credentials are configured in Settings.</li>
 *   <li><b>HttpURLConnection + Authenticator</b> — Java-native fallback
 *       with explicit credentials from Settings.</li>
 * </ol>
 */
public final class SharePointLinkFetcher {

    private static final Logger LOG = Logger.getLogger(SharePointLinkFetcher.class.getName());

    /** Timeout for PowerShell process in seconds. */
    private static final int PS_TIMEOUT_SECONDS = 30;

    private SharePointLinkFetcher() {}

    /**
     * Download the HTML at {@code parentUrl} and extract all {@code <a href>} links.
     * Uses Windows SSO if available, or stored SharePoint credentials.
     *
     * @return list of discovered links (name + URL), never null
     */
    public static List<SharePointSite> fetchLinks(String parentUrl) throws Exception {
        Settings settings = SettingsHelper.load();
        String user = settings.sharepointUser;
        String password = decryptPassword(settings.sharepointEncryptedPassword);
        return fetchLinks(parentUrl, user, password);
    }

    /**
     * Download the HTML at {@code parentUrl} with explicit credentials.
     *
     * @param user     DOMAIN&#92;user or user@domain (may be {@code null} for SSO-only)
     * @param password plaintext password (may be {@code null} for SSO-only)
     * @return list of discovered links (name + URL), never null
     */
    public static List<SharePointSite> fetchLinks(String parentUrl, String user, String password)
            throws Exception {
        if (parentUrl == null || parentUrl.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String url = parentUrl.trim();

        // ── 1. Try PowerShell with Windows SSO (UseDefaultCredentials) ──
        try {
            String html = downloadViaPowerShellSso(url);
            if (html != null && !html.isEmpty()) {
                LOG.info("[SP-Fetch] SSO download succeeded (" + html.length() + " chars)");
                return extractLinks(html, url);
            }
        } catch (Exception e) {
            LOG.info("[SP-Fetch] SSO download failed: " + e.getMessage());
        }

        // ── 2. Try PowerShell with explicit credentials ─────────────
        if (user != null && !user.trim().isEmpty() && password != null && !password.isEmpty()) {
            try {
                String html = downloadViaPowerShellCredentials(url, user, password);
                if (html != null && !html.isEmpty()) {
                    LOG.info("[SP-Fetch] Credentials download succeeded (" + html.length() + " chars)");
                    return extractLinks(html, url);
                }
            } catch (Exception e) {
                LOG.info("[SP-Fetch] Credentials download failed: " + e.getMessage());
            }
        }

        // ── 3. Fallback: HttpURLConnection with Authenticator ───────
        try {
            String html = downloadViaHttpUrlConnection(url, user, password);
            if (html != null && !html.isEmpty()) {
                LOG.info("[SP-Fetch] HttpURLConnection download succeeded (" + html.length() + " chars)");
                return extractLinks(html, url);
            }
        } catch (Exception e) {
            LOG.info("[SP-Fetch] HttpURLConnection download failed: " + e.getMessage());
        }

        // ── All methods failed ──────────────────────────────────────
        throw new RuntimeException(
                "Die Seite konnte nicht abgerufen werden (401 / Zugriff verweigert).\n\n"
                        + "Alle Authentifizierungsmethoden sind fehlgeschlagen:\n"
                        + "• Windows SSO (Kerberos/NTLM) — Ihre AD-Kennung wurde nicht akzeptiert\n"
                        + "• Gespeicherte Zugangsdaten — "
                        + (user != null && !user.isEmpty() ? "Benutzer \"" + user + "\" abgelehnt" : "nicht konfiguriert")
                        + "\n\n"
                        + "Bitte prüfen Sie:\n"
                        + "• Ist die URL korrekt?\n"
                        + "• Haben Sie Zugriff auf diese Seite im Browser?\n"
                        + "• Geben Sie Ihre AD-Zugangsdaten im Abschnitt \"Zugangsdaten\" ein");
    }

    // ═══════════════════════════════════════════════════════════
    //  Download methods
    // ═══════════════════════════════════════════════════════════

    /**
     * Download a page via PowerShell using {@code System.Net.WebClient}
     * with {@code UseDefaultCredentials = $true} — true Windows SSO.
     * <p>
     * This is the same mechanism the browser uses: Windows SSPI sends the
     * current user's Kerberos ticket or NTLM hash automatically.
     */
    private static String downloadViaPowerShellSso(String url) throws Exception {
        // PowerShell script that uses .NET WebClient with Windows credentials
        // Also sets proxy credentials for corporate proxy environments
        String script = "$ProgressPreference = 'SilentlyContinue'; "
                + "[System.Net.ServicePointManager]::SecurityProtocol = "
                + "[System.Net.SecurityProtocolType]::Tls12 -bor "
                + "[System.Net.SecurityProtocolType]::Tls11 -bor "
                + "[System.Net.SecurityProtocolType]::Tls; "
                + "$wc = New-Object System.Net.WebClient; "
                + "$wc.UseDefaultCredentials = $true; "
                + "if ($wc.Proxy -ne $null) { "
                + "$wc.Proxy.Credentials = [System.Net.CredentialCache]::DefaultNetworkCredentials "
                + "}; "
                + "$wc.Encoding = [System.Text.Encoding]::UTF8; "
                + "$wc.DownloadString('" + escapePsString(url) + "')";

        return executePowerShell(script);
    }

    /**
     * Download a page via PowerShell using {@code System.Net.WebClient}
     * with explicit {@code NetworkCredential}.
     */
    private static String downloadViaPowerShellCredentials(String url, String user, String password)
            throws Exception {
        // Split DOMAIN\\user if present
        String domain = "";
        String username = user;
        if (user.contains("\\")) {
            String[] parts = user.split("\\\\", 2);
            domain = parts[0];
            username = parts[1];
        }

        String script = "$ProgressPreference = 'SilentlyContinue'; "
                + "[System.Net.ServicePointManager]::SecurityProtocol = "
                + "[System.Net.SecurityProtocolType]::Tls12 -bor "
                + "[System.Net.SecurityProtocolType]::Tls11 -bor "
                + "[System.Net.SecurityProtocolType]::Tls; "
                + "$wc = New-Object System.Net.WebClient; "
                + "$wc.Credentials = New-Object System.Net.NetworkCredential("
                + "'" + escapePsString(username) + "', "
                + "'" + escapePsString(password) + "'"
                + (domain.isEmpty() ? "" : ", '" + escapePsString(domain) + "'")
                + "); "
                + "$wc.Encoding = [System.Text.Encoding]::UTF8; "
                + "$wc.DownloadString('" + escapePsString(url) + "')";

        return executePowerShell(script);
    }

    /**
     * Download a page via {@link HttpURLConnection} with a Java {@link Authenticator}.
     * Fallback for environments where PowerShell is blocked.
     */
    private static String downloadViaHttpUrlConnection(String url, String user, String password)
            throws Exception {
        // Set system property for Java 9+ transparent NTLM (harmless on Java 8)
        System.setProperty("jdk.http.ntlm.transparentAuth", "allHosts");

        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (user != null && !user.trim().isEmpty() && password != null && !password.isEmpty()) {
                    return new PasswordAuthentication(user, password.toCharArray());
                }
                return null;
            }
        });

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "MainframeMate/5.x");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(30_000);

            int code = conn.getResponseCode();
            if (code != 200) {
                throw new RuntimeException("HTTP " + code);
            }

            String encoding = "UTF-8";
            String ct = conn.getContentType();
            if (ct != null) {
                for (String param : ct.split(";")) {
                    param = param.trim();
                    if (param.toLowerCase().startsWith("charset=")) {
                        encoding = param.substring(8).trim();
                        break;
                    }
                }
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), encoding));
            StringBuilder html = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                html.append(line).append("\n");
            }
            reader.close();
            conn.disconnect();
            return html.toString();
        } finally {
            Authenticator.setDefault(null);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PowerShell execution
    // ═══════════════════════════════════════════════════════════

    /**
     * Execute a PowerShell command and return stdout.
     *
     * @throws Exception if PowerShell fails or times out
     */
    private static String executePowerShell(String script) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-Command", script);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        // Read stdout in a separate thread to avoid deadlock
        final StringBuilder stdout = new StringBuilder();
        final StringBuilder stderr = new StringBuilder();

        Thread stdoutReader = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), Charset.forName("UTF-8")));
                String line;
                while ((line = reader.readLine()) != null) {
                    stdout.append(line).append("\n");
                }
            } catch (Exception e) {
                LOG.log(Level.FINE, "[SP-Fetch] stdout reader error", e);
            }
        }, "SP-Fetch-stdout");

        Thread stderrReader = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), Charset.forName("UTF-8")));
                String line;
                while ((line = reader.readLine()) != null) {
                    stderr.append(line).append("\n");
                }
            } catch (Exception e) {
                LOG.log(Level.FINE, "[SP-Fetch] stderr reader error", e);
            }
        }, "SP-Fetch-stderr");

        stdoutReader.start();
        stderrReader.start();

        boolean finished = process.waitFor(PS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("PowerShell-Timeout nach " + PS_TIMEOUT_SECONDS + " Sekunden");
        }

        stdoutReader.join(3000);
        stderrReader.join(3000);

        int exitCode = process.exitValue();
        String out = stdout.toString().trim();
        String err = stderr.toString().trim();

        LOG.fine("[SP-Fetch] PowerShell exit=" + exitCode
                + " stdout=" + out.length() + " chars"
                + (err.isEmpty() ? "" : " stderr=" + err.substring(0, Math.min(200, err.length()))));

        if (exitCode != 0) {
            String msg = err.isEmpty() ? "Exit-Code " + exitCode : err;
            if (msg.length() > 300) msg = msg.substring(0, 300) + "…";
            throw new RuntimeException("PowerShell-Fehler: " + msg);
        }

        return out;
    }

    // ═══════════════════════════════════════════════════════════
    //  Link extraction
    // ═══════════════════════════════════════════════════════════

    /**
     * Parse all {@code <a href="...">label</a>} from HTML and return them as
     * {@link SharePointSite} instances.  The {@code selected} flag is false by default;
     * the user picks which ones are actual SharePoint sites in the Settings panel.
     */
    static List<SharePointSite> extractLinks(String html, String baseUrl) {
        List<SharePointSite> result = new ArrayList<SharePointSite>();
        if (html == null || html.isEmpty()) return result;

        Pattern p = Pattern.compile(
                "<a\\s[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(html);
        Set<String> seen = new HashSet<String>();

        while (m.find()) {
            String href = m.group(1).trim();
            String label = m.group(2).replaceAll("<[^>]+>", "").trim();

            // Skip non-navigable
            if (href.isEmpty() || href.startsWith("javascript:") || href.startsWith("mailto:")
                    || href.startsWith("tel:") || href.equals("#")) {
                continue;
            }

            // Resolve relative
            if (!href.startsWith("http://") && !href.startsWith("https://")) {
                href = resolveRelative(baseUrl, href);
            }

            if (seen.contains(href)) continue;
            seen.add(href);

            if (label.isEmpty()) {
                label = href.length() > 60 ? href.substring(0, 60) + "…" : href;
            }
            if (label.length() > 120) {
                label = label.substring(0, 120) + "…";
            }

            result.add(new SharePointSite(label, href, false));
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    private static String resolveRelative(String base, String relative) {
        if (relative.startsWith("//")) return "https:" + relative;
        if (relative.startsWith("/")) {
            try {
                java.net.URL u = new java.net.URL(base);
                return u.getProtocol() + "://" + u.getHost()
                        + (u.getPort() > 0 ? ":" + u.getPort() : "") + relative;
            } catch (Exception e) {
                return relative;
            }
        }
        int lastSlash = base.lastIndexOf('/');
        return lastSlash >= 0 ? base.substring(0, lastSlash + 1) + relative : base + "/" + relative;
    }

    private static String decryptPassword(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) return null;
        try {
            return WindowsCryptoUtil.decrypt(encrypted);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[SP-Fetch] Failed to decrypt password", e);
            return null;
        }
    }

    /**
     * Escape a string for use inside a PowerShell single-quoted string.
     * Single quotes are escaped by doubling them: {@code '} → {@code ''}.
     */
    private static String escapePsString(String s) {
        if (s == null) return "";
        return s.replace("'", "''");
    }
}
