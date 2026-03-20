package de.bund.zrb.wiki.ui;

import java.io.IOException;

/**
 * Pluggable image downloader used by {@link ImageStripPanel} and {@link ImageThumbnailPanel}.
 * <p>
 * The default implementation ({@code null}) uses plain HTTP via {@link java.net.HttpURLConnection}.
 * Confluence integration supplies an authenticated variant that delegates to
 * {@code ConfluenceRestClient.getBytes(path)}.
 */
public interface ByteDownloader {
    /**
     * Download the resource at the given URL/path and return its raw bytes.
     *
     * @param url image URL (may be absolute or relative, depending on the implementation)
     * @return raw image bytes
     * @throws IOException on download failure
     */
    byte[] download(String url) throws IOException;
}

