package de.bund.zrb.ui.mermaid.tile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Sequential, memory-safe exporter for arbitrarily large tiled diagrams.
 * <p>
 * Supports PNG and SVG export, viewport-only export, parallel tile
 * rendering, and SVG splitting for large diagrams that exceed browser
 * zoom capabilities.
 */
public final class TiledExporter {

    private static final Logger LOG = Logger.getLogger(TiledExporter.class.getName());
    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** Callback for export progress (0–100) with step description. */
    public interface ProgressListener {
        void onProgress(int percentComplete);

        /** Optional: report the current step description. Default no-op. */
        default void onStep(String stepDescription) {}
    }

    private TiledExporter() {}

    /**
     * Export the diagram at the given LOD to a PNG file.
     * Processes tiles row-by-row. Uses parallel rendering for speed.
     */
    public static void export(TiledDiagramRenderer renderer,
                               DiagramTileCache cache,
                               int lod,
                               File output,
                               ProgressListener listener) throws IOException {
        int cols = renderer.getCols();
        int rows = renderer.getRows();
        int totalTiles = cols * rows;

        if (listener != null) listener.onStep("Tile-Dimensionen werden berechnet\u2026");

        // First pass: determine actual pixel dimensions
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

        int totalWidth = 0;
        for (int w : colWidths) totalWidth += w;
        int totalHeight = 0;
        for (int h : rowHeights) totalHeight += h;

        LOG.info("[TiledExporter] Exporting " + cols + "x" + rows + " grid \u2192 "
                + totalWidth + "x" + totalHeight + " px at LOD " + lod);

        if (listener != null) listener.onStep("Bild wird zusammengesetzt (" + totalWidth + "\u00D7" + totalHeight + " px)\u2026");

        BufferedImage outputImage = new BufferedImage(totalWidth, totalHeight,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = outputImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, totalWidth, totalHeight);

        // Parallel rendering of all tiles
        int threads = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        final AtomicInteger tilesProcessed = new AtomicInteger(0);

        // Build list of all tile tasks
        List<Future<TileResult>> futures = new ArrayList<Future<TileResult>>();
        for (int r = 0; r < rows; r++) {
            int yOff = 0;
            for (int rr = 0; rr < r; rr++) yOff += rowHeights[rr];
            for (int c = 0; c < cols; c++) {
                int xOff = 0;
                for (int cc = 0; cc < c; cc++) xOff += colWidths[cc];
                final int fr = r, fc = c, fxOff = xOff, fyOff = yOff;
                futures.add(pool.submit(new Callable<TileResult>() {
                    @Override
                    public TileResult call() {
                        DiagramTile t = renderer.getTile(fc, fr);
                        BufferedImage tileImg = t.getImage();
                        if (tileImg == null) {
                            tileImg = renderer.renderTile(t, lod, cache);
                        }
                        int done = tilesProcessed.incrementAndGet();
                        if (listener != null) {
                            listener.onStep("Kachel " + done + "/" + totalTiles + " gerendert\u2026");
                            listener.onProgress((int) ((done * 80L) / totalTiles));
                        }
                        return new TileResult(fxOff, fyOff, tileImg);
                    }
                }));
            }
        }

        // Collect results and compose
        if (listener != null) listener.onStep("Kacheln werden zusammengesetzt\u2026");
        for (Future<TileResult> f : futures) {
            try {
                TileResult tr = f.get();
                if (tr.img != null) {
                    g.drawImage(tr.img, tr.x, tr.y, null);
                }
            } catch (Exception ignored) {}
        }
        pool.shutdown();
        g.dispose();

        // Release tile images
        for (DiagramTile t : renderer.getAllTiles()) t.setImage(null);

        if (listener != null) listener.onStep("PNG wird geschrieben\u2026");
        if (listener != null) listener.onProgress(90);
        ImageIO.write(outputImage, "PNG", output);
        outputImage.flush();

        if (listener != null) listener.onProgress(100);
        if (listener != null) listener.onStep("Export abgeschlossen.");
        LOG.info("[TiledExporter] Export complete: " + output.getAbsolutePath());
    }

    /** Helper class for parallel tile rendering results. */
    private static final class TileResult {
        final int x, y;
        final BufferedImage img;
        TileResult(int x, int y, BufferedImage img) {
            this.x = x; this.y = y; this.img = img;
        }
    }

    /**
     * Export only the viewport-visible portion of the diagram to PNG.
     *
     * @param renderer   the tiled renderer
     * @param cache      tile cache
     * @param lod        level of detail
     * @param vpSvgX     viewport left edge in SVG space
     * @param vpSvgY     viewport top edge in SVG space
     * @param vpSvgW     viewport width in SVG space
     * @param vpSvgH     viewport height in SVG space
     * @param output     output PNG file
     * @param listener   optional progress listener
     */
    public static void exportViewport(TiledDiagramRenderer renderer,
                                       DiagramTileCache cache, int lod,
                                       double vpSvgX, double vpSvgY,
                                       double vpSvgW, double vpSvgH,
                                       File output,
                                       ProgressListener listener) throws IOException {
        if (listener != null) listener.onStep("Sichtbaren Ausschnitt exportieren\u2026");

        List<DiagramTile> visible = renderer.getVisibleTiles(vpSvgX, vpSvgY, vpSvgW, vpSvgH);
        if (visible.isEmpty()) {
            throw new IOException("Keine Kacheln im sichtbaren Bereich.");
        }

        // Determine bounding box in SVG space of visible tiles
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        for (DiagramTile t : visible) {
            minX = Math.min(minX, t.getSvgX());
            minY = Math.min(minY, t.getSvgY());
            maxX = Math.max(maxX, t.getSvgX() + t.getSvgW());
            maxY = Math.max(maxY, t.getSvgY() + t.getSvgH());
        }

        // Clip to actual viewport bounds
        minX = Math.max(minX, vpSvgX);
        minY = Math.max(minY, vpSvgY);
        maxX = Math.min(maxX, vpSvgX + vpSvgW);
        maxY = Math.min(maxY, vpSvgY + vpSvgH);

        int maxPx = lod >= 0 && lod < 4 ? new int[]{256, 512, 1024, 2048}[lod] : 1024;
        double scale = maxPx / Math.max(maxX - minX, 1);
        int outW = (int) Math.round((maxX - minX) * scale);
        int outH = (int) Math.round((maxY - minY) * scale);
        outW = Math.max(outW, 1);
        outH = Math.max(outH, 1);

        // Render a single clipped SVG for the viewport region
        if (listener != null) listener.onStep("Ausschnitt wird gerendert (" + outW + "\u00D7" + outH + " px)\u2026");
        try {
            String clippedSvg = TiledDiagramRenderer.clipSvgToViewBox(
                    renderer.getFullSvg(), minX, minY, maxX - minX, maxY - minY);
            BufferedImage img = de.bund.zrb.wiki.ui.SvgRenderer.renderToBufferedImageForced(
                    clippedSvg.getBytes(UTF8), (float) outW);
            if (img != null) {
                ImageIO.write(img, "PNG", output);
                img.flush();
            }
        } catch (Exception e) {
            throw new IOException("Viewport-Export fehlgeschlagen: " + e.getMessage(), e);
        }
        if (listener != null) listener.onProgress(100);
        if (listener != null) listener.onStep("Export abgeschlossen.");
    }

    /**
     * Export the full SVG to a single SVG file.
     */
    public static void exportSvg(String svgContent, File output,
                                  ProgressListener listener) throws IOException {
        if (listener != null) listener.onStep("SVG wird geschrieben\u2026");
        Files.write(output.toPath(), svgContent.getBytes(UTF8));
        if (listener != null) listener.onProgress(100);
        if (listener != null) listener.onStep("Export abgeschlossen.");
    }

    /**
     * Export the SVG viewport (crop) to a single SVG file.
     */
    public static void exportSvgViewport(String fullSvg,
                                          double vpSvgX, double vpSvgY,
                                          double vpSvgW, double vpSvgH,
                                          File output,
                                          ProgressListener listener) throws IOException {
        if (listener != null) listener.onStep("SVG-Ausschnitt wird erzeugt\u2026");
        String clipped = TiledDiagramRenderer.clipSvgToViewBox(fullSvg, vpSvgX, vpSvgY, vpSvgW, vpSvgH);
        Files.write(output.toPath(), clipped.getBytes(UTF8));
        if (listener != null) listener.onProgress(100);
        if (listener != null) listener.onStep("Export abgeschlossen.");
    }

    /**
     * Split a large SVG into multiple SVG files, each covering a portion of the
     * diagram. This allows viewers with limited zoom (e.g. browsers at 500%)
     * to still see details of large diagrams.
     * <p>
     * The split count is computed from the viewBox dimensions so that each
     * piece is small enough for comfortable 500% browser zoom.
     *
     * @param svgContent  the full SVG content
     * @param outputDir   directory for the split files
     * @param baseName    base name for files (e.g. "diagram" → "diagram_1.svg")
     * @param listener    optional progress listener
     * @return list of created SVG files
     */
    public static List<File> exportSvgSplit(String svgContent, File outputDir,
                                             String baseName,
                                             ProgressListener listener) throws IOException {
        if (listener != null) listener.onStep("SVG wird aufgeteilt\u2026");

        // Parse viewBox
        double[] vb = parseViewBox(svgContent);
        double vbX = vb[0], vbY = vb[1], vbW = vb[2], vbH = vb[3];

        // Determine split grid: each piece should cover max ~800 SVG units
        // so that at 500% browser zoom, detail is visible
        double maxChunkSize = 800.0;
        int splitCols = Math.max(1, (int) Math.ceil(vbW / maxChunkSize));
        int splitRows = Math.max(1, (int) Math.ceil(vbH / maxChunkSize));

        // If diagram is small enough, no splitting needed
        if (splitCols <= 1 && splitRows <= 1) {
            List<File> result = new ArrayList<File>();
            File single = new File(outputDir, baseName + ".svg");
            Files.write(single.toPath(), svgContent.getBytes(UTF8));
            result.add(single);
            if (listener != null) {
                listener.onProgress(100);
                listener.onStep("SVG exportiert (keine Aufteilung n\u00F6tig).");
            }
            return result;
        }

        int totalParts = splitCols * splitRows;
        double chunkW = vbW / splitCols;
        double chunkH = vbH / splitRows;

        if (!outputDir.exists()) outputDir.mkdirs();

        List<File> result = new ArrayList<File>();
        int partNum = 0;

        // Also create an HTML index page for easy navigation
        StringBuilder htmlIndex = new StringBuilder();
        htmlIndex.append("<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\">\n");
        htmlIndex.append("<title>").append(baseName).append(" \u2014 Split SVG</title>\n");
        htmlIndex.append("<style>body{font-family:sans-serif;margin:20px;}");
        htmlIndex.append(".grid{display:grid;grid-template-columns:repeat(")
                .append(splitCols).append(",1fr);gap:4px;}");
        htmlIndex.append(".cell{border:1px solid #ccc;text-align:center;padding:4px;}");
        htmlIndex.append(".cell img{max-width:100%;height:auto;}");
        htmlIndex.append(".cell a{text-decoration:none;color:#333;}");
        htmlIndex.append("</style></head><body>\n");
        htmlIndex.append("<h1>").append(baseName).append("</h1>\n");
        htmlIndex.append("<p>Aufgeteilt in ").append(splitCols).append(" \u00D7 ")
                .append(splitRows).append(" = ").append(totalParts).append(" Teile</p>\n");
        htmlIndex.append("<div class=\"grid\">\n");

        for (int r = 0; r < splitRows; r++) {
            for (int c = 0; c < splitCols; c++) {
                partNum++;
                double cx = vbX + c * chunkW;
                double cy = vbY + r * chunkH;

                String clipped = TiledDiagramRenderer.clipSvgToViewBox(
                        svgContent, cx, cy, chunkW, chunkH);

                String fileName = baseName + "_" + partNum + ".svg";
                File partFile = new File(outputDir, fileName);
                Files.write(partFile.toPath(), clipped.getBytes(UTF8));
                result.add(partFile);

                htmlIndex.append("<div class=\"cell\"><a href=\"").append(fileName).append("\">")
                        .append("<img src=\"").append(fileName).append("\" loading=\"lazy\"><br>")
                        .append("Teil ").append(partNum).append(" (Zeile ").append(r + 1)
                        .append(", Spalte ").append(c + 1).append(")")
                        .append("</a></div>\n");

                if (listener != null) {
                    listener.onStep("SVG-Teil " + partNum + "/" + totalParts + " geschrieben\u2026");
                    listener.onProgress((int) ((partNum * 100L) / totalParts));
                }
            }
        }

        htmlIndex.append("</div>\n</body></html>");
        File indexFile = new File(outputDir, baseName + "_index.html");
        Files.write(indexFile.toPath(), htmlIndex.toString().getBytes(UTF8));
        result.add(0, indexFile); // index at front

        if (listener != null) listener.onStep(totalParts + " SVG-Teile + Index exportiert.");
        LOG.info("[TiledExporter] SVG split export: " + totalParts + " parts to " + outputDir);
        return result;
    }

    /** Parse viewBox="x y w h" from SVG markup. */
    private static double[] parseViewBox(String svg) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("viewBox\\s*=\\s*\"([^\"]+)\"").matcher(svg);
        if (m.find()) {
            String[] parts = m.group(1).trim().split("[\\s,]+");
            if (parts.length >= 4) {
                return new double[]{
                        Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]), Double.parseDouble(parts[3])
                };
            }
        }
        return new double[]{0, 0, 800, 600};
    }

    /**
     * Export using a row-streaming approach for truly huge diagrams.
     * Each row strip is rendered, written, and discarded before the next.
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

        int[] colWidths = new int[cols];
        int[] rowHeights = new int[rows];
        boolean dimensionsDetermined = false;

        File[] rowFiles = new File[rows];

        for (int r = 0; r < rows; r++) {
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
                    rowTiles[c] = null;
                }
                xOff += colWidths[c];
                tilesProcessed++;
                if (listener != null) {
                    listener.onProgress((int) ((tilesProcessed * 100L) / totalTiles));
                }
            }
            g.dispose();

            File rowFile = new File(outputDir, baseName + "_row" + r + ".png");
            ImageIO.write(strip, "PNG", rowFile);
            strip.flush();
            rowFiles[r] = rowFile;
        }

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
                rowFiles[r].delete();
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

