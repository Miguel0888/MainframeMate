package de.bund.zrb.ui.search;

import de.bund.zrb.search.io.SearchDataExportImportService;

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
    private final JButton closeButton = new JButton("Schließen");

    public SearchExportDialog(Window owner) {
        super(owner, "Suchdaten exportieren", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(460, 300));

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
        closeButton.addActionListener(e -> dispose());
        buttonBar.add(exportButton);
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

        exportButton.setEnabled(false);
        closeButton.setEnabled(false);

        final File finalZip = zipFile;
        new SwingWorker<Void, int[]>() {
            @Override
            protected Void doInBackground() throws Exception {
                SearchDataExportImportService.exportToZip(finalZip, sourceTypes,
                        (pct, msg) -> publish(new int[]{pct}, msg));
                return null;
            }

            // Workaround: SwingWorker.publish only takes one vararg type
            // We use process() to update the UI
            private String lastMessage = "";
            private void publish(int[] pct, String msg) {
                lastMessage = msg;
                publish(pct);
            }

            @Override
            protected void process(java.util.List<int[]> chunks) {
                int[] last = chunks.get(chunks.size() - 1);
                progressBar.setValue(last[0]);
                progressBar.setString(last[0] + "%");
                statusLabel.setText(lastMessage);
            }

            @Override
            protected void done() {
                exportButton.setEnabled(true);
                closeButton.setEnabled(true);
                try {
                    get();
                    progressBar.setValue(100);
                    progressBar.setString("100%");
                    statusLabel.setText("✅ Export abgeschlossen: " + finalZip.getName()
                            + " (" + (finalZip.length() / 1024) + " KB)");
                    JOptionPane.showMessageDialog(SearchExportDialog.this,
                            "Export erfolgreich!\n" + finalZip.getAbsolutePath(),
                            "Export", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    statusLabel.setText("❌ Fehler: " + msg);
                    JOptionPane.showMessageDialog(SearchExportDialog.this,
                            "Export fehlgeschlagen:\n" + msg,
                            "Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}

