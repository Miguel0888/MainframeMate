package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.AiProvider;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.components.ChatMode;
import de.bund.zrb.ui.settings.FormBuilder;
import de.bund.zrb.ui.settings.ModeToolsetDialog;
import de.bund.zrb.util.ExecutableLauncher;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AiSettingsPanel extends AbstractSettingsPanel {

    /**
     * In-memory buffer for aiConfig keys that are edited via sub-dialogs
     * (ModeToolsetDialog, mode prefix/postfix) and must survive the
     * {@code settings = SettingsHelper.load()} in {@code apply()}.
     */
    private final Map<String, String> pendingAiConfig = new LinkedHashMap<>();

    private final JComboBox<ChatMode> aiModeCombo;
    private final JTextArea aiToolPrefix, aiToolPostfix;
    private final JComboBox<String> aiLanguageCombo;
    private final JComboBox<String> aiEditorFontCombo, aiEditorFontSizeCombo;
    private final JSpinner aiEditorHeightSpinner;
    private final JCheckBox wrapJsonBox, prettyJsonBox;
    private final JComboBox<AiProvider> providerCombo;

    // Toolset per mode
    private final JCheckBox toolsetSwitchBox;
    private final JButton toolsetButton;

    // Ollama
    private final JTextField ollamaUrlField, ollamaModelField, ollamaKeepAliveField;
    // Ollama Proxy Auth & E2E (optional)
    private final JTextField ollamaProxyUsernameField;
    private final JPasswordField ollamaProxyPasswordField;
    private final JPasswordField ollamaE2ePasswordField;
    private final JCheckBox ollamaProxyAuthBox, ollamaE2eBox;
    // Cloud
    private final JComboBox<String> cloudProviderField;
    private final JTextField cloudApiKeyField, cloudApiUrlField, cloudModelField;
    private final JTextField cloudAuthHeaderField, cloudAuthPrefixField, cloudApiVersionField;
    private final JTextField cloudOrgField, cloudProjectField;
    // Llama
    private final JCheckBox llamaEnabledBox, llamaStreamingBox;
    private final JTextField llamaBinaryField, llamaModelField, llamaTempField;
    private final JSpinner llamaPortSpinner, llamaThreadsSpinner, llamaContextSpinner;

    public AiSettingsPanel() {
        super("ai", "KI");

        // Seed pendingAiConfig with all existing toolset/toolsetSwitch/prefix/postfix keys
        // so they survive the SettingsHelper.load() in apply()
        for (Map.Entry<String, String> entry : settings.aiConfig.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("toolset.") || key.startsWith("toolsetSwitch.")
                    || key.startsWith("toolPrefix.") || key.startsWith("toolPostfix.")) {
                pendingAiConfig.put(key, entry.getValue());
            }
        }

        FormBuilder fb = new FormBuilder();

        aiModeCombo = new JComboBox<>(ChatMode.values());
        aiModeCombo.setSelectedItem(ChatMode.AGENT);
        fb.addRow("Mode für Tool-Contract:", aiModeCombo);

        // ── Toolset switching per mode ──
        toolsetSwitchBox = new JCheckBox("Tools beim Mode-Wechsel aktualisieren");
        toolsetSwitchBox.setToolTipText("Wenn aktiviert, werden beim Umschalten in diesen Mode\n"
                + "nur die ausgewählten Tools dem Bot zur Verfügung gestellt.");
        toolsetButton = new JButton("🔧 Tools auswählen…");
        toolsetButton.setToolTipText("Verfügbare Tools für diesen Mode konfigurieren");
        toolsetButton.addActionListener(e -> {
            ChatMode mode = (ChatMode) aiModeCombo.getSelectedItem();
            if (mode != null) {
                ModeToolsetDialog.show(toolsetButton, mode, pendingAiConfig);
            }
        });
        // Enable/disable button based on checkbox
        toolsetSwitchBox.addActionListener(e -> {
            toolsetButton.setEnabled(toolsetSwitchBox.isSelected());
            ChatMode mode = (ChatMode) aiModeCombo.getSelectedItem();
            if (mode != null) {
                ModeToolsetDialog.setToolsetSwitchingEnabled(pendingAiConfig, mode, toolsetSwitchBox.isSelected());
            }
        });
        JPanel toolsetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        toolsetRow.add(toolsetSwitchBox);
        toolsetRow.add(toolsetButton);
        fb.addWide(toolsetRow);

        aiToolPrefix = new JTextArea(3, 30);
        aiToolPrefix.setLineWrap(true); aiToolPrefix.setWrapStyleWord(true);
        JButton prefixResetBtn = new JButton("↺");
        prefixResetBtn.setToolTipText("Prefix auf Default zurücksetzen");
        prefixResetBtn.setMargin(new Insets(2, 6, 2, 6));
        prefixResetBtn.addActionListener(e -> {
            ChatMode mode = (ChatMode) aiModeCombo.getSelectedItem();
            if (mode != null) aiToolPrefix.setText(mode.getDefaultToolPrefix());
        });
        fb.addRowWithButton("KI-Prefix:", new JScrollPane(aiToolPrefix), prefixResetBtn);

        aiToolPostfix = new JTextArea(2, 30);
        aiToolPostfix.setLineWrap(true); aiToolPostfix.setWrapStyleWord(true);
        JButton postfixResetBtn = new JButton("↺");
        postfixResetBtn.setToolTipText("Postfix auf Default zurücksetzen");
        postfixResetBtn.setMargin(new Insets(2, 6, 2, 6));
        postfixResetBtn.addActionListener(e -> {
            ChatMode mode = (ChatMode) aiModeCombo.getSelectedItem();
            if (mode != null) aiToolPostfix.setText(mode.getDefaultToolPostfix());
        });
        fb.addRowWithButton("KI-Postfix:", new JScrollPane(aiToolPostfix), postfixResetBtn);

        JButton aiResetButton = new JButton("Alles auf Default zurücksetzen");
        aiResetButton.addActionListener(e -> resetModeToolContract((ChatMode) aiModeCombo.getSelectedItem()));
        fb.addButtons(aiResetButton);

        aiLanguageCombo = new JComboBox<>(new String[]{"Deutsch (Standard)", "Keine Vorgabe", "Englisch"});
        String lang = settings.aiConfig.getOrDefault("assistant.language", "de").trim().toLowerCase();
        if ("".equals(lang) || "none".equals(lang)) aiLanguageCombo.setSelectedItem("Keine Vorgabe");
        else if ("en".equals(lang) || "english".equals(lang)) aiLanguageCombo.setSelectedItem("Englisch");
        else aiLanguageCombo.setSelectedItem("Deutsch (Standard)");
        fb.addRow("Antwortsprache:", aiLanguageCombo);

        final ChatMode[] previousMode = {(ChatMode) aiModeCombo.getSelectedItem()};
        loadModeToolContract(previousMode[0]);
        loadToolsetState(previousMode[0]);
        aiModeCombo.addActionListener(e -> {
            ChatMode newMode = (ChatMode) aiModeCombo.getSelectedItem();
            if (previousMode[0] != null) {
                pendingAiConfig.put("toolPrefix." + previousMode[0].name(), aiToolPrefix.getText().trim());
                pendingAiConfig.put("toolPostfix." + previousMode[0].name(), aiToolPostfix.getText().trim());
                ModeToolsetDialog.setToolsetSwitchingEnabled(pendingAiConfig, previousMode[0], toolsetSwitchBox.isSelected());
            }
            loadModeToolContract(newMode);
            loadToolsetState(newMode);
            previousMode[0] = newMode;
        });

        fb.addSection("KI-Editor");

        aiEditorFontCombo = new JComboBox<>(new String[]{"Monospaced", "Consolas", "Courier New", "Dialog", "Menlo"});
        aiEditorFontCombo.setSelectedItem(settings.aiConfig.getOrDefault("editor.font", "Monospaced"));
        fb.addRow("Schriftart:", aiEditorFontCombo);

        aiEditorFontSizeCombo = new JComboBox<>(new String[]{"10","11","12","13","14","16","18","20","24","28","32"});
        aiEditorFontSizeCombo.setEditable(true);
        aiEditorFontSizeCombo.setSelectedItem(settings.aiConfig.getOrDefault("editor.fontSize", "12"));
        fb.addRow("Schriftgröße:", aiEditorFontSizeCombo);

        aiEditorHeightSpinner = new JSpinner(new SpinnerNumberModel(
                Integer.parseInt(settings.aiConfig.getOrDefault("editor.lines", "3")), 1, 1000, 1));
        fb.addRow("Editor-Höhe (Zeilen):", aiEditorHeightSpinner);

        wrapJsonBox = new JCheckBox("JSON als Markdown-Codeblock einrahmen");
        wrapJsonBox.setSelected(Boolean.parseBoolean(settings.aiConfig.getOrDefault("wrapjson", "true")));
        fb.addWide(wrapJsonBox);

        prettyJsonBox = new JCheckBox("JSON schön formatieren (Pretty-Print)");
        prettyJsonBox.setSelected(Boolean.parseBoolean(settings.aiConfig.getOrDefault("prettyjson", "true")));
        fb.addWide(prettyJsonBox);

        fb.addSection("KI-Provider");

        providerCombo = new JComboBox<>();
        providerCombo.addItem(AiProvider.DISABLED); providerCombo.addItem(AiProvider.OLLAMA);
        providerCombo.addItem(AiProvider.CLOUD); providerCombo.addItem(AiProvider.LOCAL_AI);
        providerCombo.addItem(AiProvider.LLAMA_CPP_SERVER);
        fb.addRow("Provider:", providerCombo);

        JPanel providerOptionsPanel = new JPanel(new CardLayout());
        providerOptionsPanel.add(new JPanel(), AiProvider.DISABLED.name());

        // OLLAMA
        FormBuilder fbOllama = new FormBuilder();
        ollamaUrlField = new JTextField(30); fbOllama.addRow("URL:", ollamaUrlField);
        ollamaModelField = new JTextField(20); fbOllama.addRow("Modellname:", ollamaModelField);
        ollamaKeepAliveField = new JTextField(20); fbOllama.addRow("Beibehalten für:", ollamaKeepAliveField);

        // Proxy Auth (optional)
        fbOllama.addSection("Proxy-Authentifizierung (optional)");
        fbOllama.addInfo("Basic-Auth für den HTTPS-Proxy. Leer lassen, wenn kein Auth benötigt wird.");
        ollamaProxyAuthBox = new JCheckBox("Proxy-Authentifizierung aktivieren");
        ollamaProxyAuthBox.setSelected(!settings.aiConfig.getOrDefault("ollama.proxy.username", "").isEmpty());
        fbOllama.addWide(ollamaProxyAuthBox);
        ollamaProxyUsernameField = new JTextField(20);
        fbOllama.addRow("Benutzername:", ollamaProxyUsernameField);
        ollamaProxyPasswordField = new JPasswordField(20);
        JButton proxyPwToggle = createPasswordToggle(ollamaProxyPasswordField);
        fbOllama.addRowWithButton("Passwort:", ollamaProxyPasswordField, proxyPwToggle);

        // E2E Encryption (optional)
        fbOllama.addSection("Ende-zu-Ende-Verschlüsselung (optional)");
        fbOllama.addInfo("AES-256-GCM Verschlüsselung unabhängig von TLS. "
                + "Das Passwort muss auf beiden Seiten (Client &amp; Proxy) identisch sein "
                + "und wird nie über das Netzwerk übertragen.");
        ollamaE2eBox = new JCheckBox("E2E-Verschlüsselung aktivieren");
        ollamaE2eBox.setSelected(!settings.aiConfig.getOrDefault("ollama.e2e.password", "").isEmpty());
        fbOllama.addWide(ollamaE2eBox);
        ollamaE2ePasswordField = new JPasswordField(30);
        JButton e2ePwToggle = createPasswordToggle(ollamaE2ePasswordField);
        fbOllama.addRowWithButton("E2E-Passwort:", ollamaE2ePasswordField, e2ePwToggle);

        // Proxy scripts & documentation button
        fbOllama.addSeparator();
        JButton proxyDocsButton = new JButton("Proxy-Scripte & Dokumentation anzeigen…");
        proxyDocsButton.setToolTipText("Zeigt die README und alle Proxy-Scripte (JS) in einem Dialog an");
        proxyDocsButton.addActionListener(e -> {
            Window win = SwingUtilities.getWindowAncestor(this);
            new de.bund.zrb.ui.settings.ProxyScriptsDialog(win).setVisible(true);
        });
        fbOllama.addWide(proxyDocsButton);

        // Enable/disable auth fields based on checkbox
        ollamaProxyAuthBox.addActionListener(e -> {
            boolean en = ollamaProxyAuthBox.isSelected();
            ollamaProxyUsernameField.setEnabled(en);
            ollamaProxyPasswordField.setEnabled(en);
            proxyPwToggle.setEnabled(en);
        });
        ollamaProxyUsernameField.setEnabled(ollamaProxyAuthBox.isSelected());
        ollamaProxyPasswordField.setEnabled(ollamaProxyAuthBox.isSelected());
        proxyPwToggle.setEnabled(ollamaProxyAuthBox.isSelected());

        // Enable/disable E2E field based on checkbox
        ollamaE2eBox.addActionListener(e -> {
            boolean en = ollamaE2eBox.isSelected();
            ollamaE2ePasswordField.setEnabled(en);
            e2ePwToggle.setEnabled(en);
        });
        ollamaE2ePasswordField.setEnabled(ollamaE2eBox.isSelected());
        e2ePwToggle.setEnabled(ollamaE2eBox.isSelected());

        providerOptionsPanel.add(fbOllama.getPanel(), AiProvider.OLLAMA.name());

        // CLOUD
        FormBuilder fbCloud = new FormBuilder();
        cloudProviderField = new JComboBox<>(new String[]{"OPENAI","CLAUDE","PERPLEXITY","GROK","GEMINI"});
        fbCloud.addRow("Cloud-Anbieter:", cloudProviderField);
        cloudApiKeyField = new JTextField(30); fbCloud.addRow("API Key:", cloudApiKeyField);
        cloudApiUrlField = new JTextField(30); fbCloud.addRow("API URL:", cloudApiUrlField);
        cloudModelField = new JTextField(30); fbCloud.addRow("Modell:", cloudModelField);
        cloudAuthHeaderField = new JTextField(30); fbCloud.addRow("Auth Header:", cloudAuthHeaderField);
        cloudAuthPrefixField = new JTextField(30); fbCloud.addRow("Auth Prefix:", cloudAuthPrefixField);
        cloudApiVersionField = new JTextField(30); fbCloud.addRow("Anthropic-Version:", cloudApiVersionField);
        cloudOrgField = new JTextField(30); fbCloud.addRow("Organisation:", cloudOrgField);
        cloudProjectField = new JTextField(30); fbCloud.addRow("Projekt:", cloudProjectField);
        JButton cloudResetButton = new JButton("Defaults zurücksetzen");
        fbCloud.addButtons(cloudResetButton);
        providerOptionsPanel.add(fbCloud.getPanel(), AiProvider.CLOUD.name());

        // LOCAL_AI
        FormBuilder fbLocal = new FormBuilder();
        fbLocal.addInfo("Konfiguration für LocalAI folgt.");
        providerOptionsPanel.add(fbLocal.getPanel(), AiProvider.LOCAL_AI.name());

        // LLAMA
        FormBuilder fbLlama = new FormBuilder();
        llamaStreamingBox = new JCheckBox("Streaming aktiviert");
        llamaStreamingBox.setSelected(Boolean.parseBoolean(settings.aiConfig.getOrDefault("llama.streaming", "true")));
        fbLlama.addWide(llamaStreamingBox);
        llamaEnabledBox = new JCheckBox("llama.cpp Server beim Start starten");
        llamaEnabledBox.setSelected(Boolean.parseBoolean(settings.aiConfig.getOrDefault("llama.enabled", "false")));
        fbLlama.addWide(llamaEnabledBox);
        llamaBinaryField = new JTextField(settings.aiConfig.getOrDefault("llama.binary", "C:/llamacpp/llama-server"), 30);
        JButton extractBtn = new JButton("🔄 Entpacken");
        extractBtn.addActionListener(e -> {
            String path = llamaBinaryField.getText().trim();
            if (path.isEmpty()) { JOptionPane.showMessageDialog(null, "Bitte Zielpfad angeben.", "Pfad fehlt", JOptionPane.WARNING_MESSAGE); return; }
            String inputHash = (String) JOptionPane.showInputDialog(null, "SHA-256-Hash:", "Hashprüfung", JOptionPane.PLAIN_MESSAGE, null, null, ExecutableLauncher.getHash());
            if (inputHash == null || inputHash.trim().isEmpty()) return;
            try { new ExecutableLauncher().extractTo(new File(path), inputHash.trim());
                JOptionPane.showMessageDialog(null, "Binary extrahiert:\n" + path, "Erfolg", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) { JOptionPane.showMessageDialog(null, "Fehler:\n" + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE); }
        });
        fbLlama.addRowWithButton("Binary-Pfad:", llamaBinaryField, extractBtn);
        llamaModelField = new JTextField(settings.aiConfig.getOrDefault("llama.model", "models/mistral.gguf"), 30);
        fbLlama.addRow("Modellpfad (.gguf):", llamaModelField);
        llamaPortSpinner = new JSpinner(new SpinnerNumberModel(Integer.parseInt(settings.aiConfig.getOrDefault("llama.port", "8080")), 1024, 65535, 1));
        fbLlama.addRow("Port:", llamaPortSpinner);
        llamaThreadsSpinner = new JSpinner(new SpinnerNumberModel(Integer.parseInt(settings.aiConfig.getOrDefault("llama.threads", "4")), 1, 64, 1));
        fbLlama.addRow("Threads:", llamaThreadsSpinner);
        llamaContextSpinner = new JSpinner(new SpinnerNumberModel(Integer.parseInt(settings.aiConfig.getOrDefault("llama.context", "2048")), 512, 8192, 64));
        fbLlama.addRow("Kontextgröße:", llamaContextSpinner);
        llamaTempField = new JTextField(settings.aiConfig.getOrDefault("llama.temp", "0.7"), 5);
        fbLlama.addRow("Temperatur:", llamaTempField);
        providerOptionsPanel.add(fbLlama.getPanel(), AiProvider.LLAMA_CPP_SERVER.name());

        List<Component> llamaConfigFields = Arrays.asList(llamaBinaryField, llamaModelField, llamaPortSpinner, llamaThreadsSpinner, llamaContextSpinner, llamaTempField);
        llamaEnabledBox.addActionListener(e -> { boolean en = llamaEnabledBox.isSelected(); for (Component c : llamaConfigFields) c.setEnabled(en); });
        for (Component c : llamaConfigFields) c.setEnabled(llamaEnabledBox.isSelected());

        fb.addWide(providerOptionsPanel);

        // Set initial values
        String providerName = settings.aiConfig.getOrDefault("provider", "DISABLED");
        AiProvider selectedProvider;
        try { selectedProvider = AiProvider.valueOf(providerName); } catch (IllegalArgumentException ex) { selectedProvider = AiProvider.DISABLED; }
        providerCombo.setSelectedItem(selectedProvider);

        ollamaUrlField.setText(settings.aiConfig.getOrDefault("ollama.url", "http://localhost:11434/api/chat"));
        ollamaModelField.setText(settings.aiConfig.getOrDefault("ollama.model", "custom-modell"));
        ollamaKeepAliveField.setText(settings.aiConfig.getOrDefault("ollama.keepalive", "10m"));
        ollamaProxyUsernameField.setText(settings.aiConfig.getOrDefault("ollama.proxy.username", ""));
        ollamaProxyPasswordField.setText(settings.aiConfig.getOrDefault("ollama.proxy.password", ""));
        ollamaE2ePasswordField.setText(settings.aiConfig.getOrDefault("ollama.e2e.password", ""));

        String initialCloudVendor = settings.aiConfig.getOrDefault("cloud.vendor", "OPENAI");
        if ("CLOUD".equalsIgnoreCase(initialCloudVendor)) initialCloudVendor = "CLAUDE";
        cloudProviderField.setSelectedItem(initialCloudVendor);
        applyCloudVendorDefaults(false);
        cloudApiKeyField.setText(settings.aiConfig.getOrDefault("cloud.apikey", ""));
        cloudApiUrlField.setText(settings.aiConfig.getOrDefault("cloud.url", cloudDefaultForVendor(initialCloudVendor, "url")));
        cloudModelField.setText(settings.aiConfig.getOrDefault("cloud.model", cloudDefaultForVendor(initialCloudVendor, "model")));
        cloudAuthHeaderField.setText(settings.aiConfig.getOrDefault("cloud.authHeader", cloudDefaultForVendor(initialCloudVendor, "authHeader")));
        cloudAuthPrefixField.setText(settings.aiConfig.getOrDefault("cloud.authPrefix", cloudDefaultForVendor(initialCloudVendor, "authPrefix")));
        cloudOrgField.setText(settings.aiConfig.getOrDefault("cloud.organization", ""));
        cloudProjectField.setText(settings.aiConfig.getOrDefault("cloud.project", ""));
        cloudApiVersionField.setText(settings.aiConfig.getOrDefault("cloud.anthropicVersion", "2023-06-01"));

        cloudProviderField.addActionListener(e -> applyCloudVendorDefaults(true));
        cloudResetButton.addActionListener(e -> {
            applyCloudVendorDefaults(true); cloudApiKeyField.setText("");
            if (!"OPENAI".equals(cloudProviderField.getSelectedItem())) { cloudOrgField.setText(""); cloudProjectField.setText(""); }
        });

        providerCombo.addActionListener(e -> ((CardLayout) providerOptionsPanel.getLayout()).show(providerOptionsPanel, ((AiProvider) providerCombo.getSelectedItem()).name()));
        ((CardLayout) providerOptionsPanel.getLayout()).show(providerOptionsPanel, selectedProvider.name());

        installPanel(fb);
    }

    @Override
    protected void applyToSettings(Settings s) {
        ChatMode selectedMode = aiModeCombo != null && aiModeCombo.getSelectedItem() != null
                ? (ChatMode) aiModeCombo.getSelectedItem() : ChatMode.AGENT;
        s.aiConfig.put("editor.font", aiEditorFontCombo.getSelectedItem().toString());
        s.aiConfig.put("editor.fontSize", aiEditorFontSizeCombo.getSelectedItem().toString());
        s.aiConfig.put("editor.lines", aiEditorHeightSpinner.getValue().toString());
        s.aiConfig.put("toolPrefix." + selectedMode.name(), aiToolPrefix.getText().trim());
        s.aiConfig.put("toolPostfix." + selectedMode.name(), aiToolPostfix.getText().trim());
        ModeToolsetDialog.setToolsetSwitchingEnabled(s.aiConfig, selectedMode, toolsetSwitchBox.isSelected());
        s.aiConfig.remove("toolPrefix"); s.aiConfig.remove("toolPostfix");

        // Persist all toolset/toolsetSwitch/toolPrefix/toolPostfix keys
        // from the pendingAiConfig buffer (survives SettingsHelper.load() in apply())
        for (Map.Entry<String, String> entry : pendingAiConfig.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("toolset.") || key.startsWith("toolsetSwitch.")
                    || key.startsWith("toolPrefix.") || key.startsWith("toolPostfix.")) {
                // Don't overwrite the currently selected mode's prefix/postfix
                // (those are taken from the text fields above)
                if ((key.startsWith("toolPrefix.") || key.startsWith("toolPostfix."))
                        && key.endsWith("." + selectedMode.name())) {
                    continue;
                }
                s.aiConfig.put(key, entry.getValue());
            }
        }
        String selectedLanguage = Objects.toString(aiLanguageCombo.getSelectedItem(), "Deutsch (Standard)");
        if ("Keine Vorgabe".equals(selectedLanguage)) s.aiConfig.put("assistant.language", "none");
        else if ("Englisch".equals(selectedLanguage)) s.aiConfig.put("assistant.language", "en");
        else s.aiConfig.put("assistant.language", "de");
        s.aiConfig.put("provider", providerCombo.getSelectedItem().toString());
        s.aiConfig.put("ollama.url", ollamaUrlField.getText().trim());
        s.aiConfig.put("ollama.model", ollamaModelField.getText().trim());
        s.aiConfig.put("ollama.keepalive", ollamaKeepAliveField.getText().trim());
        // Proxy Auth (optional — only save when enabled)
        if (ollamaProxyAuthBox.isSelected()) {
            s.aiConfig.put("ollama.proxy.username", ollamaProxyUsernameField.getText().trim());
            s.aiConfig.put("ollama.proxy.password", new String(ollamaProxyPasswordField.getPassword()).trim());
        } else {
            s.aiConfig.put("ollama.proxy.username", "");
            s.aiConfig.put("ollama.proxy.password", "");
        }
        // E2E Encryption (optional — only save when enabled)
        if (ollamaE2eBox.isSelected()) {
            s.aiConfig.put("ollama.e2e.password", new String(ollamaE2ePasswordField.getPassword()).trim());
        } else {
            s.aiConfig.put("ollama.e2e.password", "");
        }
        s.aiConfig.put("cloud.vendor", Objects.toString(cloudProviderField.getSelectedItem(), "OPENAI"));
        s.aiConfig.put("cloud.apikey", cloudApiKeyField.getText().trim());
        s.aiConfig.put("cloud.url", cloudApiUrlField.getText().trim());
        s.aiConfig.put("cloud.model", cloudModelField.getText().trim());
        s.aiConfig.put("cloud.authHeader", cloudAuthHeaderField.getText().trim());
        s.aiConfig.put("cloud.authPrefix", cloudAuthPrefixField.getText().trim());
        s.aiConfig.put("cloud.anthropicVersion", cloudApiVersionField.getText().trim());
        s.aiConfig.put("cloud.organization", cloudOrgField.getText().trim());
        s.aiConfig.put("cloud.project", cloudProjectField.getText().trim());
        s.aiConfig.put("llama.enabled", String.valueOf(llamaEnabledBox.isSelected()));
        s.aiConfig.put("llama.binary", llamaBinaryField.getText().trim());
        s.aiConfig.put("llama.model", llamaModelField.getText().trim());
        s.aiConfig.put("llama.port", llamaPortSpinner.getValue().toString());
        s.aiConfig.put("llama.threads", llamaThreadsSpinner.getValue().toString());
        s.aiConfig.put("llama.context", llamaContextSpinner.getValue().toString());
        s.aiConfig.put("llama.temp", llamaTempField.getText().trim());
        s.aiConfig.put("llama.streaming", String.valueOf(llamaStreamingBox.isSelected()));
        s.aiConfig.put("wrapjson", String.valueOf(wrapJsonBox.isSelected()));
        s.aiConfig.put("prettyjson", String.valueOf(prettyJsonBox.isSelected()));
    }

    // ──── private helpers ────

    private void resetModeToolContract(ChatMode mode) {
        ChatMode resolved = mode != null ? mode : ChatMode.AGENT;
        aiToolPrefix.setText(resolved.getDefaultToolPrefix());
        aiToolPostfix.setText(resolved.getDefaultToolPostfix());
    }

    private void loadModeToolContract(ChatMode mode) {
        ChatMode resolved = mode != null ? mode : ChatMode.AGENT;
        String prefixKey = "toolPrefix." + resolved.name();
        String postfixKey = "toolPostfix." + resolved.name();
        aiToolPrefix.setText(pendingAiConfig.containsKey(prefixKey)
                ? pendingAiConfig.get(prefixKey)
                : settings.aiConfig.getOrDefault(prefixKey, resolved.getDefaultToolPrefix()));
        aiToolPostfix.setText(pendingAiConfig.containsKey(postfixKey)
                ? pendingAiConfig.get(postfixKey)
                : settings.aiConfig.getOrDefault(postfixKey, resolved.getDefaultToolPostfix()));
    }

    private void loadToolsetState(ChatMode mode) {
        boolean enabled = ModeToolsetDialog.isToolsetSwitchingEnabled(pendingAiConfig, mode)
                || ModeToolsetDialog.isToolsetSwitchingEnabled(settings.aiConfig, mode);
        toolsetSwitchBox.setSelected(enabled);
        toolsetButton.setEnabled(enabled);
    }

    private void applyCloudVendorDefaults(boolean clearOptionalFields) {
        String vendor = Objects.toString(cloudProviderField.getSelectedItem(), "OPENAI");
        cloudApiUrlField.setText(cloudDefaultForVendor(vendor, "url"));
        cloudModelField.setText(cloudDefaultForVendor(vendor, "model"));
        cloudAuthHeaderField.setText(cloudDefaultForVendor(vendor, "authHeader"));
        cloudAuthPrefixField.setText(cloudDefaultForVendor(vendor, "authPrefix"));
        cloudApiVersionField.setText(cloudDefaultForVendor(vendor, "anthropicVersion"));
        boolean isOpenAi = "OPENAI".equals(vendor);
        boolean isClaude = "CLAUDE".equals(vendor);
        cloudOrgField.setEnabled(isOpenAi);
        cloudProjectField.setEnabled(isOpenAi);
        cloudApiVersionField.setEnabled(isClaude);
        if (clearOptionalFields && !isOpenAi) { cloudOrgField.setText(""); cloudProjectField.setText(""); }
    }

    private static String cloudDefaultForVendor(String vendor, String key) {
        switch (vendor) {
            case "PERPLEXITY": if ("url".equals(key)) return "https://api.perplexity.ai/chat/completions"; if ("model".equals(key)) return "sonar"; break;
            case "GROK": if ("url".equals(key)) return "https://api.x.ai/v1/chat/completions"; if ("model".equals(key)) return "grok-2-latest"; break;
            case "GEMINI": if ("url".equals(key)) return "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"; if ("model".equals(key)) return "gemini-2.0-flash"; break;
            case "CLAUDE":
                if ("url".equals(key)) return "https://api.anthropic.com/v1/messages";
                if ("model".equals(key)) return "claude-3-5-sonnet-latest";
                if ("authHeader".equals(key)) return "x-api-key";
                if ("authPrefix".equals(key)) return "";
                if ("anthropicVersion".equals(key)) return "2023-06-01";
                break;
            case "OPENAI": default:
                if ("url".equals(key)) return "https://api.openai.com/v1/chat/completions";
                if ("model".equals(key)) return "gpt-4o-mini";
                break;
        }
        if ("authHeader".equals(key)) return "Authorization";
        if ("authPrefix".equals(key)) return "Bearer";
        if ("anthropicVersion".equals(key)) return "2023-06-01";
        return "";
    }

    /**
     * Creates a small toggle button that shows/hides the content of a JPasswordField.
     * Default state: password hidden (echo char = '●').
     */
    private static JButton createPasswordToggle(JPasswordField field) {
        final char defaultEcho = field.getEchoChar() != 0 ? field.getEchoChar() : '●';
        JButton btn = new JButton("👁");
        btn.setToolTipText("Passwort anzeigen/verbergen");
        btn.setMargin(new Insets(1, 4, 1, 4));
        btn.setFocusable(false);
        btn.addActionListener(e -> {
            if (field.getEchoChar() == 0) {
                // Currently visible → hide
                field.setEchoChar(defaultEcho);
                btn.setText("👁");
            } else {
                // Currently hidden → show
                field.setEchoChar((char) 0);
                btn.setText("🔒");
            }
        });
        return btn;
    }
}

