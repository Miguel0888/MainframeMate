package de.bund.zrb.mail.model;

import java.util.Date;

/**
 * Header/summary of a mail message or Outlook item (lightweight, for list display).
 *
 * Supports different item types via messageClass:
 *   IPM.Note         → E-Mail
 *   IPM.Appointment  → Kalendertermin (startDate, endDate, location)
 *   IPM.Contact      → Kontakt (location = company)
 *   IPM.Task         → Aufgabe (startDate = due date, percentComplete)
 *   IPM.StickyNote   → Notiz
 *   REPORT.*         → Quittung/Bericht
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

    // ── Optional fields for non-mail items ──
    private Date startDate;    // Appointment: start time, Task: due date
    private Date endDate;      // Appointment: end time
    private String location;   // Appointment: location, Contact: company
    private boolean allDay;    // Appointment: all-day event
    private int percentComplete; // Task: 0-100

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

    // ── Setters for optional fields (builder-style) ──

    public MailMessageHeader withAppointmentInfo(Date startDate, Date endDate, String location, boolean allDay) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.location = location;
        this.allDay = allDay;
        return this;
    }

    public MailMessageHeader withTaskInfo(Date dueDate, int percentComplete) {
        this.startDate = dueDate;
        this.percentComplete = percentComplete;
        return this;
    }

    public MailMessageHeader withContactInfo(String company) {
        this.location = company;
        return this;
    }

    // ── Getters ──

    public String getSubject() { return subject; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public Date getDate() { return date; }
    public String getFolderPath() { return folderPath; }
    public long getDescriptorNodeId() { return descriptorNodeId; }
    public boolean hasAttachments() { return hasAttachments; }
    public String getMessageClass() { return messageClass; }
    public Date getStartDate() { return startDate; }
    public Date getEndDate() { return endDate; }
    public String getLocation() { return location; }
    public boolean isAllDay() { return allDay; }
    public int getPercentComplete() { return percentComplete; }

    // ── Type checks ──

    public boolean isReport() {
        return messageClass != null && messageClass.toUpperCase().startsWith("REPORT.");
    }

    public boolean isAppointment() {
        return messageClass != null && messageClass.toUpperCase().startsWith("IPM.APPOINTMENT");
    }

    public boolean isContact() {
        return messageClass != null && messageClass.toUpperCase().startsWith("IPM.CONTACT");
    }

    public boolean isTask() {
        return messageClass != null && messageClass.toUpperCase().startsWith("IPM.TASK");
    }

    public boolean isStickyNote() {
        return messageClass != null && messageClass.toUpperCase().startsWith("IPM.STICKYNOTE");
    }

    /**
     * Returns a display icon based on the item type.
     */
    public String getTypeIcon() {
        if (isAppointment()) return "📅";
        if (isContact()) return "👤";
        if (isTask()) return "✅";
        if (isStickyNote()) return "📝";
        if (isReport()) return "📨";
        if (hasAttachments) return "📎";
        return "✉";
    }

    @Override
    public String toString() {
        String icon = getTypeIcon();
        String subj = subject != null && !subject.isEmpty() ? subject : "(kein Betreff)";

        if (isAppointment()) {
            return formatAppointment(icon, subj);
        }
        if (isContact()) {
            return formatContact(icon, subj);
        }
        if (isTask()) {
            return formatTask(icon, subj);
        }
        if (isStickyNote()) {
            String dateStr = date != null ? String.format("%1$td.%1$tm.%1$tY", date) : "";
            return icon + " " + dateStr + "  " + subj;
        }
        if (isReport()) {
            subj = "[Bericht] " + subj;
        }

        // Default: E-Mail format
        String dateStr = date != null ? String.format("%1$td.%1$tm.%1$tY %1$tH:%1$tM", date) : "";
        String sender = from != null ? from : "";
        return icon + " " + dateStr + "  " + sender + " – " + subj;
    }

    private String formatAppointment(String icon, String subj) {
        StringBuilder sb = new StringBuilder();
        sb.append(icon).append(" ");
        if (startDate != null) {
            if (allDay) {
                sb.append(String.format("%1$td.%1$tm.%1$tY", startDate));
                if (endDate != null && !sameDay(startDate, endDate)) {
                    sb.append(" – ").append(String.format("%1$td.%1$tm.%1$tY", endDate));
                }
            } else {
                sb.append(String.format("%1$td.%1$tm.%1$tY %1$tH:%1$tM", startDate));
                if (endDate != null) {
                    if (sameDay(startDate, endDate)) {
                        sb.append("–").append(String.format("%1$tH:%1$tM", endDate));
                    } else {
                        sb.append(" – ").append(String.format("%1$td.%1$tm.%1$tY %1$tH:%1$tM", endDate));
                    }
                }
            }
        } else if (date != null) {
            sb.append(String.format("%1$td.%1$tm.%1$tY", date));
        }
        sb.append("  ").append(subj);
        if (location != null && !location.trim().isEmpty()) {
            sb.append("  📍 ").append(location.trim());
        }
        return sb.toString();
    }

    private String formatContact(String icon, String subj) {
        StringBuilder sb = new StringBuilder();
        sb.append(icon).append(" ").append(subj);
        if (location != null && !location.trim().isEmpty()) {
            sb.append("  🏢 ").append(location.trim());
        }
        return sb.toString();
    }

    private String formatTask(String icon, String subj) {
        StringBuilder sb = new StringBuilder();
        sb.append(icon);
        if (percentComplete >= 100) {
            sb.append("✓ ");
        } else {
            sb.append(" ");
        }
        if (startDate != null) {
            sb.append("Fällig: ").append(String.format("%1$td.%1$tm.%1$tY", startDate)).append("  ");
        }
        sb.append(subj);
        if (percentComplete > 0 && percentComplete < 100) {
            sb.append("  (").append(percentComplete).append("%)");
        }
        return sb.toString();
    }

    private static boolean sameDay(Date a, Date b) {
        if (a == null || b == null) return false;
        String da = String.format("%1$tY%1$tm%1$td", a);
        String db = String.format("%1$tY%1$tm%1$td", b);
        return da.equals(db);
    }
}
