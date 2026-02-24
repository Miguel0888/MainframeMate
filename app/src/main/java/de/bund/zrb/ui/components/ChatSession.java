package de.bund.zrb.ui.components;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.chat.attachment.AttachTabToChatUseCase;
import de.bund.zrb.chat.attachment.AttachmentContextBuilder;
import de.bund.zrb.chat.attachment.BuildHiddenContextUseCase;
import de.bund.zrb.chat.attachment.ChatAttachment;
import de.bund.zrb.chat.attachment.ChatAttachmentStore;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.rag.service.RagService;
import de.bund.zrb.rag.usecase.RagContextBuilder;
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
import de.zrb.bund.newApi.ui.FtpTab;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private volatile String lastUserRequestText = "";
    private final Set<String> toolsUsedInThisChat = new HashSet<>();
    private final Set<String> schemaKnownTools = new HashSet<>();

    // Attachment system
    private final AttachmentChipsPanel attachmentChipsPanel;
    private final AttachTabToChatUseCase attachTabUseCase;
    private final BuildHiddenContextUseCase buildHiddenContextUseCase;
    private final List<String> currentAttachmentIds = new ArrayList<>();

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

        // Initialize attachment system
        this.attachTabUseCase = new AttachTabToChatUseCase();
        this.buildHiddenContextUseCase = new BuildHiddenContextUseCase();
        this.attachmentChipsPanel = new AttachmentChipsPanel(this::onAttachmentRemoved);

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

        JButton attachButton = new JButton("📎");
        attachButton.setToolTipText("Tab anhängen");
        attachButton.addActionListener(e -> {
            showAttachTabDialog();
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

        // Input panel with attachment chips
        JPanel inputPanel = new JPanel(new BorderLayout(4, 4));
        inputPanel.add(statusPanel, BorderLayout.NORTH);

        // Wrapper for input area + attachment chips
        JPanel inputWrapper = new JPanel(new BorderLayout());
        inputWrapper.add(attachmentChipsPanel, BorderLayout.NORTH);
        inputWrapper.add(inputScroll, BorderLayout.CENTER);

        inputPanel.add(inputWrapper, BorderLayout.CENTER);
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
        // Legacy method - now opens dialog
        showAttachTabDialog();
    }

    private void showAttachTabDialog() {
        // Get available tabs from TabbedPaneManager
        List<FtpTab> availableTabs = maeinframeContext.getAllOpenTabs();

        if (availableTabs.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Keine Tabs zum Anhängen geöffnet.",
                    "Keine Tabs", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        AttachTabDialog dialog = new AttachTabDialog(SwingUtilities.getWindowAncestor(this), availableTabs);
        List<FtpTab> selectedTabs = dialog.showAndGetSelection();

        for (FtpTab tab : selectedTabs) {
            attachTab(tab);
        }
    }

    private void attachTab(FtpTab tab) {
        if (tab == null) return;

        try {
            ChatAttachment attachment = attachTabUseCase.execute(tab);
            if (attachment != null) {
                currentAttachmentIds.add(attachment.getId());
                attachmentChipsPanel.addAttachment(attachment);
                setStatus("📎 Anhang hinzugefügt: " + attachment.getName());
            } else {
                JOptionPane.showMessageDialog(this,
                        "Tab konnte nicht angehängt werden (kein Inhalt).",
                        "Anhang fehlgeschlagen", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Fehler beim Anhängen:\n" + e.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onAttachmentRemoved(ChatAttachment attachment) {
        currentAttachmentIds.remove(attachment.getId());
        ChatAttachmentStore.getInstance().remove(attachment.getId());

        // Remove from RAG index to keep index/store consistent
        try {
            RagService.getInstance().removeDocument(attachment.getId());
        } catch (Exception e) {
            // Log but don't fail
        }

        setStatus("📎 Anhang entfernt: " + attachment.getName());
    }

    /**
     * Build hidden context from attachments using query-dependent RAG retrieval.
     * Only the most relevant chunks (Top-K) are included, not the full document text.
     *
     * @param userQuery the user's query to match against
     * @return hidden context string or empty
     */
    private String buildHiddenContextFromAttachments(String userQuery) {
        if (currentAttachmentIds.isEmpty()) {
            return "";
        }

        try {
            // Use RAG to retrieve only relevant chunks
            RagService ragService = RagService.getInstance();
            Set<String> allowedIds = new HashSet<>(currentAttachmentIds);

            // Build context using Top-K retrieval filtered by current attachments
            RagContextBuilder.BuildResult result = ragService.buildContext(
                    userQuery,
                    ragService.getConfig().getFinalTopK(),
                    allowedIds
            );

            if (result.hasTruncations()) {
                setStatus("⚠️ Attachment-Kontext wurde gekürzt");
            }

            String context = result.getContext();
            if (context != null && !context.trim().isEmpty()) {
                return "=== ATTACHMENT CONTEXT (Top-K Relevant Chunks) ===\n" + context + "\n=== END ATTACHMENT CONTEXT ===";
            }
            return "";
        } catch (Exception e) {
            // Fallback to old method if RAG fails
            try {
                AttachmentContextBuilder.BuildResult result = buildHiddenContextUseCase.execute(currentAttachmentIds);
                if (result.hasTruncations()) {
                    setStatus("⚠️ Anhänge wurden gekürzt (Kontextlimit)");
                }
                return result.getContext();
            } catch (Exception ex) {
                setStatus("❌ Fehler beim Erstellen des Attachment-Kontexts");
                return "";
            }
        }
    }

    private void sendMessage() {
        String message = inputArea.getText().trim();
        if (message.isEmpty()) return;
        lastUserRequestText = message;
        agentFollowUpRetries = 0; // Reset retries for new conversation turn

        // Prefix with system prompt based on selected mode
        ChatMode mode = (ChatMode) modeComboBox.getSelectedItem();
        ChatMode resolvedMode = mode != null ? mode : ChatMode.ASK;
        String systemPrompt = composeSystemPrompt(resolvedMode);

        // Always persist system prompt in history so it's included in every API call
        // (incl. tool follow-ups). This is necessary even without context memory because
        // the tool-call loop needs the system prompt to keep working.
        chatManager.getHistory(sessionId).setSystemPrompt(systemPrompt);

        // Build hidden context from attachments using RAG (query-dependent, NOT shown in chat UI)
        String hiddenContext = buildHiddenContextFromAttachments(message);

        String finalPrompt = buildPromptWithMode(systemPrompt, message, contextMemoryCheckbox.isSelected());

        // Prepend hidden context to the prompt (invisible in UI)
        if (!hiddenContext.isEmpty()) {
            finalPrompt = hiddenContext + "\n\n" + finalPrompt;
        }

        awaitingBotResponse = true;

        final String userMessageForHistory = message; // Only store user message, not hidden context
        final String finalMessage = finalPrompt;
        final int attachmentCount = currentAttachmentIds.size();

        // Clear attachments after sending
        currentAttachmentIds.clear();
        attachmentChipsPanel.clear();

        new Thread(() -> {
            try {
                final StringBuilder currentBotResponse = new StringBuilder();
                boolean success = chatManager.streamAnswer(sessionId, contextMemoryCheckbox.isSelected(), finalMessage, new ChatStreamListener() {
                    @Override
                    public void onStreamStart() {
                        SwingUtilities.invokeLater(() -> {
                            Timestamp usrId = chatManager.getHistory(sessionId).addUserMessage(userMessageForHistory);
                            // Show attachment count in UI (but not content)
                            String displayMessage = attachmentCount > 0
                                ? userMessageForHistory + "\n\n📎 " + attachmentCount + " Anhang(e)"
                                : userMessageForHistory;
                            formatter.appendUserMessage(displayMessage, () -> chatManager.getHistory(sessionId).remove(usrId));
                            inputArea.setText("");
                            formatter.startBotMessage();
                            setStatus("🤖 Bot schreibt...");
                            cancelButton.setVisible(true);
                        });
                    }

                    @Override
                    public void onStreamChunk(String chunk) {
                        if (chunk == null || chunk.isEmpty()) {
                            return;
                        }
                        currentBotResponse.append(chunk);
                        SwingUtilities.invokeLater(() -> formatter.appendBotMessageChunk(chunk));
                    }

                    @Override
                    public void onStreamEnd() {
                        SwingUtilities.invokeLater(() -> {
                            String botText = currentBotResponse.toString();

                            java.util.List<JsonObject> toolCalls = extractToolCalls(botText);
                            if (!toolCalls.isEmpty()) {
                                // Remove the raw bot message and replace with (optional prefix +) folded tool-call cards
                                formatter.removeCurrentBotMessage();

                                // Preserve any text the bot wrote before the first tool call JSON
                                String prefixText = extractTextBeforeToolCall(botText);
                                if (prefixText != null && !prefixText.trim().isEmpty()) {
                                    String cleanPrefix = prefixText.trim();
                                    Timestamp prefixId = chatManager.getHistory(sessionId).addBotMessage(cleanPrefix);
                                    formatter.startBotMessage();
                                    formatter.appendBotMessageChunk(cleanPrefix);
                                    formatter.endBotMessage(() -> chatManager.getHistory(sessionId).remove(prefixId));
                                }

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

        String languageInstruction = buildLanguageInstruction();
        if (!languageInstruction.isEmpty()) {
            sb.append("\n\n").append(languageInstruction);
        }

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

    private String buildLanguageInstruction() {
        Settings settings = SettingsHelper.load();
        String language = settings.aiConfig.getOrDefault("assistant.language", "de").trim().toLowerCase();
        if (language.isEmpty() || "none".equals(language)) {
            return "";
        }
        if ("en".equals(language) || "english".equals(language)) {
            return "Respond in English.";
        }
        return "Sprich immer auf Deutsch.";
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

        // Check if native tool calling is active (Ollama /api/chat)
        // In that case, tool schemas are sent natively in the request body,
        // so we only provide a minimal summary in the system prompt.
        boolean nativeToolCalling = isNativeToolCallingActive();

        java.util.List<ToolPolicy> policies = toolPolicyRepository.loadAll();
        StringBuilder sb = new StringBuilder();
        if (nativeToolCalling) {
            sb.append("Dir stehen Tools zur Verfügung (die Schemas werden nativ übergeben).\n");
            sb.append("Nutze die Tools direkt über native tool_calls.\n");
        } else {
            sb.append("Aktivierte Tools (mit wichtigsten Parametern):\n");
        }
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
                    .append(description == null || description.trim().isEmpty() ? "" : ": " + trimDescription(description))
                    .append("\n");

            // Only add detailed parameters when NOT using native tool calling
            if (!nativeToolCalling && tool != null) {
                String paramInfo = extractRequiredParams(tool);
                if (!paramInfo.isEmpty()) {
                    sb.append("  Parameters: ").append(paramInfo).append("\n");
                }
            }

            found = true;
        }
        sb.append("- describe_tool [READ]: Liefert Details/Schema für ein Tool nur bei Bedarf.\n");
        if (!nativeToolCalling) {
            sb.append("  Parameters: tool (string, required)\n");
        }
        if (!found) {
            sb.append("- (keine aktivierten Tools in diesem Modus)\n");
        }
        if (!nativeToolCalling) {
            sb.append("Nutze describe_tool, wenn du Tool-Details/Parameter brauchst.");
        }
        return sb.toString();
    }

    /**
     * Checks if the current AI provider supports native tool calling
     * (Ollama with /api/chat endpoint).
     */
    private boolean isNativeToolCallingActive() {
        try {
            Settings settings = SettingsHelper.load();
            String provider = settings.aiConfig.getOrDefault("provider", "DISABLED");
            if ("OLLAMA".equals(provider)) {
                String url = settings.aiConfig.getOrDefault("ollama.url", "");
                return url.contains("/api/chat");
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Extract a compact summary of required parameters from a tool spec.
     * Returns something like "url (string, required), selector (string, optional)"
     */
    private String extractRequiredParams(de.zrb.bund.newApi.mcp.McpTool tool) {
        try {
            String json = tool.getSpec().toJson();
            JsonObject spec = JsonParser.parseString(json).getAsJsonObject();
            if (!spec.has("input_schema") || !spec.get("input_schema").isJsonObject()) {
                return "";
            }
            JsonObject schema = spec.getAsJsonObject("input_schema");
            if (!schema.has("properties") || !schema.get("properties").isJsonObject()) {
                return "";
            }

            JsonObject props = schema.getAsJsonObject("properties");
            java.util.Set<String> required = new java.util.HashSet<>();
            if (schema.has("required") && schema.get("required").isJsonArray()) {
                for (JsonElement el : schema.getAsJsonArray("required")) {
                    if (el.isJsonPrimitive()) {
                        required.add(el.getAsString());
                    }
                }
            }

            StringBuilder sb = new StringBuilder();
            int count = 0;
            // First add required params, then optional (up to a limit)
            for (java.util.Map.Entry<String, JsonElement> entry : props.entrySet()) {
                if (count >= 5) break; // Limit to avoid bloating system prompt
                String paramName = entry.getKey();
                String paramType = "any";
                if (entry.getValue().isJsonObject() && entry.getValue().getAsJsonObject().has("type")) {
                    paramType = entry.getValue().getAsJsonObject().get("type").getAsString();
                }
                boolean isRequired = required.contains(paramName);
                if (sb.length() > 0) sb.append(", ");
                sb.append(paramName).append(" (").append(paramType);
                if (isRequired) sb.append(", required");
                sb.append(")");
                count++;
            }
            if (props.size() > 5) {
                sb.append(", ...");
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
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

        // Case C: JSON is embedded within other text – find the FIRST valid tool-call JSON
        // (skip small JSON fragments like {"ref":"n1"} that aren't tool calls)
        int searchFrom = 0;
        while (searchFrom < trimmed.length()) {
            int firstBrace = trimmed.indexOf('{', searchFrom);
            if (firstBrace < 0) break;

            // Find matching closing brace
            int depth = 0;
            int matchEnd = -1;
            for (int j = firstBrace; j < trimmed.length(); j++) {
                if (trimmed.charAt(j) == '{') depth++;
                else if (trimmed.charAt(j) == '}') {
                    depth--;
                    if (depth == 0) {
                        matchEnd = j;
                        break;
                    }
                }
            }

            if (matchEnd > firstBrace) {
                String candidate = trimmed.substring(firstBrace, matchEnd + 1).trim();
                obj = tryParseToolCallJson(candidate);
                if (obj != null) {
                    return obj;
                }
                // Not a valid tool call – skip past this JSON object and keep looking
                searchFrom = matchEnd + 1;
            } else {
                break; // unmatched braces
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
            JsonObject normalized = normalizeToolCall(obj);

            if (!normalized.has("name") || normalized.get("name").isJsonNull()
                    || normalized.get("name").getAsString().trim().isEmpty()) {
                return null;
            }

            // Accept tool calls even without explicit input/arguments if the name
            // looks like a registered tool (some tools have no required parameters).
            boolean hasInput = normalized.has("input") || normalized.has("tool_input") || normalized.has("arguments");
            if (!hasInput) {
                String toolName = normalized.get("name").getAsString().trim();
                // If it looks like a known tool or has other tool-like fields, add empty input
                if (isSystemTool(toolName) || isRegisteredTool(toolName) || fuzzyMatchToolName(toolName) != null) {
                    normalized.add("input", new JsonObject());
                } else {
                    return null;
                }
            }
            return normalized;
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * Normalize permissive model outputs to our canonical call shape.
     */
    private JsonObject normalizeToolCall(JsonObject original) {
        JsonObject obj = original.deepCopy();

        // Recognize aliases for "name": toolName, tool_name, id, tool, function
        // Always try to resolve, even if "name" already exists but is empty or a meta-value
        String currentName = obj.has("name") && obj.get("name").isJsonPrimitive()
                ? obj.get("name").getAsString().trim() : "";
        boolean nameIsMissing = currentName.isEmpty() || "tool_name".equalsIgnoreCase(currentName);
        if (nameIsMissing) {
            for (String alias : new String[]{"toolName", "tool_name", "id", "tool", "function"}) {
                if (obj.has(alias) && obj.get(alias).isJsonPrimitive() && !obj.get(alias).getAsString().trim().isEmpty()) {
                    obj.addProperty("name", obj.get(alias).getAsString().trim());
                    break;
                }
            }
        }

        // Handle double-nested structure: {"action":{"toolName":"x","toolInput":{...}}}
        // or {"toolName":"x","toolInput":"..."} where toolInput is string-encoded
        for (String wrapper : new String[]{"action", "request", "call"}) {
            if (obj.has(wrapper) && obj.get(wrapper).isJsonObject()) {
                JsonObject inner = obj.getAsJsonObject(wrapper);
                // Merge inner fields if they look like a tool call
                for (String key : new String[]{"name", "toolName", "tool_name", "id", "input", "toolInput", "tool_input", "arguments", "parameters", "params"}) {
                    if (inner.has(key) && !obj.has(key)) {
                        obj.add(key, inner.get(key));
                    }
                }
                // Re-check name after merge
                if (!obj.has("name") || obj.get("name").isJsonNull() || obj.get("name").getAsString().trim().isEmpty()) {
                    for (String alias : new String[]{"toolName", "tool_name", "id"}) {
                        if (obj.has(alias) && obj.get(alias).isJsonPrimitive() && !obj.get(alias).getAsString().trim().isEmpty()) {
                            obj.addProperty("name", obj.get(alias).getAsString().trim());
                            break;
                        }
                    }
                }
            }
        }

        // Recognize aliases for "input": parameters, params, toolInput, tool_input
        if (!obj.has("input") && !obj.has("arguments")) {
            for (String alias : new String[]{"parameters", "params", "toolInput", "tool_input"}) {
                if (!obj.has(alias) || obj.get(alias).isJsonNull()) {
                    continue;
                }
                if (obj.get(alias).isJsonObject()) {
                    obj.add("input", obj.get(alias).getAsJsonObject());
                    break;
                }
                // Handle string-encoded JSON (e.g. toolInput: "{\"id\":\"n33\"}")
                if (obj.get(alias).isJsonPrimitive()) {
                    try {
                        JsonElement parsed = JsonParser.parseString(obj.get(alias).getAsString());
                        if (parsed.isJsonObject()) {
                            obj.add("input", parsed.getAsJsonObject());
                            break;
                        }
                    } catch (Exception ignore) {
                        // not valid JSON string, skip
                    }
                }
            }
        }

        // Clean up malformed tool names like "browser[name=browser]" → "browser"
        if (obj.has("name") && obj.get("name").isJsonPrimitive()) {
            String rawName = obj.get("name").getAsString();
            int bracketIdx = rawName.indexOf('[');
            if (bracketIdx > 0) {
                rawName = rawName.substring(0, bracketIdx).trim();
                obj.addProperty("name", rawName);
            }
        }

        JsonObject argsObj = null;
        if (obj.has("arguments") && !obj.get("arguments").isJsonNull()) {
            if (obj.get("arguments").isJsonObject()) {
                argsObj = obj.getAsJsonObject("arguments");
            } else if (obj.get("arguments").isJsonPrimitive()) {
                try {
                    JsonElement parsedArgs = JsonParser.parseString(obj.get("arguments").getAsString());
                    if (parsedArgs.isJsonObject()) {
                        argsObj = parsedArgs.getAsJsonObject();
                    }
                } catch (Exception ignore) {
                    // keep raw arguments
                }
            }
        }

        if ((!obj.has("name") || obj.get("name").isJsonNull() || obj.get("name").getAsString().trim().isEmpty()
                || "tool_name".equalsIgnoreCase(obj.get("name").getAsString()))
                && argsObj != null && argsObj.has("name") && !argsObj.get("name").isJsonNull()) {
            obj.addProperty("name", argsObj.get("name").getAsString());
        }

        if (obj.has("input") && !obj.get("input").isJsonNull()) {
            // Handle input as string-encoded JSON
            if (obj.get("input").isJsonPrimitive()) {
                try {
                    JsonElement parsedInput = JsonParser.parseString(obj.get("input").getAsString());
                    if (parsedInput.isJsonObject()) {
                        obj.add("input", parsedInput.getAsJsonObject());
                    }
                } catch (Exception ignore) {
                    // not valid JSON string
                }
            }
            if (obj.get("input").isJsonObject()) {
                JsonObject input = obj.getAsJsonObject("input");
                if (input.has("arguments") && input.get("arguments").isJsonPrimitive()) {
                    try {
                        JsonElement parsedNested = JsonParser.parseString(input.get("arguments").getAsString());
                        if (parsedNested.isJsonObject()) {
                            JsonObject nested = parsedNested.getAsJsonObject();
                            for (java.util.Map.Entry<String, JsonElement> e : nested.entrySet()) {
                                if (!input.has(e.getKey())) {
                                    input.add(e.getKey(), e.getValue());
                                }
                            }
                            input.remove("arguments");
                        }
                    } catch (Exception ignore) {
                        // ignore
                    }
                }
            }
            obj.remove("arguments");
        }

        // Also handle tool_input as string-encoded JSON
        if (!obj.has("input") && obj.has("tool_input") && !obj.get("tool_input").isJsonNull()) {
            if (obj.get("tool_input").isJsonObject()) {
                obj.add("input", obj.getAsJsonObject("tool_input"));
            } else if (obj.get("tool_input").isJsonPrimitive()) {
                try {
                    JsonElement parsedToolInput = JsonParser.parseString(obj.get("tool_input").getAsString());
                    if (parsedToolInput.isJsonObject()) {
                        obj.add("input", parsedToolInput.getAsJsonObject());
                    }
                } catch (Exception ignore) {
                    // not valid JSON string
                }
            }
        }

        if (!obj.has("input") && !obj.has("tool_input") && argsObj != null) {
            if (argsObj.has("input") && argsObj.get("input").isJsonObject()) {
                obj.add("input", argsObj.getAsJsonObject("input"));
            } else {
                JsonObject cleanedArgs = argsObj.deepCopy();
                cleanedArgs.remove("name");
                if (cleanedArgs.size() > 0) {
                    obj.add("input", cleanedArgs);
                }
            }
        }

        return obj;
    }

    private volatile int agentFollowUpRetries = 0;
    private static final int MAX_AGENT_RETRIES = 8;

    private void streamAssistantFollowUp(String followUpUserText) {
        ChatMode mode = (ChatMode) modeComboBox.getSelectedItem();
        ChatMode resolvedMode = mode != null ? mode : ChatMode.ASK;
        String systemPrompt = composeSystemPrompt(resolvedMode);
        String baseFollowUp = (followUpUserText == null || followUpUserText.trim().isEmpty())
                ? "Bitte fahre fort basierend auf dem TOOL_RESULT." : followUpUserText;
        String userText;
        if (resolvedMode == ChatMode.AGENT) {
            userText = baseFollowUp
                    + "\n\nUrsprüngliche Nutzeranfrage: "
                    + (lastUserRequestText == null ? "" : lastUserRequestText);
        } else {
            userText = baseFollowUp
                    + "\n\nUrsprüngliche Nutzeranfrage: "
                    + (lastUserRequestText == null ? "" : lastUserRequestText)
                    + "\nAntworte direkt auf diese Anfrage auf Deutsch, ohne Standard-Floskeln wie I don't see a specific question.";
        }
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
                        if (chunk == null || chunk.isEmpty()) {
                            return;
                        }
                        followUpResponse.append(chunk);
                        SwingUtilities.invokeLater(() -> formatter.appendBotMessageChunk(chunk));
                    }

                    @Override
                    public void onStreamEnd() {
                        SwingUtilities.invokeLater(() -> {
                            String botText = followUpResponse.toString();
                            java.util.List<JsonObject> toolCalls = extractToolCalls(botText);
                            if (!toolCalls.isEmpty()) {
                                agentFollowUpRetries = 0; // Reset retry counter on successful tool call
                                formatter.removeCurrentBotMessage();

                                // Preserve any text the bot wrote before the first tool call JSON
                                String prefixText = extractTextBeforeToolCall(botText);
                                if (prefixText != null && !prefixText.trim().isEmpty()) {
                                    String cleanPrefix = prefixText.trim();
                                    Timestamp prefixId = chatManager.getHistory(sessionId).addBotMessage(cleanPrefix);
                                    formatter.startBotMessage();
                                    formatter.appendBotMessageChunk(cleanPrefix);
                                    formatter.endBotMessage(() -> chatManager.getHistory(sessionId).remove(prefixId));
                                }

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
                                agentFollowUpRetries = 0;
                            } else {
                                // In AGENT mode, if the bot responded with text instead of a tool
                                // call, it might have "thought out loud" instead of acting.
                                // Retry with a more explicit instruction.
                                ChatMode currentMode2 = (ChatMode) modeComboBox.getSelectedItem();
                                if (currentMode2 == ChatMode.AGENT && agentFollowUpRetries < MAX_AGENT_RETRIES) {
                                    agentFollowUpRetries++;
                                    // Save what the bot said as context
                                    Timestamp botId = chatManager.getHistory(sessionId).addBotMessage(botText);
                                    formatter.endBotMessage(() -> chatManager.getHistory(sessionId).remove(botId));

                                    String retryPrompt = "FEHLER: Du hast Text statt eines Tool-Calls geantwortet. " +
                                            "Die Aufgabe des Nutzers war: \"" + lastUserRequestText + "\"\n" +
                                            "Ist die Aufgabe VOLLSTÄNDIG erledigt? " +
                                            "NEIN → Antworte NUR mit einem JSON-Tool-Call. Beispiel:\n" +
                                            "{\"name\":\"web_navigate\",\"input\":{\"url\":\"https://example.com\"}}\n" +
                                            "JA → Fasse die gesammelten Ergebnisse zusammen und antworte dem Nutzer.\n" +
                                            "WICHTIG: Kein Text VOR dem JSON. Kein Erklärtext. NUR das JSON-Objekt.";
                                    streamAssistantFollowUp(retryPrompt);
                                    return;
                                }

                                agentFollowUpRetries = 0;
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

    /**
     * Export the entire chat history as Markdown text.
     */
    public String exportAsMarkdown() {
        de.zrb.bund.api.ChatHistory history = chatManager.getHistory(sessionId);
        if (history == null || history.isEmpty()) {
            return "(Leerer Chat)";
        }

        StringBuilder md = new StringBuilder();
        md.append("# Chat ").append(sessionId.toString().substring(0, 8)).append("\n\n");

        for (de.zrb.bund.api.ChatHistory.Message msg : history.getMessages()) {
            switch (msg.role) {
                case "user":
                    md.append("## 👤 Benutzer\n\n");
                    md.append(msg.content).append("\n\n");
                    break;
                case "assistant":
                    md.append("## 🤖 Bot\n\n");
                    md.append(msg.content).append("\n\n");
                    break;
                case "tool":
                    md.append("<details>\n<summary>🔧 Tool-Ergebnis</summary>\n\n");
                    md.append("```json\n").append(msg.content).append("\n```\n\n");
                    md.append("</details>\n\n");
                    break;
                default:
                    md.append("## ").append(msg.role).append("\n\n");
                    md.append(msg.content).append("\n\n");
                    break;
            }
            md.append("---\n\n");
        }

        return md.toString();
    }

    /**
     * Extract any text the bot wrote before the first embedded tool-call JSON.
     * Returns null if the entire text is a tool call or no prefix text exists.
     */
    private String extractTextBeforeToolCall(String text) {
        if (text == null) return null;
        String trimmed = text.trim();

        // If the whole text is valid JSON tool call, there's no prefix
        if (trimmed.startsWith("{") && tryParseToolCallJson(trimmed) != null) {
            return null;
        }

        // Check for ```json fenced block
        int fenceStart = trimmed.indexOf("```json");
        if (fenceStart > 0) {
            String prefix = trimmed.substring(0, fenceStart).trim();
            return prefix.isEmpty() ? null : prefix;
        }

        // Find the first '{' that starts a valid tool-call JSON
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) == '{') {
                // Try to find the matching closing brace
                int depth = 0;
                for (int j = i; j < trimmed.length(); j++) {
                    if (trimmed.charAt(j) == '{') depth++;
                    else if (trimmed.charAt(j) == '}') {
                        depth--;
                        if (depth == 0) {
                            String candidate = trimmed.substring(i, j + 1);
                            if (tryParseToolCallJson(candidate) != null) {
                                String prefix = trimmed.substring(0, i).trim();
                                return prefix.isEmpty() ? null : prefix;
                            }
                            break;
                        }
                    }
                }
            }
        }

        return null;
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

        // Fallback: If the LLM mixed text with inline tool-call JSON that wasn't
        // parsed above (e.g. because it's embedded after other text on the same
        // brace-nesting level), try a regex-based extraction as last resort.
        if (results.isEmpty()) {
            results = extractToolCallsByRegex(text);
        }

        if (MAX_TOOL_CALLS > 0 && results.size() > MAX_TOOL_CALLS) {
            return results.subList(0, MAX_TOOL_CALLS);
        }

        return results;
    }

    /**
     * Regex-based fallback for extracting tool-call JSON fragments from mixed text.
     * Handles cases where the LLM writes prose and then appends or injects JSON.
     */
    private java.util.List<JsonObject> extractToolCallsByRegex(String text) {
        java.util.List<JsonObject> results = new java.util.ArrayList<>();
        if (text == null) return results;

        // Find all top-level JSON objects in the text
        int i = 0;
        while (i < text.length()) {
            int braceStart = text.indexOf('{', i);
            if (braceStart < 0) break;

            // Quick pre-check: must contain a tool-call indicator within reasonable distance
            int previewEnd = Math.min(text.length(), braceStart + 300);
            String preview = text.substring(braceStart, previewEnd).toLowerCase();
            boolean looksLikeToolCall = preview.contains("\"name\"") || preview.contains("\"toolname\"")
                    || preview.contains("\"tool_name\"") || preview.contains("\"id\"")
                    || preview.contains("\"function\"");

            if (!looksLikeToolCall) {
                i = braceStart + 1;
                continue;
            }

            // Find matching closing brace
            int depth = 0;
            int end = -1;
            for (int j = braceStart; j < text.length(); j++) {
                if (text.charAt(j) == '{') depth++;
                else if (text.charAt(j) == '}') {
                    depth--;
                    if (depth == 0) {
                        end = j;
                        break;
                    }
                }
            }

            if (end > braceStart) {
                String candidate = text.substring(braceStart, end + 1).trim();
                JsonObject obj = tryParseToolCallJson(candidate);
                if (obj != null) {
                    results.add(obj);
                }
                i = end + 1;
            } else {
                i = braceStart + 1;
            }
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
                JsonObject effectiveCall = normalizeToolCall(call);
                String requestedTool = effectiveCall.has("name") && !effectiveCall.get("name").isJsonNull()
                        ? effectiveCall.get("name").getAsString()
                        : null;

                if (requestedTool != null && !isSystemTool(requestedTool) && !schemaKnownTools.contains(requestedTool)) {
                    JsonObject describeCall = new JsonObject();
                    describeCall.addProperty("name", "describe_tool");
                    JsonObject describeInput = new JsonObject();
                    describeInput.addProperty("tool", requestedTool);
                    describeInput.addProperty("detailLevel", "schema");
                    describeCall.add("input", describeInput);

                    JsonObject describeResult = executeSingleToolCall(describeCall);
                    if (isSuccessResult(describeResult)) {
                        schemaKnownTools.add(requestedTool);
                    }

                    results.add(describeResult);
                    JsonObject finalDescribe = describeResult == null ? new JsonObject() : describeResult;
                    SwingUtilities.invokeLater(() -> formatter.appendToolEvent(
                            "Tool: describe_tool",
                            finalDescribe.toString(),
                            false
                    ));
                }

                JsonObject result = executeSingleToolCall(effectiveCall);
                if (shouldRetryToolCall(result)) {
                    if (requestedTool != null && !isSystemTool(requestedTool) && !schemaKnownTools.contains(requestedTool)) {
                        JsonObject describeCall = new JsonObject();
                        describeCall.addProperty("name", "describe_tool");
                        JsonObject describeInput = new JsonObject();
                        describeInput.addProperty("tool", requestedTool);
                        describeInput.addProperty("detailLevel", "schema");
                        describeCall.add("input", describeInput);

                        JsonObject describeResult = executeSingleToolCall(describeCall);
                        if (isSuccessResult(describeResult)) {
                            schemaKnownTools.add(requestedTool);
                        }
                        results.add(describeResult);
                        JsonObject finalDescribe = describeResult == null ? new JsonObject() : describeResult;
                        SwingUtilities.invokeLater(() -> formatter.appendToolEvent(
                                "Tool: describe_tool",
                                finalDescribe.toString(),
                                false
                        ));
                    }

                    JsonObject repaired = repairToolCallForRetry(effectiveCall);
                    result = executeSingleToolCall(repaired);
                }

                if (isSuccessResult(result) && requestedTool != null && !isSystemTool(requestedTool)) {
                    toolsUsedInThisChat.add(requestedTool);
                }

                results.add(result);

                boolean isError = result != null && result.has("status")
                        && !result.get("status").isJsonNull()
                        && "error".equalsIgnoreCase(result.get("status").getAsString());

                String toolName = result != null && result.has("toolName") && !result.get("toolName").isJsonNull()
                        ? result.get("toolName").getAsString()
                        : (effectiveCall.has("name") ? effectiveCall.get("name").getAsString() : "unbekannt");

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
                    followUp = "Du hast Tool-Ergebnisse erhalten. Ursprüngliche Nutzeranfrage: \"" + lastUserRequestText + "\"\n\n" +
                            "Prüfe: Ist die Aufgabe VOLLSTÄNDIG erledigt? " +
                            "Wenn NEIN: Antworte NUR mit dem nächsten Tool-Call als reines JSON – KEIN Text davor oder danach. " +
                            "Frage NICHT den Nutzer. Handle autonom. " +
                            "Beispiele für nächste Schritte: weitere Seiten navigieren, Artikel-Links anklicken, web_read_page aufrufen, Daten sammeln. " +
                            "Wenn JA: fasse die gesammelten Informationen zusammen und antworte dem Nutzer auf Deutsch. " +
                            "WICHTIG: Erfinde KEINE Daten. Nur was du über Tools gelesen hast, darfst du berichten. " +
                            "Falls ein Tool-Result 'blocked' oder 'cancelled' ist, erkläre das kurz und biete eine Alternative an. " +
                            "ERINNERUNG: Du bist ein Agent. Du darfst NICHT stoppen und den Nutzer fragen. " +
                            "Mache einfach weiter bis die Aufgabe erledigt ist.";
                } else {
                    followUp = "Nutze die TOOL_RESULTS oben und antworte dem Nutzer direkt und konkret auf Deutsch.";
                }

                streamAssistantFollowUp(followUp);
            }
        }).start();
    }

    private boolean shouldRetryToolCall(JsonObject result) {
        if (result == null || !result.has("status") || result.get("status").isJsonNull()) {
            return false;
        }
        String status = result.get("status").getAsString();
        if (!"error".equalsIgnoreCase(status)) {
            return false;
        }
        String message = result.has("message") && !result.get("message").isJsonNull()
                ? result.get("message").getAsString()
                : "";
        return message.contains("Pflichtfelder fehlen") || message.toLowerCase().contains("arguments");
    }

    private boolean isSuccessResult(JsonObject result) {
        if (result == null || !result.has("status") || result.get("status").isJsonNull()) {
            return false;
        }
        String status = result.get("status").getAsString();
        return "success".equalsIgnoreCase(status) || "ok".equalsIgnoreCase(status);
    }

    private JsonObject repairToolCallForRetry(JsonObject call) {
        JsonObject repaired = normalizeToolCall(call);
        if (repaired.has("input") && repaired.get("input").isJsonObject()) {
            JsonObject input = repaired.getAsJsonObject("input");
            if (input.has("arguments") && !input.get("arguments").isJsonNull() && input.get("arguments").isJsonPrimitive()) {
                try {
                    JsonElement parsed = JsonParser.parseString(input.get("arguments").getAsString());
                    if (parsed.isJsonObject()) {
                        JsonObject parsedObj = parsed.getAsJsonObject();
                        for (java.util.Map.Entry<String, JsonElement> e : parsedObj.entrySet()) {
                            if (!input.has(e.getKey())) {
                                input.add(e.getKey(), e.getValue());
                            }
                        }
                        input.remove("arguments");
                    }
                } catch (Exception ignore) {
                    // keep original input
                }
            }
        }
        return repaired;
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

            // Fuzzy match: if the exact name isn't found, try to find the best match
            if (!isSystemTool(toolName) && !isRegisteredTool(toolName)) {
                String resolved = fuzzyMatchToolName(toolName);
                if (resolved != null) {
                    toolName = resolved;
                    call.addProperty("name", resolved);
                } else {
                    return createUnknownToolResult(toolName, call);
                }
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

    private boolean isRegisteredTool(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) {
            return false;
        }
        final String targetName = toolName;
        return ToolRegistryImpl.getInstance().getAllTools().stream()
                .anyMatch(t -> targetName.equalsIgnoreCase(t.getSpec().getName()));
    }

    private JsonObject executeSystemTool(String toolName, JsonObject call) {
        if (!"describe_tool".equalsIgnoreCase(toolName)) {
            return createErrorResult(toolName, "Unbekanntes Systemtool", call);
        }

        JsonObject input = call != null && call.has("input") && call.get("input").isJsonObject()
                ? call.getAsJsonObject("input")
                : new JsonObject();

        String target = null;
        if (input.has("tool") && !input.get("tool").isJsonNull()) {
            target = input.get("tool").getAsString();
        } else if (input.has("name") && !input.get("name").isJsonNull()) {
            target = input.get("name").getAsString();
        }

        String detailLevel = input.has("detailLevel") && !input.get("detailLevel").isJsonNull()
                ? input.get("detailLevel").getAsString().trim().toLowerCase()
                : "schema";
        boolean includeExamples = input.has("includeExamples") && !input.get("includeExamples").isJsonNull()
                && input.get("includeExamples").getAsBoolean();

        JsonObject result = new JsonObject();
        result.addProperty("status", "ok");
        result.addProperty("toolName", "describe_tool");

        if (target == null || target.trim().isEmpty()) {
            result.addProperty("status", "error");
            result.addProperty("message", "input.tool oder input.name fehlt");
            return result;
        }

        final String targetName = target;
        de.zrb.bund.newApi.mcp.McpTool tool = ToolRegistryImpl.getInstance().getAllTools().stream()
                .filter(t -> targetName.equalsIgnoreCase(t.getSpec().getName()))
                .findFirst()
                .orElse(null);

        if (tool == null) {
            result.addProperty("status", "error");
            result.addProperty("message", "Tool nicht gefunden: " + target);
            return result;
        }

        String resolvedName = tool.getSpec().getName();
        result.addProperty("targetTool", resolvedName);
        result.addProperty("description", trimDescription(tool.getSpec().getDescription()));
        result.addProperty("canonicalCallFormat", "{\"name\":\"" + resolvedName + "\",\"input\":{...}}");
        result.addProperty("inputShapeRules", "Alle Parameter direkt unter input; kein input.arguments; kein toolName.");

        ToolPolicy policy = toolPolicyRepository.findByToolName(resolvedName);
        ToolAccessType accessType = policy != null && policy.getAccessType() != null
                ? policy.getAccessType()
                : ToolAccessTypeDefaults.resolveDefault(resolvedName);
        result.addProperty("accessType", accessType.name());

        com.google.gson.JsonObject spec = com.google.gson.JsonParser.parseString(tool.getSpec().toJson()).getAsJsonObject();
        com.google.gson.JsonArray capabilities = buildCapabilities(resolvedName, tool.getSpec().getDescription());
        result.add("capabilities", capabilities);

        JsonObject constraints = new JsonObject();
        if ("read_resource".equalsIgnoreCase(resolvedName)) {
            constraints.addProperty("directoryListing", "non_recursive");
        }
        if (constraints.size() > 0) {
            result.add("constraints", constraints);
        }

        boolean wantsSchema = "schema".equals(detailLevel) || "full".equals(detailLevel)
                || (!"summary".equals(detailLevel) && !"examples".equals(detailLevel));
        if ("summary".equals(detailLevel)) {
            wantsSchema = false;
        }
        if (wantsSchema && spec.has("input_schema")) {
            result.add("inputSchema", spec.get("input_schema"));
        }

        boolean wantsExamples = includeExamples || "examples".equals(detailLevel) || "full".equals(detailLevel);
        if (wantsExamples && tool.getSpec().getExampleInput() != null) {
            com.google.gson.JsonArray examples = new com.google.gson.JsonArray();
            JsonObject ex = new JsonObject();
            ex.addProperty("purpose", "Beispielaufruf");
            JsonObject callObj = new JsonObject();
            callObj.addProperty("name", resolvedName);
            callObj.add("input", com.google.gson.JsonParser.parseString(new com.google.gson.Gson().toJson(tool.getSpec().getExampleInput())).getAsJsonObject());
            ex.add("call", callObj);
            examples.add(ex);
            result.add("examples", examples);
        }

        return result;
    }

    private String trimDescription(String description) {
        if (description == null) {
            return "";
        }
        String d = description.trim();
        return d.length() > 250 ? d.substring(0, 250) : d;
    }

    private com.google.gson.JsonArray buildCapabilities(String toolName, String description) {
        com.google.gson.JsonArray capabilities = new com.google.gson.JsonArray();
        String n = toolName == null ? "" : toolName.toLowerCase();
        String d = description == null ? "" : description.toLowerCase();

        if ("read_resource".equals(n) || d.contains("liest") || d.contains("read")) {
            capabilities.add("read_file_content");
        }
        if ("read_resource".equals(n) || d.contains("verzeichnis") || d.contains("directory")) {
            capabilities.add("list_directory");
        }
        if (n.contains("open")) {
            capabilities.add("open_resource");
        }
        return capabilities;
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

    /**
     * Try to match a mistyped tool name to a registered tool.
     * E.g. "browser" → "web_navigate", "browse" → "web_navigate".
     */
    private String fuzzyMatchToolName(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase().trim();

        // Collect all registered tool names
        java.util.List<String> candidates = new java.util.ArrayList<>();
        for (de.zrb.bund.newApi.mcp.McpTool tool : ToolRegistryImpl.getInstance().getAllTools()) {
            candidates.add(tool.getSpec().getName());
        }

        // Exact match (case-insensitive)
        for (String c : candidates) {
            if (c.equalsIgnoreCase(lower)) return c;
        }

        // Prefix match: "browser" matches "browser_eval"
        String bestPrefix = null;
        for (String c : candidates) {
            if (c.toLowerCase().startsWith(lower) || lower.startsWith(c.toLowerCase())) {
                if (bestPrefix == null || c.length() < bestPrefix.length()) {
                    bestPrefix = c;
                }
            }
        }

        // Common aliases
        if (bestPrefix == null) {
            if (lower.contains("browse") || lower.contains("browser") || lower.equals("navigate")) {
                bestPrefix = "web_navigate";
            } else if (lower.contains("snapshot") || lower.equals("page")) {
                bestPrefix = "web_snapshot";
            }
        }

        // Only return if the candidate is actually registered
        if (bestPrefix != null && isRegisteredTool(bestPrefix)) {
            return bestPrefix;
        }
        return null;
    }

    private JsonObject createUnknownToolResult(String toolName, JsonObject call) {
        JsonObject error = createErrorResult(toolName, "Tool nicht gefunden: " + toolName, call);
        error.addProperty("errorType", "ToolNotFound");

        com.google.gson.JsonArray availableTools = new com.google.gson.JsonArray();
        ChatMode mode = (ChatMode) modeComboBox.getSelectedItem();
        ChatMode resolvedMode = mode != null ? mode : ChatMode.ASK;
        for (ToolPolicy policy : toolPolicyRepository.loadAll()) {
            if (policy == null || !policy.isEnabled() || policy.getToolName() == null || policy.getToolName().trim().isEmpty()) {
                continue;
            }

            ToolAccessType accessType = policy.getAccessType() != null
                    ? policy.getAccessType()
                    : ToolAccessTypeDefaults.resolveDefault(policy.getToolName());
            if (resolvedMode.isToolAware() && !resolvedMode.getAllowedToolAccess().contains(accessType)) {
                continue;
            }

            if (isRegisteredTool(policy.getToolName())) {
                availableTools.add(policy.getToolName());
            }
        }
        availableTools.add("describe_tool");
        error.add("availableTools", availableTools);
        error.addProperty("hint",
                "Nutze nur Tools aus availableTools. Wenn unsicher, rufe describe_tool mit detailLevel='schema' auf."
        );
        return error;
    }
}
