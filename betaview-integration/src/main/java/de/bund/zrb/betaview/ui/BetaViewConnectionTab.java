package de.bund.zrb.betaview.ui;

import de.bund.zrb.betaview.infrastructure.*;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * MainframeMate ConnectionTab for BetaView.
 * <p>
 * This is the integration layer that maps the BetaView standalone app concepts
 * into the MainframeMate tab system:
 * <ul>
 *   <li>BetaView's {@code ConnectionTabPanel} (filter tabs + results table) is embedded directly</li>
 *   <li>BetaView's {@code DocumentTabbedPane} (right-side document previews) is mapped to
 *       MainframeMate's {@code TabbedPaneManager.openFileTab()} via the {@link DocumentOpenCallback}</li>
 *   <li>BetaView's {@code ConnectDialog} is replaced by MainframeMate's {@code LoginManager}
 *       via the {@link CredentialsProvider}</li>
 * </ul>
 */
public class BetaViewConnectionTab implements ConnectionTab {

    // ── Callbacks (set by OpenBetaViewMenuCommand) ──────────────────────

    public interface DocumentOpenCallback {
        /**
         * @param tab         parsed DocumentTab with docId/favId/linkID/title
         * @param htmlContent the full document HTML (for DocumentPreviewPanel.loadDocument)
         */
        void openDocument(DocumentTab tab, String htmlContent);
    }

    public interface CredentialsProvider {
        String[] getCredentials(String host);
    }

    public interface CloseAllDocumentTabsCallback {
        void closeAllBetaViewDocumentTabs();
    }

    // ── State ───────────────────────────────────────────────────────────

    private final JPanel mainPanel;
    private final JLabel connectingLabel = new JLabel("Verbinde...");
    private final JProgressBar connectProgress = new JProgressBar();

    private String baseUrlText = "";
    private BetaViewAppProperties defaults;
    private DocumentOpenCallback openCallback;
    private CredentialsProvider credentialsProvider;
    private CloseAllDocumentTabsCallback closeAllTabsCallback;

    private ConnectionTabPanel connectionTabPanel;
    private BetaViewClient client;
    private BetaViewSession session;
    private URL baseUrl;

    public BetaViewConnectionTab() {
        mainPanel = new JPanel(new BorderLayout());
        JPanel placeholder = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        connectProgress.setIndeterminate(true);
        placeholder.add(connectingLabel);
        placeholder.add(connectProgress);
        mainPanel.add(placeholder, BorderLayout.NORTH);
    }

    // ── Configuration ───────────────────────────────────────────────────

    public void setBaseUrl(String url) {
        this.baseUrlText = url != null ? url : "";
    }

    public void setFilterDefaults(String favoriteId, String locale, String extension,
                                  String form, int daysBack) {
        this.defaults = new BetaViewAppProperties(
                favoriteId, locale, extension, form, "*", "*", daysBack);
    }

    public void setOpenCallback(DocumentOpenCallback callback) {
        this.openCallback = callback;
    }

    public void setCredentialsProvider(CredentialsProvider provider) {
        this.credentialsProvider = provider;
    }

    public void setCloseAllTabsCallback(CloseAllDocumentTabsCallback cb) {
        this.closeAllTabsCallback = cb;
    }

    /** Active client after successful login. May be null before connect. */
    public BetaViewClient getClient()   { return client; }
    /** Active session after successful login. May be null before connect. */
    public BetaViewSession getSession() { return session; }

    // ── Connect ─────────────────────────────────────────────────────────

    public void connectInBackground() {
        if (baseUrlText.isEmpty()) {
            connectingLabel.setText("Keine URL konfiguriert");
            connectProgress.setVisible(false);
            return;
        }

        String user = null;
        String password = null;
        if (credentialsProvider != null) {
            try {
                String host = extractHost(baseUrlText);
                String[] creds = credentialsProvider.getCredentials(host);
                if (creds == null || creds.length < 2) {
                    connectingLabel.setText("Anmeldung abgebrochen");
                    connectProgress.setVisible(false);
                    return;
                }
                user = creds[0];
                password = creds[1];
            } catch (Exception ex) {
                connectingLabel.setText("Fehler: " + ex.getMessage());
                connectProgress.setVisible(false);
                return;
            }
        }

        if (user == null || user.isEmpty() || password == null || password.isEmpty()) {
            connectingLabel.setText("Anmeldedaten fehlen");
            connectProgress.setVisible(false);
            return;
        }

        final String fUser = user;
        final String fPassword = password;

        new SwingWorker<ConnectResult, Void>() {
            @Override
            protected ConnectResult doInBackground() throws Exception {
                baseUrl = normalizeBaseUrl(baseUrlText);
                BetaViewClient c = new BetaViewHttpClient(new BetaViewBaseUrl(baseUrl));
                LoadResultsHtmlUseCase useCase = new LoadResultsHtmlUseCase(c);
                BetaViewSession s = c.login(new BetaViewCredentials(fUser, fPassword));
                String displayName = fUser + "@" + baseUrl.getHost();
                return new ConnectResult(baseUrl, c, s, useCase, displayName, true);
            }

            @Override
            protected void done() {
                try {
                    ConnectResult result = get();
                    client = result.client();
                    session = result.session();
                    baseUrl = result.baseUrl();

                    connectionTabPanel = new ConnectionTabPanel(result, defaults);
                    connectionTabPanel.setConnectionListener(new ConnectionTabPanel.ConnectionListener() {
                        @Override
                        public void onOpenDocument(String html, String action) {
                            handleOpenDocument(html, action);
                        }
                        @Override
                        public void onOpenBookmarkDocument(String html, SidebarPanel.BookmarkItem item) {
                            handleOpenBookmarkDocument(html, item);
                        }
                        @Override
                        public void onCloseAllTabs() {
                            // Send closeAllDocuments.action to the server
                            new SwingWorker<Void, Void>() {
                                @Override
                                protected Void doInBackground() {
                                    try {
                                        client.getText(session, "closeAllDocuments.action");
                                    } catch (Exception ignore) { }
                                    return null;
                                }
                                @Override
                                protected void done() {
                                    // Close all BetaView document tabs in MainframeMate
                                    if (closeAllTabsCallback != null) {
                                        closeAllTabsCallback.closeAllBetaViewDocumentTabs();
                                    }
                                }
                            }.execute();
                        }
                    });

                    mainPanel.removeAll();
                    mainPanel.add(connectionTabPanel, BorderLayout.CENTER);
                    mainPanel.revalidate();
                    mainPanel.repaint();

                    fetchAndOpenServerTabs();

                    // Load sidebar data (saved searches + bookmarks) like BetaViewSwingFrame does
                    connectionTabPanel.refreshSavedSearches();
                    connectionTabPanel.refreshBookmarks();

                } catch (Exception ex) {
                    connectingLabel.setText("Verbindung fehlgeschlagen: " +
                            (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    connectProgress.setVisible(false);
                    ex.printStackTrace();
                }
            }
        }.execute();
    }

    // ── Document handling (from BetaViewSwingFrame) ─────────────────────

    private void handleOpenDocument(String html, String action) {
        List<DocumentTab> tabs = DocumentTabParser.parse(html);
        DocumentTab activeTab = null;
        for (DocumentTab t : tabs) {
            if (t.isActive()) activeTab = t;
        }
        if (activeTab == null) {
            activeTab = new DocumentTab("", "", action, "Dokument", "", action, true);
        }
        if (openCallback != null) {
            openCallback.openDocument(activeTab, html);
        }
    }

    private void handleOpenBookmarkDocument(String html, SidebarPanel.BookmarkItem item) {
        List<DocumentTab> tabs = DocumentTabParser.parse(html);
        DocumentTab activeTab = null;
        for (DocumentTab t : tabs) {
            if (t.isActive()) activeTab = t;
        }
        if (activeTab == null) {
            activeTab = new DocumentTab("", "", item.action(), item.name(), "", item.action(), true);
        }
        if (openCallback != null) {
            openCallback.openDocument(activeTab, html);
        }
    }

    private void fetchAndOpenServerTabs() {
        if (client == null || session == null) return;
        new SwingWorker<List<DocumentTab>, Void>() {
            @Override
            protected List<DocumentTab> doInBackground() throws Exception {
                LinkedHashMap<String, String> form = new LinkedHashMap<String, String>();
                if (session.csrfToken() != null) {
                    form.put("csrfToken", session.csrfToken().value());
                }
                String json = client.postFormText(session, "getDBBookmarksJSON.json.action", form);
                return parseDocBrowserBookmarks(json);
            }
            @Override
            protected void done() {
                try {
                    List<DocumentTab> tabs = get();
                    for (DocumentTab tab : tabs) {
                        loadAndOpenServerTab(tab);
                    }
                } catch (Exception ignored) { }
            }
        }.execute();
    }

    private void loadAndOpenServerTab(final DocumentTab tab) {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return client.getText(session, tab.openAction());
            }
            @Override
            protected void done() {
                try {
                    String html = get();
                    if (openCallback != null) {
                        // Parse the returned HTML to find the real active tab metadata
                        List<DocumentTab> parsed = DocumentTabParser.parse(html);
                        DocumentTab activeTab = null;
                        for (DocumentTab t : parsed) {
                            if (t.isActive()) activeTab = t;
                        }
                        if (activeTab == null) {
                            activeTab = tab; // fallback to the server-tab info
                        }
                        openCallback.openDocument(activeTab, html);
                    }
                } catch (Exception ignored) { }
            }
        }.execute();
    }

    // ── Server-tab JSON parsing (from BetaViewSwingFrame) ───────────────

    private static List<DocumentTab> parseDocBrowserBookmarks(String json) {
        java.util.ArrayList<DocumentTab> result = new java.util.ArrayList<DocumentTab>();
        if (json == null || json.isEmpty()) return result;
        int arrStart = json.indexOf("\"dbbookmarks\":[");
        if (arrStart < 0) return result;
        arrStart = json.indexOf('[', arrStart);
        int arrEnd = json.indexOf(']', arrStart);
        if (arrEnd < 0) return result;
        String arrContent = json.substring(arrStart + 1, arrEnd);
        String[] objects = arrContent.split("\\},\\s*\\{");
        for (String obj : objects) {
            obj = obj.trim();
            if (obj.startsWith("{")) obj = obj.substring(1);
            if (obj.endsWith("}")) obj = obj.substring(0, obj.length() - 1);
            String fav = extractJsonString(obj, "fav");
            if (!"<docbrowsertab>".equals(fav)) continue;
            String link = extractJsonString(obj, "link");
            String linkID = extractJsonString(obj, "linkID");
            String name = extractJsonString(obj, "name");
            String desc = extractJsonString(obj, "description");
            String docId = "", favId = "";
            for (String part : link.split("&")) {
                if (part.startsWith("docid=")) docId = part.substring(6);
                else if (part.startsWith("favid=")) favId = part.substring(6);
            }
            String openAction = "opendocumentlink.action?" + link;
            result.add(new DocumentTab(docId, favId, linkID, name, desc, openAction, false));
        }
        return result;
    }

    private static String extractJsonString(String obj, String key) {
        String search = "\"" + key + "\":\"";
        int start = obj.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        int end = obj.indexOf('"', start);
        return end > start ? obj.substring(start, end) : "";
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String extractHost(String url) {
        try { return new URL(url).getHost(); }
        catch (Exception e) { return url; }
    }

    private URL normalizeBaseUrl(String text) throws MalformedURLException {
        String s = text.trim();
        if (!s.endsWith("/")) s += "/";
        return new URL(s);
    }

    // ── ConnectionTab interface ─────────────────────────────────────────

    @Override public String getTitle()   { return "BetaView"; }
    @Override public String getTooltip() { return "BetaView Recherche"; }
    @Override public JComponent getComponent() { return mainPanel; }
    @Override public void onClose() { }
    @Override public void saveIfApplicable() { }

    @Override
    public JPopupMenu createContextMenu(Runnable closeAction) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem closeItem = new JMenuItem("Tab schließen");
        closeItem.addActionListener(e -> closeAction.run());
        menu.add(closeItem);
        return menu;
    }

    @Override public void focusSearchField() { }
    @Override public void searchFor(String query) { }
    @Override public String getContent() { return ""; }
    @Override public void markAsChanged() { }
    @Override public String getPath() { return "betaview://" + baseUrlText; }
    @Override public Type getType() { return Type.CONNECTION; }
}

