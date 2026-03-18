package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.helper.SettingsHelper;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Downloads a Phi-3 ONNX model from Hugging Face into {@code ~/.mainframemate/model/}.
 * <p>
 * The download URL stored in the repo points to the large {@code model.onnx.data} file.
 * All companion files (tokenizer, config, etc.) are downloaded from the same HF directory.
 * <p>
 * A progress dialog with a cancellable progress bar is shown during the download.
 */
final class OnnxModelDownloader {

    private static final Logger LOG = Logger.getLogger(OnnxModelDownloader.class.getName());

    /**
     * Base URL for the Phi-3-mini-4k-instruct-onnx DirectML INT4 variant.
     * HuggingFace "resolve" URLs allow direct binary download.
     */
    private static final String HF_BASE =
            "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/directml/directml-int4-awq-block-128/";

    /** Files to download.  The large file comes last so the small ones are already there. */
    private static final List<FileEntry> FILES = Arrays.asList(
            new FileEntry("genai_config.json",       1_679L),
            new FileEntry("special_tokens_map.json",   599L),
            new FileEntry("tokenizer_config.json",   3_441L),
            new FileEntry("tokenizer.json",      1_937_869L),
            new FileEntry("model.onnx",          2_109_332L),
            new FileEntry("model.onnx.data", 2_131_292_928L)   // ~2 GB
    );

    private OnnxModelDownloader() { }

    /** Returns the default target directory ({@code ~/.mainframemate/model/}). */
    static Path getDefaultModelDir() {
        return SettingsHelper.getSettingsFolder().toPath().resolve("model");
    }

    /**
     * Shows a dialog and downloads the model in the background.
     *
     * @param parent  parent component for the dialog
     * @param onDone  callback invoked on the EDT with the model directory path on success
     */
    static void downloadModel(Component parent, java.util.function.Consumer<String> onDone) {
        Path targetDir = getDefaultModelDir();

        // Quick check: is the model already present?
        if (isModelPresent(targetDir)) {
            int choice = JOptionPane.showConfirmDialog(parent,
                    "Das Modell scheint bereits unter\n" + targetDir + "\nvorhanden zu sein.\n\nTrotzdem erneut herunterladen?",
                    "Modell bereits vorhanden", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                onDone.accept(targetDir.toAbsolutePath().toString());
                return;
            }
        }

        // Calculate total size
        long totalBytes = 0;
        for (FileEntry f : FILES) totalBytes += f.expectedSize;

        // Build progress dialog
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "ONNX-Modell herunterladen", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.setLayout(new BorderLayout(8, 8));
        ((JPanel) dialog.getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel statusLabel = new JLabel("Vorbereitung …");
        JProgressBar progressBar = new JProgressBar(0, 1000);
        progressBar.setStringPainted(true);
        progressBar.setString("0 %");
        JLabel detailLabel = new JLabel(" ");
        detailLabel.setFont(detailLabel.getFont().deriveFont(Font.PLAIN, 11f));
        detailLabel.setForeground(Color.GRAY);

        JButton cancelBtn = new JButton("Abbrechen");

        JPanel topPanel = new JPanel(new BorderLayout(0, 4));
        topPanel.add(statusLabel, BorderLayout.NORTH);
        topPanel.add(progressBar, BorderLayout.CENTER);
        topPanel.add(detailLabel, BorderLayout.SOUTH);
        dialog.add(topPanel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(cancelBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.setSize(480, 160);
        dialog.setLocationRelativeTo(parent);

        final boolean[] cancelled = {false};
        cancelBtn.addActionListener(e -> {
            cancelled[0] = true;
            cancelBtn.setEnabled(false);
            statusLabel.setText("Abbruch …");
        });

        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                cancelled[0] = true;
            }
        });

        final long totalBytesF = totalBytes;

        // Background download
        SwingWorker<String, long[]> worker = new SwingWorker<String, long[]>() {
            @Override
            protected String doInBackground() throws Exception {
                Files.createDirectories(targetDir);

                long downloaded = 0;
                for (int i = 0; i < FILES.size(); i++) {
                    if (cancelled[0]) return null;
                    FileEntry entry = FILES.get(i);
                    final int fileIdx = i + 1;
                    final int fileCount = FILES.size();
                    SwingUtilities.invokeLater(() ->
                            statusLabel.setText("Datei " + fileIdx + "/" + fileCount + ": " + entry.name));

                    Path targetFile = targetDir.resolve(entry.name);
                    downloaded = downloadFile(entry, targetFile, downloaded, totalBytesF,
                            progressBar, detailLabel, cancelled);

                    if (cancelled[0]) return null;
                }
                return targetDir.toAbsolutePath().toString();
            }

            @Override
            protected void done() {
                dialog.dispose();
                try {
                    String result = get();
                    if (result != null) {
                        onDone.accept(result);
                        JOptionPane.showMessageDialog(parent,
                                "Modell erfolgreich heruntergeladen nach:\n" + result,
                                "Download abgeschlossen", JOptionPane.INFORMATION_MESSAGE);
                    } else if (cancelled[0]) {
                        JOptionPane.showMessageDialog(parent,
                                "Download wurde abgebrochen.",
                                "Abgebrochen", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    LOG.warning("Download fehlgeschlagen: " + ex.getMessage());
                    JOptionPane.showMessageDialog(parent,
                            "Download fehlgeschlagen:\n" + ex.getMessage(),
                            "Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
        dialog.setVisible(true);  // blocks until dialog is disposed
    }

    private static long downloadFile(FileEntry entry, Path targetFile,
                                     long alreadyDownloaded, long totalBytes,
                                     JProgressBar progressBar, JLabel detailLabel,
                                     boolean[] cancelled) throws IOException {
        String urlStr = HF_BASE + entry.name;
        long existingLen = 0;

        // Resume support: if partial file exists, try range request
        if (Files.exists(targetFile)) {
            existingLen = Files.size(targetFile);
            // If file is already complete, skip
            if (existingLen == entry.expectedSize) {
                updateProgress(progressBar, detailLabel, alreadyDownloaded + existingLen, totalBytes, entry.name);
                return alreadyDownloaded + existingLen;
            }
        }

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "MainframeMate/5.4");
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(60_000);

        // HuggingFace LFS redirects — follow them
        conn.setInstanceFollowRedirects(true);

        // Resume partial download
        if (existingLen > 0) {
            conn.setRequestProperty("Range", "bytes=" + existingLen + "-");
        }

        int responseCode = conn.getResponseCode();
        boolean append;
        if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
            append = true;
        } else if (responseCode == HttpURLConnection.HTTP_OK) {
            existingLen = 0;  // Server doesn't support range, restart
            append = false;
        } else if (responseCode >= 300 && responseCode < 400) {
            // Manual redirect follow (some Java versions don't follow cross-protocol)
            String location = conn.getHeaderField("Location");
            if (location != null) {
                conn.disconnect();
                conn = (HttpURLConnection) new URL(location).openConnection();
                conn.setRequestProperty("User-Agent", "MainframeMate/5.4");
                conn.setConnectTimeout(30_000);
                conn.setReadTimeout(60_000);
                responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP " + responseCode + " für " + location);
                }
                existingLen = 0;
            }
            append = false;
        } else {
            throw new IOException("HTTP " + responseCode + " für " + urlStr);
        }

        byte[] buffer = new byte[256 * 1024];  // 256 KB buffer
        long globalDownloaded = alreadyDownloaded + existingLen;

        try (InputStream in = new BufferedInputStream(conn.getInputStream(), 256 * 1024);
             OutputStream out = new FileOutputStream(targetFile.toFile(), append)) {

            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                if (cancelled[0]) {
                    conn.disconnect();
                    return globalDownloaded;
                }
                out.write(buffer, 0, bytesRead);
                globalDownloaded += bytesRead;

                updateProgress(progressBar, detailLabel, globalDownloaded, totalBytes, entry.name);
            }
        } finally {
            conn.disconnect();
        }

        return globalDownloaded;
    }

    private static void updateProgress(JProgressBar bar, JLabel detail,
                                       long downloaded, long total, String fileName) {
        final int permille = total > 0 ? (int) (downloaded * 1000 / total) : 0;
        final String pct = String.format("%d %%", permille / 10);
        final String info = formatSize(downloaded) + " / " + formatSize(total) + "  —  " + fileName;
        SwingUtilities.invokeLater(() -> {
            bar.setValue(permille);
            bar.setString(pct);
            detail.setText(info);
        });
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /** Checks whether the model directory has the essential files. */
    static boolean isModelPresent(Path dir) {
        if (!Files.isDirectory(dir)) return false;
        return Files.exists(dir.resolve("model.onnx"))
                && Files.exists(dir.resolve("model.onnx.data"))
                && Files.exists(dir.resolve("tokenizer.json"));
    }

    /** Descriptor for a file to download. */
    private static final class FileEntry {
        final String name;
        final long expectedSize;

        FileEntry(String name, long expectedSize) {
            this.name = name;
            this.expectedSize = expectedSize;
        }
    }
}

