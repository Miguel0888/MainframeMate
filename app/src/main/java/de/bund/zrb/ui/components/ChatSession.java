package de.bund.zrb.ui.components;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.runtime.ToolRegistryImpl;
import de.bund.zrb.ui.util.ChatFormatter;
import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.ChatStreamListener;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.mcp.McpTool;
import de.bund.zrb.service.McpServiceImpl;
import de.bund.zrb.service.McpChatEventBridge;

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
    private final JComboBox<String> toolComboBox;
    private final JLabel statusLabel;
    private final JButton cancelButton;
    private final MainframeContext maeinframeContext;
    private boolean awaitingBotResponse = false;

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


        toolComboBox = new JComboBox<>();
        toolComboBox.addItem("");
        ToolRegistryImpl.getInstance().getAllTools().forEach(tool ->
                toolComboBox.addItem(tool.getSpec().getName())
        );
        toolComboBox.setPreferredSize(new Dimension(150, 24));
        toolComboBox.setFocusable(false);

        JPanel buttonPanel = new JPanel(new BorderLayout(4, 0));
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftButtons.add(attachButton);
        leftButtons.add(toolComboBox);

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

                // If context memory is enabled, feed tool result back into the session history
                // so the next LLM call can take it into account.
                if (contextMemoryCheckbox != null && contextMemoryCheckbox.isSelected()
                        && event.getType() == de.zrb.bund.newApi.ChatEvent.Type.TOOL_RESULT) {
                    String msg = "TOOL_RESULT " + (event.getToolName() == null ? "" : event.getToolName())
                            + "\n```json\n" + body + "\n```";
                    chatManager.getHistory(sessionId).addToolMessage(msg);
                }
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

        message = applyTool(message);
        awaitingBotResponse = true;

        String finalMessage = message;
        new Thread(() -> {
            try {
                final StringBuilder currentBotResponse = new StringBuilder();
                boolean success = chatManager.streamAnswer(sessionId, contextMemoryCheckbox.isSelected(), finalMessage, new ChatStreamListener() {
                    @Override
                    public void onStreamStart() {
                        SwingUtilities.invokeLater(() -> {
                            Timestamp usrId = chatManager.getHistory(sessionId).addUserMessage(finalMessage);
                            formatter.appendUserMessage(finalMessage, () -> {
                                chatManager.getHistory(sessionId).remove(usrId);
                            });
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

    private void maybeExecuteToolCall(String botText) {
        JsonObject call = extractToolCall(botText);
        if (call == null) {
            return;
        }

        try {
            // 1) Execute tool-call (this will publish TOOL_USE/TOOL_RESULT events)
            mcpService.accept(call, sessionId, null);

            // 2) Trigger an automatic assistant follow-up so the model can respond using the tool result
            //    (tool result was added to history as role=tool in subscribeToToolEvents when context memory is on)
            if (contextMemoryCheckbox != null && contextMemoryCheckbox.isSelected()) {
                streamAssistantFollowUp("Nutze das TOOL_RESULT oben und antworte dem Nutzer.");
            }
        } catch (Exception e) {
            // Tool konnte nicht ausgeführt werden (Parsing/Validierung/Tool-Handler Fehler)
            SwingUtilities.invokeLater(() -> {
                String toolName = (call != null && call.has("name") && !call.get("name").isJsonNull())
                        ? call.get("name").getAsString()
                        : "(unbekannt)";

                JsonObject error = new JsonObject();
                error.addProperty("status", "error");
                error.addProperty("errorType", e.getClass().getName());
                error.addProperty("message", e.getMessage() == null ? "Unbekannter Fehler" : e.getMessage());
                error.add("toolCall", call);

                // Include expected fields (types + descriptions) so the model can repair the tool call.
                try {
                    de.zrb.bund.newApi.mcp.McpTool tool = ToolRegistryImpl.getInstance().getToolByName(toolName);
                    if (tool != null && tool.getSpec() != null && tool.getSpec().getInputSchema() != null
                            && tool.getSpec().getInputSchema().getProperties() != null) {
                        com.google.gson.JsonObject expected = new com.google.gson.JsonObject();
                        for (java.util.Map.Entry<String, de.zrb.bund.newApi.mcp.ToolSpec.Property> en : tool.getSpec().getInputSchema().getProperties().entrySet()) {
                            if (en.getKey() == null || en.getValue() == null) {
                                continue;
                            }
                            com.google.gson.JsonObject p = new com.google.gson.JsonObject();
                            p.addProperty("type", en.getValue().getType());
                            p.addProperty("description", en.getValue().getDescription());
                            expected.add(en.getKey(), p);
                        }
                        error.add("expectedProperties", expected);
                        if (tool.getSpec().getInputSchema().getRequired() != null) {
                            com.google.gson.JsonArray req = new com.google.gson.JsonArray();
                            for (String r : tool.getSpec().getInputSchema().getRequired()) {
                                req.add(r);
                            }
                            error.add("required", req);
                        }
                        if (tool.getSpec().getExampleInput() != null) {
                            com.google.gson.JsonElement ex = new com.google.gson.Gson().toJsonTree(tool.getSpec().getExampleInput());
                            error.add("exampleInput", ex);
                        }
                    }
                } catch (Exception ignore) {
                    // don't let introspection break the error reporting
                }

                error.addProperty("hint",
                        "Prüfe das Tool-Call JSON. Erwartet wird z.B. {\"name\":\"open_file\",\"input\":{...}} oder tool_input/arguments. " +
                                "Stelle sicher, dass alle Pflichtfelder laut ToolSpec vorhanden sind.");

                formatter.appendToolEvent("\u26a0 Tool-Fehler: " + toolName, error.toString(), true);

                if (contextMemoryCheckbox != null && contextMemoryCheckbox.isSelected()) {
                    String msg = "TOOL_RESULT " + toolName + "\n```json\n" + error + "\n```";
                    chatManager.getHistory(sessionId).addToolMessage(msg);
                    // Danach direkt Follow-up anstoßen: KI soll den Call korrigieren/erneut versuchen.
                    streamAssistantFollowUp("Das Tool ist fehlgeschlagen. Analysiere TOOL_RESULT (Fehler + ToolCall + Required/ExpectedProperties) und sende einen korrigierten Tool-Call JSON.");
                }
            });
        }
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
        // We intentionally re-use the same UI streaming UX ('Bot schreibt...')
        // but we do NOT add a new user message to history (this is an internal follow-up).
        String prompt = (followUpUserText == null || followUpUserText.trim().isEmpty())
                ? "Bitte fahre fort basierend auf dem TOOL_RESULT." : followUpUserText;

        new Thread(() -> {
            try {
                final StringBuilder followUpResponse = new StringBuilder();
                chatManager.streamAnswer(sessionId, true, prompt, new ChatStreamListener() {
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
                            Timestamp botId = chatManager.getHistory(sessionId).addBotMessage(followUpResponse.toString());
                            formatter.endBotMessage(() -> chatManager.getHistory(sessionId).remove(botId));
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

    private String applyTool(String userInput) {
        String selectedToolName = (String) toolComboBox.getSelectedItem();
        if (selectedToolName == null || selectedToolName.trim().isEmpty()) {
            return userInput;
        }

        McpTool tool = ToolRegistryImpl.getInstance().getAllTools().stream()
                .filter(t -> t.getSpec().getName().equals(selectedToolName))
                .findFirst()
                .orElse(null);

        if (tool == null) return userInput;

        Settings settings = SettingsHelper.load();
        String prefix = settings.aiConfig.getOrDefault("toolPrefix", "");
        String postfix = settings.aiConfig.getOrDefault("toolPostfix", "");
        boolean wrap = Boolean.parseBoolean(settings.aiConfig.getOrDefault("wrapjson", "true"));
        boolean pretty = Boolean.parseBoolean(settings.aiConfig.getOrDefault("prettyjson", "true"));
        String toolJson = tool.getSpec().toWrappedJson(wrap, pretty);

        return String.format("%s\n%s\n%s\n%s",
                prefix, toolJson, postfix, userInput);
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

        // 1) Try to parse full response
        JsonObject single = extractToolCall(text);
        if (single != null) {
            results.add(single);
            return results;
        }

        // 2) Parse multiple JSON objects by brace matching
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

        // Enforce limit if configured
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
                SwingUtilities.invokeLater(() -> formatter.appendToolEvent("Tool: " + toolName, finalResult.toString(), isError));

                if (contextMemoryCheckbox != null && contextMemoryCheckbox.isSelected()) {
                    String msg = "TOOL_RESULT " + toolName + "\n```json\n" + finalResult + "\n```";
                    chatManager.getHistory(sessionId).addToolMessage(msg);
                }
            }

            // Single follow-up with all results collected
            if (contextMemoryCheckbox != null && contextMemoryCheckbox.isSelected()) {
                streamAssistantFollowUp("Nutze die TOOL_RESULTs oben und antworte dem Nutzer in EINER Nachricht. " +
                        "Wenn Fehler enthalten sind, weise darauf hin und schlage einen korrigierten Tool-Call vor.");
            }
        }).start();
    }

    private JsonObject executeSingleToolCall(JsonObject call) {
        try {
            JsonObject result = mcpService.executeToolCall(call, null);
            if (result != null && !result.has("toolName")) {
                String toolName = call.has("name") ? call.get("name").getAsString() : "unbekannt";
                result.addProperty("toolName", toolName);
            }
            return result;
        } catch (Exception e) {
            JsonObject error = new JsonObject();
            error.addProperty("status", "error");
            error.addProperty("errorType", e.getClass().getName());
            error.addProperty("message", e.getMessage() == null ? "Unbekannter Fehler" : e.getMessage());
            error.add("toolCall", call);
            error.addProperty("toolName", call.has("name") ? call.get("name").getAsString() : "unbekannt");
            error.addProperty("hint", "Tool-Call pruefen und erneut senden.");
            return error;
        }
    }
}
