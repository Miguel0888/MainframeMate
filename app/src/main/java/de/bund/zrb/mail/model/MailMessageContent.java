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

        // Type-specific header fields
        if (header.isAppointment()) {
            sb.append("| **Typ** | 📅 Termin |\n");
            if (header.getStartDate() != null) {
                sb.append("| **Beginn** | ").append(formatDate(header.getStartDate(), header.isAllDay())).append(" |\n");
            }
            if (header.getEndDate() != null) {
                sb.append("| **Ende** | ").append(formatDate(header.getEndDate(), header.isAllDay())).append(" |\n");
            }
            if (header.getLocation() != null && !header.getLocation().trim().isEmpty()) {
                sb.append("| **Ort** | ").append(header.getLocation()).append(" |\n");
            }
            if (header.getFrom() != null && !header.getFrom().isEmpty()) {
                sb.append("| **Organisator** | ").append(safe(header.getFrom())).append(" |\n");
            }
            if (header.getTo() != null && !header.getTo().isEmpty()) {
                sb.append("| **Teilnehmer** | ").append(safe(header.getTo())).append(" |\n");
            }
        } else if (header.isContact()) {
            sb.append("| **Typ** | 👤 Kontakt |\n");
            if (header.getFrom() != null && !header.getFrom().isEmpty()) {
                sb.append("| **Name** | ").append(safe(header.getFrom())).append(" |\n");
            }
            if (header.getLocation() != null && !header.getLocation().trim().isEmpty()) {
                sb.append("| **Firma** | ").append(header.getLocation()).append(" |\n");
            }
            if (header.getTo() != null && !header.getTo().isEmpty()) {
                sb.append("| **E-Mail** | ").append(safe(header.getTo())).append(" |\n");
            }
        } else if (header.isTask()) {
            sb.append("| **Typ** | ✅ Aufgabe |\n");
            if (header.getStartDate() != null) {
                sb.append("| **Fällig** | ").append(formatDate(header.getStartDate(), false)).append(" |\n");
            }
            if (header.getPercentComplete() > 0) {
                sb.append("| **Fortschritt** | ").append(header.getPercentComplete()).append("% |\n");
            }
        } else {
            // Regular email
            sb.append("| **Von** | ").append(safe(header.getFrom())).append(" |\n");
            sb.append("| **An** | ").append(safe(header.getTo())).append(" |\n");
        }

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

    private static String formatDate(java.util.Date date, boolean dateOnly) {
        if (date == null) return "";
        if (dateOnly) {
            return String.format("%1$td.%1$tm.%1$tY", date);
        }
        return String.format("%1$td.%1$tm.%1$tY %1$tH:%1$tM", date);
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }
}
