package de.bund.zrb.ui.chat;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;

public class ChatFormatter {

    private final JTextPane chatPane;
    private final HTMLEditorKit kit;
    private final HTMLDocument doc;

    private boolean insideCodeBlock = false;
    private final StringBuilder buffer = new StringBuilder();

    public ChatFormatter(JTextPane chatPane) {
        this.chatPane = chatPane;
        this.kit = new HTMLEditorKit();
        this.doc = new HTMLDocument();
        chatPane.setEditorKit(kit);
        chatPane.setDocument(doc);
        chatPane.setContentType("text/html");
        chatPane.setEditable(false);
        chatPane.setBackground(UIManager.getColor("Panel.background"));

        buffer.append("<html><body style='font-family:sans-serif; font-size:12px;'>");
    }

    public void appendUserMessage(String message) {
        flushCodeBlock(); // falls offener Block existiert
        buffer.append("<div style='background-color:#e6f0ff; padding:4px;'><b>👤 Du:</b><br/>");
        appendFormatted(message);
        buffer.append("</div>");
        updatePane();
    }

    public void appendBotMessageChunk(String chunk) {
        appendFormatted(chunk);
        updatePane();
    }

    private void appendFormatted(String text) {
        String[] lines = text.split("(?<=\n)"); // erhält \n am Zeilenende

        for (String lineWithBreak : lines) {
            String line = lineWithBreak.replace("\n", ""); // \n separat behandeln

            if (line.trim().startsWith("```")) {
                insideCodeBlock = !insideCodeBlock;
                if (insideCodeBlock) {
                    buffer.append("<pre style='background:#f9f9f9; border:1px solid #ccc; padding:4px;'>");
                } else {
                    buffer.append("</pre>");
                }
                continue;
            }

            buffer.append(escapeHtml(line));

            // Falls die Zeile ursprünglich mit \n endete: HTML-Zeilenumbruch hinzufügen
            if (lineWithBreak.endsWith("\n") && !insideCodeBlock) {
                buffer.append("<br/>");
            }
        }
    }

    private void flushCodeBlock() {
        if (insideCodeBlock) {
            buffer.append("</pre>");
            insideCodeBlock = false;
        }
    }

    private void updatePane() {
        buffer.append("</body></html>");
        chatPane.setText(buffer.toString());
        chatPane.setCaretPosition(chatPane.getDocument().getLength());
        // danach wieder <body> anhängen, da sonst keine weiteren Nachrichten gehen
        buffer.setLength(buffer.length() - "</body></html>".length());
    }

    private String escapeHtml(String input) {
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public void startBotMessage() {
        flushCodeBlock(); // falls noch offen
        buffer.append("<div style='background-color:#f0ffe6; padding:6px; margin-top:6px; border-left:4px solid #aaddaa;'>");
        buffer.append("<b>🤖 Bot:</b><br/>");
    }


    public void endBotMessage() {
        flushCodeBlock(); // falls offener Block
        buffer.append("</div>");
        updatePane(); // finaler Render
    }

}
