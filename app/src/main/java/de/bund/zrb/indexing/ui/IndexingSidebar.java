package de.bund.zrb.indexing.ui;

import de.bund.zrb.indexing.model.*;
import de.bund.zrb.indexing.service.IndexingService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Sidebar panel for Connection Tabs showing indexing status and actions
 * for the current path. Can be toggled on/off.
 *
 * Features:
 * - Shows index status of current directory/mailbox
 * - "Indexieren" button: creates a recurring rule for this path
 * - "Jetzt Indexieren" button: runs one-time indexing immediately
 * - Checkbox for including child elements
 * - Depth spinner (0 = unlimited)
 * - Status info: last indexed, item counts
 */
public class IndexingSidebar extends JPanel {

    private static final int SIDEBAR_WIDTH = 280;
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    private final IndexingService indexingService;
    private final SourceType sourceType;

    // Current state
    private String currentPath = "";

    // UI components
    private final JLabel pathLabel;
    private final JLabel statusLabel;
    private final JLabel lastIndexedLabel;
    private final JLabel itemCountLabel;
    private final JCheckBox includeChildrenCheck;
    private final JSpinner depthSpinner;

    public IndexingSidebar(SourceType sourceType) {
        this.indexingService = IndexingService.getInstance();
        this.sourceType = sourceType;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(12, 12, 12, 12));
        setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));
        setBackground(new Color(248, 249, 250));

        // ── Header ──
        add(createSectionHeader("📊 Indexierung"));

        // ── Path info ──
        pathLabel = createValueLabel("-");
        pathLabel.setToolTipText("");
        add(createRow("Pfad:", pathLabel));

        statusLabel = createValueLabel("⬜ Nicht indexiert");
        add(createRow("Status:", statusLabel));

        lastIndexedLabel = createValueLabel("-");
        add(createRow("Zuletzt:", lastIndexedLabel));

        itemCountLabel = createValueLabel("-");
        add(createRow("Items:", itemCountLabel));

        add(Box.createVerticalStrut(16));

        // ── Options ──
        add(createSectionHeader("⚙ Optionen"));

        includeChildrenCheck = new JCheckBox("Kind-Elemente einschließen", true);
        includeChildrenCheck.setOpaque(false);
        includeChildrenCheck.setAlignmentX(LEFT_ALIGNMENT);
        includeChildrenCheck.setFont(includeChildrenCheck.getFont().deriveFont(Font.PLAIN, 11f));
        add(includeChildrenCheck);

        add(Box.createVerticalStrut(4));

        JPanel depthPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        depthPanel.setOpaque(false);
        depthPanel.setAlignmentX(LEFT_ALIGNMENT);
        depthPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JLabel depthLabel = new JLabel("Tiefe (0=∞):");
        depthLabel.setFont(depthLabel.getFont().deriveFont(Font.PLAIN, 11f));
        depthLabel.setForeground(Color.GRAY);
        depthPanel.add(depthLabel);
        depthSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        depthSpinner.setPreferredSize(new Dimension(60, 22));
        depthPanel.add(depthSpinner);
        add(depthPanel);

        add(Box.createVerticalStrut(16));

        // ── Actions ──
        add(createSectionHeader("▶ Aktionen"));

        JButton indexButton = new JButton("📅 Indexieren (Regel anlegen)");
        indexButton.setAlignmentX(LEFT_ALIGNMENT);
        indexButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        indexButton.setToolTipText("Erstellt eine wiederkehrende Indexierungs-Regel für diesen Pfad");
        indexButton.addActionListener(e -> createIndexRule());
        add(indexButton);

        add(Box.createVerticalStrut(4));

        JButton indexNowButton = new JButton("▶ Jetzt Indexieren (einmalig)");
        indexNowButton.setAlignmentX(LEFT_ALIGNMENT);
        indexNowButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        indexNowButton.setToolTipText("Startet sofortige einmalige Indexierung für diesen Pfad");
        indexNowButton.addActionListener(e -> indexNow());
        add(indexNowButton);

        add(Box.createVerticalGlue());

        // ── Info ──
        JLabel infoLabel = new JLabel("<html><small>"
                + "\u201EIndexieren\u201C erstellt eine Regel mit dem Standard-Zeitplan aus den Einstellungen.<br>"
                + "\u201EJetzt Indexieren\u201C f\u00FChrt die Indexierung nur einmal sofort aus."
                + "</small></html>");
        infoLabel.setForeground(Color.GRAY);
        infoLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(infoLabel);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Update sidebar for the given path.
     */
    public void setCurrentPath(String path) {
        this.currentPath = path;
        pathLabel.setText(truncate(path, 30));
        pathLabel.setToolTipText(path);
        refreshStatus();
    }

    /**
     * Refresh the index status display.
     */
    public void refreshStatus() {
        // Find existing source that covers this path
        IndexSource matchingSource = findMatchingSource();
        if (matchingSource != null) {
            Map<IndexItemState, Integer> counts = indexingService.getItemCounts(matchingSource.getSourceId());
            int indexed = counts.getOrDefault(IndexItemState.INDEXED, 0);
            int total = 0;
            for (int v : counts.values()) total += v;

            if (indexed > 0) {
                statusLabel.setText("✅ Indexiert");
                statusLabel.setForeground(new Color(76, 175, 80));
            } else if (total > 0) {
                statusLabel.setText("⏳ Ausstehend");
                statusLabel.setForeground(new Color(255, 152, 0));
            } else {
                statusLabel.setText("⬜ Nicht indexiert");
                statusLabel.setForeground(Color.GRAY);
            }

            itemCountLabel.setText(indexed + " / " + total);

            IndexRunStatus lastRun = indexingService.getLastSuccessfulRun(matchingSource.getSourceId());
            if (lastRun != null && lastRun.getCompletedAt() > 0) {
                lastIndexedLabel.setText(DATE_FMT.format(new Date(lastRun.getCompletedAt())));
            } else {
                lastIndexedLabel.setText("-");
            }
        } else {
            statusLabel.setText("⬜ Nicht indexiert");
            statusLabel.setForeground(Color.GRAY);
            lastIndexedLabel.setText("-");
            itemCountLabel.setText("-");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Actions
    // ═══════════════════════════════════════════════════════════════

    /**
     * Create a recurring index rule for the current path.
     */
    private void createIndexRule() {
        if (currentPath == null || currentPath.isEmpty()) return;

        IndexSource source = new IndexSource();
        source.setName(truncate(currentPath, 40));
        source.setSourceType(sourceType);
        source.setEnabled(true);

        List<String> paths = new ArrayList<>();
        paths.add(currentPath);
        source.setScopePaths(paths);

        int depth = (Integer) depthSpinner.getValue();
        source.setMaxDepth(depth == 0 ? Integer.MAX_VALUE : depth);

        if (!includeChildrenCheck.isSelected()) {
            source.setMaxDepth(1);
        }

        // Use default schedule settings (DAILY at 12:00, 30 min)
        source.setScheduleMode(ScheduleMode.DAILY);
        source.setStartHour(12);
        source.setStartMinute(0);
        source.setMaxDurationMinutes(30);
        source.setIndexDirection(IndexDirection.NEWEST_FIRST);
        source.setChangeDetection(ChangeDetectionMode.MTIME_THEN_HASH);
        source.setFulltextEnabled(true);
        source.setEmbeddingEnabled(false);

        // Apply type-specific defaults
        if (sourceType == SourceType.LOCAL) {
            source.setIncludePatterns(java.util.Arrays.asList(
                    "*.pdf", "*.docx", "*.doc", "*.xlsx", "*.xls", "*.pptx", "*.ppt",
                    "*.txt", "*.md", "*.eml", "*.msg", "*.csv", "*.html", "*.xml", "*.json"
            ));
        }

        indexingService.saveSource(source);
        refreshStatus();
        JOptionPane.showMessageDialog(this,
                "Indexierungs-Regel erstellt:\n" + source.getName()
                        + "\n\nZeitplan: Täglich um 12:00 Uhr"
                        + "\nMax. Dauer: 30 Minuten"
                        + "\nReihenfolge: Neueste zuerst"
                        + "\n\nKann unter Einstellungen → Indexierung angepasst werden.",
                "Regel erstellt", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Run one-time indexing for the current path immediately.
     * Does NOT create a recurring rule.
     */
    private void indexNow() {
        if (currentPath == null || currentPath.isEmpty()) return;

        // Check if there's already a matching source
        IndexSource matchingSource = findMatchingSource();
        if (matchingSource != null) {
            indexingService.runNow(matchingSource.getSourceId());
            statusLabel.setText("⏳ Wird indexiert...");
            statusLabel.setForeground(new Color(255, 152, 0));
            return;
        }

        // Create a temporary source (MANUAL, one-time)
        IndexSource source = new IndexSource();
        source.setName("[Einmalig] " + truncate(currentPath, 30));
        source.setSourceType(sourceType);
        source.setEnabled(true);
        source.setScheduleMode(ScheduleMode.MANUAL);

        List<String> paths = new ArrayList<>();
        paths.add(currentPath);
        source.setScopePaths(paths);

        int depth = (Integer) depthSpinner.getValue();
        source.setMaxDepth(depth == 0 ? Integer.MAX_VALUE : depth);
        if (!includeChildrenCheck.isSelected()) {
            source.setMaxDepth(1);
        }

        source.setFulltextEnabled(true);
        source.setEmbeddingEnabled(false);
        source.setChangeDetection(ChangeDetectionMode.MTIME_THEN_HASH);

        if (sourceType == SourceType.LOCAL) {
            source.setIncludePatterns(java.util.Arrays.asList(
                    "*.pdf", "*.docx", "*.doc", "*.xlsx", "*.xls", "*.pptx", "*.ppt",
                    "*.txt", "*.md", "*.eml", "*.msg", "*.csv", "*.html", "*.xml", "*.json"
            ));
        }

        indexingService.saveSource(source);
        indexingService.runNow(source.getSourceId());

        statusLabel.setText("⏳ Wird indexiert...");
        statusLabel.setForeground(new Color(255, 152, 0));
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════

    private IndexSource findMatchingSource() {
        for (IndexSource source : indexingService.getAllSources()) {
            if (source.getSourceType() != sourceType) continue;
            for (String scope : source.getScopePaths()) {
                if (currentPath.startsWith(scope) || scope.equals(currentPath)) {
                    return source;
                }
            }
        }
        return null;
    }

    private JPanel createSectionHeader(String title) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        panel.add(label);
        return panel;
    }

    private JPanel createRow(String labelText, JLabel valueLabel) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        panel.setBorder(new EmptyBorder(2, 0, 2, 0));
        JLabel label = new JLabel(labelText);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
        label.setForeground(Color.GRAY);
        label.setPreferredSize(new Dimension(65, 18));
        panel.add(label, BorderLayout.WEST);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.PLAIN, 11f));
        panel.add(valueLabel, BorderLayout.CENTER);
        return panel;
    }

    private JLabel createValueLabel(String text) {
        return new JLabel(text);
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return "..." + text.substring(text.length() - max + 3);
    }
}
