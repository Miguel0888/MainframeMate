//package de.bund.zrb.ui.components;
//
//import de.bund.zrb.helper.SettingsHelper;
//import de.bund.zrb.model.Settings;
//import de.bund.zrb.runtime.ToolRegistryImpl;
//import de.zrb.bund.api.ChatManager;
//import de.zrb.bund.api.ChatStreamListener;
//import de.zrb.bund.newApi.mcp.McpTool;
//
//import javax.swing.*;
//import java.awt.*;
//import java.awt.event.KeyAdapter;
//import java.awt.event.KeyEvent;
//import java.io.IOException;
//import java.util.UUID;
//
//public class ChatSessionPanel extends JPanel {
//
//    private final UUID sessionId = UUID.randomUUID();
//    private final ChatManager chatManager;
//    private final JPanel messagePanel;
//    private final JTextArea inputArea;
//    private final JScrollPane scrollPane;
//    private final JComboBox<String> toolComboBox;
//    private final JButton sendButton;
//
//    private UiMessage currentBotMessage;
//
//    public ChatSessionPanel(ChatManager chatManager, de.bund.zrb.ui.ChatDrawer drawer) {
//        this.chatManager = chatManager;
//        setLayout(new BorderLayout(8, 8));
//
//        // Chat area
//        messagePanel = new JPanel();
//        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
//        messagePanel.setBackground(Color.WHITE);
//
//        scrollPane = new JScrollPane(messagePanel);
//        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
//        scrollPane.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
//        add(scrollPane, BorderLayout.CENTER);
//
//        // Input area
//        JPanel inputPanel = new JPanel(new BorderLayout(4, 4));
//        Settings settings = SettingsHelper.load();
//        int rows = Integer.parseInt(settings.aiConfig.getOrDefault("editor.lines", "3"));
//        String fontName = settings.aiConfig.getOrDefault("editor.font", "Monospaced");
//        int fontSize = Integer.parseInt(settings.aiConfig.getOrDefault("editor.fontSize", "12"));
//
//        inputArea = new JTextArea(rows, 30);
//        inputArea.setFont(new Font(fontName, Font.PLAIN, fontSize));
//        inputArea.setLineWrap(true);
//        inputArea.setWrapStyleWord(true);
//        inputArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
//        JScrollPane inputScroll = new JScrollPane(inputArea);
//        inputScroll.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
//
//        inputArea.addKeyListener(new KeyAdapter() {
//            @Override
//            public void keyPressed(KeyEvent e) {
//                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
//                    e.consume();
//                    sendMessage(drawer);
//                }
//            }
//        });
//
//        sendButton = new JButton("⏎");
//        sendButton.addActionListener(e -> sendMessage(drawer));
//
//        toolComboBox = new JComboBox<>();
//        toolComboBox.addItem("");
//        ToolRegistryImpl.getInstance().getAllTools().forEach(tool ->
//                toolComboBox.addItem(tool.getSpec().getName())
//        );
//
//        JPanel comboPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
//        comboPanel.add(toolComboBox);
//
//        JPanel lower = new JPanel(new BorderLayout(4, 4));
//        lower.add(comboPanel, BorderLayout.WEST);
//        lower.add(sendButton, BorderLayout.EAST);
//
//        inputPanel.add(inputScroll, BorderLayout.CENTER);
//        inputPanel.add(lower, BorderLayout.SOUTH);
//        add(inputPanel, BorderLayout.SOUTH);
//    }
//
//    public UUID getSessionId() {
//        return sessionId;
//    }
//
//    private void sendMessage(de.bund.zrb.ui.ChatDrawer drawer) {
//        String userText = inputArea.getText().trim();
//        if (userText.isEmpty()) return;
//
//        inputArea.setText("");
//
//        // Tool Prompt
//        String message = applyTool(userText);
//
//        // User Message anzeigen
//        appendMessage(message, true);
//
//        currentBotMessage = appendMessage("", false); // Initial leere Bot-Nachricht
//
//        new Thread(() -> {
//            try {
//                chatManager.streamAnswer(sessionId,
//                        drawer.isContextMemoryEnabled(),
//                        message,
//                        new ChatStreamListener() {
//                            @Override
//                            public void onStreamStart() {}
//
//                            @Override
//                            public void onStreamChunk(String chunk) {
//                                SwingUtilities.invokeLater(() -> {
//                                    if (currentBotMessage != null) {
//                                        currentBotMessage.appendText(chunk);
//                                        scrollToBottom();
//                                    }
//                                });
//                            }
//
//                            @Override
//                            public void onStreamEnd() {
//                                currentBotMessage = null;
//                            }
//
//                            @Override
//                            public void onError(Exception e) {
//                                SwingUtilities.invokeLater(() ->
//                                        JOptionPane.showMessageDialog(ChatSessionPanel.this,
//                                                "Fehler: " + e.getMessage(),
//                                                "Chat-Fehler",
//                                                JOptionPane.ERROR_MESSAGE));
//                            }
//                        },
//                        drawer.isKeepAliveEnabled()
//                );
//            } catch (IOException e) {
//                SwingUtilities.invokeLater(() ->
//                        JOptionPane.showMessageDialog(this,
//                                "Sendeproblem: " + e.getMessage(),
//                                "Chat-Fehler",
//                                JOptionPane.ERROR_MESSAGE));
//            }
//        }).start();
//    }
//
//    private UiMessage appendMessage(String text, boolean fromUser) {
//        UiMessage msg = new UiMessage(text, fromUser);
//        msg.setAlignmentX(Component.LEFT_ALIGNMENT);
//        messagePanel.add(msg);
//        messagePanel.add(Box.createVerticalStrut(4));
//        messagePanel.revalidate();
//        messagePanel.repaint();
//        scrollToBottom();
//        return msg;
//    }
//
//    private void scrollToBottom() {
//        SwingUtilities.invokeLater(() -> {
//            JScrollBar vertical = scrollPane.getVerticalScrollBar();
//            vertical.setValue(vertical.getMaximum());
//        });
//    }
//
//    private String applyTool(String userInput) {
//        String selected = (String) toolComboBox.getSelectedItem();
//        if (selected == null || selected.trim().isEmpty()) return userInput;
//
//        McpTool tool = ToolRegistryImpl.getInstance().getAllTools().stream()
//                .filter(t -> t.getSpec().getName().equals(selected))
//                .findFirst().orElse(null);
//
//        if (tool == null) return userInput;
//
//        Settings settings = SettingsHelper.load();
//        String prefix = settings.aiConfig.getOrDefault("toolPrefix", "");
//        String postfix = settings.aiConfig.getOrDefault("toolPostfix", "");
//
//        return String.format("%s\n%s\n%s\n\n%s", prefix, tool.getSpec().toJson(), postfix, userInput);
//    }
//}
