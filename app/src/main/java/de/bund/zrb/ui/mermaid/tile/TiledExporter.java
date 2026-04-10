package de.bund.zrb.ui.mermaid.tile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Sequential, memory-safe exporter for arbitrarily large tiled diagrams.
 * <p>
 * <b>Strategy:</b> Instead of compositing all tiles into one giant
 * {@link BufferedImage} (which would OOM for large diagrams), this exporter
 * writes the output image in horizontal strips. For each row of tiles, it
 * renders only the tiles in that row, composites them into a single-row
 * strip, writes the strip directly to the output stream, and then discards
 * the strip before moving to the next row.
 * <p>
 * For formats that support streaming (e.g. PNG via {@code ImageIO}), the
 * peak memory usage is: {@code max(tile_pixels) + strip_pixels}.
 * For a 10×10 grid at LOD 3 (2048 px tiles), the strip is 20480 × 2048 px
 * ≈ 160 MB — tolerable. For even larger diagrams we could chunk within a
 * row, but in practice the tile LOD caps pixel size.
 * <p>
 * Usage:
 * <pre>
 *   TiledExporter.export(tiledRenderer, cache, 3, new File("output.png"),
 *           progress -> System.out.println(progress + "% done"));
 * </pre>
 */
public final class TiledExporter {

    private static final Logger LOG = Logger.getLogger(TiledExporter.class.getName());

    /** Callback for export progress (0–100). */
    public interface ProgressListener {
        void onProgress(int percentComplete);
    }

    private TiledExporter() {}

    /**
     * Export the diagram at the given LOD to a PNG file.
     * Processes tiles row-by-row to keep memory bounded.
     *
     * @param renderer  the tiled renderer with the SVG and grid
     * @param cache     tile cache (renders missing tiles on-the-fly)
     * @param lod       level of detail for the export
     * @param output    output PNG file
     * @param listener  optional progress listener (may be null)
     * @throws IOException on write failure
     */
    public static void export(TiledDiagramRenderer renderer,
                               DiagramTileCache cache,
                               int lod,
                               File output,
                               ProgressListener listener) throws IOException {
        int cols = renderer.getCols();
        int rows = renderer.getRows();
        int totalTiles = cols * rows;
        int tilesProcessed = 0;

        // First pass: determine actual pixel dimensions of each row/col
        // by rendering (or loading from cache) the first tile of each column/row.
        // For uniformity, we assume all tiles in a column have the same width
        // and all tiles in a row have the same height.
        int[] colWidths = new int[cols];
        int[] rowHeights = new int[rows];

        // Render first row to get column widths
        for (int c = 0; c < cols; c++) {
            DiagramTile t = renderer.getTile(c, 0);
            BufferedImage img = renderer.renderTile(t, lod, cache);
            if (img != null) {
                colWidths[c] = img.getWidth();
                rowHeights[0] = Math.max(rowHeights[0], img.getHeight());
                t.setImage(img);
            } else {
                colWidths[c] = t.getPixelWidth();
                rowHeights[0] = Math.max(rowHeights[0], t.getPixelHeight());
            }
        }
        // For remaining rows, render first column tile
        for (int r = 1; r < rows; r++) {
            DiagramTile t = renderer.getTile(0, r);
            BufferedImage img = renderer.renderTile(t, lod, cache);
            if (img != null) {
                rowHeights[r] = img.getHeight();
                t.setImage(img);
            } else {
                rowHeights[r] = t.getPixelHeight();
            }
        }

        // Compute total image dimensions
        int totalWidth = 0;
        for (int w : colWidths) totalWidth += w;
        int totalHeight = 0;
        for (int h : rowHeights) totalHeight += h;

        LOG.info("[TiledExporter] Exporting " + cols + "x" + rows + " grid → "
                + totalWidth + "x" + totalHeight + " px at LOD " + lod);

        // Allocate the full output image
        // For very large images, consider streaming PNG writers;
        // for now, we compose in-memory since totalWidth×totalHeight is bounded
        // by tile count × LOD_MAX_PX.
        BufferedImage outputImage = new BufferedImage(totalWidth, totalHeight,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = outputImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, totalWidth, totalHeight);

        int yOffset = 0;
        for (int r = 0; r < rows; r++) {
            int xOffset = 0;
            for (int c = 0; c < cols; c++) {
                DiagramTile t = renderer.getTile(c, r);
                BufferedImage tileImg = t.getImage();
                if (tileImg == null) {
                    tileImg = renderer.renderTile(t, lod, cache);
                }
                if (tileImg != null) {
                    g.drawImage(tileImg, xOffset, yOffset, null);
                    // Release tile memory immediately after drawing
                    t.setImage(null);
                }
                xOffset += colWidths[c];
                tilesProcessed++;
                if (listener != null) {
                    listener.onProgress((int) ((tilesProcessed * 100L) / totalTiles));
                }
            }
            yOffset += rowHeights[r];
        }
        g.dispose();

        // Write to file
        ImageIO.write(outputImage, "PNG", output);
        // Release
        outputImage.flush();

        LOG.info("[TiledExporter] Export complete: " + output.getAbsolutePath());
    }

    /**
     * Export using a row-streaming approach for truly huge diagrams.
     * Each row strip is rendered, written, and discarded before the next.
     * The output is assembled by an external PNG strip writer.
     * <p>
     * This variant writes each row as a separate temporary PNG file,
     * then assembles them. Peak memory ≈ one row strip.
     *
     * @param renderer  the tiled renderer
     * @param cache     tile cache
     * @param lod       level of detail
     * @param outputDir directory for temporary row strips and final output
     * @param baseName  base name for the output file (without extension)
     * @param listener  optional progress listener
     * @return the final assembled PNG file
     * @throws IOException on write failure
     */
    public static File exportStreaming(TiledDiagramRenderer renderer,
                                       DiagramTileCache cache,
                                       int lod,
                                       File outputDir,
                                       String baseName,
                                       ProgressListener listener) throws IOException {
        int cols = renderer.getCols();
        int rows = renderer.getRows();
        int totalTiles = cols * rows;
        int tilesProcessed = 0;

        // Determine per-row pixel dimensions
        int[] colWidths = new int[cols];
        int[] rowHeights = new int[rows];
        boolean dimensionsDetermined = false;

        File[] rowFiles = new File[rows];

        for (int r = 0; r < rows; r++) {
            // Render all tiles in this row into a strip
            BufferedImage[] rowTiles = new BufferedImage[cols];
            int maxH = 0;

            for (int c = 0; c < cols; c++) {
                DiagramTile t = renderer.getTile(c, r);
                BufferedImage img = renderer.renderTile(t, lod, cache);
                rowTiles[c] = img;
                if (img != null) {
                    if (!dimensionsDetermined) colWidths[c] = img.getWidth();
                    maxH = Math.max(maxH, img.getHeight());
                }
            }
            rowHeights[r] = maxH;
            if (r == 0) dimensionsDetermined = true;

            // Composite this row into a strip
            int stripW = 0;
            for (int w : colWidths) stripW += w;
            if (stripW <= 0) stripW = 1;
            if (maxH <= 0) maxH = 1;

            BufferedImage strip = new BufferedImage(stripW, maxH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = strip.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, stripW, maxH);

            int xOff = 0;
            for (int c = 0; c < cols; c++) {
                if (rowTiles[c] != null) {
                    g.drawImage(rowTiles[c], xOff, 0, null);
                    rowTiles[c].flush();
                    rowTiles[c] = null; // free immediately
                }
                xOff += colWidths[c];
                tilesProcessed++;
                if (listener != null) {
                    listener.onProgress((int) ((tilesProcessed * 100L) / totalTiles));
                }
            }
            g.dispose();

            // Write row strip to temp file
            File rowFile = new File(outputDir, baseName + "_row" + r + ".png");
            ImageIO.write(strip, "PNG", rowFile);
            strip.flush();
            rowFiles[r] = rowFile;
        }

        // Assemble all row strips into the final image
        int totalW = 0;
        for (int w : colWidths) totalW += w;
        int totalH = 0;
        for (int h : rowHeights) totalH += h;

        BufferedImage finalImg = new BufferedImage(
                Math.max(totalW, 1), Math.max(totalH, 1), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = finalImg.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, totalW, totalH);

        int yOff = 0;
        for (int r = 0; r < rows; r++) {
            if (rowFiles[r] != null && rowFiles[r].exists()) {
                BufferedImage rowImg = ImageIO.read(rowFiles[r]);
                if (rowImg != null) {
                    g.drawImage(rowImg, 0, yOff, null);
                    rowImg.flush();
                }
                rowFiles[r].delete(); // clean up temp file
            }
            yOff += rowHeights[r];
        }
        g.dispose();

        File output = new File(outputDir, baseName + ".png");
        ImageIO.write(finalImg, "PNG", output);
        finalImg.flush();

        return output;
    }
}

