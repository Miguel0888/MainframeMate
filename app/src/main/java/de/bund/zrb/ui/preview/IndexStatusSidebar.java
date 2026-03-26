package de.bund.zrb.ui.preview;

import de.bund.zrb.rag.service.RagService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Sidebar panel showing file details and index status.
 * Supports temporary context-specific override panes (e.g. diagram details)
 * with a dot-indicator switcher at the bottom.
 */
public class IndexStatusSidebar extends JPanel {

    private static final int SIDEBAR_WIDTH = 280;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    // ── Default content labels ──
    private final JLabel fileNameLabel;
    private final JLabel filePathLabel;
    private final JLabel fileSizeLabel;
    private final JLabel mimeTypeLabel;
    private final JLabel encodingLabel;
    private final JLabel lastModifiedLabel;
    private final JLabel sourceTypeLabel;

    private final JLabel extractorLabel;
    private final JLabel warningsLabel;
    private final JPanel warningsPanel;

    private final JLabel luceneStatusLabel;
    private final JLabel embeddingsStatusLabel;
    private final JLabel chunkCountLabel;
    private final JLabel chunkParamsLabel;
    private final JLabel embeddingModelLabel;
    private final JLabel indexedAtLabel;

    private String documentId;
    private Runnable indexAction;
    private final JButton indexNowButton;

    // ── Override pane infrastructure ──
    private static final String DEFAULT_PAGE = "__default__";
    private final JPanel contentCards;              // CardLayout container
    private final CardLayout cardLayout;
    private final JPanel defaultPane;               // the original file-details content
    private final LinkedHashMap<String, JPanel> overridePages = new LinkedHashMap<String, JPanel>(); // id → panel
    private final JPanel dotBar;                    // dot indicator bar (bottom)
    private String activePage = DEFAULT_PAGE;

    public IndexStatusSidebar() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));
        setBackground(new Color(248, 249, 250));

        // ── Card layout for pane switching ──
        cardLayout = new CardLayout();
        contentCards = new JPanel(cardLayout);
        contentCards.setOpaque(false);

        // ── Build default pane ──
        defaultPane = new JPanel();
        defaultPane.setLayout(new BoxLayout(defaultPane, BoxLayout.Y_AXIS));
        defaultPane.setBorder(new EmptyBorder(12, 12, 12, 12));
        defaultPane.setBackground(new Color(248, 249, 250));

        // === File Details Section ===
        defaultPane.add(createSectionHeader("\uD83D\uDCC1 Datei-Details"));

        fileNameLabel = createValueLabel("-");
        defaultPane.add(createRow("Name:", fileNameLabel));

        filePathLabel = createValueLabel("-");
        filePathLabel.setToolTipText("");
        defaultPane.add(createRow("Pfad:", filePathLabel));

        fileSizeLabel = createValueLabel("-");
        defaultPane.add(createRow("Größe:", fileSizeLabel));

        mimeTypeLabel = createValueLabel("-");
        defaultPane.add(createRow("MIME-Type:", mimeTypeLabel));

        encodingLabel = createValueLabel("-");
        defaultPane.add(createRow("Encoding:", encodingLabel));

        lastModifiedLabel = createValueLabel("-");
        defaultPane.add(createRow("Geändert:", lastModifiedLabel));

        sourceTypeLabel = createValueLabel("-");
        defaultPane.add(createRow("Quelle:", sourceTypeLabel));

        defaultPane.add(Box.createVerticalStrut(16));

        // === Extraction Section ===
        defaultPane.add(createSectionHeader("\uD83D\uDD27 Extraktion"));

        extractorLabel = createValueLabel("-");
        defaultPane.add(createRow("Extractor:", extractorLabel));

        warningsLabel = createValueLabel("0");
        defaultPane.add(createRow("Warnungen:", warningsLabel));

        warningsPanel = new JPanel();
        warningsPanel.setLayout(new BoxLayout(warningsPanel, BoxLayout.Y_AXIS));
        warningsPanel.setBackground(new Color(255, 243, 205));
        warningsPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 193, 7)),
                new EmptyBorder(8, 8, 8, 8)
        ));
        warningsPanel.setVisible(false);
        warningsPanel.setAlignmentX(LEFT_ALIGNMENT);
        defaultPane.add(warningsPanel);

        defaultPane.add(Box.createVerticalStrut(16));

        // === Index Status Section ===
        defaultPane.add(createSectionHeader("\uD83D\uDCCA Index-Status"));

        luceneStatusLabel = createStatusLabel("Nicht indexiert", IndexStatus.NOT_INDEXED);
        defaultPane.add(createRow("Lucene:", luceneStatusLabel));

        embeddingsStatusLabel = createStatusLabel("Nicht indexiert", IndexStatus.NOT_INDEXED);
        defaultPane.add(createRow("Embeddings:", embeddingsStatusLabel));

        chunkCountLabel = createValueLabel("-");
        defaultPane.add(createRow("Chunks:", chunkCountLabel));

        chunkParamsLabel = createValueLabel("-");
        defaultPane.add(createRow("Chunk-Params:", chunkParamsLabel));

        embeddingModelLabel = createValueLabel("-");
        defaultPane.add(createRow("Embedding-Modell:", embeddingModelLabel));

        indexedAtLabel = createValueLabel("-");
        defaultPane.add(createRow("Indexiert am:", indexedAtLabel));

        defaultPane.add(Box.createVerticalStrut(8));
        indexNowButton = new JButton("\uD83D\uDCCA Jetzt indexieren");
        indexNowButton.setAlignmentX(LEFT_ALIGNMENT);
        indexNowButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        indexNowButton.setFont(indexNowButton.getFont().deriveFont(Font.PLAIN, 11f));
        indexNowButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (indexAction != null) indexAction.run();
            }
        });
        defaultPane.add(indexNowButton);
        defaultPane.add(Box.createVerticalGlue());

        // Wrap default in scroll
        JScrollPane defaultScroll = new JScrollPane(defaultPane);
        defaultScroll.setBorder(null);
        defaultScroll.getVerticalScrollBar().setUnitIncrement(12);
        contentCards.add(defaultScroll, DEFAULT_PAGE);

        // ── Dot indicator bar (initially hidden) ──
        dotBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        dotBar.setOpaque(false);
        dotBar.setVisible(false);
        dotBar.setBorder(new EmptyBorder(2, 0, 4, 0));

        add(contentCards, BorderLayout.CENTER);
        add(dotBar, BorderLayout.SOUTH);
    }

    // ═══════════════════════════════════════════════════════════
    //  Override pane API
    // ═══════════════════════════════════════════════════════════

    /**
     * Push an override pane. If an override with the same id exists, it is replaced.
     * The sidebar automatically switches to the new override and shows the dot indicator.
     */
    public void pushOverride(String id, JPanel content) {
        if (id == null || id.equals(DEFAULT_PAGE)) throw new IllegalArgumentException("reserved id");
        // Remove existing override with same id
        if (overridePages.containsKey(id)) {
            overridePages.remove(id);
        }
        overridePages.put(id, content);
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(12);
        // Rebuild to ensure correct CardLayout state
        rebuildCardLayout();
        activePage = id;
        cardLayout.show(contentCards, id);
        rebuildDots();
    }


    /**
     * Remove an override pane by id. If the removed pane was active, switches back to default.
     */
    public void removeOverride(String id) {
        if (id == null || !overridePages.containsKey(id)) return;
        overridePages.remove(id);
        rebuildCardLayout();
        if (id.equals(activePage)) {
            activePage = DEFAULT_PAGE;
            cardLayout.show(contentCards, DEFAULT_PAGE);
        }
        rebuildDots();
    }

    /**
     * Remove all override panes and switch to default.
     */
    public void clearOverrides() {
        overridePages.clear();
        rebuildCardLayout();
        activePage = DEFAULT_PAGE;
        cardLayout.show(contentCards, DEFAULT_PAGE);
        rebuildDots();
    }

    /**
     * Switch to a specific page by id.
     */
    public void switchToPage(String id) {
        if (DEFAULT_PAGE.equals(id) || overridePages.containsKey(id)) {
            activePage = id;
            cardLayout.show(contentCards, id);
            rebuildDots();
        }
    }

    /**
     * @return true if any override pane is currently registered
     */
    public boolean hasOverrides() {
        return !overridePages.isEmpty();
    }

    private void rebuildCardLayout() {
        contentCards.removeAll();
        JScrollPane defaultScroll = new JScrollPane(defaultPane);
        defaultScroll.setBorder(null);
        defaultScroll.getVerticalScrollBar().setUnitIncrement(12);
        contentCards.add(defaultScroll, DEFAULT_PAGE);
        for (Map.Entry<String, JPanel> entry : overridePages.entrySet()) {
            JScrollPane scroll = new JScrollPane(entry.getValue());
            scroll.setBorder(null);
            scroll.getVerticalScrollBar().setUnitIncrement(12);
            contentCards.add(scroll, entry.getKey());
        }
        cardLayout.show(contentCards, activePage);
        contentCards.revalidate();
        contentCards.repaint();
    }

    private void rebuildDots() {
        dotBar.removeAll();
        if (overridePages.isEmpty()) {
            dotBar.setVisible(false);
            dotBar.revalidate();
            return;
        }

        dotBar.setVisible(true);
        // Default dot
        dotBar.add(createDot(DEFAULT_PAGE, "\uD83D\uDCC1", "Datei-Details")); // 📁
        // Override dots
        for (String id : overridePages.keySet()) {
            dotBar.add(createDot(id, "\u25CF", id)); // ● filled circle
        }
        dotBar.revalidate();
        dotBar.repaint();
    }

    private JLabel createDot(final String pageId, String symbol, String tooltip) {
        final boolean isActive = pageId.equals(activePage);
        final JLabel dot = new JLabel(isActive ? "\u25CF" : "\u25CB"); // ● vs ○
        dot.setFont(dot.getFont().deriveFont(Font.PLAIN, 14f));
        dot.setForeground(isActive ? new Color(66, 133, 244) : new Color(180, 180, 180));
        dot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        dot.setToolTipText(tooltip);
        dot.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                switchToPage(pageId);
            }
        });
        return dot;
    }

    // ═══════════════════════════════════════════════════════════
    //  Helper methods
    // ═══════════════════════════════════════════════════════════

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
        label.setPreferredSize(new Dimension(90, 18));
        panel.add(label, BorderLayout.WEST);

        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.PLAIN, 11f));
        panel.add(valueLabel, BorderLayout.CENTER);

        return panel;
    }

    private JLabel createValueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
        return label;
    }

    private JLabel createStatusLabel(String text, IndexStatus status) {
        JLabel label = new JLabel(status.getIcon() + " " + text);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
        label.setForeground(status.getColor());
        return label;
    }

    // === Public API ===

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
        indexNowButton.setEnabled(documentId != null && !documentId.isEmpty());
        refreshIndexStatus();
    }

    public void setIndexAction(Runnable action) {
        this.indexAction = action;
    }

    public void setFileDetails(String name, String path, Long sizeBytes, String mimeType,
                               String encoding, Long lastModifiedMs, String sourceType) {
        fileNameLabel.setText(name != null ? truncate(name, 25) : "-");
        fileNameLabel.setToolTipText(name);

        if (path != null) {
            filePathLabel.setText(truncate(path, 25));
            filePathLabel.setToolTipText(path);
        } else {
            filePathLabel.setText("-");
        }

        if (sizeBytes != null) {
            fileSizeLabel.setText(formatFileSize(sizeBytes));
        } else {
            fileSizeLabel.setText("-");
        }

        mimeTypeLabel.setText(mimeType != null ? mimeType : "-");
        encodingLabel.setText(encoding != null ? encoding : "UTF-8 (angenommen)");

        if (lastModifiedMs != null && lastModifiedMs > 0) {
            lastModifiedLabel.setText(DATE_FORMAT.format(new Date(lastModifiedMs)));
        } else {
            lastModifiedLabel.setText("-");
        }

        sourceTypeLabel.setText(resolveSourceLabel(sourceType));
    }

    private static String resolveSourceLabel(String sourceType) {
        if (sourceType == null) return "\u2753 Unbekannt";
        switch (sourceType.toUpperCase()) {
            case "LOCAL":     return "\uD83D\uDCBB Lokal";
            case "FTP":       return "\uD83C\uDF10 FTP";
            case "NDV":       return "\uD83D\uDDA5 NDV";
            case "MAIL":      return "\uD83D\uDCE7 E-Mail";
            case "WEB":       return "\uD83C\uDF10 Web";
            case "BETAVIEW":  return "\uD83D\uDCD8 BetaView";
            case "WIKI":      return "\uD83D\uDCD6 Wiki";
            case "CONFLUENCE":return "\uD83D\uDCD3 Confluence";
            default:          return sourceType;
        }
    }

    public void setExtractionInfo(String extractorName, java.util.List<String> warnings) {
        extractorLabel.setText(extractorName != null ? extractorName : "-");

        int warningCount = warnings != null ? warnings.size() : 0;
        warningsLabel.setText(String.valueOf(warningCount));

        if (warningCount > 0) {
            warningsLabel.setForeground(new Color(255, 152, 0));
            warningsPanel.removeAll();
            for (String warning : warnings) {
                JLabel warnLabel = new JLabel("\u26A0 " + truncate(warning, 35));
                warnLabel.setFont(warnLabel.getFont().deriveFont(Font.PLAIN, 10f));
                warnLabel.setToolTipText(warning);
                warningsPanel.add(warnLabel);
            }
            warningsPanel.setVisible(true);
        } else {
            warningsLabel.setForeground(new Color(76, 175, 80));
            warningsPanel.setVisible(false);
        }

        revalidate();
        repaint();
    }

    public void setIndexStatus(IndexStatus lucene, IndexStatus embeddings,
                               int chunkCount, Integer chunkSize, Integer overlap,
                               String embeddingModel, Integer dimension, Long indexedAtMs) {
        updateStatusLabel(luceneStatusLabel, lucene);
        updateStatusLabel(embeddingsStatusLabel, embeddings);

        chunkCountLabel.setText(chunkCount > 0 ? String.valueOf(chunkCount) : "-");

        if (chunkSize != null && overlap != null) {
            chunkParamsLabel.setText(chunkSize + " / " + overlap + " overlap");
        } else {
            chunkParamsLabel.setText("-");
        }

        if (embeddingModel != null) {
            String modelInfo = embeddingModel;
            if (dimension != null) {
                modelInfo += " (" + dimension + "d)";
            }
            embeddingModelLabel.setText(truncate(modelInfo, 25));
            embeddingModelLabel.setToolTipText(modelInfo);
        } else {
            embeddingModelLabel.setText("-");
        }

        if (indexedAtMs != null && indexedAtMs > 0) {
            indexedAtLabel.setText(DATE_FORMAT.format(new Date(indexedAtMs)));
        } else {
            indexedAtLabel.setText("-");
        }
    }

    public void refreshIndexStatus() {
        if (documentId == null) {
            setIndexStatus(IndexStatus.NOT_INDEXED, IndexStatus.NOT_INDEXED, 0, null, null, null, null, null);
            return;
        }

        try {
            RagService ragService = RagService.getInstance();

            if (ragService.isIndexed(documentId)) {
                RagService.IndexedDocument indexedDoc = ragService.getIndexedDocument(documentId);

                if (indexedDoc != null) {
                    setIndexStatus(
                            IndexStatus.INDEXED,
                            ragService.getStats().embeddingsAvailable ? IndexStatus.INDEXED : IndexStatus.NOT_INDEXED,
                            indexedDoc.chunkCount,
                            ragService.getConfig().getChunkSizeChars(),
                            ragService.getConfig().getOverlapChars(),
                            null,
                            null,
                            indexedDoc.indexedAt
                    );
                }
            } else {
                setIndexStatus(IndexStatus.NOT_INDEXED, IndexStatus.NOT_INDEXED, 0, null, null, null, null, null);
            }
        } catch (Exception e) {
            setIndexStatus(IndexStatus.FAILED, IndexStatus.FAILED, 0, null, null, null, null, null);
        }
    }

    private void updateStatusLabel(JLabel label, IndexStatus status) {
        label.setText(status.getIcon() + " " + status.getDisplayName());
        label.setForeground(status.getColor());
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Index status enumeration.
     */
    public enum IndexStatus {
        NOT_INDEXED("Nicht indexiert", "\u2B1C", Color.GRAY),
        INDEXING("Indexierung...", "\u23F3", new Color(255, 152, 0)),
        INDEXED("Indexiert", "\u2705", new Color(76, 175, 80)),
        FAILED("Fehlgeschlagen", "\u274C", new Color(244, 67, 54)),
        STALE("Veraltet", "\uD83D\uDD04", new Color(255, 193, 7));

        private final String displayName;
        private final String icon;
        private final Color color;

        IndexStatus(String displayName, String icon, Color color) {
            this.displayName = displayName;
            this.icon = icon;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public String getIcon() { return icon; }
        public Color getColor() { return color; }
    }
}

