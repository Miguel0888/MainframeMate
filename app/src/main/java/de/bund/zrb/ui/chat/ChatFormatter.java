package de.bund.zrb.ui.chat;

import javax.swing.*;
import javax.swing.text.View;
import java.awt.*;

public class ChatFormatter {

    public enum Role {
        USER("👤 Du:", "#e6f0ff"),
        BOT("🤖 Bot:", "#f0ffe6");

        public final String label;
        public final String bgColor;

        Role(String label, String bgColor) {
            this.label = label;
            this.bgColor = bgColor;
        }
    }

    private final java.util.List<JTextPane> allTextPanes = new java.util.ArrayList<>();

    private final JPanel messageContainer;
    private final StringBuilder buffer = new StringBuilder();
    private JTextPane currentBotPane;

    public ChatFormatter(JPanel messageContainer) {
        this.messageContainer = messageContainer;
        attachResizeListenerToWindow();
    }

    private void attachResizeListenerToWindow() {
        SwingUtilities.invokeLater(() -> {
            Window window = SwingUtilities.getWindowAncestor(messageContainer);
            if (window != null) {
                window.addComponentListener(new java.awt.event.ComponentAdapter() {
                    @Override
                    public void componentResized(java.awt.event.ComponentEvent e) {
                        resizeAllTextPanes();
                    }
                });
            }
        });
    }

    public void appendUserMessage(String text) {
        JTextPane pane = createConfiguredTextPane();
        pane.setText(formatHtml(escapeHtml(text).replace("\n", "<br/>")));
        applyDynamicSizing(pane);

        JPanel panel = createMessagePanel(Role.USER, pane);
        messageContainer.add(panel);
        messageContainer.add(Box.createVerticalStrut(6));
        scrollToBottom();
    }

    public void startBotMessage() {
        buffer.setLength(0);
        currentBotPane = createConfiguredTextPane();

        JPanel panel = createMessagePanel(Role.BOT, currentBotPane);
        messageContainer.add(panel);
        messageContainer.add(Box.createVerticalStrut(6));
        scrollToBottom();
    }

    public void appendBotMessageChunk(String chunk) {
        buffer.append(chunk);
        SwingUtilities.invokeLater(() -> {
            currentBotPane.setText(formatHtml(escapeHtml(buffer.toString()).replace("\n", "<br/>")));
            applyDynamicSizing(currentBotPane);
            scrollToBottom();
        });
    }

    public void endBotMessage() {
        currentBotPane = null;
    }

    private JTextPane createConfiguredTextPane() {
        JTextPane pane = new JTextPane();
        allTextPanes.add(pane);
        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(null);
        pane.setAlignmentX(Component.LEFT_ALIGNMENT);
        return pane;
    }

    private JPanel createMessagePanel(Role role, JTextPane textPane) {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(Color.decode(role.bgColor));
        wrapper.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel(role.label);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        wrapper.add(titleLabel);
        wrapper.add(Box.createVerticalStrut(4));
        wrapper.add(textPane);

        return wrapper;
    }

    private void applyDynamicSizing(JTextPane pane) {
        int width = (messageContainer.getParent() != null)
                ? messageContainer.getParent().getWidth() - 32
                : 600; // Fallback

        int height = calculateHtmlContentHeight(pane, width);
        pane.setPreferredSize(new Dimension(width, height));
        pane.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        pane.revalidate();
        pane.repaint();
    }

    private void resizeAllTextPanes() {
        SwingUtilities.invokeLater(() -> {
            int width = (messageContainer.getParent() != null)
                    ? messageContainer.getParent().getWidth() - 32
                    : 600;

            for (JTextPane pane : allTextPanes) {
                int height = calculateHtmlContentHeight(pane, width);
                pane.setPreferredSize(new Dimension(width, height));
                pane.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
                pane.revalidate();
                pane.repaint();
            }
        });
    }

    private int calculateHtmlContentHeight(JTextPane pane, int width) {
        pane.setSize(new Dimension(width, Integer.MAX_VALUE));
        View rootView = pane.getUI().getRootView(pane);
        rootView.setSize(width, Integer.MAX_VALUE);
        return (int) rootView.getPreferredSpan(View.Y_AXIS);
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
