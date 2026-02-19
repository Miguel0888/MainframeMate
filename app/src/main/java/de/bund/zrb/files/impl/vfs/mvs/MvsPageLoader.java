package de.bund.zrb.files.impl.vfs.mvs;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asynchronous page loader for MVS directory listings.
 *
 * Features:
 * - First page delivered ASAP
 * - Remaining pages loaded in background
 * - Cancellation support
 * - Thread-safe
 */
public class MvsPageLoader {

    private static final int DEFAULT_PAGE_SIZE = 200;

    private final MvsFtpClient ftpClient;
    private final MvsLoadedModel model;
    private final ExecutorService executor;

    private volatile AtomicBoolean currentCancellation = null;
    private volatile MvsLocation currentLocation = null;

    /**
     * Listener for load completion.
     */
    public interface LoadListener {
        void onFirstPageLoaded(int itemCount);
        void onLoadComplete(int totalItems);
        void onLoadError(String message);
    }

    private LoadListener listener;

    public MvsPageLoader(MvsFtpClient ftpClient, MvsLoadedModel model) {
        this.ftpClient = ftpClient;
        this.model = model;
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Set load listener.
     */
    public void setListener(LoadListener listener) {
        this.listener = listener;
    }

    /**
     * Start loading children of a location.
     * Cancels any previous loading operation.
     */
    public void loadChildren(MvsLocation location) {
        loadChildren(location, DEFAULT_PAGE_SIZE);
    }

    /**
     * Start loading children of a location with specified page size.
     */
    public void loadChildren(MvsLocation location, int pageSize) {
        // Cancel previous load
        cancelCurrentLoad();

        // Clear model and set new location
        model.clear();
        currentLocation = location;
        currentCancellation = new AtomicBoolean(false);
        model.setLoading(true);

        final AtomicBoolean cancellation = currentCancellation;

        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("[MvsPageLoader] Starting load for: " + location);

                    final boolean[] firstPageDelivered = {false};

                    ftpClient.listChildren(location, pageSize, cancellation, new MvsFtpClient.PageCallback() {
                        @Override
                        public void onPage(List<MvsVirtualResource> items, boolean isLast) {
                            if (cancellation.get()) {
                                return;
                            }

                            // Add items to model (handles deduplication)
                            List<MvsVirtualResource> added = model.addItems(items);

                            System.out.println("[MvsPageLoader] Page received: " + items.size() +
                                              " items, " + added.size() + " new, isLast=" + isLast);

                            if (!firstPageDelivered[0] && !added.isEmpty()) {
                                firstPageDelivered[0] = true;
                                if (listener != null) {
                                    listener.onFirstPageLoaded(model.getLoadedCount());
                                }
                            }

                            if (isLast) {
                                model.setLoading(false);
                                if (listener != null) {
                                    listener.onLoadComplete(model.getLoadedCount());
                                }
                            }
                        }
                    });

                } catch (IOException e) {
                    System.err.println("[MvsPageLoader] Error loading: " + e.getMessage());
                    model.setLoading(false);
                    model.notifyError("Fehler beim Laden: " + e.getMessage());
                    if (listener != null) {
                        listener.onLoadError(e.getMessage());
                    }
                }
            }
        });
    }

    /**
     * Cancel the current loading operation.
     */
    public void cancelCurrentLoad() {
        if (currentCancellation != null) {
            currentCancellation.set(true);
        }
        model.setLoading(false);
    }

    /**
     * Check if currently loading.
     */
    public boolean isLoading() {
        return model.isLoading();
    }

    /**
     * Get current location being loaded.
     */
    public MvsLocation getCurrentLocation() {
        return currentLocation;
    }

    /**
     * Shutdown the executor.
     */
    public void shutdown() {
        cancelCurrentLoad();
        executor.shutdownNow();
    }
}

