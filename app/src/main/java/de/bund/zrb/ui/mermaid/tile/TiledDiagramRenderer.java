package de.bund.zrb.ui.mermaid.tile;

import de.bund.zrb.wiki.ui.SvgRenderer;

import java.awt.image.BufferedImage;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Breaks a large SVG diagram into a grid of tiles and renders each tile
 * independently, keeping only a bounded number of raster images in memory
 * at any time.
 * <p>
 * <b>Key idea:</b> Given the full SVG and its viewBox, we split the SVG
 * coordinate space into an N×M grid. For each tile, we produce a clipped
 * SVG (same markup, but with a modified {@code viewBox} that covers only
 * that tile's region) and rasterise it via Batik at the required LOD.
 * <p>
 * <b>LOD levels:</b>
 * <ul>
 *   <li>LOD 0 — thumbnail: max 256 px wide per tile</li>
 *   <li>LOD 1 — medium:   max 512 px wide per tile</li>
 *   <li>LOD 2 — detail:   max 1024 px wide per tile (readable text)</li>
 *   <li>LOD 3 — crisp:    max 2048 px wide per tile (print quality)</li>
 * </ul>
 */
public final class TiledDiagramRenderer {

    private static final Logger LOG = Logger.getLogger(TiledDiagramRenderer.class.getName());
    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** Maximum tile edge in SVG user-space units. */
    private static final double MAX_TILE_SVG_SIZE = 800.0;

    /** LOD → max tile pixel width. */
    private static final int[] LOD_MAX_PX = {256, 512, 1024, 2048};

    private final String fullSvg;
    private final String diagramId;
    private final double vbX, vbY, vbW, vbH;
    private final int cols, rows;
    private final DiagramTile[][] grid;

    /**
     * Pre-compute the tile grid from the full SVG's viewBox.
     *
     * @param fullSvg    the complete SVG markup (after MermaidSvgFixup)
     * @param diagramId  unique id for disk cache (UUID recommended)
     */
    public TiledDiagramRenderer(String fullSvg, String diagramId) {
        this.fullSvg = fullSvg;
        this.diagramId = diagramId != null ? diagramId : UUID.randomUUID().toString();

        double[] vb = parseViewBox(fullSvg);
        this.vbX = vb[0];
        this.vbY = vb[1];
        this.vbW = vb[2];
        this.vbH = vb[3];

        // Compute grid dimensions
        this.cols = Math.max(1, (int) Math.ceil(vbW / MAX_TILE_SVG_SIZE));
        this.rows = Math.max(1, (int) Math.ceil(vbH / MAX_TILE_SVG_SIZE));

        double tileW = vbW / cols;
        double tileH = vbH / rows;

        this.grid = new DiagramTile[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double tx = vbX + c * tileW;
                double ty = vbY + r * tileH;
                // Pixel dimensions at LOD 2 (default detail level)
                int pw = LOD_MAX_PX[2];
                int ph = (int) Math.round(pw * (tileH / tileW));
                grid[r][c] = new DiagramTile(c, r, tx, ty, tileW, tileH, pw, ph);
            }
        }
    }

    // ── Public getters ─────────────────────────────────────────

    public String getDiagramId() { return diagramId; }
    public String getFullSvg() { return fullSvg; }
    public int getCols() { return cols; }
    public int getRows() { return rows; }
    public double getViewBoxX() { return vbX; }
    public double getViewBoxY() { return vbY; }
    public double getViewBoxWidth() { return vbW; }
    public double getViewBoxHeight() { return vbH; }

    /** Total number of tiles. */
    public int getTileCount() { return cols * rows; }

    /** True when the diagram is small enough for a single tile. */
    public boolean isSingleTile() { return cols == 1 && rows == 1; }

    public DiagramTile getTile(int col, int row) {
        return grid[row][col];
    }

    /** Flat list of all tiles (row-major order). */
    public List<DiagramTile> getAllTiles() {
        List<DiagramTile> list = new ArrayList<DiagramTile>(cols * rows);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                list.add(grid[r][c]);
            }
        }
        return list;
    }

    // ── Rendering ──────────────────────────────────────────────

    /**
     * Render a single tile at the given LOD.
     * This creates a clipped SVG with a modified viewBox and rasterises it.
     * Thread-safe — can be called from multiple workers concurrently.
     *
     * @param tile  the tile to render
     * @param lod   level of detail (0–3)
     * @param cache optional disk cache (may be null)
     * @return the rendered image, or null on failure
     */
    public BufferedImage renderTile(DiagramTile tile, int lod, DiagramTileCache cache) {
        // Check disk cache first
        if (cache != null) {
            BufferedImage cached = cache.get(diagramId, tile, lod);
            if (cached != null) {
                return cached;
            }
        }

        int maxPx = lod >= 0 && lod < LOD_MAX_PX.length ? LOD_MAX_PX[lod] : LOD_MAX_PX[2];

        try {
            // Build a clipped SVG with a modified viewBox AND matching
            // width/height so Batik preserves the correct aspect ratio.
            String clippedSvg = clipSvgToViewBox(fullSvg,
                    tile.getSvgX(), tile.getSvgY(), tile.getSvgW(), tile.getSvgH());

            // Use the full LOD pixel budget.  The old formula capped at
            // svgW*2 which produced far too few pixels for wide tiles.
            float targetWidth = (float) maxPx;
            targetWidth = Math.max(targetWidth, 64);

            BufferedImage img = SvgRenderer.renderToBufferedImageForced(
                    clippedSvg.getBytes(UTF8), targetWidth);

            // Store in disk cache
            if (img != null && cache != null) {
                cache.put(diagramId, tile, lod, img);
            }
            return img;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[TiledRenderer] Failed to render tile " + tile, e);
            return null;
        }
    }

    /**
     * Determine the LOD level for the current zoom.
     * Higher zoom → higher LOD (more detail).
     */
    public static int lodForZoom(double zoom) {
        if (zoom < 0.15) return 0;
        if (zoom < 0.5)  return 1;
        if (zoom < 2.0)  return 2;
        return 3;
    }

    /**
     * Determine which tiles are visible in the current viewport.
     *
     * @param vpSvgX  viewport left edge in SVG space
     * @param vpSvgY  viewport top edge in SVG space
     * @param vpSvgW  viewport width in SVG space
     * @param vpSvgH  viewport height in SVG space
     * @return list of visible tiles (may be empty for fully panned-away views)
     */
    public List<DiagramTile> getVisibleTiles(double vpSvgX, double vpSvgY,
                                              double vpSvgW, double vpSvgH) {
        List<DiagramTile> visible = new ArrayList<DiagramTile>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                DiagramTile t = grid[r][c];
                // AABB overlap test
                if (t.getSvgX() < vpSvgX + vpSvgW &&
                    t.getSvgX() + t.getSvgW() > vpSvgX &&
                    t.getSvgY() < vpSvgY + vpSvgH &&
                    t.getSvgY() + t.getSvgH() > vpSvgY) {
                    visible.add(t);
                }
            }
        }
        return visible;
    }

    // ── SVG manipulation ───────────────────────────────────────

    private static final Pattern VIEWBOX_PATTERN =
            Pattern.compile("(viewBox\\s*=\\s*\")([^\"]+)(\")");

    /** Matches width="…" on the root &lt;svg&gt; element. */
    private static final Pattern WIDTH_ATTR =
            Pattern.compile("(<svg[^>]*?)\\s+width\\s*=\\s*\"[^\"]*\"");

    /** Matches height="…" on the root &lt;svg&gt; element. */
    private static final Pattern HEIGHT_ATTR =
            Pattern.compile("(<svg[^>]*?)\\s+height\\s*=\\s*\"[^\"]*\"");

    /**
     * Produce a copy of the SVG with a different viewBox <b>and matching
     * {@code width}/{@code height} attributes</b>, effectively "cropping"
     * the rendering to the given region.
     * <p>
     * <b>Why width/height must be updated:</b> Batik's transcoder computes
     * the output image aspect ratio from the SVG's {@code width}/{@code height}
     * attributes, not from the {@code viewBox}. If only the viewBox is changed,
     * the tile region is mapped into a viewport with the wrong aspect ratio,
     * causing {@code preserveAspectRatio="xMidYMid meet"} to centre the
     * content with large empty margins — every tile then looks the same.
     */
    public static String clipSvgToViewBox(String svg, double x, double y, double w, double h) {
        String newVB = String.format(java.util.Locale.US, "%.2f %.2f %.2f %.2f", x, y, w, h);

        // 1) Replace viewBox
        Matcher m = VIEWBOX_PATTERN.matcher(svg);
        if (m.find()) {
            svg = m.replaceFirst("$1" + Matcher.quoteReplacement(newVB) + "$3");
        } else {
            svg = svg.replaceFirst("<svg", "<svg viewBox=\"" + newVB + "\"");
        }

        // 2) Replace width/height so viewport matches the new viewBox.
        //    Use the viewBox dimensions directly — Batik's KEY_WIDTH will
        //    scale the output to the desired pixel size anyway; what matters
        //    here is the correct aspect ratio.
        String wStr = String.format(java.util.Locale.US, "%.0f", w);
        String hStr = String.format(java.util.Locale.US, "%.0f", h);

        Matcher wm = WIDTH_ATTR.matcher(svg);
        if (wm.find()) {
            svg = wm.replaceFirst("$1 width=\"" + wStr + "\"");
        }

        Matcher hm = HEIGHT_ATTR.matcher(svg);
        if (hm.find()) {
            svg = hm.replaceFirst("$1 height=\"" + hStr + "\"");
        }

        return svg;
    }

    /** Parse viewBox="x y w h" from SVG markup. */
    private static double[] parseViewBox(String svg) {
        Matcher m = VIEWBOX_PATTERN.matcher(svg);
        if (m.find()) {
            String[] parts = m.group(2).trim().split("[\\s,]+");
            if (parts.length >= 4) {
                return new double[]{
                        Double.parseDouble(parts[0]),
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3])
                };
            }
        }
        // Fallback: try width/height attributes
        double w = parseAttr(svg, "width", 800);
        double h = parseAttr(svg, "height", 600);
        return new double[]{0, 0, w, h};
    }

    private static double parseAttr(String svg, String attr, double fallback) {
        Pattern p = Pattern.compile(attr + "\\s*=\\s*\"([\\d.]+)");
        Matcher m = p.matcher(svg);
        if (m.find()) {
            try { return Double.parseDouble(m.group(1)); }
            catch (NumberFormatException e) { /* fallback */ }
        }
        return fallback;
    }
}

