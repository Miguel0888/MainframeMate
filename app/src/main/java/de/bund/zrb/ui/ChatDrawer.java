package de.bund.zrb.ui;

import de.bund.zrb.ui.chat.ChatFormatter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

public class ChatDrawer extends JPanel {

    private final JTextPane chatPane;
    private JTextArea inputArea;
    private JButton sendButton;
    private JButton attachButton;
    private JLabel statusLabel;
    private JCheckBox keepAliveCheckbox;
    private JCheckBox contextMemoryCheckbox;
    private final ChatFormatter formatter;
    private boolean awaitingBotResponse = false;

    public ChatDrawer(Consumer<String> onSend) {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Header (Titel + Checkboxen)
        JPanel headerPanel = createHeader();
        add(headerPanel, BorderLayout.NORTH);

        // Chat-Ausgabe
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        formatter = new ChatFormatter(chatPane);
        JScrollPane chatScroll = new JScrollPane(chatPane);
        add(chatScroll, BorderLayout.CENTER);

        // Eingabebereich
        JPanel inputPanel = createInputPanel(onSend);
        add(inputPanel, BorderLayout.SOUTH);
    }

    private JPanel createHeader() {
        JLabel titleLabel = new JLabel("💬 Chat");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));

        keepAliveCheckbox = new JCheckBox("Modell behalten", true);
        contextMemoryCheckbox = new JCheckBox("Kontext merken", true);
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
        headerLine.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(0, 0, 4, 0)
        ));

        return headerLine;
    }

    private JPanel createInputPanel(Consumer<String> onSend) {
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
                        sendMessage(onSend);
                    }
                }
            }
        });

        sendButton = new JButton("⏎");
        sendButton.setToolTipText("Nachricht senden");
        sendButton.addActionListener(e -> sendMessage(onSend));

        attachButton = new JButton("+");
        attachButton.setToolTipText("Aktiven Tab teilen");

        sendButton.setMargin(new Insets(2, 8, 2, 8));
        attachButton.setMargin(new Insets(2, 6, 2, 6));

        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.add(attachButton, BorderLayout.WEST);
        buttonPanel.add(sendButton, BorderLayout.EAST);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.add(statusLabel, BorderLayout.NORTH);
        bottomPanel.add(inputScroll, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        return bottomPanel;
    }

    private void sendMessage(Consumer<String> onSend) {
        String message = inputArea.getText().trim();
        if (!message.isEmpty()) {
            formatter.appendUserMessage(message);
            inputArea.setText("");
            awaitingBotResponse = true;
            onSend.accept(message);
        }
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
        setStatus(" ");
        awaitingBotResponse = false;
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

}
