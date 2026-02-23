package de.bund.zrb.mail.model;

import java.util.Date;

/**
 * Header/summary of a mail message (lightweight, for list display).
 */
public class MailMessageHeader {
    private final String subject;
    private final String from;
    private final String to;
    private final Date date;
    private final String folderPath;
    private final long descriptorNodeId;
    private final boolean hasAttachments;
    private final String messageClass;

    public MailMessageHeader(String subject, String from, String to, Date date,
                              String folderPath, long descriptorNodeId, boolean hasAttachments,
                              String messageClass) {
        this.subject = subject;
        this.from = from;
        this.to = to;
        this.date = date;
        this.folderPath = folderPath;
        this.descriptorNodeId = descriptorNodeId;
        this.hasAttachments = hasAttachments;
        this.messageClass = messageClass;
    }

    /** Convenience constructor without messageClass (backward compat). */
    public MailMessageHeader(String subject, String from, String to, Date date,
                              String folderPath, long descriptorNodeId, boolean hasAttachments) {
        this(subject, from, to, date, folderPath, descriptorNodeId, hasAttachments, null);
    }

    public String getSubject() {
        return subject;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public Date getDate() {
        return date;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public long getDescriptorNodeId() {
        return descriptorNodeId;
    }

    public boolean hasAttachments() {
        return hasAttachments;
    }

    public String getMessageClass() {
        return messageClass;
    }

    /**
     * Returns true for report/receipt messages (read receipts, delivery reports, etc.).
     */
    public boolean isReport() {
        return messageClass != null && messageClass.toUpperCase().startsWith("REPORT.");
    }

    @Override
    public String toString() {
        String prefix;
        if (isReport()) {
            prefix = "📨 ";
        } else if (hasAttachments) {
            prefix = "📎 ";
        } else {
            prefix = "✉ ";
        }
        String dateStr = date != null ? String.format("%1$td.%1$tm.%1$tY %1$tH:%1$tM", date) : "";
        String subj = subject != null && !subject.isEmpty() ? subject : "(kein Betreff)";
        if (isReport()) {
            subj = "[Bericht] " + subj;
        }
        String sender = from != null ? from : "";
        return prefix + dateStr + "  " + sender + " – " + subj;
    }
}
