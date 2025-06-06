package de.bund.zrb.ui.util;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.View;
import java.awt.*;
import java.util.Optional;

public class ChatFormatter {

    private JPanel currentBotWrapper;

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
    private JTextPane currentBotContent;

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

    public void appendUserMessage(String text, @NotNull Runnable onDelete) {
        JTextPane pane = createConfiguredTextPane();
//        pane.setText(formatHtml(escapeHtml(text).replace("\n", "<br/>")));
        String html = ChatMarkdownFormatter.format(text);
        pane.setText(formatHtml(html));
        applyDynamicSizing(pane);

        JPanel panel = createMessagePanel(Role.USER, pane, Optional.ofNullable(onDelete));
        messageContainer.add(panel);
        messageContainer.add(Box.createVerticalStrut(6));
        scrollToBottom();
    }

    public void startBotMessage() {
        buffer.setLength(0);
        currentBotContent = createConfiguredTextPane();

        currentBotWrapper = createMessagePanel(Role.BOT, currentBotContent, Optional.empty());
        messageContainer.add(currentBotWrapper);
        messageContainer.add(Box.createVerticalStrut(6));
        scrollToBottom();
    }

    public void appendBotMessageChunk(String chunk) {
        buffer.append(chunk);
        SwingUtilities.invokeLater(() -> {
//            currentBotContent.setText(formatHtml(escapeHtml(buffer.toString()).replace("\n", "<br/>")));
            String html = ChatMarkdownFormatter.format(buffer.toString());
            currentBotContent.setText(formatHtml(html));
            applyDynamicSizing(currentBotContent);
            scrollToBottom();
        });
    }

    public void endBotMessage(@NotNull Runnable runnable) {
        Component header = SwingComponentFinder.findByName(currentBotWrapper, "header");
        if (header instanceof JPanel) {
            JPanel headerPanel = (JPanel) header;
            // Delete-Button hinzufügen
            addDeleteButton(currentBotWrapper, headerPanel, runnable);
        }
    }

    private JTextPane createConfiguredTextPane() {
        JTextPane pane = new JTextPane();
        pane.setName("contentPane");
        allTextPanes.add(pane);
        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(null);
        pane.setAlignmentX(Component.LEFT_ALIGNMENT);
        return pane;
    }

    private JPanel createMessagePanel(Role role, JTextPane textPane, Optional<Runnable> onDelete) {
        JPanel wrapper = createMessagePanelWrapper(role);

        // Titel + Lösch-Button
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setOpaque(false);

        JLabel titleLabel = new JLabel(role.label);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(titleLabel);
        header.setName("header"); // Set the name for later retrieval if needed
        header.add(Box.createHorizontalGlue());
        onDelete.ifPresent((action) -> {
            addDeleteButton(wrapper, header, action);
        });

        wrapper.add(header);
        wrapper.add(Box.createVerticalStrut(4));
        textPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(textPane);

        return wrapper;
    }

    private void addDeleteButton(JPanel wrapper, JPanel header, Runnable onDelete) {
        JButton deleteButton = new JButton("🗑");
        deleteButton.setName("deleteButton");
        deleteButton.setToolTipText("Nachricht löschen");
        deleteButton.setPreferredSize(new Dimension(32, 32));
        deleteButton.setFont(deleteButton.getFont().deriveFont(Font.BOLD, 24f));
        deleteButton.setMargin(new Insets(0, 4, 0, 4));
        deleteButton.setFocusable(false);
        deleteButton.setContentAreaFilled(false);
        deleteButton.setBorder(BorderFactory.createEmptyBorder());
        deleteButton.addActionListener(e -> {
            onDelete.run(); // Entfernt aus History und GUI
            messageContainer.remove(wrapper);
            messageContainer.revalidate();
            messageContainer.repaint();
        });
        header.add(deleteButton);
    }

    private @NotNull JPanel createMessagePanelWrapper(Role role) {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(Color.decode(role.bgColor));
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                role == Role.BOT
                        ? BorderFactory.createMatteBorder(0, 4, 0, 0, new Color(0x66AA66))
                        : BorderFactory.createEmptyBorder(),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
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
        Settings settings = SettingsHelper.load();
        String fontName = settings.aiConfig.getOrDefault("editor.font", "SansSerif");
        int fontSize = Integer.parseInt(settings.aiConfig.getOrDefault("editor.fontSize", "16"));
        return String.format(
                "<html><body style='font-family:%s; font-size:%dpx; margin:0; text-align:left;'>%s</body></html>",
                fontName, fontSize, html
        );
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
