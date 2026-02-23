package de.bund.zrb.mail.model;

import java.util.Collections;
import java.util.List;

/**
 * Full content of a mail message (header + body + attachment info).
 */
public class MailMessageContent {
    private final MailMessageHeader header;
    private final String bodyText;
    private final String bodyHtml;
    private final List<String> attachmentNames;

    public MailMessageContent(MailMessageHeader header, String bodyText, String bodyHtml, List<String> attachmentNames) {
        this.header = header;
        this.bodyText = bodyText;
        this.bodyHtml = bodyHtml;
        this.attachmentNames = attachmentNames != null ? attachmentNames : Collections.<String>emptyList();
    }

    public MailMessageHeader getHeader() {
        return header;
    }

    public String getBodyText() {
        return bodyText;
    }

    public String getBodyHtml() {
        return bodyHtml;
    }

    public List<String> getAttachmentNames() {
        return Collections.unmodifiableList(attachmentNames);
    }

    /**
     * Renders the mail content as Markdown for preview.
     */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(header.getSubject() != null ? header.getSubject() : "(kein Betreff)").append("\n\n");
        sb.append("| | |\n|---|---|\n");
        sb.append("| **Von** | ").append(safe(header.getFrom())).append(" |\n");
        sb.append("| **An** | ").append(safe(header.getTo())).append(" |\n");
        if (header.getDate() != null) {
            sb.append("| **Datum** | ").append(String.format("%1$td.%1$tm.%1$tY %1$tH:%1$tM", header.getDate())).append(" |\n");
        }
        if (header.getFolderPath() != null) {
            sb.append("| **Ordner** | ").append(header.getFolderPath()).append(" |\n");
        }
        sb.append("\n---\n\n");

        // Body
        String body = bodyText;
        if (body == null || body.trim().isEmpty()) {
            body = bodyHtml;
        }
        if (body != null && !body.trim().isEmpty()) {
            sb.append(body.trim());
        } else {
            sb.append("_(kein Inhalt)_");
        }
        sb.append("\n\n");

        // Attachments
        if (!attachmentNames.isEmpty()) {
            sb.append("---\n\n### Anhänge\n\n");
            for (String name : attachmentNames) {
                sb.append("- 📎 ").append(name).append("\n");
            }
        }

        return sb.toString();
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }
}
