package de.bund.zrb.ui.preview;


import de.bund.zrb.chat.attachment.AttachTabToChatUseCase;
import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;
import de.bund.zrb.ingestion.ui.ChatMarkdownFormatter;
import de.bund.zrb.jcl.model.JclOutlineModel;
import de.bund.zrb.jcl.parser.AntlrJclParser;
import de.bund.zrb.jcl.parser.CobolParser;
import de.bund.zrb.jcl.parser.NaturalParser;
import de.bund.zrb.rag.service.RagService;
import de.bund.zrb.ui.filetab.NaturalSubroutineHighlighter;
import de.bund.zrb.ui.syntax.MainframeSyntaxSupport;
import de.bund.zrb.ui.mermaid.MermaidDiagramPanel;
import de.bund.zrb.ui.mermaid.OutlineToMermaidConverter;
import de.bund.zrb.ui.mermaid.OutlineToMermaidConverter.DiagramType;
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
            "jcl", "proc", "prc", "cbl", "cob", "cobol",
            // Natural (Software AG) extensions
            "nat", "nsp", "nsn", "nss", "nsh", "nsc", "nsl", "nsa", "nsg", "nsm",
            "ns4", "ns7", "nsd", "ns3", "ns5", "ns6", "ns8"
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
            "jcl", "proc", "prc", "cbl", "cob", "cobol",
            // Natural (Software AG) extensions
            "nat", "nsp", "nsn", "nss", "nsh", "nsc", "nsl", "nsa", "nsg", "nsm",
            "ns4", "ns7", "nsd", "ns3", "ns5", "ns6", "ns8"
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
        // Natural (Software AG) extensions → custom syntax highlighting
        String natStyle = de.bund.zrb.ui.syntax.MainframeSyntaxSupport.SYNTAX_STYLE_NATURAL;
        for (String ne : new String[]{"nat","nsp","nsn","nss","nsh","nsc","nsl","nsa","nsg","nsm","ns4","ns7","nsd","ns3","ns5","ns6","ns8"}) {
            SYNTAX_STYLES.put(ne, natStyle);
        }
    }

    /** All known Natural file extensions (lowercase). */
    private static final Set<String> NATURAL_EXTENSIONS = new HashSet<>(Arrays.asList(
            "nat", "nsp", "nsn", "nss", "nsh", "nsc", "nsl", "nsa", "nsg", "nsm",
            "ns4", "ns7", "nsd", "ns3", "ns5", "ns6", "ns8"
    ));

    /** Check if extension is a Natural file extension. */
    protected static boolean isNaturalExtension(String ext) {
        return ext != null && NATURAL_EXTENSIONS.contains(ext.toLowerCase());
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
    protected String syntaxStyle;
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

    /** ApplicationState key for persisting the line-wrap preference. */
    private static final String LINE_WRAP_STATE_KEY = "preview.lineWrap";

    /** ApplicationState key for persisting step-search mode. */
    private static final String STEP_SEARCH_STATE_KEY = "preview.findBar.stepSearch";

    /** ApplicationState key for persisting the diagram type. */
    private static final String DIAGRAM_TYPE_STATE_KEY = "preview.diagramType";

    /** ApplicationState key for persisting the mindmap call depth. */
    private static final String MINDMAP_DEPTH_STATE_KEY = "preview.mindmapDepth";

    /** ApplicationState key for persisting the auto-refresh zoom threshold (in percent points). */
    private static final String AUTO_REFRESH_ZOOM_THRESHOLD_KEY = "diagram.autoRefreshZoomThreshold";

    /** ApplicationState key for persisting whether auto-refresh on zoom is enabled. */
    private static final String AUTO_REFRESH_ZOOM_ENABLED_KEY = "diagram.autoRefreshZoomEnabled";

    /** Override pane id for the diagram detail sidebar. */
    private static final String DIAGRAM_OVERRIDE_ID = "diagram";

    /** Line-wrap toggle checkbox (in toolbar). */
    protected JCheckBox lineWrapCheckBox;

    /** Extra info properties shown in the ℹ popup (populated by subclasses). */
    protected final java.util.LinkedHashMap<String, String> infoProperties = new java.util.LinkedHashMap<String, String>();

    /** The ℹ button in the toolbar. */
    private JButton infoButton;

    // ── Step-search navigation state ──
    /** All match positions (character offsets) in the current rawPane search. */
    protected java.util.List<Integer> findMatchPositions = new java.util.ArrayList<Integer>();
    /** Current index into {@link #findMatchPositions} for step-navigation. */
    protected int findMatchIndex = -1;

    /** Mermaid diagram toggle button — only shown for mainframe source code. */
    protected JButton diagramToggleButton;

    /** Mermaid diagram panel — lazy-initialized on first toggle. */
    protected MermaidDiagramPanel mermaidDiagramPanel;

    /** true when the interactive diagram view is currently active. */
    protected boolean diagramViewActive = false;

    /** Cached: whether the current content is mainframe code (JCL/COBOL/Natural). */
    protected boolean isMainframeCode = false;

    /** Cached: whether the current content is raw Mermaid diagram source code. */
    protected boolean isMermaidCode = false;


    /** Currently selected diagram type (restored from ApplicationState or default STRUCTURE). */
    protected OutlineToMermaidConverter.DiagramType activeDiagramType = restoreDiagramTypePreference();

    /** Current mindmap call tree depth (1 = direct calls only, higher = recursive). Default 2. */
    protected int mindmapDepth = restoreMindmapDepth();

    /**
     * Whether the diagram is currently in collapsed mode (only top-level summary nodes).
     * Auto-set to {@code true} when switching to visual view for large models.
     */
    protected boolean diagramCollapsed = false;

    /** Optional source resolver for resolving external call targets to source code. */
    protected de.bund.zrb.service.codeanalytics.SourceResolver sourceResolver;

    /** Callback for navigating to an external file (from diagram double-click or sidebar link). */
    public interface ExternalNavigationCallback {
        /** Open the given target name as a file (e.g. NDV object, JCL member). */
        void openExternalTarget(String targetName);
    }
    private ExternalNavigationCallback externalNavigationCallback;

    public void setExternalNavigationCallback(ExternalNavigationCallback callback) {
        this.externalNavigationCallback = callback;
    }

    /** @return true if the interactive diagram view is currently active. */
    public boolean isDiagramViewActive() { return diagramViewActive; }

    /** @return the MermaidDiagramPanel (may be null if not yet initialized). */
    public MermaidDiagramPanel getMermaidDiagramPanel() { return mermaidDiagramPanel; }

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
        this.isMainframeCode = isMainframeCodeFile(sourceName, this.rawContent);
        this.isMermaidCode = isMermaidContent(this.rawContent);

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
        findBar.setPrevAction(e -> navigateFindMatch(-1));
        findBar.setNextAction(e -> navigateFindMatch(+1));

        // Exit listener: arrow keys leave the find bar and focus the editor
        findBar.setExitListener(new FindBarPanel.FindBarExitListener() {
            @Override
            public void onExitUp() {
                // UP → focus editor, caret to first line
                rawPane.requestFocusInWindow();
                rawPane.setCaretPosition(0);
            }

            @Override
            public void onExitDown() {
                // DOWN → focus editor, caret to last line
                rawPane.requestFocusInWindow();
                int lastPos = rawPane.getDocument().getLength();
                rawPane.setCaretPosition(lastPos);
            }

            @Override
            public void onDismiss() {
                // Enter on empty / Escape → focus editor at current position
                rawPane.requestFocusInWindow();
            }
        });

        // Restore step-search preference from ApplicationState
        findBar.setStepSearchEnabled(restoreStepSearchPreference());
        findBar.setStepSearchModeListener(new FindBarPanel.StepSearchModeListener() {
            @Override
            public void onStepSearchModeChanged(boolean stepSearch) {
                persistStepSearchPreference(stepSearch);
            }
        });

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
     * Check if a file is mainframe code by extension or content analysis.
     * Used to decide whether to show the Visuell (diagram) button.
     */
    protected boolean isMainframeCodeFile(String name, String content) {
        // 1) Check by file extension
        String ext = getExtension(name);
        if (ext != null) {
            String lower = ext.toLowerCase();
            if (isNaturalExtension(lower)) return true;
            if ("jcl".equals(lower) || "proc".equals(lower) || "prc".equals(lower)) return true;
            if ("cbl".equals(lower) || "cob".equals(lower) || "cobol".equals(lower)) return true;
        }
        // 2) Fallback: detect by content
        if (content != null && !content.isEmpty()) {
            return isJclContent(content) || isCobolContent(content)
                    || isNaturalContent(content)
                    || de.bund.zrb.jcl.parser.DdmParser.isDdmContent(content);
        }
        return false;
    }

    /**
     * Detect if content is raw Mermaid diagram source code.
     * Checks whether the first non-empty line starts with a known Mermaid diagram keyword.
     */
    protected boolean isMermaidContent(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        String firstWord = content.trim().split("[\\s({]+")[0].toLowerCase();
        return firstWord.startsWith("flowchart") || firstWord.startsWith("graph")
                || firstWord.startsWith("sequencediagram") || firstWord.startsWith("classdiagram")
                || firstWord.startsWith("erdiagram") || firstWord.startsWith("statediagram")
                || firstWord.startsWith("pie") || firstWord.startsWith("gantt")
                || firstWord.startsWith("gitgraph") || firstWord.startsWith("mindmap")
                || firstWord.startsWith("timeline") || firstWord.startsWith("sankey")
                || firstWord.startsWith("xychart") || firstWord.startsWith("block")
                || firstWord.startsWith("c4context") || firstWord.startsWith("c4container")
                || firstWord.startsWith("c4component") || firstWord.startsWith("c4deployment")
                || firstWord.startsWith("journey") || firstWord.startsWith("quadrantchart")
                || firstWord.startsWith("requirementdiagram") || firstWord.startsWith("packet");
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
        // Line-wrap will be set via applyLineWrap() when the toolbar is built,
        // but set a reasonable default here so the pane is usable before that.
        area.setLineWrap(restoreLineWrapPreference());
        area.setWrapStyleWord(restoreLineWrapPreference());
        area.setText(rawContent);
        area.setCaretPosition(0);

        // Track changes — only insertUpdate/removeUpdate represent actual text changes.
        // changedUpdate fires on attribute changes (e.g., syntax highlighting style switch)
        // and must NOT set hasUnsavedChanges.
        area.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { onRawContentChanged(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { onRawContentChanged(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { /* attribute-only, ignore */ }
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
            // Re-apply subroutine highlighting when editing in RENDERED_ONLY mode
            if (currentMode == ViewMode.RENDERED_ONLY) {
                scheduleSubroutineHighlighting(rawPane);
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

        // Sync changes back to rawContent and rawPane — only on actual text changes.
        // changedUpdate fires on attribute changes (syntax highlighting) and must be ignored.
        area.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { syncFromHighlighted(area); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { syncFromHighlighted(area); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { /* attribute-only, ignore */ }
        });

        // Apply Natural subroutine block highlighting (pale pink background)
        applySubroutineHighlighting(area);

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
            // Re-apply subroutine highlighting after text change
            scheduleSubroutineHighlighting(highlightedArea);
        }
    }

    /**
     * Apply Natural subroutine block highlighting if the syntax style is Natural.
     * Highlights DEFINE SUBROUTINE … END-SUBROUTINE blocks with a pale pink background.
     */
    protected void applySubroutineHighlighting(final RSyntaxTextArea area) {
        if (!MainframeSyntaxSupport.SYNTAX_STYLE_NATURAL.equals(syntaxStyle)) return;
        // Defer to ensure text layout is complete
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                NaturalSubroutineHighlighter.apply(area);
            }
        });
    }

    /** Timer to coalesce rapid edits before re-applying subroutine highlights. */
    private Timer subroutineHighlightTimer;

    /**
     * Schedule a deferred re-application of subroutine highlighting.
     * Coalesces rapid changes (typing) into a single update after 300 ms idle.
     */
    private void scheduleSubroutineHighlighting(final RSyntaxTextArea area) {
        if (!MainframeSyntaxSupport.SYNTAX_STYLE_NATURAL.equals(syntaxStyle)) return;
        if (subroutineHighlightTimer != null) {
            subroutineHighlightTimer.stop();
        }
        subroutineHighlightTimer = new Timer(300, new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                NaturalSubroutineHighlighter.apply(area);
            }
        });
        subroutineHighlightTimer.setRepeats(false);
        subroutineHighlightTimer.start();
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
            if (diagramViewActive && mermaidDiagramPanel != null && !(isMermaidCode && isEditable)) {
                exportMermaidDiagram();
            } else if (activeFileType != null && needsHtmlRendering) {
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

        // ── Mermaid diagram toggle (centered in toolbar) ──
        toolbar.add(Box.createHorizontalGlue());
        if (isMainframeCode || isMermaidCode) {
            diagramToggleButton = new JButton("\uD83D\uDC41 Visuell"); // 👁 Visuell
            diagramToggleButton.setToolTipText("Interaktive Diagramm-Ansicht");
            diagramToggleButton.addActionListener(e -> toggleDiagramView());
            toolbar.add(diagramToggleButton);
        }

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

        // ── Line-Wrap checkbox ──
        boolean lineWrapDefault = restoreLineWrapPreference();
        lineWrapCheckBox = new JCheckBox("Zeilenumbruch", lineWrapDefault);
        lineWrapCheckBox.setToolTipText("Automatischer Zeilenumbruch bei langen Zeilen");
        lineWrapCheckBox.setFocusable(false);
        applyLineWrap(lineWrapDefault);
        lineWrapCheckBox.addActionListener(e -> {
            boolean wrap = lineWrapCheckBox.isSelected();
            applyLineWrap(wrap);
            persistLineWrapPreference(wrap);
        });
        toolbar.add(lineWrapCheckBox);
        toolbar.addSeparator(new Dimension(8, 0));

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

        // ── Info button (ℹ) ──
        infoButton = new JButton("ℹ");
        infoButton.setToolTipText("Zusätzliche Informationen zu dieser Datei");
        infoButton.setFocusable(false);
        infoButton.addActionListener(e -> showInfoPopup());
        toolbar.add(infoButton);

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
                    applySubroutineHighlighting(rawPane);
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
                NaturalSubroutineHighlighter.clearHighlights(rawPane);
                contentPanel.add(rawScrollPane, BorderLayout.CENTER);
                break;
        }

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    // ═══════════════════════════════════════════════════════════
    //  Mermaid diagram view
    // ═══════════════════════════════════════════════════════════

    /**
     * Ensure the "Visuell" diagram toggle button is present in the toolbar.
     * Called when a mainframe file type is explicitly selected via dropdown,
     * even if the button was not created during initial toolbar construction.
     */
    protected void ensureDiagramToggleVisible() {
        if (diagramToggleButton != null) {
            diagramToggleButton.setVisible(true);
            return;
        }
        // Create the button and insert it between the two glue components
        diagramToggleButton = new JButton("\uD83D\uDC41 Visuell"); // 👁 Visuell
        diagramToggleButton.setToolTipText("Interaktive Diagramm-Ansicht");
        diagramToggleButton.addActionListener(e -> toggleDiagramView());
        // Find the first glue in the toolbar and insert after it
        for (int i = 0; i < toolbar.getComponentCount(); i++) {
            Component c = toolbar.getComponentAtIndex(i);
            if (c instanceof Box.Filler) {
                toolbar.add(diagramToggleButton, i + 1);
                toolbar.revalidate();
                toolbar.repaint();
                return;
            }
        }
        // Fallback: add before last glue
        toolbar.add(diagramToggleButton);
        toolbar.revalidate();
        toolbar.repaint();
    }

    /**
     * Toggle between normal code view and interactive Mermaid diagram view.
     * For mainframe code, the diagram is generated from the parsed outline model.
     * For pure Mermaid source code, the raw content is used directly.
     */
    protected void toggleDiagramView() {
        diagramViewActive = !diagramViewActive;
        updateDiagramToggleLabel();

        if (diagramViewActive) {
            // For DDM files, automatically use ER diagram type
            String ext = getExtension(sourceName);
            boolean isDdmFile = ext != null && "nsd".equalsIgnoreCase(ext);
            if (isDdmFile || de.bund.zrb.jcl.parser.DdmParser.isDdmContent(rawContent)) {
                activeDiagramType = DiagramType.ER_DIAGRAM;
            }

            // Lazy-init MermaidDiagramPanel (always read-only initially; edit toggle in sidebar)
            if (mermaidDiagramPanel == null) {
                mermaidDiagramPanel = new MermaidDiagramPanel(false);
                mermaidDiagramPanel.setAutoRefreshThresholdPercent(restoreAutoRefreshThreshold());
                mermaidDiagramPanel.setSourceChangeListener(new MermaidDiagramPanel.SourceChangeListener() {
                    @Override
                    public void onSourceChanged(String newMermaidSource) {
                        if (isMermaidCode) {
                            rawContent = newMermaidSource;
                            rawPane.setText(rawContent);
                            hasUnsavedChanges = true;
                        }
                    }
                });
                // Double-click on a diagram node → open external target file
                mermaidDiagramPanel.setNodeDoubleClickListener(label -> {
                    if (externalNavigationCallback != null && label != null && !label.isEmpty()) {
                        externalNavigationCallback.openExternalTarget(label.trim());
                    }
                });
            }

            // Show loading state immediately so the user sees feedback right away
            mermaidDiagramPanel.setLoading();

            // Replace content panel with diagram (still in loading state)
            contentPanel.removeAll();
            contentPanel.add(mermaidDiagramPanel, BorderLayout.CENTER);
            contentPanel.revalidate();
            contentPanel.repaint();

            // Push diagram details override onto the sidebar
            pushDiagramOverride();

            // Generate Mermaid source in background, then render
            final boolean mermaid = isMermaidCode;
            SwingWorker<String, Void> genWorker = new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() {
                    if (mermaid) {
                        return rawContent != null ? rawContent.trim() : null;
                    }
                    return parseMermaidFromOutline();
                }

                @Override
                protected void done() {
                    try {
                        String mermaidCode = get();
                        if (mermaidCode == null || mermaidCode.trim().isEmpty()) {
                            JOptionPane.showMessageDialog(SplitPreviewTab.this,
                                    "Kein Diagramm erzeugbar \u2014 die Datei enth\u00E4lt keine erkennbare Struktur.",
                                    "Visuell", JOptionPane.INFORMATION_MESSAGE);
                            diagramViewActive = false;
                            updateDiagramToggleLabel();
                            sidebar.removeOverride(DIAGRAM_OVERRIDE_ID);
                            restoreCodeView();
                            return;
                        }
                        mermaidDiagramPanel.setMermaidSource(mermaidCode);
                    } catch (Exception ex) {
                        diagramViewActive = false;
                        updateDiagramToggleLabel();
                        sidebar.removeOverride(DIAGRAM_OVERRIDE_ID);
                        restoreCodeView();
                    }
                }
            };
            genWorker.execute();

            // If editing is not available, switch save button to export mode
            updateSaveButtonForDiagramView();
        } else {
            // Remove diagram override from sidebar
            sidebar.removeOverride(DIAGRAM_OVERRIDE_ID);

            // Reset findBar arrows (were forced visible for diagram step navigation)
            findBar.resetToEnterButton();

            // Clear diagram search state
            if (mermaidDiagramPanel != null) {
                mermaidDiagramPanel.clearSearch();
                mermaidDiagramPanel.setEditable(false);
            }

            // Restore save button to normal mode
            updateSaveDownloadButton(activeFileType != null && needsHtmlRendering);

            // Restore normal code view
            applyViewMode(currentMode);
        }
    }

    /**
     * When the diagram view is active and editing is not possible (edit toggle
     * is greyed out), switch the save button to "Export" mode.
     */
    private void updateSaveButtonForDiagramView() {
        if (saveButton == null) return;
        boolean editingPossible = isMermaidCode && isEditable;
        if (!editingPossible) {
            saveButton.setText("\uD83D\uDCE4 Exportieren"); // 📤
            saveButton.setToolTipText("Mermaid-Diagramm als Datei exportieren");
            saveButton.setVisible(true);
        }
        // If editing IS possible, the save button keeps its normal function
    }

    /**
     * Restore the normal code view — used when diagram generation fails or is aborted.
     */
    private void restoreCodeView() {
        findBar.resetToEnterButton();
        if (mermaidDiagramPanel != null) {
            mermaidDiagramPanel.clearSearch();
            mermaidDiagramPanel.setEditable(false);
        }
        updateSaveDownloadButton(activeFileType != null && needsHtmlRendering);
        applyViewMode(currentMode);
    }

    /**
     * Update the diagram toggle button label to reflect the current state.
     * Shows "📝 Text" when a diagram is active (clicking would switch to text),
     * and "👁 Visuell" when text is shown (clicking would switch to diagram).
     */
    protected void updateDiagramToggleLabel() {
        if (diagramToggleButton == null) return;
        if (diagramViewActive) {
            diagramToggleButton.setText("\uD83D\uDCDD Text"); // 📝 Text
            diagramToggleButton.setToolTipText("Zurück zur Text-/Code-Ansicht");
        } else {
            diagramToggleButton.setText("\uD83D\uDC41 Visuell"); // 👁 Visuell
            diagramToggleButton.setToolTipText("Interaktive Diagramm-Ansicht");
        }
    }

    /**
     * Build and push the diagram detail override pane to the sidebar.
     * <p>
     * Structure: fixed header (edit toggle + type buttons) + scrollable body (detail/edit/status).
     * The header is outside the scroll area so the type buttons are always accessible.
     * The "Visuell" label is replaced by an edit toggle button (pencil icon).
     */
    private void pushDiagramOverride() {
        // ── Outer container with fixed header + scrollable body ──
        JPanel outerPane = new JPanel(new BorderLayout());
        outerPane.setBackground(new Color(248, 249, 250));

        // ── Fixed header panel (NOT scrollable) ──
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(8, 12, 4, 12));

        JPanel headerRow = new JPanel(new BorderLayout(4, 0));
        headerRow.setOpaque(false);
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        // Edit toggle button (replaces "Visuell" label)
        final JToggleButton editToggle = new JToggleButton("\u270F\uFE0F"); // ✏️
        editToggle.setToolTipText("Bearbeiten aktivieren");
        editToggle.setFont(editToggle.getFont().deriveFont(Font.PLAIN, 14f));
        editToggle.setMargin(new Insets(2, 6, 2, 6));
        editToggle.setFocusable(false);
        // Only enabled for pure mermaid code; greyed out for outline-based diagrams
        editToggle.setEnabled(isMermaidCode && isEditable);
        editToggle.setSelected(false);
        editToggle.addActionListener(e -> {
            boolean edit = editToggle.isSelected();
            if (mermaidDiagramPanel != null) {
                mermaidDiagramPanel.setEditable(edit);
            }
        });
        headerRow.add(editToggle, BorderLayout.WEST);

        // Collapse/Expand toggle (only for outline-based diagrams, not mermaid code)
        if (!isMermaidCode) {
            final JToggleButton collapseToggle = new JToggleButton(diagramCollapsed ? "\uD83D\uDD0D" : "\uD83D\uDD0E"); // 🔍/🔎
            collapseToggle.setToolTipText(diagramCollapsed
                    ? "Details einblenden (vollständiges Diagramm)"
                    : "Übersicht (kompaktes Diagramm)");
            collapseToggle.setFont(collapseToggle.getFont().deriveFont(Font.PLAIN, 14f));
            collapseToggle.setMargin(new Insets(2, 6, 2, 6));
            collapseToggle.setFocusable(false);
            collapseToggle.setSelected(diagramCollapsed);
            collapseToggle.addActionListener(e -> {
                diagramCollapsed = collapseToggle.isSelected();
                collapseToggle.setText(diagramCollapsed ? "\uD83D\uDD0D" : "\uD83D\uDD0E");
                collapseToggle.setToolTipText(diagramCollapsed
                        ? "Details einblenden (vollständiges Diagramm)"
                        : "Übersicht (kompaktes Diagramm)");
                if (diagramViewActive) {
                    switchDiagramType(activeDiagramType);
                }
            });
            headerRow.add(collapseToggle, BorderLayout.CENTER);
        }

        // Diagram type buttons (only visible for outline-based diagrams, not mermaid code)
        if (!isMermaidCode) {
            JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
            typePanel.setOpaque(false);
            ButtonGroup typeGroup = new ButtonGroup();
            for (final OutlineToMermaidConverter.DiagramType dt : OutlineToMermaidConverter.DiagramType.values()) {
                final JToggleButton tb = new JToggleButton(dt.getIcon());
                tb.setToolTipText(dt.getLabel());
                tb.setFont(tb.getFont().deriveFont(Font.PLAIN, 14f));
                tb.setMargin(new Insets(2, 4, 2, 4));
                Dimension sq = new Dimension(28, 28);
                tb.setPreferredSize(sq);
                tb.setMinimumSize(sq);
                tb.setMaximumSize(sq);
                tb.setFocusable(false);
                if (dt == activeDiagramType) {
                    tb.setSelected(true);
                }
                tb.addActionListener(ev -> {
                    activeDiagramType = dt;
                    persistDiagramTypePreference(dt);
                    if (diagramViewActive) {
                        switchDiagramType(dt);
                    }
                });
                typeGroup.add(tb);
                typePanel.add(tb);
            }
            headerRow.add(typePanel, BorderLayout.EAST);
        }

        headerPanel.add(headerRow);

        // ── Mindmap depth control (only for outline-based diagrams) ──
        if (!isMermaidCode) {
            JPanel depthPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            depthPanel.setOpaque(false);
            depthPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            depthPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            JLabel depthLabel = new JLabel("Mindmap-Tiefe:");
            depthLabel.setFont(depthLabel.getFont().deriveFont(Font.PLAIN, 11f));
            depthLabel.setToolTipText("Rekursionstiefe für externe Aufrufe in der Mindmap (0 = keine Rekursion)");
            final JSpinner depthSpinner = new JSpinner(new SpinnerNumberModel(mindmapDepth, 0, 10, 1));
            depthSpinner.setPreferredSize(new Dimension(50, 22));
            depthSpinner.setToolTipText("0 = flach, 1 = direkte Calls, 2+ = rekursiv");
            depthSpinner.addChangeListener(e -> {
                int newDepth = (Integer) depthSpinner.getValue();
                if (newDepth != mindmapDepth) {
                    mindmapDepth = newDepth;
                    persistMindmapDepth(newDepth);
                    // Refresh mindmap if currently showing
                    if (diagramViewActive && activeDiagramType == DiagramType.MINDMAP) {
                        switchDiagramType(DiagramType.MINDMAP);
                    }
                }
            });
            depthPanel.add(depthLabel);
            depthPanel.add(depthSpinner);
            headerPanel.add(depthPanel);
        }

        outerPane.add(headerPanel, BorderLayout.NORTH);

        // ── Scrollable body (detail area, edit panel, status) ──
        JPanel bodyPane = new JPanel();
        bodyPane.setLayout(new BoxLayout(bodyPane, BoxLayout.Y_AXIS));
        bodyPane.setBorder(new EmptyBorder(4, 12, 12, 12));
        bodyPane.setBackground(new Color(248, 249, 250));

        // Detail area from MermaidDiagramPanel
        JScrollPane detailScroll = new JScrollPane(mermaidDiagramPanel.getDetailArea());
        detailScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        detailScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        bodyPane.add(detailScroll);

        // Edit panel from MermaidDiagramPanel (always present; content depends on editable state)
        bodyPane.add(Box.createVerticalStrut(6));
        JPanel editPanel = mermaidDiagramPanel.getEditPanel();
        editPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        editPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        bodyPane.add(editPanel);

        bodyPane.add(Box.createVerticalGlue());
        bodyPane.add(Box.createVerticalStrut(4));

        // Status label from MermaidDiagramPanel
        JLabel statusLbl = mermaidDiagramPanel.getStatusLabel();
        statusLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        bodyPane.add(statusLbl);

        JScrollPane bodyScroll = new JScrollPane(bodyPane);
        bodyScroll.setBorder(null);
        bodyScroll.getVerticalScrollBar().setUnitIncrement(12);
        outerPane.add(bodyScroll, BorderLayout.CENTER);

        sidebar.pushOverrideRaw(DIAGRAM_OVERRIDE_ID, outerPane);
    }

    /**
     * Parse the current content into a {@link JclOutlineModel} and convert
     * it to Mermaid diagram code using {@link OutlineToMermaidConverter}.
     *
     * @return Mermaid source code, or {@code null} if parsing fails
     */
    protected String parseMermaidFromOutline() {
        return parseMermaidFromOutline(activeDiagramType);
    }

    /**
     * Parse the current content and convert to the given diagram type.
     * For MINDMAP, builds a recursive call tree using {@link de.bund.zrb.service.codeanalytics.CodeAnalyticsService}.
     */
    protected String parseMermaidFromOutline(OutlineToMermaidConverter.DiagramType type) {
        // ── Sync rawContent from the editor pane (may have been edited since construction) ──
        try {
            String editorText = rawPane.getText();
            if (editorText != null && !editorText.isEmpty()) {
                rawContent = editorText;
            }
        } catch (Exception ignored) {
            // rawPane might not be accessible from background thread — use existing rawContent
        }

        if (rawContent == null || rawContent.isEmpty()) return null;

        // Detect language: extension-based detection takes priority,
        // then fall back to content analysis
        String ext = getExtension(sourceName);
        boolean isNatByExt = isNaturalExtension(ext);
        boolean isDdmFile = ext != null && "nsd".equalsIgnoreCase(ext);

        // ── DDM file (.NSD): always render as ER diagram ──
        // Only enter DDM path if the file extension is .NSD or the content
        // genuinely looks like a DDM (and the extension is NOT a known non-DDM Natural extension).
        boolean isDdmByContent = !isNatByExt
                && de.bund.zrb.jcl.parser.DdmParser.isDdmContent(rawContent);
        if (isDdmFile || isDdmByContent) {
            String ddmName = sourceName;
            if (ddmName != null && ddmName.contains(".")) {
                ddmName = ddmName.substring(0, ddmName.lastIndexOf('.'));
            }
            if (ddmName != null && ddmName.contains("/")) {
                ddmName = ddmName.substring(ddmName.lastIndexOf('/') + 1);
            }
            de.bund.zrb.jcl.parser.DdmParser ddmParser = new de.bund.zrb.jcl.parser.DdmParser();
            de.bund.zrb.jcl.parser.DdmParser.DdmDefinition ddmDef =
                    ddmParser.parse(rawContent, ddmName);
            if (ddmDef != null) {
                String erResult = OutlineToMermaidConverter.convertDdmToErDiagram(ddmDef);
                if (erResult != null) {
                    return erResult;
                }
            }
            // If DDM parsing fails or produces no diagram, fall through to try normal parsing
        }

        JclOutlineModel model = null;

        if (isNatByExt || isNaturalContent(rawContent)) {
            model = new NaturalParser().parse(rawContent, sourceName);
        } else if (isCobolContent(rawContent)) {
            model = new CobolParser().parse(rawContent, sourceName);
        } else if (isJclContent(rawContent)) {
            model = new AntlrJclParser().parse(rawContent, sourceName);
        }

        if (model == null || model.isEmpty()) return null;

        // For MINDMAP: build recursive call tree if depth > 0
        de.bund.zrb.service.codeanalytics.CallTreeNode callTree = null;
        if (type == DiagramType.MINDMAP && mindmapDepth > 0) {
            try {
                de.bund.zrb.service.codeanalytics.CodeAnalyticsService analytics =
                        de.bund.zrb.service.codeanalytics.CodeAnalyticsService.getInstance();
                de.bund.zrb.service.codeanalytics.SourceLanguage lang =
                        analytics.detectLanguage(rawContent);
                callTree = analytics.buildCallTree(rawContent, sourceName, lang,
                        mindmapDepth, sourceResolver);
            } catch (Exception e) {
                // fallback: no call tree
                java.util.logging.Logger.getLogger(getClass().getName())
                        .log(java.util.logging.Level.FINE, "Call tree build failed", e);
            }
        }

        // For ER_DIAGRAM on Natural programs: resolve DDMs from cache
        java.util.List<de.bund.zrb.jcl.parser.DdmParser.DdmDefinition> ddmDefs = null;
        if (type == DiagramType.ER_DIAGRAM
                && model.getLanguage() == JclOutlineModel.Language.NATURAL) {
            ddmDefs = resolveDdmDefinitions(model);
        }

        // ── Generate diagram; if the selected type yields nothing, fall back to STRUCTURE ──
        // Auto-collapse for large models when entering visual view
        boolean collapsed = diagramCollapsed || OutlineToMermaidConverter.shouldCollapse(model);
        if (collapsed && !diagramCollapsed) {
            diagramCollapsed = true; // remember for subsequent type switches
        }
        String result = OutlineToMermaidConverter.convert(model, type, callTree, ddmDefs, diagramCollapsed);
        if (result == null && type != DiagramType.STRUCTURE) {
            // The chosen diagram type couldn't produce output for this file
            // (e.g., ER_DIAGRAM on a program without VIEW statements).
            // Fall back to STRUCTURE which always produces output for parsed models.
            result = OutlineToMermaidConverter.convert(model, DiagramType.STRUCTURE, null, null, diagramCollapsed);
        }
        return result;
    }

    /**
     * Resolve DDM definitions referenced by VIEW statements in a Natural program.
     * Looks up DDM source from NDV source cache and parses it.
     */
    private java.util.List<de.bund.zrb.jcl.parser.DdmParser.DdmDefinition> resolveDdmDefinitions(
            JclOutlineModel model) {
        java.util.List<de.bund.zrb.jcl.parser.DdmParser.DdmDefinition> result =
                new java.util.ArrayList<de.bund.zrb.jcl.parser.DdmParser.DdmDefinition>();

        // Collect unique DDM names from VIEW OF references
        java.util.Set<String> ddmNames = new java.util.LinkedHashSet<String>();
        for (de.bund.zrb.jcl.model.JclElement elem : model.getElements()) {
            if (elem.getType() == de.bund.zrb.jcl.model.JclElementType.NAT_DATA_VIEW) {
                String ddmRef = elem.getParameter("OF");
                if (ddmRef != null && !ddmRef.isEmpty()) {
                    ddmNames.add(ddmRef.toUpperCase());
                }
            }
        }

        if (ddmNames.isEmpty()) return result;

        // Try to resolve DDM source from NDV cache
        de.bund.zrb.service.NdvSourceCacheService cache =
                de.bund.zrb.service.NdvSourceCacheService.getInstance();
        de.bund.zrb.jcl.parser.DdmParser ddmParser = new de.bund.zrb.jcl.parser.DdmParser();

        // Determine library search order
        java.util.List<String> libraries = new java.util.ArrayList<String>();
        // Current file's library
        if (sourcePath != null && sourcePath.contains("/")) {
            String currentLib = sourcePath.substring(0, sourcePath.indexOf('/')).toUpperCase();
            libraries.add(currentLib);
        }
        // Add settings search order
        try {
            de.bund.zrb.model.Settings settings = de.bund.zrb.helper.SettingsHelper.load();
            if (settings.ndvDefaultLibrary != null && !settings.ndvDefaultLibrary.trim().isEmpty()) {
                String defLib = settings.ndvDefaultLibrary.trim().toUpperCase();
                if (!libraries.contains(defLib)) libraries.add(defLib);
            }
            if (settings.ndvLibrarySearchOrder != null) {
                for (String lib : settings.ndvLibrarySearchOrder) {
                    String u = lib.toUpperCase();
                    if (!libraries.contains(u)) libraries.add(u);
                }
            }
        } catch (Exception ignored) {}

        for (String ddmName : ddmNames) {
            String ddmSource = null;
            for (String lib : libraries) {
                ddmSource = cache.getCachedSource(lib, ddmName);
                if (ddmSource != null) break;
            }
            if (ddmSource != null) {
                de.bund.zrb.jcl.parser.DdmParser.DdmDefinition def =
                        ddmParser.parse(ddmSource, ddmName);
                if (def != null) {
                    result.add(def);
                }
            }
        }

        return result;
    }

    /**
     * Switch the diagram type while the diagram view is active.
     */
    protected void switchDiagramType(OutlineToMermaidConverter.DiagramType type) {
        if (!diagramViewActive || mermaidDiagramPanel == null) return;
        String mermaidCode = parseMermaidFromOutline(type);
        if (mermaidCode == null || mermaidCode.trim().isEmpty()) {
            statusLabel("Kein " + type.getLabel() + "-Diagramm erzeugbar.");
            return;
        }
        mermaidDiagramPanel.setMermaidSource(mermaidCode);
    }

    /**
     * Helper to set the status label text — used when no statusLabel field is directly available.
     */
    private void statusLabel(String text) {
        if (mermaidDiagramPanel != null) {
            JOptionPane.showMessageDialog(this, text, "Visuell", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ── Line-wrap persistence ──────────────────────────────────

    /**
     * Applies the line-wrap setting to the raw pane.
     */
    protected void applyLineWrap(boolean wrap) {
        rawPane.setLineWrap(wrap);
        rawPane.setWrapStyleWord(wrap);
    }

    /**
     * Persists the line-wrap preference into {@code Settings.applicationState}.
     */
    private void persistLineWrapPreference(boolean wrap) {
        try {
            de.bund.zrb.model.Settings settings = de.bund.zrb.helper.SettingsHelper.load();
            settings.applicationState.put(LINE_WRAP_STATE_KEY, String.valueOf(wrap));
            de.bund.zrb.helper.SettingsHelper.save(settings);
        } catch (Exception ignored) {
            // best effort — don't crash if settings file is locked
        }
    }

    /**
     * Restores the line-wrap preference from {@code Settings.applicationState}.
     * Defaults to {@code true} (line-wrap on) if not yet stored.
     */
    private static boolean restoreLineWrapPreference() {
        try {
            de.bund.zrb.model.Settings settings = de.bund.zrb.helper.SettingsHelper.load();
            String value = settings.applicationState.get(LINE_WRAP_STATE_KEY);
            if (value != null) {
                return Boolean.parseBoolean(value);
            }
        } catch (Exception ignored) { }
        return true; // default: wrap enabled
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
            // Remove old version first to avoid orphaned chunks with stale content
            if (ragService.isIndexed(docId)) {
                ragService.removeDocument(docId);
            }
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
     * When the diagram view is active, delegates to diagram node search instead.
     * For binary documents (PDF, DOCX, etc.) only the HTML rendered pane is searched
     * because the raw pane contains meaningless binary data.
     * <p>
     * In <b>step-search mode</b>, match positions are collected and only the first
     * match is highlighted orange. The user then navigates with ◀ ▶ arrows.
     * In <b>normal mode</b>, all matches are highlighted yellow at once.
     */
    protected void highlightFindMatches() {
        String query = findBar.getText().trim();

        // ── Diagram search mode ──
        if (diagramViewActive && mermaidDiagramPanel != null && mermaidDiagramPanel.hasDiagram()) {
            int count = mermaidDiagramPanel.searchAndHighlight(query);
            // In diagram mode, always use step (per-node) navigation
            if (count > 0 && !query.isEmpty()) {
                findBar.showArrows();
            } else {
                findBar.resetToEnterButton();
            }
            return;
        }

        boolean stepMode = findBar.isStepSearchEnabled();

        if (!isTextFile && needsHtmlRendering) {
            // Binary document: rawPane has garbage, only search htmlRenderedPane
            highlightInTextComponent(rawPane, "", false);
            highlightInTextComponent(htmlRenderedPane, query, stepMode);
        } else if (needsHtmlRendering) {
            // Text-based HTML (Markdown, HTML) — search both panes
            highlightInTextComponent(rawPane, query, stepMode);
            highlightInTextComponent(htmlRenderedPane, query, stepMode);
        } else {
            // Plain text / source code — only rawPane
            highlightInTextComponent(rawPane, query, stepMode);
        }

        // In step mode, show the first result
        if (stepMode && !findMatchPositions.isEmpty()) {
            findMatchIndex = 0;
            showCurrentMatch();
        }
    }

    /**
     * Highlight all case-insensitive occurrences of {@code query} in the given text component.
     *
     * @param comp      the text component
     * @param query     the search text
     * @param stepMode  if true, only collects match positions (for step navigation)
     *                  and highlights the first match with orange;
     *                  if false, highlights all matches yellow at once
     */
    protected void highlightInTextComponent(javax.swing.text.JTextComponent comp,
                                            String query, boolean stepMode) {
        Highlighter highlighter = comp.getHighlighter();
        highlighter.removeAllHighlights();
        findMatchPositions = new java.util.ArrayList<Integer>();
        findMatchIndex = -1;

        if (query.isEmpty()) return;

        try {
            javax.swing.text.Document doc = comp.getDocument();
            String fullText = doc.getText(0, doc.getLength());
            String lowerText = fullText.toLowerCase();
            String lowerQuery = query.toLowerCase();

            int pos = 0;
            while (pos < lowerText.length()) {
                int idx = lowerText.indexOf(lowerQuery, pos);
                if (idx < 0) break;

                int end = idx + query.length();
                findMatchPositions.add(idx);

                if (!stepMode) {
                    // Normal mode: highlight everything yellow
                    highlighter.addHighlight(idx, end, YELLOW_PAINTER);
                }
                pos = end;
            }

            if (stepMode) {
                // Step mode: highlight only the first match orange, rest yellow dimmed
                for (int i = 0; i < findMatchPositions.size(); i++) {
                    int matchPos = findMatchPositions.get(i);
                    int matchEnd = matchPos + query.length();
                    highlighter.addHighlight(matchPos, matchEnd,
                            i == 0 ? ORANGE_PAINTER : YELLOW_DIM_PAINTER);
                }
            }

            // Scroll to first match
            if (!findMatchPositions.isEmpty()) {
                int firstHit = findMatchPositions.get(0);
                comp.setCaretPosition(firstHit);
                scrollToPosition(comp, firstHit);
            }
        } catch (BadLocationException ex) {
            // ignore
        }
    }

    /** Orange painter for the current match in step-search mode. */
    private static final Highlighter.HighlightPainter ORANGE_PAINTER =
            new DefaultHighlighter.DefaultHighlightPainter(new Color(0xFF, 0x98, 0x00, 200));

    /** Dimmed yellow painter for non-current matches in step-search mode. */
    private static final Highlighter.HighlightPainter YELLOW_DIM_PAINTER =
            new DefaultHighlighter.DefaultHighlightPainter(new Color(0xFF, 0xEB, 0x3B, 80));

    /**
     * Navigate to the previous or next match in step-search mode.
     * When the diagram view is active, delegates to diagram navigation.
     *
     * @param direction -1 for previous, +1 for next
     */
    protected void navigateFindMatch(int direction) {
        // ── Diagram navigation ──
        if (diagramViewActive && mermaidDiagramPanel != null && mermaidDiagramPanel.hasDiagram()) {
            if (direction > 0) {
                mermaidDiagramPanel.searchNext();
            } else {
                mermaidDiagramPanel.searchPrev();
            }
            return;
        }

        if (findMatchPositions.isEmpty()) return;
        String query = findBar.getText().trim();
        if (query.isEmpty()) return;

        // Advance index
        findMatchIndex += direction;
        if (findMatchIndex >= findMatchPositions.size()) findMatchIndex = 0;
        if (findMatchIndex < 0) findMatchIndex = findMatchPositions.size() - 1;

        showCurrentMatch();
    }

    /**
     * Repaint highlights to show the current match as orange and scroll to it.
     */
    protected void showCurrentMatch() {
        if (findMatchPositions.isEmpty() || findMatchIndex < 0) return;
        String query = findBar.getText().trim();
        if (query.isEmpty()) return;

        // Determine which component to operate on
        javax.swing.text.JTextComponent comp;
        if (!isTextFile && needsHtmlRendering) {
            comp = htmlRenderedPane;
        } else {
            comp = rawPane;
        }

        // Repaint all highlights
        Highlighter highlighter = comp.getHighlighter();
        highlighter.removeAllHighlights();

        try {
            for (int i = 0; i < findMatchPositions.size(); i++) {
                int matchPos = findMatchPositions.get(i);
                int matchEnd = matchPos + query.length();
                highlighter.addHighlight(matchPos, matchEnd,
                        i == findMatchIndex ? ORANGE_PAINTER : YELLOW_DIM_PAINTER);
            }

            int currentPos = findMatchPositions.get(findMatchIndex);
            comp.setCaretPosition(currentPos);
            scrollToPosition(comp, currentPos);
        } catch (BadLocationException ex) {
            // ignore
        }
    }

    /** Scroll a text component so the given character position is visible. */
    private void scrollToPosition(javax.swing.text.JTextComponent comp, int pos) {
        try {
            Rectangle rect = comp.modelToView(pos);
            if (rect != null) {
                comp.scrollRectToVisible(rect);
            }
        } catch (BadLocationException ex) {
            // ignore
        }
    }

    // ── Step-search preference persistence ──

    protected void persistStepSearchPreference(boolean stepSearch) {
        try {
            de.bund.zrb.model.Settings settings = de.bund.zrb.helper.SettingsHelper.load();
            settings.applicationState.put(STEP_SEARCH_STATE_KEY, String.valueOf(stepSearch));
            de.bund.zrb.helper.SettingsHelper.save(settings);
        } catch (Exception ignored) { }
    }

    protected static boolean restoreStepSearchPreference() {
        try {
            de.bund.zrb.model.Settings settings = de.bund.zrb.helper.SettingsHelper.load();
            String value = settings.applicationState.get(STEP_SEARCH_STATE_KEY);
            if (value != null) {
                return Boolean.parseBoolean(value);
            }
        } catch (Exception ignored) { }
        return false; // default: highlight-all mode
    }

    // ── Diagram type preference persistence ──

    private void persistDiagramTypePreference(OutlineToMermaidConverter.DiagramType type) {
        try {
            de.bund.zrb.model.Settings settings = de.bund.zrb.helper.SettingsHelper.load();
            settings.applicationState.put(DIAGRAM_TYPE_STATE_KEY, type.name());
            de.bund.zrb.helper.SettingsHelper.save(settings);
        } catch (Exception ignored) { }
    }

    private static OutlineToMermaidConverter.DiagramType restoreDiagramTypePreference() {
        try {
            de.bund.zrb.model.Settings settings = de.bund.zrb.helper.SettingsHelper.load();
            String value = settings.applicationState.get(DIAGRAM_TYPE_STATE_KEY);
            if (value != null) {
                return OutlineToMermaidConverter.DiagramType.valueOf(value);
            }
        } catch (Exception ignored) { }
        return OutlineToMermaidConverter.DiagramType.STRUCTURE;
    }

    // ── Mindmap depth preference persistence ──

    private void persistMindmapDepth(int depth) {
        try {
            de.bund.zrb.model.Settings settings = de.bund.zrb.helper.SettingsHelper.load();
            settings.applicationState.put(MINDMAP_DEPTH_STATE_KEY, String.valueOf(depth));
            de.bund.zrb.helper.SettingsHelper.save(settings);
        } catch (Exception ignored) { }
    }

    private static int restoreMindmapDepth() {
        try {
            de.bund.zrb.model.Settings settings = de.bund.zrb.helper.SettingsHelper.load();
            String value = settings.applicationState.get(MINDMAP_DEPTH_STATE_KEY);
            if (value != null) {
                int depth = Integer.parseInt(value);
                return Math.max(0, Math.min(depth, 10));
            }
        } catch (Exception ignored) { }
        return 2; // default: 2 levels deep
    }

    /**
     * Set the source resolver for resolving external call targets.
     * Called by TabbedPaneManager when the tab is created for an NDV/FTP source.
     */
    public void setSourceResolver(de.bund.zrb.service.codeanalytics.SourceResolver resolver) {
        this.sourceResolver = resolver;
    }

    // ── Auto-refresh zoom threshold persistence ──

    /**
     * Restore the effective auto-refresh zoom threshold from ApplicationState.
     * Returns 0 (disabled) if the auto-refresh checkbox is unchecked,
     * otherwise returns the stored threshold value.
     * Default: 0 (disabled — zoom only scales the bitmap, no auto re-rasterize).
     */
    public static double restoreAutoRefreshThreshold() {
        try {
            de.bund.zrb.model.Settings settings = de.bund.zrb.helper.SettingsHelper.load();
            String enabled = settings.applicationState.get(AUTO_REFRESH_ZOOM_ENABLED_KEY);
            if (!"true".equals(enabled)) {
                return 0; // disabled
            }
            String value = settings.applicationState.get(AUTO_REFRESH_ZOOM_THRESHOLD_KEY);
            if (value != null) {
                return Double.parseDouble(value);
            }
        } catch (Exception ignored) { }
        return 0; // default: disabled
    }

    /**
     * Restore just the threshold value (ignoring the enabled flag).
     * Used by the settings panel to populate the spinner.
     */
    public static double restoreAutoRefreshThresholdValue() {
        try {
            de.bund.zrb.model.Settings settings = de.bund.zrb.helper.SettingsHelper.load();
            String value = settings.applicationState.get(AUTO_REFRESH_ZOOM_THRESHOLD_KEY);
            if (value != null) {
                return Double.parseDouble(value);
            }
        } catch (Exception ignored) { }
        return 5.0; // default threshold: 5 %
    }

    /**
     * Restore the enabled flag for auto-refresh on zoom.
     */
    public static boolean restoreAutoRefreshEnabled() {
        try {
            de.bund.zrb.model.Settings settings = de.bund.zrb.helper.SettingsHelper.load();
            return "true".equals(settings.applicationState.get(AUTO_REFRESH_ZOOM_ENABLED_KEY));
        } catch (Exception ignored) { }
        return false; // default: disabled
    }

    /**
     * Persist the auto-refresh zoom threshold to ApplicationState.
     */
    public static void persistAutoRefreshThreshold(double thresholdPercent) {
        try {
            de.bund.zrb.model.Settings settings = de.bund.zrb.helper.SettingsHelper.load();
            settings.applicationState.put(AUTO_REFRESH_ZOOM_THRESHOLD_KEY, String.valueOf(thresholdPercent));
            de.bund.zrb.helper.SettingsHelper.save(settings);
        } catch (Exception ignored) { }
    }

    /**
     * Persist the auto-refresh enabled flag.
     */
    public static void persistAutoRefreshEnabled(boolean enabled) {
        try {
            de.bund.zrb.model.Settings settings = de.bund.zrb.helper.SettingsHelper.load();
            settings.applicationState.put(AUTO_REFRESH_ZOOM_ENABLED_KEY, String.valueOf(enabled));
            de.bund.zrb.helper.SettingsHelper.save(settings);
        } catch (Exception ignored) { }
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
                this.syntaxStyle = SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE;
                this.isSourceCode = true;
                this.isMainframeCode = true;
                this.needsHtmlRendering = false;
                rawPane.setSyntaxEditingStyle(syntaxStyle);
                rawPane.setCodeFoldingEnabled(true);
                ensureDiagramToggleVisible();
                applyViewMode(currentMode);
                updateSaveDownloadButton(false);
                break;

            case "COBOL":
                // COBOL → source code (no native highlighting)
                this.syntaxStyle = SyntaxConstants.SYNTAX_STYLE_NONE;
                this.isSourceCode = true;
                this.isMainframeCode = true;
                this.needsHtmlRendering = false;
                rawPane.setSyntaxEditingStyle(syntaxStyle);
                rawPane.setCodeFoldingEnabled(true);
                ensureDiagramToggleVisible();
                applyViewMode(currentMode);
                updateSaveDownloadButton(false);
                break;

            case "NATURAL":
                // Natural → source code with custom syntax
                this.syntaxStyle = MainframeSyntaxSupport.SYNTAX_STYLE_NATURAL;
                this.isSourceCode = true;
                this.isMainframeCode = true;
                this.needsHtmlRendering = false;
                rawPane.setSyntaxEditingStyle(syntaxStyle);
                rawPane.setCodeFoldingEnabled(true);
                ensureDiagramToggleVisible();
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
        // If syntaxStyle was set to a known language (e.g., via NDV metadata detection),
        // preserve isSourceCode even if the filename has no extension.
        if (!isSourceCode && syntaxStyle != null
                && !SyntaxConstants.SYNTAX_STYLE_NONE.equals(syntaxStyle)) {
            isSourceCode = true;
        }
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
        this.syntaxStyle = syntaxStyleConstant;
        this.isSourceCode = true;
        this.needsHtmlRendering = false;
        this.activeFileType = null; // not a document type
        // Check if this is a Mainframe language
        if (MainframeSyntaxSupport.SYNTAX_STYLE_NATURAL.equals(syntaxStyleConstant)
                || SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE.equals(syntaxStyleConstant)) {
            this.isMainframeCode = true;
            ensureDiagramToggleVisible();
        }
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
     * Export the current Mermaid diagram as SVG or PNG via a file chooser dialog.
     * Available when the diagram view is active and editing is not possible.
     */
    protected void exportMermaidDiagram() {
        if (mermaidDiagramPanel == null) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Mermaid-Diagramm exportieren");

        // Build suggested file name from source name
        String baseName = sourceName != null ? sourceName : "diagram";
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) baseName = baseName.substring(0, dot);

        javax.swing.filechooser.FileNameExtensionFilter svgFilter =
                new javax.swing.filechooser.FileNameExtensionFilter("SVG-Datei (*.svg)", "svg");
        javax.swing.filechooser.FileNameExtensionFilter pngFilter =
                new javax.swing.filechooser.FileNameExtensionFilter("PNG-Bild (*.png)", "png");
        chooser.addChoosableFileFilter(svgFilter);
        chooser.addChoosableFileFilter(pngFilter);
        chooser.setFileFilter(svgFilter); // SVG as default
        chooser.setSelectedFile(new java.io.File(baseName + ".svg"));

        // Update file name extension when filter changes
        final String finalBaseName = baseName;
        chooser.addPropertyChangeListener(JFileChooser.FILE_FILTER_CHANGED_PROPERTY, evt -> {
            javax.swing.filechooser.FileFilter filter = chooser.getFileFilter();
            String ext = (filter == pngFilter) ? ".png" : ".svg";
            chooser.setSelectedFile(new java.io.File(finalBaseName + ext));
        });

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            java.io.File target = chooser.getSelectedFile();
            if (target == null) return;

            try {
                String name = target.getName().toLowerCase();
                if (name.endsWith(".png")) {
                    // Export as PNG
                    java.awt.image.BufferedImage img = mermaidDiagramPanel.getImage();
                    if (img != null) {
                        javax.imageio.ImageIO.write(img, "PNG", target);
                    } else {
                        JOptionPane.showMessageDialog(this,
                                "Kein Bild verf\u00FCgbar \u2014 bitte warten, bis das Rendering abgeschlossen ist.",
                                "Export", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                } else {
                    // Export as SVG (default)
                    if (!name.endsWith(".svg")) {
                        target = new java.io.File(target.getAbsolutePath() + ".svg");
                    }
                    String svgContent = mermaidDiagramPanel.getSvg();
                    if (svgContent != null && !svgContent.isEmpty()) {
                        java.nio.file.Files.write(target.toPath(),
                                svgContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    } else {
                        JOptionPane.showMessageDialog(this,
                                "Kein SVG verf\u00FCgbar \u2014 bitte warten, bis das Rendering abgeschlossen ist.",
                                "Export", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                }

                // Brief confirmation
                String original = saveButton.getText();
                saveButton.setText("\u2713 Exportiert!");
                Timer timer = new Timer(1500, evt -> saveButton.setText(original));
                timer.setRepeats(false);
                timer.start();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Fehler beim Exportieren:\n" + ex.getMessage(),
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
        hasUnsavedChanges = false; // programmatic content set is not a user change
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

    /**
     * Add extra info properties that will be shown when the ℹ button is clicked.
     * Subclasses (e.g. FileTabImpl) call this to add NDV/FTP specific metadata.
     *
     * @param key   display label (e.g. "Library", "User")
     * @param value the value (null/empty values are ignored)
     */
    public void addInfoProperty(String key, String value) {
        if (key != null && value != null && !value.isEmpty()) {
            infoProperties.put(key, value);
        }
    }

    /**
     * Show the extra info popup with details about the file (BetaView-style ℹ popup).
     * Combines generic file metadata with any extra properties set by subclasses.
     */
    protected void showInfoPopup() {
        JPopupMenu infoPopup = new JPopupMenu();
        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.insets = new Insets(2, 4, 2, 8);
        gc.gridy = 0;

        // ── Extra properties first (NDV metadata, etc.) ──
        for (Map.Entry<String, String> entry : infoProperties.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                addInfoPopupRow(content, gc, entry.getKey(), entry.getValue());
            }
        }

        // ── Generic file metadata as fallback ──
        if (!infoProperties.containsKey("Datei")) {
            addInfoPopupRow(content, gc, "Datei", sourceName);
        }
        if (!infoProperties.containsKey("Backend")) {
            addInfoPopupRow(content, gc, "Backend", backendType);
        }
        if (metadata != null && metadata.getMimeType() != null
                && !infoProperties.containsKey("MIME")) {
            addInfoPopupRow(content, gc, "MIME", metadata.getMimeType());
        }

        if (gc.gridy == 0) {
            // No info at all
            content.add(new JLabel("Keine zusätzlichen Informationen verfügbar."));
        }

        infoPopup.add(content);
        infoPopup.show(infoButton, 0, infoButton.getHeight());
    }

    /** Labels that get a clipboard-copy button in the info popup. */
    private static final java.util.Set<String> COPYABLE_INFO_LABELS = new java.util.HashSet<>(
            java.util.Arrays.asList("Pfad", "Datei", "Library")
    );

    /** Add a label+value row to the info popup (BetaView style), with optional copy button. */
    private static void addInfoPopupRow(JPanel panel, GridBagConstraints gc, String label, String value) {
        gc.gridx = 0;
        gc.weightx = 0.0;
        gc.fill = GridBagConstraints.NONE;
        JLabel lbl = new JLabel(label + ":");
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
        panel.add(lbl, gc);

        gc.gridx = 1;
        gc.weightx = 1.0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        String displayValue = value != null ? value : "\u2013";
        panel.add(new JLabel(displayValue), gc);

        gc.gridx = 2;
        gc.weightx = 0.0;
        gc.fill = GridBagConstraints.NONE;
        if (COPYABLE_INFO_LABELS.contains(label) && value != null && !value.isEmpty()) {
            final String copyText = value;
            JButton copyBtn = new JButton("\uD83D\uDCCB"); // 📋
            copyBtn.setToolTipText(label + " kopieren");
            copyBtn.setMargin(new Insets(0, 2, 0, 2));
            copyBtn.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
            copyBtn.setFocusable(false);
            copyBtn.setContentAreaFilled(false);
            copyBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            copyBtn.addActionListener(new java.awt.event.ActionListener() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    java.awt.datatransfer.StringSelection sel =
                            new java.awt.datatransfer.StringSelection(copyText);
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
                    // Brief visual feedback
                    copyBtn.setText("✓");
                    javax.swing.Timer reset = new javax.swing.Timer(800, ev -> copyBtn.setText("\uD83D\uDCCB"));
                    reset.setRepeats(false);
                    reset.start();
                }
            });
            panel.add(copyBtn, gc);
        } else {
            panel.add(new JLabel(""), gc); // empty placeholder for alignment
        }

        gc.gridy++;
    }
}
