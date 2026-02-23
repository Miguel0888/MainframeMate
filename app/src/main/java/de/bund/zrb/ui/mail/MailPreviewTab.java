package de.bund.zrb.ui.mail;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.mail.model.MailMessageContent;
import de.bund.zrb.mail.model.MailMessageHeader;
import de.bund.zrb.model.Settings;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;

/**
 * Read-only mail preview tab with Text/HTML toggle.
 *
 * Security:
 * - Default mode is TEXT (plain text only, no HTML processing)
 * - HTML is only processed when user explicitly switches to HTML mode
 * - Whitelisted senders automatically open in HTML mode
 * - HTML is sanitized via {@link MailHtmlSanitizer} before rendering
 *
 * Layout:
 *  ┌──────────────────────────────────────────────────┐
 *  │ Toolbar: [📄 Text | 🌐 HTML] ✉ Subject  📋 Copy │
 *  ├──────────────────────────────────────────────────┤
 *  │ Header panel (Von, An, Datum, Betreff, Anhänge)  │
 *  ├──────────────────────────────────────────────────┤
 *  │ Body (Text or HTML, switchable)                  │
 *  ├──────────────────────────────────────────────────┤
 *  │ Attachments (if any)                             │
 *  └──────────────────────────────────────────────────┘
 */
public class MailPreviewTab extends JPanel implements ConnectionTab {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    private final MailMessageContent content;
    private final String tabTitle;
    private final String senderAddress; // normalized lowercase email
    private final String mailboxPath; // OST/PST file path (for bookmark restoration)
    private boolean htmlMode;
    private boolean htmlRendered = false; // lazy: HTML only rendered on first switch

    // UI
    private final CardLayout bodyCardLayout = new CardLayout();
    private final JPanel bodyPanel;
    private final JTextArea textPane;
    private JEditorPane htmlPane; // lazy-created
    private final JButton textButton;
    private final JButton htmlButton;
    private final JButton whitelistButton;

    private static final String CARD_TEXT = "TEXT";
    private static final String CARD_HTML = "HTML";

    public MailPreviewTab(MailMessageContent content, String mailboxPath) {
        this.content = content;
        this.mailboxPath = mailboxPath != null ? mailboxPath : "";
        MailMessageHeader header = content.getHeader();
        String subject = header.getSubject();
        if (subject == null || subject.trim().isEmpty()) subject = "(kein Betreff)";
        this.tabTitle = "✉ " + subject;
        this.senderAddress = MailHtmlSanitizer.extractEmailAddress(header.getFrom());

        // Determine initial mode: whitelisted sender → HTML, otherwise TEXT
        boolean hasHtml = content.getBodyHtml() != null && !content.getBodyHtml().trim().isEmpty();
        boolean whitelisted = isSenderWhitelisted();
        this.htmlMode = hasHtml && whitelisted;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 0, 0, 0));

        // ─── Text body pane (always created) ───
        textPane = new JTextArea();
        textPane.setEditable(false);
        textPane.setLineWrap(true);
        textPane.setWrapStyleWord(true);
        textPane.setFont(new Font("SansSerif", Font.PLAIN, 13));
        textPane.setMargin(new Insets(12, 12, 12, 12));
        String bodyText = content.getBodyText();
        textPane.setText(bodyText != null && !bodyText.trim().isEmpty()
                ? bodyText
                : (hasHtml ? "(Nur HTML-Inhalt verfügbar – wechsle zur HTML-Ansicht)" : "(kein Inhalt)"));
        textPane.setCaretPosition(0);
        addContextMenu(textPane);

        // ─── Body panel with CardLayout ───
        bodyPanel = new JPanel(bodyCardLayout);
        bodyPanel.add(new JScrollPane(textPane), CARD_TEXT);
        // HTML pane placeholder – will be created lazily
        bodyPanel.add(new JPanel(), CARD_HTML);

        // ─── Toolbar ───
        textButton = new JButton("📄 Text");
        htmlButton = new JButton("🌐 HTML");
        whitelistButton = new JButton();
        updateWhitelistButton();
        JToolBar toolbar = createToolbar(subject, hasHtml);

        // ─── Assembly ───
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(createHeaderPanel(header), BorderLayout.NORTH);
        centerPanel.add(bodyPanel, BorderLayout.CENTER);
        if (!content.getAttachmentNames().isEmpty()) {
            centerPanel.add(createAttachmentPanel(), BorderLayout.SOUTH);
        }

        add(toolbar, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);

        // Apply initial mode
        if (htmlMode) {
            switchToHtml();
        } else {
            bodyCardLayout.show(bodyPanel, CARD_TEXT);
            highlightActiveButton(false);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Mode switching
    // ═══════════════════════════════════════════════════════════════

    private void switchToText() {
        htmlMode = false;
        bodyCardLayout.show(bodyPanel, CARD_TEXT);
        highlightActiveButton(false);
    }

    private void switchToHtml() {
        htmlMode = true;
        if (!htmlRendered) {
            renderHtmlLazily();
        }
        bodyCardLayout.show(bodyPanel, CARD_HTML);
        highlightActiveButton(true);
    }

    private void renderHtmlLazily() {
        String rawHtml = content.getBodyHtml();
        if (rawHtml == null || rawHtml.trim().isEmpty()) {
            // No HTML – show info
            JLabel noHtml = new JLabel("(Kein HTML-Inhalt verfügbar)", JLabel.CENTER);
            noHtml.setForeground(Color.GRAY);
            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.add(noHtml, BorderLayout.CENTER);
            replaceHtmlCard(new JScrollPane(wrapper));
            htmlRendered = true;
            return;
        }

        // Sanitize HTML
        String sanitized = MailHtmlSanitizer.sanitize(rawHtml);

        htmlPane = new JEditorPane();
        htmlPane.setEditable(false);
        htmlPane.setContentType("text/html");

        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet styleSheet = kit.getStyleSheet();
        styleSheet.addRule("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; "
                + "margin: 12px; line-height: 1.5; font-size: 13px; }");
        styleSheet.addRule("p { margin: 0 0 8px 0; }");
        styleSheet.addRule("table { border-collapse: collapse; }");
        styleSheet.addRule("td, th { padding: 4px 8px; }");
        styleSheet.addRule("blockquote { border-left: 3px solid #ccc; margin: 4px 0; padding-left: 12px; color: #555; }");
        styleSheet.addRule("a { color: #0066cc; }");
        styleSheet.addRule("img { max-width: 600px; }");
        htmlPane.setEditorKit(kit);

        htmlPane.setText(sanitized);
        SwingUtilities.invokeLater(() -> htmlPane.setCaretPosition(0));
        addContextMenu(htmlPane);

        replaceHtmlCard(new JScrollPane(htmlPane));
        htmlRendered = true;
    }

    private void replaceHtmlCard(JScrollPane scrollPane) {
        // Remove old HTML card and add new one
        Component[] comps = bodyPanel.getComponents();
        if (comps.length > 1) {
            bodyPanel.remove(1);
        }
        bodyPanel.add(scrollPane, CARD_HTML);
        bodyPanel.revalidate();
        bodyPanel.repaint();
    }

    private void highlightActiveButton(boolean htmlActive) {
        textButton.setFont(textButton.getFont().deriveFont(htmlActive ? Font.PLAIN : Font.BOLD));
        htmlButton.setFont(htmlButton.getFont().deriveFont(htmlActive ? Font.BOLD : Font.PLAIN));
        textButton.setBackground(htmlActive ? null : new Color(220, 230, 245));
        htmlButton.setBackground(htmlActive ? new Color(220, 230, 245) : null);
        textButton.setOpaque(!htmlActive);
        htmlButton.setOpaque(htmlActive);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Whitelist
    // ═══════════════════════════════════════════════════════════════

    private boolean isSenderWhitelisted() {
        if (senderAddress == null || senderAddress.isEmpty()) return false;
        Settings settings = SettingsHelper.load();
        return settings.mailHtmlWhitelistedSenders != null
                && settings.mailHtmlWhitelistedSenders.contains(senderAddress);
    }

    private void toggleWhitelist() {
        Settings settings = SettingsHelper.load();
        if (settings.mailHtmlWhitelistedSenders == null) {
            settings.mailHtmlWhitelistedSenders = new java.util.HashSet<>();
        }
        if (settings.mailHtmlWhitelistedSenders.contains(senderAddress)) {
            settings.mailHtmlWhitelistedSenders.remove(senderAddress);
        } else {
            settings.mailHtmlWhitelistedSenders.add(senderAddress);
            // Auto-switch to HTML when whitelisting
            if (!htmlMode) switchToHtml();
        }
        SettingsHelper.save(settings);
        updateWhitelistButton();
    }

    private void updateWhitelistButton() {
        boolean wl = isSenderWhitelisted();
        whitelistButton.setText(wl ? "⭐ HTML-Whitelist ✓" : "☆ HTML-Whitelist");
        whitelistButton.setToolTipText(wl
                ? "Absender '" + senderAddress + "' von HTML-Whitelist entfernen"
                : "Absender '" + senderAddress + "' immer in HTML anzeigen");
    }

    // ═══════════════════════════════════════════════════════════════
    //  Context menu (right-click on body/tab)
    // ═══════════════════════════════════════════════════════════════

    private void addContextMenu(JComponent component) {
        JPopupMenu popup = new JPopupMenu();

        // Whitelist toggle
        JMenuItem whitelistItem = new JMenuItem();
        popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                boolean wl = isSenderWhitelisted();
                whitelistItem.setText(wl
                        ? "HTML-Whitelist entfernen: " + senderAddress
                        : "Immer HTML anzeigen für: " + senderAddress);
            }
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
        });
        whitelistItem.addActionListener(e -> toggleWhitelist());
        popup.add(whitelistItem);

        popup.addSeparator();

        // Copy
        JMenuItem copyText = new JMenuItem("📋 Text kopieren");
        copyText.addActionListener(e -> copyToClipboard(content.getBodyText()));
        popup.add(copyText);

        JMenuItem copyMarkdown = new JMenuItem("📋 Markdown kopieren");
        copyMarkdown.addActionListener(e -> copyToClipboard(content.toMarkdown()));
        popup.add(copyMarkdown);

        component.setComponentPopupMenu(popup);
    }

    private void copyToClipboard(String text) {
        if (text == null) text = "";
        StringSelection sel = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
    }

    // ═══════════════════════════════════════════════════════════════
    //  UI Building
    // ═══════════════════════════════════════════════════════════════

    private JToolBar createToolbar(String subject, boolean hasHtml) {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(new EmptyBorder(4, 8, 4, 8));

        // View mode buttons
        textButton.setFocusable(false);
        textButton.addActionListener(e -> switchToText());
        toolbar.add(textButton);

        htmlButton.setFocusable(false);
        htmlButton.setEnabled(hasHtml);
        htmlButton.addActionListener(e -> {
            if (hasHtml) switchToHtml();
        });
        toolbar.add(htmlButton);

        toolbar.addSeparator();

        // Whitelist button
        if (hasHtml && senderAddress != null && !senderAddress.isEmpty()) {
            whitelistButton.setFocusable(false);
            whitelistButton.addActionListener(e -> toggleWhitelist());
            toolbar.add(whitelistButton);
            toolbar.addSeparator();
        }

        // Title
        JLabel titleLabel = new JLabel("✉ " + truncate(subject, 50));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleLabel.setToolTipText(subject);
        toolbar.add(titleLabel);

        toolbar.add(Box.createHorizontalGlue());

        // Copy buttons
        JButton copyMdButton = new JButton("📋 Markdown");
        copyMdButton.setFocusable(false);
        copyMdButton.setToolTipText("Mail als Markdown kopieren");
        copyMdButton.addActionListener(e -> {
            copyToClipboard(content.toMarkdown());
            copyMdButton.setText("✓ Kopiert!");
            Timer timer = new Timer(1500, evt -> copyMdButton.setText("📋 Markdown"));
            timer.setRepeats(false);
            timer.start();
        });
        toolbar.add(copyMdButton);

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
        labelGbc.gridy = row; valueGbc.gridy = row;
        panel.add(boldLabel("Von:"), labelGbc);
        panel.add(valueLabel(safe(header.getFrom())), valueGbc);
        row++;

        // An
        labelGbc.gridy = row; valueGbc.gridy = row;
        panel.add(boldLabel("An:"), labelGbc);
        panel.add(valueLabel(safe(header.getTo())), valueGbc);
        row++;

        // Datum
        if (header.getDate() != null) {
            labelGbc.gridy = row; valueGbc.gridy = row;
            panel.add(boldLabel("Datum:"), labelGbc);
            panel.add(valueLabel(DATE_FORMAT.format(header.getDate())), valueGbc);
            row++;
        }

        // Betreff
        labelGbc.gridy = row; valueGbc.gridy = row;
        panel.add(boldLabel("Betreff:"), labelGbc);
        JLabel subjectLabel = valueLabel(safe(header.getSubject()));
        subjectLabel.setFont(subjectLabel.getFont().deriveFont(Font.BOLD));
        panel.add(subjectLabel, valueGbc);
        row++;

        // Anhänge
        if (header.hasAttachments()) {
            labelGbc.gridy = row; valueGbc.gridy = row;
            panel.add(boldLabel("Anhänge:"), labelGbc);
            panel.add(valueLabel("📎 " + content.getAttachmentNames().size()), valueGbc);
        }

        return panel;
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
            item.setBorder(new EmptyBorder(1, 0, 1, 0));
            listPanel.add(item);
        }
        panel.add(listPanel, BorderLayout.CENTER);
        return panel;
    }

    // ─── Helpers ───

    private static JLabel boldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        l.setForeground(new Color(80, 80, 80));
        return l;
    }

    private static JLabel valueLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.PLAIN));
        return l;
    }

    private static String safe(String s) { return s != null ? s : ""; }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max - 1) + "…" : text;
    }

    // ═══════════════════════════════════════════════════════════════
    //  ConnectionTab interface (read-only)
    // ═══════════════════════════════════════════════════════════════

    @Override public String getTitle() { return tabTitle; }

    @Override
    public String getTooltip() {
        MailMessageHeader h = content.getHeader();
        String date = h.getDate() != null ? DATE_FORMAT.format(h.getDate()) : "";
        return safe(h.getSubject()) + " – " + safe(h.getFrom()) + " – " + date;
    }

    @Override public JComponent getComponent() { return this; }
    @Override public void onClose() { /* read-only */ }
    @Override public void saveIfApplicable() { /* read-only */ }
    @Override public String getContent() { return content.toMarkdown(); }
    @Override public void markAsChanged() { /* n/a */ }

    @Override
    public String getPath() {
        MailMessageHeader h = content.getHeader();
        // Format: mailboxPath#folderPath#descriptorNodeId
        // This allows bookmark restoration via PstMailboxReader.readMessage()
        return mailboxPath + "#" + safe(h.getFolderPath()) + "#" + h.getDescriptorNodeId();
    }

    @Override public Type getType() { return Type.PREVIEW; }
    @Override public void focusSearchField() { /* no search */ }
    @Override public void searchFor(String searchPattern) { /* no search */ }
}
