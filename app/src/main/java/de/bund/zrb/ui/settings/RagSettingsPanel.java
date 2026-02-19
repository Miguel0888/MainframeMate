package de.bund.zrb.ui.settings;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.AiProvider;
import de.bund.zrb.model.Settings;
import de.bund.zrb.rag.config.EmbeddingSettings;
import de.bund.zrb.ui.components.HelpButton;
import de.bund.zrb.ui.help.HelpContentProvider;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Settings panel for RAG/Embedding configuration.
 * Uses the shared AiProviderSettingsPanel for provider selection.
 * Proxy settings are taken from the central Proxy tab.
 */
public class RagSettingsPanel extends JPanel {

    private final AiProviderSettingsPanel providerPanel;
    private final JCheckBox enabledCheckbox;
    private final JSpinner timeoutSpinner;
    private final JSpinner batchSizeSpinner;
    private final JLabel proxyInfoLabel;
    private final HelpButton helpButton;

    public RagSettingsPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // Header mit Hilfe-Button
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("RAG & Embedding-Konfiguration");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        helpButton = new HelpButton("Was ist RAG?",
                e -> HelpContentProvider.showHelpPopup(
                        (Component) e.getSource(),
                        HelpContentProvider.HelpTopic.SETTINGS_RAG));
        helpButton.setVisible(SettingsHelper.load().showHelpIcons);
        JPanel helpPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        helpPanel.add(helpButton);
        headerPanel.add(helpPanel, BorderLayout.EAST);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        mainPanel.add(headerPanel);

        // Aktivierung
        JPanel enablePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        enablePanel.setBorder(new TitledBorder("RAG-Status"));
        enabledCheckbox = new JCheckBox("RAG/Embeddings aktiviert");
        enabledCheckbox.setSelected(true);
        enablePanel.add(enabledCheckbox);
        mainPanel.add(enablePanel);

        // Provider-Panel (wiederverwendbar)
        providerPanel = new AiProviderSettingsPanel(AiProviderSettingsPanel.ConfigType.EMBEDDING);
        providerPanel.setBorder(new TitledBorder("Embedding-Provider"));
        mainPanel.add(providerPanel);

        // Performance
        JPanel perfPanel = new JPanel(new GridBagLayout());
        perfPanel.setBorder(new TitledBorder("Performance"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        perfPanel.add(new JLabel("Timeout (Sekunden):"), gbc);
        gbc.gridx = 1;
        timeoutSpinner = new JSpinner(new SpinnerNumberModel(30, 5, 300, 5));
        perfPanel.add(timeoutSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        perfPanel.add(new JLabel("Batch-Größe:"), gbc);
        gbc.gridx = 1;
        batchSizeSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 100, 1));
        perfPanel.add(batchSizeSpinner, gbc);

        mainPanel.add(perfPanel);

        // Proxy-Hinweis
        JPanel proxyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        proxyPanel.setBorder(new TitledBorder("Proxy"));
        proxyInfoLabel = new JLabel();
        updateProxyInfoLabel();
        proxyPanel.add(proxyInfoLabel);

        JButton refreshProxyButton = new JButton("🔄 Aktualisieren");
        refreshProxyButton.addActionListener(e -> updateProxyInfoLabel());
        proxyPanel.add(refreshProxyButton);

        mainPanel.add(proxyPanel);

        // Info-Panel
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(new TitledBorder("Hinweise"));
        JTextArea infoText = new JTextArea(
                "RAG (Retrieval-Augmented Generation) verbessert KI-Antworten\n" +
                "durch Zugriff auf Ihre Dokumente.\n\n" +
                "Embeddings wandeln Text in Vektoren um, die Bedeutung erfassen.\n" +
                "So findet das System auch konzeptuell verwandte Passagen.\n\n" +
                "Empfohlene Embedding-Modelle:\n" +
                "• Ollama: nomic-embed-text, all-minilm\n" +
                "• OpenAI: text-embedding-3-small\n\n" +
                "💡 Proxy-Einstellungen werden aus dem Proxy-Tab übernommen."
        );
        infoText.setEditable(false);
        infoText.setBackground(getBackground());
        infoText.setFont(infoText.getFont().deriveFont(Font.PLAIN, 11f));
        infoPanel.add(infoText, BorderLayout.CENTER);
        mainPanel.add(infoPanel);

        add(new JScrollPane(mainPanel), BorderLayout.CENTER);

        // Lade aktuelle Einstellungen
        loadSettings();
    }

    private void updateProxyInfoLabel() {
        Settings settings = SettingsHelper.load();
        if (settings.proxyEnabled) {
            String mode = settings.proxyMode;
            if ("MANUAL".equals(mode)) {
                proxyInfoLabel.setText("✅ Proxy aktiv: " + settings.proxyHost + ":" + settings.proxyPort);
            } else {
                proxyInfoLabel.setText("✅ Proxy aktiv (PAC/WPAD)");
            }
            proxyInfoLabel.setForeground(new Color(0, 128, 0));
        } else {
            proxyInfoLabel.setText("ℹ️ Kein Proxy konfiguriert (siehe Proxy-Tab)");
            proxyInfoLabel.setForeground(Color.GRAY);
        }
    }

    private void loadSettings() {
        Settings settings = SettingsHelper.load();
        Map<String, String> embConfig = settings.embeddingConfig;

        if (embConfig == null) {
            embConfig = new HashMap<>();
        }

        enabledCheckbox.setSelected(Boolean.parseBoolean(embConfig.getOrDefault("enabled", "true")));

        // Provider-Panel laden
        providerPanel.loadFromConfig(embConfig);

        // Performance
        try {
            timeoutSpinner.setValue(Integer.parseInt(embConfig.getOrDefault("timeout", "30")));
        } catch (NumberFormatException e) { /* ignore */ }
        try {
            batchSizeSpinner.setValue(Integer.parseInt(embConfig.getOrDefault("batchSize", "10")));
        } catch (NumberFormatException e) { /* ignore */ }
    }

    /**
     * Speichert die aktuellen Einstellungen in das Settings-Objekt.
     */
    public void saveToSettings(Settings settings) {
        if (settings.embeddingConfig == null) {
            settings.embeddingConfig = new HashMap<>();
        }

        // Provider-Einstellungen
        Map<String, String> providerConfig = providerPanel.saveToConfig();
        settings.embeddingConfig.putAll(providerConfig);

        // RAG-spezifische Einstellungen
        settings.embeddingConfig.put("enabled", String.valueOf(enabledCheckbox.isSelected()));
        settings.embeddingConfig.put("timeout", String.valueOf(timeoutSpinner.getValue()));
        settings.embeddingConfig.put("batchSize", String.valueOf(batchSizeSpinner.getValue()));
    }

    /**
     * Gibt die aktuellen Einstellungen als EmbeddingSettings-Objekt zurück.
     */
    public EmbeddingSettings getEmbeddingSettings() {
        AiProvider provider = providerPanel.getSelectedProvider();

        return new EmbeddingSettings()
                .setProvider(provider != null ? provider : AiProvider.OLLAMA)
                .setModel(providerPanel.getSelectedModel())
                .setApiKey(providerPanel.getApiKey())
                .setBaseUrl(providerPanel.getBaseUrl())
                .setTimeoutSeconds((Integer) timeoutSpinner.getValue())
                .setBatchSize((Integer) batchSizeSpinner.getValue())
                .setEnabled(enabledCheckbox.isSelected());
    }

    /**
     * Gibt zurück, ob RAG aktiviert ist.
     */
    public boolean isEnabled() {
        return enabledCheckbox.isSelected();
    }
}

