package de.bund.zrb.ui.settings;

import de.bund.zrb.model.AiProvider;
import de.bund.zrb.ui.components.HelpButton;
import de.bund.zrb.ui.help.HelpContentProvider;
import de.bund.zrb.util.ExecutableLauncher;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Wiederverwendbares Panel für AI-Provider-Einstellungen.
 * Kann sowohl für KI-Chat als auch für RAG/Embeddings verwendet werden.
 */
public class AiProviderSettingsPanel extends JPanel {

    // Provider-Auswahl
    private final JComboBox<AiProvider> providerCombo;
    private final JPanel providerOptionsPanel;

    // Ollama-Felder
    private final JTextField ollamaUrlField;
    private final JTextField ollamaModelField;
    private final JTextField ollamaKeepAliveField;

    // Cloud-Felder
    private final JComboBox<String> cloudVendorCombo;
    private final JTextField cloudApiKeyField;
    private final JTextField cloudApiUrlField;
    private final JTextField cloudModelField;
    private final JTextField cloudAuthHeaderField;
    private final JTextField cloudAuthPrefixField;
    private final JTextField cloudApiVersionField;
    private final JTextField cloudOrgField;
    private final JTextField cloudProjectField;

    // LlamaCpp-Felder
    private final JCheckBox llamaStreamingBox;
    private final JCheckBox llamaEnabledBox;
    private final JTextField llamaBinaryField;
    private final JTextField llamaModelField;
    private final JSpinner llamaPortSpinner;
    private final JSpinner llamaThreadsSpinner;
    private final JSpinner llamaContextSpinner;
    private final JTextField llamaTempField;

    // Test-Bereich
    private final JButton testButton;
    private final JLabel statusLabel;

    // Konfigurationstyp (für unterschiedliche Default-Werte)
    private final ConfigType configType;

    public enum ConfigType {
        CHAT,       // Für Chat/KI - verwendet Chat-Modelle
        EMBEDDING   // Für RAG/Embeddings - verwendet Embedding-Modelle
    }

    public AiProviderSettingsPanel(ConfigType configType) {
        this.configType = configType;
        setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // Provider-Auswahl
        JPanel providerSelectPanel = new JPanel(new GridBagLayout());
        providerSelectPanel.setBorder(new TitledBorder("Provider"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        providerSelectPanel.add(new JLabel("Provider:"), gbc);
        gbc.gridx = 1;
        providerCombo = new JComboBox<>();
        providerCombo.addItem(AiProvider.DISABLED);
        providerCombo.addItem(AiProvider.OLLAMA);
        providerCombo.addItem(AiProvider.CLOUD);
        providerCombo.addItem(AiProvider.LOCAL_AI);
        providerCombo.addItem(AiProvider.LLAMA_CPP_SERVER);
        providerSelectPanel.add(providerCombo, gbc);

        mainPanel.add(providerSelectPanel);

        // Provider-spezifische Optionen (CardLayout)
        providerOptionsPanel = new JPanel(new CardLayout());

        // Disabled Panel
        JPanel disabledPanel = new JPanel();
        disabledPanel.add(new JLabel("Provider ist deaktiviert."));
        providerOptionsPanel.add(disabledPanel, AiProvider.DISABLED.name());

        // Ollama Panel
        JPanel ollamaPanel = new JPanel(new GridBagLayout());
        ollamaPanel.setBorder(new TitledBorder("Ollama Einstellungen"));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        ollamaPanel.add(new JLabel("URL:"), gbc);
        gbc.gridx = 1;
        ollamaUrlField = new JTextField(30);
        ollamaPanel.add(ollamaUrlField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        ollamaPanel.add(new JLabel("Modell:"), gbc);
        gbc.gridx = 1;
        ollamaModelField = new JTextField(20);
        ollamaPanel.add(ollamaModelField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        ollamaPanel.add(new JLabel("Keep-Alive (z.B. 30m):"), gbc);
        gbc.gridx = 1;
        ollamaKeepAliveField = new JTextField(10);
        ollamaPanel.add(ollamaKeepAliveField, gbc);

        providerOptionsPanel.add(ollamaPanel, AiProvider.OLLAMA.name());

        // Cloud Panel
        JPanel cloudPanel = new JPanel(new GridBagLayout());
        cloudPanel.setBorder(new TitledBorder("Cloud Einstellungen"));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        cloudPanel.add(new JLabel("Cloud-Anbieter:"), gbc);
        gbc.gridx = 1;
        cloudVendorCombo = new JComboBox<>(new String[]{"OPENAI", "CLAUDE", "PERPLEXITY", "GROK", "GEMINI"});
        cloudPanel.add(cloudVendorCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        cloudPanel.add(new JLabel("API Key:"), gbc);
        gbc.gridx = 1;
        cloudApiKeyField = new JTextField(30);
        cloudPanel.add(cloudApiKeyField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        cloudPanel.add(new JLabel("API URL:"), gbc);
        gbc.gridx = 1;
        cloudApiUrlField = new JTextField(30);
        cloudPanel.add(cloudApiUrlField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        cloudPanel.add(new JLabel("Modell:"), gbc);
        gbc.gridx = 1;
        cloudModelField = new JTextField(30);
        cloudPanel.add(cloudModelField, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        cloudPanel.add(new JLabel("Auth Header:"), gbc);
        gbc.gridx = 1;
        cloudAuthHeaderField = new JTextField(30);
        cloudPanel.add(cloudAuthHeaderField, gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        cloudPanel.add(new JLabel("Auth Prefix:"), gbc);
        gbc.gridx = 1;
        cloudAuthPrefixField = new JTextField(20);
        cloudPanel.add(cloudAuthPrefixField, gbc);

        gbc.gridx = 0; gbc.gridy = 6;
        cloudPanel.add(new JLabel("API-Version (Claude):"), gbc);
        gbc.gridx = 1;
        cloudApiVersionField = new JTextField(20);
        cloudPanel.add(cloudApiVersionField, gbc);

        gbc.gridx = 0; gbc.gridy = 7;
        cloudPanel.add(new JLabel("Organisation (OpenAI):"), gbc);
        gbc.gridx = 1;
        cloudOrgField = new JTextField(30);
        cloudPanel.add(cloudOrgField, gbc);

        gbc.gridx = 0; gbc.gridy = 8;
        cloudPanel.add(new JLabel("Projekt (OpenAI):"), gbc);
        gbc.gridx = 1;
        cloudProjectField = new JTextField(30);
        cloudPanel.add(cloudProjectField, gbc);

        gbc.gridx = 1; gbc.gridy = 9;
        JButton cloudResetButton = new JButton("Defaults zurücksetzen");
        cloudResetButton.addActionListener(e -> applyCloudVendorDefaults(true));
        cloudPanel.add(cloudResetButton, gbc);

        cloudVendorCombo.addActionListener(e -> applyCloudVendorDefaults(false));

        providerOptionsPanel.add(cloudPanel, AiProvider.CLOUD.name());

        // LocalAI Panel
        JPanel localAiPanel = new JPanel(new GridBagLayout());
        localAiPanel.setBorder(new TitledBorder("LocalAI Einstellungen"));
        localAiPanel.add(new JLabel("Konfiguration für LocalAI (verwendet Ollama-kompatible API)."));
        providerOptionsPanel.add(localAiPanel, AiProvider.LOCAL_AI.name());

        // LlamaCpp Panel
        JPanel llamaPanel = new JPanel(new GridBagLayout());
        llamaPanel.setBorder(new TitledBorder("LlamaCpp Server Einstellungen"));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;

        gbc.gridy = 0;
        llamaStreamingBox = new JCheckBox("Streaming aktiviert");
        llamaPanel.add(llamaStreamingBox, gbc);

        gbc.gridy = 1;
        llamaEnabledBox = new JCheckBox("Server beim Start starten");
        llamaPanel.add(llamaEnabledBox, gbc);

        gbc.gridy = 2;
        llamaPanel.add(new JLabel("Pfad zur Binary:"), gbc);
        gbc.gridy = 3;
        llamaBinaryField = new JTextField(30);
        llamaPanel.add(llamaBinaryField, gbc);

        gbc.gridy = 4;
        JButton extractButton = new JButton("🔄 Entpacken, falls fehlt");
        extractButton.addActionListener(e -> extractLlamaBinary());
        llamaPanel.add(extractButton, gbc);

        gbc.gridy = 5;
        llamaPanel.add(new JLabel("Modellpfad (.gguf):"), gbc);
        gbc.gridy = 6;
        llamaModelField = new JTextField(30);
        llamaPanel.add(llamaModelField, gbc);

        gbc.gridy = 7;
        llamaPanel.add(new JLabel("Port:"), gbc);
        gbc.gridy = 8;
        llamaPortSpinner = new JSpinner(new SpinnerNumberModel(8080, 1024, 65535, 1));
        llamaPanel.add(llamaPortSpinner, gbc);

        gbc.gridy = 9;
        llamaPanel.add(new JLabel("Threads:"), gbc);
        gbc.gridy = 10;
        llamaThreadsSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 64, 1));
        llamaPanel.add(llamaThreadsSpinner, gbc);

        gbc.gridy = 11;
        llamaPanel.add(new JLabel("Kontextgröße:"), gbc);
        gbc.gridy = 12;
        llamaContextSpinner = new JSpinner(new SpinnerNumberModel(2048, 512, 8192, 64));
        llamaPanel.add(llamaContextSpinner, gbc);

        gbc.gridy = 13;
        llamaPanel.add(new JLabel("Temperatur:"), gbc);
        gbc.gridy = 14;
        llamaTempField = new JTextField("0.7", 5);
        llamaPanel.add(llamaTempField, gbc);

        // Felder aktivieren/deaktivieren
        List<Component> llamaConfigFields = Arrays.asList(
                llamaBinaryField, llamaModelField, llamaPortSpinner,
                llamaThreadsSpinner, llamaContextSpinner, llamaTempField
        );
        llamaEnabledBox.addActionListener(e -> {
            boolean enabled = llamaEnabledBox.isSelected();
            for (Component c : llamaConfigFields) c.setEnabled(enabled);
        });

        providerOptionsPanel.add(llamaPanel, AiProvider.LLAMA_CPP_SERVER.name());

        mainPanel.add(providerOptionsPanel);

        // Test-Bereich
        JPanel testPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        testPanel.setBorder(new TitledBorder("Verbindungstest"));
        testButton = new JButton("🧪 Verbindung testen");
        testButton.addActionListener(e -> testConnection());
        testPanel.add(testButton);
        statusLabel = new JLabel(" ");
        testPanel.add(statusLabel);
        mainPanel.add(testPanel);

        // Provider-Wechsel
        providerCombo.addActionListener(e -> {
            AiProvider selected = (AiProvider) providerCombo.getSelectedItem();
            CardLayout cl = (CardLayout) providerOptionsPanel.getLayout();
            if (selected != null) {
                cl.show(providerOptionsPanel, selected.name());
            }
        });

        add(new JScrollPane(mainPanel), BorderLayout.CENTER);

        // Defaults setzen
        applyDefaults();
    }

    private void applyDefaults() {
        if (configType == ConfigType.EMBEDDING) {
            ollamaUrlField.setText("http://localhost:11434");
            ollamaModelField.setText("nomic-embed-text");
            ollamaKeepAliveField.setText("5m");
            cloudModelField.setText("text-embedding-3-small");
        } else {
            ollamaUrlField.setText("http://localhost:11434/api/generate");
            ollamaModelField.setText("llama3.2");
            ollamaKeepAliveField.setText("10m");
            cloudModelField.setText("gpt-4o-mini");
        }
        llamaStreamingBox.setSelected(true);
        llamaBinaryField.setText("C:/llamacpp/llama-server");
        llamaModelField.setText("models/mistral.gguf");
        applyCloudVendorDefaults(false);
    }

    private void applyCloudVendorDefaults(boolean clearApiKey) {
        String vendor = Objects.toString(cloudVendorCombo.getSelectedItem(), "OPENAI");

        switch (vendor) {
            case "PERPLEXITY":
                cloudApiUrlField.setText("https://api.perplexity.ai/chat/completions");
                if (configType == ConfigType.CHAT) cloudModelField.setText("sonar");
                else cloudModelField.setText(""); // Perplexity hat keine Embeddings
                break;
            case "GROK":
                cloudApiUrlField.setText("https://api.x.ai/v1/chat/completions");
                if (configType == ConfigType.CHAT) cloudModelField.setText("grok-2-latest");
                else cloudModelField.setText("");
                break;
            case "GEMINI":
                cloudApiUrlField.setText("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions");
                if (configType == ConfigType.CHAT) cloudModelField.setText("gemini-2.0-flash");
                else cloudModelField.setText("text-embedding-004");
                break;
            case "CLAUDE":
                cloudApiUrlField.setText("https://api.anthropic.com/v1/messages");
                if (configType == ConfigType.CHAT) cloudModelField.setText("claude-3-5-sonnet-latest");
                else cloudModelField.setText(""); // Claude hat keine Embeddings
                cloudAuthHeaderField.setText("x-api-key");
                cloudAuthPrefixField.setText("");
                cloudApiVersionField.setText("2023-06-01");
                cloudOrgField.setEnabled(false);
                cloudProjectField.setEnabled(false);
                return;
            case "OPENAI":
            default:
                cloudApiUrlField.setText("https://api.openai.com/v1/chat/completions");
                if (configType == ConfigType.CHAT) cloudModelField.setText("gpt-4o-mini");
                else cloudModelField.setText("text-embedding-3-small");
                break;
        }

        cloudAuthHeaderField.setText("Authorization");
        cloudAuthPrefixField.setText("Bearer");
        cloudApiVersionField.setText("");
        cloudOrgField.setEnabled("OPENAI".equals(vendor));
        cloudProjectField.setEnabled("OPENAI".equals(vendor));

        if (clearApiKey) {
            cloudApiKeyField.setText("");
            if (!"OPENAI".equals(vendor)) {
                cloudOrgField.setText("");
                cloudProjectField.setText("");
            }
        }
    }

    private void extractLlamaBinary() {
        String path = llamaBinaryField.getText().trim();
        if (path.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bitte Zielpfad angeben.", "Pfad fehlt", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String defaultHash = ExecutableLauncher.getHash();
        String inputHash = (String) JOptionPane.showInputDialog(
                this,
                "Erwarteter SHA-256-Hash der Binary:",
                "Hash-Prüfung",
                JOptionPane.PLAIN_MESSAGE,
                null, null, defaultHash
        );

        if (inputHash == null || inputHash.trim().isEmpty()) {
            return;
        }

        try {
            new ExecutableLauncher().extractTo(new File(path), inputHash.trim());
            JOptionPane.showMessageDialog(this, "Binary erfolgreich extrahiert:\n" + path, "Erfolg", JOptionPane.INFORMATION_MESSAGE);
        } catch (SecurityException se) {
            JOptionPane.showMessageDialog(this, "Hash stimmt nicht:\n" + se.getMessage(), "Sicherheitswarnung", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Fehler:\n" + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void testConnection() {
        statusLabel.setText("⏳ Teste...");
        statusLabel.setForeground(Color.BLACK);
        testButton.setEnabled(false);

        new Thread(() -> {
            try {
                boolean success = performConnectionTest();
                SwingUtilities.invokeLater(() -> {
                    if (success) {
                        statusLabel.setText("✅ Verbindung erfolgreich");
                        statusLabel.setForeground(new Color(0, 128, 0));
                    } else {
                        statusLabel.setText("❌ Verbindung fehlgeschlagen");
                        statusLabel.setForeground(Color.RED);
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("❌ " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                });
            } finally {
                SwingUtilities.invokeLater(() -> testButton.setEnabled(true));
            }
        }).start();
    }

    private boolean performConnectionTest() {
        AiProvider provider = (AiProvider) providerCombo.getSelectedItem();
        if (provider == null || provider == AiProvider.DISABLED) {
            return false;
        }

        // Einfacher HTTP-Test je nach Provider
        String url;
        switch (provider) {
            case OLLAMA:
                url = ollamaUrlField.getText().trim();
                if (url.contains("/api/")) {
                    url = url.substring(0, url.indexOf("/api/"));
                }
                url += "/api/tags"; // Ollama tags endpoint
                break;
            case CLOUD:
                // Cloud-Test ist komplexer wegen Auth
                return testCloudConnection();
            case LLAMA_CPP_SERVER:
                url = "http://localhost:" + llamaPortSpinner.getValue() + "/health";
                break;
            default:
                return false;
        }

        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();
            return code >= 200 && code < 400;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean testCloudConnection() {
        // Vereinfachter Test - prüft nur ob URL erreichbar ist
        String url = cloudApiUrlField.getText().trim();
        if (url.isEmpty()) return false;

        try {
            java.net.URL u = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            // Bei Cloud erwarten wir 401/403 ohne Auth, das ist OK
            int code = conn.getResponseCode();
            return code < 500;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== Laden/Speichern ====================

    /**
     * Lädt Einstellungen aus einer Map.
     */
    public void loadFromConfig(Map<String, String> config) {
        if (config == null) config = new HashMap<>();

        try {
            String providerStr = config.getOrDefault("provider", "OLLAMA");
            providerCombo.setSelectedItem(AiProvider.valueOf(providerStr));
        } catch (Exception e) {
            providerCombo.setSelectedItem(AiProvider.OLLAMA);
        }

        // Ollama
        ollamaUrlField.setText(config.getOrDefault("ollama.url",
                configType == ConfigType.EMBEDDING ? "http://localhost:11434" : "http://localhost:11434/api/generate"));
        ollamaModelField.setText(config.getOrDefault("ollama.model",
                configType == ConfigType.EMBEDDING ? "nomic-embed-text" : "llama3.2"));
        ollamaKeepAliveField.setText(config.getOrDefault("ollama.keepalive", "10m"));

        // Cloud
        String vendor = config.getOrDefault("cloud.vendor", "OPENAI");
        cloudVendorCombo.setSelectedItem(vendor);
        cloudApiKeyField.setText(config.getOrDefault("cloud.apikey", ""));
        cloudApiUrlField.setText(config.getOrDefault("cloud.url", ""));
        cloudModelField.setText(config.getOrDefault("cloud.model", ""));
        cloudAuthHeaderField.setText(config.getOrDefault("cloud.authHeader", "Authorization"));
        cloudAuthPrefixField.setText(config.getOrDefault("cloud.authPrefix", "Bearer"));
        cloudApiVersionField.setText(config.getOrDefault("cloud.anthropicVersion", "2023-06-01"));
        cloudOrgField.setText(config.getOrDefault("cloud.organization", ""));
        cloudProjectField.setText(config.getOrDefault("cloud.project", ""));

        // LlamaCpp
        llamaStreamingBox.setSelected(Boolean.parseBoolean(config.getOrDefault("llama.streaming", "true")));
        llamaEnabledBox.setSelected(Boolean.parseBoolean(config.getOrDefault("llama.enabled", "false")));
        llamaBinaryField.setText(config.getOrDefault("llama.binary", "C:/llamacpp/llama-server"));
        llamaModelField.setText(config.getOrDefault("llama.model", "models/mistral.gguf"));
        try {
            llamaPortSpinner.setValue(Integer.parseInt(config.getOrDefault("llama.port", "8080")));
        } catch (NumberFormatException e) { /* ignore */ }
        try {
            llamaThreadsSpinner.setValue(Integer.parseInt(config.getOrDefault("llama.threads", "4")));
        } catch (NumberFormatException e) { /* ignore */ }
        try {
            llamaContextSpinner.setValue(Integer.parseInt(config.getOrDefault("llama.context", "2048")));
        } catch (NumberFormatException e) { /* ignore */ }
        llamaTempField.setText(config.getOrDefault("llama.temp", "0.7"));

        // Provider-Panel anzeigen
        AiProvider selected = (AiProvider) providerCombo.getSelectedItem();
        if (selected != null) {
            ((CardLayout) providerOptionsPanel.getLayout()).show(providerOptionsPanel, selected.name());
        }
    }

    /**
     * Speichert Einstellungen in eine Map.
     */
    public Map<String, String> saveToConfig() {
        Map<String, String> config = new HashMap<>();

        AiProvider provider = (AiProvider) providerCombo.getSelectedItem();
        config.put("provider", provider != null ? provider.name() : "OLLAMA");

        // Ollama
        config.put("ollama.url", ollamaUrlField.getText().trim());
        config.put("ollama.model", ollamaModelField.getText().trim());
        config.put("ollama.keepalive", ollamaKeepAliveField.getText().trim());

        // Cloud
        config.put("cloud.vendor", Objects.toString(cloudVendorCombo.getSelectedItem(), "OPENAI"));
        config.put("cloud.apikey", cloudApiKeyField.getText().trim());
        config.put("cloud.url", cloudApiUrlField.getText().trim());
        config.put("cloud.model", cloudModelField.getText().trim());
        config.put("cloud.authHeader", cloudAuthHeaderField.getText().trim());
        config.put("cloud.authPrefix", cloudAuthPrefixField.getText().trim());
        config.put("cloud.anthropicVersion", cloudApiVersionField.getText().trim());
        config.put("cloud.organization", cloudOrgField.getText().trim());
        config.put("cloud.project", cloudProjectField.getText().trim());

        // LlamaCpp
        config.put("llama.streaming", String.valueOf(llamaStreamingBox.isSelected()));
        config.put("llama.enabled", String.valueOf(llamaEnabledBox.isSelected()));
        config.put("llama.binary", llamaBinaryField.getText().trim());
        config.put("llama.model", llamaModelField.getText().trim());
        config.put("llama.port", llamaPortSpinner.getValue().toString());
        config.put("llama.threads", llamaThreadsSpinner.getValue().toString());
        config.put("llama.context", llamaContextSpinner.getValue().toString());
        config.put("llama.temp", llamaTempField.getText().trim());

        return config;
    }

    /**
     * Gibt den ausgewählten Provider zurück.
     */
    public AiProvider getSelectedProvider() {
        return (AiProvider) providerCombo.getSelectedItem();
    }

    /**
     * Gibt das ausgewählte Modell zurück (je nach Provider).
     */
    public String getSelectedModel() {
        AiProvider provider = getSelectedProvider();
        if (provider == null) return "";
        switch (provider) {
            case OLLAMA: return ollamaModelField.getText().trim();
            case CLOUD: return cloudModelField.getText().trim();
            case LLAMA_CPP_SERVER: return llamaModelField.getText().trim();
            default: return "";
        }
    }

    /**
     * Gibt die Basis-URL zurück (je nach Provider).
     */
    public String getBaseUrl() {
        AiProvider provider = getSelectedProvider();
        if (provider == null) return "";
        switch (provider) {
            case OLLAMA: return ollamaUrlField.getText().trim();
            case CLOUD: return cloudApiUrlField.getText().trim();
            case LLAMA_CPP_SERVER: return "http://localhost:" + llamaPortSpinner.getValue();
            default: return "";
        }
    }

    /**
     * Gibt den API-Key zurück (nur für Cloud).
     */
    public String getApiKey() {
        return cloudApiKeyField.getText().trim();
    }
}

