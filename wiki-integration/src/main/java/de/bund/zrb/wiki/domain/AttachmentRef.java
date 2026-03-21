package de.bund.zrb.wiki.domain;

/**
 * A reference to an attachment extracted from an HTML page, email, or document.
 * Generalises {@link ImageRef} to also cover PDFs, Office documents, archives, etc.
 * <p>
 * Used by the strip and thumbnail panels to display mixed content
 * (images + documents) in a unified way.
 */
public final class AttachmentRef {

    /** Classification of the attachment for icon / thumbnail selection. */
    public enum Type {
        /** Raster/vector image (PNG, JPG, GIF, SVG, …). */
        IMAGE("\uD83D\uDDBC"),        // 🖼
        /** PDF document — first-page thumbnail can be rendered. */
        PDF("\uD83D\uDCC4"),           // 📄
        /** Word document (DOC, DOCX, ODT, RTF). */
        WORD("\uD83D\uDCDD"),          // 📝
        /** Spreadsheet (XLS, XLSX, ODS, CSV). */
        EXCEL("\uD83D\uDCCA"),         // 📊
        /** Presentation (PPT, PPTX, ODP). */
        POWERPOINT("\uD83D\uDCFD"),    // 📽
        /** Plain or rich text file. */
        TEXT("\uD83D\uDCC3"),           // 📃
        /** Archive (ZIP, TAR, GZ, 7Z, …). */
        ARCHIVE("\uD83D\uDDC4"),       // 🗄
        /** Email message (EML, MSG). */
        EMAIL("\u2709"),               // ✉
        /** Anything else. */
        OTHER("\uD83D\uDCCE");         // 📎

        private final String icon;

        Type(String icon) {
            this.icon = icon;
        }

        /** Emoji icon for this attachment type. */
        public String icon() {
            return icon;
        }
    }

    private final String src;          // URL, file path, or content-ID
    private final String name;         // display name / filename
    private final String title;        // optional tooltip / description
    private final Type type;
    private final long size;           // size in bytes, -1 if unknown
    private final String mimeType;     // optional MIME type (e.g. "application/pdf")

    /** Optional pre-loaded raw data (may be null until downloaded). */
    private byte[] data;

    // ── Constructors ──

    public AttachmentRef(String src, String name, Type type) {
        this(src, name, null, type, -1, null, null);
    }

    public AttachmentRef(String src, String name, String title, Type type,
                         long size, String mimeType) {
        this(src, name, title, type, size, mimeType, null);
    }

    public AttachmentRef(String src, String name, String title, Type type,
                         long size, String mimeType, byte[] data) {
        this.src = src;
        this.name = name != null ? name : guessName(src);
        this.title = title != null ? title : "";
        this.type = type != null ? type : Type.OTHER;
        this.size = size;
        this.mimeType = mimeType != null ? mimeType : "";
        this.data = data;
    }

    // ── Factory methods ──

    /**
     * Wrap an {@link ImageRef} as an IMAGE attachment.
     */
    public static AttachmentRef fromImageRef(ImageRef img) {
        return new AttachmentRef(
                img.src(),
                guessName(img.src()),
                img.description(),
                Type.IMAGE,
                -1, null, null);
    }

    /**
     * Determine the attachment type from a filename or MIME type.
     */
    public static Type guessType(String filename, String mimeType) {
        if (mimeType != null && !mimeType.isEmpty()) {
            String m = mimeType.toLowerCase();
            if (m.startsWith("image/"))                            return Type.IMAGE;
            if (m.equals("application/pdf"))                       return Type.PDF;
            if (m.contains("wordprocessingml") || m.contains("msword")
                    || m.contains("opendocument.text") || m.equals("application/rtf"))
                                                                    return Type.WORD;
            if (m.contains("spreadsheetml") || m.contains("ms-excel")
                    || m.contains("opendocument.spreadsheet"))     return Type.EXCEL;
            if (m.contains("presentationml") || m.contains("ms-powerpoint")
                    || m.contains("opendocument.presentation"))    return Type.POWERPOINT;
            if (m.startsWith("text/"))                             return Type.TEXT;
            if (m.contains("zip") || m.contains("tar") || m.contains("gzip")
                    || m.contains("7z") || m.contains("rar"))     return Type.ARCHIVE;
            if (m.equals("message/rfc822"))                        return Type.EMAIL;
        }
        if (filename != null && !filename.isEmpty()) {
            String lc = filename.toLowerCase();
            int dot = lc.lastIndexOf('.');
            if (dot >= 0) {
                String ext = lc.substring(dot + 1);
                switch (ext) {
                    case "png": case "jpg": case "jpeg": case "gif":
                    case "bmp": case "svg": case "webp": case "tiff": case "tif":
                    case "ico":
                        return Type.IMAGE;
                    case "pdf":
                        return Type.PDF;
                    case "doc": case "docx": case "odt": case "rtf":
                        return Type.WORD;
                    case "xls": case "xlsx": case "ods": case "csv":
                        return Type.EXCEL;
                    case "ppt": case "pptx": case "odp":
                        return Type.POWERPOINT;
                    case "txt": case "log": case "md": case "xml":
                    case "json": case "yaml": case "yml": case "ini":
                    case "cfg": case "properties": case "html": case "htm":
                        return Type.TEXT;
                    case "zip": case "tar": case "gz": case "7z":
                    case "rar": case "bz2": case "xz": case "jar": case "war":
                        return Type.ARCHIVE;
                    case "eml": case "msg":
                        return Type.EMAIL;
                }
            }
        }
        return Type.OTHER;
    }

    /**
     * Create an AttachmentRef auto-detecting the type from filename and MIME type.
     */
    public static AttachmentRef of(String src, String name, String mimeType,
                                   long size, byte[] data) {
        Type type = guessType(name != null ? name : src, mimeType);
        return new AttachmentRef(src, name, null, type, size, mimeType, data);
    }

    // ── Getters ──

    public String src()      { return src; }
    public String name()     { return name; }
    public String title()    { return title; }
    public Type type()       { return type; }
    public long size()       { return size; }
    public String mimeType() { return mimeType; }
    public byte[] data()     { return data; }

    public void setData(byte[] data) { this.data = data; }

    /** Whether this is a displayable image (can use image overlay). */
    public boolean isImage() { return type == Type.IMAGE; }

    /** Whether this is a document (non-image, can show document thumbnail). */
    public boolean isDocument() { return type != Type.IMAGE; }

    /** Best available description for tooltip. */
    public String description() {
        if (!title.isEmpty()) return title;
        if (!name.isEmpty()) return name;
        return src;
    }

    /** Human-readable file size (e.g. "1.2 MB"). */
    public String formattedSize() {
        if (size < 0) return "";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024L * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }

    /**
     * Convert this attachment back to an {@link ImageRef} if it is an image.
     * Returns {@code null} for non-image types.
     */
    public ImageRef toImageRef() {
        if (type != Type.IMAGE) return null;
        return new ImageRef(src, name, title, 0, 0);
    }

    // ── Internal helpers ──

    private static String guessName(String src) {
        if (src == null || src.isEmpty()) return "attachment";
        String path = src;
        int q = path.indexOf('?');
        if (q > 0) path = path.substring(0, q);
        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash < path.length() - 1) return path.substring(slash + 1);
        return path;
    }
}

