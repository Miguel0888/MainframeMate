package de.bund.zrb.betaview.ui;

import de.bund.zrb.betaview.domain.*;
import de.bund.zrb.betaview.infrastructure.BetaViewGatewayHttpAdapter;
import de.bund.zrb.betaview.infrastructure.BetaViewSession;
import de.bund.zrb.betaview.infrastructure.BetaViewClient;
import de.bund.zrb.betaview.port.BetaViewGateway;
import de.bund.zrb.betaview.usecase.LoadBetaViewDocumentUseCase;
import de.bund.zrb.betaview.usecase.SearchBetaViewUseCase;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.util.List;

/**
 * MainframeMate ConnectionTab for BetaView search and navigation.
 * <p>
 * Connection settings (URL) come from Settings, credentials from the shared LoginManager
 * (same as FTP/NDV). Only the search filter is editable within this tab.
 */
public class BetaViewConnectionTab implements ConnectionTab {

    private final JPanel mainPanel;
    private final BetaViewGateway gateway;
    private final SearchBetaViewUseCase searchUseCase;
    private final LoadBetaViewDocumentUseCase loadDocumentUseCase;

    // Filter fields (pre-filled from Settings)
    private final JTextField favoriteIdField = new JTextField();
    private final JTextField localeField = new JTextField();
    private final JTextField extensionField = new JTextField();
    private final JTextField formField = new JTextField();
    private final JTextField daysBackField = new JTextField("60");

    // Actions
    private final JButton searchButton = new JButton("Suche starten");
    private final JLabel statusLabel = new JLabel("Nicht verbunden");
    private final JProgressBar progressBar = new JProgressBar();

    // Results
    private final JEditorPane htmlView = new JEditorPane();
    private final DefaultListModel<BetaViewDocumentRef> resultListModel = new DefaultListModel<BetaViewDocumentRef>();
    private final JList<BetaViewDocumentRef> resultList = new JList<BetaViewDocumentRef>(resultListModel);

    // Callback for opening documents in FileTab
    private DocumentOpenCallback openCallback;

    // Credentials provider – set externally by the MenuCommand (avoids compile dependency on app)
    private CredentialsProvider credentialsProvider;

    // HTML navigator for server-side hyperlink navigation
    private BetaViewHtmlNavigator navigator;
    private HyperlinkListener currentHyperlinkListener;

    // Base URL – set externally from Settings
    private String baseUrl = "";

    public BetaViewConnectionTab() {
        this.gateway = new BetaViewGatewayHttpAdapter();
        this.searchUseCase = new SearchBetaViewUseCase(gateway);
        this.loadDocumentUseCase = new LoadBetaViewDocumentUseCase(gateway);
        this.mainPanel = buildUi();
        wireActions();
    }

    // ── External configuration (set by MenuCommand) ─────────────────────

    /**
     * Callback interface for opening a document in a MainframeMate FileTab.
     */
    public interface DocumentOpenCallback {
        void openDocument(BetaViewDocumentRef ref, String content);
    }

    /**
     * Callback for obtaining credentials (user/password) from the shared LoginManager.
     */
    public interface CredentialsProvider {
        /** Returns [user, password] or null if user cancels. */
        String[] getCredentials(String host);
    }

    public void setOpenCallback(DocumentOpenCallback callback) {
        this.openCallback = callback;
    }

    public void setCredentialsProvider(CredentialsProvider provider) {
        this.credentialsProvider = provider;
    }

    /** Set the BetaView base URL (from Settings). */
    public void setBaseUrl(String url) {
        this.baseUrl = url != null ? url : "";
    }

    /** Pre-fill filter fields with defaults from Settings. */
    public void setFilterDefaults(String favoriteId, String locale, String extension, String form, int daysBack) {
        favoriteIdField.setText(favoriteId != null ? favoriteId : "");
        localeField.setText(locale != null ? locale : "de");
        extensionField.setText(extension != null ? extension : "*");
        formField.setText(form != null ? form : "APZF");
        daysBackField.setText(String.valueOf(daysBack > 0 ? daysBack : 60));
    }

    // ── UI Construction ─────────────────────────────────────────────────

    private JPanel buildUi() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Top: Filter only (connection comes from Settings)
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        topPanel.add(buildFilterPanel(), BorderLayout.CENTER);
        topPanel.add(buildStatusPanel(), BorderLayout.SOUTH);

        // Center: Split between result list and HTML view
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

        JScrollPane listScroll = new JScrollPane(resultList);
        listScroll.setPreferredSize(new Dimension(250, 400));
        splitPane.setLeftComponent(listScroll);

        htmlView.setContentType("text/html");
        htmlView.setEditable(false);
        JScrollPane htmlScroll = new JScrollPane(htmlView);
        htmlScroll.setPreferredSize(new Dimension(700, 400));
        splitPane.setRightComponent(htmlScroll);

        splitPane.setDividerLocation(280);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(splitPane, BorderLayout.CENTER);

        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        panel.add(progressBar, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildFilterPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Suchfilter"));
        GridBagConstraints c = gbc();

        addRow(p, c, 0, "Favorite ID", favoriteIdField);
        addRow(p, c, 1, "Locale", localeField);
        addRow(p, c, 2, "Extension", extensionField);
        addRow(p, c, 3, "Form", formField);
        addRow(p, c, 4, "Tage zurück", daysBackField);

        c.gridx = 1;
        c.gridy = 5;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.LINE_START;
        p.add(searchButton, c);

        return p;
    }

    private JPanel buildStatusPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        p.add(statusLabel, BorderLayout.WEST);
        return p;
    }

    // ── Actions ─────────────────────────────────────────────────────────

    private void wireActions() {
        searchButton.addActionListener(e -> doSearch());

        resultList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof BetaViewDocumentRef) {
                    setText(((BetaViewDocumentRef) value).displayName());
                }
                return this;
            }
        });

        resultList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                BetaViewDocumentRef ref = resultList.getSelectedValue();
                if (ref != null) {
                    doOpenDocument(ref);
                }
            }
        });

        // Note: HyperlinkListener is installed via BetaViewHtmlNavigator after successful search
    }

    private void doSearch() {
        if (baseUrl.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel,
                    "BetaView-URL ist nicht konfiguriert.\nBitte unter Einstellungen → BetaView die Base URL eintragen.",
                    "Konfiguration fehlt", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Get credentials from shared LoginManager
        String user = null;
        String password = null;

        if (credentialsProvider != null) {
            try {
                String host = extractHost(baseUrl);
                String[] creds = credentialsProvider.getCredentials(host);
                if (creds == null || creds.length < 2) {
                    statusLabel.setText("Anmeldung abgebrochen");
                    return;
                }
                user = creds[0];
                password = creds[1];
            } catch (Exception ex) {
                showError(ex);
                return;
            }
        }

        if (user == null || user.isEmpty() || password == null || password.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Benutzername oder Passwort nicht verfügbar.\nBitte zuerst über FTP oder NDV anmelden.",
                    "Anmeldung erforderlich", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int daysBack;
        try {
            daysBack = Integer.parseInt(daysBackField.getText().trim());
        } catch (NumberFormatException e) {
            daysBack = 60;
        }

        final BetaViewSearchQuery query = new BetaViewSearchQuery(
                baseUrl, user, password,
                favoriteIdField.getText().trim(),
                localeField.getText().trim(),
                extensionField.getText().trim(),
                formField.getText().trim(),
                daysBack
        );

        setBusy(true);
        statusLabel.setText("Suche läuft...");

        new SwingWorker<BetaViewSearchResult, Void>() {
            @Override
            protected BetaViewSearchResult doInBackground() throws Exception {
                return searchUseCase.execute(query);
            }

            @Override
            protected void done() {
                try {
                    BetaViewSearchResult result = get();
                    resultListModel.clear();
                    for (BetaViewDocumentRef ref : result.documents()) {
                        resultListModel.addElement(ref);
                    }

                    // Install navigator from the session (for hyperlink handling)
                    installNavigator();

                    if (navigator != null) {
                        navigator.showInitialHtml(result.rawHtml());
                    } else {
                        htmlView.setText(result.rawHtml());
                        htmlView.setCaretPosition(0);
                    }

                    statusLabel.setText(result.documents().size() + " Treffer gefunden");
                } catch (Exception ex) {
                    statusLabel.setText("Suche fehlgeschlagen");
                    showError(ex);
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void doOpenDocument(BetaViewDocumentRef ref) {
        if (openCallback == null) return;

        setBusy(true);
        statusLabel.setText("Lade Dokument: " + ref.displayName() + "...");

        new SwingWorker<BetaViewDocument, Void>() {
            @Override
            protected BetaViewDocument doInBackground() throws Exception {
                return loadDocumentUseCase.execute(ref);
            }

            @Override
            protected void done() {
                try {
                    BetaViewDocument doc = get();
                    openCallback.openDocument(doc.ref(), doc.content());
                    statusLabel.setText("Geöffnet: " + ref.displayName());
                } catch (Exception ex) {
                    statusLabel.setText("Fehler beim Laden");
                    showError(ex);
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void installNavigator() {
        // Remove old listener if exists
        if (currentHyperlinkListener != null) {
            htmlView.removeHyperlinkListener(currentHyperlinkListener);
            currentHyperlinkListener = null;
        }

        // Get session and client from the gateway adapter
        if (gateway instanceof BetaViewGatewayHttpAdapter) {
            BetaViewGatewayHttpAdapter adapter = (BetaViewGatewayHttpAdapter) gateway;
            BetaViewSession session = adapter.getSession();
            BetaViewClient client = adapter.getClient();

            if (session != null && client != null) {
                try {
                    java.net.URL bUrl = new java.net.URL(baseUrl);
                    navigator = new BetaViewHtmlNavigator(client, session, htmlView, bUrl);
                    htmlView.addHyperlinkListener(navigator);
                    currentHyperlinkListener = navigator;
                } catch (Exception e) {
                    System.err.println("[BetaView] Failed to install navigator: " + e.getMessage());
                }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String extractHost(String url) {
        try {
            return new java.net.URL(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }

    private void setBusy(boolean busy) {
        progressBar.setVisible(busy);
        searchButton.setEnabled(!busy);
    }

    private void showError(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isEmpty()) msg = ex.getClass().getName();
        if (ex.getCause() != null && ex.getCause().getMessage() != null) {
            msg = ex.getCause().getMessage();
        }
        JOptionPane.showMessageDialog(mainPanel, msg, "Fehler", JOptionPane.ERROR_MESSAGE);
        ex.printStackTrace();
    }

    private GridBagConstraints gbc() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 5, 3, 5);
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        return c;
    }

    private void addRow(JPanel panel, GridBagConstraints c, int row, String label, Component field) {
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = 1; c.gridy = row; c.weightx = 1.0;
        panel.add(field, c);
    }

    // ── ConnectionTab / FtpTab contract ─────────────────────────────────

    @Override
    public String getTitle() {
        return "BetaView";
    }

    @Override
    public String getTooltip() {
        return "BetaView Recherche";
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void onClose() {
        // Nothing to clean up
    }

    @Override
    public void saveIfApplicable() {
        // Nothing to save
    }

    @Override
    public JPopupMenu createContextMenu(Runnable closeAction) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem closeItem = new JMenuItem("Tab schließen");
        closeItem.addActionListener(e -> closeAction.run());
        menu.add(closeItem);
        return menu;
    }

    @Override
    public void focusSearchField() {
        favoriteIdField.requestFocusInWindow();
    }

    @Override
    public void searchFor(String query) {
        extensionField.setText(query);
    }

    // ── Bookmarkable ────────────────────────────────────────────────────

    @Override
    public String getContent() {
        return "";
    }

    @Override
    public void markAsChanged() {
        // No-op
    }

    @Override
    public String getPath() {
        return "betaview://" + baseUrl;
    }

    @Override
    public Type getType() {
        return Type.CONNECTION;
    }
}

