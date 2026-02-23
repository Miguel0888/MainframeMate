package de.bund.zrb.ui.mail;

import de.bund.zrb.mail.model.MailMessageContent;
import de.bund.zrb.mail.model.MailMessageHeader;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;
import de.bund.zrb.ingestion.ui.ChatMarkdownFormatter;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;

/**
 * Read-only tab for displaying a single e-mail message.
 * No save/edit functionality – pure preview.
 *
 * Layout:
 *  ┌──────────────────────────────────────┐
 *  │ Toolbar: ✉ Subject  |  📋 Copy      │
 *  ├──────────────────────────────────────┤
 *  │ Header panel (Von, An, Datum, …)     │
 *  ├──────────────────────────────────────┤
 *  │ Body (rendered HTML or plain text)   │
 *  ├──────────────────────────────────────┤
 *  │ Attachments (if any)                 │
 *  └──────────────────────────────────────┘
 */
public class MailPreviewTab extends JPanel implements ConnectionTab {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    private final MailMessageContent content;
    private final String tabTitle;
    private final String markdown;

    public MailPreviewTab(MailMessageContent content) {
        this.content = content;
        MailMessageHeader header = content.getHeader();
        String subject = header.getSubject();
        if (subject == null || subject.trim().isEmpty()) {
            subject = "(kein Betreff)";
        }
        this.tabTitle = "✉ " + subject;
        this.markdown = content.toMarkdown();

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 0, 0, 0));

        // Toolbar
        add(createToolbar(subject), BorderLayout.NORTH);

        // Main content: header panel + body
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(createHeaderPanel(header), BorderLayout.NORTH);
        centerPanel.add(createBodyPanel(), BorderLayout.CENTER);

        // Attachments at bottom (if any)
        if (!content.getAttachmentNames().isEmpty()) {
            centerPanel.add(createAttachmentPanel(), BorderLayout.SOUTH);
        }

        add(centerPanel, BorderLayout.CENTER);
    }

    // ─── UI Building ───

    private JToolBar createToolbar(String subject) {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(new EmptyBorder(4, 8, 4, 8));

        JLabel titleLabel = new JLabel("✉ " + truncate(subject, 60));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setToolTipText(subject);
        toolbar.add(titleLabel);

        toolbar.add(Box.createHorizontalGlue());

        // Copy as Markdown
        JButton copyMdButton = new JButton("📋 Markdown");
        copyMdButton.setToolTipText("Mail-Inhalt als Markdown kopieren");
        copyMdButton.addActionListener(e -> {
            StringSelection sel = new StringSelection(markdown);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
            copyMdButton.setText("✓ Kopiert!");
            Timer timer = new Timer(1500, evt -> copyMdButton.setText("📋 Markdown"));
            timer.setRepeats(false);
            timer.start();
        });
        toolbar.add(copyMdButton);

        // Copy as plain text
        JButton copyTxtButton = new JButton("📋 Text");
        copyTxtButton.setToolTipText("Mail-Body als reinen Text kopieren");
        copyTxtButton.addActionListener(e -> {
            String text = content.getBodyText();
            if (text == null || text.trim().isEmpty()) text = content.getBodyHtml();
            if (text == null) text = "";
            StringSelection sel = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
            copyTxtButton.setText("✓ Kopiert!");
            Timer timer = new Timer(1500, evt -> copyTxtButton.setText("📋 Text"));
            timer.setRepeats(false);
            timer.start();
        });
        toolbar.add(copyTxtButton);

        return toolbar;
    }

    private JPanel createHeaderPanel(MailMessageHeader header) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)),
                new EmptyBorder(8, 12, 8, 12)
        ));
        panel.setBackground(new Color(248, 248, 248));

        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.anchor = GridBagConstraints.NORTHWEST;
        labelGbc.insets = new Insets(2, 0, 2, 8);
        labelGbc.gridx = 0;

        GridBagConstraints valueGbc = new GridBagConstraints();
        valueGbc.anchor = GridBagConstraints.NORTHWEST;
        valueGbc.fill = GridBagConstraints.HORIZONTAL;
        valueGbc.weightx = 1.0;
        valueGbc.insets = new Insets(2, 0, 2, 0);
        valueGbc.gridx = 1;

        int row = 0;

        // Von
        labelGbc.gridy = row;
        valueGbc.gridy = row;
        panel.add(createBoldLabel("Von:"), labelGbc);
        panel.add(createValueLabel(safe(header.getFrom())), valueGbc);
        row++;

        // An
        labelGbc.gridy = row;
        valueGbc.gridy = row;
        panel.add(createBoldLabel("An:"), labelGbc);
        panel.add(createValueLabel(safe(header.getTo())), valueGbc);
        row++;

        // Datum
        if (header.getDate() != null) {
            labelGbc.gridy = row;
            valueGbc.gridy = row;
            panel.add(createBoldLabel("Datum:"), labelGbc);
            panel.add(createValueLabel(DATE_FORMAT.format(header.getDate())), valueGbc);
            row++;
        }

        // Betreff
        labelGbc.gridy = row;
        valueGbc.gridy = row;
        panel.add(createBoldLabel("Betreff:"), labelGbc);
        JLabel subjectLabel = createValueLabel(safe(header.getSubject()));
        subjectLabel.setFont(subjectLabel.getFont().deriveFont(Font.BOLD));
        panel.add(subjectLabel, valueGbc);
        row++;

        // Ordner
        if (header.getFolderPath() != null && !header.getFolderPath().isEmpty()) {
            labelGbc.gridy = row;
            valueGbc.gridy = row;
            panel.add(createBoldLabel("Ordner:"), labelGbc);
            panel.add(createValueLabel(header.getFolderPath()), valueGbc);
            row++;
        }

        // Anhänge-Info
        if (header.hasAttachments()) {
            labelGbc.gridy = row;
            valueGbc.gridy = row;
            panel.add(createBoldLabel("Anhänge:"), labelGbc);
            panel.add(createValueLabel("📎 " + content.getAttachmentNames().size() + " Anhang/Anhänge"), valueGbc);
        }

        return panel;
    }

    private JComponent createBodyPanel() {
        // Try HTML body first, fall back to plain text
        String bodyHtml = content.getBodyHtml();
        String bodyText = content.getBodyText();

        if (bodyHtml != null && !bodyHtml.trim().isEmpty()) {
            return createHtmlBodyPane(bodyHtml);
        } else if (bodyText != null && !bodyText.trim().isEmpty()) {
            // Render plain text as Markdown via the existing formatter
            return createMarkdownBodyPane(bodyText);
        } else {
            JLabel emptyLabel = new JLabel("(kein Inhalt)", JLabel.CENTER);
            emptyLabel.setForeground(Color.GRAY);
            emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC, 14f));
            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.add(emptyLabel, BorderLayout.CENTER);
            return new JScrollPane(wrapper);
        }
    }

    private JScrollPane createHtmlBodyPane(String html) {
        JEditorPane pane = new JEditorPane();
        pane.setEditable(false);
        pane.setContentType("text/html");

        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet styleSheet = kit.getStyleSheet();
        styleSheet.addRule("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; "
                + "margin: 12px; line-height: 1.5; font-size: 13px; }");
        styleSheet.addRule("p { margin: 0 0 8px 0; }");
        styleSheet.addRule("table { border-collapse: collapse; }");
        styleSheet.addRule("td, th { padding: 4px 8px; }");
        styleSheet.addRule("blockquote { border-left: 3px solid #ccc; margin: 4px 0; padding-left: 12px; color: #555; }");
        styleSheet.addRule("a { color: #0066cc; }");
        styleSheet.addRule("img { max-width: 100%; }");
        pane.setEditorKit(kit);

        // Sanitize: wrap in html/body if needed
        String wrapped = html.trim();
        if (!wrapped.toLowerCase().startsWith("<html")) {
            wrapped = "<html><body>" + wrapped + "</body></html>";
        }
        pane.setText(wrapped);
        SwingUtilities.invokeLater(() -> pane.setCaretPosition(0));

        return new JScrollPane(pane);
    }

    private JScrollPane createMarkdownBodyPane(String text) {
        JEditorPane pane = new JEditorPane();
        pane.setEditable(false);
        pane.setContentType("text/html");

        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet styleSheet = kit.getStyleSheet();
        styleSheet.addRule("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; "
                + "margin: 12px; line-height: 1.5; font-size: 13px; }");
        styleSheet.addRule("pre { background-color: #f5f5f5; padding: 8px; font-family: Consolas, monospace; "
                + "white-space: pre-wrap; }");
        pane.setEditorKit(kit);

        try {
            // Try to render as Markdown
            ChatMarkdownFormatter formatter = ChatMarkdownFormatter.getInstance();
            String html = formatter.renderToHtml(text);
            pane.setText("<html><body>" + html + "</body></html>");
        } catch (Exception e) {
            // Fallback: plain text as <pre>
            pane.setText("<html><body><pre>" + escapeHtml(text) + "</pre></body></html>");
        }
        SwingUtilities.invokeLater(() -> pane.setCaretPosition(0));

        return new JScrollPane(pane);
    }

    private JPanel createAttachmentPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)),
                new EmptyBorder(6, 12, 6, 12)
        ));
        panel.setBackground(new Color(252, 252, 252));

        JLabel label = new JLabel("📎 Anhänge:");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, BorderLayout.NORTH);

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setOpaque(false);

        for (String name : content.getAttachmentNames()) {
            JLabel item = new JLabel("    • " + name);
            item.setFont(item.getFont().deriveFont(Font.PLAIN));
            item.setBorder(new EmptyBorder(1, 0, 1, 0));
            listPanel.add(item);
        }

        panel.add(listPanel, BorderLayout.CENTER);
        return panel;
    }

    // ─── Helpers ───

    private static JLabel createBoldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setForeground(new Color(80, 80, 80));
        return label;
    }

    private static JLabel createValueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.PLAIN));
        return label;
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen - 3) + "…" : text;
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ─── ConnectionTab Interface (read-only) ───

    @Override
    public String getTitle() {
        return tabTitle;
    }

    @Override
    public String getTooltip() {
        MailMessageHeader h = content.getHeader();
        String date = h.getDate() != null ? DATE_FORMAT.format(h.getDate()) : "";
        return safe(h.getSubject()) + " – " + safe(h.getFrom()) + " – " + date;
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    @Override
    public void onClose() {
        // nothing – read-only
    }

    @Override
    public void saveIfApplicable() {
        // read-only – no save
    }

    @Override
    public String getContent() {
        return markdown;
    }

    @Override
    public void markAsChanged() {
        // not applicable
    }

    @Override
    public String getPath() {
        return "mail://" + safe(content.getHeader().getSubject());
    }

    @Override
    public Type getType() {
        return Type.PREVIEW;
    }

    @Override
    public void focusSearchField() {
        // no search
    }

    @Override
    public void searchFor(String searchPattern) {
        // no search
    }
}
