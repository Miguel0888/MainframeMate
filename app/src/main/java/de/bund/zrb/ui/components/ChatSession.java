package de.bund.zrb.ui.components;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.runtime.ToolRegistryImpl;
import de.bund.zrb.tools.ToolAccessType;
import de.bund.zrb.tools.ToolAccessTypeDefaults;
import de.bund.zrb.tools.ToolPolicy;
import de.bund.zrb.tools.ToolPolicyRepository;
import de.bund.zrb.ui.settings.ToolPolicyDialog;
import de.bund.zrb.ui.util.ChatFormatter;
import de.bund.zrb.ui.util.ToolApprovalDecision;
import de.bund.zrb.ui.util.ToolApprovalRequest;
import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.ChatStreamListener;
import de.zrb.bund.api.MainframeContext;
import de.bund.zrb.service.McpServiceImpl;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.UUID;

public class ChatSession extends JPanel {

    private final UUID sessionId;
    private final ChatManager chatManager;

    private final ChatFormatter formatter;
    private final JTextArea inputArea;
    private final JLabel statusLabel;
    private final JButton cancelButton;
    private final MainframeContext maeinframeContext;
    private final ToolPolicyRepository toolPolicyRepository;
    private boolean awaitingBotResponse = false;

    private final JComboBox<ChatMode> modeComboBox;
    private final JCheckBox keepAliveCheckbox;
    private final JCheckBox contextMemoryCheckbox;

    private final de.bund.zrb.service.McpChatEventBridge chatEventBridge;
    private de.bund.zrb.service.McpChatEventBridge.Listener chatEventListener;
    private final McpServiceImpl mcpService;

    private static final int MAX_TOOL_CALLS = 0; // 0 = unlimited

    public ChatSession(MainframeContext mainframeContext, ChatManager chatManager, JCheckBox keepAliveCheckbox, JCheckBox contextMemoryCheckbox) {
        this(mainframeContext, chatManager, keepAliveCheckbox, contextMemoryCheckbox, null);
    }

    public ChatSession(MainframeContext mainframeContext, ChatManager chatManager, JCheckBox keepAliveCheckbox, JCheckBox contextMemoryCheckbox,
                       de.bund.zrb.service.McpChatEventBridge chatEventBridge) {
        this.maeinframeContext = mainframeContext;
        this.chatManager = chatManager;
        this.keepAliveCheckbox = keepAliveCheckbox;
        this.contextMemoryCheckbox = contextMemoryCheckbox;
        this.chatEventBridge = chatEventBridge;
        this.mcpService = new McpServiceImpl(ToolRegistryImpl.getInstance(), chatEventBridge);
        this.toolPolicyRepository = new ToolPolicyRepository();
        this.sessionId = UUID.randomUUID();

        setLayout(new BorderLayout(4, 4));

        JPanel messageContainer = new JPanel();
        messageContainer.setLayout(new BoxLayout(messageContainer, BoxLayout.Y_AXIS));
        messageContainer.setBackground(UIManager.getColor("Panel.background"));

        this.formatter = new ChatFormatter(messageContainer);

        JScrollPane chatScroll = new JScrollPane(messageContainer);
        chatScroll.setBorder(BorderFactory.createEmptyBorder());
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(chatScroll, BorderLayout.CENTER);

        Settings settings = SettingsHelper.load();
        String fontName = settings.aiConfig.getOrDefault("editor.font", "SansSerif");
        int fontSize = Integer.parseInt(settings.aiConfig.getOrDefault("editor.fontSize", "18"));

        inputArea = new JTextArea(Integer.parseInt(settings.aiConfig.getOrDefault("editor.lines", "3")), 30);
        inputArea.setFont(new Font(fontName, Font.PLAIN, fontSize));
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (e.isShiftDown()) {
                        inputArea.append("\n");
                    } else {
                        e.consume();
                        sendMessage();
                    }
                }
            }
        });

        JButton sendButton = new JButton("⏎");
        sendButton.setToolTipText("Nachricht senden");
        sendButton.addActionListener(e -> sendMessage());

        JButton attachButton = new JButton("+");
        attachButton.setToolTipText("Aktiven Tab teilen");
        attachButton.addActionListener(e -> {
            onAttachContent();
        });


        // Chat-Mode Dropdown (Ask/Edit/Plan/Agent)
        modeComboBox = new JComboBox<>(ChatMode.values());
        modeComboBox.setSelectedItem(ChatMode.AGENT);
        modeComboBox.setToolTipText(((ChatMode) modeComboBox.getSelectedItem()).getTooltip());
        modeComboBox.addActionListener(e -> {
            ChatMode m = (ChatMode) modeComboBox.getSelectedItem();
            if (m != null) {
                modeComboBox.setToolTipText(m.getTooltip());
            }
        });
        modeComboBox.setPreferredSize(new Dimension(120, 24));
        modeComboBox.setFocusable(false);

        JPanel buttonPanel = new JPanel(new BorderLayout(4, 0));
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton toolPolicyButton = new JButton("🛠");
        toolPolicyButton.setToolTipText("Tools konfigurieren");
        toolPolicyButton.setFocusable(false);
        toolPolicyButton.addActionListener(e -> ToolPolicyDialog.show(this));

        leftButtons.add(attachButton);
        leftButtons.add(toolPolicyButton);
        leftButtons.add(modeComboBox);

        buttonPanel.add(leftButtons, BorderLayout.WEST);
        buttonPanel.add(sendButton, BorderLayout.EAST);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));

        cancelButton = new JButton("⛔");
        cancelButton.setToolTipText("Abbrechen");
        cancelButton.setVisible(false);
        cancelButton.setFocusable(false);
        cancelButton.setMargin(new Insets(0, 4, 0, 4));
        cancelButton.addActionListener(e -> {
            chatManager.cancel(sessionId);
            setStatus("❌ Abgebrochen");
            cancelButton.setVisible(false);
        });

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        statusPanel.add(cancelButton, BorderLayout.EAST);
        statusPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JPanel inputPanel = new JPanel(new BorderLayout(4, 4));
        inputPanel.add(statusPanel, BorderLayout.NORTH);
        inputPanel.add(inputScroll, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(inputPanel, BorderLayout.SOUTH);

        subscribeToToolEvents();
    }

    private void subscribeToToolEvents() {
        if (chatEventBridge == null) {
            return;
        }
        chatEventListener = (id, event) -> {
            if (id == null || !id.equals(sessionId) || event == null) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                String header = "🔧 Tool: " + (event.getToolName() == null ? "" : event.getToolName());
                String body = event.getPayload() != null ? event.getPayload().toString() : "";

                boolean isError = false;
                if (event.getPayload() != null && event.getPayload().has("status")
                        && !event.getPayload().get("status").isJsonNull()) {
                    try {
                        isError = "error".equalsIgnoreCase(event.getPayload().get("status").getAsString());
                    } catch (Exception ignore) {
                        // ignore
                    }
                }

                formatter.appendToolEvent(header, body, isError);

                // Wichtig: Tool-Results werden in ChatSession aggregiert und als EIN Tool-Message in die History geschrieben.
                // Hier deshalb NICHT nochmal in die History spiegeln (verhindert Duplikate).
            });
        };
        chatEventBridge.addListener(chatEventListener);

        addHierarchyListener(e -> {
            if (!isDisplayable() && chatEventListener != null) {
                chatEventBridge.removeListener(chatEventListener);
            }
        });
    }

    private void onAttachContent() {
        maeinframeContext.getSelectedTab().ifPresent(tab -> {
            String code = tab.getContent();
            if (code != null && !code.trim().isEmpty()) {
                String escaped = code.replace("```", "ʼʼʼ"); // Triple backtick schützen
                String wrapped = "```\n" + escaped + "\n```";

                // Optional: ans Eingabefeld anhängen oder ersetzen
                inputArea.setText(inputArea.getText().isEmpty()
                        ? wrapped
                        : inputArea.getText() + "\n\n" + wrapped);
                inputArea.requestFocus();
                inputArea.setCaretPosition(inputArea.getText().length());
            } else {
                JOptionPane.showMessageDialog(this,
                        "Der aktuelle Editor-Tab ist leer oder nicht lesbar.",
                        "Kein Inhalt", JOptionPane.WARNING_MESSAGE);
            }
        });
    }

    private void sendMessage() {
        String message = inputArea.getText().trim();
        if (message.isEmpty()) return;

        // Prefix with system prompt based on selected mode
        ChatMode mode = (ChatMode) modeComboBox.getSelectedItem();
        ChatMode resolvedMode = mode != null ? mode : ChatMode.ASK;
        String systemPrompt = composeSystemPrompt(resolvedMode);

        // Persist system prompt in history so it's included in every API call (incl. tool follow-ups)
        if (contextMemoryCheckbox.isSelected()) {
            chatManager.getHistory(sessionId).setSystemPrompt(systemPrompt);
        }

        String finalPrompt = buildPromptWithMode(systemPrompt, message, contextMemoryCheckbox.isSelected());

        awaitingBotResponse = true;

        final String userMessageForHistory = message;
        final String finalMessage = finalPrompt;
        new Thread(() -> {
            try {
                final StringBuilder currentBotResponse = new StringBuilder();
                boolean success = chatManager.streamAnswer(sessionId, contextMemoryCheckbox.isSelected(), finalMessage, new ChatStreamListener() {
                    @Override
                    public void onStreamStart() {
                        SwingUtilities.invokeLater(() -> {
                            Timestamp usrId = chatManager.getHistory(sessionId).addUserMessage(userMessageForHistory);
                            formatter.appendUserMessage(userMessageForHistory, () -> chatManager.getHistory(sessionId).remove(usrId));
                            inputArea.setText("");
                            formatter.startBotMessage();
                            setStatus("🤖 Bot schreibt...");
                            cancelButton.setVisible(true);
                        });
                    }

                    @Override
                    public void onStreamChunk(String chunk) {
                        currentBotResponse.append(chunk);
                        SwingUtilities.invokeLater(() -> formatter.appendBotMessageChunk(chunk));
                    }

                    @Override
                    public void onStreamEnd() {
                        SwingUtilities.invokeLater(() -> {
                            String botText = currentBotResponse.toString();

                            java.util.List<JsonObject> toolCalls = extractToolCalls(botText);
                            if (!toolCalls.isEmpty()) {
                                // Remove the raw bot message and replace with folded tool-call cards
                                formatter.removeCurrentBotMessage();

                                for (JsonObject call : toolCalls) {
                                    String toolName = call.has("name") && !call.get("name").isJsonNull()
                                            ? call.get("name").getAsString() : "unbekannt";
                                    formatter.appendBotToolCall("Tool-Call: " + toolName, call.toString());
                                }

                                executeToolCallsSequentially(toolCalls);
                            } else if (botText == null || botText.trim().isEmpty()) {
                                formatter.removeCurrentBotMessage();
                                formatter.appendToolEvent(
                                        "⚠️ Leere Modellantwort",
                                        "Das Modell hat keine Textantwort und keinen Tool-Call geliefert.",
                                        true
                                );
                            } else {
                                Timestamp botId = chatManager.getHistory(sessionId).addBotMessage(botText);
                                formatter.endBotMessage(() -> chatManager.getHistory(sessionId).remove(botId));
                            }

                            awaitingBotResponse = false;
                            cancelButton.setVisible(false);
                            setStatus(" ");
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        SwingUtilities.invokeLater(() -> {
                            setStatus("⚠️ Fehler");
                            cancelButton.setVisible(false);
                            JOptionPane.showMessageDialog(ChatSession.this,
                                    "Fehler beim Abrufen der AI-Antwort:\n" + e.getMessage(),
                                    "AI-Fehler", JOptionPane.ERROR_MESSAGE);
                        });
                    }
                }, keepAliveCheckbox.isSelected());

                if (!success) awaitingBotResponse = false;

            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    setStatus("⚠️ Fehler");
                    JOptionPane.showMessageDialog(ChatSession.this,
                            "Fehler beim Starten der Anfrage:\n" + e.getMessage(),
                            "AI-Fehler", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private String composeSystemPrompt(ChatMode mode) {
        StringBuilder sb = new StringBuilder();
        sb.append(mode.getSystemPrompt());

        String contractPrefix = resolveModeToolPrefix(mode);
        String contractPostfix = resolveModeToolPostfix(mode);
        if (!contractPrefix.isEmpty()) {
            sb.append("\n\n").append(contractPrefix);
        }

        String summary = buildToolSummary(mode);
        if (!summary.isEmpty()) {
            sb.append("\n\n").append(summary);
        }

        if (!contractPostfix.isEmpty()) {
            sb.append("\n\n").append(contractPostfix);
        }
        return sb.toString();
    }

    private String resolveModeToolPrefix(ChatMode mode) {
        Settings settings = SettingsHelper.load();
        String modeKey = "toolPrefix." + mode.name();
        String value = settings.aiConfig.getOrDefault(modeKey, "").trim();
        if (!value.isEmpty()) {
            return value;
        }
        value = settings.aiConfig.getOrDefault("toolPrefix", "").trim();
        if (!value.isEmpty()) {
            return value;
        }
        return mode.getDefaultToolPrefix();
    }

    private String resolveModeToolPostfix(ChatMode mode) {
        Settings settings = SettingsHelper.load();
        String modeKey = "toolPostfix." + mode.name();
        String value = settings.aiConfig.getOrDefault(modeKey, "").trim();
        if (!value.isEmpty()) {
            return value;
        }
        value = settings.aiConfig.getOrDefault("toolPostfix", "").trim();
        if (!value.isEmpty()) {
            return value;
        }
        return mode.getDefaultToolPostfix();
    }

    private String buildToolSummary(ChatMode mode) {
        if (!mode.isToolAware()) {
            return "";
        }

        java.util.List<ToolPolicy> policies = toolPolicyRepository.loadAll();
        StringBuilder sb = new StringBuilder("Aktivierte Tools (nur Übersicht):\n");
        boolean found = false;
        for (ToolPolicy policy : policies) {
            if (policy == null || !policy.isEnabled() || policy.getToolName() == null) {
                continue;
            }
            ToolAccessType accessType = policy.getAccessType() != null
                    ? policy.getAccessType()
                    : ToolAccessTypeDefaults.resolveDefault(policy.getToolName());
            if (!mode.getAllowedToolAccess().contains(accessType)) {
                continue;
            }

            de.zrb.bund.newApi.mcp.McpTool tool = ToolRegistryImpl.getInstance().getAllTools().stream()
                    .filter(t -> policy.getToolName().equals(t.getSpec().getName()))
                    .findFirst()
                    .orElse(null);
            String description = tool != null ? tool.getSpec().getDescription() : "";
            sb.append("- ").append(policy.getToolName())
                    .append(" [").append(accessType.name()).append("]")
                    .append(policy.isAskBeforeUse() ? " [ASK]" : "")
                    .append(description == null || description.trim().isEmpty() ? "" : ": " + description)
                    .append("\n");
            found = true;
        }
        sb.append("- describe_tool [READ]: Liefert Details/Schema für ein Tool nur bei Bedarf.\n");
        if (!found) {
            sb.append("- (keine aktivierten Tools in diesem Modus)\n");
        }
        sb.append("Nutze describe_tool, wenn du Tool-Details/Parameter brauchst.");
        return sb.toString();
    }

    /**
     * Build the final prompt for the model.
     * If context is enabled, send only the current user text and rely on ChatManager history handling.
     * If context is disabled, inline the system prompt manually.
     */
    private String buildPromptWithMode(String systemPrompt, String userText, boolean useContext) {
        if (useContext) {
            return userText == null ? "" : userText.trim();
        }

        StringBuilder sb = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            sb.append("SYSTEM:\n").append(systemPrompt.trim()).append("\n\n");
        }
        if (userText != null && !userText.trim().isEmpty()) {
            sb.append("Du: ").append(userText.trim());
        }
        return sb.toString();
    }
    private JsonObject extractToolCall(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();

        // Case A: assistant replies with just JSON
        JsonObject obj = tryParseToolCallJson(trimmed);
        if (obj != null) {
            return obj;
        }

        // Case B: assistant wraps JSON in a ```json ... ``` fenced block
        int fenceStart = trimmed.indexOf("```json");
        if (fenceStart >= 0) {
            int jsonStart = trimmed.indexOf('\n', fenceStart);
            if (jsonStart >= 0) {
                int fenceEnd = trimmed.indexOf("```", jsonStart + 1);
                if (fenceEnd > jsonStart) {
                    String inside = trimmed.substring(jsonStart + 1, fenceEnd).trim();
                    obj = tryParseToolCallJson(inside);
                    if (obj != null) {
                        return obj;
                    }
                }
            }
        }

        // Case C: JSON is embedded within other text
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            String inside = trimmed.substring(firstBrace, lastBrace + 1).trim();
            obj = tryParseToolCallJson(inside);
            if (obj != null) {
                return obj;
            }
        }

        return null;
    }

    private JsonObject tryParseToolCallJson(String json) {
        if (json == null) {
            return null;
        }
        String s = json.trim();
        if (!s.startsWith("{") || !s.endsWith("}")) {
            return null;
        }

        try {
            JsonElement parsed = JsonParser.parseString(s);
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonObject obj = parsed.getAsJsonObject();

            // Accept both formats:
            // 1) {"name":"open_file","input":{...}}
            // 2) {"name":"open_file","tool_input":{...}}
            // 3) {"name":"open_file","arguments":"{...}"}
            if (!obj.has("name")) {
                return null;
            }
            if (!obj.has("input") && !obj.has("tool_input") && !obj.has("arguments")) {
                return null;
            }
            return obj;
        } catch (Exception ignore) {
            return null;
        }
    }

    private void streamAssistantFollowUp(String followUpUserText) {
        ChatMode mode = (ChatMode) modeComboBox.getSelectedItem();
        ChatMode resolvedMode = mode != null ? mode : ChatMode.ASK;
        String systemPrompt = composeSystemPrompt(resolvedMode);
        String userText = (followUpUserText == null || followUpUserText.trim().isEmpty())
                ? "Bitte fahre fort basierend auf dem TOOL_RESULT." : followUpUserText;
        String finalPrompt = buildPromptWithMode(systemPrompt, userText, true);

        new Thread(() -> {
            try {
                final StringBuilder followUpResponse = new StringBuilder();
                chatManager.streamAnswer(sessionId, true, finalPrompt, new ChatStreamListener() {
                    @Override
                    public void onStreamStart() {
                        SwingUtilities.invokeLater(() -> {
                            formatter.startBotMessage();
                            setStatus("🤖 Bot schreibt...");
                            cancelButton.setVisible(true);
                        });
                    }

                    @Override
                    public void onStreamChunk(String chunk) {
                        followUpResponse.append(chunk);
                        SwingUtilities.invokeLater(() -> formatter.appendBotMessageChunk(chunk));
                    }

                    @Override
                    public void onStreamEnd() {
                        SwingUtilities.invokeLater(() -> {
                            String botText = followUpResponse.toString();
                            java.util.List<JsonObject> toolCalls = extractToolCalls(botText);
                            if (!toolCalls.isEmpty()) {
                                formatter.removeCurrentBotMessage();
                                for (JsonObject call : toolCalls) {
                                    String toolName = call.has("name") && !call.get("name").isJsonNull()
                                            ? call.get("name").getAsString() : "unbekannt";
                                    formatter.appendBotToolCall("Tool-Call: " + toolName, call.toString());
                                }
                                executeToolCallsSequentially(toolCalls);
                            } else if (botText == null || botText.trim().isEmpty()) {
                                formatter.removeCurrentBotMessage();
                                formatter.appendToolEvent(
                                        "⚠️ Leere Modellantwort",
                                        "Das Modell hat keine Textantwort und keinen Tool-Call geliefert.",
                                        true
                                );
                            } else {
                                Timestamp botId = chatManager.getHistory(sessionId).addBotMessage(botText);
                                formatter.endBotMessage(() -> chatManager.getHistory(sessionId).remove(botId));
                            }
                            cancelButton.setVisible(false);
                            setStatus(" ");
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        SwingUtilities.invokeLater(() -> {
                            setStatus("⚠️ Fehler");
                            cancelButton.setVisible(false);
                            JOptionPane.showMessageDialog(ChatSession.this,
                                    "Fehler beim Abrufen der AI-Antwort:\n" + e.getMessage(),
                                    "AI-Fehler", JOptionPane.ERROR_MESSAGE);
                        });
                    }
                }, keepAliveCheckbox.isSelected());
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    setStatus("⚠️ Fehler");
                    JOptionPane.showMessageDialog(ChatSession.this,
                            "Fehler beim Starten der Anfrage:\n" + e.getMessage(),
                            "AI-Fehler", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void setStatus(String status) {
        statusLabel.setText(status);
    }

    public UUID getSessionId() {
        return sessionId;
    }

    private java.util.List<JsonObject> extractToolCalls(String text) {
        java.util.List<JsonObject> results = new java.util.ArrayList<>();
        if (text == null) {
            return results;
        }

        JsonObject single = extractToolCall(text);
        if (single != null) {
            results.add(single);
            return results;
        }

        String s = text;
        int depth = 0;
        int start = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    String candidate = s.substring(start, i + 1).trim();
                    JsonObject obj = tryParseToolCallJson(candidate);
                    if (obj != null) {
                        results.add(obj);
                    }
                    start = -1;
                }
            }
        }

        if (MAX_TOOL_CALLS > 0 && results.size() > MAX_TOOL_CALLS) {
            return results.subList(0, MAX_TOOL_CALLS);
        }

        return results;
    }

    private void executeToolCallsSequentially(java.util.List<JsonObject> calls) {
        if (calls == null || calls.isEmpty()) {
            return;
        }

        new Thread(() -> {
            java.util.List<JsonObject> results = new java.util.ArrayList<>();

            for (JsonObject call : calls) {
                JsonObject result = executeSingleToolCall(call);
                results.add(result);

                boolean isError = result != null && result.has("status")
                        && !result.get("status").isJsonNull()
                        && "error".equalsIgnoreCase(result.get("status").getAsString());

                String toolName = result != null && result.has("toolName") && !result.get("toolName").isJsonNull()
                        ? result.get("toolName").getAsString()
                        : (call.has("name") ? call.get("name").getAsString() : "unbekannt");

                JsonObject finalResult = result == null ? new JsonObject() : result;
                SwingUtilities.invokeLater(() -> formatter.appendToolEvent(
                        "Tool: " + toolName,
                        finalResult.toString(),
                        isError
                ));
            }

            if (contextMemoryCheckbox != null && contextMemoryCheckbox.isSelected()) {
                JsonObject aggregated = new JsonObject();
                aggregated.addProperty("type", "tool_results");
                aggregated.addProperty("count", results.size());

                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (JsonObject r : results) {
                    arr.add(r == null ? new JsonObject() : r);
                }
                aggregated.add("results", arr);

                chatManager.getHistory(sessionId).addToolMessage(
                        "TOOL_RESULTS\n```json\n" + aggregated.toString() + "\n```"
                );

                ChatMode currentMode = (ChatMode) modeComboBox.getSelectedItem();
                String followUp;
                if (currentMode == ChatMode.AGENT) {
                    followUp = "Du hast Tool-Ergebnisse erhalten. " +
                            "Kannst du die Frage des Nutzers damit beantworten? " +
                            "Wenn ja, antworte direkt in natürlicher Sprache (KEIN JSON, KEIN Tool-Call). " +
                            "Nur wenn du KONKRET weitere Dateien lesen musst, um die Frage zu beantworten, " +
                            "erzeuge einen weiteren Tool-Call. Lies keine Datei, die du bereits gelesen hast. " +
                            "Wenn ein Fehler aufgetreten ist, weise darauf hin.";
                } else {
                    followUp = "Nutze die TOOL_RESULTS oben und antworte dem Nutzer. " +
                            "Wenn Fehler enthalten sind, weise darauf hin.";
                }

                streamAssistantFollowUp(followUp);
            }
        }).start();
    }

    private JsonObject executeSingleToolCall(JsonObject call) {
        try {
            if (call == null) {
                return createErrorResult(null, "Leerer Tool-Call", null);
            }

            String toolName = call.has("name") && !call.get("name").isJsonNull()
                    ? call.get("name").getAsString()
                    : null;

            if (toolName == null || toolName.trim().isEmpty()) {
                return createErrorResult(null, "Tool-Name fehlt", call);
            }

            if (!isSystemTool(toolName)) {
                ToolPolicy policy = toolPolicyRepository.findByToolName(toolName);
                if (policy == null || !policy.isEnabled()) {
                    return createBlockedResult(toolName, "Tool ist vom Nutzer deaktiviert", call);
                }

                ToolAccessType accessType = policy.getAccessType() != null
                        ? policy.getAccessType()
                        : ToolAccessTypeDefaults.resolveDefault(toolName);

                ChatMode mode = (ChatMode) modeComboBox.getSelectedItem();
                ChatMode resolvedMode = mode != null ? mode : ChatMode.ASK;
                if (resolvedMode.isToolAware() && !resolvedMode.getAllowedToolAccess().contains(accessType)) {
                    return createBlockedResult(toolName, "Tool ist in diesem Modus nicht erlaubt", call);
                }

                if (policy.isAskBeforeUse()) {
                    ToolApprovalDecision decision = requestUserApproval(toolName, call.toString(), accessType.isWrite());
                    if (decision == ToolApprovalDecision.CANCELLED) {
                        return createCancelledResult(toolName, call);
                    }
                }
            }

            JsonObject result = isSystemTool(toolName)
                    ? executeSystemTool(toolName, call)
                    : mcpService.executeToolCall(call, null);
            if (result != null && !result.has("toolName")) {
                result.addProperty("toolName", toolName);
            }
            return result;

        } catch (Exception e) {
            return createErrorResult(null, e.getMessage(), call);
        }
    }

    private boolean isSystemTool(String toolName) {
        return "describe_tool".equalsIgnoreCase(toolName);
    }

    private JsonObject executeSystemTool(String toolName, JsonObject call) {
        if (!"describe_tool".equalsIgnoreCase(toolName)) {
            return createErrorResult(toolName, "Unbekanntes Systemtool", call);
        }

        String target = null;
        if (call.has("input") && call.get("input").isJsonObject()) {
            JsonObject input = call.getAsJsonObject("input");
            if (input.has("tool") && !input.get("tool").isJsonNull()) {
                target = input.get("tool").getAsString();
            } else if (input.has("name") && !input.get("name").isJsonNull()) {
                target = input.get("name").getAsString();
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("status", "ok");
        result.addProperty("toolName", "describe_tool");

        if (target == null || target.trim().isEmpty()) {
            result.addProperty("message", "input.tool oder input.name fehlt");
            return result;
        }

        // Make target effectively final for use in lambda (Java 8 requires captured locals to be final/effectively final)
        final String targetName = target;

        de.zrb.bund.newApi.mcp.McpTool tool = ToolRegistryImpl.getInstance().getAllTools().stream()
                .filter(t -> targetName.equalsIgnoreCase(t.getSpec().getName()))
                .findFirst()
                .orElse(null);
        if (tool == null) {
            result.addProperty("message", "Tool nicht gefunden: " + target);
            return result;
        }

        result.addProperty("targetTool", tool.getSpec().getName());
        result.addProperty("description", tool.getSpec().getDescription());

        com.google.gson.JsonObject spec = com.google.gson.JsonParser.parseString(tool.getSpec().toJson()).getAsJsonObject();
        if (spec.has("input_schema")) {
            result.add("inputSchema", spec.get("input_schema"));
        }
        if (tool.getSpec().getExampleInput() != null) {
            result.add("exampleInput", com.google.gson.JsonParser.parseString(new com.google.gson.Gson().toJson(tool.getSpec().getExampleInput())));
        }
        return result;
    }

    private ToolApprovalDecision requestUserApproval(String toolName, String toolCallJson, boolean isWrite) {
        final ToolApprovalRequest[] requestHolder = new ToolApprovalRequest[1];
        Runnable createUi = () -> requestHolder[0] = formatter.requestToolApproval(toolName, toolCallJson, isWrite);

        if (SwingUtilities.isEventDispatchThread()) {
            createUi.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(createUi);
            } catch (Exception e) {
                return fallbackApproval(toolName, isWrite);
            }
        }

        ToolApprovalRequest request = requestHolder[0];
        if (request == null) {
            return fallbackApproval(toolName, isWrite);
        }
        return request.awaitDecision();
    }

    private ToolApprovalDecision fallbackApproval(String toolName, boolean isWrite) {
        int option = JOptionPane.showConfirmDialog(this,
                "Tool ausführen?\n\n" + toolName + (isWrite ? " (WRITE)" : " (READ)"),
                "Tool-Freigabe", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        return option == JOptionPane.OK_OPTION ? ToolApprovalDecision.APPROVED : ToolApprovalDecision.CANCELLED;
    }

    private JsonObject createBlockedResult(String toolName, String message, JsonObject call) {
        JsonObject o = new JsonObject();
        o.addProperty("status", "blocked");
        o.addProperty("toolName", toolName);
        o.addProperty("message", message);
        if (call != null) {
            o.add("toolCall", call);
        }
        return o;
    }

    private JsonObject createCancelledResult(String toolName, JsonObject call) {
        JsonObject o = new JsonObject();
        o.addProperty("status", "cancelled");
        o.addProperty("toolName", toolName);
        o.addProperty("message", "Nutzer hat die Ausführung abgebrochen");
        if (call != null) {
            o.add("toolCall", call);
        }
        return o;
    }

    private JsonObject createErrorResult(String toolName, String message, JsonObject call) {
        JsonObject error = new JsonObject();
        error.addProperty("status", "error");
        if (toolName != null) {
            error.addProperty("toolName", toolName);
        }
        error.addProperty("errorType", "ToolExecutionError");
        error.addProperty("message", message == null ? "Unbekannter Fehler" : message);
        if (call != null) {
            error.add("toolCall", call);
        }
        error.addProperty("hint",
                "Tool-Call prüfen. Erwartetes Format z.B. {\"name\":\"open_file\",\"input\":{\"file\":\"C:\\TEST\"}}."
        );
        return error;
    }
}
