package de.bund.zrb.files.impl.vfs.mvs;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Controller for MVS browser UI.
 *
 * Coordinates between:
 * - MvsFtpClient (FTP operations)
 * - MvsLoadedModel (data model)
 * - MvsPageLoader (background loading)
 * - UI components (via listeners)
 */
public class MvsBrowserController implements MvsLoadedModel.ModelListener, MvsPageLoader.LoadListener {

    /**
     * Listener for UI updates.
     */
    public interface BrowserListener {
        void onViewModelChanged(List<MvsVirtualResource> viewModel);
        void onLoadingStateChanged(boolean isLoading, int loadedCount);
        void onError(String message);
        void onStatusMessage(String message);
    }

    private final MvsFtpClient ftpClient;
    private final MvsLoadedModel model;
    private final MvsPageLoader pageLoader;

    private final List<BrowserListener> listeners = new CopyOnWriteArrayList<BrowserListener>();

    private MvsLocation currentLocation;

    public MvsBrowserController(MvsFtpClient ftpClient) {
        this.ftpClient = ftpClient;
        this.model = new MvsLoadedModel();
        this.pageLoader = new MvsPageLoader(ftpClient, model);

        // Wire up listeners
        model.addListener(this);
        pageLoader.setListener(this);
    }

    /**
     * Add a browser listener.
     */
    public void addListener(BrowserListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Remove a browser listener.
     */
    public void removeListener(BrowserListener listener) {
        listeners.remove(listener);
    }

    /**
     * Navigate to a path (input from UI).
     * Handles quote normalization automatically.
     */
    public void navigateTo(String path) {
        MvsLocation location = MvsLocation.parse(path);
        navigateTo(location);
    }

    /**
     * Navigate to a location.
     */
    public void navigateTo(MvsLocation location) {
        System.out.println("[MvsBrowserController] Navigating to: " + location);

        currentLocation = location;

        if (location.getType() == MvsLocationType.ROOT) {
            // ROOT - show hint
            model.clear();
            notifyStatus("Bitte HLQ eingeben (z.B. USERID)");
            notifyViewModelChanged();
            return;
        }

        if (location.getType() == MvsLocationType.MEMBER) {
            // MEMBER - this should open the file, not list
            notifyStatus("Member kann nicht aufgelistet werden");
            return;
        }

        // HLQ or DATASET - load children
        pageLoader.loadChildren(location);
    }

    /**
     * Open a resource (double-click action).
     * Returns true if handled as navigation, false if it should open as file.
     */
    public boolean openResource(MvsVirtualResource resource) {
        if (resource == null) {
            return false;
        }

        MvsLocation location = resource.getLocation();

        switch (location.getType()) {
            case HLQ:
            case DATASET:
                // Navigate into it
                navigateTo(location);
                return true;

            case MEMBER:
                // Open as file
                return false;

            default:
                return false;
        }
    }

    /**
     * Refresh current listing.
     */
    public void refresh() {
        if (currentLocation != null) {
            navigateTo(currentLocation);
        }
    }

    /**
     * Get current location.
     */
    public MvsLocation getCurrentLocation() {
        return currentLocation;
    }

    /**
     * Get current logical path for display.
     */
    public String getCurrentPath() {
        return currentLocation != null ? currentLocation.getLogicalPath() : "";
    }

    /**
     * Set filter pattern.
     */
    public void setFilter(String pattern) {
        model.setFilterPattern(pattern);
        notifyViewModelChanged();
    }

    /**
     * Get current view model.
     */
    public List<MvsVirtualResource> getViewModel() {
        return model.getViewModel();
    }

    /**
     * Check if loading.
     */
    public boolean isLoading() {
        return pageLoader.isLoading();
    }

    /**
     * Cancel current loading.
     */
    public void cancelLoading() {
        pageLoader.cancelCurrentLoad();
    }

    /**
     * Shutdown controller.
     */
    public void shutdown() {
        pageLoader.shutdown();
    }

    // === MvsLoadedModel.ModelListener ===

    @Override
    public void onItemsAdded(List<MvsVirtualResource> newItems) {
        // Notify UI that view model may have changed
        notifyViewModelChanged();
    }

    @Override
    public void onLoadingStateChanged(boolean isLoading) {
        for (BrowserListener listener : listeners) {
            listener.onLoadingStateChanged(isLoading, model.getLoadedCount());
        }
    }

    @Override
    public void onError(String message) {
        for (BrowserListener listener : listeners) {
            listener.onError(message);
        }
    }

    // === MvsPageLoader.LoadListener ===

    @Override
    public void onFirstPageLoaded(int itemCount) {
        notifyStatus(itemCount + " Einträge geladen, weitere werden geladen...");
    }

    @Override
    public void onLoadComplete(int totalItems) {
        notifyStatus(totalItems + " Einträge");
    }

    @Override
    public void onLoadError(String message) {
        for (BrowserListener listener : listeners) {
            listener.onError("Fehler beim Laden: " + message);
        }
    }

    // === Private helpers ===

    private void notifyViewModelChanged() {
        List<MvsVirtualResource> viewModel = model.getViewModel();
        for (BrowserListener listener : listeners) {
            listener.onViewModelChanged(viewModel);
        }
    }

    private void notifyStatus(String message) {
        for (BrowserListener listener : listeners) {
            listener.onStatusMessage(message);
        }
    }
}

