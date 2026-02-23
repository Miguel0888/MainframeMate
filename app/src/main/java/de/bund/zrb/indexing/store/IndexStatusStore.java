package de.bund.zrb.indexing.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.indexing.model.IndexItemState;
import de.bund.zrb.indexing.model.IndexItemStatus;
import de.bund.zrb.indexing.model.IndexRunStatus;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persists IndexItemStatus and IndexRunStatus per source as JSON files.
 *
 * Storage layout:
 *   ~/.mainframemate/db/indexing/status/{sourceId}/items.json
 *   ~/.mainframemate/db/indexing/status/{sourceId}/runs.json
 *
 * Thread-safety: synchronized on instance level (single-writer assumption).
 */
public class IndexStatusStore {

    private static final Logger LOG = Logger.getLogger(IndexStatusStore.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type ITEM_MAP_TYPE = new TypeToken<Map<String, IndexItemStatus>>() {}.getType();
    private static final Type RUN_LIST_TYPE = new TypeToken<List<IndexRunStatus>>() {}.getType();

    private static final int MAX_RUNS_KEPT = 50;

    private final File baseDir;

    public IndexStatusStore() {
        this.baseDir = new File(SettingsHelper.getSettingsFolder(), "db/indexing/status");
    }

    // ═══════════════════════════════════════════════════════════════
    //  Item Status
    // ═══════════════════════════════════════════════════════════════

    /**
     * Load all item statuses for a source.
     * Key = itemPath.
     */
    public synchronized Map<String, IndexItemStatus> loadItemStatuses(String sourceId) {
        File file = itemsFile(sourceId);
        if (!file.exists()) return new LinkedHashMap<>();
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Map<String, IndexItemStatus> map = GSON.fromJson(r, ITEM_MAP_TYPE);
            return map != null ? map : new LinkedHashMap<>();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error loading item statuses for " + sourceId, e);
            return new LinkedHashMap<>();
        }
    }

    /**
     * Save all item statuses for a source.
     */
    public synchronized void saveItemStatuses(String sourceId, Map<String, IndexItemStatus> statuses) {
        File file = itemsFile(sourceId);
        file.getParentFile().mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(statuses, ITEM_MAP_TYPE, w);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error saving item statuses for " + sourceId, e);
        }
    }

    /**
     * Update a single item status.
     */
    public synchronized void updateItemStatus(String sourceId, IndexItemStatus status) {
        Map<String, IndexItemStatus> map = loadItemStatuses(sourceId);
        map.put(status.getItemPath(), status);
        saveItemStatuses(sourceId, map);
    }

    /**
     * Get status for a specific item, or null if not tracked.
     */
    public synchronized IndexItemStatus getItemStatus(String sourceId, String itemPath) {
        return loadItemStatuses(sourceId).get(itemPath);
    }

    /**
     * Count items by state for a source.
     */
    public synchronized Map<IndexItemState, Integer> countByState(String sourceId) {
        Map<IndexItemState, Integer> counts = new LinkedHashMap<>();
        for (IndexItemState s : IndexItemState.values()) counts.put(s, 0);
        for (IndexItemStatus item : loadItemStatuses(sourceId).values()) {
            counts.merge(item.getState(), 1, Integer::sum);
        }
        return counts;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Run Status (history of indexing runs)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Load run history for a source (newest first).
     */
    public synchronized List<IndexRunStatus> loadRuns(String sourceId) {
        File file = runsFile(sourceId);
        if (!file.exists()) return new ArrayList<>();
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            List<IndexRunStatus> list = GSON.fromJson(r, RUN_LIST_TYPE);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error loading run history for " + sourceId, e);
            return new ArrayList<>();
        }
    }

    /**
     * Save a completed run to history (keeps last MAX_RUNS_KEPT).
     */
    public synchronized void saveRun(IndexRunStatus run) {
        List<IndexRunStatus> runs = loadRuns(run.getSourceId());
        runs.add(0, run); // newest first
        if (runs.size() > MAX_RUNS_KEPT) {
            runs = new ArrayList<>(runs.subList(0, MAX_RUNS_KEPT));
        }
        File file = runsFile(run.getSourceId());
        file.getParentFile().mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(runs, RUN_LIST_TYPE, w);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error saving run for " + run.getSourceId(), e);
        }
    }

    /**
     * Get the last successful run for a source, or null.
     */
    public synchronized IndexRunStatus getLastSuccessfulRun(String sourceId) {
        for (IndexRunStatus run : loadRuns(sourceId)) {
            if (run.getRunState() == IndexRunStatus.RunState.COMPLETED) return run;
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Cleanup
    // ═══════════════════════════════════════════════════════════════

    /**
     * Delete all status data for a source (used when removing a source config).
     */
    public synchronized void deleteSource(String sourceId) {
        File dir = sourceDir(sourceId);
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
            dir.delete();
        }
    }

    // ─── Helpers ───

    private File sourceDir(String sourceId) {
        return new File(baseDir, sourceId);
    }

    private File itemsFile(String sourceId) {
        return new File(sourceDir(sourceId), "items.json");
    }

    private File runsFile(String sourceId) {
        return new File(sourceDir(sourceId), "runs.json");
    }
}
