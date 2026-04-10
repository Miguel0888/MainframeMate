package de.bund.zrb.ui.mermaid.tile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the tiled diagram rendering infrastructure.
 * These tests use synthetic SVG data and do not require the Mermaid JS runtime.
 */
class TiledDiagramRendererTest {

    /** Minimal SVG with a known viewBox for testing. */
    private static final String SMALL_SVG =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 400 300\">"
                    + "<rect width=\"400\" height=\"300\" fill=\"white\"/>"
                    + "<circle cx=\"200\" cy=\"150\" r=\"50\" fill=\"blue\"/>"
                    + "</svg>";

    /** Large SVG that exceeds the tile threshold (viewBox > 1500). */
    private static final String LARGE_SVG =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 3200 2400\">"
                    + "<rect width=\"3200\" height=\"2400\" fill=\"white\"/>"
                    + "<circle cx=\"800\" cy=\"600\" r=\"100\" fill=\"red\"/>"
                    + "<circle cx=\"2400\" cy=\"1800\" r=\"100\" fill=\"green\"/>"
                    + "</svg>";

    @Test
    @DisplayName("Small SVG → single tile")
    void smallSvgSingleTile() {
        TiledDiagramRenderer r = new TiledDiagramRenderer(SMALL_SVG, "test-small");
        assertEquals(1, r.getCols());
        assertEquals(1, r.getRows());
        assertTrue(r.isSingleTile());
        assertEquals(1, r.getTileCount());
    }

    @Test
    @DisplayName("Large SVG → multiple tiles")
    void largeSvgMultipleTiles() {
        TiledDiagramRenderer r = new TiledDiagramRenderer(LARGE_SVG, "test-large");
        assertTrue(r.getCols() > 1, "Should have multiple columns");
        assertTrue(r.getRows() > 1, "Should have multiple rows");
        assertFalse(r.isSingleTile());
        assertEquals(r.getCols() * r.getRows(), r.getTileCount());
    }

    @Test
    @DisplayName("ViewBox is parsed correctly")
    void viewBoxParsing() {
        TiledDiagramRenderer r = new TiledDiagramRenderer(LARGE_SVG, "test-vb");
        assertEquals(0.0, r.getViewBoxX(), 0.01);
        assertEquals(0.0, r.getViewBoxY(), 0.01);
        assertEquals(3200.0, r.getViewBoxWidth(), 0.01);
        assertEquals(2400.0, r.getViewBoxHeight(), 0.01);
    }

    @Test
    @DisplayName("Tile grid covers entire viewBox without gaps")
    void tilesCoverViewBox() {
        TiledDiagramRenderer r = new TiledDiagramRenderer(LARGE_SVG, "test-cover");
        List<DiagramTile> all = r.getAllTiles();

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (DiagramTile t : all) {
            minX = Math.min(minX, t.getSvgX());
            minY = Math.min(minY, t.getSvgY());
            maxX = Math.max(maxX, t.getSvgX() + t.getSvgW());
            maxY = Math.max(maxY, t.getSvgY() + t.getSvgH());
        }

        assertEquals(r.getViewBoxX(), minX, 0.01, "Grid should start at viewBox X");
        assertEquals(r.getViewBoxY(), minY, 0.01, "Grid should start at viewBox Y");
        assertEquals(r.getViewBoxX() + r.getViewBoxWidth(), maxX, 0.01,
                "Grid should end at viewBox right edge");
        assertEquals(r.getViewBoxY() + r.getViewBoxHeight(), maxY, 0.01,
                "Grid should end at viewBox bottom edge");
    }

    @Test
    @DisplayName("Visible tiles: full viewport → all tiles")
    void allTilesVisibleInFullViewport() {
        TiledDiagramRenderer r = new TiledDiagramRenderer(LARGE_SVG, "test-vis-all");
        List<DiagramTile> visible = r.getVisibleTiles(0, 0, 3200, 2400);
        assertEquals(r.getTileCount(), visible.size(),
                "All tiles should be visible in the full viewport");
    }

    @Test
    @DisplayName("Visible tiles: small viewport → subset of tiles")
    void subsetOfTilesVisible() {
        TiledDiagramRenderer r = new TiledDiagramRenderer(LARGE_SVG, "test-vis-sub");
        // Query a small region in the top-left corner
        List<DiagramTile> visible = r.getVisibleTiles(0, 0, 400, 300);
        assertTrue(visible.size() > 0, "Should find at least one tile");
        assertTrue(visible.size() < r.getTileCount(),
                "Should not find all tiles for a small viewport");
    }

    @Test
    @DisplayName("Visible tiles: viewport outside diagram → no tiles")
    void noTilesOutsideViewport() {
        TiledDiagramRenderer r = new TiledDiagramRenderer(LARGE_SVG, "test-vis-none");
        List<DiagramTile> visible = r.getVisibleTiles(5000, 5000, 100, 100);
        assertEquals(0, visible.size(), "No tiles should be visible outside the diagram");
    }

    @Test
    @DisplayName("LOD selection based on zoom level")
    void lodForZoom() {
        assertEquals(0, TiledDiagramRenderer.lodForZoom(0.05));
        assertEquals(1, TiledDiagramRenderer.lodForZoom(0.3));
        assertEquals(2, TiledDiagramRenderer.lodForZoom(1.0));
        assertEquals(3, TiledDiagramRenderer.lodForZoom(5.0));
    }

    @Test
    @DisplayName("clipSvgToViewBox modifies viewBox attribute")
    void clipSvgModifiesViewBox() {
        String clipped = TiledDiagramRenderer.clipSvgToViewBox(LARGE_SVG, 100, 200, 800, 600);
        assertTrue(clipped.contains("100.00 200.00 800.00 600.00"),
                "Clipped SVG should contain the new viewBox: " + clipped);
        assertTrue(clipped.contains("<circle"), "Original content should be preserved");
    }

    @Test
    @DisplayName("Tile cache: store and retrieve")
    void tileCacheStoreAndRetrieve(@TempDir File tempDir) {
        DiagramTileCache cache = new DiagramTileCache(tempDir);
        DiagramTile tile = new DiagramTile(0, 0, 0, 0, 800, 600, 512, 384);

        BufferedImage img = new BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB);
        cache.put("test-diagram", tile, 2, img);

        assertTrue(cache.has("test-diagram", tile, 2));
        BufferedImage loaded = cache.get("test-diagram", tile, 2);
        assertNotNull(loaded, "Should load the cached tile");
        assertEquals(64, loaded.getWidth());
        assertEquals(48, loaded.getHeight());
    }

    @Test
    @DisplayName("Tile cache: evict diagram")
    void tileCacheEvict(@TempDir File tempDir) {
        DiagramTileCache cache = new DiagramTileCache(tempDir);
        DiagramTile tile = new DiagramTile(0, 0, 0, 0, 800, 600, 512, 384);

        BufferedImage img = new BufferedImage(32, 24, BufferedImage.TYPE_INT_ARGB);
        cache.put("evict-test", tile, 1, img);
        assertTrue(cache.has("evict-test", tile, 1));

        cache.evict("evict-test");
        assertFalse(cache.has("evict-test", tile, 1),
                "Tile should be gone after eviction");
    }

    @Test
    @DisplayName("Tile cache: evict all")
    void tileCacheEvictAll(@TempDir File tempDir) {
        DiagramTileCache cache = new DiagramTileCache(tempDir);
        DiagramTile tile = new DiagramTile(1, 1, 800, 600, 800, 600, 512, 384);

        cache.put("d1", tile, 0, new BufferedImage(16, 12, BufferedImage.TYPE_INT_ARGB));
        cache.put("d2", tile, 0, new BufferedImage(16, 12, BufferedImage.TYPE_INT_ARGB));

        cache.evictAll();
        assertFalse(cache.has("d1", tile, 0));
        assertFalse(cache.has("d2", tile, 0));
    }

    @Test
    @DisplayName("DiagramTile: SoftReference lifecycle")
    void tileSoftReference() {
        DiagramTile tile = new DiagramTile(0, 0, 0, 0, 100, 100, 256, 256);
        assertNull(tile.getImage(), "Initially no image");

        BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        tile.setImage(img);
        assertNotNull(tile.getImage(), "Image should be retrievable");

        tile.setImage(null);
        assertNull(tile.getImage(), "Image should be null after clearing");
    }

    @Test
    @DisplayName("Tile cacheKey includes LOD")
    void tileCacheKeyFormat() {
        DiagramTile tile = new DiagramTile(3, 2, 0, 0, 100, 100, 256, 256);
        assertEquals("3_2_lod0", tile.cacheKey(0));
        assertEquals("3_2_lod3", tile.cacheKey(3));
    }

    @Test
    @DisplayName("Render a single tile (Batik integration)")
    void renderSingleTile() {
        TiledDiagramRenderer r = new TiledDiagramRenderer(SMALL_SVG, "test-render");
        DiagramTile tile = r.getTile(0, 0);

        BufferedImage img = r.renderTile(tile, 0, null);
        assertNotNull(img, "Batik should render the tile");
        assertTrue(img.getWidth() > 0);
        assertTrue(img.getHeight() > 0);
    }
}

