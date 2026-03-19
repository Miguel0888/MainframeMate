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
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches a web page and extracts all hyperlinks.
 * Used by the SharePoint settings panel to discover available links
 * from the configured parent page URL.
 * <p>
 * Uses {@link HttpURLConnection} instead of OkHttp because Java's built-in
 * HTTP client supports <b>NTLM authentication</b> natively on Windows.
 * This is essential for corporate SharePoint servers that use Windows
 * Integrated Authentication (Kerberos/NTLM) — the same mechanism that
 * allows the browser to log in automatically with the AD account.
 * <p>
 * <b>Authentication strategy:</b>
 * <ol>
 *   <li>Try transparent NTLM/SSO (via {@code NTLMAuthenticationCallback}) — no credentials needed</li>
 *   <li>Fall back to explicit credentials from {@link Settings#sharepointUser} /
 *       {@link Settings#sharepointEncryptedPassword}</li>
 *   <li>If no credentials configured and SSO fails → 401 error with helpful message</li>
 * </ol>
 */
public final class SharePointLinkFetcher {

    private static final Logger LOG = Logger.getLogger(SharePointLinkFetcher.class.getName());

    private SharePointLinkFetcher() {}

    /**
     * Download the HTML at {@code parentUrl} and extract all {@code <a href>} links.
     * Uses Windows NTLM/Kerberos SSO if available, or stored SharePoint credentials.
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

        // Enable transparent NTLM for trusted hosts (Java 8 internal API)
        enableTransparentNtlm();

        // Install a temporary Authenticator for NTLM/Basic challenges
        Authenticator previous = installAuthenticator(user, password);
        try {
            String html = downloadPage(parentUrl.trim());
            return extractLinks(html, parentUrl);
        } finally {
            // Restore previous authenticator
            Authenticator.setDefault(previous);
        }
    }

    /**
     * Download a page using {@link HttpURLConnection} (supports NTLM on Windows).
     */
    private static String downloadPage(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "MainframeMate/5.x SharePointLinkFetcher");
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(20_000);
        conn.setReadTimeout(30_000);

        int code = conn.getResponseCode();
        LOG.info("[SP-Fetch] HTTP " + code + " for " + urlStr);

        if (code == 401) {
            throw new RuntimeException(
                    "HTTP 401 – Zugriff verweigert.\n\n"
                            + "Die automatische Windows-Anmeldung (SSO) hat nicht funktioniert.\n"
                            + "Bitte konfigurieren Sie Ihre SharePoint-Zugangsdaten\n"
                            + "im Abschnitt \"Zugangsdaten\" weiter unten und versuchen Sie es erneut.");
        }

        if (code != 200) {
            throw new RuntimeException("HTTP " + code + " " + conn.getResponseMessage());
        }

        // Detect encoding from Content-Type header
        String encoding = "UTF-8";
        String contentType = conn.getContentType();
        if (contentType != null) {
            for (String param : contentType.split(";")) {
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

        LOG.info("[SP-Fetch] Downloaded " + html.length() + " chars from " + urlStr);
        return html.toString();
    }

    /**
     * Install a custom {@link Authenticator} that provides NTLM/Basic credentials
     * for SharePoint 401 challenges.
     *
     * @return the previous authenticator (may be {@code null})
     */
    private static Authenticator installAuthenticator(final String user, final String password) {
        // Java 8 doesn't expose getDefault(), so we can't save/restore cleanly.
        // We set our authenticator and restore to null afterwards.
        Authenticator auth = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                LOG.fine("[SP-Fetch] Auth challenge: scheme=" + getRequestingScheme()
                        + " host=" + getRequestingHost() + " prompt=" + getRequestingPrompt());

                if (user != null && !user.trim().isEmpty() && password != null && !password.isEmpty()) {
                    LOG.fine("[SP-Fetch] Responding with stored credentials for user: " + user);
                    return new PasswordAuthentication(user, password.toCharArray());
                }

                // No credentials — return null; Java may still try transparent NTLM
                LOG.fine("[SP-Fetch] No credentials available, relying on transparent NTLM");
                return null;
            }
        };
        Authenticator.setDefault(auth);
        return null; // Java 8 has no getDefault()
    }

    /**
     * Try to enable transparent NTLM authentication.
     * <ul>
     *   <li><b>Java 9+</b>: system property {@code jdk.http.ntlm.transparentAuth=allHosts}
     *       makes {@link HttpURLConnection} use the current Windows user's Kerberos/NTLM
     *       credentials automatically — no password needed (true SSO).</li>
     *   <li><b>Java 8</b>: transparent NTLM would require calling the internal
     *       {@code sun.net.www.protocol.http.ntlm.NTLMAuthenticationCallback}, which is
     *       an abstract class and cannot be proxied via reflection. Instead, the
     *       {@link Authenticator} installed by {@link #installAuthenticator} provides
     *       the configured SharePoint credentials for the NTLM handshake.</li>
     * </ul>
     */
    private static void enableTransparentNtlm() {
        try {
            // Works on Java 9+ — harmless no-op on Java 8
            System.setProperty("jdk.http.ntlm.transparentAuth", "allHosts");
            LOG.fine("[SP-Fetch] Set jdk.http.ntlm.transparentAuth=allHosts");
        } catch (Exception e) {
            LOG.log(Level.FINE, "[SP-Fetch] Could not set transparent NTLM property", e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Link extraction (unchanged)
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
}
