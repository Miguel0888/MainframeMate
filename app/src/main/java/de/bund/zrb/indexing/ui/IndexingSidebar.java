package de.bund.zrb.indexing.ui;

import de.bund.zrb.indexing.model.*;
import de.bund.zrb.indexing.service.IndexingService;
import de.bund.zrb.security.SecurityFilterService;

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
    private final JLabel scopeLabel;
    private final JLabel statusLabel;
    private final JLabel lastIndexedLabel;
    private final JLabel itemCountLabel;
    private final JCheckBox includeChildrenCheck;
    private final JSpinner depthSpinner;
    private JButton indexNowButton;

    // Track which source we're currently indexing (for listener updates)
    private volatile String activeSourceId = null;

    // Optional custom action for "Jetzt Indexieren" — set by parent tab (e.g. NdvConnectionTab)
    private Runnable customIndexAction;

    // Optional custom status supplier — returns [statusText, statusColor, itemCountText, lastIndexedText]
    // When set, refreshStatus() uses this instead of querying the IndexingService pipeline.
    private java.util.function.Supplier<String[]> customStatusSupplier;

    // ── Security Filter ──
    private final JLabel securityStatusLabel;
    /** Connection group for security filter (e.g. "FTP", "NDV", "LOCAL"). */
    private String securityGroup = "";
    /** Optional supplier for paths to whitelist/blacklist. Returns list of prefixed paths.
     *  If not set, uses currentPath. */
    private java.util.function.Supplier<List<String>> securityPathSupplier;

    public IndexingSidebar(SourceType sourceType) {
        this.indexingService = IndexingService.getInstance();
        this.sourceType = sourceType;

        // Register as listener for run completion updates
        indexingService.addListener(new IndexingService.IndexingListener() {
            @Override
            public void onRunStarted(String sourceId) {
                if (sourceId.equals(activeSourceId) || activeSourceId == null) {
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("⏳ Wird indexiert...");
                        statusLabel.setForeground(new Color(255, 152, 0));
                        itemCountLabel.setText("0 / ...");
                    });
                }
            }

            @Override
            public void onRunCompleted(String sourceId, IndexRunStatus result) {
                if (sourceId.equals(activeSourceId) || activeSourceId == null) {
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        activeSourceId = null; // reset so next refresh picks up any source
                        refreshStatus();
                    });
                }
            }

            @Override
            public void onRunFailed(String sourceId, String error) {
                if (sourceId.equals(activeSourceId) || activeSourceId == null) {
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("❌ Fehler: " + truncate(error, 25));
                        statusLabel.setForeground(new Color(244, 67, 54));
                    });
                }
            }

            @Override
            public void onProgress(String sourceId, int current, int total) {
                if (sourceId.equals(activeSourceId) || activeSourceId == null) {
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        itemCountLabel.setText(current + " / " + total);
                        statusLabel.setText("⏳ Wird indexiert...");
                        statusLabel.setForeground(new Color(255, 152, 0));
                    });
                }
            }
        });

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

        scopeLabel = createValueLabel("Alles");
        scopeLabel.setForeground(new Color(33, 150, 243));
        add(createRow("Umfang:", scopeLabel));

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

        indexNowButton = new JButton("▶ Jetzt Indexieren (einmalig)");
        indexNowButton.setAlignmentX(LEFT_ALIGNMENT);
        indexNowButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        indexNowButton.setToolTipText("Startet sofortige einmalige Indexierung für diesen Pfad");
        indexNowButton.addActionListener(e -> indexNow());
        add(indexNowButton);

        add(Box.createVerticalStrut(16));

        // ── Security / Filter ──
        add(createSectionHeader("\uD83D\uDD12 Sicherheitsfilter"));

        securityStatusLabel = createValueLabel("-");
        add(createRow("Filter:", securityStatusLabel));

        add(Box.createVerticalStrut(4));

        JButton whitelistButton = new JButton("\u2705 Auf Whitelist setzen");
        whitelistButton.setAlignmentX(LEFT_ALIGNMENT);
        whitelistButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        whitelistButton.setToolTipText("Aktuellen Pfad / Auswahl auf die Whitelist setzen (Caching/Indexierung erlauben)");
        whitelistButton.addActionListener(e -> addCurrentToWhitelist());
        add(whitelistButton);

        add(Box.createVerticalStrut(4));

        JButton blacklistButton = new JButton("\u26D4 Auf Blacklist setzen");
        blacklistButton.setAlignmentX(LEFT_ALIGNMENT);
        blacklistButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        blacklistButton.setToolTipText("Aktuellen Pfad / Auswahl auf die Blacklist setzen (Caching/Indexierung sperren)");
        blacklistButton.addActionListener(e -> addCurrentToBlacklist());
        add(blacklistButton);

        add(Box.createVerticalStrut(4));

        JButton removeFilterButton = new JButton("\u21A9 Filter entfernen");
        removeFilterButton.setAlignmentX(LEFT_ALIGNMENT);
        removeFilterButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        removeFilterButton.setToolTipText("Aktuellen Pfad von Whitelist und Blacklist entfernen");
        removeFilterButton.addActionListener(e -> removeCurrentFromFilter());
        add(removeFilterButton);

        add(Box.createVerticalGlue());

        // ── Info ──
        JLabel infoLabel = new JLabel("<html><small>"
                + "\u201EIndexieren\u201C erstellt eine Regel mit dem Standard-Zeitplan aus den Einstellungen.<br>"
                + "\u201EJetzt Indexieren\u201C f\u00FChrt die Indexierung nur einmal sofort aus.<br>"
                + "\uD83D\uDD12 Der Sicherheitsfilter steuert, ob Dateien gecacht/indexiert werden d\u00FCrfen."
                + "</small></html>");
        infoLabel.setForeground(Color.GRAY);
        infoLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(infoLabel);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Set a custom action to execute when "Jetzt Indexieren" is clicked.
     * If set, the default pipeline-based indexing is bypassed.
     * Used by NdvConnectionTab to route indexing through its own selection-aware logic.
     */
    public void setCustomIndexAction(Runnable action) {
        this.customIndexAction = action;
    }

    /**
     * Set a custom status supplier for refreshStatus().
     * Returns a String array: [statusText, colorHex, itemCountText, lastIndexedText].
     * When set, refreshStatus() uses this instead of querying IndexingService.
     * Used by NdvConnectionTab to show NDV cache status.
     */
    public void setCustomStatusSupplier(java.util.function.Supplier<String[]> supplier) {
        this.customStatusSupplier = supplier;
    }

    /**
     * Set the connection group for security filter actions (e.g. "FTP", "NDV", "LOCAL").
     */
    public void setSecurityGroup(String group) {
        this.securityGroup = group != null ? group.toUpperCase() : "";
    }

    /**
     * Set a custom supplier for paths to whitelist/blacklist.
     * Used by connection tabs that have multi-selection.
     * Should return a list of fully prefixed paths (e.g. ["ftp://host/dir/file.txt"]).
     */
    public void setSecurityPathSupplier(java.util.function.Supplier<List<String>> supplier) {
        this.securityPathSupplier = supplier;
    }

    /**
     * Update sidebar for the given path.
     */
    public void setCurrentPath(String path) {
        this.currentPath = path;
        pathLabel.setText(truncate(path, 30));
        pathLabel.setToolTipText(path);
        refreshStatus();
        refreshSecurityStatus();
    }

    /**
     * Set the indexing scope description (shown in the "Umfang:" row and on the button tooltip).
     * E.g. "Auswahl (5 Objekte)" or "Alle 320 Objekte" or "3 Bibliotheken".
     */
    public void setScopeInfo(String scopeText) {
        scopeLabel.setText(truncate(scopeText, 28));
        scopeLabel.setToolTipText(scopeText);
        indexNowButton.setToolTipText("Jetzt indexieren: " + scopeText);
    }

    /**
     * Update progress display from external source (e.g., NDV prefetch).
     * Uses "Wird indexiert..." label. Call from EDT.
     *
     * @param current number of items processed so far
     * @param total   total number of items
     */
    public void updateProgress(int current, int total) {
        updateProgress(current, total, false);
    }

    /**
     * Update progress display from external source.
     * Call from EDT.
     *
     * @param current    number of items processed so far
     * @param total      total number of items
     * @param isCaching  true for automatic background caching, false for explicit user-triggered indexing
     */
    public void updateProgress(int current, int total, boolean isCaching) {
        itemCountLabel.setText(current + " / " + total);
        if (isCaching) {
            statusLabel.setText("\uD83D\uDD04 Wird gecacht...");
            statusLabel.setForeground(new Color(100, 149, 237)); // cornflower blue
        } else {
            statusLabel.setText("⏳ Wird indexiert...");
            statusLabel.setForeground(new Color(255, 152, 0));   // orange
        }
    }

    /**
     * Signal that external indexing is complete.
     * Call from EDT.
     *
     * @param total   total items found
     * @param indexed number successfully indexed
     */
    public void updateComplete(int total, int indexed) {
        updateComplete(total, indexed, false);
    }

    /**
     * Signal that external caching/indexing is complete.
     * Call from EDT.
     *
     * @param total      total items found
     * @param indexed    number successfully indexed/cached
     * @param isCaching  true for automatic background caching, false for explicit user-triggered indexing
     */
    public void updateComplete(int total, int indexed, boolean isCaching) {
        itemCountLabel.setText(indexed + " / " + total);
        if (indexed > 0) {
            if (isCaching) {
                statusLabel.setText("✅ Gecacht");
            } else {
                statusLabel.setText("✅ Indexiert");
            }
            statusLabel.setForeground(new Color(76, 175, 80));
        } else {
            if (isCaching) {
                statusLabel.setText("⬜ Keine Änderungen");
            } else {
                statusLabel.setText("⬜ Nicht indexiert");
            }
            statusLabel.setForeground(Color.GRAY);
        }
        lastIndexedLabel.setText(DATE_FMT.format(new Date(System.currentTimeMillis())));
    }

    /**
     * Signal that external indexing failed.
     * Call from EDT.
     */
    public void updateError(String message) {
        statusLabel.setText("❌ " + truncate(message, 25));
        statusLabel.setForeground(new Color(244, 67, 54));
    }

    /**
     * Refresh the index status display.
     */
    public void refreshStatus() {
        // Use custom supplier if set (e.g. NDV cache-aware status)
        if (customStatusSupplier != null) {
            try {
                String[] info = customStatusSupplier.get();
                if (info != null && info.length >= 4) {
                    statusLabel.setText(info[0]);
                    statusLabel.setForeground(Color.decode(info[1]));
                    itemCountLabel.setText(info[2]);
                    lastIndexedLabel.setText(info[3]);
                    return;
                }
            } catch (Exception ignored) {
                // fall through to default
            }
        }

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

        // Apply typSchluessel-specific defaults
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
        // Delegate to custom action if set (e.g. NDV selection-aware indexing)
        if (customIndexAction != null) {
            customIndexAction.run();
            return;
        }

        if (currentPath == null || currentPath.isEmpty()) return;

        // Check if there's already a matching source
        IndexSource matchingSource = findMatchingSource();
        if (matchingSource != null) {
            activeSourceId = matchingSource.getSourceId();
            indexingService.runNow(matchingSource.getSourceId());
            statusLabel.setText("\u23F3 Wird indexiert...");
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
        activeSourceId = source.getSourceId();
        indexingService.runNow(source.getSourceId());

        statusLabel.setText("\u23F3 Wird indexiert...");
        statusLabel.setForeground(new Color(255, 152, 0));
    }

    // ═══════════════════════════════════════════════════════════════
    //  Security Filter Actions
    // ═══════════════════════════════════════════════════════════════

    private List<String> getSecurityPaths() {
        if (securityPathSupplier != null) {
            List<String> paths = securityPathSupplier.get();
            if (paths != null && !paths.isEmpty()) return paths;
        }
        // Fall back to current path
        if (currentPath != null && !currentPath.isEmpty()) {
            return Collections.singletonList(currentPath);
        }
        return Collections.emptyList();
    }

    private void addCurrentToWhitelist() {
        if (securityGroup.isEmpty()) return;
        List<String> paths = getSecurityPaths();
        if (paths.isEmpty()) return;
        SecurityFilterService sfs = SecurityFilterService.getInstance();
        for (String p : paths) {
            sfs.addToWhitelist(securityGroup, p);
        }
        refreshSecurityStatus();
        javax.swing.JOptionPane.showMessageDialog(this,
                paths.size() + " Pfad(e) auf die Whitelist gesetzt.",
                "Whitelist", javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }

    private void addCurrentToBlacklist() {
        if (securityGroup.isEmpty()) return;
        List<String> paths = getSecurityPaths();
        if (paths.isEmpty()) return;

        // ── Check if any of the paths have cached or indexed data ──
        int cachedCount = 0;
        int indexedCount = 0;
        try {
            de.bund.zrb.archive.store.CacheRepository cacheRepo =
                    de.bund.zrb.archive.store.CacheRepository.getInstance();
            de.bund.zrb.rag.service.RagService ragService =
                    de.bund.zrb.rag.service.RagService.getInstance();
            Map<String, String> allIndexed = ragService.listAllIndexedDocuments();

            for (String p : paths) {
                // H2 cache uses the same URL scheme as security paths (ftp://, ndv://, local://)
                cachedCount += cacheRepo.countByUrlPrefix(p);

                // RAG document IDs use scheme-uppercase prefix: ftp://host/x → FTP:host/x
                String ragPrefix = toRagPrefix(p);
                if (ragPrefix != null) {
                    for (String docId : allIndexed.keySet()) {
                        if (docId.startsWith(ragPrefix)) {
                            indexedCount++;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            // If check fails, proceed without warning
        }

        if (cachedCount > 0 || indexedCount > 0) {
            StringBuilder msg = new StringBuilder();
            msg.append("Für die ausgewählten Pfade existieren bereits gespeicherte Daten:\n\n");
            if (cachedCount > 0) {
                msg.append("  • ").append(cachedCount).append(" Cache-Einträge (H2)\n");
            }
            if (indexedCount > 0) {
                msg.append("  • ").append(indexedCount).append(" indexierte Dokumente (RAG/Lucene)\n");
            }
            msg.append("\nSollen diese Daten gelöscht werden?");

            int choice = JOptionPane.showOptionDialog(this,
                    msg.toString(),
                    "Blacklist — Gecachte Daten löschen?",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    new String[]{"Löschen & Blacklisten", "Nur Blacklisten", "Abbrechen"},
                    "Löschen & Blacklisten");

            if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
                return; // User cancelled
            }

            if (choice == 0) {
                // Delete cached/indexed data
                purgeDataForPaths(paths);
            }
        }

        // Add to blacklist
        SecurityFilterService sfs = SecurityFilterService.getInstance();
        for (String p : paths) {
            sfs.addToBlacklist(securityGroup, p);
        }
        refreshSecurityStatus();
        JOptionPane.showMessageDialog(this,
                paths.size() + " Pfad(e) auf die Blacklist gesetzt.",
                "Blacklist", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Convert a security-filter path (e.g. "ftp://host/dir") to the RAG document ID prefix
     * (e.g. "FTP:host/dir"). Returns null if the scheme is unknown.
     */
    private static String toRagPrefix(String securityPath) {
        int schemeEnd = securityPath.indexOf("://");
        if (schemeEnd < 0) return null;
        String scheme = securityPath.substring(0, schemeEnd).toUpperCase();
        String rest = securityPath.substring(schemeEnd + 3);
        return scheme + ":" + rest;
    }

    /**
     * Delete all H2 cache entries and RAG-indexed documents whose URL/docId
     * matches any of the given security paths (prefix match).
     */
    private void purgeDataForPaths(List<String> paths) {
        try {
            de.bund.zrb.archive.store.CacheRepository cacheRepo =
                    de.bund.zrb.archive.store.CacheRepository.getInstance();
            de.bund.zrb.rag.service.RagService ragService =
                    de.bund.zrb.rag.service.RagService.getInstance();

            for (String p : paths) {
                // Remove H2 cache entries by URL prefix
                List<de.bund.zrb.archive.model.ArchiveEntry> entries =
                        cacheRepo.findByUrlPrefixWithMetadata(p);
                for (de.bund.zrb.archive.model.ArchiveEntry entry : entries) {
                    cacheRepo.delete(entry.getEntryId());
                }

                // Remove RAG-indexed documents
                String ragPrefix = toRagPrefix(p);
                if (ragPrefix != null) {
                    Map<String, String> allDocs = ragService.listAllIndexedDocuments();
                    for (String docId : allDocs.keySet()) {
                        if (docId.startsWith(ragPrefix)) {
                            ragService.removeDocument(docId);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Fehler beim Löschen der gecachten Daten:\n" + ex.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeCurrentFromFilter() {
        if (securityGroup.isEmpty()) return;
        List<String> paths = getSecurityPaths();
        if (paths.isEmpty()) return;
        SecurityFilterService sfs = SecurityFilterService.getInstance();
        for (String p : paths) {
            sfs.removeFromWhitelist(securityGroup, p);
            sfs.removeFromBlacklist(securityGroup, p);
        }
        refreshSecurityStatus();
    }

    /**
     * Refresh the security status label based on the current path.
     */
    public void refreshSecurityStatus() {
        if (securityGroup.isEmpty() || currentPath == null || currentPath.isEmpty()) {
            securityStatusLabel.setText("-");
            securityStatusLabel.setForeground(Color.GRAY);
            return;
        }
        SecurityFilterService sfs = SecurityFilterService.getInstance();
        if (sfs.isBlacklisted(securityGroup, currentPath)) {
            securityStatusLabel.setText("\u26D4 Gesperrt");
            securityStatusLabel.setForeground(new Color(244, 67, 54));
        } else if (sfs.isWhitelisted(securityGroup, currentPath)) {
            securityStatusLabel.setText("\u2705 Erlaubt");
            securityStatusLabel.setForeground(new Color(76, 175, 80));
        } else if (sfs.isBlacklistAll(securityGroup)) {
            securityStatusLabel.setText("\u26D4 Nicht auf Whitelist");
            securityStatusLabel.setForeground(new Color(255, 152, 0));
        } else {
            securityStatusLabel.setText("\u2705 Erlaubt (Standard)");
            securityStatusLabel.setForeground(new Color(76, 175, 80));
        }
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
