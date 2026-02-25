package de.bund.zrb.mcpserver.research;

/**
 * Network resource categories, mirroring browser DevTools classification.
 */
public enum ResourceCategory {
    HTML,
    JS,
    CSS,
    XHR,
    FONT,
    IMAGE,
    MEDIA,
    WEBSOCKET,
    OTHER;

    /**
     * Classify a network response by MIME type and initiator type.
     *
     * @param mimeType    Content-Type header value (may include charset)
     * @param initiator   request initiator ("xmlhttprequest", "fetch", "script", "parser", etc.)
     * @return category
     */
    public static ResourceCategory classify(String mimeType, String initiator) {
        String mime = mimeType != null ? mimeType.toLowerCase().split(";")[0].trim() : "";
        String init = initiator != null ? initiator.toLowerCase() : "";

        // XHR / fetch first (initiator takes priority over mime)
        if ("xmlhttprequest".equals(init) || "fetch".equals(init)) {
            return XHR;
        }

        // By MIME type
        if (mime.contains("text/html") || mime.contains("application/xhtml")) return HTML;
        if (mime.contains("javascript") || mime.contains("ecmascript")) return JS;
        if (mime.contains("text/css")) return CSS;
        if (mime.contains("application/json") || mime.contains("application/xml")
                || mime.contains("text/xml") || mime.contains("application/rss")
                || mime.contains("application/atom")) return XHR;
        if (mime.startsWith("font/") || mime.contains("woff") || mime.contains("opentype")
                || mime.contains("truetype")) return FONT;
        if (mime.startsWith("image/") || mime.contains("svg")) return IMAGE;
        if (mime.startsWith("video/") || mime.startsWith("audio/")
                || mime.contains("mpegurl") || mime.contains("dash+xml")) return MEDIA;

        return OTHER;
    }
}
