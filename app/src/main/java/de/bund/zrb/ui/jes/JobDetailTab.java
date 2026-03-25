package de.bund.zrb.ui.jes;

import de.bund.zrb.files.impl.ftp.jes.JesFtpService;
import de.bund.zrb.files.impl.ftp.jes.JesJob;
import de.bund.zrb.files.impl.ftp.jes.JesSpoolFile;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.jcl.model.JclOutlineModel;
import de.bund.zrb.jcl.parser.AntlrJclParser;
import de.bund.zrb.jcl.parser.CobolParser;
import de.bund.zrb.jcl.parser.NaturalParser;
import de.bund.zrb.login.LoginManager;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.mermaid.MermaidDiagramPanel;
import de.bund.zrb.ui.mermaid.OutlineToMermaidConverter;
import de.bund.zrb.ui.syntax.MainframeSyntaxSupport;
import de.zrb.bund.newApi.ui.AppTab;
import de.zrb.bund.newApi.ui.FindBarPanel;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tab showing spool details for a single JES job.
 * <p>
 * Analogous to FileTab but for JES spool output: lists spool files on the left,
 * shows content of the selected spool file on the right with syntax highlighting
 * for JCL/COBOL/Natural.
 */
public class JobDetailTab implements AppTab {

    private static final Logger LOG = Logger.getLogger(JobDetailTab.class.getName());
    private static final String[] SPOOL_COLUMNS = {"#", "DD-Name", "Step", "Proc", "Class", "Records", "Bytes"};

    // JCL detection patterns
    private static final String JCL_PATTERN_START = "//";
    private static final String[] JCL_KEYWORDS = {"JOB", "EXEC", "DD", "PROC", "PEND", "SET", "JCLLIB", "INCLUDE"};

    /** Language options for the dropdown */
    private static final String LANG_AUTO = "Auto";
    private static final String LANG_JCL = "JCL";
    private static final String LANG_COBOL = "COBOL";
    private static final String LANG_NATURAL = "NATURAL";
    private static final String LANG_PLAIN = "Plain Text";
    private static final String[] LANGUAGE_OPTIONS = {LANG_AUTO, LANG_JCL, LANG_COBOL, LANG_NATURAL, LANG_PLAIN};

    private final JesFtpService service;
    private final JesJob job;

    private final JPanel mainPanel;
    private final JTable spoolTable;
    private final SpoolTableModel spoolModel;
    private final RSyntaxTextArea contentArea;
    private final JLabel statusLabel;
    private final FindBarPanel searchBar;
    private final JLabel searchCountLabel;
    private final JComboBox<String> languageCombo;
    private int lastSearchPos = 0;

    /** Currently effective language hint (null = auto-detect) */
    private String languageHint;

    /** Callback to notify TabbedPaneManager that content/language changed → outline refresh */
    private Runnable outlineRefreshCallback;

    /** Mermaid diagram panel — lazy-initialized on first toggle. */
    private MermaidDiagramPanel mermaidDiagramPanel;
    /** Toggle button for diagram view. */
    private JToggleButton diagramToggleButton;
    /** The content panel holding the spool content (for diagram swap). */
    private JPanel contentPanelRef;
    /** The scroll pane wrapping the RSyntaxTextArea. */
    private RTextScrollPane contentScrollRef;
    /** True when diagram view is active. */
    private boolean diagramViewActive = false;

    public JobDetailTab(JesFtpService service, JesJob job) {
        this.service = service;
        this.job = job;
        this.spoolModel = new SpoolTableModel();

        // Register custom syntax highlighters (idempotent)
        MainframeSyntaxSupport.register();

        mainPanel = new JPanel(new BorderLayout(0, 4));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // ── Info header ─────────────────────────────────────────────
        JPanel infoPanel = new JPanel(new BorderLayout(8, 0));
        infoPanel.setBorder(BorderFactory.createTitledBorder("Job-Info"));

        JPanel infoLabels = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        infoLabels.add(createInfoLabel("Job-ID:", job.getJobId()));
        infoLabels.add(createInfoLabel("Name:", job.getJobName()));
        infoLabels.add(createInfoLabel("Owner:", job.getOwner()));
        infoLabels.add(createInfoLabel("Status:", job.getStatus()));
        if (job.getRetCode() != null && !job.getRetCode().isEmpty()) {
            infoLabels.add(createInfoLabel("RC:", job.getRetCode()));
        }
        if (job.getJobClass() != null && !job.getJobClass().isEmpty()) {
            infoLabels.add(createInfoLabel("Class:", job.getJobClass()));
        }
        infoPanel.add(infoLabels, BorderLayout.CENTER);

        JPanel infoButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        JButton allButton = new JButton("📑 Gesamter Output");
        allButton.setToolTipText("Alle Spool-Files zusammen laden");
        allButton.addActionListener(e -> loadAllSpool());
        infoButtons.add(allButton);

        JButton deleteButton = new JButton("🗑️ Job löschen");
        deleteButton.addActionListener(e -> deleteJob());
        infoButtons.add(deleteButton);
        infoPanel.add(infoButtons, BorderLayout.EAST);

        mainPanel.add(infoPanel, BorderLayout.NORTH);

        // ── Split pane: spool list (top) + content (bottom) ─────────
        spoolTable = new JTable(spoolModel);
        spoolTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        spoolTable.setRowHeight(22);
        spoolTable.setFillsViewportHeight(true);
        spoolTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadSelectedSpool();
        });
        spoolTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) loadSelectedSpool();
            }
        });

        JScrollPane spoolScroll = new JScrollPane(spoolTable);
        spoolScroll.setPreferredSize(new Dimension(0, 160));

        // RSyntaxTextArea with syntax highlighting support
        contentArea = new RSyntaxTextArea();
        contentArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        contentArea.setCodeFoldingEnabled(false);
        contentArea.setEditable(false);
        contentArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        contentArea.setTabSize(8);
        contentArea.setLineWrap(false);
        contentArea.setWrapStyleWord(false);
        RTextScrollPane contentScroll = new RTextScrollPane(contentArea);
        this.contentScrollRef = contentScroll;

        // Content panel: just the content (search bar moves to bottom)
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(contentScroll, BorderLayout.CENTER);
        this.contentPanelRef = contentPanel;

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, spoolScroll, contentPanel);
        splitPane.setDividerLocation(160);
        splitPane.setResizeWeight(0.3);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // ── Bottom bar: [Copy] [🔍 Search ↓ count] [Language ▼] [status] ────
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 0));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        // Right: Status (initialized first so other listeners can reference it)
        statusLabel = new JLabel("Lade Spool-Liste…");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));

        // Left: Copy button
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));

        JButton copyButton = new JButton("📋 Kopieren");
        copyButton.setToolTipText("Angezeigten Inhalt in die Zwischenablage kopieren");
        copyButton.addActionListener(e -> {
            try {
                String text = contentArea.getText();
                if (text != null && !text.isEmpty()) {
                    // Re-sanitize before clipboard (belt-and-suspenders against null bytes)
                    String safe = sanitizeSpoolContent(text);
                    Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new StringSelection(safe), null);
                    statusLabel.setText("✅ In Zwischenablage kopiert (" + safe.length() + " Zeichen)");
                }
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "[JES] Clipboard copy failed", ex);
                statusLabel.setText("❌ Kopieren fehlgeschlagen: " + ex.getMessage());
            }
        });
        leftPanel.add(copyButton);

        // Diagram toggle button (read-only, for JCL/COBOL/Natural spool content)
        diagramToggleButton = new JToggleButton("\uD83D\uDCC8 Diagramm");
        diagramToggleButton.setToolTipText("Interaktives Mermaid-Diagramm anzeigen (Read-Only)");
        diagramToggleButton.setFocusable(false);
        diagramToggleButton.addActionListener(e -> toggleDiagramView());
        leftPanel.add(diagramToggleButton);


        bottomPanel.add(leftPanel, BorderLayout.WEST);

        // Center: Search bar (fills remaining space)
        searchBar = new FindBarPanel("Suche im Spool-Output…",
                "Suche im Spool-Output (z.B. JOBLIB, IEF, ABEND)");
        searchBar.addSearchAction(e -> searchInContent());

        JButton searchNextBtn = new JButton("↓");
        searchNextBtn.setMargin(new Insets(1, 4, 1, 4));
        searchNextBtn.setFocusable(false);
        searchNextBtn.setToolTipText("Nächstes Ergebnis");
        searchNextBtn.addActionListener(e -> searchNextInContent());

        searchCountLabel = new JLabel("");
        searchCountLabel.setFont(searchCountLabel.getFont().deriveFont(Font.PLAIN, 11f));
        searchCountLabel.setForeground(Color.GRAY);

        searchBar.addEastComponent(searchNextBtn);
        searchBar.addEastComponent(searchCountLabel);

        JPanel searchWrapper = new JPanel(new BorderLayout());
        searchWrapper.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        searchWrapper.add(searchBar, BorderLayout.CENTER);
        bottomPanel.add(searchWrapper, BorderLayout.CENTER);

        // Right: Language combo only (status label is intentionally hidden)
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        languageCombo = new JComboBox<>(LANGUAGE_OPTIONS);
        languageCombo.setSelectedItem(LANG_AUTO);
        languageCombo.setPreferredSize(new Dimension(100, 24));
        languageCombo.setToolTipText("Syntax-Highlighting / Outline-Sprache wählen");
        languageCombo.addActionListener(e -> onLanguageChanged());
        rightPanel.add(languageCombo);


        bottomPanel.add(rightPanel, BorderLayout.EAST);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Load spool files initially
        loadSpoolList();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Language detection & syntax highlighting
    // ═══════════════════════════════════════════════════════════════════

    /** Called when the language dropdown changes. */
    private void onLanguageChanged() {
        String selected = (String) languageCombo.getSelectedItem();
        if (LANG_AUTO.equals(selected)) {
            languageHint = null;
            applySyntaxForContent(contentArea.getText());
        } else {
            languageHint = selected;
            applySyntaxStyle(resolveLanguageToSyntaxStyle(selected));
        }
        fireOutlineRefresh();
    }

    /**
     * Auto-detect language from content and apply syntax highlighting.
     * Called whenever new spool content is loaded (unless a manual language is set).
     */
    private void applySyntaxForContent(String content) {
        applySyntaxForContent(content, null);
    }

    /**
     * Auto-detect language from content and optional DD-name, then apply syntax highlighting.
     */
    private void applySyntaxForContent(String content, String ddName) {
        if (languageHint != null) {
            // Manual selection takes precedence
            applySyntaxStyle(resolveLanguageToSyntaxStyle(languageHint));
            return;
        }
        if (content == null || content.isEmpty()) {
            applySyntaxStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
            return;
        }
        // DD-name based detection (JESJCL always contains JCL)
        if (ddName != null) {
            String upperDd = ddName.toUpperCase();
            if (upperDd.equals("JESJCL") || upperDd.endsWith(".JCL")) {
                applySyntaxStyle(SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE);
                return;
            }
        }
        // Auto-detect from content
        if (isNaturalContent(content)) {
            applySyntaxStyle(MainframeSyntaxSupport.SYNTAX_STYLE_NATURAL);
        } else if (isJclContent(content)) {
            applySyntaxStyle(SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE);
        } else if (isCobolContent(content)) {
            applySyntaxStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        } else {
            applySyntaxStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        }
    }

    private void applySyntaxStyle(String style) {
        contentArea.setSyntaxEditingStyle(style);
        boolean hasHighlighting = !SyntaxConstants.SYNTAX_STYLE_NONE.equals(style);
        contentArea.setCodeFoldingEnabled(hasHighlighting);
    }

    private static String resolveLanguageToSyntaxStyle(String lang) {
        if (lang == null) return SyntaxConstants.SYNTAX_STYLE_NONE;
        switch (lang) {
            case LANG_JCL:     return SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE;
            case LANG_COBOL:   return SyntaxConstants.SYNTAX_STYLE_NONE;
            case LANG_NATURAL: return MainframeSyntaxSupport.SYNTAX_STYLE_NATURAL;
            default:           return SyntaxConstants.SYNTAX_STYLE_NONE;
        }
    }

    /**
     * Returns the effective language hint for outline purposes.
     * When "Auto" is selected, detects from content.
     */
    public String getEffectiveLanguageHint() {
        if (languageHint != null) return languageHint;
        // auto-detect
        String content = contentArea.getText();
        if (content != null && !content.isEmpty()) {
            if (isJclContent(content)) return LANG_JCL;
            if (isCobolContent(content)) return LANG_COBOL;
            if (isNaturalContent(content)) return LANG_NATURAL;
        }
        return null;
    }

    // ── Content-based language detection (mirrors SplitPreviewTab / TabbedPaneManager logic) ──

    private static boolean isJclContent(String content) {
        if (content == null || content.length() < 3) return false;
        String[] lines = content.split("\\r?\\n", 80);
        int jclLineCount = 0;
        int jclKeywordCount = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            // JES spool output may prepend line numbers → trim before checking
            if (trimmed.startsWith(JCL_PATTERN_START)) {
                jclLineCount++;
                String upperLine = trimmed.toUpperCase();
                for (String keyword : JCL_KEYWORDS) {
                    if (upperLine.contains(" " + keyword + " ")
                            || upperLine.contains(" " + keyword + ",")
                            || upperLine.contains("//" + keyword + " ")) {
                        jclKeywordCount++;
                        break;
                    }
                }
            } else {
                // Also detect JCL lines with leading line numbers, e.g. "   1 //JOBNAME JOB ..."
                String stripped = trimmed.replaceFirst("^\\d+\\s+", "");
                if (stripped.startsWith(JCL_PATTERN_START)) {
                    jclLineCount++;
                    String upperLine = stripped.toUpperCase();
                    for (String keyword : JCL_KEYWORDS) {
                        if (upperLine.contains(" " + keyword + " ")
                                || upperLine.contains(" " + keyword + ",")
                                || upperLine.contains("//" + keyword + " ")) {
                            jclKeywordCount++;
                            break;
                        }
                    }
                }
            }
        }
        return jclLineCount >= 2 && jclKeywordCount >= 1;
    }

    private static boolean isCobolContent(String content) {
        if (content == null) return false;
        String upper = content.toUpperCase();
        return upper.contains("IDENTIFICATION DIVISION")
                || upper.contains("PROCEDURE DIVISION")
                || upper.contains("DATA DIVISION")
                || upper.contains("WORKING-STORAGE SECTION");
    }

    private static boolean isNaturalContent(String content) {
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

    // ═══════════════════════════════════════════════════════════════════
    //  Outline integration
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Set callback for outline refresh (called by TabbedPaneManager when wiring this tab).
     */
    public void setOutlineRefreshCallback(Runnable callback) {
        this.outlineRefreshCallback = callback;
    }

    private void fireOutlineRefresh() {
        if (outlineRefreshCallback != null) {
            outlineRefreshCallback.run();
        }
    }

    /**
     * Get the RSyntaxTextArea for line navigation from the outline panel.
     */
    public RSyntaxTextArea getContentArea() {
        return contentArea;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Data loading
    // ═══════════════════════════════════════════════════════════════════

    /** Cached full output – populated when we had to fall back to full-output parsing. */
    private String cachedFullOutput;

    /**
     * Map from spool-table row → line range in cachedFullOutput (start inclusive, end exclusive).
     * Only populated when sections were parsed from full output.
     */
    private final List<int[]> sectionLineRanges = new ArrayList<>();

    private void loadSpoolList() {
        Settings settings = SettingsHelper.load();
        String ddNameMode = settings.jesSpoolDdNameMode != null ? settings.jesSpoolDdNameMode : "FAST";

        if ("OFF".equalsIgnoreCase(ddNameMode)) {
            // OFF mode: load full output quickly, all SPOOL#n, then background probe
            statusLabel.setText("⏳ Lade gesamten Output…");
            loadFullOutputOffMode();
            return;
        }

        new SwingWorker<List<JesSpoolFile>, Void>() {
            @Override
            protected List<JesSpoolFile> doInBackground() throws Exception {
                return service.listSpoolFiles(job.getJobId());
            }

            @Override
            protected void done() {
                try {
                    List<JesSpoolFile> files = get();
                    if (!files.isEmpty()) {
                        spoolModel.setFiles(files);
                        statusLabel.setText(files.size() + " Spool-File(s)");
                        spoolTable.setRowSelectionInterval(0, 0);

                        // FAST mode: optionally start background probe to refine DDNames
                        if ("FAST".equalsIgnoreCase(ddNameMode) && settings.jesFastBackgroundProbe) {
                            startBackgroundDdNameProbe(files.size());
                        }
                    } else {
                        // Spool list parsing returned nothing – load full output and parse sections
                        statusLabel.setText("⏳ Lade gesamten Output und analysiere Sections…");
                        loadFullOutputAndParseSections();
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    LOG.log(Level.WARNING, "[JES] Failed to load spool list for " + job.getJobId(), cause);
                    statusLabel.setText("⏳ Lade gesamten Output…");
                    loadFullOutputAndParseSections();
                }
            }
        }.execute();
    }

    /**
     * Load the concatenated full output, parse DD sections from it,
     * populate the spool table, and show the full content.
     */
    private void loadFullOutputAndParseSections() {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return service.getAllSpoolContent(job.getJobId());
            }

            @Override
            protected void done() {
                try {
                    String fullOutput = sanitizeSpoolContent(get());
                    cachedFullOutput = fullOutput;
                    cleanAndSetContent(fullOutput, null);

                    // Parse sections from the output
                    List<JesSpoolFile> sections = JesFtpService.parseSpoolSectionsFromOutput(fullOutput);
                    if (!sections.isEmpty()) {
                        // Also compute line ranges for each section so we can scroll to them
                        computeSectionRanges(fullOutput);
                        spoolModel.setFiles(sections);
                        statusLabel.setText(sections.size() + " Section(s) aus Output erkannt");
                        spoolTable.setRowSelectionInterval(0, 0);
                    } else {
                        statusLabel.setText("✅ Gesamter Output geladen (keine Sections erkannt)");
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    LOG.log(Level.WARNING, "[JES] Failed to load full output for " + job.getJobId(), cause);
                    statusLabel.setText("❌ Fehler beim Laden");
                    contentArea.setText("Fehler: " + cause.getMessage());
                }
            }
        }.execute();
    }

    /**
     * Compute line ranges for each section in the full output, delimited by JES separator lines.
     * <p>
     * IMPORTANT: Must stay aligned with {@link JesFtpService#parseSpoolSectionsFromOutput}
     * so that table row N maps to sectionLineRanges[N]. Both methods skip empty sections
     * (where there are only blank lines between two separators) and advance past leading
     * blank lines after each separator.
     */
    private void computeSectionRanges(String fullOutput) {
        sectionLineRanges.clear();
        String[] lines = fullOutput.split("\\r?\\n");
        int sectionStart = findFirstNonEmpty(lines, 0);

        for (int i = Math.max(sectionStart, 0); i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.contains("END OF JES SPOOL FILE")
                    || trimmed.contains("END OF JES2 SPOOL")
                    || trimmed.matches("^\\s*!!\\s+END\\s+.*!!\\s*$")) {
                // Only add if the section has content (sectionStart < separator line)
                if (sectionStart >= 0 && sectionStart < i) {
                    sectionLineRanges.add(new int[]{sectionStart, i});
                }
                sectionStart = findFirstNonEmpty(lines, i + 1);
            }
        }
        // Last section (after last separator or entire output if no separators found)
        if (sectionStart >= 0 && sectionStart < lines.length) {
            sectionLineRanges.add(new int[]{sectionStart, lines.length});
        }
    }

    /** Find index of the first non-empty line starting at {@code from}, or -1 if none. */
    private static int findFirstNonEmpty(String[] lines, int from) {
        for (int i = Math.max(0, from); i < lines.length; i++) {
            if (lines[i] != null && !lines[i].trim().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private void loadSelectedSpool() {
        int row = spoolTable.getSelectedRow();
        if (row < 0) return;

        JesSpoolFile sf = spoolModel.getFileAt(row);
        String ddName = sf.getDdName();

        // If we have cached full output with section ranges, scroll to that section
        if (cachedFullOutput != null && row < sectionLineRanges.size()) {
            int[] range = sectionLineRanges.get(row);
            String[] allLines = cachedFullOutput.split("\\r?\\n");
            StringBuilder sb = new StringBuilder();
            int endLine = Math.min(range[1], allLines.length);
            for (int i = range[0]; i < endLine; i++) {
                sb.append(allLines[i]).append('\n');
            }
            String sectionContent = sb.toString();
            cleanAndSetContent(sectionContent, ddName);
            int lineCount = endLine - range[0];
            statusLabel.setText("✅ " + sf.getDdName() + " (" + lineCount + " Zeilen)");
            return;
        }

        // Normal mode: load from FTP
        statusLabel.setText("⏳ Lade " + sf.getDdName() + "…");
        contentArea.setText("Lade " + sf.getDdName() + " (Spool #" + sf.getId() + ")…");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return service.getSpoolContent(job.getJobId(), sf.getId());
            }

            @Override
            protected void done() {
                try {
                    String content = get();
                    cleanAndSetContent(content, ddName);
                    statusLabel.setText("✅ " + sf.getDdName() + " geladen (" + sf.getRecordCount() + " Records)");
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    contentArea.setText("Fehler beim Laden:\n" + cause.getMessage());
                    statusLabel.setText("❌ Fehler");
                }
            }
        }.execute();
    }

    private void loadAllSpool() {
        // If we already have the full output cached, just show it
        if (cachedFullOutput != null) {
            cleanAndSetContent(cachedFullOutput, null);
            statusLabel.setText("✅ Gesamter Output angezeigt");
            spoolTable.clearSelection();
            return;
        }

        statusLabel.setText("⏳ Lade gesamten Output…");
        contentArea.setText("Lade alle Spool-Files für " + job.getJobId() + "…");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return service.getAllSpoolContent(job.getJobId());
            }

            @Override
            protected void done() {
                try {
                    String content = get();
                    cleanAndSetContent(content, null);
                    statusLabel.setText("✅ Gesamter Output geladen");
                    spoolTable.clearSelection();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    contentArea.setText("Fehler beim Laden:\n" + cause.getMessage());
                    statusLabel.setText("❌ Fehler");
                }
            }
        }.execute();
    }

    private void deleteJob() {
        int confirm = JOptionPane.showConfirmDialog(mainPanel,
                "Job " + job.getJobId() + " (" + job.getJobName() + ") wirklich löschen?\n"
                        + "Der Spool-Output wird unwiderruflich entfernt.",
                "Job löschen?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return service.deleteJob(job.getJobId());
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        statusLabel.setText("✅ " + job.getJobId() + " gelöscht");
                        contentArea.setText("Job wurde gelöscht.");
                    } else {
                        statusLabel.setText("❌ Löschen fehlgeschlagen");
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    statusLabel.setText("❌ Fehler");
                    JOptionPane.showMessageDialog(mainPanel,
                            "Fehler beim Löschen:\n" + cause.getMessage(),
                            "JES-Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Search
    // ═══════════════════════════════════════════════════════════════════

    private void searchInContent() {
        lastSearchPos = 0;
        searchNextInContent();
    }

    private void searchNextInContent() {
        String query = searchBar.getText();
        if (query == null || query.isEmpty()) return;

        String text = contentArea.getText();
        if (text == null || text.isEmpty()) return;

        String textLower = text.toLowerCase();
        String queryLower = query.toLowerCase();

        // Count total matches
        int count = 0;
        int idx = 0;
        while ((idx = textLower.indexOf(queryLower, idx)) >= 0) {
            count++;
            idx += queryLower.length();
        }

        // Find next match from lastSearchPos
        int pos = textLower.indexOf(queryLower, lastSearchPos);
        if (pos < 0 && lastSearchPos > 0) {
            // Wrap around
            pos = textLower.indexOf(queryLower, 0);
        }

        if (pos >= 0) {
            contentArea.setCaretPosition(pos);
            contentArea.select(pos, pos + query.length());
            contentArea.requestFocusInWindow();
            lastSearchPos = pos + query.length();
            searchCountLabel.setText(count + " Treffer");
        } else {
            searchCountLabel.setText("Nicht gefunden");
            lastSearchPos = 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Helpers – JES spool JCL normalizer (state machine)
    // ═══════════════════════════════════════════════════════════════════

    /** State: processing regular JCL statements. */
    private static final int ST_NORMAL = 0;
    /** State: inside instream data (after DD * or DD DATA, until /*). */
    private static final int ST_INSTREAM = 1;
    /** State: inside a (possibly multi-line) IEFC/IEF substitution message. */
    private static final int ST_SUBST_MSG = 2;

    /**
     * Normalize JES spool JCL output into clean, resubmittable JCL.
     * <p>
     * JES JESJCL spool format uses a fixed 10-character prefix per line
     * (right-justified statement number in cols 1–9, space in col 10).
     * <p>
     * A three-state machine classifies every line after stripping the prefix:
     * <dl>
     *   <dt>{@code NORMAL}</dt>
     *   <dd>{@code //…} → keep; check for {@code DD *}/{@code DD DATA} → enter {@code INSTREAM}.<br>
     *       {@code XX…} → discard (PROC expansion).<br>
     *       {@code X/…} → discard (overridden PROC statement).<br>
     *       {@code IEF…} → discard, enter {@code SUBST_MSG}.<br>
     *       anything else → discard (JES artefact).</dd>
     *   <dt>{@code INSTREAM}</dt>
     *   <dd>All lines kept verbatim until {@code /*} (→ keep, return to {@code NORMAL})
     *       or until a recognisable JCL prefix ({@code //}, {@code XX}, {@code X/}, {@code IEF})
     *       appears (→ JES omitted the data, return to {@code NORMAL}).</dd>
     *   <dt>{@code SUBST_MSG}</dt>
     *   <dd>Discard every line until a recognisable JCL prefix starts
     *       ({@code //}, {@code XX}, {@code X/}, {@code IEF}),
     *       then return to {@code NORMAL} and re-classify.</dd>
     * </dl>
     * <p>
     * If the content does not look like JES spool, it is returned unchanged.
     */
    static String normalizeJesSpoolJcl(String content) {
        if (content == null || content.isEmpty()) return content;
        String[] lines = content.split("\\r?\\n", -1);
        if (lines.length < 2) return content;

        if (!looksLikeJesSpool(lines)) {
            return content;
        }

        int state = ST_NORMAL;
        boolean instreamHadData = false;
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (String line : lines) {
            // ── Strip the 10-character prefix (statement number area) ──
            String stripped;
            if (line.length() > 10) {
                stripped = line.substring(10);
            } else if (line.length() == 10) {
                stripped = "";
            } else {
                stripped = line.trim();      // very short / empty
            }

            // ── SUBSTITUTION_MSG state ────────────────────────────────
            if (state == ST_SUBST_MSG) {
                if (isRecognisedPrefix(stripped)) {
                    state = ST_NORMAL;      // fall through to NORMAL below
                } else {
                    continue;               // continuation of IEF message → discard
                }
            }

            // ── INSTREAM state ────────────────────────────────────────
            if (state == ST_INSTREAM) {
                if (stripped.startsWith("/*")) {
                    // delimiter ends instream data → keep it, back to NORMAL
                    if (!first) sb.append('\n');
                    first = false;
                    sb.append(rtrim(stripped));
                    state = ST_NORMAL;
                    continue;
                }
                if (isRecognisedPrefix(stripped)) {
                    // JES2 omitted the instream data → insert placeholder, back to NORMAL
                    if (!instreamHadData) {
                        sb.append('\n');
                        sb.append("//* << instream data omitted by JES2 >>");
                    }
                    state = ST_NORMAL;
                    // fall through to NORMAL processing
                } else {
                    // actual instream data line → keep
                    instreamHadData = true;
                    if (!first) sb.append('\n');
                    first = false;
                    sb.append(rtrim(stripped));
                    continue;
                }
            }

            // ── NORMAL state ──────────────────────────────────────────
            if (stripped.startsWith("XX")) {
                continue;                   // PROC expansion → discard
            }
            if (stripped.startsWith("X/")) {
                continue;                   // overridden PROC stmt → discard
            }
            if (stripped.startsWith("IEF")) {
                state = ST_SUBST_MSG;       // JES message (may span lines)
                continue;
            }
            if (stripped.startsWith("//") || stripped.startsWith("/*")) {
                if (!first) sb.append('\n');
                first = false;
                String rtrimmed = rtrim(stripped);

                // Strip JES job number from JOB card (JES2 puts Jnnnnnnn in cols 73-80)
                rtrimmed = stripJobNumber(rtrimmed);

                sb.append(rtrimmed);

                // After DD * or DD DATA the next lines are instream data.
                // NOTE: JES2 typically omits instream data from JESJCL spool output;
                // the data is stored in separate spool files. In that case the state
                // machine will exit ST_INSTREAM immediately when the next // line appears.
                if (isInstreamDataDd(rtrimmed)) {
                    state = ST_INSTREAM;
                    instreamHadData = false;
                }
                continue;
            }
            // Any other unrecognised line → discard (JES artefact / noise)
        }

        // Strip trailing empty lines
        String result = sb.toString();
        while (result.endsWith("\n")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * Does the stripped line start with a prefix that the spool format uses
     * for a "real" classified line?  Used by the state machine to detect
     * when a multi-line IEF message or omitted instream data ends.
     */
    private static boolean isRecognisedPrefix(String stripped) {
        return stripped.startsWith("//")
                || stripped.startsWith("XX")
                || stripped.startsWith("X/")
                || stripped.startsWith("IEF");
    }

    /**
     * Check whether a (rtrimmed, prefix-stripped) JCL line declares
     * instream data, i.e. contains {@code DD *} or {@code DD DATA}.
     */
    static boolean isInstreamDataDd(String line) {
        String upper = line.toUpperCase();
        int ddIdx = upper.indexOf(" DD ");
        if (ddIdx < 0) return false;
        String afterDd = upper.substring(ddIdx + 4).trim();
        return afterDd.startsWith("*") || afterDd.startsWith("DATA");
    }

    /**
     * Strip the JES job number from the JOB statement card.
     * JES2 places the job number (e.g. {@code J0753875}) in columns 73-80
     * of the JOB card, overwriting the original identification field.
     * This method detects and removes it.
     */
    static String stripJobNumber(String line) {
        if (line.length() < 20) return line;
        // Only process JOB cards
        String upper = line.toUpperCase();
        if (!upper.contains(" JOB ") && !upper.contains(" JOB(")) return line;
        // Check if line ends with Jnnnnnnn (JES job number)
        String trimmed = rtrim(line);
        if (trimmed.length() >= 8) {
            String last8 = trimmed.substring(trimmed.length() - 8);
            if (last8.matches("J\\d{7}")) {
                return rtrim(trimmed.substring(0, trimmed.length() - 8));
            }
        }
        return line;
    }

    /**
     * Detect whether the content lines follow JES JESJCL spool format.
     * Checks for the characteristic pattern of numbered JCL lines:
     * 10-char prefix with right-justified statement number followed by {@code //}.
     * <p>
     * Also counts secondary evidence (XX/X/ proc expansion lines, IEFC substitution
     * messages) that only appear in spool output, allowing earlier detection even when
     * JCL has very long JOB card headers with many comment/continuation lines before
     * the second numbered statement (e.g. Entire Operations generated JCL).
     */
    static boolean looksLikeJesSpool(String[] lines) {
        int numberedJclLines = 0;
        int xxLines = 0;
        int iefcLines = 0;
        // Scan up to 500 lines — Entire Operations / long JOB headers can easily
        // push the second numbered statement past line 50.
        int checkLimit = Math.min(lines.length, 500);

        for (int i = 0; i < checkLimit; i++) {
            String line = lines[i];
            if (line.length() < 12) continue;

            String prefix = line.substring(0, 10);
            String after  = line.substring(10);

            // Numbered line: prefix has digits + trailing space, content starts with //
            // e.g. "        1 //KKR097XP JOB ..."
            if (prefix.charAt(9) == ' ' && prefix.substring(0, 9).trim().matches("\\d+")) {
                if (after.startsWith("//") || after.startsWith("XX")) {
                    numberedJclLines++;
                }
            }

            // Secondary evidence: PROC expansion, override, or JES substitution messages
            if (after.startsWith("XX") || after.startsWith("X/")) {
                xxLines++;
            }
            if (after.startsWith("IEFC")) {
                iefcLines++;
            }

            // Early exit: strong evidence already found
            if (numberedJclLines >= 3) return true;
            if (numberedJclLines >= 1 && (xxLines + iefcLines) >= 2) return true;
        }

        return numberedJclLines >= 3;
    }

    /** Right-trim trailing whitespace. */
    private static String rtrim(String s) {
        int len = s.length();
        while (len > 0 && s.charAt(len - 1) <= ' ') {
            len--;
        }
        return s.substring(0, len);
    }

    /**
     * Cleans and sets content: sanitizes control characters, normalizes JES spool JCL,
     * sets text, applies syntax, fires outline refresh.
     * @param rawContent  content as retrieved from JES
     * @param ddName      DD name for detection hinting (may be null)
     */
    private void cleanAndSetContent(String rawContent, String ddName) {
        String sanitized = sanitizeSpoolContent(rawContent);
        String cleaned = normalizeJesSpoolJcl(sanitized);
        contentArea.setText(cleaned);
        contentArea.setCaretPosition(0);
        applySyntaxForContent(cleaned, ddName);
        fireOutlineRefresh();
    }

    /**
     * Sanitize raw spool content by removing or replacing control characters
     * that cause problems with display and clipboard operations.
     * <p>
     * z/OS JES spool output often contains:
     * <ul>
     *   <li>ASA carriage control (form feed {@code \f} from page break '1' in col 1)</li>
     *   <li>Null bytes ({@code \0}) from EBCDIC padding / fixed-length records</li>
     *   <li>Other non-printable control characters</li>
     * </ul>
     * These characters are valid in Java strings but cause silent truncation
     * when copied to the Windows system clipboard (which uses C-style null-terminated strings)
     * and display issues in Swing text components.
     */
    static String sanitizeSpoolContent(String content) {
        if (content == null || content.isEmpty()) return content;

        StringBuilder sb = new StringBuilder(content.length());
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                sb.append(c);                       // keep standard whitespace
            } else if (c == '\f') {
                // Form feed (ASA page break '1') → blank line separator
                sb.append('\n');
            } else if (c == '\0') {
                // Null byte → skip (clipboard truncation cause)
            } else if (c < 0x20) {
                // Other control characters (SOH, STX, …) → skip
            } else {
                sb.append(c);                       // printable character
            }
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Background DDName probing
    // ═══════════════════════════════════════════════════════════════════

    /**
     * OFF mode: Load full output quickly, parse sections with SPOOL#n names,
     * then start background probe to correct DDNames.
     */
    private void loadFullOutputOffMode() {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return service.getAllSpoolContent(job.getJobId());
            }

            @Override
            protected void done() {
                try {
                    String fullOutput = sanitizeSpoolContent(get());
                    cachedFullOutput = fullOutput;
                    cleanAndSetContent(fullOutput, null);

                    // Parse sections but force all DDNames to SPOOL#n
                    List<JesSpoolFile> sections = parseSpoolSectionsAsNumbered(fullOutput);
                    if (!sections.isEmpty()) {
                        computeSectionRanges(fullOutput);
                        spoolModel.setFiles(sections);
                        statusLabel.setText(sections.size() + " Section(s) – DDNames werden nachgeladen…");
                        spoolTable.setRowSelectionInterval(0, 0);
                        // Start background probe to correct names
                        startBackgroundDdNameProbe(sections.size());
                    } else {
                        statusLabel.setText("✅ Gesamter Output geladen");
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    LOG.log(Level.WARNING, "[JES] Failed to load full output (OFF mode)", cause);
                    statusLabel.setText("❌ Fehler beim Laden");
                    contentArea.setText("Fehler: " + cause.getMessage());
                }
            }
        }.execute();
    }

    /**
     * Parse sections from full output but label all as SPOOL#n (no content detection).
     */
    private static List<JesSpoolFile> parseSpoolSectionsAsNumbered(String fullOutput) {
        List<JesSpoolFile> sections = JesFtpService.parseSpoolSectionsFromOutput(fullOutput);
        List<JesSpoolFile> numbered = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            JesSpoolFile orig = sections.get(i);
            numbered.add(new JesSpoolFile(
                    orig.getId(),
                    "SPOOL#" + orig.getId(),
                    orig.getStepName(),
                    orig.getProcStep(),
                    orig.getDsClass(),
                    orig.getByteCount(),
                    orig.getRecordCount()
            ));
        }
        return numbered;
    }

    /**
     * Start a background SwingWorker that probes DDNames via parallel FTP connections
     * and updates the spool table as results come in.
     */
    private void startBackgroundDdNameProbe(int spoolCount) {
        Settings settings = SettingsHelper.load();
        String host = service.getHost();
        String user = service.getUser();
        int parallelConns = Math.max(1, settings.jesProbeParallelConnections);

        // Get password from LoginManager cache
        String password = LoginManager.getInstance().getCachedPassword(host, user);
        if (password == null) {
            LOG.warning("[JES] No cached password for background probe – skipping");
            return;
        }

        LOG.info("[JES] Starting background DDName probe for " + job.getJobId()
                + " (" + spoolCount + " files, " + parallelConns + " connections)");

        new SwingWorker<Map<Integer, String>, Void>() {
            @Override
            protected Map<Integer, String> doInBackground() {
                return JesFtpService.probeSpoolDdNamesParallel(
                        job.getJobId(), host, user, password, spoolCount, parallelConns);
            }

            @Override
            protected void done() {
                try {
                    Map<Integer, String> ddNames = get();
                    if (ddNames != null && !ddNames.isEmpty()) {
                        // Update the spool table with corrected DDNames
                        List<JesSpoolFile> currentFiles = new ArrayList<>();
                        for (int i = 0; i < spoolModel.getRowCount(); i++) {
                            JesSpoolFile orig = spoolModel.getFileAt(i);
                            String newDdName = ddNames.get(orig.getId());
                            if (newDdName != null && !newDdName.startsWith("SPOOL#")) {
                                currentFiles.add(new JesSpoolFile(
                                        orig.getId(), newDdName,
                                        orig.getStepName(), orig.getProcStep(),
                                        orig.getDsClass(), orig.getByteCount(),
                                        orig.getRecordCount()));
                            } else {
                                currentFiles.add(orig);
                            }
                        }
                        int selectedRow = spoolTable.getSelectedRow();
                        spoolModel.setFiles(currentFiles);
                        if (selectedRow >= 0 && selectedRow < currentFiles.size()) {
                            spoolTable.setRowSelectionInterval(selectedRow, selectedRow);
                        }
                        int updated = (int) ddNames.values().stream()
                                .filter(n -> !n.startsWith("SPOOL#")).count();
                        statusLabel.setText("✅ " + updated + "/" + spoolCount
                                + " DDNames per Probe aktualisiert");
                        LOG.info("[JES] Background probe updated " + updated + " DDNames");
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[JES] Background DDName probe failed", e);
                }
            }
        }.execute();
    }

    private static JPanel createInfoLabel(String label, String value) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        p.add(l);
        p.add(new JLabel(value != null ? value : "–"));
        return p;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FtpTab interface
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public String getTitle() {
        return "🖨️ " + job.getJobId() + " " + job.getJobName();
    }

    @Override
    public String getTooltip() {
        return "JES Spool – " + job.getJobId() + " " + job.getJobName()
                + " (" + job.getStatus() + ")"
                + (job.getRetCode() != null ? " RC=" + job.getRetCode() : "");
    }

    @Override public JComponent getComponent() { return mainPanel; }
    @Override public void onClose() { /* service is shared with ConnectionTab – don't close */ }
    @Override public void saveIfApplicable() { }
    @Override public void focusSearchField() {
        searchBar.focusAndSelectAll();
    }
    @Override public void searchFor(String s) {
        if (s != null && !s.isEmpty()) {
            searchBar.setText(s);
            searchInContent();
        }
    }

    @Override
    public String getPath() {
        return "jes://" + service.getUser() + "@" + service.getHost() + "/" + job.getJobId();
    }

    @Override
    public Type getType() { return Type.FILE; }

    @Override
    public String getContent() {
        return contentArea.getText();
    }

    @Override
    public void markAsChanged() { }

    // ═══════════════════════════════════════════════════════════════════
    //  Mermaid diagram view (read-only)
    // ═══════════════════════════════════════════════════════════════════

    private void toggleDiagramView() {
        diagramViewActive = !diagramViewActive;

        if (diagramViewActive) {
            String content = contentArea.getText();
            if (content == null || content.trim().isEmpty()) {
                diagramViewActive = false;
                diagramToggleButton.setSelected(false);
                return;
            }

            String mermaidCode = parseMermaidFromOutline(content);
            if (mermaidCode == null) {
                JOptionPane.showMessageDialog(mainPanel,
                        "Kein Diagramm erzeugbar — die Datei enthält keine erkennbare Struktur.",
                        "Diagramm", JOptionPane.INFORMATION_MESSAGE);
                diagramViewActive = false;
                diagramToggleButton.setSelected(false);
                return;
            }

            if (mermaidDiagramPanel == null) {
                mermaidDiagramPanel = new MermaidDiagramPanel(false); // always read-only for spool
            }
            mermaidDiagramPanel.setMermaidSource(mermaidCode);

            contentPanelRef.removeAll();
            contentPanelRef.add(mermaidDiagramPanel, BorderLayout.CENTER);
            contentPanelRef.revalidate();
            contentPanelRef.repaint();
        } else {
            // Restore normal content view
            contentPanelRef.removeAll();
            contentPanelRef.add(contentScrollRef, BorderLayout.CENTER);
            contentPanelRef.revalidate();
            contentPanelRef.repaint();
        }
    }

    private String parseMermaidFromOutline(String content) {
        JclOutlineModel model = null;

        if (isNaturalContent(content)) {
            model = new NaturalParser().parse(content, job.getJobName());
        } else if (isCobolContent(content)) {
            model = new CobolParser().parse(content, job.getJobName());
        } else if (isJclContent(content)) {
            model = new AntlrJclParser().parse(content, job.getJobName());
        }

        if (model == null || model.isEmpty()) return null;
        return OutlineToMermaidConverter.convert(model);
    }


    // ═══════════════════════════════════════════════════════════════════
    //  Spool table model
    // ═══════════════════════════════════════════════════════════════════

    private static class SpoolTableModel extends AbstractTableModel {
        private List<JesSpoolFile> files = new ArrayList<JesSpoolFile>();

        void setFiles(List<JesSpoolFile> files) {
            this.files = files != null ? files : new ArrayList<JesSpoolFile>();
            fireTableDataChanged();
        }

        JesSpoolFile getFileAt(int row) { return files.get(row); }

        @Override public int getRowCount() { return files.size(); }
        @Override public int getColumnCount() { return SPOOL_COLUMNS.length; }
        @Override public String getColumnName(int col) { return SPOOL_COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            JesSpoolFile f = files.get(row);
            switch (col) {
                case 0: return f.getId();
                case 1: return f.getDdName();
                case 2: return f.getStepName();
                case 3: return f.getProcStep();
                case 4: return f.getDsClass();
                case 5: return f.getRecordCount();
                case 6: return f.getByteCount();
                default: return "";
            }
        }
    }
}

