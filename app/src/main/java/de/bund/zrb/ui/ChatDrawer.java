package de.bund.zrb.ui;

import de.bund.zrb.ui.chat.ChatFormatter;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

public class ChatDrawer extends JPanel {

    private final JTextPane chatArea;
    private final JTextArea inputArea;
    private final JButton sendButton;
    private final JButton attachButton;
    private final ChatFormatter chatFormatter;

    public ChatDrawer(Consumer<String> onSend) {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(300, 0));
        setBorder(BorderFactory.createTitledBorder("💬 Chat"));

        // RSyntaxTextArea für Ausgabe
        chatArea = new JTextPane();
        chatFormatter = new ChatFormatter(chatArea);

        chatArea.setEditable(false);
        chatArea.setBackground(UIManager.getColor("Panel.background")); // systemgrau
        chatArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JScrollPane chatScroll = new JScrollPane(chatArea);
        add(chatScroll, BorderLayout.CENTER);

        // Eingabefeld
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

        // Button-Leiste unten
        attachButton = new JButton("+");
        attachButton.setMargin(new Insets(3, 6, 3, 6)); // optional
        attachButton.setToolTipText("Aktiven Tab teilen");

        sendButton = new JButton("⏎");
        attachButton.setMargin(new Insets(3, 6, 3, 6)); // optional
        sendButton.setToolTipText("Nachricht senden");

        sendButton.addActionListener(e -> send(onSend));

        JPanel buttonBar = new JPanel(new BorderLayout());
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
            chatFormatter.appendUserMessage(message);
            onSend.accept(message);
            inputArea.setText("");
        }
    }

    public void appendBotMessage(String message) {
        chatFormatter.appendBotMessage(message);
    }

    public void appendBotMessageChunk(String chunk) {
        chatFormatter.appendBotMessageChunk(chunk);
    }

    public void setUserInput(String text) {
        inputArea.setText(text);
    }

    public void onAttachClick(Runnable handler) {
        attachButton.addActionListener(e -> handler.run());
    }
}
