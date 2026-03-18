package de.bund.zrb.net;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generic model downloader with progress dialog, resume support, and proxy awareness.
 * <p>
 * All information (base URL, files, target directory name) comes from a {@link ModelManifest}.
 * Before the download starts an input dialog is shown where the user can review/edit
 * the base URL and see the target installation directory.
 * <p>
 * Proxy settings are resolved via {@link ProxyResolver} using the central Proxy tab configuration.
 */
public final class ModelDownloader {

    private static final Logger LOG = Logger.getLogger(ModelDownloader.class.getName());

    private ModelDownloader() { }

    // ─── public API ──────────────────────────────────────────

    /**
     * Returns the default model root directory ({@code ~/.mainframemate/model/}).
     */
    public static Path getModelRoot() {
        return SettingsHelper.getSettingsFolder().toPath().resolve("model");
    }

    /**
     * Returns the target directory for a specific manifest
     * ({@code ~/.mainframemate/model/<modelName>/}).
     */
    public static Path getModelDir(ModelManifest manifest) {
        return getModelRoot().resolve(manifest.getModelName());
    }

    /**
     * Opens a URL-confirmation dialog, then downloads all files listed in the manifest.
     * Files are stored under {@code ~/.mainframemate/model/<modelName>/}.
     *
     * @param parent   parent component for dialogs
     * @param manifest model manifest describing what to download
     * @param onDone   callback invoked on the EDT with the model directory path on success
     */
    public static void download(Component parent,
                                ModelManifest manifest,
                                java.util.function.Consumer<String> onDone) {

        String dialogTitle = manifest.getDisplayName() != null
                ? manifest.getDisplayName() + " herunterladen"
                : "Modell herunterladen";

        Path targetDir = getModelDir(manifest);
        List<ModelManifest.FileEntry> files = manifest.getFiles();

        // ── 1. URL-Eingabedialog ──────────────────────────────
        String confirmedUrl = showUrlInputDialog(parent, dialogTitle, manifest.getBaseUrl(), targetDir);
        if (confirmedUrl == null) return; // cancelled

        // Normalize: ensure trailing slash
        if (!confirmedUrl.endsWith("/")) confirmedUrl += "/";
        final String baseUrl = confirmedUrl;

        // ── 2. Already-present check ──────────────────────────
        if (areFilesPresent(targetDir, files)) {
            int choice = JOptionPane.showConfirmDialog(parent,
                    "Die Dateien scheinen bereits unter\n" + targetDir + "\nvorhanden zu sein.\n\nTrotzdem erneut herunterladen?",
                    "Dateien bereits vorhanden", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                onDone.accept(targetDir.toAbsolutePath().toString());
                return;
            }
        }

        // ── 3. Resolve proxy once (for the base URL) ─────────
        Settings settings = SettingsHelper.load();
        ProxyResolver.ProxyResolution proxyRes = ProxyResolver.resolveForUrl(baseUrl, settings);
        final Proxy proxy = proxyRes.getProxy();
        LOG.info("Proxy für Download: " + (proxyRes.isDirect() ? "DIRECT" : proxy.address())
                + " (" + proxyRes.getReason() + ")");

        // ── 4. Calculate total size ───────────────────────────
        long totalBytes = 0;
        for (ModelManifest.FileEntry f : files) {
            if (f.getSize() > 0) totalBytes += f.getSize();
        }

        // ── 5. Build progress dialog ─────────────────────────
        JDialog dialog = new JDialog(
                SwingUtilities.getWindowAncestor(parent),
                dialogTitle,
                Dialog.ModalityType.APPLICATION_MODAL);
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

        dialog.setSize(520, 170);
        dialog.setLocationRelativeTo(parent);

        final boolean[] cancelled = {false};
        cancelBtn.addActionListener(e -> {
            cancelled[0] = true;
            cancelBtn.setEnabled(false);
            statusLabel.setText("Abbruch …");
        });
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { cancelled[0] = true; }
        });

        final long totalBytesF = totalBytes;

        // ── 6. Background worker ─────────────────────────────
        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                Files.createDirectories(targetDir);

                long downloaded = 0;
                for (int i = 0; i < files.size(); i++) {
                    if (cancelled[0]) return null;
                    ModelManifest.FileEntry entry = files.get(i);
                    final int fileIdx = i + 1;
                    final int fileCount = files.size();
                    SwingUtilities.invokeLater(() ->
                            statusLabel.setText("Datei " + fileIdx + "/" + fileCount + ": " + entry.getName()));

                    Path targetFile = targetDir.resolve(entry.getName());
                    downloaded = downloadFile(baseUrl + entry.getName(), entry.getSize(),
                            targetFile, downloaded, totalBytesF,
                            proxy, progressBar, detailLabel, cancelled);

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
                                "Download abgeschlossen.\nInstalliert nach:\n" + result,
                                "Download abgeschlossen", JOptionPane.INFORMATION_MESSAGE);
                    } else if (cancelled[0]) {
                        JOptionPane.showMessageDialog(parent,
                                "Download wurde abgebrochen.",
                                "Abgebrochen", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "Download fehlgeschlagen", ex);
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(parent,
                            "Download fehlgeschlagen:\n" + cause.getMessage(),
                            "Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
        dialog.setVisible(true);  // blocks until disposed
    }

    /**
     * Checks whether all files listed in the manifest are present (with correct sizes).
     */
    public static boolean areFilesPresent(Path dir, List<ModelManifest.FileEntry> files) {
        if (!Files.isDirectory(dir)) return false;
        for (ModelManifest.FileEntry f : files) {
            Path p = dir.resolve(f.getName());
            if (!Files.exists(p)) return false;
            if (f.getSize() > 0) {
                try {
                    if (Files.size(p) != f.getSize()) return false;
                } catch (IOException e) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Checks whether all files of the given manifest are present in their target directory.
     */
    public static boolean isModelPresent(ModelManifest manifest) {
        return areFilesPresent(getModelDir(manifest), manifest.getFiles());
    }

    /**
     * Formats a byte count for human display.
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    // ─── private helpers ─────────────────────────────────────

    /**
     * Shows a dialog where the user can review/edit the download URL.
     * Returns the confirmed URL, or {@code null} if cancelled.
     */
    private static String showUrlInputDialog(Component parent, String title,
                                             String defaultUrl, Path targetDir) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));

        JLabel infoLabel = new JLabel("<html>"
                + "Download-URL eingeben oder bestätigen.<br>"
                + "<br>"
                + "<b>Installationsverzeichnis:</b><br>"
                + "<code>" + escapeHtml(targetDir.toAbsolutePath().toString()) + "</code>"
                + "</html>");
        infoLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        panel.add(infoLabel, BorderLayout.NORTH);

        JTextField urlField = new JTextField(defaultUrl, 55);
        urlField.setFont(urlField.getFont().deriveFont(Font.PLAIN, 12f));
        urlField.selectAll();
        panel.add(urlField, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(
                parent, panel, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return null;

        String url = urlField.getText().trim();
        if (url.isEmpty()) return null;
        return url;
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Downloads a single file with resume support and proxy.
     *
     * @return cumulative bytes downloaded (including prior files)
     */
    private static long downloadFile(String urlStr, long expectedSize, Path targetFile,
                                     long alreadyDownloaded, long totalBytes,
                                     Proxy proxy,
                                     JProgressBar progressBar, JLabel detailLabel,
                                     boolean[] cancelled) throws IOException {
        long existingLen = 0;

        // Resume support: if partial file exists, try range request
        if (Files.exists(targetFile)) {
            existingLen = Files.size(targetFile);
            if (expectedSize > 0 && existingLen == expectedSize) {
                updateProgress(progressBar, detailLabel, alreadyDownloaded + existingLen, totalBytes, targetFile.getFileName().toString());
                return alreadyDownloaded + existingLen;
            }
        }

        HttpURLConnection conn = openConnection(urlStr, proxy, existingLen);

        int responseCode = conn.getResponseCode();
        boolean append;
        if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
            append = true;
        } else if (responseCode == HttpURLConnection.HTTP_OK) {
            existingLen = 0;
            append = false;
        } else if (responseCode >= 300 && responseCode < 400) {
            // Manual redirect (some Java versions don't follow cross-protocol redirects)
            String location = conn.getHeaderField("Location");
            if (location != null) {
                conn.disconnect();
                conn = openConnection(location, proxy, 0);
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

        byte[] buffer = new byte[256 * 1024];
        long globalDownloaded = alreadyDownloaded + existingLen;
        String fileName = targetFile.getFileName().toString();

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
                updateProgress(progressBar, detailLabel, globalDownloaded, totalBytes, fileName);
            }
        } finally {
            conn.disconnect();
        }

        return globalDownloaded;
    }

    private static HttpURLConnection openConnection(String urlStr, Proxy proxy,
                                                    long rangeStart) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) (proxy != null && proxy != Proxy.NO_PROXY
                ? url.openConnection(proxy)
                : url.openConnection());
        conn.setRequestProperty("User-Agent", "MainframeMate/5.4");
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(60_000);
        conn.setInstanceFollowRedirects(true);
        if (rangeStart > 0) {
            conn.setRequestProperty("Range", "bytes=" + rangeStart + "-");
        }
        return conn;
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
}

