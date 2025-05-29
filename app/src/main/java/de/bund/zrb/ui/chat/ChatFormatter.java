package de.bund.zrb.ui.chat;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;

public class ChatFormatter {

    private final JTextPane chatPane;
    private final StyledDocument doc;

    private boolean insideCodeBlock = false;

    private static final Color USER_BG = new Color(230, 240, 255);
    private static final Color BOT_BG = new Color(240, 255, 230);
    private static final Color CODE_BG = new Color(250, 250, 250);

    public ChatFormatter(JTextPane chatPane) {
        this.chatPane = chatPane;
        this.doc = chatPane.getStyledDocument();
        chatPane.setEditable(false);
        chatPane.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chatPane.setBackground(Color.WHITE);
    }

    public void appendUserMessage(String message) {
        appendHeader("👤 Du:", USER_BG);
        appendFormatted(message, USER_BG);
    }

    public void appendBotMessage(String message) {
        appendHeader("🤖 Bot:", BOT_BG);
        appendFormatted(message, BOT_BG);
    }

    public void appendBotMessageChunk(String chunk) {
        appendFormatted(chunk, BOT_BG);
    }

    private void appendHeader(String label, Color bgColor) {
        appendStyledText(label + "\n", bgColor, "SansSerif", Font.BOLD, 12);
    }

    private void appendFormatted(String text, Color bgColor) {
        String[] lines = text.split("\n");

        for (String line : lines) {
            if (line.trim().startsWith("```")) {
                insideCodeBlock = !insideCodeBlock;
                continue; // Marker selbst nicht anzeigen
            }

            if (insideCodeBlock) {
                appendStyledText(line + "\n", CODE_BG, "Monospaced", Font.PLAIN, 12);
            } else {
                appendStyledText(line + "\n", bgColor, "SansSerif", Font.PLAIN, 12);
            }
        }
    }

    private void appendStyledText(String text, Color bg, String fontName, int style, int size) {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(attrs, fontName);
        StyleConstants.setFontSize(attrs, size);
        StyleConstants.setBold(attrs, (style & Font.BOLD) != 0);
        StyleConstants.setItalic(attrs, (style & Font.ITALIC) != 0);
        StyleConstants.setBackground(attrs, bg);

        try {
            doc.insertString(doc.getLength(), text, attrs);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
}
