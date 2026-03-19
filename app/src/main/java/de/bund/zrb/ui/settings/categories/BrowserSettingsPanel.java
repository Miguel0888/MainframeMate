package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.settings.FormBuilder;
import de.bund.zrb.mcpserver.browser.BrowserLauncher;

import javax.swing.*;
import java.awt.*;

/**
 * Settings panel for the headless browser connection.
 * Allows configuring browser type (Firefox/Chrome/Edge), path, headless mode,
 * debug port, timeout, and home page.
 */
public class BrowserSettingsPanel extends AbstractSettingsPanel {

    private final JComboBox<String> browserTypeCombo;
    private final JTextField browserPathField;
    private final JCheckBox headlessCheckbox;
    private final JSpinner debugPortSpinner;
    private final JSpinner timeoutSpinner;
    private final JTextField homePageField;

    public BrowserSettingsPanel() {
        super("browser", "Browser");
        FormBuilder fb = new FormBuilder();

        // Browser type
        browserTypeCombo = new JComboBox<>(new String[]{"Firefox", "Chrome", "Edge"});
        String savedType = settings.browserType != null ? settings.browserType : "Firefox";
        browserTypeCombo.setSelectedItem(savedType);
        fb.addRow("Browser:", browserTypeCombo);

        // Browser path with browse button
        String savedPath = settings.browserPath != null ? settings.browserPath : "";
        String defaultPath = getDefaultPathForBrowser(savedType);
        String displayPath = !savedPath.trim().isEmpty() ? savedPath : defaultPath;
        browserPathField = new JTextField(displayPath, 25);
        browserPathField.setToolTipText("Standard: " + defaultPath);

        JButton browseBtn = new JButton("...");
        browseBtn.setPreferredSize(new Dimension(30, 25));
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                browserPathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        fb.addRowWithButton("Browser-Pfad:", browserPathField, browseBtn);

        // Update default path hint when browser type changes
        browserTypeCombo.addActionListener(e -> {
            String selected = (String) browserTypeCombo.getSelectedItem();
            String defPath = getDefaultPathForBrowser(selected);
            browserPathField.setToolTipText("Standard: " + defPath);
            // Only update text if still showing a default path
            String current = browserPathField.getText().trim();
            if (current.isEmpty()
                    || current.equals(getDefaultPathForBrowser("Firefox"))
                    || current.equals(getDefaultPathForBrowser("Chrome"))
                    || current.equals(getDefaultPathForBrowser("Edge"))) {
                browserPathField.setText(defPath);
            }
        });

        // Headless
        headlessCheckbox = new JCheckBox("Headless (ohne sichtbares Browser-Fenster)");
        headlessCheckbox.setSelected(settings.browserHeadless);
        fb.addWide(headlessCheckbox);

        // Debug port
        debugPortSpinner = new JSpinner(new SpinnerNumberModel(settings.browserDebugPort, 0, 65535, 1));
        debugPortSpinner.setToolTipText("0 = automatisch einen freien Port wählen");
        fb.addRow("Debug-Port:", debugPortSpinner);

        // Navigate timeout
        timeoutSpinner = new JSpinner(new SpinnerNumberModel(settings.browserNavigateTimeoutSeconds, 5, 300, 5));
        timeoutSpinner.setToolTipText("Maximale Wartezeit in Sekunden für eine Seitennavigation");
        fb.addRow("Navigations-Timeout (s):", timeoutSpinner);

        fb.addSeparator();

        // Home page
        homePageField = new JTextField(
                settings.browserHomePage != null ? settings.browserHomePage : "https://www.google.com", 30);
        homePageField.setToolTipText("Startseite beim Öffnen eines neuen Browser-Tabs");
        fb.addRow("Startseite:", homePageField);

        fb.addInfo("<html><i>Der Browser wird beim Öffnen eines Browser-Tabs gestartet und beim "
                + "Schließen des Tabs wieder beendet.<br>"
                + "Die Browser-Einstellungen werden auch für die WebSearch-Recherche verwendet, "
                + "falls dort kein eigener Browser konfiguriert ist.</i></html>");

        installPanel(fb);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.browserType = (String) browserTypeCombo.getSelectedItem();
        String path = browserPathField.getText().trim();
        // Only save non-default paths
        String defaultPath = getDefaultPathForBrowser(s.browserType);
        s.browserPath = path.equals(defaultPath) ? "" : path;
        s.browserHeadless = headlessCheckbox.isSelected();
        s.browserDebugPort = ((Number) debugPortSpinner.getValue()).intValue();
        s.browserNavigateTimeoutSeconds = ((Number) timeoutSpinner.getValue()).intValue();
        s.browserHomePage = homePageField.getText().trim();
    }

    /**
     * Returns the default executable path for the given browser name.
     */
    private static String getDefaultPathForBrowser(String browser) {
        if (browser == null) return BrowserLauncher.DEFAULT_FIREFOX_PATH;
        switch (browser.toLowerCase()) {
            case "chrome":
                return BrowserLauncher.DEFAULT_CHROME_PATH;
            case "edge":
                return BrowserLauncher.resolveEdgePath();
            default:
                return BrowserLauncher.DEFAULT_FIREFOX_PATH;
        }
    }
}

