package de.bund.zrb.ui;

import de.bund.zrb.chat.attachment.AttachTabToChatUseCase;
import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;
import de.bund.zrb.ingestion.ui.ChatMarkdownFormatter;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * Tab for displaying document previews rendered from the Document Model.
 * Stores raw Markdown and renders it as HTML for display.
 * Provides Copy Raw functionality.
 * Implements DocumentPreviewTabAdapter for attachment support.
 */
public class DocumentPreviewTab implements ConnectionTab, AttachTabToChatUseCase.DocumentPreviewTabAdapter {

    private final JPanel mainPanel;
    private final JEditorPane previewPane;
    private final String sourceName;
    private final String rawMarkdown;
    private final DocumentMetadata metadata;
    private final List<String> warnings;
    private final ChatMarkdownFormatter formatter;
    private final Document document;

    public DocumentPreviewTab(String sourceName, String rawMarkdown, DocumentMetadata metadata, List<String> warnings) {
        this(sourceName, rawMarkdown, metadata, warnings, null);
    }

    public DocumentPreviewTab(String sourceName, String rawMarkdown, DocumentMetadata metadata, List<String> warnings, Document document) {
        this.sourceName = sourceName;
        this.rawMarkdown = rawMarkdown;
        this.metadata = metadata;
        this.warnings = warnings;
        this.document = document;
        this.formatter = ChatMarkdownFormatter.getInstance();

        this.mainPanel = new JPanel(new BorderLayout());
        this.previewPane = createPreviewPane();

        // Toolbar
        JToolBar toolbar = createToolbar();
        mainPanel.add(toolbar, BorderLayout.NORTH);

        // Preview content
        JScrollPane scrollPane = new JScrollPane(previewPane);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Status bar with metadata
        if (metadata != null || (warnings != null && !warnings.isEmpty())) {
            JPanel statusBar = createStatusBar();
            mainPanel.add(statusBar, BorderLayout.SOUTH);
        }

        // Render content
        renderMarkdown();
    }

    private JEditorPane createPreviewPane() {
        JEditorPane pane = new JEditorPane();
        pane.setEditable(false);
        pane.setContentType("text/html");

        // Set up HTML styling
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet styleSheet = kit.getStyleSheet();
        styleSheet.addRule("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; " +
                "margin: 16px; line-height: 1.6; }");
        styleSheet.addRule("h1, h2, h3, h4, h5, h6 { margin-top: 24px; margin-bottom: 16px; }");
        styleSheet.addRule("p { margin: 0 0 16px 0; }");
        styleSheet.addRule("pre { background-color: #f6f8fa; padding: 16px; border-radius: 6px; " +
                "overflow-x: auto; font-family: 'Consolas', 'Monaco', monospace; }");
        styleSheet.addRule("code { background-color: #f6f8fa; padding: 2px 4px; border-radius: 3px; " +
                "font-family: 'Consolas', 'Monaco', monospace; }");
        styleSheet.addRule("blockquote { border-left: 4px solid #dfe2e5; margin: 0; padding-left: 16px; color: #6a737d; }");
        styleSheet.addRule("ul, ol { padding-left: 24px; }");
        styleSheet.addRule("li { margin: 4px 0; }");
        styleSheet.addRule("table { border-collapse: collapse; width: 100%; margin: 16px 0; }");
        styleSheet.addRule("th, td { border: 1px solid #dfe2e5; padding: 8px 12px; text-align: left; }");
        styleSheet.addRule("th { background-color: #f6f8fa; font-weight: 600; }");
        pane.setEditorKit(kit);

        return pane;
    }

    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        // Copy Raw button
        JButton copyButton = new JButton("📋 Copy Raw");
        copyButton.setToolTipText("Kopiert den Raw-Markdown in die Zwischenablage");
        copyButton.addActionListener(this::copyRawMarkdown);
        toolbar.add(copyButton);

        toolbar.addSeparator();

        // Info label
        JLabel infoLabel = new JLabel("📄 Preview: " + sourceName);
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.BOLD));
        toolbar.add(infoLabel);

        toolbar.add(Box.createHorizontalGlue());

        // MIME type info
        if (metadata != null && metadata.getMimeType() != null) {
            JLabel mimeLabel = new JLabel("Type: " + metadata.getMimeType());
            mimeLabel.setForeground(Color.GRAY);
            toolbar.add(mimeLabel);
        }

        return toolbar;
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        StringBuilder status = new StringBuilder();

        if (metadata != null) {
            if (metadata.getSourceName() != null) {
                status.append("Quelle: ").append(metadata.getSourceName());
            }
            if (metadata.getPageCount() != null) {
                if (status.length() > 0) status.append(" | ");
                status.append("Seiten: ").append(metadata.getPageCount());
            }
        }

        if (warnings != null && !warnings.isEmpty()) {
            if (status.length() > 0) status.append(" | ");
            status.append("⚠ ").append(warnings.size()).append(" Warnung(en)");
        }

        JLabel statusLabel = new JLabel(status.toString());
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusBar.add(statusLabel, BorderLayout.WEST);

        // Show warnings on hover
        if (warnings != null && !warnings.isEmpty()) {
            StringBuilder tooltipBuilder = new StringBuilder("<html><b>Warnungen:</b><br>");
            for (String warning : warnings) {
                tooltipBuilder.append("• ").append(warning).append("<br>");
            }
            tooltipBuilder.append("</html>");
            statusLabel.setToolTipText(tooltipBuilder.toString());
        }

        return statusBar;
    }

    private void renderMarkdown() {
        if (rawMarkdown == null || rawMarkdown.isEmpty()) {
            previewPane.setText("<html><body><p><i>Kein Inhalt</i></p></body></html>");
            return;
        }

        try {
            String html = formatter.renderToHtml(rawMarkdown);
            previewPane.setText("<html><body>" + html + "</body></html>");
            // Scroll to top
            SwingUtilities.invokeLater(() -> previewPane.setCaretPosition(0));
        } catch (Exception e) {
            previewPane.setText("<html><body><p style='color:red;'>Fehler beim Rendern: " +
                    escapeHtml(e.getMessage()) + "</p><pre>" + escapeHtml(rawMarkdown) + "</pre></body></html>");
        }
    }

    private void copyRawMarkdown(ActionEvent e) {
        if (rawMarkdown != null && !rawMarkdown.isEmpty()) {
            StringSelection selection = new StringSelection(rawMarkdown);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);

            // Show feedback
            JButton source = (JButton) e.getSource();
            String originalText = source.getText();
            source.setText("✓ Kopiert!");
            Timer timer = new Timer(1500, evt -> source.setText(originalText));
            timer.setRepeats(false);
            timer.start();
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    // === ConnectionTab Interface ===

    @Override
    public String getTitle() {
        return "📄 Preview: " + (sourceName != null ? sourceName : "Dokument");
    }

    @Override
    public String getTooltip() {
        StringBuilder tip = new StringBuilder();
        tip.append("Dokument-Preview");
        if (metadata != null && metadata.getMimeType() != null) {
            tip.append(" (").append(metadata.getMimeType()).append(")");
        }
        return tip.toString();
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void onClose() {
        // Nothing to clean up
    }

    @Override
    public void saveIfApplicable() {
        // Preview is read-only
    }

    @Override
    public String getContent() {
        return rawMarkdown;
    }

    @Override
    public void markAsChanged() {
        // Read-only, no changes
    }

    @Override
    public String getPath() {
        return metadata != null ? metadata.getSourceName() : sourceName;
    }

    @Override
    public Type getType() {
        return Type.PREVIEW;
    }

    @Override
    public void focusSearchField() {
        // No search in preview
    }

    @Override
    public void searchFor(String searchPattern) {
        // No search in preview
    }

    /**
     * Get the raw Markdown content.
     */
    public String getRawMarkdown() {
        return rawMarkdown;
    }

    /**
     * Get the document metadata.
     */
    @Override
    public DocumentMetadata getMetadata() {
        return metadata;
    }

    /**
     * Get any warnings from extraction.
     */
    @Override
    public List<String> getWarnings() {
        return warnings;
    }

    /**
     * Get the Document model (for attachment support).
     */
    @Override
    public Document getDocument() {
        return document;
    }
}

