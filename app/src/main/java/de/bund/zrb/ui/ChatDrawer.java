package de.bund.zrb.ui;

import de.bund.zrb.ui.chat.ChatFormatter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

public class ChatDrawer extends JPanel {

    private final JTextPane chatPane;
    private final JTextArea inputArea;
    private final JButton sendButton;
    private final JButton attachButton;
    private final JLabel statusLabel;
    private final ChatFormatter formatter;

    private boolean awaitingBotResponse = false;

    public ChatDrawer(Consumer<String> onSend) {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(300, 0));
        setBorder(BorderFactory.createTitledBorder("💬 Chat"));

        chatPane = new JTextPane();
        formatter = new ChatFormatter(chatPane);

        JScrollPane chatScroll = new JScrollPane(chatPane);
        add(chatScroll, BorderLayout.CENTER);

        inputArea = new JTextArea(3, 30);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBackground(Color.WHITE);
        inputArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (e.isShiftDown()) {
                        inputArea.append("\n");
                    } else {
                        e.consume();
                        onSend(onSend);
                    }
                }
            }
        });

        sendButton = new JButton("⏎");
        sendButton.setToolTipText("Nachricht senden");
        sendButton.addActionListener(e -> onSend(onSend));

        attachButton = new JButton("+");
        attachButton.setToolTipText("Aktiven Tab teilen");

        JPanel buttonBar = new JPanel(new BorderLayout());
        buttonBar.add(attachButton, BorderLayout.WEST);
        buttonBar.add(sendButton, BorderLayout.EAST);

        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createEmptyBorder());

        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 4));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        bottomPanel.add(inputScroll, BorderLayout.CENTER);
        bottomPanel.add(buttonBar, BorderLayout.SOUTH);
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void onSend(Consumer<String> onSend) {
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
        formatter.startBotMessage(); // erzeugt <div> mit Kopfzeile
        setStatus("🤖 Bot schreibt...");
    }

    public void endBotMessage() {
        formatter.endBotMessage();
        setStatus(" ");
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }


}
