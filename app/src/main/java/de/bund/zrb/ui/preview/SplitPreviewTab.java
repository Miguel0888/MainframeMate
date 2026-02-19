package de.bund.zrb.ui.preview;

import de.bund.zrb.chat.attachment.AttachTabToChatUseCase;
import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;
import de.bund.zrb.ingestion.ui.ChatMarkdownFormatter;
import de.zrb.bund.newApi.ui.ConnectionTab;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enhanced preview/editor tab with split view (Raw + Rendered), view mode toggle,
 * and optional sidebar showing file details and index status.
 *
 * Architecture:
 * - RAW view: RSyntaxTextArea WITHOUT syntax highlighting (plain text view)
 * - RENDERED view depends on file type:
 *   - For source code files: RSyntaxTextArea WITH syntax highlighting (same content, just highlighted)
 *   - For documents (MD, HTML): JEditorPane with HTML/Markdown rendering
 *   - For binary docs (PDF, DOCX): JEditorPane showing extracted text as HTML
 * - Default mode: RENDERED_ONLY
 *
 * IMPORTANT: Data integrity is preserved - highlighting never changes the actual content.
 */
public class SplitPreviewTab extends JPanel implements ConnectionTab, AttachTabToChatUseCase.DocumentPreviewTabAdapter {

    // Source code extensions - use RSyntaxTextArea with highlighting for RENDERED view
    protected static final Set<String> SOURCE_CODE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "java", "py", "js", "ts", "c", "cpp", "h", "hpp", "go", "rb", "php",
            "sql", "sh", "bash", "bat", "cmd", "ps1", "groovy", "gradle", "scala",
            "kotlin", "kt", "lua", "perl", "pl", "json", "xml", "yml", "yaml",
            "properties", "ini", "csv", "css",
            // Mainframe languages
            "jcl", "proc", "prc", "cbl", "cob", "cobol"
    ));

    // Document extensions that need HTML rendering (Markdown, etc.)
    protected static final Set<String> HTML_RENDERED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "md", "markdown", "html", "htm"
    ));

    // Binary document formats that only support RENDERED view (extracted text shown as HTML)
    protected static final Set<String> BINARY_DOCUMENT_EXTENSIONS = new HashSet<>(Arrays.asList(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp"
    ));

    // Text file extensions (includes source code and plain text)
    protected static final Set<String> TEXT_EXTENSIONS = new HashSet<>(Arrays.asList(
            "txt", "md", "json", "yml", "yaml", "properties", "ini", "conf", "cfg",
            "xml", "csv", "log", "java", "py", "js", "ts", "html", "css", "sql",
            "sh", "bat", "ps1", "rb", "php", "c", "cpp", "h", "hpp", "go",
            // Mainframe languages
            "jcl", "proc", "prc", "cbl", "cob", "cobol"
    ));

    // Extension to RSyntaxTextArea syntax style mapping
    private static final Map<String, String> SYNTAX_STYLES = new HashMap<>();
    static {
        SYNTAX_STYLES.put("java", SyntaxConstants.SYNTAX_STYLE_JAVA);
        SYNTAX_STYLES.put("py", SyntaxConstants.SYNTAX_STYLE_PYTHON);
        SYNTAX_STYLES.put("js", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        SYNTAX_STYLES.put("ts", SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT);
        SYNTAX_STYLES.put("json", SyntaxConstants.SYNTAX_STYLE_JSON);
        SYNTAX_STYLES.put("xml", SyntaxConstants.SYNTAX_STYLE_XML);
        SYNTAX_STYLES.put("html", SyntaxConstants.SYNTAX_STYLE_HTML);
        SYNTAX_STYLES.put("htm", SyntaxConstants.SYNTAX_STYLE_HTML);
        SYNTAX_STYLES.put("css", SyntaxConstants.SYNTAX_STYLE_CSS);
        SYNTAX_STYLES.put("sql", SyntaxConstants.SYNTAX_STYLE_SQL);
        SYNTAX_STYLES.put("sh", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
        SYNTAX_STYLES.put("bash", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
        SYNTAX_STYLES.put("bat", SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH);
        SYNTAX_STYLES.put("cmd", SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH);
        SYNTAX_STYLES.put("ps1", SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH);
        SYNTAX_STYLES.put("rb", SyntaxConstants.SYNTAX_STYLE_RUBY);
        SYNTAX_STYLES.put("php", SyntaxConstants.SYNTAX_STYLE_PHP);
        SYNTAX_STYLES.put("c", SyntaxConstants.SYNTAX_STYLE_C);
        SYNTAX_STYLES.put("cpp", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS);
        SYNTAX_STYLES.put("h", SyntaxConstants.SYNTAX_STYLE_C);
        SYNTAX_STYLES.put("hpp", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS);
        SYNTAX_STYLES.put("go", SyntaxConstants.SYNTAX_STYLE_GO);
        SYNTAX_STYLES.put("yml", SyntaxConstants.SYNTAX_STYLE_YAML);
        SYNTAX_STYLES.put("yaml", SyntaxConstants.SYNTAX_STYLE_YAML);
        SYNTAX_STYLES.put("md", SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
        SYNTAX_STYLES.put("markdown", SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
        SYNTAX_STYLES.put("properties", SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE);
        SYNTAX_STYLES.put("ini", SyntaxConstants.SYNTAX_STYLE_INI);
        SYNTAX_STYLES.put("csv", SyntaxConstants.SYNTAX_STYLE_CSV);
        SYNTAX_STYLES.put("groovy", SyntaxConstants.SYNTAX_STYLE_GROOVY);
        SYNTAX_STYLES.put("gradle", SyntaxConstants.SYNTAX_STYLE_GROOVY);
        SYNTAX_STYLES.put("scala", SyntaxConstants.SYNTAX_STYLE_SCALA);
        SYNTAX_STYLES.put("kotlin", SyntaxConstants.SYNTAX_STYLE_KOTLIN);
        SYNTAX_STYLES.put("kt", SyntaxConstants.SYNTAX_STYLE_KOTLIN);
        SYNTAX_STYLES.put("lua", SyntaxConstants.SYNTAX_STYLE_LUA);
        SYNTAX_STYLES.put("perl", SyntaxConstants.SYNTAX_STYLE_PERL);
        SYNTAX_STYLES.put("pl", SyntaxConstants.SYNTAX_STYLE_PERL);
        // Mainframe languages
        SYNTAX_STYLES.put("cbl", SyntaxConstants.SYNTAX_STYLE_NONE); // COBOL - no native support
        SYNTAX_STYLES.put("cob", SyntaxConstants.SYNTAX_STYLE_NONE);
        SYNTAX_STYLES.put("cobol", SyntaxConstants.SYNTAX_STYLE_NONE);
        SYNTAX_STYLES.put("jcl", SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE); // JCL - use properties as approximation
        SYNTAX_STYLES.put("proc", SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE); // JCL PROC
        SYNTAX_STYLES.put("prc", SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE);  // JCL PROC variant
    }

    // JCL detection patterns - JCL typically starts with // in columns 1-2
    private static final String JCL_PATTERN_START = "//";
    private static final String[] JCL_KEYWORDS = {"JOB", "EXEC", "DD", "PROC", "PEND", "SET", "JCLLIB", "INCLUDE"};

    protected final String sourceName;
    protected final String sourcePath;
    protected String rawContent;
    protected final DocumentMetadata metadata;
    protected final List<String> warnings;
    protected final Document document;
    protected final boolean isTextFile;
    protected final boolean isRemote;
    protected final String syntaxStyle;
    protected final boolean isSourceCode;       // Source code files use RSyntaxTextArea for rendering
    protected final boolean needsHtmlRendering; // MD/HTML/Binary docs use JEditorPane for rendering
    protected final ChatMarkdownFormatter markdownFormatter;

    // UI Components
    protected final JPanel mainPanel;
    protected final RSyntaxTextArea rawPane;              // RSyntaxTextArea for RAW view (no highlighting)
    protected final JEditorPane htmlRenderedPane;         // HTML rendering for MD/Binary documents
    protected final JSplitPane splitPane;
    protected final RTextScrollPane rawScrollPane;
    protected final JScrollPane htmlRenderedScrollPane;
    protected final IndexStatusSidebar sidebar;
    protected final JPanel contentPanel;

    // State
    protected ViewMode currentMode;
    protected boolean sidebarVisible = false;
    protected boolean hasUnsavedChanges = false;

    public SplitPreviewTab(String sourceName, String rawContent, DocumentMetadata metadata,
                           List<String> warnings, Document document, boolean isRemote) {
        this.sourceName = sourceName;
        this.sourcePath = metadata != null ? metadata.getSourceName() : sourceName;
        this.rawContent = rawContent != null ? rawContent : "";
        this.metadata = metadata;
        this.warnings = warnings;
        this.document = document;
        this.isRemote = isRemote;
        this.isTextFile = determineIfTextFile(sourceName, metadata);
        this.syntaxStyle = detectSyntaxStyle(sourceName);
        this.isSourceCode = isSourceCodeFile(sourceName);
        this.needsHtmlRendering = needsHtmlRendering(sourceName, metadata);
        this.markdownFormatter = ChatMarkdownFormatter.getInstance();

        setLayout(new BorderLayout());

        // Create RAW pane (RSyntaxTextArea WITHOUT highlighting)
        this.rawPane = createRawPane();
        this.rawScrollPane = new RTextScrollPane(rawPane);

        // Create HTML rendered pane for documents that need it
        this.htmlRenderedPane = createHtmlRenderedPane();
        this.htmlRenderedScrollPane = new JScrollPane(htmlRenderedPane);

        // Create split pane
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

        // Set default mode: RENDERED_ONLY
        this.currentMode = ViewMode.RENDERED_ONLY;
        applyViewMode(currentMode);

        // Populate sidebar
        updateSidebarInfo();

        // Render HTML content if needed
        if (needsHtmlRendering) {
            renderHtmlContent();
        }
    }

    /**
     * Check if file is source code (uses RSyntaxTextArea with highlighting for RENDERED view)
     * Also detects Mainframe languages (JCL, COBOL) by content analysis.
     */
    protected boolean isSourceCodeFile(String name) {
        String ext = getExtension(name);
        if (ext != null && SOURCE_CODE_EXTENSIONS.contains(ext)) {
            return true;
        }
        // For files without extension (common on Mainframe), check content
        if (rawContent != null && !rawContent.isEmpty()) {
            return isJclContent(rawContent) || isCobolContent(rawContent);
        }
        return false;
    }

    /**
     * Check if file needs HTML rendering (MD, HTML, or binary documents)
     */
    protected boolean needsHtmlRendering(String name, DocumentMetadata meta) {
        String ext = getExtension(name);
        if (ext != null && (HTML_RENDERED_EXTENSIONS.contains(ext) || BINARY_DOCUMENT_EXTENSIONS.contains(ext))) {
            return true;
        }
        // Also check MIME type for binary documents
        if (meta != null && meta.getMimeType() != null) {
            String mime = meta.getMimeType().toLowerCase();
            if (mime.contains("pdf") || mime.contains("msword") || mime.contains("officedocument")) {
                return true;
            }
        }
        return false;
    }

    protected String getExtension(String name) {
        if (name == null) return null;
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(dotIndex + 1).toLowerCase() : null;
    }

    protected boolean determineIfTextFile(String name, DocumentMetadata metadata) {
        // Check MIME type first
        if (metadata != null && metadata.getMimeType() != null) {
            String mime = metadata.getMimeType().toLowerCase();
            if (mime.startsWith("text/")) return true;
            if (mime.contains("json") || mime.contains("xml") || mime.contains("yaml")) return true;
            if (mime.contains("pdf") || mime.contains("msword") || mime.contains("officedocument")) return false;
        }

        // Fallback to extension
        String ext = getExtension(name);
        if (ext != null) {
            if (BINARY_DOCUMENT_EXTENSIONS.contains(ext)) return false;
            if (TEXT_EXTENSIONS.contains(ext) || SOURCE_CODE_EXTENSIONS.contains(ext)) return true;
        }

        // Default to text
        return true;
    }

    /**
     * Detect RSyntaxTextArea syntax style based on file extension.
     */
    protected String detectSyntaxStyle(String name) {
        String ext = getExtension(name);
        if (ext != null) {
            String style = SYNTAX_STYLES.get(ext);
            if (style != null) return style;
        }
        // No extension match - try content-based detection
        return detectSyntaxStyleByContent();
    }

    /**
     * Detect syntax style by analyzing file content.
     * Used for Mainframe files without extensions (JCL, COBOL, etc.)
     */
    protected String detectSyntaxStyleByContent() {
        if (rawContent == null || rawContent.isEmpty()) {
            return SyntaxConstants.SYNTAX_STYLE_NONE;
        }

        // Check for JCL: lines starting with // followed by JCL keywords
        if (isJclContent(rawContent)) {
            return SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE; // Best available approximation
        }

        // Check for COBOL: look for IDENTIFICATION DIVISION, PROCEDURE DIVISION, etc.
        if (isCobolContent(rawContent)) {
            return SyntaxConstants.SYNTAX_STYLE_NONE; // No native COBOL support
        }

        return SyntaxConstants.SYNTAX_STYLE_NONE;
    }

    /**
     * Detect if content is JCL (Job Control Language).
     * JCL characteristics:
     * - Lines start with // in columns 1-2
     * - Contains JCL keywords like JOB, EXEC, DD, PROC
     * - Comments start with //*
     */
    protected boolean isJclContent(String content) {
        if (content == null || content.length() < 3) return false;

        String[] lines = content.split("\\r?\\n", 20); // Check first 20 lines
        int jclLineCount = 0;
        int jclKeywordCount = 0;

        for (String line : lines) {
            if (line.startsWith(JCL_PATTERN_START)) {
                jclLineCount++;
                // Check for JCL keywords
                String upperLine = line.toUpperCase();
                for (String keyword : JCL_KEYWORDS) {
                    if (upperLine.contains(" " + keyword + " ") ||
                        upperLine.contains(" " + keyword + ",") ||
                        upperLine.contains("//" + keyword + " ")) {
                        jclKeywordCount++;
                        break;
                    }
                }
            }
        }

        // Consider it JCL if most lines start with // and at least one JCL keyword found
        return jclLineCount >= 2 && jclKeywordCount >= 1;
    }

    /**
     * Detect if content is COBOL.
     */
    protected boolean isCobolContent(String content) {
        if (content == null) return false;
        String upper = content.toUpperCase();
        return upper.contains("IDENTIFICATION DIVISION") ||
               upper.contains("PROCEDURE DIVISION") ||
               upper.contains("DATA DIVISION") ||
               upper.contains("WORKING-STORAGE SECTION");
    }

    /**
     * Create RAW pane - RSyntaxTextArea WITHOUT syntax highlighting.
     * This is the plain text view that shows content exactly as-is.
     */
    protected RSyntaxTextArea createRawPane() {
        RSyntaxTextArea area = new RSyntaxTextArea();
        area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE); // NO highlighting for RAW
        area.setCodeFoldingEnabled(false);
        area.setFont(new Font("Consolas", Font.PLAIN, 13));
        area.setEditable(isTextFile);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setText(rawContent);
        area.setCaretPosition(0);

        // Track changes
        area.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { onRawContentChanged(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { onRawContentChanged(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { onRawContentChanged(); }
        });

        return area;
    }

    protected void onRawContentChanged() {
        if (isTextFile) {
            hasUnsavedChanges = true;
            rawContent = rawPane.getText();
            // Re-render HTML if needed
            if (needsHtmlRendering && currentMode != ViewMode.RAW_ONLY) {
                renderHtmlContent();
            }
        }
    }

    /**
     * Create HTML rendered pane for documents (Markdown, binary docs).
     * This is used when the file needs actual HTML rendering (not just syntax highlighting).
     */
    protected JEditorPane createHtmlRenderedPane() {
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

    /**
     * Render content to HTML (for Markdown and document files).
     */
    protected void renderHtmlContent() {
        if (rawContent != null && !rawContent.isEmpty()) {
            try {
                String html = markdownFormatter.renderToHtml(rawContent);
                htmlRenderedPane.setText("<html><body>" + html + "</body></html>");
                SwingUtilities.invokeLater(() -> htmlRenderedPane.setCaretPosition(0));
            } catch (Exception e) {
                htmlRenderedPane.setText("<html><body><p style='color:red;'>Rendering-Fehler: " +
                        escapeHtml(e.getMessage()) + "</p></body></html>");
            }
        } else {
            htmlRenderedPane.setText("<html><body><p><i>Kein Inhalt</i></p></body></html>");
        }
    }

    /**
     * Create a RSyntaxTextArea with syntax highlighting for the RENDERED view of source code files.
     */
    protected RSyntaxTextArea createHighlightedSourcePane() {
        RSyntaxTextArea area = new RSyntaxTextArea();
        area.setSyntaxEditingStyle(syntaxStyle); // WITH highlighting
        area.setCodeFoldingEnabled(true);
        area.setFont(new Font("Consolas", Font.PLAIN, 13));
        area.setEditable(isTextFile);
        area.setLineWrap(false);
        area.setWrapStyleWord(false);
        area.setText(rawContent);
        area.setCaretPosition(0);

        // Sync changes back to rawContent and rawPane
        area.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { syncFromHighlighted(area); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { syncFromHighlighted(area); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { syncFromHighlighted(area); }
        });

        return area;
    }

    protected void syncFromHighlighted(RSyntaxTextArea highlightedArea) {
        if (isTextFile) {
            hasUnsavedChanges = true;
            rawContent = highlightedArea.getText();
            // Sync to raw pane
            if (rawPane != null && !rawPane.getText().equals(rawContent)) {
                SwingUtilities.invokeLater(() -> {
                    int caretPos = rawPane.getCaretPosition();
                    rawPane.setText(rawContent);
                    try {
                        rawPane.setCaretPosition(Math.min(caretPos, rawContent.length()));
                    } catch (Exception ignored) {}
                });
            }
        }
    }


    protected JSplitPane createSplitPane() {
        // Initial setup - will be reconfigured by applyViewMode
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setLeftComponent(rawScrollPane);

        // Right component depends on file type
        if (needsHtmlRendering) {
            split.setRightComponent(htmlRenderedScrollPane);
        } else if (isSourceCode) {
            // For source code: right side will have highlighted pane (created in applyViewMode)
            split.setRightComponent(new RTextScrollPane(createHighlightedSourcePane()));
        } else {
            // Plain text files - just use raw pane on both sides initially
            split.setRightComponent(rawScrollPane);
        }

        split.setResizeWeight(0.5);
        split.setDividerLocation(0.5);
        split.setOneTouchExpandable(true);
        return split;
    }

    protected JToolBar createToolbar() {
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
        // Default is RENDERED_ONLY
        modeCombo.setSelectedItem(ViewMode.RENDERED_ONLY);
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

    protected JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(new EmptyBorder(4, 8, 4, 8));
        statusBar.setBackground(new Color(245, 245, 245));

        StringBuilder status = new StringBuilder();
        if (metadata != null && metadata.getMimeType() != null) {
            status.append("Type: ").append(metadata.getMimeType());
        }
        if (isSourceCode) {
            if (status.length() > 0) status.append(" | ");
            status.append("💻 Quellcode");
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

    /**
     * Apply view mode - key method that handles switching between views.
     *
     * For source code files:
     * - RAW_ONLY: RSyntaxTextArea WITHOUT highlighting
     * - RENDERED_ONLY: RSyntaxTextArea WITH syntax highlighting
     * - SPLIT: Both side by side
     *
     * For documents (MD, HTML, PDF, DOCX):
     * - RAW_ONLY: RSyntaxTextArea WITHOUT highlighting (plain text)
     * - RENDERED_ONLY: JEditorPane with HTML rendering
     * - SPLIT: Both side by side
     */
    protected void applyViewMode(ViewMode mode) {
        this.currentMode = mode;
        contentPanel.removeAll();

        switch (mode) {
            case SPLIT:
                splitPane.setLeftComponent(rawScrollPane);
                if (needsHtmlRendering) {
                    // Documents use HTML rendering on the right
                    splitPane.setRightComponent(htmlRenderedScrollPane);
                } else if (isSourceCode) {
                    // Source code uses highlighted RSyntaxTextArea on the right
                    splitPane.setRightComponent(new RTextScrollPane(createHighlightedSourcePane()));
                } else {
                    // Plain text - just raw on both sides (no real split benefit)
                    splitPane.setRightComponent(rawScrollPane);
                }
                splitPane.setDividerLocation(0.5);
                contentPanel.add(splitPane, BorderLayout.CENTER);
                break;

            case RENDERED_ONLY:
                if (needsHtmlRendering) {
                    // Documents show HTML rendering
                    contentPanel.add(htmlRenderedScrollPane, BorderLayout.CENTER);
                } else if (isSourceCode) {
                    // Source code: use rawPane but WITH highlighting applied
                    rawPane.setSyntaxEditingStyle(syntaxStyle);
                    rawPane.setCodeFoldingEnabled(true);
                    contentPanel.add(rawScrollPane, BorderLayout.CENTER);
                } else {
                    // Plain text - just show raw pane (no highlighting available)
                    contentPanel.add(rawScrollPane, BorderLayout.CENTER);
                }
                break;

            case RAW_ONLY:
                // Always show RAW without highlighting
                rawPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
                rawPane.setCodeFoldingEnabled(false);
                contentPanel.add(rawScrollPane, BorderLayout.CENTER);
                break;
        }

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    protected void toggleSidebar() {
        sidebarVisible = !sidebarVisible;
        sidebar.setVisible(sidebarVisible);

        if (sidebarVisible) {
            sidebar.refreshIndexStatus();
        }

        mainPanel.revalidate();
        mainPanel.repaint();
    }


    protected void updateSidebarInfo() {
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

    protected void copyRaw(ActionEvent e) {
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


    protected String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen - 3) + "..." : text;
    }

    protected String escapeHtml(String text) {
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

    /**
     * Get the raw pane (RSyntaxTextArea) for direct access.
     */
    public RSyntaxTextArea getRawPane() {
        return rawPane;
    }

    /**
     * Check if file is source code.
     */
    public boolean isSourceCode() {
        return isSourceCode;
    }

    /**
     * Set content programmatically.
     */
    public void setContent(String content) {
        this.rawContent = content != null ? content : "";
        rawPane.setText(this.rawContent);
        rawPane.setCaretPosition(0);
        if (needsHtmlRendering) {
            renderHtmlContent();
        }
    }

    /**
     * Get current syntax style.
     */
    public String getSyntaxStyle() {
        return syntaxStyle;
    }
}
