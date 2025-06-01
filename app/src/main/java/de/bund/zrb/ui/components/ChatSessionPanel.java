package de.bund.zrb.ui.components;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.runtime.ToolRegistryImpl;
import de.bund.zrb.ui.ChatDrawer;
import de.bund.zrb.ui.components.UiMessage;
import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.ChatStreamListener;
import de.zrb.bund.newApi.mcp.McpTool;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.UUID;

public class ChatSessionPanel extends JPanel {

    private final UUID sessionId;
    private final ChatManager chatManager;
    private final ChatDrawer parentDrawer;

    private final JPanel messageContainer;
    private final JTextArea inputArea;
    private final JComboBox<String> toolComboBox;
    private final JButton cancelButton;
    private UiMessage currentBotMessage;

    private final StringBuilder botBuffer = new StringBuilder();
    private boolean awaitingBotResponse = false;

    public ChatSessionPanel(ChatManager chatManager, ChatDrawer parentDrawer) {
        this.chatManager = chatManager;
        this.parentDrawer = parentDrawer;
        this.sessionId = UUID.randomUUID();

        setLayout(new BorderLayout(8, 8));

        messageContainer = new JPanel();
        messageContainer.setLayout(new BoxLayout(messageContainer, BoxLayout.Y_AXIS));
        JScrollPane chatScroll = new JScrollPane(messageContainer);
        chatScroll.setBorder(null);
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(chatScroll, BorderLayout.CENTER);

        inputArea = new JTextArea(3, 30);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        inputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
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

        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

        JButton sendButton = new JButton("⏎");
        sendButton.addActionListener(e -> sendMessage());

        JButton attachButton = new JButton("+");

        toolComboBox = new JComboBox<>();
        toolComboBox.addItem("");
        ToolRegistryImpl.getInstance().getAllTools().forEach(tool ->
                toolComboBox.addItem(tool.getSpec().getName())
        );

        cancelButton = new JButton("⛔");
        cancelButton.setVisible(false);
        cancelButton.addActionListener(e -> {
            chatManager.cancel(sessionId);
            cancelButton.setVisible(false);
        });

        JPanel buttonPanel = new JPanel(new BorderLayout(4, 0));
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftButtons.add(attachButton);
        leftButtons.add(toolComboBox);

        buttonPanel.add(leftButtons, BorderLayout.WEST);
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightButtons.add(cancelButton);
        rightButtons.add(sendButton);
        buttonPanel.add(rightButtons, BorderLayout.EAST);

        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.add(inputScroll, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void sendMessage() {
        String message = inputArea.getText().trim();
        if (message.isEmpty()) return;
        message = applyTool(message);
        inputArea.setText("");
        addMessage(message, true);

        botBuffer.setLength(0);
        awaitingBotResponse = true;
        currentBotMessage = new UiMessage("", false);
        messageContainer.add(currentBotMessage);
        revalidate();
        scrollToBottom();

        String finalMessage = message;
        new Thread(() -> {
            try {
                boolean success = chatManager.streamAnswer(
                        sessionId,
                        parentDrawer.isContextMemoryEnabled(),
                        finalMessage,
                        new ChatStreamListener() {
                            @Override
                            public void onStreamStart() {
                                SwingUtilities.invokeLater(() -> cancelButton.setVisible(true));
                            }

                            @Override
                            public void onStreamChunk(String chunk) {
                                botBuffer.append(chunk);
                                SwingUtilities.invokeLater(() -> {
                                    currentBotMessage.setText(botBuffer.toString());
                                    scrollToBottom();
                                });
                            }

                            @Override
                            public void onStreamEnd() {
                                awaitingBotResponse = false;
                                SwingUtilities.invokeLater(() -> {
                                    cancelButton.setVisible(false);
                                    currentBotMessage.setText(botBuffer.toString());
                                });
                            }

                            @Override
                            public void onError(Exception e) {
                                awaitingBotResponse = false;
                                SwingUtilities.invokeLater(() -> {
                                    cancelButton.setVisible(false);
                                    JOptionPane.showMessageDialog(ChatSessionPanel.this,
                                            "Fehler beim Abrufen der AI-Antwort:\n" + e.getMessage(),
                                            "AI-Fehler", JOptionPane.ERROR_MESSAGE);
                                });
                            }
                        },
                        parentDrawer.isKeepAliveEnabled()
                );

                if (!success) awaitingBotResponse = false;

            } catch (IOException e) {
                awaitingBotResponse = false;
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Fehler beim Starten der Anfrage:\n" + e.getMessage(),
                        "AI-Fehler", JOptionPane.ERROR_MESSAGE));
            }
        }).start();
    }

    private String applyTool(String userInput) {
        String selectedToolName = (String) toolComboBox.getSelectedItem();
        if (selectedToolName == null || selectedToolName.trim().isEmpty()) {
            return userInput;
        }

        McpTool tool = ToolRegistryImpl.getInstance().getAllTools().stream()
                .filter(t -> selectedToolName.equals(t.getSpec().getName()))
                .findFirst().orElse(null);

        if (tool == null) return userInput;

        Settings settings = SettingsHelper.load();
        String prefix = settings.aiConfig.getOrDefault("toolPrefix", "");
        String postfix = settings.aiConfig.getOrDefault("toolPostfix", "");
        String toolJson = tool.getSpec().toJson();

        return String.format("%s\n%s\n%s\n\n%s", prefix, toolJson, postfix, userInput);
    }

    private void addMessage(String message, boolean fromUser) {
        UiMessage uiMessage = new UiMessage(message, fromUser);
        messageContainer.add(uiMessage);
        revalidate();
        scrollToBottom();
    }

    private void scrollToBottom() {
        JScrollBar vertical = ((JScrollPane) messageContainer.getParent().getParent()).getVerticalScrollBar();
        vertical.setValue(vertical.getMaximum());
    }

    public UUID getSessionId() {
        return sessionId;
    }
}
