package de.bund.zrb.ui.preview;

import de.bund.zrb.chat.attachment.AttachTabToChatUseCase;
import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;
import de.bund.zrb.ingestion.ui.ChatMarkdownFormatter;
import de.zrb.bund.newApi.ui.ConnectionTab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Enhanced preview/editor tab with split view (Raw + Rendered), view mode toggle,
 * and optional sidebar showing file details and index status.
 */
public class SplitPreviewTab extends JPanel implements ConnectionTab, AttachTabToChatUseCase.DocumentPreviewTabAdapter {

    // Text file extensions that default to SPLIT mode
    private static final Set<String> TEXT_EXTENSIONS = new HashSet<>(Arrays.asList(
            "txt", "md", "json", "yml", "yaml", "properties", "ini", "conf", "cfg",
            "xml", "csv", "log", "java", "py", "js", "ts", "html", "css", "sql",
            "sh", "bat", "ps1", "rb", "php", "c", "cpp", "h", "hpp", "go", "rs"
    ));

    // Binary document formats that default to RENDERED_ONLY
    private static final Set<String> RENDERED_ONLY_EXTENSIONS = new HashSet<>(Arrays.asList(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp"
    ));

    private final String sourceName;
    private final String sourcePath;
    private String rawContent;
    private final DocumentMetadata metadata;
    private final List<String> warnings;
    private final Document document;
    private final ChatMarkdownFormatter formatter;
    private final boolean isTextFile;
    private final boolean isRemote;

    // UI Components
    private final JPanel mainPanel;
    private final JTextArea rawPane;
    private final JEditorPane renderedPane;
    private final JSplitPane splitPane;
    private final JScrollPane rawScrollPane;
    private final JScrollPane renderedScrollPane;
    private final IndexStatusSidebar sidebar;
    private final JPanel contentPanel;

    // State
    private ViewMode currentMode;
    private boolean sidebarVisible = false;
    private boolean hasUnsavedChanges = false;

    public SplitPreviewTab(String sourceName, String rawContent, DocumentMetadata metadata,
                           List<String> warnings, Document document, boolean isRemote) {
        this.sourceName = sourceName;
        this.sourcePath = metadata != null ? metadata.getSourceName() : sourceName;
        this.rawContent = rawContent != null ? rawContent : "";
        this.metadata = metadata;
        this.warnings = warnings;
        this.document = document;
        this.formatter = ChatMarkdownFormatter.getInstance();
        this.isRemote = isRemote;
        this.isTextFile = determineIfTextFile(sourceName, metadata);

        setLayout(new BorderLayout());

        // Create components
        this.rawPane = createRawPane();
        this.renderedPane = createRenderedPane();
        this.rawScrollPane = new JScrollPane(rawPane);
        this.renderedScrollPane = new JScrollPane(renderedPane);
        this.splitPane = createSplitPane();
        this.sidebar = new IndexStatusSidebar();
        this.mainPanel = new JPanel(new BorderLayout());

        // Toolbar with view mode toggle
        JToolBar toolbar = createToolbar();

        // Content panel (holds split/single view)
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(splitPane, BorderLayout.CENTER);

        // Main panel assembly
        mainPanel.add(contentPanel, BorderLayout.CENTER);

        // Add sidebar (initially hidden)
        sidebar.setVisible(false);
        mainPanel.add(sidebar, BorderLayout.EAST);

        add(toolbar, BorderLayout.NORTH);
        add(mainPanel, BorderLayout.CENTER);

        // Status bar
        if (metadata != null || (warnings != null && !warnings.isEmpty())) {
            add(createStatusBar(), BorderLayout.SOUTH);
        }

        // Set default mode based on file type
        this.currentMode = isTextFile ? ViewMode.SPLIT : ViewMode.RENDERED_ONLY;
        applyViewMode(currentMode);

        // Populate sidebar
        updateSidebarInfo();

        // Render content
        renderContent();
    }

    private boolean determineIfTextFile(String name, DocumentMetadata metadata) {
        // Check MIME type first
        if (metadata != null && metadata.getMimeType() != null) {
            String mime = metadata.getMimeType().toLowerCase();
            if (mime.startsWith("text/")) return true;
            if (mime.contains("json") || mime.contains("xml") || mime.contains("yaml")) return true;
            if (mime.contains("pdf") || mime.contains("msword") || mime.contains("officedocument")) return false;
        }

        // Fallback to extension
        if (name != null) {
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex > 0) {
                String ext = name.substring(dotIndex + 1).toLowerCase();
                if (RENDERED_ONLY_EXTENSIONS.contains(ext)) return false;
                if (TEXT_EXTENSIONS.contains(ext)) return true;
            }
        }

        // Default to text
        return true;
    }

    private JTextArea createRawPane() {
        JTextArea area = new JTextArea();
        area.setFont(new Font("Consolas", Font.PLAIN, 13));
        area.setEditable(isTextFile); // Only text files are editable
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setText(rawContent);
        area.setCaretPosition(0);

        // Track changes
        area.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { markChanged(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { markChanged(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { markChanged(); }
            private void markChanged() {
                if (isTextFile) {
                    hasUnsavedChanges = true;
                    rawContent = area.getText();
                }
            }
        });

        return area;
    }

    private JEditorPane createRenderedPane() {
        JEditorPane pane = new JEditorPane();
        pane.setEditable(false);
        pane.setContentType("text/html");

        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet styleSheet = kit.getStyleSheet();
        styleSheet.addRule("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; " +
                "margin: 16px; line-height: 1.6; }");
        styleSheet.addRule("h1, h2, h3 { margin-top: 20px; margin-bottom: 12px; }");
        styleSheet.addRule("p { margin: 0 0 12px 0; }");
        styleSheet.addRule("pre { background-color: #f5f5f5; padding: 12px; border-radius: 4px; " +
                "font-family: Consolas, monospace; white-space: pre-wrap; }");
        styleSheet.addRule("code { background-color: #f5f5f5; padding: 2px 4px; border-radius: 3px; " +
                "font-family: Consolas, monospace; }");
        styleSheet.addRule("table { border-collapse: collapse; width: 100%; margin: 12px 0; }");
        styleSheet.addRule("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }");
        styleSheet.addRule("th { background-color: #f0f0f0; font-weight: bold; }");
        styleSheet.addRule("blockquote { border-left: 4px solid #ddd; margin: 0; padding-left: 16px; color: #666; }");
        styleSheet.addRule("ul, ol { padding-left: 24px; }");
        pane.setEditorKit(kit);

        return pane;
    }

    private JSplitPane createSplitPane() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, rawScrollPane, renderedScrollPane);
        split.setResizeWeight(0.5);
        split.setDividerLocation(0.5);
        split.setOneTouchExpandable(true);
        return split;
    }

    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(new EmptyBorder(4, 8, 4, 8));

        // File info
        JLabel fileLabel = new JLabel((isTextFile ? "📝 " : "📄 ") + truncate(sourceName, 40));
        fileLabel.setFont(fileLabel.getFont().deriveFont(Font.BOLD));
        fileLabel.setToolTipText(sourcePath);
        toolbar.add(fileLabel);

        toolbar.addSeparator(new Dimension(16, 0));

        // Save button (only for text files)
        if (isTextFile) {
            JButton saveButton = new JButton("💾 Speichern");
            saveButton.setToolTipText("Änderungen speichern");
            saveButton.addActionListener(e -> saveIfApplicable());
            toolbar.add(saveButton);
            toolbar.addSeparator(new Dimension(8, 0));
        }

        // Copy Raw button
        JButton copyButton = new JButton("📋 Copy Raw");
        copyButton.setToolTipText("Raw-Inhalt kopieren");
        copyButton.addActionListener(this::copyRaw);
        toolbar.add(copyButton);

        toolbar.add(Box.createHorizontalGlue());

        // View Mode selector
        JLabel modeLabel = new JLabel("Ansicht:");
        toolbar.add(modeLabel);
        toolbar.add(Box.createHorizontalStrut(4));

        JComboBox<ViewMode> modeCombo = new JComboBox<>(ViewMode.values());
        modeCombo.setName("viewModeCombo");
        modeCombo.setSelectedItem(isTextFile ? ViewMode.SPLIT : ViewMode.RENDERED_ONLY);
        modeCombo.setMaximumSize(new Dimension(120, 28));
        modeCombo.addActionListener(e -> {
            ViewMode selected = (ViewMode) modeCombo.getSelectedItem();
            if (selected != null) {
                applyViewMode(selected);
            }
        });
        toolbar.add(modeCombo);

        toolbar.addSeparator(new Dimension(16, 0));

        // Sidebar toggle
        JToggleButton sidebarBtn = new JToggleButton("📊 Details");
        sidebarBtn.setName("sidebarToggle");
        sidebarBtn.setToolTipText("Datei-Details und Index-Status anzeigen");
        sidebarBtn.addActionListener(e -> toggleSidebar());
        toolbar.add(sidebarBtn);

        return toolbar;
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(new EmptyBorder(4, 8, 4, 8));
        statusBar.setBackground(new Color(245, 245, 245));

        StringBuilder status = new StringBuilder();
        if (metadata != null && metadata.getMimeType() != null) {
            status.append("Type: ").append(metadata.getMimeType());
        }
        if (warnings != null && !warnings.isEmpty()) {
            if (status.length() > 0) status.append(" | ");
            status.append("⚠ ").append(warnings.size()).append(" Warnung(en)");
        }

        JLabel statusLabel = new JLabel(status.toString());
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusBar.add(statusLabel, BorderLayout.WEST);

        return statusBar;
    }

    private void applyViewMode(ViewMode mode) {
        this.currentMode = mode;

        contentPanel.removeAll();

        switch (mode) {
            case SPLIT:
                splitPane.setLeftComponent(rawScrollPane);
                splitPane.setRightComponent(renderedScrollPane);
                splitPane.setDividerLocation(0.5);
                contentPanel.add(splitPane, BorderLayout.CENTER);
                break;
            case RENDERED_ONLY:
                contentPanel.add(renderedScrollPane, BorderLayout.CENTER);
                break;
            case RAW_ONLY:
                contentPanel.add(rawScrollPane, BorderLayout.CENTER);
                break;
        }

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private void toggleSidebar() {
        sidebarVisible = !sidebarVisible;
        sidebar.setVisible(sidebarVisible);

        if (sidebarVisible) {
            sidebar.refreshIndexStatus();
        }

        mainPanel.revalidate();
        mainPanel.repaint();
    }

    private void renderContent() {
        // Raw pane already has content from constructor

        // Render to HTML
        if (rawContent != null && !rawContent.isEmpty()) {
            try {
                String html = formatter.renderToHtml(rawContent);
                renderedPane.setText("<html><body>" + html + "</body></html>");
                SwingUtilities.invokeLater(() -> renderedPane.setCaretPosition(0));
            } catch (Exception e) {
                renderedPane.setText("<html><body><p style='color:red;'>Rendering-Fehler: " +
                        escapeHtml(e.getMessage()) + "</p></body></html>");
            }
        } else {
            renderedPane.setText("<html><body><p><i>Kein Inhalt</i></p></body></html>");
        }
    }

    private void updateSidebarInfo() {
        // File details
        Long fileSize = null;
        Long lastModified = null;

        if (sourcePath != null && !isRemote) {
            try {
                File file = new File(sourcePath);
                if (file.exists()) {
                    fileSize = file.length();
                    lastModified = file.lastModified();
                }
            } catch (Exception ignored) {}
        }

        sidebar.setFileDetails(
                sourceName,
                sourcePath,
                fileSize,
                metadata != null ? metadata.getMimeType() : null,
                "UTF-8", // Default assumption
                lastModified,
                isRemote
        );

        // Extraction info
        String extractorName = "Standard";
        if (metadata != null && metadata.getMimeType() != null) {
            String mime = metadata.getMimeType().toLowerCase();
            if (mime.contains("pdf")) extractorName = "PDFBox";
            else if (mime.contains("word") || mime.contains("docx")) extractorName = "docx4j";
            else if (mime.contains("excel") || mime.contains("xlsx")) extractorName = "Apache POI";
            else if (mime.startsWith("text/")) extractorName = "Plaintext";
        }
        sidebar.setExtractionInfo(extractorName, warnings);

        // Set document ID for index status
        if (document != null && document.getMetadata() != null) {
            sidebar.setDocumentId(sourcePath);
        }
    }

    private void copyRaw(ActionEvent e) {
        String content = rawPane.getText();
        if (content != null && !content.isEmpty()) {
            StringSelection selection = new StringSelection(content);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);

            JButton source = (JButton) e.getSource();
            String originalText = source.getText();
            source.setText("✓ Kopiert!");
            Timer timer = new Timer(1500, evt -> source.setText(originalText));
            timer.setRepeats(false);
            timer.start();
        }
    }


    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen - 3) + "..." : text;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // === ConnectionTab Interface ===

    @Override
    public String getTitle() {
        String prefix = hasUnsavedChanges ? "● " : "";
        return prefix + (isTextFile ? "📝 " : "📄 ") + truncate(sourceName, 25);
    }

    @Override
    public String getTooltip() {
        return sourcePath != null ? sourcePath : sourceName;
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    @Override
    public void onClose() {
        // Could prompt for unsaved changes
    }

    @Override
    public void saveIfApplicable() {
        if (!isTextFile || !hasUnsavedChanges) return;

        // For text files, save the raw content
        // In a real implementation, this would write to file
        rawContent = rawPane.getText();
        hasUnsavedChanges = false;

        // Mark index as stale - TODO: trigger re-indexing here when implemented
        // if (sourcePath != null) {
        //     ragService.markDocumentStale(sourcePath);
        // }

        JOptionPane.showMessageDialog(this,
                "Datei gespeichert (simuliert).\nIn der vollständigen Implementierung würde hier die Datei geschrieben.",
                "Speichern", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public String getContent() {
        // Return content based on current view mode (for attachment)
        if (currentMode == ViewMode.RENDERED_ONLY) {
            // Return rendered/extracted content
            return rawContent; // In reality, this would be the processed content
        }
        // For SPLIT or RAW_ONLY, return raw
        return rawPane.getText();
    }

    @Override
    public void markAsChanged() {
        hasUnsavedChanges = true;
    }

    @Override
    public String getPath() {
        return sourcePath;
    }

    @Override
    public Type getType() {
        return Type.PREVIEW;
    }

    @Override
    public void focusSearchField() {
        // Could implement search
    }

    @Override
    public void searchFor(String searchPattern) {
        // Could implement search
    }

    // === DocumentPreviewTabAdapter Interface ===

    @Override
    public Document getDocument() {
        return document;
    }

    @Override
    public DocumentMetadata getMetadata() {
        return metadata;
    }

    @Override
    public List<String> getWarnings() {
        return warnings;
    }

    // === Public API ===

    /**
     * Get the current view mode.
     */
    public ViewMode getViewMode() {
        return currentMode;
    }

    /**
     * Check if raw content is currently visible.
     */
    public boolean isRawVisible() {
        return currentMode == ViewMode.SPLIT || currentMode == ViewMode.RAW_ONLY;
    }

    /**
     * Get raw content for indexing.
     * For text files, returns raw. For binary formats, returns extracted text.
     */
    public String getContentForIndexing() {
        return rawContent;
    }

    /**
     * Check if this is a text file.
     */
    public boolean isTextFile() {
        return isTextFile;
    }
}

