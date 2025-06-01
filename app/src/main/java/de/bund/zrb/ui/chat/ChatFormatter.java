package de.bund.zrb.ui.chat;

import javax.swing.*;
import java.awt.*;

public class ChatFormatter {

    private final JPanel messageContainer;
    private JTextPane currentBotPane;
    private boolean insideCodeBlock = false;
    private final StringBuilder buffer = new StringBuilder();

    public ChatFormatter(JPanel messageContainer) {
        this.messageContainer = messageContainer;
    }

    public void appendUserMessage(String text) {
        JTextPane pane = createStyledPane("👤 Du:", "#e6f0ff", "#000000");
        appendFormatted(pane, text);
        messageContainer.add(pane);
        messageContainer.add(Box.createVerticalStrut(6));
        pane.setText(formatHtml(text));
        scrollToBottom();
    }

    public void startBotMessage() {
        currentBotPane = createStyledPane("🤖 Bot:", "#f0ffe6", "#000000");
        messageContainer.add(currentBotPane);
        messageContainer.add(Box.createVerticalStrut(6));
        buffer.setLength(0);
    }

    public void appendBotMessageChunk(String chunk) {
        buffer.append(chunk);
        currentBotPane.setText(formatHtml(buffer.toString()));
        scrollToBottom();
    }

    public void endBotMessage() {
        currentBotPane = null;
    }

    private JTextPane createStyledPane(String title, String bgColor, String fgColor) {
        JTextPane pane = new JTextPane();
        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.setOpaque(true);
        pane.setBackground(Color.decode(bgColor));
        pane.setForeground(Color.decode(fgColor));
        pane.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        pane.setText(formatHtml("<b>" + title + "</b><br/>"));
        pane.setAlignmentX(Component.LEFT_ALIGNMENT);
        return pane;
    }

    private void appendFormatted(JTextPane pane, String raw) {
        pane.setText(formatHtml("<b>" + pane.getText() + "</b><br/>" + escapeHtml(raw).replace("\n", "<br/>")));
    }

    private String formatHtml(String html) {
        return "<html><body style='font-family:sans-serif; font-size:12px;'>" + html + "</body></html>";
    }

    private String escapeHtml(String input) {
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            Container parent = messageContainer.getParent();
            while (!(parent instanceof JScrollPane) && parent != null) {
                parent = parent.getParent();
            }
            if (parent instanceof JScrollPane) {
                JScrollPane scrollPane = (JScrollPane) parent;
                JScrollBar vBar = scrollPane.getVerticalScrollBar();
                vBar.setValue(vBar.getMaximum());
            }
        });
    }
}
