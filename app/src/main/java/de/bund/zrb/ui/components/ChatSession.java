package de.bund.zrb.ui.components;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.runtime.ToolRegistryImpl;
import de.bund.zrb.ui.chat.ChatFormatter;
import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.ChatStreamListener;
import de.zrb.bund.newApi.mcp.McpTool;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.UUID;

public class ChatSession extends JPanel {

    private final UUID sessionId;
    private final ChatManager chatManager;

    private final ChatFormatter formatter;
    private final JTextArea inputArea;
    private final JComboBox<String> toolComboBox;
    private final JLabel statusLabel;
    private final JButton cancelButton;
    private boolean awaitingBotResponse = false;

    private final JCheckBox keepAliveCheckbox;
    private final JCheckBox contextMemoryCheckbox;

    public ChatSession(ChatManager chatManager, JCheckBox keepAliveCheckbox, JCheckBox contextMemoryCheckbox) {
        this.chatManager = chatManager;
        this.keepAliveCheckbox = keepAliveCheckbox;
        this.contextMemoryCheckbox = contextMemoryCheckbox;
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
        String fontName = settings.aiConfig.getOrDefault("editor.font", "Monospaced");
        int fontSize = Integer.parseInt(settings.aiConfig.getOrDefault("editor.fontSize", "12"));

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
    }

    private void sendMessage() {
        String message = inputArea.getText().trim();
        if (message.isEmpty()) return;

        message = applyTool(message);
        awaitingBotResponse = true;

        String finalMessage = message;
        new Thread(() -> {
            try {
                boolean success = chatManager.streamAnswer(sessionId, contextMemoryCheckbox.isSelected(), finalMessage, new ChatStreamListener() {
                    @Override
                    public void onStreamStart() {
                        SwingUtilities.invokeLater(() -> {
                            formatter.appendUserMessage(finalMessage);
                            inputArea.setText("");
                            startBotMessage();
                            cancelButton.setVisible(true);
                        });
                    }

                    @Override
                    public void onStreamChunk(String chunk) {
                        SwingUtilities.invokeLater(() -> formatter.appendBotMessageChunk(chunk));
                    }

                    @Override
                    public void onStreamEnd() {
                        SwingUtilities.invokeLater(() -> {
                            formatter.endBotMessage();
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
        String toolJson = tool.getSpec().toJson();

        return String.format("%s\n%s\n%s\n\n%s",
                prefix, toolJson, postfix, userInput);
    }

    private void startBotMessage() {
        formatter.startBotMessage();
        setStatus("🤖 Bot schreibt...");
    }

    private void setStatus(String status) {
        statusLabel.setText(status);
    }

    public UUID getSessionId() {
        return sessionId;
    }
}
