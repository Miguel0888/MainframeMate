package de.bund.zrb.websearch.ui;

import de.bund.zrb.mcpserver.browser.BrowserLauncher;
import de.bund.zrb.websearch.tools.BrowserToolAdapter;
import de.zrb.bund.api.MainframeContext;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settings dialog for the WebSearch plugin.
 * Allows configuring browser type, headless mode, and URL boundaries.
 * Browser paths are stored per browser (browserPath.Firefox, browserPath.Chrome, browserPath.Edge).
 */
public class WebSearchSettingsDialog extends JDialog {

    private static final String PLUGIN_KEY = "webSearch";

    /** Default blacklist: block URLs whose host is a bare IP address (IPv4 or IPv6). */
    static final String DEFAULT_BLACKLIST =
            "# IPv4-Adressen blockieren (http(s)://123.45.67.89)\n"
          + "https?://\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}([:/]|$)\n"
          + "# IPv6-Adressen blockieren (http(s)://[::1])\n"
          + "https?://\\[[:0-9a-fA-F]+\\]";

    /** Default paths per browser. */
    private static final Map<String, String> DEFAULT_BROWSER_PATHS = new LinkedHashMap<>();
    static {
        DEFAULT_BROWSER_PATHS.put("Firefox", BrowserLauncher.DEFAULT_FIREFOX_PATH);
        DEFAULT_BROWSER_PATHS.put("Chrome",  BrowserLauncher.DEFAULT_CHROME_PATH);
        DEFAULT_BROWSER_PATHS.put("Edge",    BrowserLauncher.DEFAULT_EDGE_PATH);
    }

    private final MainframeContext context;
    private final JComboBox<String> browserCombo;
    private final JCheckBox headlessCheckbox;
    private final JTextField browserPathField;
    private final JSpinner timeoutSpinner;
    private final JTextArea whitelistArea;
    private final JTextArea blacklistArea;

    /** Cached paths per browser – loaded from settings, updated on browser switch. */
    private final Map<String, String> browserPaths = new LinkedHashMap<>();

    public WebSearchSettingsDialog(MainframeContext context) {
        super(context.getMainFrame(), "Websearch-Einstellungen", true);
        this.context = context;

        Map<String, String> settings = context.loadPluginSettings(PLUGIN_KEY);

        // Load per-browser paths from settings (with defaults)
        for (Map.Entry<String, String> entry : DEFAULT_BROWSER_PATHS.entrySet()) {
            String browser = entry.getKey();
            String defaultPath = entry.getValue();
            String saved = settings.getOrDefault("browserPath." + browser, "");
            browserPaths.put(browser, saved.isEmpty() ? defaultPath : saved);
        }
        // Migrate legacy single "browserPath" setting
        String legacyPath = settings.getOrDefault("browserPath", "");
        if (!legacyPath.isEmpty()) {
            String legacyBrowser = settings.getOrDefault("browser", "Firefox");
            // Only migrate if browser-specific key is still the default
            String current = browserPaths.get(legacyBrowser);
            String defaultForBrowser = DEFAULT_BROWSER_PATHS.getOrDefault(legacyBrowser, "");
            if (current.equals(defaultForBrowser)) {
                browserPaths.put(legacyBrowser, legacyPath);
            }
        }

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
        String initialPath = browserPaths.getOrDefault(savedBrowser,
                DEFAULT_BROWSER_PATHS.getOrDefault(savedBrowser, ""));
        browserPathField = new JTextField(initialPath, 25);
        updatePathTooltip(savedBrowser);
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

        JButton resetPathBtn = new JButton("↺");
        resetPathBtn.setToolTipText("Pfad auf Standard zurücksetzen");
        resetPathBtn.setPreferredSize(new Dimension(30, 25));
        resetPathBtn.setMargin(new Insets(0, 0, 0, 0));
        resetPathBtn.addActionListener(e -> {
            String browser = (String) browserCombo.getSelectedItem();
            String defaultPath = DEFAULT_BROWSER_PATHS.getOrDefault(browser, "");
            browserPathField.setText(defaultPath);
        });

        JPanel pathButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        pathButtons.add(browseBtn);
        pathButtons.add(resetPathBtn);
        pathPanel.add(pathButtons, BorderLayout.EAST);

        gbc.gridx = 1; gbc.weightx = 1;
        form.add(pathPanel, gbc);

        // ── Browser-ComboBox change listener: save current path, load new one ──
        browserCombo.addActionListener(e -> {
            // Save current browser path before switching
            String previousBrowser = findPreviousBrowser();
            if (previousBrowser != null) {
                browserPaths.put(previousBrowser, browserPathField.getText().trim());
            }
            // Load path for newly selected browser
            String newBrowser = (String) browserCombo.getSelectedItem();
            String newPath = browserPaths.getOrDefault(newBrowser,
                    DEFAULT_BROWSER_PATHS.getOrDefault(newBrowser, ""));
            browserPathField.setText(newPath);
            updatePathTooltip(newBrowser);
        });

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

        String savedBlacklist = settings.containsKey("urlBlacklist")
                ? settings.get("urlBlacklist")
                : DEFAULT_BLACKLIST;
        blacklistArea = new JTextArea(savedBlacklist, 4, 30);
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

    /** Track the previous browser selection to save its path before switching. */
    private String lastSelectedBrowser = null;

    private String findPreviousBrowser() {
        // On first call, we don't know the previous, return all browsers that are NOT current
        String current = (String) browserCombo.getSelectedItem();
        if (lastSelectedBrowser != null && !lastSelectedBrowser.equals(current)) {
            String prev = lastSelectedBrowser;
            lastSelectedBrowser = current;
            return prev;
        }
        lastSelectedBrowser = current;
        return null;
    }

    private void updatePathTooltip(String browser) {
        String defaultPath = DEFAULT_BROWSER_PATHS.getOrDefault(browser, "");
        browserPathField.setToolTipText("Standard für " + browser + ": " + defaultPath);
    }

    private void saveSettings() {
        String selectedBrowser = (String) browserCombo.getSelectedItem();

        // Save current browser path into the map
        browserPaths.put(selectedBrowser, browserPathField.getText().trim());

        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("browser", selectedBrowser);
        settings.put("headless", String.valueOf(headlessCheckbox.isSelected()));

        // Save per-browser paths
        for (Map.Entry<String, String> entry : browserPaths.entrySet()) {
            settings.put("browserPath." + entry.getKey(), entry.getValue());
        }
        // Also save as legacy "browserPath" for backward compatibility
        settings.put("browserPath", browserPaths.getOrDefault(selectedBrowser,
                DEFAULT_BROWSER_PATHS.getOrDefault(selectedBrowser, "")));

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

