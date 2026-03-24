package de.bund.zrb.wiki.ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic async image loader for JEditorPane HTML content.
 * <p>
 * Scans the HTML for {@code <img src="...">} tags, downloads images via HTTP,
 * and injects them into the {@link HTMLDocument}'s image cache.
 * After loading, the HTML is re-set on the pane to force fresh {@code ImageView}s.
 * <p>
 * This is the generalized version of the image loading pattern used in
 * {@code ConfluenceConnectionTab} and {@code ConfluenceReaderTab},
 * adapted for plain HTTP URLs (no REST client needed).
 */
public final class WikiAsyncImageLoader {

    private static final Logger LOG = Logger.getLogger(WikiAsyncImageLoader.class.getName());

    private static final String USER_AGENT =
            "MainframeMate/1.0 (https://github.com/Miguel0888/MainframeMate; Java)";

    /** Optional proxy resolver: given a URL string, returns the Proxy to use. */
    private static volatile java.util.function.Function<String, Proxy> proxyResolver;

    /** Pattern to find img src attributes in HTML. */
    private static final Pattern IMG_SRC_PATTERN = Pattern.compile(
            "<img\\s[^>]*src\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    private WikiAsyncImageLoader() { /* utility class */ }

    /**
     * Set a global proxy resolver for image downloads.
     * Called once from the app module to wire in PAC-script / manual proxy settings.
     *
     * @param resolver maps a URL string to a {@link Proxy} (may return {@code null} or {@link Proxy#NO_PROXY})
     */
    public static void setProxyResolver(java.util.function.Function<String, Proxy> resolver) {
        proxyResolver = resolver;
    }

    /**
     * Load all images in the given HTML asynchronously and inject them
     * into the JEditorPane's HTMLDocument image cache.
     *
     * @param htmlPane the JEditorPane displaying HTML content
     * @param html     the raw body HTML (used to scan for img src)
     * @param fullHtml the full wrapped HTML (re-set after loading to refresh ImageViews)
     */
    public static void loadImagesAsync(final JEditorPane htmlPane, final String html, final String fullHtml) {
        loadImagesAsync(htmlPane, html, fullHtml, null, null);
    }

    /**
     * Load all images in the given HTML asynchronously and inject them
     * into the JEditorPane's HTMLDocument image cache.
     * <p>
     * When a {@link ByteDownloader} is provided, it is used for relative URLs
     * (e.g. Confluence attachment paths). Absolute URLs are downloaded via plain
     * HTTP with the global proxy resolver. This ensures that both Confluence
     * (authenticated, relative) and Wikipedia (public, absolute) images are
     * loaded correctly through the proxy.
     *
     * @param htmlPane       the JEditorPane displaying HTML content
     * @param html           the raw body HTML (used to scan for img src)
     * @param fullHtml       the full wrapped HTML (re-set after loading to refresh ImageViews)
     * @param baseUrl        optional base URL for resolving relative image paths
     * @param byteDownloader optional authenticated downloader (e.g. for Confluence mTLS)
     */
    public static void loadImagesAsync(final JEditorPane htmlPane, final String html, final String fullHtml,
                                       final String baseUrl, final ByteDownloader byteDownloader) {
        if (html == null || html.isEmpty()) return;

        new SwingWorker<Hashtable<URL, Image>, Object[]>() {
            private final Hashtable<URL, Image> loaded = new Hashtable<URL, Image>();

            @Override
            protected Hashtable<URL, Image> doInBackground() {
                Matcher matcher = IMG_SRC_PATTERN.matcher(html);
                while (matcher.find()) {
                    String src = matcher.group(1);
                    if (src.startsWith("data:")) continue; // skip data URIs

                    try {
                        // Resolve the image URL for the HTMLDocument cache key
                        URL imageUrl;
                        boolean isAbsolute = src.startsWith("http://") || src.startsWith("https://");
                        if (isAbsolute) {
                            imageUrl = new URL(src);
                        } else if (baseUrl != null) {
                            imageUrl = new URL(new URL(baseUrl), src);
                        } else {
                            imageUrl = new URL(src); // will fail for relative URLs without base
                        }

                        // Download bytes — prefer ByteDownloader for relative URLs
                        byte[] data;
                        if (!isAbsolute && byteDownloader != null) {
                            data = byteDownloader.download(src);
                        } else {
                            data = downloadBytes(isAbsolute ? src : imageUrl.toString());
                        }
                        if (data == null || data.length == 0) continue;

                        Image image = decodeImage(data);
                        if (image != null) {
                            loaded.put(imageUrl, image);
                            publish(new Object[]{imageUrl, image});
                        }
                    } catch (Exception e) {
                        LOG.log(Level.FINE, "[WikiImageLoader] Image load failed: " + src, e);
                    }
                }
                return loaded;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void process(List<Object[]> chunks) {
                javax.swing.text.Document doc = htmlPane.getDocument();
                if (!(doc instanceof HTMLDocument)) return;

                Dictionary<URL, Image> cache = (Dictionary<URL, Image>) doc.getProperty("imageCache");
                if (cache == null) {
                    cache = new Hashtable<URL, Image>();
                    doc.putProperty("imageCache", cache);
                }
                for (Object[] pair : chunks) {
                    cache.put((URL) pair[0], (Image) pair[1]);
                }
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void done() {
                try {
                    Hashtable<URL, Image> allImages = get();
                    if (allImages == null || allImages.isEmpty()) return;

                    // 1) Populate cache BEFORE setText so ImageViews find images immediately
                    javax.swing.text.Document doc = htmlPane.getDocument();
                    doc.putProperty("imageCache", allImages);

                    int caretPos = 0;
                    try {
                        caretPos = Math.min(htmlPane.getCaretPosition(), doc.getLength());
                    } catch (Exception ignored) { }

                    // 2) Re-set HTML to force fresh ImageViews
                    htmlPane.setText(fullHtml);

                    // 3) Safety: if setText() replaced the document, re-apply cache
                    javax.swing.text.Document newDoc = htmlPane.getDocument();
                    if (newDoc != doc) {
                        newDoc.putProperty("imageCache", allImages);
                        htmlPane.setText(fullHtml);
                    }

                    try {
                        htmlPane.setCaretPosition(Math.min(caretPos,
                                htmlPane.getDocument().getLength()));
                    } catch (Exception ignored) { }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "[WikiImageLoader] Failed to refresh after image load", e);
                }
            }
        }.execute();
    }

    /**
     * Decode raw image bytes into an {@link Image}.
     * <p>
     * For GIF images this uses {@link ImageIcon} (backed by {@link Toolkit}) which
     * preserves animation frames.  {@code ImageIO.read()} only reads the first frame
     * of an animated GIF and returns a static {@code BufferedImage} — causing animated
     * GIFs to appear as broken/static placeholders.
     * <p>
     * For all other formats (PNG, JPEG, BMP, …) {@code ImageIO.read()} is used.
     */
    private static Image decodeImage(byte[] data) {
        // SVG → rasterise with Apache Batik
        if (SvgRenderer.isSvg(data)) {
            return SvgRenderer.renderToBufferedImage(data);
        }
        if (isGif(data)) {
            // ImageIcon uses Toolkit internally and supports animated GIF frames.
            // Its constructor blocks until the image is fully loaded (MediaTracker).
            ImageIcon icon = new ImageIcon(data);
            return icon.getIconWidth() > 0 ? icon.getImage() : null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(data));
        } catch (Exception e) {
            LOG.log(Level.FINE, "[WikiImageLoader] ImageIO.read failed, trying Toolkit", e);
            // Fallback: Toolkit can handle some formats ImageIO cannot
            ImageIcon icon = new ImageIcon(data);
            return icon.getIconWidth() > 0 ? icon.getImage() : null;
        }
    }

    /**
     * Check whether the raw bytes represent a GIF image (magic bytes "GIF").
     */
    private static boolean isGif(byte[] data) {
        return data != null && data.length > 3
                && data[0] == 'G' && data[1] == 'I' && data[2] == 'F';
    }

    /**
     * Download the image bytes from a URL using plain HTTP.
     * Uses the global proxy resolver if set.
     */
    private static byte[] downloadBytes(String imageUrl) throws java.io.IOException {
        URL url = new URL(imageUrl);
        HttpURLConnection conn;

        java.util.function.Function<String, Proxy> resolver = proxyResolver;
        if (resolver != null) {
            Proxy proxy = resolver.apply(imageUrl);
            if (proxy != null && !Proxy.NO_PROXY.equals(proxy)) {
                LOG.fine("[WikiImageLoader] Using proxy " + proxy + " for " + imageUrl);
                conn = (HttpURLConnection) url.openConnection(proxy);
            } else {
                conn = (HttpURLConnection) url.openConnection();
            }
        } else {
            conn = (HttpURLConnection) url.openConnection();
        }

        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "image/*,*/*;q=0.8");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(30_000);
        conn.setInstanceFollowRedirects(true);

        InputStream in = conn.getInputStream();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(32768);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        } finally {
            in.close();
        }
    }

    /**
     * Wrap body HTML in a full HTML document with styles suitable for rendered mode
     * (images inline, max-width, etc.). Analogous to Confluence's wrapHtml with img support.
     */
    public static String wrapHtmlWithImages(String bodyHtml) {
        return "<html><head><style>"
                + "body { font-family: sans-serif; font-size: 13px; margin: 12px; }"
                + "h1,h2,h3 { color: #333; }"
                + "a { color: #0645ad; }"
                + "pre, code { background: #f4f4f4; padding: 4px; }"
                + "table { border-collapse: collapse; } "
                + "td, th { border: 1px solid #ccc; padding: 4px; }"
                + "img { max-width: 100%; height: auto; }"
                + ".thumb, figure { margin: 8px 0; }"
                + "</style></head><body>"
                + bodyHtml
                + "</body></html>";
    }
}

