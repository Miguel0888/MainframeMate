package de.bund.zrb.ui.commands;

import de.bund.zrb.confluence.ConfluenceConnectionConfig;
import de.bund.zrb.confluence.ConfluencePrefetchService;
import de.bund.zrb.confluence.ConfluenceRestClient;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.ConfluenceConnectionTab;
import de.bund.zrb.ui.ConfluenceReaderTab;
import de.bund.zrb.ui.TabbedPaneManager;
import de.bund.zrb.util.CredentialStore;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Opens a Confluence connection tab.
 * <p>
 * Reads Confluence entries (category "Confluence") from the password entries,
 * builds a {@link ConfluenceRestClient} with mTLS + Basic Auth, and opens
 * a browsing tab.
 */
public class OpenConfluenceMenuCommand extends ShortcutMenuCommand {

    private static final Logger LOG = Logger.getLogger(OpenConfluenceMenuCommand.class.getName());
    private final TabbedPaneManager tabManager;

    public OpenConfluenceMenuCommand(TabbedPaneManager tabManager) {
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "connection.confluence";
    }

    @Override
    public String getLabel() {
        return "Confluence\u2026";
    }

    @Override
    public void perform() {
        // ── 1. Find Confluence entries ──
        List<ConfluenceEntry> confluenceEntries = findConfluenceEntries();

        if (confluenceEntries.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "Keine Confluence-Eintr\u00e4ge konfiguriert.\n\n"
                            + "Bitte unter Einstellungen \u2192 Passw\u00f6rter mindestens einen\n"
                            + "Eintrag mit Kategorie \"Confluence\", URL und Zertifikat anlegen.",
                    "Keine Confluence-Konfiguration", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ── 2. If multiple entries, let user choose ──
        ConfluenceEntry selected;
        if (confluenceEntries.size() == 1) {
            selected = confluenceEntries.get(0);
        } else {
            String[] names = new String[confluenceEntries.size()];
            for (int i = 0; i < confluenceEntries.size(); i++) {
                names[i] = confluenceEntries.get(i).displayName
                        + " (" + confluenceEntries.get(i).url + ")";
            }
            String choice = (String) JOptionPane.showInputDialog(null,
                    "Welche Confluence-Instanz soll ge\u00f6ffnet werden?",
                    "Confluence ausw\u00e4hlen",
                    JOptionPane.QUESTION_MESSAGE, null, names, names[0]);
            if (choice == null) return;
            int idx = 0;
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(choice)) { idx = i; break; }
            }
            selected = confluenceEntries.get(idx);
        }

        // ── 3. Resolve credentials ──
        String user = selected.userName;
        String pass = selected.password;
        if (selected.requiresLogin) {
            if ((user == null || user.isEmpty()) && (pass == null || pass.isEmpty())) {
                try {
                    String[] creds = CredentialStore.resolveIncludingEmpty("pwd:" + selected.id);
                    if (creds != null) {
                        user = creds[0];
                        pass = creds[1];
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[Confluence] Credentials aufl\u00f6sen fehlgeschlagen", e);
                }
            }

            // If still no password (savePassword is off / no session cache) → prompt user
            if (user != null && !user.isEmpty() && (pass == null || pass.isEmpty())) {
                pass = promptForPassword("Confluence \u2014 " + selected.displayName, user);
                if (pass == null) {
                    return; // User cancelled
                }
                // Store in session cache if entry allows it
                storeInSessionIfAllowed(selected.id, user, pass);
            }

            if (user == null || user.isEmpty()) {
                // No username at all → prompt for both user and password
                String[] prompted = promptForCredentials("Confluence \u2014 " + selected.displayName);
                if (prompted == null) {
                    return; // User cancelled
                }
                user = prompted[0];
                pass = prompted[1];
                storeInSessionIfAllowed(selected.id, user, pass);
            }
        } else {
            // No login required — use empty credentials (cert-only or anonymous)
            user = "";
            pass = "";
        }

        // ── 4. Build client + open tab ──
        try {
            Settings settings = SettingsHelper.load();
            ConfluenceConnectionConfig config = new ConfluenceConnectionConfig(
                    selected.url, user, pass, selected.certAlias, 15000, 30000);
            ConfluenceRestClient client = new ConfluenceRestClient(config);

            ConfluenceConnectionTab tab = new ConfluenceConnectionTab(client, selected.url);

            // Wire up prefetch: cache search results in the background
            try {
                de.bund.zrb.archive.store.CacheRepository cacheRepo =
                        de.bund.zrb.archive.store.CacheRepository.getInstance();
                ConfluencePrefetchService prefetch = new ConfluencePrefetchService(
                        client, cacheRepo,
                        settings.confluencePrefetchCacheMaxMb,
                        settings.confluencePrefetchMaxItems,
                        settings.confluencePrefetchConcurrency);
                tab.setPrefetchService(prefetch);
            } catch (Exception prefetchErr) {
                // CacheRepository not available — prefetch disabled, no problem
                LOG.log(Level.FINE, "[Confluence] Prefetch disabled (CacheRepository unavailable)", prefetchErr);
            }

            // Wire open callback: creates ConfluenceReaderTab when user double-clicks/enters a page
            tab.setOpenCallback((readerClient, readerBaseUrl, pageId, pageTitle, htmlContent, outline) -> {
                ConfluenceReaderTab readerTab = new ConfluenceReaderTab(
                        readerClient, readerBaseUrl, pageId, pageTitle, htmlContent, outline);
                // Wire link callback: clicking links in reader opens new reader tabs
                readerTab.setLinkCallback(linkedPageId ->
                        tab.openPageByIdAsReaderTab(linkedPageId));
                // Wire outline callback for RightDrawer
                if (tabManager.getMainframeContext() instanceof de.bund.zrb.ui.MainFrame) {
                    de.bund.zrb.ui.MainFrame mainFrame = (de.bund.zrb.ui.MainFrame) tabManager.getMainframeContext();
                    de.bund.zrb.ui.drawer.RightDrawer rd = mainFrame.getRightDrawer();
                    if (rd != null) {
                        readerTab.setOutlineCallback((outlineNode, title) -> {
                            java.util.function.Consumer<String> scroller = anchor -> readerTab.scrollToAnchor(anchor);
                            rd.updateWikiOutline(outlineNode, title, scroller);
                        });
                    }
                }
                tabManager.addTab(readerTab);
            });

            // Wire outline callback: update RightDrawer when preview changes
            if (tabManager.getMainframeContext() instanceof de.bund.zrb.ui.MainFrame) {
                de.bund.zrb.ui.MainFrame mf = (de.bund.zrb.ui.MainFrame) tabManager.getMainframeContext();
                de.bund.zrb.ui.drawer.RightDrawer rightDrawer = mf.getRightDrawer();
                if (rightDrawer != null) {
                    tab.setOutlineCallback((outlineNode, title) -> {
                        java.util.function.Consumer<String> scroller = anchor -> tab.scrollToAnchor(anchor);
                        rightDrawer.updateWikiOutline(outlineNode, title, scroller);
                    });
                }

                // Wire dependency callback: update LeftDrawer with ancestors/children/labels
                tab.setDependencyCallback(item -> {
                    // The TabbedPaneManager handles this when switching tabs,
                    // but we also trigger it proactively for inline preview changes.
                    tabManager.refreshRelationsForActiveTab();
                });
            }

            // Wire up index callback: indexes a single Confluence page into Lucene on demand
            tab.setIndexCallback((pageId, title, spaceKey, html) -> {
                try {
                    String docId = "confluence://" + pageId;
                    de.bund.zrb.rag.service.RagService rag = de.bund.zrb.rag.service.RagService.getInstance();
                    // Remove old version if already indexed (re-index)
                    if (rag.isIndexed(docId)) {
                        rag.removeDocument(docId);
                    }
                    String text = stripHtmlForIndex(html);
                    if (text.isEmpty()) return 0;
                    de.bund.zrb.ingestion.model.document.DocumentMetadata meta =
                            de.bund.zrb.ingestion.model.document.DocumentMetadata.builder()
                                    .sourceName(title)
                                    .mimeType("text/html")
                                    .attribute("sourcePath", docId)
                                    .attribute("confluencePageId", pageId)
                                    .attribute("confluenceSpace", spaceKey)
                                    .build();
                    de.bund.zrb.ingestion.model.document.Document doc =
                            de.bund.zrb.ingestion.model.document.Document.builder()
                                    .metadata(meta)
                                    .paragraph(text)
                                    .build();
                    rag.indexDocument(docId, title, doc, false);
                    LOG.info("[Confluence] Indexed on demand: " + title + " (" + text.length() + " chars)");
                    de.bund.zrb.rag.service.RagService.IndexedDocument indexed = rag.getIndexedDocument(docId);
                    return indexed != null ? indexed.chunkCount : 1;
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[Confluence] Index on demand failed: " + title, e);
                    return -1;
                }
            });

            tabManager.addTab(tab);

            // Auto-save checkbox state on every toggle
            tab.setStateSaveCallback(() -> {
                de.bund.zrb.model.Settings s = de.bund.zrb.helper.SettingsHelper.load();
                tab.addApplicationState(s.applicationState);
                de.bund.zrb.helper.SettingsHelper.save(s);
            });
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Confluence] Tab \u00f6ffnen fehlgeschlagen", e);
            // Build readable error chain
            StringBuilder detail = new StringBuilder();
            Throwable current = e;
            int depth = 0;
            while (current != null && depth < 5) {
                if (depth > 0) detail.append("\n  \u2190 ");
                detail.append(current.getClass().getSimpleName());
                if (current.getMessage() != null) {
                    detail.append(": ").append(current.getMessage());
                }
                current = current.getCause();
                depth++;
            }
            JOptionPane.showMessageDialog(null,
                    "Confluence-Verbindung fehlgeschlagen:\n\n" + detail.toString(),
                    "Verbindungsfehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Read all password entries with category "Confluence" from settings.
     */
    private List<ConfluenceEntry> findConfluenceEntries() {
        Settings settings = SettingsHelper.load();
        List<ConfluenceEntry> result = new ArrayList<ConfluenceEntry>();
        for (Settings.PasswordEntryMeta meta : settings.passwordEntries) {
            if ("Confluence".equalsIgnoreCase(meta.category)) {
                String user = "";
                String pass = "";
                try {
                    String[] cred = CredentialStore.resolveIncludingEmpty("pwd:" + meta.id);
                    if (cred != null) {
                        user = cred[0];
                        pass = cred[1];
                    }
                } catch (Exception ignore) { }
                result.add(new ConfluenceEntry(
                        meta.id,
                        meta.displayName != null ? meta.displayName : meta.id,
                        meta.url != null ? meta.url : "",
                        meta.certAlias != null ? meta.certAlias : "",
                        user, pass, meta.requiresLogin));
            }
        }
        return result;
    }

    private static final class ConfluenceEntry {
        final String id;
        final String displayName;
        final String url;
        final String certAlias;
        final String userName;
        final String password;
        final boolean requiresLogin;

        ConfluenceEntry(String id, String displayName, String url,
                        String certAlias, String userName, String password,
                        boolean requiresLogin) {
            this.id = id;
            this.displayName = displayName;
            this.url = url;
            this.certAlias = certAlias;
            this.userName = userName;
            this.password = password;
            this.requiresLogin = requiresLogin;
        }
    }

    /**
     * Simple HTML→text conversion for indexing: strip tags, decode entities, collapse whitespace.
     */
    private static String stripHtmlForIndex(String html) {
        if (html == null || html.isEmpty()) return "";
        String text = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", "");
        text = text.replaceAll("(?i)<(br|p|div|h[1-6]|li|tr)[^>]*>", "\n");
        text = text.replaceAll("<[^>]+>", "");
        text = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                   .replace("&quot;", "\"").replace("&nbsp;", " ").replace("&#39;", "'");
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }

    // ── Credential prompt helpers ────────────────────────────────────────

    /**
     * Prompt the user for a password only (username is known).
     *
     * @return the password, or {@code null} if the user cancelled
     */
    private static String promptForPassword(String title, String knownUser) {
        JPasswordField passField = new JPasswordField(25);
        JPanel panel = new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.insets = new java.awt.Insets(4, 4, 4, 4);
        gbc.anchor = java.awt.GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Benutzer:"), gbc);
        gbc.gridx = 1; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        JLabel userLabel = new JLabel(knownUser);
        panel.add(userLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = java.awt.GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Passwort:"), gbc);
        gbc.gridx = 1; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(passField, gbc);

        SwingUtilities.invokeLater(passField::requestFocusInWindow);

        int result = JOptionPane.showConfirmDialog(null, panel, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return null;

        String password = new String(passField.getPassword());
        return password.isEmpty() ? null : password;
    }

    /**
     * Prompt the user for username + password.
     *
     * @return {@code String[]{user, password}} or {@code null} if cancelled/empty
     */
    private static String[] promptForCredentials(String title) {
        JTextField userField = new JTextField(25);
        JPasswordField passField = new JPasswordField(25);
        JPanel panel = new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.insets = new java.awt.Insets(4, 4, 4, 4);
        gbc.anchor = java.awt.GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Benutzer:"), gbc);
        gbc.gridx = 1; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(userField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = java.awt.GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Passwort:"), gbc);
        gbc.gridx = 1; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(passField, gbc);

        int result = JOptionPane.showConfirmDialog(null, panel, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return null;

        String user = userField.getText().trim();
        String password = new String(passField.getPassword());
        if (user.isEmpty()) return null;
        return new String[]{user, password};
    }

    /**
     * Store credentials in session cache if the matching password entry allows it.
     */
    private static void storeInSessionIfAllowed(String entryId, String user, String password) {
        Settings settings = SettingsHelper.load();
        for (Settings.PasswordEntryMeta meta : settings.passwordEntries) {
            if (entryId.equals(meta.id)) {
                if (!meta.savePassword && meta.sessionCache) {
                    CredentialStore.storeInSession("pwd:" + entryId, user, password);
                }
                break;
            }
        }
    }
}
