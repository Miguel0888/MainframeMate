package de.bund.zrb.ui.settings;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.AiProvider;
import de.bund.zrb.model.Settings;
import de.bund.zrb.rag.config.EmbeddingSettings;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Map;

/**
 * Settings panel for embedding configuration.
 * Separate from AI settings to allow different providers for embeddings.
 */
public class EmbeddingSettingsPanel extends JPanel {

    private final JComboBox<AiProvider> providerCombo;
    private final JTextField modelField;
    private final JTextField apiKeyField;
    private final JTextField baseUrlField;
    private final JCheckBox useProxyCheckbox;
    private final JTextField proxyHostField;
    private final JSpinner proxyPortSpinner;
    private final JSpinner timeoutSpinner;
    private final JSpinner batchSizeSpinner;
    private final JCheckBox enabledCheckbox;

    private final JButton testButton;
    private final JLabel statusLabel;

    public EmbeddingSettingsPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // Provider section
        JPanel providerPanel = new JPanel(new GridBagLayout());
        providerPanel.setBorder(new TitledBorder("Embedding Provider"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        providerPanel.add(new JLabel("Aktiviert:"), gbc);
        gbc.gridx = 1;
        enabledCheckbox = new JCheckBox();
        enabledCheckbox.setSelected(true);
        providerPanel.add(enabledCheckbox, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        providerPanel.add(new JLabel("Provider:"), gbc);
        gbc.gridx = 1;
        providerCombo = new JComboBox<>(AiProvider.values());
        providerCombo.addActionListener(e -> onProviderChanged());
        providerPanel.add(providerCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        providerPanel.add(new JLabel("Modell:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        modelField = new JTextField(20);
        providerPanel.add(modelField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE;
        providerPanel.add(new JLabel("API Key:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        apiKeyField = new JTextField(20);
        providerPanel.add(apiKeyField, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE;
        providerPanel.add(new JLabel("Base URL:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        baseUrlField = new JTextField(30);
        providerPanel.add(baseUrlField, gbc);

        mainPanel.add(providerPanel);

        // Proxy section
        JPanel proxyPanel = new JPanel(new GridBagLayout());
        proxyPanel.setBorder(new TitledBorder("Proxy"));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        proxyPanel.add(new JLabel("Proxy verwenden:"), gbc);
        gbc.gridx = 1;
        useProxyCheckbox = new JCheckBox();
        useProxyCheckbox.addActionListener(e -> updateProxyFields());
        proxyPanel.add(useProxyCheckbox, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        proxyPanel.add(new JLabel("Proxy Host:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        proxyHostField = new JTextField(20);
        proxyPanel.add(proxyHostField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
        proxyPanel.add(new JLabel("Proxy Port:"), gbc);
        gbc.gridx = 1;
        proxyPortSpinner = new JSpinner(new SpinnerNumberModel(8080, 1, 65535, 1));
        proxyPanel.add(proxyPortSpinner, gbc);

        mainPanel.add(proxyPanel);

        // Performance section
        JPanel perfPanel = new JPanel(new GridBagLayout());
        perfPanel.setBorder(new TitledBorder("Performance"));
        gbc = new GridBagConstraints();
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

        // Test section
        JPanel testPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        testPanel.setBorder(new TitledBorder("Verbindungstest"));
        testButton = new JButton("🧪 Verbindung testen");
        testButton.addActionListener(e -> testConnection());
        testPanel.add(testButton);
        statusLabel = new JLabel(" ");
        testPanel.add(statusLabel);
        mainPanel.add(testPanel);

        // Info panel
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(new TitledBorder("Hinweise"));
        JTextArea infoText = new JTextArea(
                "Embeddings werden für die semantische Suche (RAG) verwendet.\n\n" +
                "Empfohlene Modelle:\n" +
                "• Ollama: nomic-embed-text, all-minilm\n" +
                "• OpenAI: text-embedding-3-small, text-embedding-ada-002\n\n" +
                "Embeddings können auf einer CPU mit llamacpp/Ollama berechnet werden."
        );
        infoText.setEditable(false);
        infoText.setBackground(getBackground());
        infoText.setFont(infoText.getFont().deriveFont(Font.PLAIN, 11f));
        infoPanel.add(infoText, BorderLayout.CENTER);
        mainPanel.add(infoPanel);

        add(new JScrollPane(mainPanel), BorderLayout.CENTER);

        // Load current settings
        loadSettings();
    }

    private void onProviderChanged() {
        AiProvider provider = (AiProvider) providerCombo.getSelectedItem();
        if (provider != null) {
            modelField.setText(EmbeddingSettings.getDefaultModel(provider));
            baseUrlField.setText(EmbeddingSettings.getDefaultBaseUrl(provider));

            // API key required for cloud providers
            apiKeyField.setEnabled(provider == AiProvider.CLOUD);
        }
    }

    private void updateProxyFields() {
        boolean useProxy = useProxyCheckbox.isSelected();
        proxyHostField.setEnabled(useProxy);
        proxyPortSpinner.setEnabled(useProxy);
    }

    private void loadSettings() {
        Settings settings = SettingsHelper.load();
        Map<String, String> embConfig = settings.embeddingConfig;

        if (embConfig == null || embConfig.isEmpty()) {
            // Set defaults
            providerCombo.setSelectedItem(AiProvider.OLLAMA);
            onProviderChanged();
            return;
        }

        try {
            String providerStr = embConfig.getOrDefault("provider", "OLLAMA");
            providerCombo.setSelectedItem(AiProvider.valueOf(providerStr));
        } catch (Exception e) {
            providerCombo.setSelectedItem(AiProvider.OLLAMA);
        }

        modelField.setText(embConfig.getOrDefault("model", "nomic-embed-text"));
        apiKeyField.setText(embConfig.getOrDefault("apiKey", ""));
        baseUrlField.setText(embConfig.getOrDefault("baseUrl", "http://localhost:11434"));
        useProxyCheckbox.setSelected(Boolean.parseBoolean(embConfig.getOrDefault("useProxy", "false")));
        proxyHostField.setText(embConfig.getOrDefault("proxyHost", ""));
        proxyPortSpinner.setValue(Integer.parseInt(embConfig.getOrDefault("proxyPort", "8080")));
        timeoutSpinner.setValue(Integer.parseInt(embConfig.getOrDefault("timeout", "30")));
        batchSizeSpinner.setValue(Integer.parseInt(embConfig.getOrDefault("batchSize", "10")));
        enabledCheckbox.setSelected(Boolean.parseBoolean(embConfig.getOrDefault("enabled", "true")));

        updateProxyFields();
    }

    /**
     * Save current settings.
     */
    public void saveSettings() {
        Settings settings = SettingsHelper.load();

        if (settings.embeddingConfig == null) {
            settings.embeddingConfig = new java.util.HashMap<>();
        }

        AiProvider provider = (AiProvider) providerCombo.getSelectedItem();
        settings.embeddingConfig.put("provider", provider != null ? provider.name() : "OLLAMA");
        settings.embeddingConfig.put("model", modelField.getText());
        settings.embeddingConfig.put("apiKey", apiKeyField.getText());
        settings.embeddingConfig.put("baseUrl", baseUrlField.getText());
        settings.embeddingConfig.put("useProxy", String.valueOf(useProxyCheckbox.isSelected()));
        settings.embeddingConfig.put("proxyHost", proxyHostField.getText());
        settings.embeddingConfig.put("proxyPort", String.valueOf(proxyPortSpinner.getValue()));
        settings.embeddingConfig.put("timeout", String.valueOf(timeoutSpinner.getValue()));
        settings.embeddingConfig.put("batchSize", String.valueOf(batchSizeSpinner.getValue()));
        settings.embeddingConfig.put("enabled", String.valueOf(enabledCheckbox.isSelected()));

        SettingsHelper.save(settings);
    }

    /**
     * Get current settings as EmbeddingSettings object.
     */
    public EmbeddingSettings getEmbeddingSettings() {
        AiProvider provider = (AiProvider) providerCombo.getSelectedItem();
        return new EmbeddingSettings()
                .setProvider(provider != null ? provider : AiProvider.OLLAMA)
                .setModel(modelField.getText())
                .setApiKey(apiKeyField.getText())
                .setBaseUrl(baseUrlField.getText())
                .setUseProxy(useProxyCheckbox.isSelected())
                .setProxyHost(proxyHostField.getText())
                .setProxyPort((Integer) proxyPortSpinner.getValue())
                .setTimeoutSeconds((Integer) timeoutSpinner.getValue())
                .setBatchSize((Integer) batchSizeSpinner.getValue())
                .setEnabled(enabledCheckbox.isSelected());
    }

    private void testConnection() {
        statusLabel.setText("⏳ Teste...");
        testButton.setEnabled(false);

        new Thread(() -> {
            try {
                EmbeddingSettings settings = getEmbeddingSettings();
                de.bund.zrb.rag.infrastructure.MultiProviderEmbeddingClient client =
                        new de.bund.zrb.rag.infrastructure.MultiProviderEmbeddingClient(settings);

                if (client.isAvailable()) {
                    int dim = client.getDimension();
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("✅ Verbindung OK (Dimension: " + dim + ")");
                        statusLabel.setForeground(new Color(0, 128, 0));
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("❌ Verbindung fehlgeschlagen");
                        statusLabel.setForeground(Color.RED);
                    });
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("❌ Fehler: " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                });
            } finally {
                SwingUtilities.invokeLater(() -> testButton.setEnabled(true));
            }
        }).start();
    }
}

