package de.bund.zrb.ui.mermaid.tile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UUID-based disk cache for diagram tiles at different LOD levels.
 * <p>
 * Structure:  {@code <cacheRoot>/<diagramId>/<col>_<row>_lod<n>.png}
 * <p>
 * Each diagram gets a UUID directory. Inside, each tile is stored as a
 * PNG at each rendered LOD level. This allows:
 * <ul>
 *   <li>Quick reload of evicted tiles (SoftReference cleared by GC)</li>
 *   <li>Persistent cache across zoom levels (different LODs coexist)</li>
 *   <li>Sequential export: read tiles from disk one-by-one, never OOM</li>
 * </ul>
 */
public final class DiagramTileCache {

    private static final Logger LOG = Logger.getLogger(DiagramTileCache.class.getName());

    private final File cacheRoot;

    /**
     * @param cacheRoot root directory for all diagram caches, e.g.
     *                  {@code ~/.mainframemate/cache/diagram-tiles/}
     */
    public DiagramTileCache(File cacheRoot) {
        this.cacheRoot = cacheRoot;
        if (!cacheRoot.exists()) {
            cacheRoot.mkdirs();
        }
    }

    /** Get or create the directory for a specific diagram. */
    private File diagramDir(String diagramId) {
        File dir = new File(cacheRoot, diagramId);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Store a rendered tile to disk.
     *
     * @param diagramId unique diagram identifier (UUID)
     * @param tile      the tile (provides col/row)
     * @param lod       level of detail
     * @param image     the raster image to persist
     */
    public void put(String diagramId, DiagramTile tile, int lod, BufferedImage image) {
        if (image == null) return;
        File file = tileFile(diagramId, tile.cacheKey(lod));
        try {
            ImageIO.write(image, "PNG", file);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[TileCache] Failed to write tile " + file.getName(), e);
        }
    }

    /**
     * Load a tile from disk cache.
     *
     * @return the cached image, or null if not cached
     */
    public BufferedImage get(String diagramId, DiagramTile tile, int lod) {
        File file = tileFile(diagramId, tile.cacheKey(lod));
        if (!file.exists()) return null;
        try {
            return ImageIO.read(file);
        } catch (IOException e) {
            LOG.log(Level.FINE, "[TileCache] Failed to read tile " + file.getName(), e);
            return null;
        }
    }

    /** Check whether a tile exists on disk. */
    public boolean has(String diagramId, DiagramTile tile, int lod) {
        return tileFile(diagramId, tile.cacheKey(lod)).exists();
    }

    /** Evict all cached tiles for a specific diagram. */
    public void evict(String diagramId) {
        File dir = new File(cacheRoot, diagramId);
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            dir.delete();
        }
    }

    /** Evict ALL cached diagrams. */
    public void evictAll() {
        File[] dirs = cacheRoot.listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                if (dir.isDirectory()) {
                    evict(dir.getName());
                }
            }
        }
    }

    /** Approximate total cache size in bytes. */
    public long getCacheSizeBytes() {
        long total = 0;
        File[] dirs = cacheRoot.listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                if (dir.isDirectory()) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            total += f.length();
                        }
                    }
                }
            }
        }
        return total;
    }

    private File tileFile(String diagramId, String key) {
        return new File(diagramDir(diagramId), key + ".png");
    }
}

