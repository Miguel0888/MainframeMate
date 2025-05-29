package de.bund.zrb.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.function.Consumer;

public class ChatDrawer extends JPanel {

    private final JTextArea chatArea;
    private final JTextField inputField;
    private final JButton sendButton;

    public ChatDrawer(Consumer<String> onSend) {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(300, 0));
        setBorder(BorderFactory.createTitledBorder("💬 Chat"));

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);

        inputField = new JTextField();
        sendButton = new JButton("Senden");

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        add(new JScrollPane(chatArea), BorderLayout.CENTER);
        add(inputPanel, BorderLayout.SOUTH);

        // Event: Enter oder Button
        inputField.addActionListener(e -> send(onSend));
        sendButton.addActionListener(e -> send(onSend));
    }

    private void send(Consumer<String> onSend) {
        String message = inputField.getText().trim();
        if (!message.isEmpty()) {
            appendUserMessage(message);
            onSend.accept(message);
            inputField.setText("");
        }
    }

    public void appendUserMessage(String message) {
        chatArea.append("👤 Du: " + message + "\n");
    }

    public void appendBotMessage(String message) {
        chatArea.append("🤖 Bot: " + message + "\n");
    }
}
