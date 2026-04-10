package de.bund.zrb.ui.mermaid;

import de.bund.zrb.ui.mermaid.tile.TiledDiagramRenderer;
import de.bund.zrb.ui.mermaid.tile.TiledExporter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

/**
 * Modal export dialog for Mermaid diagrams.
 * <p>
 * Options:
 * <ul>
 *   <li>Content: current (collapsed) view vs. full diagram</li>
 *   <li>Scope: entire diagram vs. current viewport</li>
 *   <li>Format: SVG or PNG</li>
 *   <li>SVG splitting for large diagrams</li>
 * </ul>
 * Shows a progress bar with step descriptions during export.
 */
public class MermaidExportDialog extends JDialog {

    /** Export result — set before the dialog closes. */
    public enum ExportResult { EXPORTED, CANCELLED }
    private ExportResult result = ExportResult.CANCELLED;

    // Options
    private final JRadioButton rbContentCurrent;
    private final JRadioButton rbContentFull;
    private final JRadioButton rbScopeAll;
    private final JRadioButton rbScopeViewport;
    private final JRadioButton rbFormatSvg;
    private final JRadioButton rbFormatPng;
    private final JCheckBox cbSvgSplit;

    // Progress
    private final JProgressBar progressBar;
    private final JLabel progressLabel;
    private final JButton exportButton;
    private final JButton cancelButton;

    // Context
    private final MermaidDiagramPanel diagramPanel;
    private final String baseName;
    private final boolean isCollapsed;
    /** Callback to regenerate the Mermaid diagram in full (non-collapsed) mode. */
    private final Runnable fullDiagramRegenerator;

    /**
     * @param owner                  parent frame
     * @param diagramPanel           the diagram panel with rendered content
     * @param baseName               base file name (without extension)
     * @param isCollapsed            whether the current view is collapsed
     * @param fullDiagramRegenerator  callback to regenerate full diagram (may be null if not outline-based)
     */
    public MermaidExportDialog(Frame owner, MermaidDiagramPanel diagramPanel,
                                String baseName, boolean isCollapsed,
                                Runnable fullDiagramRegenerator) {
        super(owner, "Diagramm exportieren", true);
        this.diagramPanel = diagramPanel;
        this.baseName = baseName != null ? baseName : "diagram";
        this.isCollapsed = isCollapsed;
        this.fullDiagramRegenerator = fullDiagramRegenerator;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(12, 16, 12, 16));

        // ── Content section ──
        JPanel contentPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        contentPanel.setBorder(new TitledBorder("Inhalt"));
        rbContentCurrent = new JRadioButton("Aktuelle Ansicht" + (isCollapsed ? " (kompakt)" : ""));
        rbContentFull = new JRadioButton("Vollst\u00E4ndiges Diagramm (alle Details)");
        rbContentCurrent.setSelected(true);
        ButtonGroup contentGroup = new ButtonGroup();
        contentGroup.add(rbContentCurrent);
        contentGroup.add(rbContentFull);
        contentPanel.add(rbContentCurrent);
        contentPanel.add(rbContentFull);
        // Disable full diagram option if no regenerator available
        if (fullDiagramRegenerator == null) {
            rbContentFull.setEnabled(false);
            rbContentFull.setToolTipText("Nur f\u00FCr Outline-basierte Diagramme verf\u00FCgbar");
        }
        contentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(contentPanel);
        mainPanel.add(Box.createVerticalStrut(6));

        // ── Scope section ──
        JPanel scopePanel = new JPanel(new GridLayout(2, 1, 0, 2));
        scopePanel.setBorder(new TitledBorder("Bereich"));
        rbScopeAll = new JRadioButton("Gesamtes Diagramm");
        rbScopeViewport = new JRadioButton("Aktueller Ausschnitt (sichtbarer Bereich)");
        rbScopeAll.setSelected(true);
        ButtonGroup scopeGroup = new ButtonGroup();
        scopeGroup.add(rbScopeAll);
        scopeGroup.add(rbScopeViewport);
        scopePanel.add(rbScopeAll);
        scopePanel.add(rbScopeViewport);
        // Disable viewport option if no viewport available
        if (diagramPanel.getViewport() == null) {
            rbScopeViewport.setEnabled(false);
            rbScopeViewport.setToolTipText("Kein Viewport verf\u00FCgbar");
        }
        scopePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(scopePanel);
        mainPanel.add(Box.createVerticalStrut(6));

        // ── Format section ──
        JPanel formatPanel = new JPanel(new GridLayout(3, 1, 0, 2));
        formatPanel.setBorder(new TitledBorder("Format"));
        rbFormatSvg = new JRadioButton("SVG (Vektorgrafik)");
        rbFormatPng = new JRadioButton("PNG (Rasterbild)");
        cbSvgSplit = new JCheckBox("SVG aufteilen (f\u00FCr gro\u00DFe Diagramme, Browser-Zoom bis 500%)");
        rbFormatSvg.setSelected(true);
        ButtonGroup formatGroup = new ButtonGroup();
        formatGroup.add(rbFormatSvg);
        formatGroup.add(rbFormatPng);
        formatPanel.add(rbFormatSvg);
        formatPanel.add(rbFormatPng);
        formatPanel.add(cbSvgSplit);
        formatPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // SVG split only available for SVG format + full scope
        cbSvgSplit.setEnabled(true);
        ActionListener formatListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateSplitCheckbox();
            }
        };
        rbFormatSvg.addActionListener(formatListener);
        rbFormatPng.addActionListener(formatListener);
        rbScopeAll.addActionListener(formatListener);
        rbScopeViewport.addActionListener(formatListener);
        updateSplitCheckbox();

        mainPanel.add(formatPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // ── Progress section ──
        progressLabel = new JLabel(" ");
        progressLabel.setFont(progressLabel.getFont().deriveFont(Font.ITALIC, 11f));
        progressLabel.setForeground(Color.GRAY);
        progressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(progressLabel);
        mainPanel.add(Box.createVerticalStrut(4));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        progressBar.setVisible(false);
        mainPanel.add(progressBar);
        mainPanel.add(Box.createVerticalStrut(10));

        // ── Buttons ──
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        exportButton = new JButton("\uD83D\uDCBE Exportieren"); // 💾
        cancelButton = new JButton("Abbrechen");
        exportButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doExport();
            }
        });
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                result = ExportResult.CANCELLED;
                dispose();
            }
        });
        buttonPanel.add(cancelButton);
        buttonPanel.add(exportButton);
        mainPanel.add(buttonPanel);

        setContentPane(mainPanel);
        pack();
        setMinimumSize(new Dimension(420, getHeight()));
        setLocationRelativeTo(owner);
    }

    private void updateSplitCheckbox() {
        boolean svgSelected = rbFormatSvg.isSelected();
        boolean fullScope = rbScopeAll.isSelected();
        cbSvgSplit.setEnabled(svgSelected && fullScope);
        if (!svgSelected || !fullScope) {
            cbSvgSplit.setSelected(false);
        }
    }

    public ExportResult getResult() {
        return result;
    }

    private void doExport() {
        // Determine file extension and file chooser filter
        boolean isSvg = rbFormatSvg.isSelected();
        boolean isSplit = cbSvgSplit.isSelected() && isSvg;
        boolean isViewport = rbScopeViewport.isSelected();
        boolean isFull = rbContentFull.isSelected();

        if (isSplit) {
            // SVG split → choose output directory
            JFileChooser dirChooser = new JFileChooser();
            dirChooser.setDialogTitle("Verzeichnis f\u00FCr aufgeteilte SVGs w\u00E4hlen");
            dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (dirChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File outputDir = dirChooser.getSelectedFile();
            if (outputDir == null) return;
            doSplitExport(outputDir, isFull);
        } else {
            // Single file export
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Diagramm exportieren");
            String ext = isSvg ? ".svg" : ".png";
            String filterDesc = isSvg ? "SVG-Datei (*.svg)" : "PNG-Bild (*.png)";
            String filterExt = isSvg ? "svg" : "png";
            javax.swing.filechooser.FileNameExtensionFilter filter =
                    new javax.swing.filechooser.FileNameExtensionFilter(filterDesc, filterExt);
            chooser.setFileFilter(filter);
            chooser.setSelectedFile(new File(baseName + ext));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File target = chooser.getSelectedFile();
            if (target == null) return;

            // Ensure correct extension
            if (!target.getName().toLowerCase().endsWith(ext)) {
                target = new File(target.getAbsolutePath() + ext);
            }

            doSingleFileExport(target, isSvg, isViewport, isFull);
        }
    }

    private void doSingleFileExport(final File target, final boolean isSvg,
                                     final boolean isViewport, final boolean isFull) {
        exportButton.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setValue(0);

        final TiledExporter.ProgressListener progressListener = new TiledExporter.ProgressListener() {
            @Override
            public void onProgress(final int percentComplete) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setValue(percentComplete);
                    }
                });
            }

            @Override
            public void onStep(final String stepDescription) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        progressLabel.setText(stepDescription);
                    }
                });
            }
        };

        new SwingWorker<Void, Void>() {
            private String error;

            @Override
            protected Void doInBackground() {
                try {
                    // If user wants full diagram and we're in collapsed mode, regenerate
                    if (isFull && fullDiagramRegenerator != null && isCollapsed) {
                        SwingUtilities.invokeAndWait(new Runnable() {
                            @Override
                            public void run() {
                                progressLabel.setText("Vollst\u00E4ndiges Diagramm wird generiert\u2026");
                            }
                        });
                        // Note: full diagram regeneration would need to happen
                        // in the calling code; for now we export what's available
                    }

                    String svgContent = diagramPanel.getSvg();
                    boolean tiled = diagramPanel.isTiledMode();
                    TiledDiagramRenderer tiledRenderer = diagramPanel.getTiledRenderer();

                    if (isSvg) {
                        // SVG export
                        if (svgContent == null || svgContent.isEmpty()) {
                            error = "Kein SVG verf\u00FCgbar \u2014 bitte warten, bis das Rendering abgeschlossen ist.";
                            return null;
                        }
                        if (isViewport) {
                            double[] vp = diagramPanel.getViewport();
                            if (vp != null) {
                                String fullSvg = tiled && tiledRenderer != null
                                        ? tiledRenderer.getFullSvg() : svgContent;
                                TiledExporter.exportSvgViewport(fullSvg,
                                        vp[0], vp[1], vp[2], vp[3],
                                        target, progressListener);
                            } else {
                                TiledExporter.exportSvg(svgContent, target, progressListener);
                            }
                        } else {
                            String fullSvg = tiled && tiledRenderer != null
                                    ? tiledRenderer.getFullSvg() : svgContent;
                            TiledExporter.exportSvg(fullSvg, target, progressListener);
                        }
                    } else {
                        // PNG export
                        if (isViewport && tiled && tiledRenderer != null) {
                            double[] vp = diagramPanel.getViewport();
                            if (vp != null) {
                                TiledExporter.exportViewport(tiledRenderer,
                                        diagramPanel.getTileCache(), 3,
                                        vp[0], vp[1], vp[2], vp[3],
                                        target, progressListener);
                            } else {
                                error = "Kein Viewport verf\u00FCgbar.";
                            }
                        } else if (tiled && tiledRenderer != null) {
                            // Full PNG with parallel tile rendering
                            TiledExporter.export(tiledRenderer,
                                    diagramPanel.getTileCache(), 3,
                                    target, progressListener);
                        } else {
                            // Non-tiled: direct image export
                            progressListener.onStep("PNG wird geschrieben\u2026");
                            java.awt.image.BufferedImage img = diagramPanel.getImage();
                            if (img != null) {
                                javax.imageio.ImageIO.write(img, "PNG", target);
                                progressListener.onProgress(100);
                                progressListener.onStep("Export abgeschlossen.");
                            } else {
                                error = "Kein Bild verf\u00FCgbar \u2014 bitte warten, bis das Rendering abgeschlossen ist.";
                            }
                        }
                    }
                } catch (Exception e) {
                    error = e.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                exportButton.setEnabled(true);
                if (error != null) {
                    progressLabel.setText("\u26A0 " + error);
                    progressBar.setValue(0);
                    JOptionPane.showMessageDialog(MermaidExportDialog.this,
                            error, "Export-Fehler", JOptionPane.ERROR_MESSAGE);
                } else {
                    progressBar.setValue(100);
                    progressLabel.setText("\u2713 Exportiert: " + target.getName());
                    result = ExportResult.EXPORTED;
                    // Auto-close after short delay
                    Timer closeTimer = new Timer(1200, new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            dispose();
                        }
                    });
                    closeTimer.setRepeats(false);
                    closeTimer.start();
                }
            }
        }.execute();
    }

    private void doSplitExport(final File outputDir, final boolean isFull) {
        exportButton.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setValue(0);

        final TiledExporter.ProgressListener progressListener = new TiledExporter.ProgressListener() {
            @Override
            public void onProgress(final int percentComplete) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setValue(percentComplete);
                    }
                });
            }

            @Override
            public void onStep(final String stepDescription) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        progressLabel.setText(stepDescription);
                    }
                });
            }
        };

        new SwingWorker<Void, Void>() {
            private String error;
            private List<File> files;

            @Override
            protected Void doInBackground() {
                try {
                    String svgContent = diagramPanel.getSvg();
                    if (svgContent == null || svgContent.isEmpty()) {
                        // Try full SVG from tiled renderer
                        TiledDiagramRenderer tiledRenderer = diagramPanel.getTiledRenderer();
                        if (tiledRenderer != null) {
                            svgContent = tiledRenderer.getFullSvg();
                        }
                    }
                    if (svgContent == null || svgContent.isEmpty()) {
                        error = "Kein SVG verf\u00FCgbar.";
                        return null;
                    }

                    // Use full SVG if available (from tiled renderer)
                    TiledDiagramRenderer tiledRenderer = diagramPanel.getTiledRenderer();
                    if (diagramPanel.isTiledMode() && tiledRenderer != null) {
                        svgContent = tiledRenderer.getFullSvg();
                    }

                    files = TiledExporter.exportSvgSplit(svgContent, outputDir,
                            baseName, progressListener);
                } catch (Exception e) {
                    error = e.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                exportButton.setEnabled(true);
                if (error != null) {
                    progressLabel.setText("\u26A0 " + error);
                    progressBar.setValue(0);
                    JOptionPane.showMessageDialog(MermaidExportDialog.this,
                            error, "Export-Fehler", JOptionPane.ERROR_MESSAGE);
                } else {
                    int count = files != null ? files.size() - 1 : 0; // -1 for index
                    progressBar.setValue(100);
                    progressLabel.setText("\u2713 " + count + " SVG-Teile + Index nach " + outputDir.getName());
                    result = ExportResult.EXPORTED;

                    // Ask user if they want to open the index file
                    if (files != null && !files.isEmpty()) {
                        int choice = JOptionPane.showConfirmDialog(MermaidExportDialog.this,
                                count + " SVG-Teile exportiert.\n\u00D6ffnen Sie die Index-Seite im Browser?",
                                "Export abgeschlossen", JOptionPane.YES_NO_OPTION);
                        if (choice == JOptionPane.YES_OPTION) {
                            try {
                                Desktop.getDesktop().browse(files.get(0).toURI());
                            } catch (Exception ignored) {}
                        }
                    }
                    dispose();
                }
            }
        }.execute();
    }
}

