package de.bund.zrb.websearch.ui;

import de.bund.zrb.websearch.tools.BrowserToolAdapter;
import de.zrb.bund.api.MainframeContext;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settings dialog for the WebSearch plugin.
 * Allows configuring browser type, headless mode, and URL boundaries.
 */
public class WebSearchSettingsDialog extends JDialog {

    private static final String PLUGIN_KEY = "webSearch";

    private final MainframeContext context;
    private final JComboBox<String> browserCombo;
    private final JCheckBox headlessCheckbox;
    private final JTextField browserPathField;
    private final JSpinner timeoutSpinner;
    private final JTextArea whitelistArea;
    private final JTextArea blacklistArea;

    public WebSearchSettingsDialog(MainframeContext context) {
        super(context.getMainFrame(), "Websearch-Einstellungen", true);
        this.context = context;

        Map<String, String> settings = context.loadPluginSettings(PLUGIN_KEY);

        setLayout(new BorderLayout(10, 10));
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // ── Browser ─────────────────────────────────────────────────
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        form.add(new JLabel("Browser:"), gbc);

        browserCombo = new JComboBox<>(new String[]{"Firefox", "Chrome", "Edge"});
        String savedBrowser = settings.getOrDefault("browser", "Firefox");
        browserCombo.setSelectedItem(savedBrowser);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(browserCombo, gbc);

        // ── Browser-Pfad ────────────────────────────────────────────
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        form.add(new JLabel("Browser-Pfad:"), gbc);

        JPanel pathPanel = new JPanel(new BorderLayout(5, 0));
        String defaultBrowserPath = "C:\\Program Files\\Mozilla Firefox\\firefox.exe";
        browserPathField = new JTextField(settings.getOrDefault("browserPath", defaultBrowserPath), 25);
        browserPathField.setToolTipText("Standard: " + defaultBrowserPath);
        pathPanel.add(browserPathField, BorderLayout.CENTER);

        JButton browseBtn = new JButton("...");
        browseBtn.setPreferredSize(new Dimension(30, 25));
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Browser-Executable auswählen");
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                browserPathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        pathPanel.add(browseBtn, BorderLayout.EAST);

        gbc.gridx = 1; gbc.weightx = 1;
        form.add(pathPanel, gbc);

        // ── Headless ────────────────────────────────────────────────
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        form.add(new JLabel("Modus:"), gbc);

        headlessCheckbox = new JCheckBox("Headless (ohne sichtbares Fenster)");
        boolean headless = !"false".equals(settings.getOrDefault("headless", "true"));
        headlessCheckbox.setSelected(headless);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(headlessCheckbox, gbc);

        // ── Navigate-Timeout ────────────────────────────────────────
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0; gbc.gridwidth = 1;
        form.add(new JLabel("Navigate-Timeout (s):"), gbc);

        int savedTimeout = 30;
        try {
            savedTimeout = Integer.parseInt(settings.getOrDefault("navigateTimeoutSeconds", "30"));
        } catch (NumberFormatException ignored) {}
        timeoutSpinner = new JSpinner(new SpinnerNumberModel(savedTimeout, 5, 300, 5));
        timeoutSpinner.setToolTipText("Maximale Wartezeit in Sekunden für eine Seitennavigation (Standard: 30)");
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(timeoutSpinner, gbc);

        // Apply timeout to system properties immediately
        System.setProperty("websearch.navigate.timeout.seconds", String.valueOf(savedTimeout));

        // ── Info-Label ──────────────────────────────────────────────
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        JLabel infoLabel = new JLabel(
                "<html><i>Die Browser-Tools (browser_navigate, browser_click_css, ...) werden "
                + "automatisch in der Tool-Registry registriert und stehen im Chat zur Verfügung.</i></html>");
        infoLabel.setForeground(Color.GRAY);
        form.add(infoLabel, gbc);

        // ── URL Whitelist ───────────────────────────────────────────
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 1; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        JLabel whitelistLabel = new JLabel("<html>URL-Whitelist<br><small>(Regex, pro Zeile)</small>:</html>");
        whitelistLabel.setToolTipText("Nur URLs, die einem Pattern matchen, werden erlaubt. Leer = alle erlaubt.");
        form.add(whitelistLabel, gbc);

        whitelistArea = new JTextArea(settings.getOrDefault("urlWhitelist", ""), 4, 30);
        whitelistArea.setToolTipText(
                "Regex-Patterns (ein Pattern pro Zeile). Beispiele:\n"
              + "  yahoo\\.com       → erlaubt alle Yahoo-URLs\n"
              + "  https://news\\.yahoo\\.com/.*  → nur Yahoo News\n"
              + "Zeilen mit # sind Kommentare. Leer = alle URLs erlaubt.");
        whitelistArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane whitelistScroll = new JScrollPane(whitelistArea);
        gbc.gridx = 1; gbc.weightx = 1; gbc.weighty = 0.3;
        gbc.fill = GridBagConstraints.BOTH;
        form.add(whitelistScroll, gbc);

        // ── URL Blacklist ───────────────────────────────────────────
        gbc.gridx = 0; gbc.gridy = 6; gbc.weightx = 0; gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        JLabel blacklistLabel = new JLabel("<html>URL-Blacklist<br><small>(Regex, pro Zeile)</small>:</html>");
        blacklistLabel.setToolTipText("URLs, die einem Blacklist-Pattern matchen, werden blockiert.");
        form.add(blacklistLabel, gbc);

        blacklistArea = new JTextArea(settings.getOrDefault("urlBlacklist", ""), 4, 30);
        blacklistArea.setToolTipText(
                "Regex-Patterns (ein Pattern pro Zeile). Beispiele:\n"
              + "  ads\\.example\\.com  → blockiert Werbe-Domain\n"
              + "  \\.(exe|zip|msi)$   → blockiert Downloads\n"
              + "Blacklist hat Vorrang vor Whitelist.");
        blacklistArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane blacklistScroll = new JScrollPane(blacklistArea);
        gbc.gridx = 1; gbc.weightx = 1; gbc.weighty = 0.3;
        gbc.fill = GridBagConstraints.BOTH;
        form.add(blacklistScroll, gbc);

        add(form, BorderLayout.CENTER);

        // ── Buttons ─────────────────────────────────────────────────
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Abbrechen");

        okButton.addActionListener(e -> {
            saveSettings();
            dispose();
        });
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(550, 550));
        setLocationRelativeTo(context.getMainFrame());
    }

    private void saveSettings() {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("browser", (String) browserCombo.getSelectedItem());
        settings.put("headless", String.valueOf(headlessCheckbox.isSelected()));
        settings.put("browserPath", browserPathField.getText().trim());
        String timeoutVal = String.valueOf(timeoutSpinner.getValue());
        settings.put("navigateTimeoutSeconds", timeoutVal);
        settings.put("urlWhitelist", whitelistArea.getText());
        settings.put("urlBlacklist", blacklistArea.getText());
        context.savePluginSettings(PLUGIN_KEY, settings);

        // Apply timeout to system properties so tools pick it up immediately
        System.setProperty("websearch.navigate.timeout.seconds", timeoutVal);

        // Reload URL boundary checker with new settings
        BrowserToolAdapter.reloadBoundaries(settings);
    }
}

