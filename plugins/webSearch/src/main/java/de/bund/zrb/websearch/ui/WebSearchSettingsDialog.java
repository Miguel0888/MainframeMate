package de.bund.zrb.websearch.ui;

import de.zrb.bund.api.MainframeContext;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settings dialog for the WebSearch plugin.
 * Allows configuring browser type and headless mode.
 */
public class WebSearchSettingsDialog extends JDialog {

    private static final String PLUGIN_KEY = "webSearch";

    private final MainframeContext context;
    private final JComboBox<String> browserCombo;
    private final JCheckBox headlessCheckbox;
    private final JTextField browserPathField;

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
        browserPathField = new JTextField(settings.getOrDefault("browserPath", ""), 25);
        browserPathField.setToolTipText("Leer = automatische Erkennung");
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

        // ── Info-Label ──────────────────────────────────────────────
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        JLabel infoLabel = new JLabel(
                "<html><i>Die Browser-Tools (browser_navigate, browser_click_css, ...) werden "
                + "automatisch in der Tool-Registry registriert und stehen im Chat zur Verfügung.</i></html>");
        infoLabel.setForeground(Color.GRAY);
        form.add(infoLabel, gbc);

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
        setMinimumSize(new Dimension(450, 250));
        setLocationRelativeTo(context.getMainFrame());
    }

    private void saveSettings() {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("browser", (String) browserCombo.getSelectedItem());
        settings.put("headless", String.valueOf(headlessCheckbox.isSelected()));
        settings.put("browserPath", browserPathField.getText().trim());
        context.savePluginSettings(PLUGIN_KEY, settings);
    }
}

