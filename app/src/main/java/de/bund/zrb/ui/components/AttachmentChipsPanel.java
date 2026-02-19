package de.bund.zrb.ui.components;

import de.bund.zrb.chat.attachment.ChatAttachment;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panel displaying attachment chips.
 * Each chip shows the attachment name with a remove button.
 */
public class AttachmentChipsPanel extends JPanel {

    private final List<ChatAttachment> attachments = new ArrayList<>();
    private final Consumer<ChatAttachment> onRemove;
    private final JPanel chipsContainer;

    public AttachmentChipsPanel() {
        this(null);
    }

    public AttachmentChipsPanel(Consumer<ChatAttachment> onRemove) {
        this.onRemove = onRemove;
        setLayout(new BorderLayout());
        setOpaque(false);

        chipsContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        chipsContainer.setOpaque(false);
        add(chipsContainer, BorderLayout.CENTER);

        // Initially hidden
        setVisible(false);
    }

    /**
     * Add an attachment chip.
     */
    public void addAttachment(ChatAttachment attachment) {
        if (attachment == null || containsAttachment(attachment.getId())) {
            return;
        }

        attachments.add(attachment);
        chipsContainer.add(createChip(attachment));
        setVisible(true);
        revalidate();
        repaint();
    }

    /**
     * Remove an attachment by ID.
     */
    public void removeAttachment(String attachmentId) {
        ChatAttachment toRemove = null;
        for (ChatAttachment att : attachments) {
            if (att.getId().equals(attachmentId)) {
                toRemove = att;
                break;
            }
        }

        if (toRemove != null) {
            attachments.remove(toRemove);
            rebuildChips();
        }
    }

    /**
     * Check if an attachment is already added.
     */
    public boolean containsAttachment(String attachmentId) {
        for (ChatAttachment att : attachments) {
            if (att.getId().equals(attachmentId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all attachment IDs.
     */
    public List<String> getAttachmentIds() {
        List<String> ids = new ArrayList<>();
        for (ChatAttachment att : attachments) {
            ids.add(att.getId());
        }
        return ids;
    }

    /**
     * Get all attachments.
     */
    public List<ChatAttachment> getAttachments() {
        return new ArrayList<>(attachments);
    }

    /**
     * Get the number of attachments.
     */
    public int getAttachmentCount() {
        return attachments.size();
    }

    /**
     * Clear all attachments.
     */
    public void clear() {
        attachments.clear();
        chipsContainer.removeAll();
        setVisible(false);
        revalidate();
        repaint();
    }

    private void rebuildChips() {
        chipsContainer.removeAll();
        for (ChatAttachment att : attachments) {
            chipsContainer.add(createChip(att));
        }
        setVisible(!attachments.isEmpty());
        revalidate();
        repaint();
    }

    private JPanel createChip(ChatAttachment attachment) {
        JPanel chip = new JPanel(new BorderLayout(2, 0));
        chip.setBackground(new Color(230, 240, 250));
        chip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 200, 220), 1, true),
                BorderFactory.createEmptyBorder(2, 6, 2, 4)
        ));

        // Label
        String labelText = attachment.getDisplayLabel();
        if (labelText.length() > 30) {
            labelText = labelText.substring(0, 27) + "...";
        }
        JLabel label = new JLabel(labelText);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
        label.setToolTipText(buildTooltip(attachment));
        chip.add(label, BorderLayout.CENTER);

        // Remove button
        JLabel removeBtn = new JLabel("×");
        removeBtn.setFont(removeBtn.getFont().deriveFont(Font.BOLD, 12f));
        removeBtn.setForeground(Color.GRAY);
        removeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        removeBtn.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 2));
        removeBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                attachments.remove(attachment);
                rebuildChips();
                if (onRemove != null) {
                    onRemove.accept(attachment);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                removeBtn.setForeground(Color.RED);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                removeBtn.setForeground(Color.GRAY);
            }
        });
        chip.add(removeBtn, BorderLayout.EAST);

        // Warning indicator
        if (attachment.hasWarnings()) {
            JLabel warnLabel = new JLabel("⚠");
            warnLabel.setForeground(new Color(200, 150, 0));
            warnLabel.setFont(warnLabel.getFont().deriveFont(10f));
            warnLabel.setToolTipText(attachment.getWarningsCount() + " Warnung(en)");
            chip.add(warnLabel, BorderLayout.WEST);
        }

        return chip;
    }

    private String buildTooltip(ChatAttachment attachment) {
        StringBuilder tip = new StringBuilder("<html>");
        tip.append("<b>").append(escapeHtml(attachment.getName())).append("</b><br>");

        if (attachment.getMimeType() != null) {
            tip.append("Typ: ").append(escapeHtml(attachment.getMimeType())).append("<br>");
        }

        if (attachment.getSourcePath() != null) {
            tip.append("Quelle: ").append(escapeHtml(attachment.getSourcePath())).append("<br>");
        }

        if (attachment.hasWarnings()) {
            tip.append("<br><b style='color:orange;'>⚠ ").append(attachment.getWarningsCount())
               .append(" Warnung(en)</b>");
        }

        tip.append("</html>");
        return tip.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }
}

