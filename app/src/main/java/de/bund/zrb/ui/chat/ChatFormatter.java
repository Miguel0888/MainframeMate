package de.bund.zrb.ui.chat;

import javax.swing.*;
import javax.swing.text.View;
import java.awt.*;

public class ChatFormatter {

    private static final int MESSAGE_WIDTH = 500;


    private final JPanel messageContainer;
    private JTextPane currentBotPane;
    private boolean insideCodeBlock = false;
    private final StringBuilder buffer = new StringBuilder();

    public ChatFormatter(JPanel messageContainer) {
        this.messageContainer = messageContainer;
    }

    public void appendUserMessage(String text) {
        JPanel userPanel = createMessagePanel("👤 Du:", "#e6f0ff", text);
        messageContainer.add(userPanel);
        messageContainer.add(Box.createVerticalStrut(6));
        scrollToBottom();
    }


    public void startBotMessage() {
        buffer.setLength(0);
        currentBotPane = new JTextPane();

        currentBotPane.setContentType("text/html");
        currentBotPane.setEditable(false);
        currentBotPane.setOpaque(false);
        currentBotPane.setBorder(null);
        currentBotPane.setText(""); // Start leer
        currentBotPane.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(Color.decode("#f0ffe6"));
        wrapper.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel("🤖 Bot:");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        wrapper.add(titleLabel);
        wrapper.add(Box.createVerticalStrut(4));
        wrapper.add(currentBotPane);

        messageContainer.add(wrapper);
        messageContainer.add(Box.createVerticalStrut(6));
        scrollToBottom();
    }

    public void appendBotMessageChunk(String chunk) {
        buffer.append(chunk);
        SwingUtilities.invokeLater(() -> {
            currentBotPane.setText(formatHtml(escapeHtml(buffer.toString()).replace("\n", "<br/>")));

            int width = messageContainer.getWidth() - 32;
            if (width <= 0) {
                // Layout ist noch nicht bereit – später nochmal versuchen
                Timer retry = new Timer(20, e -> appendBotMessageChunk(""));
                retry.setRepeats(false);
                retry.start();
                return;
            }

            // Jetzt HTML-Höhe korrekt berechnen
            int height = calculateHtmlContentHeight(currentBotPane, width);

            currentBotPane.setPreferredSize(new Dimension(width, height));
            currentBotPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));

            currentBotPane.revalidate();
            currentBotPane.repaint();
            scrollToBottom();
        });
    }

    private int calculateHtmlContentHeight(JTextPane pane, int width) {
        pane.setSize(new Dimension(width, Integer.MAX_VALUE)); // zwingend nötig
        View rootView = pane.getUI().getRootView(pane);
        rootView.setSize(width, Integer.MAX_VALUE);
        return (int) rootView.getPreferredSpan(View.Y_AXIS);
    }

    private int getActualHTMLHeight(JTextPane pane, int width) {
        pane.setSize(new Dimension(width, Short.MAX_VALUE));
        View rootView = pane.getUI().getRootView(pane);
        rootView.setSize(width, Integer.MAX_VALUE);
        return (int) rootView.getPreferredSpan(View.Y_AXIS);
    }


    public void endBotMessage() {
        currentBotPane = null;
    }

    private JPanel createMessagePanel(String title, String bgColor, String text) {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(Color.decode(bgColor));
        wrapper.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextPane textPane = new JTextPane() {
            @Override
            public Dimension getMaximumSize() {
                Dimension pref = getPreferredSize();
                // maximale Breite auf messageContainer beschränken, Höhe dynamisch
                return new Dimension(Integer.MAX_VALUE, pref.height);
            }
        };

        textPane.setContentType("text/html");
        textPane.setEditable(false);
        textPane.setOpaque(false);
        textPane.setBorder(null);
        textPane.setText(formatHtml(escapeHtml(text).replace("\n", "<br/>")));
        textPane.setAlignmentX(Component.LEFT_ALIGNMENT);

        wrapper.add(titleLabel);
        wrapper.add(Box.createVerticalStrut(4));
        wrapper.add(textPane);

        return wrapper;
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
