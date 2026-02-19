package de.bund.zrb.ui;

import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.impl.factory.FileServiceFactory;
import de.bund.zrb.files.model.FilePayload;
import de.bund.zrb.files.path.VirtualResourceRef;
import de.bund.zrb.files.retry.FtpRetryPolicy;
import de.bund.zrb.files.retry.RetryExecutor;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Async opener for raw FTP files used by MVS tab.
 * Keeps a reusable FileService session and performs all IO outside EDT.
 * Uses configurable retry policy from Settings.
 */
public class AsyncRawFileOpener {

    private final TabbedPaneManager tabbedPaneManager;
    private final Component parentComponent;
    private final String host;
    private final String user;
    private final String password;
    private final String encoding;
    private final ExecutorService executor;

    private final Object lock = new Object();
    private FileService openFileService;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private RetryExecutor currentRetryExecutor;

    public AsyncRawFileOpener(TabbedPaneManager tabbedPaneManager,
                              Component parentComponent,
                              String host,
                              String user,
                              String password,
                              String encoding) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.parentComponent = parentComponent;
        this.host = host;
        this.user = user;
        this.password = password;
        this.encoding = encoding;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MvsRawFileOpenWorker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Bricht laufende Operationen ab.
     */
    public void cancel() {
        cancelled.set(true);
        if (currentRetryExecutor != null) {
            currentRetryExecutor.cancel();
        }
    }

    public void openAsync(String path, Runnable onStartUi, Runnable onDoneUi) {
        cancelled.set(false);
        final Window window = SwingUtilities.getWindowAncestor(parentComponent);
        final Cursor originalCursor = window != null ? window.getCursor() : null;

        SwingUtilities.invokeLater(() -> {
            if (window != null) {
                window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            }
            if (onStartUi != null) {
                onStartUi.run();
            }
        });

        executor.submit(() -> {
            long started = System.currentTimeMillis();
            try {
                System.out.println("[AsyncRawFileOpener] start open path=" + path);

                FilePayload payload = readWithRetry(path);
                // IMPORTANT: Use getEditorText() for proper RECORD_STRUCTURE handling
                String content = payload.getEditorText();

                long duration = System.currentTimeMillis() - started;
                System.out.println("[AsyncRawFileOpener] read finished bytes=" + payload.getBytes().length +
                        " elapsedMs=" + duration + " path=" + path);

                VirtualResource virtualResource = new VirtualResource(
                        VirtualResourceRef.of("ftp:" + path),
                        VirtualResourceKind.FILE,
                        path,
                        VirtualBackendType.FTP,
                        new FtpResourceState(
                                new de.bund.zrb.files.auth.ConnectionId("ftp", host, user),
                                Boolean.TRUE,
                                "MVS",
                                encoding));

                SwingUtilities.invokeLater(() -> tabbedPaneManager.openFileTab(virtualResource, content, null, null, false));
            } catch (InterruptedException ie) {
                System.out.println("[AsyncRawFileOpener] cancelled path=" + path);
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - started;
                System.err.println("[AsyncRawFileOpener] open failed after " + duration + "ms path=" + path +
                        " error=" + e.getMessage());
                if (!cancelled.get()) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            parentComponent,
                            "Datei konnte nicht geöffnet werden:\n" + e.getMessage(),
                            "Öffnen fehlgeschlagen",
                            JOptionPane.ERROR_MESSAGE));
                }
            } finally {
                currentRetryExecutor = null;
                SwingUtilities.invokeLater(() -> {
                    if (window != null && originalCursor != null) {
                        window.setCursor(originalCursor);
                    }
                    if (onDoneUi != null) {
                        onDoneUi.run();
                    }
                });
            }
        });
    }

    private FilePayload readWithRetry(final String path) throws Exception {
        // Get retry policy from Settings
        Settings settings = SettingsHelper.load();
        FtpRetryPolicy policy = FtpRetryPolicy.fromSettings(settings);

        // Log active policy
        System.out.println("[AsyncRawFileOpener] RetryPolicy: " + policy);

        // Create executor with logging listener
        RetryExecutor retryExecutor = new RetryExecutor(policy,
                RetryExecutor.createLoggingListener("readFile(" + path + ")"));
        currentRetryExecutor = retryExecutor;

        return retryExecutor.execute(new Callable<FilePayload>() {
            @Override
            public FilePayload call() throws Exception {
                if (cancelled.get()) {
                    throw new InterruptedException("Operation cancelled");
                }
                FileService service = getOrCreateService();
                return service.readFile(path);
            }
        }, null);
    }

    private FileService getOrCreateService() throws Exception {
        synchronized (lock) {
            if (openFileService == null) {
                long t0 = System.currentTimeMillis();
                openFileService = new FileServiceFactory().createFtp(host, user, password);
                System.out.println("[AsyncRawFileOpener] created openFileService elapsedMs=" +
                        (System.currentTimeMillis() - t0));
            }
            return openFileService;
        }
    }

    private void resetService() {
        synchronized (lock) {
            if (openFileService != null) {
                try {
                    openFileService.close();
                } catch (Exception ignore) {
                }
                openFileService = null;
            }
        }
    }

    public void shutdown() {
        resetService();
        executor.shutdownNow();
    }
}
