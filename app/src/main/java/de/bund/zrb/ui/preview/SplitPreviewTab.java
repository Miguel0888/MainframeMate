package de.bund.zrb.ui.preview;

import de.bund.zrb.chat.attachment.AttachTabToChatUseCase;
import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;
import de.zrb.bund.newApi.ui.ConnectionTab;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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
 * Uses RSyntaxTextArea for both Raw and Rendered views to preserve data integrity
 * while providing syntax highlighting.
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
    }

    private final String sourceName;
    private final String sourcePath;
    private String rawContent;
    private final DocumentMetadata metadata;
    private final List<String> warnings;
    private final Document document;
    private final boolean isTextFile;
    private final boolean isRemote;
    private final String syntaxStyle;

    // UI Components - both use RSyntaxTextArea for consistent editing and highlighting
    private final JPanel mainPanel;
    private final RSyntaxTextArea rawPane;
    private final RSyntaxTextArea renderedPane;
    private final JSplitPane splitPane;
    private final RTextScrollPane rawScrollPane;
    private final RTextScrollPane renderedScrollPane;
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
        this.isRemote = isRemote;
        this.isTextFile = determineIfTextFile(sourceName, metadata);
        this.syntaxStyle = detectSyntaxStyle(sourceName);

        setLayout(new BorderLayout());

        // Create components - both panes use RSyntaxTextArea
        this.rawPane = createRawPane();
        this.renderedPane = createRenderedPane();
        this.rawScrollPane = new RTextScrollPane(rawPane);
        this.renderedScrollPane = new RTextScrollPane(renderedPane);
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

        // Set default mode: RENDERED_ONLY for all (shows syntax highlighting)
        this.currentMode = ViewMode.RENDERED_ONLY;
        applyViewMode(currentMode);

        // Populate sidebar
        updateSidebarInfo();
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

    /**
     * Detect RSyntaxTextArea syntax style based on file extension.
     */
    private String detectSyntaxStyle(String name) {
        if (name == null) return SyntaxConstants.SYNTAX_STYLE_NONE;

        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            String ext = name.substring(dotIndex + 1).toLowerCase();
            String style = SYNTAX_STYLES.get(ext);
            if (style != null) {
                return style;
            }
        }
        return SyntaxConstants.SYNTAX_STYLE_NONE;
    }

    private RSyntaxTextArea createRawPane() {
        RSyntaxTextArea area = new RSyntaxTextArea();
        area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE); // Raw = no highlighting
        area.setCodeFoldingEnabled(false);
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
                    // Sync to rendered pane
                    if (renderedPane != null) {
                        renderedPane.setText(rawContent);
                    }
                }
            }
        });

        return area;
    }

    /**
     * Creates the rendered pane with syntax highlighting.
     * IMPORTANT: Uses RSyntaxTextArea, NOT HTML rendering, to preserve data integrity.
     * The text content is IDENTICAL to raw - only visual highlighting is added.
     */
    private RSyntaxTextArea createRenderedPane() {
        RSyntaxTextArea area = new RSyntaxTextArea();
        area.setSyntaxEditingStyle(syntaxStyle); // Syntax highlighting based on file type
        area.setCodeFoldingEnabled(true);
        area.setFont(new Font("Consolas", Font.PLAIN, 13));
        area.setEditable(isTextFile); // Editable if text file
        area.setLineWrap(false); // Code is typically not wrapped
        area.setWrapStyleWord(false);
        area.setText(rawContent);
        area.setCaretPosition(0);

        // Track changes and sync back to raw pane
        area.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { syncFromRendered(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { syncFromRendered(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { syncFromRendered(); }
            private void syncFromRendered() {
                if (isTextFile) {
                    hasUnsavedChanges = true;
                    rawContent = area.getText();
                    // Sync to raw pane if not already in sync
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
        });

        return area;
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

