package de.bund.zrb.ui.mermaid.tile;

import java.awt.image.BufferedImage;
import java.lang.ref.SoftReference;

/**
 * A single tile of a tiled diagram. Holds the tile coordinates,
 * pixel dimensions, and a soft-referenced raster image that can be
 * evicted by the GC under memory pressure and reloaded from disk.
 */
public final class DiagramTile {

    /** Column index (0-based, left to right). */
    private final int col;
    /** Row index (0-based, top to bottom). */
    private final int row;

    /** SVG-space bounding box of this tile (viewBox coordinates). */
    private final double svgX, svgY, svgW, svgH;

    /** Desired raster width/height in pixels at this LOD. */
    private final int pixelWidth, pixelHeight;

    /** Soft reference — GC may reclaim; reload from cache on next access. */
    private volatile SoftReference<BufferedImage> imageRef;

    /** True while a background worker is rendering this tile. */
    private volatile boolean rendering;

    public DiagramTile(int col, int row,
                       double svgX, double svgY, double svgW, double svgH,
                       int pixelWidth, int pixelHeight) {
        this.col = col;
        this.row = row;
        this.svgX = svgX;
        this.svgY = svgY;
        this.svgW = svgW;
        this.svgH = svgH;
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
    }

    public int getCol() { return col; }
    public int getRow() { return row; }
    public double getSvgX() { return svgX; }
    public double getSvgY() { return svgY; }
    public double getSvgW() { return svgW; }
    public double getSvgH() { return svgH; }
    public int getPixelWidth() { return pixelWidth; }
    public int getPixelHeight() { return pixelHeight; }

    /** @return the cached raster image, or null if evicted / not yet rendered. */
    public BufferedImage getImage() {
        SoftReference<BufferedImage> ref = imageRef;
        return ref != null ? ref.get() : null;
    }

    public void setImage(BufferedImage img) {
        this.imageRef = img != null ? new SoftReference<BufferedImage>(img) : null;
    }

    public boolean isRendering() { return rendering; }
    public void setRendering(boolean rendering) { this.rendering = rendering; }

    /** Unique key for this tile at a given LOD, e.g. "3_2_lod2". */
    public String cacheKey(int lod) {
        return col + "_" + row + "_lod" + lod;
    }

    @Override
    public String toString() {
        return "Tile[" + col + "," + row + " svgXY=(" + (int) svgX + "," + (int) svgY
                + ") " + pixelWidth + "x" + pixelHeight + "px]";
    }
}

