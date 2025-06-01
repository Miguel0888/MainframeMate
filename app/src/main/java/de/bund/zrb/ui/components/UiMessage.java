package de.bund.zrb.ui.components;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;

public class UiMessage extends JPanel {

    private final JTextPane textPane;
    private final StringBuilder buffer;

    public UiMessage(String initialText, boolean fromUser) {
        setLayout(new BorderLayout());
        setOpaque(false);

        buffer = new StringBuilder();

        textPane = new JTextPane();
        textPane.setContentType("text/html");
        textPane.setEditorKit(createHtmlKit());
        textPane.setEditable(false);
        textPane.setOpaque(false);

        textPane.setText(wrapHtml(initialText));

        int maxWidth = 600;
        textPane.setMaximumSize(new Dimension(maxWidth, Integer.MAX_VALUE));
        textPane.setPreferredSize(new Dimension(maxWidth, textPane.getPreferredSize().height));

        JPanel bubble = new JPanel(new BorderLayout());
        bubble.setBackground(fromUser ? new Color(230, 240, 255) : new Color(240, 255, 230));
        bubble.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        bubble.setOpaque(true);
        bubble.add(textPane, BorderLayout.CENTER);
        bubble.setAlignmentX(fromUser ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);

        setLayout(new FlowLayout(fromUser ? FlowLayout.RIGHT : FlowLayout.LEFT));
        add(bubble);
    }

    public void appendChunk(String chunk) {
        buffer.append(chunk);
        textPane.setText(wrapHtml(buffer.toString()));
        revalidate();
    }

    private String wrapHtml(String content) {
        return "<html><body style='font-family:sans-serif; font-size:12px;'>" + escape(content) + "</body></html>";
    }

    private String escape(String content) {
        return content.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br/>");
    }

    private HTMLEditorKit createHtmlKit() {
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet style = kit.getStyleSheet();
        style.addRule("body { margin: 0; padding: 0; }");
        return kit;
    }
}
