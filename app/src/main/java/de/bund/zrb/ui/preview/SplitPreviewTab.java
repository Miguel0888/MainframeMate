package de.bund.zrb.ui.preview;


import de.bund.zrb.chat.attachment.AttachTabToChatUseCase;
import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;
import de.bund.zrb.ingestion.ui.ChatMarkdownFormatter;
import de.bund.zrb.rag.service.RagService;
import de.zrb.bund.newApi.ui.ConnectionTab;
import de.zrb.bund.newApi.ui.FindBarPanel;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
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
 * - RENDERED view depends on file typSchluessel:
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
    protected final String backendType; // e.g. "LOCAL", "FTP", "NDV", "MAIL", "WEB", "BETAVIEW"
    protected final String syntaxStyle;
    protected boolean isSourceCode;       // Source code files use RSyntaxTextArea for rendering
    protected boolean needsHtmlRendering; // MD/HTML/Binary docs use JEditorPane for rendering
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
    protected final FindBarPanel findBar;                 // Orange find-in-document bar

    // Highlight painter for find-in-document matches
    private static final Highlighter.HighlightPainter YELLOW_PAINTER =
            new DefaultHighlighter.DefaultHighlightPainter(new Color(0xFF, 0xEB, 0x3B, 180));

    // State
    protected ViewMode currentMode;
    protected boolean sidebarVisible = false;
    protected boolean hasUnsavedChanges = false;
    protected String activeFileType = null; // currently selected file type (null = sentence type or none)
    protected JButton saveButton; // Save/Download button (can change label dynamically)
    protected JButton uploadButton; // Upload button (visible only when in download/binary mode)
    protected byte[] rawBytes = null; // Binary content for download (set when reloaded as binary)
    protected JToolBar toolbar; // Toolbar reference for subclass extension

    // Editing action buttons (in toolbar, only visible for editable text files)
    protected final JToggleButton compareButton;
    protected final JButton undoButton;
    protected final JButton redoButton;
    /** true when the raw content is editable (text file, not BetaView read-only) */
    protected final boolean isEditable;

    public SplitPreviewTab(String sourceName, String rawContent, DocumentMetadata metadata,
                           List<String> warnings, Document document, boolean isRemote) {
        this(sourceName, rawContent, metadata, warnings, document, isRemote,
                isRemote ? "FTP" : "LOCAL");
    }

    public SplitPreviewTab(String sourceName, String rawContent, DocumentMetadata metadata,
                           List<String> warnings, Document document, boolean isRemote,
                           String backendType) {
        this.sourceName = sourceName;
        this.sourcePath = metadata != null ? metadata.getSourceName() : sourceName;
        this.rawContent = rawContent != null ? rawContent : "";
        this.metadata = metadata;
        this.warnings = warnings;
        this.document = document;
        this.isRemote = isRemote;
        this.backendType = backendType != null ? backendType : (isRemote ? "FTP" : "LOCAL");
        this.isTextFile = determineIfTextFile(sourceName, metadata);
        this.syntaxStyle = detectSyntaxStyle(sourceName);
        this.isSourceCode = isSourceCodeFile(sourceName);
        this.needsHtmlRendering = needsHtmlRendering(sourceName, metadata);
        this.markdownFormatter = ChatMarkdownFormatter.getInstance();
        this.isEditable = isTextFile && !"BETAVIEW".equals(this.backendType);

        // Editing action buttons — only shown for editable text files
        this.compareButton = createIconToggleButton("\uD83D\uDD00", "Vergleichen");  // 🔀
        this.undoButton    = createIconButton("↶", "Rückgängig");
        this.redoButton    = createIconButton("↷", "Wiederholen");

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
        this.toolbar = createToolbar();

        // Content panel (holds split/single view)
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(splitPane, BorderLayout.CENTER);

        // FindBarPanel (orange) for in-document search
        this.findBar = new FindBarPanel("Im Dokument suchen\u2026");
        findBar.addSearchAction(e -> highlightFindMatches());

        // Main panel assembly
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        mainPanel.add(findBar, BorderLayout.SOUTH);

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
            return isJclContent(rawContent) || isCobolContent(rawContent) || isNaturalContent(rawContent);
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
        // Also check MIME typSchluessel for binary documents
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
        // Check MIME typSchluessel first
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

        // Check for Natural: DEFINE DATA, CALLNAT, END-DEFINE, etc.
        if (isNaturalContent(rawContent)) {
            return de.bund.zrb.ui.syntax.MainframeSyntaxSupport.SYNTAX_STYLE_NATURAL;
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
     * Detect if content is Natural (Software AG).
     */
    protected boolean isNaturalContent(String content) {
        if (content == null) return false;
        String[] lines = content.split("\\r?\\n", 40);
        int naturalHits = 0;
        for (String line : lines) {
            String trimmed = line.trim().toUpperCase();
            if (trimmed.startsWith("DEFINE DATA")
                    || trimmed.startsWith("END-DEFINE")
                    || trimmed.startsWith("DEFINE SUBROUTINE")
                    || trimmed.startsWith("CALLNAT ")
                    || trimmed.startsWith("END-SUBROUTINE")
                    || trimmed.startsWith("LOCAL USING")
                    || trimmed.startsWith("PARAMETER USING")
                    || trimmed.startsWith("DECIDE ON")
                    || trimmed.startsWith("DECIDE FOR")
                    || trimmed.startsWith("INPUT USING MAP")
                    || trimmed.startsWith("FETCH RETURN")) {
                naturalHits++;
            }
        }
        return naturalHits >= 2;
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
        area.setEditable(isTextFile && !"BETAVIEW".equals(backendType));
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
     * For PDFs with available raw bytes, delegates to {@link #renderPdfPages(byte[])}.
     */
    protected void renderHtmlContent() {
        // If we already have binary PDF bytes, render actual pages instead of extracted text
        if (rawBytes != null && isPdf()) {
            renderPdfPages(rawBytes);
            return;
        }

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
     * Render PDF pages visually using PDFBox and display them in the rendered scroll pane.
     * This replaces the JEditorPane with a {@link PdfPagesPanel} showing actual page images.
     *
     * @param pdfBytes the raw PDF document bytes
     */
    protected void renderPdfPages(byte[] pdfBytes) {
        PdfPagesPanel pagesPanel = new PdfPagesPanel();
        htmlRenderedScrollPane.setViewportView(pagesPanel);
        pagesPanel.loadAsync(pdfBytes);
    }

    /**
     * Check whether this tab's document is a PDF (by extension or MIME type).
     */
    protected boolean isPdf() {
        String ext = getExtension(sourceName);
        if ("pdf".equalsIgnoreCase(ext)) return true;
        if (metadata != null && metadata.getMimeType() != null) {
            return metadata.getMimeType().toLowerCase().contains("pdf");
        }
        return false;
    }

    /**
     * Set raw binary bytes and, if this is a PDF, re-render as actual pages.
     *
     * @param bytes the binary file bytes
     */
    public void setRawBytes(byte[] bytes) {
        this.rawBytes = bytes;
        if (bytes != null && isPdf() && needsHtmlRendering) {
            renderPdfPages(bytes);
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

        // Right component depends on file typSchluessel
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

    /** Create a small icon-style toolbar button. */
    private static JButton createIconButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setToolTipText(tooltip);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 18f));
        btn.setMargin(new Insets(0, 4, 0, 4));
        btn.setFocusable(false);
        return btn;
    }

    /** Create a small icon-style toolbar toggle button. */
    private static JToggleButton createIconToggleButton(String text, String tooltip) {
        JToggleButton btn = new JToggleButton(text);
        btn.setToolTipText(tooltip);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 18f));
        btn.setMargin(new Insets(0, 4, 0, 4));
        btn.setFocusable(false);
        return btn;
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

        // Save/Download button
        saveButton = new JButton("💾 Speichern");
        saveButton.setToolTipText("Änderungen speichern");
        saveButton.addActionListener(e -> {
            if (activeFileType != null && needsHtmlRendering) {
                downloadContent();
            } else {
                saveIfApplicable();
            }
        });
        if (!isTextFile && !needsHtmlRendering) {
            saveButton.setVisible(false); // Hide if neither text nor renderable
        }
        toolbar.add(saveButton);

        // Upload button (visible only when in download/binary mode)
        uploadButton = new JButton("📤 Hochladen");
        uploadButton.setToolTipText("Lokale Datei hochladen und die Remote-Datei ersetzen");
        uploadButton.setVisible(false);
        uploadButton.addActionListener(e -> uploadContent());
        toolbar.add(uploadButton);

        toolbar.addSeparator(new Dimension(8, 0));

        // Copy Raw button
        JButton copyButton = new JButton("📋 Copy Raw");
        copyButton.setToolTipText("Raw-Inhalt kopieren");
        copyButton.addActionListener(this::copyRaw);
        toolbar.add(copyButton);

        toolbar.add(Box.createHorizontalGlue());

        // ── Editable: Undo / Redo / Compare ──
        if (isEditable) {
            toolbar.add(undoButton);
            toolbar.add(Box.createHorizontalStrut(2));
            toolbar.add(redoButton);
            toolbar.addSeparator(new Dimension(8, 0));
            toolbar.add(compareButton);
            toolbar.addSeparator(new Dimension(8, 0));
        }

        // View Mode selector (only for non-editable content — editable files are always raw text)
        if (!isEditable) {
            JLabel modeLabel = new JLabel("Ansicht:");
            toolbar.add(modeLabel);
            toolbar.add(Box.createHorizontalStrut(4));

            JComboBox<ViewMode> modeCombo = new JComboBox<>(ViewMode.values());
            modeCombo.setName("viewModeCombo");
            modeCombo.setSelectedItem(ViewMode.RENDERED_ONLY);
            modeCombo.setMaximumSize(new Dimension(120, 28));
            modeCombo.addActionListener(e -> {
                ViewMode selected = (ViewMode) modeCombo.getSelectedItem();
                if (selected != null) {
                    applyViewMode(selected);
                }
            });
            toolbar.add(modeCombo);

            toolbar.addSeparator(new Dimension(8, 0));
        }

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
                backendType
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

        // Set document ID for index status — use sourcePath as universal document ID
        String docId = sourcePath != null ? sourcePath : sourceName;
        sidebar.setDocumentId(docId);

        // Wire the "Index Now" button
        sidebar.setIndexAction(() -> indexCurrentContent(docId));
    }

    /**
     * Index the current content via RagService. Works for any source type.
     */
    private void indexCurrentContent(String docId) {
        if (docId == null || docId.isEmpty()) return;

        String content = rawPane.getText();
        if (content == null || content.trim().isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Kein Inhalt zum Indexieren vorhanden.",
                    "Indexierung", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            RagService ragService = RagService.getInstance();
            // Build a minimal Document from the current content if none exists
            Document doc = this.document;
            if (doc == null) {
                DocumentMetadata meta = DocumentMetadata.builder()
                        .sourceName(docId)
                        .mimeType("text/plain")
                        .build();
                doc = Document.fromText(content, meta);
            }
            ragService.indexDocument(docId, sourceName != null ? sourceName : docId, doc);
            sidebar.refreshIndexStatus();
            JOptionPane.showMessageDialog(mainPanel,
                    "Dokument wurde erfolgreich indexiert.\nID: " + docId,
                    "Indexierung erfolgreich", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Indexierung fehlgeschlagen:\n" + ex.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
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
        // BetaView documents are read-only
        if ("BETAVIEW".equals(backendType)) return;

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
        findBar.focusAndSelectAll();
    }

    @Override
    public void searchFor(String searchPattern) {
        if (searchPattern == null || searchPattern.trim().isEmpty()) return;
        findBar.setText(searchPattern.trim());
        highlightFindMatches();
    }

    /**
     * Highlight all occurrences of the find bar query in the currently visible pane(s).
     * For binary documents (PDF, DOCX, etc.) only the HTML rendered pane is searched
     * because the raw pane contains meaningless binary data.
     */
    protected void highlightFindMatches() {
        String query = findBar.getText().trim();

        if (!isTextFile && needsHtmlRendering) {
            // Binary document: rawPane has garbage, only search htmlRenderedPane
            highlightInTextComponent(rawPane, ""); // clear old highlights
            highlightInTextComponent(htmlRenderedPane, query);
        } else if (needsHtmlRendering) {
            // Text-based HTML (Markdown, HTML) — search both panes
            highlightInTextComponent(rawPane, query);
            highlightInTextComponent(htmlRenderedPane, query);
        } else {
            // Plain text / source code — only rawPane
            highlightInTextComponent(rawPane, query);
        }
    }

    /**
     * Highlight all case-insensitive occurrences of {@code query} in the given text component,
     * scrolling to the first match.
     */
    protected void highlightInTextComponent(javax.swing.text.JTextComponent comp, String query) {
        Highlighter highlighter = comp.getHighlighter();
        highlighter.removeAllHighlights();

        if (query.isEmpty()) return;

        try {
            javax.swing.text.Document doc = comp.getDocument();
            String fullText = doc.getText(0, doc.getLength());
            String lowerText = fullText.toLowerCase();
            String lowerQuery = query.toLowerCase();

            int firstHit = -1;
            int pos = 0;

            while (pos < lowerText.length()) {
                int idx = lowerText.indexOf(lowerQuery, pos);
                if (idx < 0) break;

                int end = idx + query.length();
                highlighter.addHighlight(idx, end, YELLOW_PAINTER);

                if (firstHit < 0) {
                    firstHit = idx;
                }
                pos = end;
            }

            // Scroll to first match
            if (firstHit >= 0) {
                comp.setCaretPosition(firstHit);
                if (comp instanceof JEditorPane) {
                    Rectangle rect = comp.modelToView(firstHit);
                    if (rect != null) {
                        comp.scrollRectToVisible(rect);
                    }
                }
            }
        } catch (BadLocationException ex) {
            // ignore
        }
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
     * Apply rendering for a specific file type (e.g., PDF, MD, JCL, COBOL).
     * This overrides the automatic detection and changes the rendering mode.
     *
     * @param fileType the file type key (e.g., "PDF", "MD", "JCL", "COBOL", "NATURAL", "WORD", "EXCEL", "OUTLOOK MAIL")
     */
    public void applyFileTypeRendering(String fileType) {
        if (fileType == null || fileType.trim().isEmpty()) {
            resetFileTypeRendering();
            return;
        }

        String type = fileType.trim().toUpperCase();
        this.activeFileType = type;

        switch (type) {
            case "PDF":
            case "WORD":
            case "EXCEL":
            case "OUTLOOK MAIL":
                // Binary document types → show HTML pane, but do NOT render rawContent
                // (it's corrupted ASCII text). Actual rendering happens in reloadAsBinary().
                this.isSourceCode = false;
                this.needsHtmlRendering = true;
                htmlRenderedPane.setText("<html><body><p style='color:#888;'>⏳ Lade Dokument...</p></body></html>");
                applyViewMode(currentMode);
                updateSaveDownloadButton(true);
                break;

            case "MD":
                // Markdown → render as HTML
                this.isSourceCode = false;
                this.needsHtmlRendering = true;
                renderHtmlContent();
                applyViewMode(currentMode);
                updateSaveDownloadButton(false);
                break;

            case "JCL":
                // JCL → source code with properties-file syntax
                this.isSourceCode = true;
                this.needsHtmlRendering = false;
                rawPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE);
                rawPane.setCodeFoldingEnabled(true);
                applyViewMode(currentMode);
                updateSaveDownloadButton(false);
                break;

            case "COBOL":
                // COBOL → source code (no native highlighting)
                this.isSourceCode = true;
                this.needsHtmlRendering = false;
                rawPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
                rawPane.setCodeFoldingEnabled(true);
                applyViewMode(currentMode);
                updateSaveDownloadButton(false);
                break;

            case "NATURAL":
                // Natural → source code with custom syntax
                this.isSourceCode = true;
                this.needsHtmlRendering = false;
                rawPane.setSyntaxEditingStyle(
                        de.bund.zrb.ui.syntax.MainframeSyntaxSupport.SYNTAX_STYLE_NATURAL);
                rawPane.setCodeFoldingEnabled(true);
                applyViewMode(currentMode);
                updateSaveDownloadButton(false);
                break;

            default:
                // Unknown file type → reset to auto-detection
                resetFileTypeRendering();
                break;
        }
    }

    /**
     * Reset rendering to the auto-detected mode (based on file name/content).
     */
    public void resetFileTypeRendering() {
        this.activeFileType = null;
        this.rawBytes = null;
        this.isSourceCode = isSourceCodeFile(sourceName);
        this.needsHtmlRendering = needsHtmlRendering(sourceName, metadata);
        if (isSourceCode) {
            rawPane.setSyntaxEditingStyle(syntaxStyle);
        } else {
            rawPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        }
        if (needsHtmlRendering) {
            renderHtmlContent();
        }
        applyViewMode(currentMode);
        updateSaveDownloadButton(false);
    }

    /**
     * Apply syntax highlighting for a programming language, using the given RSyntaxTextArea syntax style.
     * This switches the view to source code mode with the specified syntax highlighting.
     * Unlike applyFileTypeRendering (which uses a hardcoded switch), this method uses the
     * syntaxStyle string directly from the SentenceDefinition's SentenceMeta.
     *
     * @param syntaxStyleConstant RSyntaxTextArea syntax style string (e.g. "text/java", "text/python")
     */
    public void applySyntaxStyleRendering(String syntaxStyleConstant) {
        if (syntaxStyleConstant == null || syntaxStyleConstant.trim().isEmpty()) {
            resetFileTypeRendering();
            return;
        }
        this.isSourceCode = true;
        this.needsHtmlRendering = false;
        this.activeFileType = null; // not a document type
        rawPane.setSyntaxEditingStyle(syntaxStyleConstant);
        rawPane.setCodeFoldingEnabled(true);
        applyViewMode(currentMode);
        updateSaveDownloadButton(false);
    }

    /**
     * Switches the save button between "Speichern" and "Herunterladen" mode.
     * When a non-text file type (PDF, WORD, EXCEL, OUTLOOK MAIL) is selected,
     * the button becomes a download button and an upload button appears.
     *
     * @param downloadMode true to show "Herunterladen" + "Hochladen", false for "Speichern"
     */
    protected void updateSaveDownloadButton(boolean downloadMode) {
        if (saveButton == null) return;

        if (downloadMode) {
            saveButton.setText("📥 Herunterladen");
            saveButton.setToolTipText("Inhalt als Datei herunterladen");
            saveButton.setVisible(true);
            if (uploadButton != null) uploadButton.setVisible(true);
        } else {
            saveButton.setText("💾 Speichern");
            saveButton.setToolTipText("Änderungen speichern");
            saveButton.setVisible(isTextFile);
            if (uploadButton != null) uploadButton.setVisible(false);
        }
    }

    /**
     * Downloads the current content as a file using a file chooser dialog.
     * Uses binary rawBytes when available (for PDF, DOCX, etc.),
     * otherwise falls back to text rawContent.
     */
    protected void downloadContent() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Inhalt herunterladen");

        // Suggest file name with appropriate extension
        String suggestedName = sourceName != null ? sourceName : "download";
        if (activeFileType != null) {
            String ext = getDefaultExtension(activeFileType);
            if (ext != null && !suggestedName.toLowerCase().endsWith("." + ext)) {
                // Remove old extension if present, add new one
                int dot = suggestedName.lastIndexOf('.');
                if (dot > 0) {
                    suggestedName = suggestedName.substring(0, dot);
                }
                suggestedName += "." + ext;
            }
        }
        chooser.setSelectedFile(new File(suggestedName));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File target = chooser.getSelectedFile();
            if (target == null) return;

            try {
                if (rawBytes != null) {
                    // Binary content available → write raw bytes (preserves PDF/DOCX structure)
                    java.nio.file.Files.write(target.toPath(), rawBytes);
                } else {
                    // Fallback: write text content
                    java.nio.file.Files.write(target.toPath(),
                            rawContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }

                // Brief confirmation
                String original = saveButton.getText();
                saveButton.setText("✓ Gespeichert!");
                Timer timer = new Timer(1500, evt -> saveButton.setText(original));
                timer.setRepeats(false);
                timer.start();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Fehler beim Herunterladen:\n" + ex.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Returns the default file extension for a given file type key.
     */
    private String getDefaultExtension(String fileType) {
        if (fileType == null) return null;
        switch (fileType.toUpperCase()) {
            case "PDF":          return "pdf";
            case "MD":           return "md";
            case "JCL":          return "jcl";
            case "COBOL":        return "cbl";
            case "NATURAL":      return "nat";
            case "WORD":         return "docx";
            case "EXCEL":        return "xlsx";
            case "OUTLOOK MAIL": return "eml";
            default:             return "txt";
        }
    }

    /**
     * Uploads a local file to replace the remote file.
     * Base implementation shows a message; subclasses (FileTabImpl) override with actual upload logic.
     */
    protected void uploadContent() {
        JOptionPane.showMessageDialog(this,
                "Hochladen ist nur für remote-geöffnete Dateien verfügbar.",
                "Nicht verfügbar", JOptionPane.INFORMATION_MESSAGE);
    }

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
