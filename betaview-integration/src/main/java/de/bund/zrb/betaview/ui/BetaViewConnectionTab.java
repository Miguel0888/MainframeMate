package de.bund.zrb.betaview.ui;

import de.bund.zrb.betaview.infrastructure.*;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

/**
 * MainframeMate ConnectionTab for BetaView search and navigation.
 * <p>
 * This tab mirrors the tested BetaViewSwingFrame from the betaview-example project 1:1.
 * The only differences are:
 * <ul>
 *   <li>It implements {@link ConnectionTab} instead of extending JFrame</li>
 *   <li>Credentials come from the shared LoginManager (via CredentialsProvider callback)</li>
 *   <li>Connection settings come from MainframeMate Settings</li>
 * </ul>
 * <p>
 * Flow: Connect (login + CSRF) → Load Results → Navigate via hyperlinks.
 */
public class BetaViewConnectionTab implements ConnectionTab {

    // ── UI components (same as BetaViewSwingFrame) ──────────────────────

    private final JPanel mainPanel;

    private final JTextField favoriteIdField = new JTextField();
    private final JTextField localeField = new JTextField();
    private final JTextField extensionField = new JTextField();
    private final JTextField formField = new JTextField();
    private final JTextField daysBackField = new JTextField();

    private final JButton loadButton = new JButton("Suche starten");
    private final JLabel statusLabel = new JLabel("Nicht verbunden");
    private final JProgressBar progressBar = new JProgressBar();

    private final JEditorPane htmlView = new JEditorPane();

    // ── State (same as BetaViewSwingFrame) ──────────────────────────────

    private URL baseUrl;
    private BetaViewClient client;
    private BetaViewSession session;
    private LoadResultsHtmlUseCase loadResultsHtmlUseCase;

    private BetaViewHtmlNavigator navigator;
    private HyperlinkListener currentHyperlinkListener;

    // ── MainframeMate-specific callbacks ────────────────────────────────

    private DocumentOpenCallback openCallback;
    private CredentialsProvider credentialsProvider;
    private String baseUrlText = "";

    public BetaViewConnectionTab() {
        this.mainPanel = buildUi();
        wireActions();
    }

    // ── External configuration (set by MenuCommand) ─────────────────────

    public interface DocumentOpenCallback {
        void openDocument(String displayName, String actionPath, String content);
    }

    public interface CredentialsProvider {
        String[] getCredentials(String host);
    }

    public void setOpenCallback(DocumentOpenCallback callback) {
        this.openCallback = callback;
    }

    public void setCredentialsProvider(CredentialsProvider provider) {
        this.credentialsProvider = provider;
    }

    public void setBaseUrl(String url) {
        this.baseUrlText = url != null ? url : "";
    }

    public void setFilterDefaults(String favoriteId, String locale, String extension, String form, int daysBack) {
        favoriteIdField.setText(favoriteId != null ? favoriteId : "");
        localeField.setText(locale != null ? locale : "de");
        extensionField.setText(extension != null ? extension : "*");
        formField.setText(form != null ? form : "APZF");
        daysBackField.setText(String.valueOf(daysBack > 0 ? daysBack : 60));
    }

    /**
     * Called by the MenuCommand after adding this tab.
     * Initiates the connect phase (login + CSRF) in the background.
     */
    public void connectInBackground() {
        if (baseUrlText.isEmpty()) {
            statusLabel.setText("Keine URL konfiguriert");
            return;
        }

        String user = null;
        String password = null;

        if (credentialsProvider != null) {
            try {
                String host = extractHost(baseUrlText);
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
            statusLabel.setText("Anmeldedaten fehlen");
            return;
        }

        final String fUser = user;
        final String fPassword = password;

        setBusy(true);
        statusLabel.setText("Verbinde...");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                // Exactly like BetaViewSwingFrame.connect()
                baseUrl = normalizeBaseUrl(baseUrlText);
                client = new BetaViewHttpClient(new BetaViewBaseUrl(baseUrl));
                loadResultsHtmlUseCase = new LoadResultsHtmlUseCase(client);
                session = client.login(new BetaViewCredentials(fUser, fPassword));
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // throws if doInBackground failed

                    installNavigator();

                    statusLabel.setText("Verbunden");
                    setFilterEnabled(true);
                    loadButton.setEnabled(true);
                } catch (Exception ex) {
                    statusLabel.setText("Verbindung fehlgeschlagen");
                    showError(ex);
                    setFilterEnabled(false);
                    loadButton.setEnabled(false);
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    // ── UI Construction ─────────────────────────────────────────────────

    private JPanel buildUi() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        topPanel.add(buildFilterPanel(), BorderLayout.CENTER);
        topPanel.add(buildStatusPanel(), BorderLayout.SOUTH);

        htmlView.setContentType("text/html");
        htmlView.setEditable(false);

        JScrollPane htmlScroll = new JScrollPane(htmlView);
        htmlScroll.setPreferredSize(new Dimension(900, 600));

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(htmlScroll, BorderLayout.CENTER);

        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        panel.add(progressBar, BorderLayout.SOUTH);

        // Start with filter disabled (like BetaViewSwingFrame)
        setFilterEnabled(false);
        loadButton.setEnabled(false);

        return panel;
    }

    private JPanel buildFilterPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Suchfilter"));
        GridBagConstraints c = baseConstraints();

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
        p.add(loadButton, c);

        return p;
    }

    private JPanel buildStatusPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        p.add(statusLabel, BorderLayout.WEST);
        return p;
    }

    // ── Actions (mirrors BetaViewSwingFrame exactly) ────────────────────

    private void wireActions() {
        loadButton.addActionListener(e -> loadResults());
    }

    private void loadResults() {
        if (client == null || session == null || loadResultsHtmlUseCase == null) {
            JOptionPane.showMessageDialog(mainPanel, "Nicht verbunden.", "Status", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final ResultFilter filter;
        try {
            filter = readFilterFromUi();
        } catch (Exception ex) {
            showError(ex);
            return;
        }

        setBusy(true);
        statusLabel.setText("Lade Ergebnisse...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                // Exactly like BetaViewSwingFrame.loadResults()
                return loadResultsHtmlUseCase.execute(session, filter);
            }

            @Override
            protected void done() {
                try {
                    String html = get();

                    if (navigator == null) {
                        installNavigator();
                    }
                    navigator.showInitialHtml(html);

                    statusLabel.setText("Ergebnisse geladen");
                } catch (Exception ex) {
                    statusLabel.setText("Laden fehlgeschlagen");
                    showError(ex);
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void installNavigator() {
        if (currentHyperlinkListener != null) {
            htmlView.removeHyperlinkListener(currentHyperlinkListener);
            currentHyperlinkListener = null;
        }

        navigator = new BetaViewHtmlNavigator(
                Objects.requireNonNull(client, "client must not be null"),
                Objects.requireNonNull(session, "session must not be null"),
                htmlView,
                Objects.requireNonNull(baseUrl, "baseUrl must not be null")
        );

        htmlView.addHyperlinkListener(navigator);
        currentHyperlinkListener = navigator;
    }

    private ResultFilter readFilterFromUi() {
        String favoriteId = favoriteIdField.getText().trim();
        String locale = localeField.getText().trim();
        String extension = extensionField.getText().trim();
        String form = formField.getText().trim();
        int daysBack = parseInt(daysBackField.getText().trim(), 60);
        return new ResultFilter(favoriteId, locale, extension, form, daysBack);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return fallback;
        }
    }

    private String extractHost(String url) {
        try {
            return new java.net.URL(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }

    private URL normalizeBaseUrl(String text) throws MalformedURLException {
        String s = text.trim();
        if (!s.endsWith("/")) {
            s = s + "/";
        }
        return new URL(s);
    }

    private void setBusy(boolean busy) {
        progressBar.setVisible(busy);
        loadButton.setEnabled(!busy && session != null);
    }

    private void setFilterEnabled(boolean enabled) {
        favoriteIdField.setEnabled(enabled);
        localeField.setEnabled(enabled);
        extensionField.setEnabled(enabled);
        formField.setEnabled(enabled);
        daysBackField.setEnabled(enabled);
        loadButton.setEnabled(enabled);
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

    private GridBagConstraints baseConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0.0;
        return c;
    }

    private void addRow(JPanel panel, GridBagConstraints c, int row, String label, Component field) {
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = 1; c.gridy = row; c.weightx = 1.0;
        panel.add(field, c);
    }

    // ── ConnectionTab contract ──────────────────────────────────────────

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
        return "betaview://" + baseUrlText;
    }

    @Override
    public Type getType() {
        return Type.CONNECTION;
    }
}

