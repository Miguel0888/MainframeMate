package de.bund.zrb.ui.settings;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.bund.zrb.model.AiProvider;
import de.bund.zrb.ui.components.HelpButton;
import de.bund.zrb.ui.help.HelpContentProvider;
import de.bund.zrb.util.ExecutableLauncher;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    private final JComboBox<String> ollamaModelCombo;
    private final JTextField ollamaKeepAliveField;

    // Cloud-Felder
    private final JComboBox<String> cloudVendorCombo;
    private final JTextField cloudApiKeyField;
    private final JTextField cloudApiUrlField;
    private final JComboBox<String> cloudModelCombo;
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

    // Custom-Felder (selbstgehostete Server)
    private final JTextField customUrlField;
    private final JComboBox<String> customModelCombo;
    private final JTextField customApiKeyField;
    private final JTextField customAuthHeaderField;
    private final JTextField customAuthPrefixField;
    private final JTextField customKeepAliveField;
    private final JTextField customTemperatureField;
    private final JSpinner customMaxTokensSpinner;
    private final JTextArea customSystemPromptField;
    private final JComboBox<String> customResponseFormatCombo;
    private final JTextField customHeadersField;
    private final JSpinner customConnectTimeoutSpinner;
    private final JSpinner customReadTimeoutSpinner;
    private final JSpinner customMaxRetriesSpinner;
    private final JCheckBox customSkipSslVerifyBox;

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
        providerCombo.addItem(AiProvider.CUSTOM);
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
        ollamaModelCombo = new JComboBox<>();
        ollamaModelCombo.setEditable(true);
        JPanel ollamaModelPanel = new JPanel(new BorderLayout(2, 0));
        ollamaModelPanel.add(ollamaModelCombo, BorderLayout.CENTER);
        JButton ollamaFetchBtn = new JButton("🔄");
        ollamaFetchBtn.setToolTipText("Verfügbare Modelle vom Server abrufen");
        ollamaFetchBtn.setMargin(new Insets(2, 4, 2, 4));
        ollamaFetchBtn.addActionListener(e -> fetchOllamaModels());
        ollamaModelPanel.add(ollamaFetchBtn, BorderLayout.EAST);
        ollamaPanel.add(ollamaModelPanel, gbc);

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
        cloudModelCombo = new JComboBox<>();
        cloudModelCombo.setEditable(true);
        JPanel cloudModelPanel = new JPanel(new BorderLayout(2, 0));
        cloudModelPanel.add(cloudModelCombo, BorderLayout.CENTER);
        JButton cloudFetchBtn = new JButton("🔄");
        cloudFetchBtn.setToolTipText("Verfügbare Modelle vom Anbieter abrufen");
        cloudFetchBtn.setMargin(new Insets(2, 4, 2, 4));
        cloudFetchBtn.addActionListener(e -> fetchCloudModels());
        cloudModelPanel.add(cloudFetchBtn, BorderLayout.EAST);
        cloudPanel.add(cloudModelPanel, gbc);

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

        // CUSTOM Panel (selbstgehostete Server)
        JPanel customPanel = new JPanel(new GridBagLayout());
        customPanel.setBorder(new TitledBorder("Custom Server Einstellungen"));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;

        // Basis-Einstellungen
        gbc.gridy = 0;
        customPanel.add(new JLabel("API-URL:"), gbc);
        gbc.gridy = 1;
        customUrlField = new JTextField(30);
        customPanel.add(customUrlField, gbc);

        gbc.gridy = 2;
        customPanel.add(new JLabel("Modell:"), gbc);
        gbc.gridy = 3;
        customModelCombo = new JComboBox<>();
        customModelCombo.setEditable(true);
        JPanel customModelPanel = new JPanel(new BorderLayout(2, 0));
        customModelPanel.add(customModelCombo, BorderLayout.CENTER);
        JButton customFetchBtn = new JButton("🔄");
        customFetchBtn.setToolTipText("Verfügbare Modelle vom Server abrufen");
        customFetchBtn.setMargin(new Insets(2, 4, 2, 4));
        customFetchBtn.addActionListener(e -> fetchCustomModels());
        customModelPanel.add(customFetchBtn, BorderLayout.EAST);
        customPanel.add(customModelPanel, gbc);

        gbc.gridy = 4;
        customPanel.add(new JLabel("Keep-Alive:"), gbc);
        gbc.gridy = 5;
        customKeepAliveField = new JTextField("10m", 10);
        customPanel.add(customKeepAliveField, gbc);

        // Authentifizierung
        gbc.gridy = 6;
        customPanel.add(new JLabel("── Authentifizierung ──"), gbc);

        gbc.gridy = 7;
        customPanel.add(new JLabel("API-Key:"), gbc);
        gbc.gridy = 8;
        customApiKeyField = new JTextField(30);
        customPanel.add(customApiKeyField, gbc);

        gbc.gridy = 9;
        customPanel.add(new JLabel("Auth-Header:"), gbc);
        gbc.gridy = 10;
        customAuthHeaderField = new JTextField("Authorization", 20);
        customPanel.add(customAuthHeaderField, gbc);

        gbc.gridy = 11;
        customPanel.add(new JLabel("Auth-Prefix:"), gbc);
        gbc.gridy = 12;
        customAuthPrefixField = new JTextField("Bearer", 15);
        customPanel.add(customAuthPrefixField, gbc);

        // Generierungsparameter
        gbc.gridy = 13;
        customPanel.add(new JLabel("── Generierung ──"), gbc);

        gbc.gridy = 14;
        customPanel.add(new JLabel("Temperatur:"), gbc);
        gbc.gridy = 15;
        customTemperatureField = new JTextField("0.7", 5);
        customPanel.add(customTemperatureField, gbc);

        gbc.gridy = 16;
        customPanel.add(new JLabel("Max Tokens (0 = unbegrenzt):"), gbc);
        gbc.gridy = 17;
        customMaxTokensSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100000, 100));
        customPanel.add(customMaxTokensSpinner, gbc);

        gbc.gridy = 18;
        customPanel.add(new JLabel("Response-Format:"), gbc);
        gbc.gridy = 19;
        customResponseFormatCombo = new JComboBox<>(new String[]{"ollama", "openai"});
        customPanel.add(customResponseFormatCombo, gbc);

        gbc.gridy = 20;
        customPanel.add(new JLabel("System-Prompt:"), gbc);
        gbc.gridy = 21;
        customSystemPromptField = new JTextArea(3, 30);
        customSystemPromptField.setLineWrap(true);
        customSystemPromptField.setWrapStyleWord(true);
        JScrollPane systemPromptScroll = new JScrollPane(customSystemPromptField);
        systemPromptScroll.setPreferredSize(new Dimension(300, 60));
        customPanel.add(systemPromptScroll, gbc);

        // Netzwerk-Einstellungen
        gbc.gridy = 22;
        customPanel.add(new JLabel("── Netzwerk ──"), gbc);

        gbc.gridy = 23;
        customPanel.add(new JLabel("Zusätzliche Header (Header1: Wert1; Header2: Wert2):"), gbc);
        gbc.gridy = 24;
        customHeadersField = new JTextField(30);
        customPanel.add(customHeadersField, gbc);

        gbc.gridy = 25;
        customPanel.add(new JLabel("Connect-Timeout (Sekunden):"), gbc);
        gbc.gridy = 26;
        customConnectTimeoutSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 300, 1));
        customPanel.add(customConnectTimeoutSpinner, gbc);

        gbc.gridy = 27;
        customPanel.add(new JLabel("Read-Timeout (ms, 0 = unbegrenzt):"), gbc);
        gbc.gridy = 28;
        customReadTimeoutSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 300000, 1000));
        customPanel.add(customReadTimeoutSpinner, gbc);

        gbc.gridy = 29;
        customPanel.add(new JLabel("Max Retries:"), gbc);
        gbc.gridy = 30;
        customMaxRetriesSpinner = new JSpinner(new SpinnerNumberModel(3, 0, 10, 1));
        customPanel.add(customMaxRetriesSpinner, gbc);

        gbc.gridy = 31;
        customSkipSslVerifyBox = new JCheckBox("SSL-Verifizierung überspringen (für selbstsignierte Zertifikate)");
        customPanel.add(customSkipSslVerifyBox, gbc);

        providerOptionsPanel.add(customPanel, AiProvider.CUSTOM.name());

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
            ollamaModelCombo.setSelectedItem("nomic-embed-text");
            ollamaKeepAliveField.setText("5m");
            cloudModelCombo.setSelectedItem("text-embedding-3-small");
            customUrlField.setText("http://localhost:11434/api/embeddings");
            customModelCombo.setSelectedItem("nomic-embed-text");
        } else {
            ollamaUrlField.setText("http://localhost:11434/api/chat");
            ollamaModelCombo.setSelectedItem("llama3.2");
            ollamaKeepAliveField.setText("10m");
            cloudModelCombo.setSelectedItem("gpt-4o-mini");
            customUrlField.setText("http://localhost:11434/api/chat");
            customModelCombo.setSelectedItem("llama3.2");
        }
        llamaStreamingBox.setSelected(true);
        llamaBinaryField.setText("C:/llamacpp/llama-server");
        llamaModelField.setText("models/mistral.gguf");

        // Custom Defaults
        customKeepAliveField.setText("10m");
        customAuthHeaderField.setText("Authorization");
        customAuthPrefixField.setText("Bearer");
        customTemperatureField.setText("0.7");
        customResponseFormatCombo.setSelectedItem("ollama");

        applyCloudVendorDefaults(false);
    }

    private void applyCloudVendorDefaults(boolean clearApiKey) {
        String vendor = Objects.toString(cloudVendorCombo.getSelectedItem(), "OPENAI");

        switch (vendor) {
            case "PERPLEXITY":
                cloudApiUrlField.setText("https://api.perplexity.ai/chat/completions");
                if (configType == ConfigType.CHAT) cloudModelCombo.setSelectedItem("sonar");
                else cloudModelCombo.setSelectedItem(""); // Perplexity hat keine Embeddings
                break;
            case "GROK":
                cloudApiUrlField.setText("https://api.x.ai/v1/chat/completions");
                if (configType == ConfigType.CHAT) cloudModelCombo.setSelectedItem("grok-2-latest");
                else cloudModelCombo.setSelectedItem("");
                break;
            case "GEMINI":
                cloudApiUrlField.setText("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions");
                if (configType == ConfigType.CHAT) cloudModelCombo.setSelectedItem("gemini-2.0-flash");
                else cloudModelCombo.setSelectedItem("text-embedding-004");
                break;
            case "CLAUDE":
                cloudApiUrlField.setText("https://api.anthropic.com/v1/messages");
                if (configType == ConfigType.CHAT) cloudModelCombo.setSelectedItem("claude-3-5-sonnet-latest");
                else cloudModelCombo.setSelectedItem(""); // Claude hat keine Embeddings
                cloudAuthHeaderField.setText("x-api-key");
                cloudAuthPrefixField.setText("");
                cloudApiVersionField.setText("2023-06-01");
                cloudOrgField.setEnabled(false);
                cloudProjectField.setEnabled(false);
                return;
            case "OPENAI":
            default:
                cloudApiUrlField.setText("https://api.openai.com/v1/chat/completions");
                if (configType == ConfigType.CHAT) cloudModelCombo.setSelectedItem("gpt-4o-mini");
                else cloudModelCombo.setSelectedItem("text-embedding-3-small");
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
            case CUSTOM:
                return testCustomConnection();
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

    private boolean testCustomConnection() {
        String url = customUrlField.getText().trim();
        if (url.isEmpty()) return false;

        // Falls URL einen API-Pfad enthält, versuche die Basis-URL zu ermitteln
        String testUrl = url;
        if (url.contains("/api/")) {
            testUrl = url.substring(0, url.indexOf("/api/")) + "/api/tags";
        }

        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(testUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            // Auth hinzufügen falls konfiguriert
            String apiKey = customApiKeyField.getText().trim();
            if (!apiKey.isEmpty()) {
                String authHeader = customAuthHeaderField.getText().trim();
                String authPrefix = customAuthPrefixField.getText().trim();
                String authValue = authPrefix.isEmpty() ? apiKey : authPrefix + " " + apiKey;
                conn.setRequestProperty(authHeader.isEmpty() ? "Authorization" : authHeader, authValue);
            }

            int code = conn.getResponseCode();
            // 401/403 ist OK wenn Auth fehlt, aber Server erreichbar
            return code < 500;
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
                configType == ConfigType.EMBEDDING ? "http://localhost:11434" : "http://localhost:11434/api/chat"));
        ollamaModelCombo.setSelectedItem(config.getOrDefault("ollama.model",
                configType == ConfigType.EMBEDDING ? "nomic-embed-text" : "llama3.2"));
        ollamaKeepAliveField.setText(config.getOrDefault("ollama.keepalive", "10m"));

        // Cloud
        String vendor = config.getOrDefault("cloud.vendor", "OPENAI");
        cloudVendorCombo.setSelectedItem(vendor);
        cloudApiKeyField.setText(config.getOrDefault("cloud.apikey", ""));
        cloudApiUrlField.setText(config.getOrDefault("cloud.url", ""));
        cloudModelCombo.setSelectedItem(config.getOrDefault("cloud.model", ""));
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

        // Custom
        customUrlField.setText(config.getOrDefault("custom.url",
                configType == ConfigType.EMBEDDING ? "http://localhost:11434/api/embeddings" : "http://localhost:11434/api/chat"));
        customModelCombo.setSelectedItem(config.getOrDefault("custom.model",
                configType == ConfigType.EMBEDDING ? "nomic-embed-text" : "llama3.2"));
        customApiKeyField.setText(config.getOrDefault("custom.apiKey", ""));
        customAuthHeaderField.setText(config.getOrDefault("custom.authHeader", "Authorization"));
        customAuthPrefixField.setText(config.getOrDefault("custom.authPrefix", "Bearer"));
        customKeepAliveField.setText(config.getOrDefault("custom.keepAlive", "10m"));
        customTemperatureField.setText(config.getOrDefault("custom.temperature", "0.7"));
        try {
            customMaxTokensSpinner.setValue(Integer.parseInt(config.getOrDefault("custom.maxTokens", "0")));
        } catch (NumberFormatException e) { /* ignore */ }
        customSystemPromptField.setText(config.getOrDefault("custom.systemPrompt", ""));
        customResponseFormatCombo.setSelectedItem(config.getOrDefault("custom.responseFormat", "ollama"));
        customHeadersField.setText(config.getOrDefault("custom.headers", ""));
        try {
            customConnectTimeoutSpinner.setValue(Integer.parseInt(config.getOrDefault("custom.connectTimeout", "10")));
        } catch (NumberFormatException e) { /* ignore */ }
        try {
            customReadTimeoutSpinner.setValue(Integer.parseInt(config.getOrDefault("custom.readTimeout", "0")));
        } catch (NumberFormatException e) { /* ignore */ }
        try {
            customMaxRetriesSpinner.setValue(Integer.parseInt(config.getOrDefault("custom.maxRetries", "3")));
        } catch (NumberFormatException e) { /* ignore */ }
        customSkipSslVerifyBox.setSelected(Boolean.parseBoolean(config.getOrDefault("custom.skipSslVerify", "false")));

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
        config.put("ollama.model", Objects.toString(ollamaModelCombo.getSelectedItem(), "").trim());
        config.put("ollama.keepalive", ollamaKeepAliveField.getText().trim());

        // Cloud
        config.put("cloud.vendor", Objects.toString(cloudVendorCombo.getSelectedItem(), "OPENAI"));
        config.put("cloud.apikey", cloudApiKeyField.getText().trim());
        config.put("cloud.url", cloudApiUrlField.getText().trim());
        config.put("cloud.model", Objects.toString(cloudModelCombo.getSelectedItem(), "").trim());
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

        // Custom
        config.put("custom.url", customUrlField.getText().trim());
        config.put("custom.model", Objects.toString(customModelCombo.getSelectedItem(), "").trim());
        config.put("custom.apiKey", customApiKeyField.getText().trim());
        config.put("custom.authHeader", customAuthHeaderField.getText().trim());
        config.put("custom.authPrefix", customAuthPrefixField.getText().trim());
        config.put("custom.keepAlive", customKeepAliveField.getText().trim());
        config.put("custom.temperature", customTemperatureField.getText().trim());
        config.put("custom.maxTokens", customMaxTokensSpinner.getValue().toString());
        config.put("custom.systemPrompt", customSystemPromptField.getText());
        config.put("custom.responseFormat", Objects.toString(customResponseFormatCombo.getSelectedItem(), "ollama"));
        config.put("custom.headers", customHeadersField.getText().trim());
        config.put("custom.connectTimeout", customConnectTimeoutSpinner.getValue().toString());
        config.put("custom.readTimeout", customReadTimeoutSpinner.getValue().toString());
        config.put("custom.maxRetries", customMaxRetriesSpinner.getValue().toString());
        config.put("custom.skipSslVerify", String.valueOf(customSkipSslVerifyBox.isSelected()));

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
            case OLLAMA: return Objects.toString(ollamaModelCombo.getSelectedItem(), "").trim();
            case CLOUD: return Objects.toString(cloudModelCombo.getSelectedItem(), "").trim();
            case LLAMA_CPP_SERVER: return llamaModelField.getText().trim();
            case CUSTOM: return Objects.toString(customModelCombo.getSelectedItem(), "").trim();
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
            case CUSTOM: return customUrlField.getText().trim();
            default: return "";
        }
    }

    /**
     * Gibt den API-Key zurück (für Cloud und Custom).
     */
    public String getApiKey() {
        AiProvider provider = getSelectedProvider();
        if (provider == AiProvider.CUSTOM) {
            return customApiKeyField.getText().trim();
        }
        return cloudApiKeyField.getText().trim();
    }

    // ==================== Modell-Abruf ====================

    /**
     * Ruft verfügbare Modelle von Ollama ab und befüllt das Dropdown.
     */
    private void fetchOllamaModels() {
        String url = ollamaUrlField.getText().trim();
        if (url.contains("/api/")) {
            url = url.substring(0, url.indexOf("/api/"));
        }
        fetchModelsAsync(ollamaModelCombo, url + "/api/tags", null, null);
    }

    /**
     * Ruft verfügbare Modelle vom konfigurierten Cloud-Anbieter ab.
     * Unterstützt OpenAI-kompatible Endpunkte (OPENAI, GROK) und Google Gemini.
     */
    private void fetchCloudModels() {
        String vendor = Objects.toString(cloudVendorCombo.getSelectedItem(), "OPENAI");
        String apiKey = cloudApiKeyField.getText().trim();
        String apiUrl = cloudApiUrlField.getText().trim();

        String modelsUrl;
        String authHeader = null;
        String authValue = null;

        switch (vendor) {
            case "OPENAI":
                modelsUrl = deriveModelsUrl(apiUrl, "/v1/models");
                authHeader = "Authorization";
                authValue = "Bearer " + apiKey;
                break;
            case "GROK":
                modelsUrl = "https://api.x.ai/v1/models";
                authHeader = "Authorization";
                authValue = "Bearer " + apiKey;
                break;
            case "GEMINI":
                modelsUrl = "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey;
                break;
            case "PERPLEXITY":
            case "CLAUDE":
                statusLabel.setText("ℹ️ Kein Modell-Endpunkt für " + vendor);
                statusLabel.setForeground(Color.DARK_GRAY);
                return;
            default:
                modelsUrl = deriveModelsUrl(apiUrl, "/v1/models");
                authHeader = "Authorization";
                authValue = "Bearer " + apiKey;
        }

        fetchModelsAsync(cloudModelCombo, modelsUrl, authHeader, authValue);
    }

    /**
     * Ruft verfügbare Modelle vom Custom-Server ab (Ollama-kompatibler /api/tags Endpunkt).
     */
    private void fetchCustomModels() {
        String url = customUrlField.getText().trim();
        if (url.contains("/api/")) {
            url = url.substring(0, url.indexOf("/api/"));
        }
        String apiKey = customApiKeyField.getText().trim();
        String authHeader = customAuthHeaderField.getText().trim();
        String authPrefix = customAuthPrefixField.getText().trim();
        String authValue = apiKey.isEmpty() ? null
                : (authPrefix.isEmpty() ? apiKey : authPrefix + " " + apiKey);

        fetchModelsAsync(customModelCombo, url + "/api/tags",
                apiKey.isEmpty() ? null : authHeader,
                authValue);
    }

    /**
     * Führt den HTTP-Abruf im Hintergrund aus und befüllt das Dropdown mit den gefundenen Modellen.
     *
     * @param combo      Ziel-Dropdown
     * @param tagsUrl    Vollständige URL des Modell-Endpunkts
     * @param authHeader HTTP-Header-Name für Authentifizierung (oder {@code null})
     * @param authValue  Wert des Auth-Headers (oder {@code null})
     */
    private void fetchModelsAsync(final JComboBox<String> combo,
                                  final String tagsUrl,
                                  final String authHeader,
                                  final String authValue) {
        statusLabel.setText("⏳ Lade Modelle...");
        statusLabel.setForeground(Color.BLACK);

        new Thread(() -> {
            java.net.HttpURLConnection conn = null;
            try {
                conn = (java.net.HttpURLConnection) new java.net.URL(tagsUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                if (authHeader != null && !authHeader.isEmpty()
                        && authValue != null && !authValue.isEmpty()) {
                    conn.setRequestProperty(authHeader, authValue);
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    final String body;
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        body = sb.toString();
                    }

                    final List<String> models = parseModelsResponse(body);
                    final String current = Objects.toString(combo.getSelectedItem(), "");

                    SwingUtilities.invokeLater(() -> {
                        combo.removeAllItems();
                        for (String m : models) {
                            combo.addItem(m);
                        }
                        combo.setSelectedItem(current);
                        statusLabel.setText("✅ " + models.size() + " Modelle geladen");
                        statusLabel.setForeground(new Color(0, 128, 0));
                    });
                } else {
                    final int finalCode = code;
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("❌ HTTP " + finalCode);
                        statusLabel.setForeground(Color.RED);
                    });
                }
            } catch (Exception ex) {
                final String msg = ex.getMessage();
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("❌ " + msg);
                    statusLabel.setForeground(Color.RED);
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    /**
     * Parst die JSON-Antwort eines Modell-Endpunkts.
     * Unterstützt das Ollama-Format ({@code models[].name}) und
     * das OpenAI-Format ({@code data[].id}).
     */
    private List<String> parseModelsResponse(String json) {
        List<String> names = new ArrayList<>();
        try {
            JsonObject obj = new Gson().fromJson(json, JsonObject.class);

            // Ollama-Format: { "models": [{ "name": "llama3.2:latest", ... }] }
            // Auch Gemini: { "models": [{ "name": "models/gemini-2.0-flash", ... }] }
            if (obj.has("models") && obj.get("models").isJsonArray()) {
                JsonArray models = obj.getAsJsonArray("models");
                for (JsonElement e : models) {
                    if (e.isJsonObject()) {
                        JsonObject model = e.getAsJsonObject();
                        if (model.has("name")) {
                            String n = model.get("name").getAsString();
                            // Gemini-Präfix "models/" entfernen
                            if (n.startsWith("models/")) {
                                n = n.substring("models/".length());
                            }
                            names.add(n);
                        }
                    }
                }
            }
            // OpenAI-Format: { "data": [{ "id": "gpt-4o", ... }] }
            else if (obj.has("data") && obj.get("data").isJsonArray()) {
                JsonArray data = obj.getAsJsonArray("data");
                for (JsonElement e : data) {
                    if (e.isJsonObject()) {
                        JsonObject model = e.getAsJsonObject();
                        if (model.has("id")) {
                            names.add(model.get("id").getAsString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Parse-Fehler: leere Liste zurückgeben
        }
        return names;
    }

    /**
     * Leitet die Modell-Auflistungs-URL von einer API-URL ab.
     * Beispiel: "https://api.openai.com/v1/chat/completions" → "https://api.openai.com/v1/models"
     */
    private String deriveModelsUrl(String apiUrl, String modelsPath) {
        if (apiUrl.contains("/v1/")) {
            return apiUrl.substring(0, apiUrl.indexOf("/v1/")) + modelsPath;
        }
        if (apiUrl.contains("/v2/")) {
            return apiUrl.substring(0, apiUrl.indexOf("/v2/")) + modelsPath;
        }
        int lastSlash = apiUrl.lastIndexOf('/');
        return lastSlash > 8 ? apiUrl.substring(0, lastSlash) + modelsPath : apiUrl + modelsPath;
    }
}

