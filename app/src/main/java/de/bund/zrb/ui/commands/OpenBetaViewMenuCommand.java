package de.bund.zrb.ui.commands;

import de.bund.zrb.betaview.ui.BetaViewConnectionTab;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.login.LoginManager;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.TabbedPaneManager;
import de.bund.zrb.ui.VirtualBackendType;
import de.bund.zrb.ui.VirtualResource;
import de.bund.zrb.ui.VirtualResourceKind;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;
import java.net.URL;

/**
 * Opens a new BetaView ConnectionTab.
 * <p>
 * On open the command:
 * <ol>
 *     <li>Loads the BetaView URL from Settings (prompts if empty)</li>
 *     <li>Auto-normalises the URL to just the scheme+host (like FTP host)</li>
 *     <li>Creates the tab, wires credentials via LoginManager, and adds it</li>
 * </ol>
 */
public class OpenBetaViewMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;
    private final TabbedPaneManager tabManager;

    public OpenBetaViewMenuCommand(JFrame parent, TabbedPaneManager tabManager) {
        this.parent = parent;
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "connection.betaview";
    }

    @Override
    public String getLabel() {
        return "BetaView";
    }

    @Override
    public void perform() {
        Settings settings = SettingsHelper.load();
        String betaviewUrl = settings.betaviewUrl != null ? settings.betaviewUrl.trim() : "";

        // ── 1) Prompt for URL if not configured ─────────────────────────
        if (betaviewUrl.isEmpty()) {
            betaviewUrl = promptForUrl();
            if (betaviewUrl == null || betaviewUrl.trim().isEmpty()) {
                return; // User cancelled
            }
            betaviewUrl = betaviewUrl.trim();
        }

        // ── 2) Auto-normalise: strip to scheme+host+port if user pasted a full URL ──
        betaviewUrl = normaliseAndPersist(betaviewUrl, settings);
        if (betaviewUrl == null) {
            return; // User cancelled after seeing correction dialog
        }

        // ── 3) Create tab and wire everything ───────────────────────────
        BetaViewConnectionTab tab = new BetaViewConnectionTab();
        tab.setBaseUrl(betaviewUrl);
        tab.setFilterDefaults(
                settings.betaviewFavoriteId,
                settings.betaviewLocale,
                settings.betaviewExtension,
                settings.betaviewForm,
                settings.betaviewDaysBack
        );

        // Credentials: shared (FTP/NDV) or separate BetaView credentials.
        tab.setCredentialsProvider(betaviewHost -> {
            Settings s = SettingsHelper.load();

            if (s.betaviewUseSharedCredentials) {
                // ── Shared mode: use the same host/user as FTP/NDV ──
                // IMPORTANT: We MUST pass settings.host (not the BetaView URL host)
                // to LoginManager because it internally overwrites settings.host.
                String serverHost = s.host;
                String user = s.user;
                if (serverHost == null || serverHost.isEmpty()) {
                    JOptionPane.showMessageDialog(parent,
                            "Kein Server konfiguriert.\n"
                                    + "Bitte zuerst unter Einstellungen → Server den Host angeben.",
                            "Server fehlt", JOptionPane.WARNING_MESSAGE);
                    return null;
                }
                if (user == null || user.isEmpty()) {
                    JOptionPane.showMessageDialog(parent,
                            "Kein Benutzername konfiguriert.\n"
                                    + "Bitte zuerst unter Einstellungen → Server den Benutzernamen eintragen.",
                            "Benutzer fehlt", JOptionPane.WARNING_MESSAGE);
                    return null;
                }
                // Uses the same cache key as FTP/NDV. Once the user enters
                // the password here, FTP/NDV will NOT prompt again and vice versa.
                String password = LoginManager.getInstance().getPassword(serverHost, user);
                if (password == null) {
                    return null;
                }
                return new String[]{user, password};
            } else {
                // ── Separate BetaView credentials ──
                String bvHost = s.betaviewHost;
                String bvUser = s.betaviewUser;
                if (bvHost == null || bvHost.isEmpty()) {
                    JOptionPane.showMessageDialog(parent,
                            "Kein BetaView-Host konfiguriert.\n"
                                    + "Bitte unter Einstellungen → BetaView den Host angeben,\n"
                                    + "oder \"Credentials aus Server-Einstellungen\" aktivieren.",
                            "BetaView-Host fehlt", JOptionPane.WARNING_MESSAGE);
                    return null;
                }
                if (bvUser == null || bvUser.isEmpty()) {
                    JOptionPane.showMessageDialog(parent,
                            "Kein BetaView-Benutzer konfiguriert.\n"
                                    + "Bitte unter Einstellungen → BetaView den Benutzer angeben.",
                            "BetaView-Benutzer fehlt", JOptionPane.WARNING_MESSAGE);
                    return null;
                }
                // Try stored encrypted password first
                if (s.betaviewEncryptedPassword != null && !s.betaviewEncryptedPassword.isEmpty()) {
                    try {
                        String pw = de.bund.zrb.util.WindowsCryptoUtil.decrypt(s.betaviewEncryptedPassword);
                        if (pw != null && !pw.isEmpty()) {
                            return new String[]{bvUser, pw};
                        }
                    } catch (Exception ignore) { /* fall through */ }
                }
                // Different host than settings.host, so this won't corrupt FTP/NDV.
                String password = LoginManager.getInstance().getPassword(bvHost, bvUser);
                if (password == null) {
                    return null;
                }
                return new String[]{bvUser, password};
            }
        });

        // Document open callback: open as read-only FileTab with proper document preview
        tab.setOpenCallback((docTab, html) -> {
            // Build a stable unique path from document metadata
            String docId = docTab.docId() != null ? docTab.docId() : "";
            String favId = docTab.favId() != null ? docTab.favId() : "";
            String linkID = docTab.linkID() != null ? docTab.linkID() : "";
            String title = docTab.title() != null && !docTab.title().isEmpty()
                    ? docTab.title() : "BetaView Dokument";

            String stablePath;
            if (!docId.isEmpty()) {
                stablePath = "betaview://doc/" + docId + "?favid=" + favId + "&linkID=" + linkID;
            } else {
                stablePath = "betaview://doc/" + title.replaceAll("[^a-zA-Z0-9_.-]", "_");
            }

            // Extract text content from the document HTML for the raw text view.
            // The HTML is the full BetaView document page — we pass it as-is so the
            // DocumentPreviewPanel can parse page-count, navigation tokens, etc.
            // For the FileTab content we give a placeholder; the real rendering
            // happens via page-get requests below.
            String placeholder = "[BetaView Dokument: " + title + "]\n"
                    + "Seiten werden vom Server geladen...";

            VirtualResource resource = new VirtualResource(
                    null, VirtualResourceKind.FILE, stablePath,
                    VirtualBackendType.BETAVIEW, null, null);

            tabManager.openFileTab(resource, placeholder, null, null, null);

            // After opening the tab, initiate page loading in the background.
            // The DocumentPreviewPanel in the BetaView integration handles the
            // actual paging — but here we need to wire it through the
            // BetaViewClient for the page.get.action calls.
            // For now the content shown is the placeholder; proper DocumentPreviewPanel
            // rendering in MainframeMate FileTabs requires further integration
            // (connecting the preview panel's PageChangeListener to
            //  BetaViewClient.postFormText(session, "document.page.get.action", ...)).
        });

        tabManager.addTab(tab);

        // Initiate connection (login + CSRF) in background
        tab.connectInBackground();
    }

    // ── URL prompt ──────────────────────────────────────────────────────

    private String promptForUrl() {
        return (String) JOptionPane.showInputDialog(
                parent,
                "BetaView-URL ist noch nicht konfiguriert.\n"
                        + "Bitte die Base-URL des BetaView-Servers eingeben\n"
                        + "(z.B. https://betaview.example.com):",
                "BetaView-URL eingeben",
                JOptionPane.QUESTION_MESSAGE,
                null, null, "https://");
    }

    // ── URL normalisation ───────────────────────────────────────────────

    /**
     * If the user typed a full URL with path (e.g. {@code https://host.example.com/betaview/login.action}),
     * strip it down to just the base ({@code https://host.example.com/betaview/}).
     * If the URL was corrected, show an info dialog and persist the corrected value.
     *
     * @return the normalised URL, or {@code null} if the user cancelled
     */
    private String normaliseAndPersist(String raw, Settings settings) {
        String normalised = normaliseUrl(raw);

        boolean changed = !normalised.equals(raw);

        if (changed) {
            int choice = JOptionPane.showConfirmDialog(parent,
                    "Die eingegebene URL wurde automatisch korrigiert:\n\n"
                            + "Eingabe:   " + raw + "\n"
                            + "Korrigiert: " + normalised + "\n\n"
                            + "Falls die automatische Korrektur falsch war,\n"
                            + "können Sie die URL unter Einstellungen → BetaView anpassen.\n\n"
                            + "Mit der korrigierten URL fortfahren?",
                    "URL korrigiert",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.INFORMATION_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return null;
            }
        }

        // Persist (always, so first-time prompt is saved too)
        if (!normalised.equals(settings.betaviewUrl)) {
            settings.betaviewUrl = normalised;
            SettingsHelper.save(settings);
        }

        return normalised;
    }

    /**
     * Normalise a BetaView URL:
     * <ul>
     *     <li>Ensure scheme (default https)</li>
     *     <li>If the URL has a path with more than one segment
     *         (e.g. {@code /betaview/login.action}), keep only the first path segment
     *         ({@code /betaview/})</li>
     *     <li>If the URL has no meaningful path, keep it as-is with trailing slash</li>
     *     <li>Ensure trailing slash</li>
     * </ul>
     */
    static String normaliseUrl(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";

        // Ensure scheme
        if (!s.contains("://")) {
            s = "https://" + s;
        }

        try {
            URL url = new URL(s);
            String scheme = url.getProtocol();
            String host = url.getHost();
            int port = url.getPort();

            String path = url.getPath();
            if (path == null) path = "";

            // Extract first path segment if present (e.g. "/betaview" from "/betaview/login.action")
            String contextPath = "";
            if (!path.isEmpty() && !path.equals("/")) {
                // Remove leading slash, split by /
                String stripped = path.startsWith("/") ? path.substring(1) : path;
                int slash = stripped.indexOf('/');
                if (slash >= 0) {
                    contextPath = "/" + stripped.substring(0, slash);
                } else {
                    // Single segment like "/betaview" – keep it
                    contextPath = "/" + stripped;
                }
            }

            StringBuilder result = new StringBuilder();
            result.append(scheme).append("://").append(host);
            if (port > 0 && port != 80 && port != 443) {
                result.append(':').append(port);
            }
            result.append(contextPath);
            if (!result.toString().endsWith("/")) {
                result.append('/');
            }
            return result.toString();
        } catch (Exception e) {
            // If URL parsing fails, just ensure trailing slash
            if (!s.endsWith("/")) s += "/";
            return s;
        }
    }
}
