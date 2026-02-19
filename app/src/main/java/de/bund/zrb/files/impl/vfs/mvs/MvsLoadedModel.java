package de.bund.zrb.files.impl.vfs.mvs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Loaded model for MVS directory listing.
 * Stores all loaded items, handles deduplication, and provides filtering/sorting.
 *
 * Thread-safe for concurrent loading and UI access.
 */
public class MvsLoadedModel {

    /**
     * Listener for model changes.
     */
    public interface ModelListener {
        void onItemsAdded(List<MvsVirtualResource> newItems);
        void onModelCleared();
        void onLoadingStateChanged(boolean isLoading);
        void onError(String message);
    }

    /** All loaded items, keyed by normalized path for deduplication */
    private final Map<String, MvsVirtualResource> items = new LinkedHashMap<String, MvsVirtualResource>();

    /** Current loading state */
    private volatile boolean loading = false;

    /** Total items loaded so far */
    private volatile int loadedCount = 0;

    /** Listeners */
    private final List<ModelListener> listeners = new CopyOnWriteArrayList<ModelListener>();

    /** Current filter */
    private volatile String filterPattern = "";

    /** Sort comparator */
    private Comparator<MvsVirtualResource> comparator = DEFAULT_COMPARATOR;

    private static final Comparator<MvsVirtualResource> DEFAULT_COMPARATOR = new Comparator<MvsVirtualResource>() {
        @Override
        public int compare(MvsVirtualResource a, MvsVirtualResource b) {
            // Directories first, then alphabetical by display name
            if (a.isDirectory() != b.isDirectory()) {
                return a.isDirectory() ? -1 : 1;
            }
            return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
        }
    };

    /**
     * Add a listener for model changes.
     */
    public void addListener(ModelListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Remove a listener.
     */
    public void removeListener(ModelListener listener) {
        listeners.remove(listener);
    }

    /**
     * Clear all items and reset state.
     */
    public synchronized void clear() {
        items.clear();
        loadedCount = 0;
        loading = false;
        for (ModelListener listener : listeners) {
            listener.onModelCleared();
        }
    }

    /**
     * Set loading state.
     */
    public void setLoading(boolean loading) {
        this.loading = loading;
        for (ModelListener listener : listeners) {
            listener.onLoadingStateChanged(loading);
        }
    }

    /**
     * Check if loading is in progress.
     */
    public boolean isLoading() {
        return loading;
    }

    /**
     * Add items (with deduplication).
     * @return list of actually new items
     */
    public synchronized List<MvsVirtualResource> addItems(List<MvsVirtualResource> newItems) {
        List<MvsVirtualResource> added = new ArrayList<MvsVirtualResource>();

        for (MvsVirtualResource item : newItems) {
            String key = item.getKey();
            if (!items.containsKey(key)) {
                items.put(key, item);
                added.add(item);
                loadedCount++;
            }
        }

        if (!added.isEmpty()) {
            for (ModelListener listener : listeners) {
                listener.onItemsAdded(added);
            }
        }

        return added;
    }

    /**
     * Get all loaded items.
     */
    public synchronized List<MvsVirtualResource> getAllItems() {
        return new ArrayList<MvsVirtualResource>(items.values());
    }

    /**
     * Get total loaded count.
     */
    public int getLoadedCount() {
        return loadedCount;
    }

    /**
     * Set filter pattern.
     */
    public void setFilterPattern(String pattern) {
        this.filterPattern = pattern != null ? pattern.trim() : "";
    }

    /**
     * Get current filter pattern.
     */
    public String getFilterPattern() {
        return filterPattern;
    }

    /**
     * Set sort comparator.
     */
    public void setComparator(Comparator<MvsVirtualResource> comparator) {
        this.comparator = comparator != null ? comparator : DEFAULT_COMPARATOR;
    }

    /**
     * Get filtered and sorted view model.
     */
    public synchronized List<MvsVirtualResource> getViewModel() {
        List<MvsVirtualResource> result = new ArrayList<MvsVirtualResource>();
        String filter = filterPattern.toLowerCase();

        for (MvsVirtualResource item : items.values()) {
            if (filter.isEmpty() || matchesFilter(item, filter)) {
                result.add(item);
            }
        }

        Collections.sort(result, comparator);
        return result;
    }

    /**
     * Check if an item matches the filter.
     */
    private boolean matchesFilter(MvsVirtualResource item, String filter) {
        // Simple contains filter on display name
        String displayName = item.getDisplayName().toLowerCase();

        // Support regex if pattern starts with /
        if (filter.startsWith("/") && filter.length() > 1) {
            try {
                String regex = filter.substring(1);
                return displayName.matches(".*" + regex + ".*");
            } catch (Exception e) {
                // Invalid regex - fall back to contains
                return displayName.contains(filter);
            }
        }

        return displayName.contains(filter);
    }

    /**
     * Notify listeners of an error.
     */
    public void notifyError(String message) {
        for (ModelListener listener : listeners) {
            listener.onError(message);
        }
    }
}

