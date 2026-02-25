package de.bund.zrb.archive.model;

/**
 * Taxonomie für Resource-Typen im Data Lake.
 * Bestimmt, ob eine Resource potentiell indexierbar ist.
 */
public enum ResourceKind {

    PAGE_HTML(true),
    PAGE_TEXT(true),
    API_JSON(false),       // nur indexable wenn Content-Heuristik greift
    API_XML(false),
    FEED_RSS(true),
    FEED_ATOM(true),
    MEDIA_VIDEO(false),
    MEDIA_AUDIO(false),
    ASSET_CSS(false),
    ASSET_JS(false),
    IMAGE(false),
    TRACKING(false),
    DOM_SNAPSHOT(true),
    OTHER(false);

    private final boolean defaultIndexable;

    ResourceKind(boolean defaultIndexable) {
        this.defaultIndexable = defaultIndexable;
    }

    /** True if resources of this kind are indexable by default. */
    public boolean isDefaultIndexable() {
        return defaultIndexable;
    }

    /**
     * Determine ResourceKind from MIME type and URL.
     */
    public static ResourceKind fromMimeAndUrl(String mimeType, String url) {
        if (mimeType == null) mimeType = "";
        String mime = mimeType.toLowerCase().split(";")[0].trim();

        // Tracking/Ads by URL pattern
        if (url != null) {
            String lowerUrl = url.toLowerCase();
            if (lowerUrl.contains("/ads/") || lowerUrl.contains("/analytics/")
                    || lowerUrl.contains("/tracking/") || lowerUrl.contains("/pixel")
                    || lowerUrl.contains("/beacon") || lowerUrl.contains("doubleclick")
                    || lowerUrl.contains("googlesyndication") || lowerUrl.contains("googleadservices")
                    || lowerUrl.contains("google-analytics") || lowerUrl.contains("googletagmanager")) {
                return TRACKING;
            }
        }

        // MIME-based classification
        if (mime.equals("text/html") || mime.equals("application/xhtml+xml")) return PAGE_HTML;
        if (mime.equals("text/plain") || mime.equals("text/csv")) return PAGE_TEXT;
        if (mime.equals("application/json") || mime.equals("application/ld+json")
                || mime.equals("application/feed+json")) return API_JSON;
        if (mime.equals("application/xml") || mime.equals("text/xml")) return API_XML;
        if (mime.equals("application/rss+xml")) return FEED_RSS;
        if (mime.equals("application/atom+xml")) return FEED_ATOM;

        if (mime.startsWith("video/")) return MEDIA_VIDEO;
        if (mime.startsWith("audio/")) return MEDIA_AUDIO;
        if (mime.startsWith("image/")) return IMAGE;
        if (mime.equals("text/css")) return ASSET_CSS;
        if (mime.equals("application/javascript") || mime.equals("text/javascript")) return ASSET_JS;

        return OTHER;
    }

    /**
     * Get file extension for this kind.
     */
    public String getDefaultExtension() {
        switch (this) {
            case PAGE_HTML: return "html";
            case PAGE_TEXT: return "txt";
            case API_JSON: return "json";
            case API_XML: return "xml";
            case FEED_RSS: return "xml";
            case FEED_ATOM: return "xml";
            case ASSET_CSS: return "css";
            case ASSET_JS: return "js";
            case IMAGE: return "bin";
            case DOM_SNAPSHOT: return "html";
            default: return "bin";
        }
    }
}
