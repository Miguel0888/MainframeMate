package de.bund.zrb.ui.preview;

import de.bund.zrb.rag.service.RagService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Sidebar panel showing file details and index status.
 * Can be toggled on/off in the preview/editor tab.
 */
public class IndexStatusSidebar extends JPanel {

    private static final int SIDEBAR_WIDTH = 280;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

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

    public IndexStatusSidebar() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(12, 12, 12, 12));
        setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));
        setBackground(new Color(248, 249, 250));

        // === File Details Section ===
        add(createSectionHeader("📁 Datei-Details"));

        fileNameLabel = createValueLabel("-");
        add(createRow("Name:", fileNameLabel));

        filePathLabel = createValueLabel("-");
        filePathLabel.setToolTipText("");
        add(createRow("Pfad:", filePathLabel));

        fileSizeLabel = createValueLabel("-");
        add(createRow("Größe:", fileSizeLabel));

        mimeTypeLabel = createValueLabel("-");
        add(createRow("MIME-Type:", mimeTypeLabel));

        encodingLabel = createValueLabel("-");
        add(createRow("Encoding:", encodingLabel));

        lastModifiedLabel = createValueLabel("-");
        add(createRow("Geändert:", lastModifiedLabel));

        sourceTypeLabel = createValueLabel("-");
        add(createRow("Quelle:", sourceTypeLabel));

        add(Box.createVerticalStrut(16));

        // === Extraction Section ===
        add(createSectionHeader("🔧 Extraktion"));

        extractorLabel = createValueLabel("-");
        add(createRow("Extractor:", extractorLabel));

        warningsLabel = createValueLabel("0");
        add(createRow("Warnungen:", warningsLabel));

        warningsPanel = new JPanel();
        warningsPanel.setLayout(new BoxLayout(warningsPanel, BoxLayout.Y_AXIS));
        warningsPanel.setBackground(new Color(255, 243, 205));
        warningsPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 193, 7)),
                new EmptyBorder(8, 8, 8, 8)
        ));
        warningsPanel.setVisible(false);
        warningsPanel.setAlignmentX(LEFT_ALIGNMENT);
        add(warningsPanel);

        add(Box.createVerticalStrut(16));

        // === Index Status Section ===
        add(createSectionHeader("📊 Index-Status"));

        luceneStatusLabel = createStatusLabel("Nicht indexiert", IndexStatus.NOT_INDEXED);
        add(createRow("Lucene:", luceneStatusLabel));

        embeddingsStatusLabel = createStatusLabel("Nicht indexiert", IndexStatus.NOT_INDEXED);
        add(createRow("Embeddings:", embeddingsStatusLabel));

        chunkCountLabel = createValueLabel("-");
        add(createRow("Chunks:", chunkCountLabel));

        chunkParamsLabel = createValueLabel("-");
        add(createRow("Chunk-Params:", chunkParamsLabel));

        embeddingModelLabel = createValueLabel("-");
        add(createRow("Embedding-Modell:", embeddingModelLabel));

        indexedAtLabel = createValueLabel("-");
        add(createRow("Indexiert am:", indexedAtLabel));

        add(Box.createVerticalGlue());
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

    /**
     * Set the document ID for index status lookup.
     */
    public void setDocumentId(String documentId) {
        this.documentId = documentId;
        refreshIndexStatus();
    }

    /**
     * Update file details.
     */
    public void setFileDetails(String name, String path, Long sizeBytes, String mimeType,
                               String encoding, Long lastModifiedMs, boolean isRemote) {
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

        sourceTypeLabel.setText(isRemote ? "🌐 Remote (FTP)" : "💻 Lokal");
    }

    /**
     * Update extraction info.
     */
    public void setExtractionInfo(String extractorName, List<String> warnings) {
        extractorLabel.setText(extractorName != null ? extractorName : "-");

        int warningCount = warnings != null ? warnings.size() : 0;
        warningsLabel.setText(String.valueOf(warningCount));

        if (warningCount > 0) {
            warningsLabel.setForeground(new Color(255, 152, 0));
            warningsPanel.removeAll();
            for (String warning : warnings) {
                JLabel warnLabel = new JLabel("⚠ " + truncate(warning, 35));
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

    /**
     * Update index status.
     */
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

    /**
     * Refresh index status from RagService.
     */
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
                    // For now, assume if indexed in RagService, both Lucene and embeddings are done
                    // In a more complete implementation, we'd track these separately
                    setIndexStatus(
                            IndexStatus.INDEXED,
                            ragService.getStats().embeddingsAvailable ? IndexStatus.INDEXED : IndexStatus.NOT_INDEXED,
                            indexedDoc.chunkCount,
                            ragService.getConfig().getChunkSizeChars(),
                            ragService.getConfig().getOverlapChars(),
                            null, // Would need to get from embedding settings
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
        NOT_INDEXED("Nicht indexiert", "⬜", Color.GRAY),
        INDEXING("Indexierung...", "⏳", new Color(255, 152, 0)),
        INDEXED("Indexiert", "✅", new Color(76, 175, 80)),
        FAILED("Fehlgeschlagen", "❌", new Color(244, 67, 54)),
        STALE("Veraltet", "🔄", new Color(255, 193, 7));

        private final String displayName;
        private final String icon;
        private final Color color;

        IndexStatus(String displayName, String icon, Color color) {
            this.displayName = displayName;
            this.icon = icon;
            this.color = color;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getIcon() {
            return icon;
        }

        public Color getColor() {
            return color;
        }
    }
}

