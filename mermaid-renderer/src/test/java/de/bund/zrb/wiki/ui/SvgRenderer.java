package de.bund.zrb.wiki.ui;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Renders SVG images to {@link BufferedImage} using Apache Batik.
 * <p>
 * Provides detection ({@link #isSvg(byte[])}) and rendering
 * ({@link #renderToBufferedImage(byte[])}) for use in all image-loading
 * pipelines (inline, strip, thumbnail, overlay, Confluence, Wiki).
 * <p>
 * <b>NOTE:</b> This is a duplicate of {@code wiki-integration/.../SvgRenderer.java},
 * kept here so that the visual render tests can live inside the
 * {@code mermaid-renderer} module without depending on {@code wiki-integration}.
 */
public final class SvgRenderer {

    private static final Logger LOG = Logger.getLogger(SvgRenderer.class.getName());

    private SvgRenderer() { /* utility */ }

    // ═══════════════════════════════════════════════════════════
    //  Detection
    // ═══════════════════════════════════════════════════════════

    /**
     * Returns {@code true} if the raw bytes look like an SVG document.
     * Checks for {@code <?xml … <svg} or a direct {@code <svg} start.
     */
    public static boolean isSvg(byte[] data) {
        if (data == null || data.length < 5) return false;
        int limit = Math.min(data.length, 1000);
        String prefix;
        try {
            prefix = new String(data, 0, limit, "UTF-8").trim().toLowerCase();
        } catch (Exception e) {
            return false;
        }
        if (prefix.startsWith("<svg")
                || prefix.startsWith("<?xml")
                || prefix.startsWith("<!doctype svg")) {
            return prefix.contains("<svg");
        }
        return false;
    }

    /**
     * Quick URL-based heuristic: returns {@code true} if the URL path
     * ends with {@code .svg} (ignoring query parameters).
     */
    public static boolean isSvgUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        int q = lower.indexOf('?');
        if (q > 0) lower = lower.substring(0, q);
        return lower.endsWith(".svg");
    }

    // ═══════════════════════════════════════════════════════════
    //  Rendering
    // ═══════════════════════════════════════════════════════════

    /**
     * Render an SVG document to a {@link BufferedImage} at intrinsic size.
     *
     * @param svgData raw SVG bytes (UTF-8 XML)
     * @return rasterised image, or {@code null} on failure
     */
    public static BufferedImage renderToBufferedImage(byte[] svgData) {
        return renderToBufferedImage(svgData, -1f, -1f);
    }

    /**
     * Render an SVG document to a {@link BufferedImage} with optional size hints.
     *
     * @param svgData   raw SVG bytes (UTF-8 XML)
     * @param maxWidth  desired maximum width (ignored if &le; 0)
     * @param maxHeight desired maximum height (ignored if &le; 0)
     * @return rasterised image, or {@code null} on failure
     */
    public static BufferedImage renderToBufferedImage(byte[] svgData, float maxWidth, float maxHeight) {
        if (svgData == null || svgData.length == 0) return null;
        try {
            // Safety net: strip CSS properties that crash Batik's CSSEngine
            svgData = sanitizeForBatik(svgData);
            BufferedImageTranscoder transcoder = new BufferedImageTranscoder();
            if (maxWidth > 0) {
                transcoder.addTranscodingHint(ImageTranscoder.KEY_MAX_WIDTH, maxWidth);
            }
            if (maxHeight > 0) {
                transcoder.addTranscodingHint(ImageTranscoder.KEY_MAX_HEIGHT, maxHeight);
            }
            TranscoderInput input = new TranscoderInput(new ByteArrayInputStream(svgData));
            transcoder.transcode(input, new TranscoderOutput());
            return transcoder.getImage();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[SvgRenderer] Failed to render SVG (" + svgData.length + " bytes): "
                    + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Render an SVG document to a {@link BufferedImage} at an <b>exact</b> pixel width.
     * <p>
     * Unlike {@link #renderToBufferedImage(byte[], float, float)} which uses
     * {@code KEY_MAX_WIDTH} (only shrinks, never enlarges), this method uses
     * {@code KEY_WIDTH} which <b>forces</b> the output to the requested width,
     * scaling the SVG content up or down as needed.  Height is determined
     * automatically from the SVG's aspect ratio.
     *
     * @param svgData raw SVG bytes (UTF-8 XML)
     * @param width   exact output width in pixels
     * @return rasterised image, or {@code null} on failure
     */
    public static BufferedImage renderToBufferedImageForced(byte[] svgData, float width) {
        if (svgData == null || svgData.length == 0) return null;
        try {
            svgData = sanitizeForBatik(svgData);
            BufferedImageTranscoder transcoder = new BufferedImageTranscoder();
            transcoder.addTranscodingHint(ImageTranscoder.KEY_WIDTH, width);
            TranscoderInput input = new TranscoderInput(new ByteArrayInputStream(svgData));
            transcoder.transcode(input, new TranscoderOutput());
            return transcoder.getImage();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[SvgRenderer] Failed to render SVG (forced width=" + width + "): "
                    + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Internal Batik transcoder that captures the BufferedImage
    // ═══════════════════════════════════════════════════════════

    /**
     * Last-resort sanitisation of SVG bytes before handing them to Batik.
     * Strips CSS properties that crash Batik's CSSEngine (e.g.
     * {@code alignment-baseline: central}, {@code dominant-baseline}).
     */
    private static byte[] sanitizeForBatik(byte[] data) {
        try {
            String svg = new String(data, "UTF-8");
            boolean changed = false;

            // Remove alignment-baseline attributes
            if (svg.contains("alignment-baseline")) {
                svg = svg.replaceAll("\\s+alignment-baseline\\s*=\\s*\"[^\"]*\"", "");
                svg = svg.replaceAll("\\s+alignment-baseline\\s*=\\s*'[^']*'", "");
                svg = svg.replaceAll("alignment-baseline\\s*:\\s*[^;\"'}<]+[;]?", "");
                changed = true;
            }

            // Remove dominant-baseline attributes and CSS
            if (svg.contains("dominant-baseline")) {
                svg = svg.replaceAll("\\s+dominant-baseline\\s*=\\s*\"[^\"]*\"", "");
                svg = svg.replaceAll("\\s+dominant-baseline\\s*=\\s*'[^']*'", "");
                svg = svg.replaceAll("dominant-baseline\\s*:\\s*[^;\"'}<]+[;]?", "");
                changed = true;
            }

            // Remove empty style attributes left behind
            if (changed) {
                svg = svg.replaceAll("\\s+style\\s*=\\s*\"[;\\s]*\"", "");
                svg = svg.replaceAll("\\s+style\\s*=\\s*'[;\\s]*'", "");
                return svg.getBytes("UTF-8");
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "[SvgRenderer] sanitizeForBatik: " + e.getMessage());
        }
        return data;
    }

    private static final class BufferedImageTranscoder extends ImageTranscoder {
        private BufferedImage image;

        @Override
        public BufferedImage createImage(int w, int h) {
            return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }

        @Override
        public void writeImage(BufferedImage img, TranscoderOutput out) {
            this.image = img;
        }

        BufferedImage getImage() {
            return image;
        }
    }
}

