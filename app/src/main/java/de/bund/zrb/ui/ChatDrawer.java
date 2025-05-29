package de.bund.zrb.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

public class ChatDrawer extends JPanel {

    private final JTextArea chatArea;
    private final JTextArea inputArea;
    private final JButton sendButton;
    private final JButton attachButton;

    public ChatDrawer(Consumer<String> onSend) {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(300, 0));
        setBorder(BorderFactory.createTitledBorder("💬 Chat"));

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(UIManager.getColor("Panel.background")); // systemgrau
        chatArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));


        JScrollPane chatScroll = new JScrollPane(chatArea);
        add(chatScroll, BorderLayout.CENTER);

        inputArea = new JTextArea(3, 30);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBackground(Color.WHITE);
        inputArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));


        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createEmptyBorder());

        // KeyListener für Enter / Shift+Enter
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (e.isShiftDown()) {
                        inputArea.append("\n");
                    } else {
                        e.consume();
                        send(onSend);
                    }
                }
            }
        });

        // Buttons: Stil & Größe an Statusbar anpassen
        attachButton = new JButton("+");
        attachButton.setMargin(new Insets(3, 6, 3, 6)); // optional
        attachButton.setToolTipText("Aktiven Tab teilen");

        sendButton = new JButton("⏎");
        attachButton.setMargin(new Insets(3, 6, 3, 6)); // optional
        sendButton.setToolTipText("Nachricht senden");

        sendButton.addActionListener(e -> send(onSend));

        JPanel buttonBar = new JPanel(new BorderLayout());
//        buttonBar.setBackground(Color.WHITE);
        buttonBar.add(attachButton, BorderLayout.WEST);
        buttonBar.add(sendButton, BorderLayout.EAST);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        bottomPanel.add(inputScroll, BorderLayout.CENTER);
        bottomPanel.add(buttonBar, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void send(Consumer<String> onSend) {
        String message = inputArea.getText().trim();
        if (!message.isEmpty()) {
            appendUserMessage(message);
            onSend.accept(message);
            inputArea.setText("");
        }
    }

    public void appendUserMessage(String message) {
        chatArea.append("👤 Du: " + message + "\n");
    }

    public void appendBotMessage(String message) {
        chatArea.append("🤖 Bot: " + message + "\n");
    }

    public void setUserInput(String text) {
        inputArea.setText(text);
    }

    public void onAttachClick(Runnable handler) {
        attachButton.addActionListener(e -> handler.run());
    }

    public void appendBotMessageChunk(String chunk) {
        chatArea.append(chunk); // oder auch Styles für farbliche Unterschiede
    }

}
