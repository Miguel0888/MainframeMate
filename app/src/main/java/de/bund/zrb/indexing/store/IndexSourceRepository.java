package de.bund.zrb.indexing.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.indexing.model.IndexSource;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persists configured IndexSource objects (indexing policies).
 *
 * Storage: ~/.mainframemate/db/indexing/sources.json
 */
public class IndexSourceRepository {

    private static final Logger LOG = Logger.getLogger(IndexSourceRepository.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<IndexSource>>() {}.getType();

    private static final File FILE = new File(SettingsHelper.getSettingsFolder(), "db/indexing/sources.json");

    /**
     * Load all configured sources.
     */
    public List<IndexSource> loadAll() {
        if (!FILE.exists()) return new ArrayList<>();
        try (Reader r = new InputStreamReader(new FileInputStream(FILE), StandardCharsets.UTF_8)) {
            List<IndexSource> list = GSON.fromJson(r, LIST_TYPE);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error loading index sources", e);
            return new ArrayList<>();
        }
    }

    /**
     * Save all sources.
     */
    public void saveAll(List<IndexSource> sources) {
        FILE.getParentFile().mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(FILE), StandardCharsets.UTF_8)) {
            GSON.toJson(sources, LIST_TYPE, w);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error saving index sources", e);
        }
    }

    /**
     * Find a source by ID.
     */
    public IndexSource findById(String sourceId) {
        for (IndexSource source : loadAll()) {
            if (sourceId.equals(source.getSourceId())) return source;
        }
        return null;
    }

    /**
     * Add or update a source.
     */
    public void save(IndexSource source) {
        List<IndexSource> all = loadAll();
        boolean found = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getSourceId().equals(source.getSourceId())) {
                all.set(i, source);
                found = true;
                break;
            }
        }
        if (!found) all.add(source);
        saveAll(all);
    }

    /**
     * Remove a source by ID.
     */
    public boolean remove(String sourceId) {
        List<IndexSource> all = loadAll();
        boolean removed = all.removeIf(s -> sourceId.equals(s.getSourceId()));
        if (removed) saveAll(all);
        return removed;
    }

    /**
     * Get all enabled sources.
     */
    public List<IndexSource> getEnabled() {
        List<IndexSource> result = new ArrayList<>();
        for (IndexSource s : loadAll()) {
            if (s.isEnabled()) result.add(s);
        }
        return result;
    }
}
