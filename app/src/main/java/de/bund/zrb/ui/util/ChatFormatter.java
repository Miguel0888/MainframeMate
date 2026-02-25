package de.bund.zrb.ui.util;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.ingestion.ui.ChatMarkdownFormatter;
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
        BOT("🤖 Bot:", "#f0ffe6"),
        TOOL("🔧 Tool:", "#fff8e6"),
        ERROR("⚠ Fehler:", "#ffe6e6");

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
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        buffer.append(chunk);
        SwingUtilities.invokeLater(() -> {
            if (currentBotContent == null) {
                return;
            }
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

    public void appendToolEvent(String headerText, String jsonBody) {
        appendToolEvent(headerText, jsonBody, false);
    }

    public void appendToolEvent(String headerText, String jsonBody, boolean isError) {
        JPanel wrapper = createMessagePanelWrapper(isError ? Role.ERROR : Role.TOOL);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setOpaque(false);

        JLabel titleLabel = new JLabel(headerText == null ? (isError ? Role.ERROR.label : Role.TOOL.label) : headerText);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        header.add(titleLabel);
        header.add(Box.createHorizontalGlue());

        JButton toggle = new JButton("Details");
        toggle.setFocusable(false);
        toggle.setMargin(new Insets(0, 4, 0, 4));
        header.add(toggle);

        JTextPane bodyPane = createConfiguredTextPane();
        String html = ChatMarkdownFormatter.format("```json\n" + (jsonBody == null ? "" : jsonBody) + "\n```");
        bodyPane.setText(formatHtml(html));
        bodyPane.setVisible(false);

        toggle.addActionListener(e -> {
            bodyPane.setVisible(!bodyPane.isVisible());
            applyDynamicSizing(bodyPane);
            wrapper.revalidate();
            wrapper.repaint();
            scrollToBottom();
        });

        wrapper.add(header);
        wrapper.add(Box.createVerticalStrut(4));
        wrapper.add(bodyPane);

        messageContainer.add(wrapper);
        messageContainer.add(Box.createVerticalStrut(6));
        scrollToBottom();
    }

    public void appendBotToolCall(String headerText, String jsonBody) {
        JPanel wrapper = createMessagePanelWrapper(Role.BOT);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setOpaque(false);

        JLabel titleLabel = new JLabel(headerText == null ? Role.BOT.label : headerText);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        header.add(titleLabel);
        header.add(Box.createHorizontalGlue());

        JButton toggle = new JButton("Details");
        toggle.setFocusable(false);
        toggle.setMargin(new Insets(0, 4, 0, 4));
        header.add(toggle);

        JTextPane bodyPane = createConfiguredTextPane();
        String html = ChatMarkdownFormatter.format("```json\n" + (jsonBody == null ? "" : jsonBody) + "\n```");
        bodyPane.setText(formatHtml(html));
        bodyPane.setVisible(false);

        toggle.addActionListener(e -> {
            bodyPane.setVisible(!bodyPane.isVisible());
            applyDynamicSizing(bodyPane);
            wrapper.revalidate();
            wrapper.repaint();
            scrollToBottom();
        });

        wrapper.add(header);
        wrapper.add(Box.createVerticalStrut(4));
        wrapper.add(bodyPane);

        messageContainer.add(wrapper);
        messageContainer.add(Box.createVerticalStrut(6));
        scrollToBottom();
    }

    public ToolApprovalRequest requestToolApproval(String toolName, String toolCallJson, boolean isWrite) {
        ToolApprovalRequest request = new ToolApprovalRequest();
        JPanel wrapper = createMessagePanelWrapper(Role.TOOL);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setOpaque(false);

        String title = "🔒 Freigabe: " + (toolName == null ? "" : toolName) + (isWrite ? " (WRITE)" : " (READ)");
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        header.add(titleLabel);
        header.add(Box.createHorizontalGlue());

        JButton detailsButton = new JButton("Details");
        detailsButton.setFocusable(false);
        header.add(detailsButton);

        JButton approveButton = new JButton("Ausführen");
        approveButton.setFocusable(false);

        JButton dropdownButton = new JButton("▾");
        dropdownButton.setFocusable(false);
        dropdownButton.setMargin(new Insets(2, 2, 2, 2));
        dropdownButton.setPreferredSize(new Dimension(20, approveButton.getPreferredSize().height));

        header.add(Box.createHorizontalStrut(6));
        header.add(approveButton);
        header.add(dropdownButton);

        JButton cancelButton = new JButton("Abbrechen");
        cancelButton.setFocusable(false);
        header.add(Box.createHorizontalStrut(4));
        header.add(cancelButton);

        JTextPane bodyPane = createConfiguredTextPane();
        String html = ChatMarkdownFormatter.format("```json\n" + (toolCallJson == null ? "" : toolCallJson) + "\n```");
        bodyPane.setText(formatHtml(html));
        bodyPane.setVisible(false);

        detailsButton.addActionListener(e -> {
            bodyPane.setVisible(!bodyPane.isVisible());
            applyDynamicSizing(bodyPane);
            wrapper.revalidate();
            wrapper.repaint();
            scrollToBottom();
        });

        java.util.concurrent.atomic.AtomicBoolean decided = new java.util.concurrent.atomic.AtomicBoolean(false);
        approveButton.addActionListener(e -> {
            if (decided.compareAndSet(false, true)) {
                approveButton.setEnabled(false);
                dropdownButton.setEnabled(false);
                cancelButton.setEnabled(false);
                titleLabel.setText(title + " ✅");
                request.approve();
            }
        });

        dropdownButton.addActionListener(e -> {
            JPopupMenu popup = new JPopupMenu();
            JMenuItem rememberItem = new JMenuItem("Für Session merken");
            rememberItem.setToolTipText("Dieses Tool wird für die restliche Chat-Session automatisch freigegeben");
            rememberItem.addActionListener(ev -> {
                if (decided.compareAndSet(false, true)) {
                    approveButton.setEnabled(false);
                    dropdownButton.setEnabled(false);
                    cancelButton.setEnabled(false);
                    titleLabel.setText(title + " ✅ (Session)");
                    request.approveForSession();
                }
            });
            popup.add(rememberItem);
            popup.show(dropdownButton, 0, dropdownButton.getHeight());
        });

        cancelButton.addActionListener(e -> {
            if (decided.compareAndSet(false, true)) {
                approveButton.setEnabled(false);
                dropdownButton.setEnabled(false);
                cancelButton.setEnabled(false);
                titleLabel.setText(title + " ❌");
                request.cancel();
            }
        });

        wrapper.add(header);
        wrapper.add(Box.createVerticalStrut(4));
        wrapper.add(bodyPane);

        messageContainer.add(wrapper);
        messageContainer.add(Box.createVerticalStrut(6));
        scrollToBottom();
        return request;
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

        // CSS for proper Markdown rendering: tables, code, headings, etc.
        String css = String.format(
                "body { font-family: %s; font-size: %dpx; margin: 0; padding: 0; line-height: 1.4; } " +
                "p { margin: 4px 0; } " +
                "h1 { font-size: 1.5em; margin: 8px 0 4px 0; font-weight: bold; } " +
                "h2 { font-size: 1.3em; margin: 6px 0 4px 0; font-weight: bold; } " +
                "h3 { font-size: 1.1em; margin: 4px 0 2px 0; font-weight: bold; } " +
                "h4, h5, h6 { font-size: 1em; margin: 4px 0 2px 0; font-weight: bold; } " +
                "table { border-collapse: collapse; margin: 8px 0; width: 100%%; } " +
                "th, td { border: 1px solid #ccc; padding: 6px 10px; text-align: left; } " +
                "th { background-color: #f0f0f0; font-weight: bold; } " +
                "tr:nth-child(even) { background-color: #fafafa; } " +
                "pre { background-color: #f5f5f5; padding: 8px; border-radius: 4px; border: 1px solid #ddd; " +
                "      font-family: monospace; white-space: pre-wrap; word-wrap: break-word; overflow-x: auto; } " +
                "code { background-color: #f5f5f5; padding: 2px 4px; border-radius: 3px; font-family: monospace; } " +
                "pre code { background-color: transparent; padding: 0; } " +
                "blockquote { border-left: 3px solid #ccc; margin: 8px 0; padding-left: 12px; color: #666; } " +
                "ul, ol { margin: 4px 0; padding-left: 24px; } " +
                "li { margin: 2px 0; } " +
                "a { color: #0066cc; } " +
                "hr { border: none; border-top: 1px solid #ccc; margin: 8px 0; } " +
                "strong, b { font-weight: bold; } " +
                "em, i { font-style: italic; }",
                fontName, fontSize
        );

        return String.format(
                "<html><head><style>%s</style></head><body>%s</body></html>",
                css, html
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

    public void removeCurrentBotMessage() {
        if (currentBotWrapper == null) {
            return;
        }
        messageContainer.remove(currentBotWrapper);
        messageContainer.revalidate();
        messageContainer.repaint();
        currentBotWrapper = null;
        currentBotContent = null;
    }
}
