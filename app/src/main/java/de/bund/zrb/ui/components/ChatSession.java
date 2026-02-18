package de.bund.zrb.ui.components;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.runtime.ToolRegistryImpl;
import de.bund.zrb.ui.util.ChatFormatter;
import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.ChatStreamListener;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.mcp.McpTool;

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
                formatter.appendToolEvent(header, body);

                // If context memory is enabled, feed tool result back into the session history
                // so the next LLM call can take it into account.
                if (contextMemoryCheckbox != null && contextMemoryCheckbox.isSelected()
                        && event.getType() == de.zrb.bund.newApi.ChatEvent.Type.TOOL_RESULT) {
                    String msg = "TOOL_RESULT " + (event.getToolName() == null ? "" : event.getToolName())
                            + "\n```json\n" + body + "\n```";
                    chatManager.getHistory(sessionId).addBotMessage(msg);
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

                            Timestamp botId = chatManager.getHistory(sessionId).addBotMessage(currentBotResponse.toString());
                            formatter.endBotMessage(() -> {
                                chatManager.getHistory(sessionId).remove(botId);
                            });
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
}
