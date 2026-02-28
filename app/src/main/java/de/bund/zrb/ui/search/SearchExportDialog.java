package de.bund.zrb.ui.search;

import de.bund.zrb.search.io.SearchDataExportImportService;
import de.bund.zrb.search.io.SearchDataExportImportService.CancelToken;
import de.bund.zrb.search.io.SearchDataExportImportService.CancelledException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Modal dialog for exporting search index + archive data to a ZIP file.
 * User selects which source types to include; MAIL is deselected by default.
 */
public final class SearchExportDialog extends JDialog {

    private final JCheckBox cbLocal   = new JCheckBox("📁 Lokal", true);
    private final JCheckBox cbFtp     = new JCheckBox("🌐 FTP", true);
    private final JCheckBox cbNdv     = new JCheckBox("🔗 NDV", true);
    private final JCheckBox cbMail    = new JCheckBox("📧 Mail", false); // default OFF
    private final JCheckBox cbArchive = new JCheckBox("📦 Archiv", true);

    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel("Bereit.");
    private final JButton exportButton = new JButton("📤 Exportieren…");
    private final JButton cancelButton = new JButton("Abbrechen");
    private final JButton closeButton = new JButton("Schließen");

    /** Token shared with the background worker to request cancellation. */
    private volatile CancelToken currentCancelToken;
    /** The currently running worker, or null. */
    private SwingWorker<Void, int[]> currentWorker;

    public SearchExportDialog(Window owner) {
        super(owner, "Suchdaten exportieren", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(460, 300));

        // Close via window-X should also cancel a running export
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                onClose();
            }
        });

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(new EmptyBorder(12, 16, 12, 16));

        // Source type checkboxes
        JPanel sourcePanel = new JPanel(new GridLayout(0, 1, 4, 2));
        sourcePanel.setBorder(BorderFactory.createTitledBorder("Quelltypen für den Export"));
        sourcePanel.add(cbLocal);
        sourcePanel.add(cbFtp);
        sourcePanel.add(cbNdv);
        sourcePanel.add(cbMail);
        sourcePanel.add(cbArchive);

        JPanel info = new JPanel(new BorderLayout(4, 4));
        info.add(sourcePanel, BorderLayout.CENTER);
        JLabel hint = new JLabel("<html><small>Der Lucene-Index wird immer vollständig exportiert.<br>"
                + "Archiv-Snapshots und Datenbank nur bei aktiviertem \"Archiv\".</small></html>");
        hint.setBorder(new EmptyBorder(6, 4, 0, 0));
        info.add(hint, BorderLayout.SOUTH);

        content.add(info, BorderLayout.NORTH);

        // Progress
        JPanel progressPanel = new JPanel(new BorderLayout(4, 4));
        progressBar.setStringPainted(true);
        progressBar.setString("");
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.add(statusLabel, BorderLayout.SOUTH);
        content.add(progressPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        exportButton.addActionListener(e -> doExport());
        cancelButton.addActionListener(e -> doCancelExport());
        closeButton.addActionListener(e -> onClose());
        cancelButton.setEnabled(false);
        cancelButton.setVisible(false);
        buttonBar.add(exportButton);
        buttonBar.add(cancelButton);
        buttonBar.add(closeButton);
        content.add(buttonBar, BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    private Set<String> getSelectedSourceTypes() {
        Set<String> types = new LinkedHashSet<String>();
        if (cbLocal.isSelected())   types.add("LOCAL");
        if (cbFtp.isSelected())     types.add("FTP");
        if (cbNdv.isSelected())     types.add("NDV");
        if (cbMail.isSelected())    types.add("MAIL");
        if (cbArchive.isSelected()) types.add("ARCHIVE");
        return types;
    }

    /** Cancel a running export and request the worker thread to stop. */
    private void doCancelExport() {
        CancelToken token = currentCancelToken;
        if (token != null) {
            token.cancel();
            statusLabel.setText("⏳ Abbrechen wird angefordert…");
            cancelButton.setEnabled(false);
        }
    }

    /** Close the dialog – cancel first if an export is running. */
    private void onClose() {
        if (currentCancelToken != null) {
            doCancelExport();
            // Let the worker's done() handle the rest (it will dispose)
        } else {
            dispose();
        }
    }

    private void doExport() {
        Set<String> sourceTypes = getSelectedSourceTypes();
        if (sourceTypes.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Bitte mindestens einen Quelltyp auswählen.",
                    "Export", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export-Datei speichern");
        fc.setSelectedFile(new File("mainframemate-export.zip"));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("ZIP-Archiv (*.zip)", "zip"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File zipFile = fc.getSelectedFile();
        if (!zipFile.getName().toLowerCase().endsWith(".zip")) {
            zipFile = new File(zipFile.getAbsolutePath() + ".zip");
        }

        // Confirm overwrite
        if (zipFile.exists()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Die Datei existiert bereits. Überschreiben?",
                    "Export", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) return;
        }

        // UI state: running
        exportButton.setEnabled(false);
        exportButton.setVisible(false);
        cancelButton.setEnabled(true);
        cancelButton.setVisible(true);
        closeButton.setEnabled(false);
        setCheckboxesEnabled(false);

        final CancelToken cancelToken = new CancelToken();
        currentCancelToken = cancelToken;
        final File finalZip = zipFile;

        currentWorker = new SwingWorker<Void, int[]>() {
            private volatile String lastMessage = "";

            @Override
            protected Void doInBackground() throws Exception {
                SearchDataExportImportService.exportToZip(finalZip, sourceTypes,
                        (pct, msg) -> {
                            lastMessage = msg;
                            publish(new int[]{pct});
                        },
                        cancelToken);
                return null;
            }

            @Override
            protected void process(java.util.List<int[]> chunks) {
                if (cancelToken.isCancelled()) return;  // Don't update UI after cancel
                int[] last = chunks.get(chunks.size() - 1);
                progressBar.setValue(last[0]);
                progressBar.setString(last[0] + "%");
                statusLabel.setText(lastMessage);
            }

            @Override
            protected void done() {
                currentCancelToken = null;
                currentWorker = null;

                // Restore UI state
                exportButton.setEnabled(true);
                exportButton.setVisible(true);
                cancelButton.setEnabled(false);
                cancelButton.setVisible(false);
                closeButton.setEnabled(true);
                setCheckboxesEnabled(true);

                // Don't show messages if the dialog was already disposed
                if (!SearchExportDialog.this.isDisplayable()) return;

                try {
                    get();
                    // Success
                    progressBar.setValue(100);
                    progressBar.setString("100%");
                    statusLabel.setText("✅ Export abgeschlossen: " + finalZip.getName()
                            + " (" + (finalZip.length() / 1024) + " KB)");
                    JOptionPane.showMessageDialog(SearchExportDialog.this,
                            "Export erfolgreich!\n" + finalZip.getAbsolutePath(),
                            "Export", JOptionPane.INFORMATION_MESSAGE);
                } catch (java.util.concurrent.CancellationException ce) {
                    // Worker was cancelled
                    showCancelledState();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof CancelledException) {
                        showCancelledState();
                    } else {
                        String msg = cause.getMessage();
                        statusLabel.setText("❌ Fehler: " + msg);
                        progressBar.setString("Fehler");
                        JOptionPane.showMessageDialog(SearchExportDialog.this,
                                "Export fehlgeschlagen:\n" + msg,
                                "Fehler", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }

            private void showCancelledState() {
                progressBar.setValue(0);
                progressBar.setString("");
                statusLabel.setText("⛔ Export wurde abgebrochen.");
            }
        };

        currentWorker.execute();
    }

    private void setCheckboxesEnabled(boolean enabled) {
        cbLocal.setEnabled(enabled);
        cbFtp.setEnabled(enabled);
        cbNdv.setEnabled(enabled);
        cbMail.setEnabled(enabled);
        cbArchive.setEnabled(enabled);
    }
}
