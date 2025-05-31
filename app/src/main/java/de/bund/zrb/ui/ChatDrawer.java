package de.bund.zrb.ui;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.chat.ChatFormatter;
import de.bund.zrb.util.SettingsManager;
import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.ChatStreamListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public class ChatDrawer extends JPanel {

    private final JTextPane chatPane;
    private JTextArea inputArea;
    private JButton sendButton;
    private JButton attachButton;
    private JLabel statusLabel;
    private JCheckBox keepAliveCheckbox;
    private JCheckBox contextMemoryCheckbox;
    private final ChatFormatter formatter;
    private final ChatManager chatManager;
    private final UUID sessionId;
    private JButton cancelButton;
    private boolean awaitingBotResponse = false;

    public ChatDrawer(ChatManager chatManager) {
        this.chatManager = chatManager;
        this.sessionId = UUID.randomUUID();

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(createHeader(), BorderLayout.NORTH);

        chatPane = new JTextPane();
        chatPane.setEditable(false);
        formatter = new ChatFormatter(chatPane);
        JScrollPane chatScroll = new JScrollPane(chatPane);
        add(chatScroll, BorderLayout.CENTER);

        add(createInputPanel(), BorderLayout.SOUTH);
    }

    private JPanel createHeader() {
        JLabel titleLabel = new JLabel("💬 Chat");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));

        Settings settings = SettingsManager.load();
        Map<String, String> state = settings.applicationState;

        boolean keepAlive = Boolean.parseBoolean(state.getOrDefault("chat.keepAlive", "true"));
        boolean rememberContext = Boolean.parseBoolean(state.getOrDefault("chat.rememberContext", "true"));

        keepAliveCheckbox = new JCheckBox("Modell behalten", keepAlive);
        contextMemoryCheckbox = new JCheckBox("Kontext merken", rememberContext);
        for (JCheckBox box : new JCheckBox[]{keepAliveCheckbox, contextMemoryCheckbox}) {
            box.setFont(new Font("Dialog", Font.PLAIN, 11));
            box.setHorizontalTextPosition(SwingConstants.LEFT);
            box.setMargin(new Insets(0, 0, 0, 0));
            box.setFocusable(false);
        }

        JPanel checkboxPanel = new JPanel();
        checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.X_AXIS));
        checkboxPanel.setOpaque(false);
        checkboxPanel.add(contextMemoryCheckbox);
        checkboxPanel.add(Box.createHorizontalStrut(8));
        checkboxPanel.add(keepAliveCheckbox);

        JPanel headerLine = new JPanel(new BorderLayout());
        headerLine.add(titleLabel, BorderLayout.WEST);
        headerLine.add(checkboxPanel, BorderLayout.EAST);
        headerLine.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        return headerLine;
    }

    private JPanel createInputPanel() {
        inputArea = new JTextArea(3, 30);
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

        sendButton = new JButton("⏎");
        sendButton.setToolTipText("Nachricht senden");
        sendButton.addActionListener(e -> sendMessage());

        attachButton = new JButton("+");
        attachButton.setToolTipText("Aktiven Tab teilen");

        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.add(attachButton, BorderLayout.WEST);
        buttonPanel.add(sendButton, BorderLayout.EAST);

        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.add(createStatusPanel(), BorderLayout.NORTH);
        bottomPanel.add(inputScroll, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        return bottomPanel;
    }

    private JPanel createStatusPanel() {
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
        return statusPanel;
    }

    private void sendMessage() {
        String message = inputArea.getText().trim();
        if (message.isEmpty()) return;

        awaitingBotResponse = true;

        new Thread(() -> {
            try {
                boolean success = chatManager.streamAnswer(sessionId, isContextMemoryEnabled() , message, new ChatStreamListener() {
                    @Override
                    public void onStreamStart() {
                        SwingUtilities.invokeLater(() -> {
                            formatter.appendUserMessage(message);
                            inputArea.setText("");
                            startBotMessage();
                            cancelButton.setVisible(true);
                        });
                    }

                    @Override
                    public void onStreamChunk(String chunk) {
                        SwingUtilities.invokeLater(() -> appendBotMessageChunk(chunk));
                    }

                    @Override
                    public void onStreamEnd() {
                        SwingUtilities.invokeLater(() -> {
                            endBotMessage();
                            setStatus(" ");
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        SwingUtilities.invokeLater(() -> {
                            setStatus("⚠️ Fehler");
                            cancelButton.setVisible(false);
                            JOptionPane.showMessageDialog(ChatDrawer.this,
                                    "Fehler beim Abrufen der AI-Antwort:\n" + e.getMessage(),
                                    "AI-Fehler", JOptionPane.ERROR_MESSAGE);
                        });
                    }
                }, isKeepAliveEnabled());
                if(!success) {
                    // Anfrage wurde unterdrückt – nicht anzeigen
                    awaitingBotResponse = false;
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    setStatus("⚠️ Fehler");
                    JOptionPane.showMessageDialog(this,
                            "Fehler beim Starten der Anfrage:\n" + e.getMessage(),
                            "AI-Fehler", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    public void appendBotMessageChunk(String chunk) {
        if (awaitingBotResponse) {
            formatter.appendBotMessageChunk(chunk);
        }
    }

    public void setUserInput(String text) {
        inputArea.setText(text);
    }

    public void onAttachClick(Runnable handler) {
        attachButton.addActionListener(e -> handler.run());
    }

    public void startBotMessage() {
        formatter.startBotMessage();
        setStatus("🤖 Bot schreibt...");
    }

    public void endBotMessage() {
        formatter.endBotMessage();
        awaitingBotResponse = false;
        cancelButton.setVisible(false);
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    public boolean isKeepAliveEnabled() {
        return keepAliveCheckbox.isSelected();
    }

    public boolean isContextMemoryEnabled() {
        return contextMemoryCheckbox.isSelected();
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void addApplicationState(Map<String, String> state) {
        if (state == null) return;

        state.put("chat.keepAlive", String.valueOf(keepAliveCheckbox.isSelected()));
        state.put("chat.rememberContext", String.valueOf(contextMemoryCheckbox.isSelected()));
    }

}
