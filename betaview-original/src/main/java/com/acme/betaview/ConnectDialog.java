package com.acme.betaview;

import javax.swing.*;
import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Modal dialog for establishing a new BetaView server connection.
 * On successful connect, {@link #getResult()} returns the connection info.
 */
public final class ConnectDialog extends JDialog {

    private final JTextField urlField = new JTextField();
    private final JTextField userField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JCheckBox openLastTabsCheckBox = new JCheckBox("Letzte Tabs öffnen", true);
    private final JButton connectButton = new JButton("Connect");
    private final JButton cancelButton = new JButton("Abbrechen");
    private final JLabel statusLabel = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar();

    private ConnectResult result;

    public ConnectDialog(Frame owner, BetaViewAppProperties defaults) {
        super(owner, "Neue Verbindung", true);
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // ---- Form ----
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        form.add(new JLabel("Base URL:"), c);
        c.gridx = 1; c.weightx = 1.0;
        urlField.setText(defaults != null ? defaults.url() : "");
        form.add(urlField, c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        form.add(new JLabel("Benutzer:"), c);
        c.gridx = 1; c.weightx = 1.0;
        userField.setText(defaults != null ? defaults.user() : "");
        form.add(userField, c);

        c.gridx = 0; c.gridy = 2; c.weightx = 0;
        form.add(new JLabel("Passwort:"), c);
        c.gridx = 1; c.weightx = 1.0;
        passwordField.setText(defaults != null ? defaults.password() : "");
        form.add(passwordField, c);

        c.gridx = 1; c.gridy = 3; c.weightx = 1.0;
        form.add(openLastTabsCheckBox, c);

        add(form, BorderLayout.CENTER);

        // ---- Buttons ----
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        progress.setIndeterminate(true);
        progress.setVisible(false);
        progress.setPreferredSize(new Dimension(80, 20));
        buttons.add(progress);
        buttons.add(statusLabel);
        buttons.add(connectButton);
        buttons.add(cancelButton);
        add(buttons, BorderLayout.SOUTH);

        // ---- Wiring ----
        connectButton.addActionListener(e -> doConnect());
        cancelButton.addActionListener(e -> dispose());
        passwordField.addActionListener(e -> doConnect());

        setSize(450, 250);
        setLocationRelativeTo(owner);
    }

    /** Returns null if the user cancelled. */
    public ConnectResult getResult() {
        return result;
    }

    private void doConnect() {
        String baseUrlText = urlField.getText().trim();
        String user = userField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (baseUrlText.isEmpty() || user.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Alle Felder ausfüllen!");
            return;
        }

        connectButton.setEnabled(false);
        cancelButton.setEnabled(false);
        progress.setVisible(true);
        statusLabel.setText("Verbinde...");

        final boolean restoreTabs = openLastTabsCheckBox.isSelected();

        new SwingWorker<ConnectResult, Void>() {
            @Override
            protected ConnectResult doInBackground() throws Exception {
                URL baseUrl = normalizeBaseUrl(baseUrlText);
                BetaViewClient client = new BetaViewHttpClient(new BetaViewBaseUrl(baseUrl));
                LoadResultsHtmlUseCase useCase = new LoadResultsHtmlUseCase(client);
                BetaViewSession session = client.login(new BetaViewCredentials(user, password));

                String displayName = user + "@" + baseUrl.getHost();
                return new ConnectResult(baseUrl, client, session, useCase, displayName, restoreTabs);
            }

            @Override
            protected void done() {
                progress.setVisible(false);
                try {
                    result = get();
                    dispose();
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    statusLabel.setText("Fehler: " + msg);
                    connectButton.setEnabled(true);
                    cancelButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private URL normalizeBaseUrl(String text) throws MalformedURLException {
        String s = text.trim();
        if (!s.endsWith("/")) s += "/";
        return new URL(s);
    }

    /** Result of a successful connection. */
    public static final class ConnectResult {
        private final URL baseUrl;
        private final BetaViewClient client;
        private final BetaViewSession session;
        private final LoadResultsHtmlUseCase loadResultsUseCase;
        private final String displayName;
        private final boolean openLastTabs;

        ConnectResult(URL baseUrl, BetaViewClient client, BetaViewSession session,
                      LoadResultsHtmlUseCase loadResultsUseCase, String displayName,
                      boolean openLastTabs) {
            this.baseUrl = baseUrl;
            this.client = client;
            this.session = session;
            this.loadResultsUseCase = loadResultsUseCase;
            this.displayName = displayName;
            this.openLastTabs = openLastTabs;
        }

        public URL baseUrl()                          { return baseUrl; }
        public BetaViewClient client()                { return client; }
        public BetaViewSession session()              { return session; }
        public LoadResultsHtmlUseCase loadResultsUseCase() { return loadResultsUseCase; }
        public String displayName()                   { return displayName; }
        public boolean openLastTabs()                 { return openLastTabs; }
    }
}

