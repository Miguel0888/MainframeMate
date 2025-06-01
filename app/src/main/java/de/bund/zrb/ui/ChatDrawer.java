
package de.bund.zrb.ui;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.runtime.ToolRegistryImpl;
import de.bund.zrb.ui.components.UiMessage;
import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.ChatStreamListener;
import de.zrb.bund.newApi.mcp.McpTool;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class ChatDrawer extends JPanel {

    private final ChatManager chatManager;
    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final JButton newTabButton = new JButton("+");
    private JCheckBox keepAliveCheckbox;
    private JCheckBox contextMemoryCheckbox;

    public ChatDrawer(ChatManager chatManager) {
        this.chatManager = chatManager;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(createHeader(), BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);

        newTab(); // Initialer Tab
    }

    private JPanel createHeader() {
        JLabel titleLabel = new JLabel("💬 Chat");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));

        Settings settings = SettingsHelper.load();
        Map<String, String> state = settings.applicationState;

        keepAliveCheckbox = new JCheckBox("Modell behalten", Boolean.parseBoolean(state.getOrDefault("chat.keepAlive", "true")));
        contextMemoryCheckbox = new JCheckBox("Kontext merken", Boolean.parseBoolean(state.getOrDefault("chat.rememberContext", "true")));

        for (JCheckBox box : new JCheckBox[]{keepAliveCheckbox, contextMemoryCheckbox}) {
            box.setFont(new Font("Dialog", Font.PLAIN, 11));
            box.setHorizontalTextPosition(SwingConstants.LEFT);
            box.setMargin(new Insets(0, 0, 0, 0));
            box.setFocusable(false);
        }

        newTabButton.setMargin(new Insets(1, 6, 1, 6));
        newTabButton.setFocusable(false);
        newTabButton.addActionListener(e -> newTab());

        JPanel checkboxPanel = new JPanel();
        checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.X_AXIS));
        checkboxPanel.setOpaque(false);
        checkboxPanel.add(contextMemoryCheckbox);
        checkboxPanel.add(Box.createHorizontalStrut(8));
        checkboxPanel.add(keepAliveCheckbox);
        checkboxPanel.add(Box.createHorizontalStrut(12));
        checkboxPanel.add(newTabButton);

        JPanel headerLine = new JPanel(new BorderLayout());
        headerLine.add(titleLabel, BorderLayout.WEST);
        headerLine.add(checkboxPanel, BorderLayout.EAST);
        headerLine.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        return headerLine;
    }

    private void newTab() {
        ChatTab tab = new ChatTab();
        String title = tab.sessionId.toString().substring(0, 8);

        tabbedPane.addTab(title, tab);
        int index = tabbedPane.indexOfComponent(tab);

        JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabHeader.setOpaque(false);

        JLabel tabLabel = new JLabel(title + " ");
        JButton closeButton = new JButton("x");
        closeButton.setMargin(new Insets(0, 2, 0, 2));
        closeButton.setFocusable(false);
        closeButton.setFont(closeButton.getFont().deriveFont(Font.PLAIN, 11));
        closeButton.addActionListener((ActionEvent e) -> {
            chatManager.closeSession(tab.sessionId);
            tabbedPane.remove(tab);
        });

        tabHeader.add(tabLabel);
        tabHeader.add(closeButton);
        tabbedPane.setTabComponentAt(index, tabHeader);
        tabbedPane.setSelectedComponent(tab);
    }

    public void addApplicationState(Map<String, String> state) {
        if (state == null) return;

        state.put("chat.keepAlive", String.valueOf(keepAliveCheckbox.isSelected()));
        state.put("chat.rememberContext", String.valueOf(contextMemoryCheckbox.isSelected()));
    }

    class ChatTab extends JPanel {
        private final UUID sessionId = UUID.randomUUID();
        private final JPanel messageContainer = new JPanel();
        private final JTextArea inputArea = new JTextArea(3, 30);
        private final JComboBox<String> toolComboBox = new JComboBox<>();
        private final JButton sendButton = new JButton("⏎");
        private final JLabel statusLabel = new JLabel(" ");
        private final JButton cancelButton = new JButton("⛔");
        private final StringBuilder botBuffer = new StringBuilder();
        private boolean awaitingResponse = false;

        public ChatTab() {
            setLayout(new BorderLayout(4, 4));

            // Nachrichtenbereich
            messageContainer.setLayout(new BoxLayout(messageContainer, BoxLayout.Y_AXIS));
            messageContainer.setOpaque(false);
            JScrollPane scrollPane = new JScrollPane(messageContainer);
            scrollPane.setBorder(null);
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);
            add(scrollPane, BorderLayout.CENTER);

            // Eingabe
            add(createInputPanel(), BorderLayout.SOUTH);
        }

        private JPanel createInputPanel() {
            Settings settings = SettingsHelper.load();
            String fontName = settings.aiConfig.getOrDefault("editor.font", "Monospaced");
            int fontSize = Integer.parseInt(settings.aiConfig.getOrDefault("editor.fontSize", "12"));
            inputArea.setFont(new Font(fontName, Font.PLAIN, fontSize));
            inputArea.setLineWrap(true);
            inputArea.setWrapStyleWord(true);
            inputArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            inputArea.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                        e.consume();
                        sendMessage();
                    }
                }
            });

            JScrollPane inputScroll = new JScrollPane(inputArea);
            inputScroll.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

            ToolRegistryImpl.getInstance().getAllTools().forEach(tool ->
                    toolComboBox.addItem(tool.getSpec().getName()));
            toolComboBox.insertItemAt("", 0);
            toolComboBox.setSelectedIndex(0);
            toolComboBox.setPreferredSize(new Dimension(150, 24));
            toolComboBox.setFocusable(false);

            sendButton.setToolTipText("Nachricht senden");
            sendButton.addActionListener(e -> sendMessage());

            cancelButton.setVisible(false);
            cancelButton.setMargin(new Insets(0, 4, 0, 4));
            cancelButton.setFocusable(false);
            cancelButton.addActionListener(e -> {
                chatManager.cancel(sessionId);
                cancelButton.setVisible(false);
            });

            JPanel inputLine = new JPanel(new BorderLayout(4, 0));
            inputLine.add(toolComboBox, BorderLayout.WEST);
            inputLine.add(sendButton, BorderLayout.EAST);

            JPanel panel = new JPanel(new BorderLayout(4, 4));
            panel.add(statusLabel, BorderLayout.NORTH);
            panel.add(inputScroll, BorderLayout.CENTER);
            panel.add(inputLine, BorderLayout.SOUTH);
            return panel;
        }

        private void sendMessage() {
            String message = inputArea.getText().trim();
            if (message.isEmpty()) return;
            inputArea.setText("");

            String enriched = applyTool(message);
            addMessage(enriched, true);

            awaitingResponse = true;
            botBuffer.setLength(0);
            statusLabel.setText("🤖 Bot schreibt...");
            cancelButton.setVisible(true);

            new Thread(() -> {
                try {
                    chatManager.streamAnswer(sessionId, true, enriched, new ChatStreamListener() {
                        @Override
                        public void onStreamStart() {}

                        @Override
                        public void onStreamChunk(String chunk) {
                            botBuffer.append(chunk);
                        }

                        @Override
                        public void onStreamEnd() {
                            SwingUtilities.invokeLater(() -> {
                                addMessage(botBuffer.toString(), false);
                                statusLabel.setText(" ");
                                cancelButton.setVisible(false);
                                awaitingResponse = false;
                            });
                        }

                        @Override
                        public void onError(Exception e) {
                            SwingUtilities.invokeLater(() -> {
                                statusLabel.setText("⚠️ Fehler: " + e.getMessage());
                                cancelButton.setVisible(false);
                            });
                        }
                    }, true);
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> statusLabel.setText("⚠️ Fehler: " + e.getMessage()));
                }
            }).start();
        }

        private void addMessage(String content, boolean fromUser) {
            UiMessage msg = new UiMessage(content, fromUser);
            messageContainer.add(msg);
            messageContainer.revalidate();
            SwingUtilities.invokeLater(() -> {
                JScrollBar vbar = ((JScrollPane) messageContainer.getParent().getParent()).getVerticalScrollBar();
                vbar.setValue(vbar.getMaximum());
            });
        }

        private String applyTool(String input) {
            String selectedTool = (String) toolComboBox.getSelectedItem();
            if (selectedTool == null || selectedTool.trim().isEmpty()) return input;

            McpTool tool = ToolRegistryImpl.getInstance().getAllTools().stream()
                    .filter(t -> t.getSpec().getName().equals(selectedTool))
                    .findFirst().orElse(null);
            if (tool == null) return input;

            Settings settings = SettingsHelper.load();
            String prefix = settings.aiConfig.getOrDefault("toolPrefix", "");
            String postfix = settings.aiConfig.getOrDefault("toolPostfix", "");
            return String.format("%s\n%s\n%s\n\n%s", prefix, tool.getSpec().toJson(), postfix, input);
        }
    }
}
