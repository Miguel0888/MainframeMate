package de.bund.zrb.ui.preview;

import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Factory for creating the appropriate preview tab based on file type.
 */
public class PreviewTabFactory {

    // Binary document formats
    private static final Set<String> BINARY_FORMATS = new HashSet<>(Arrays.asList(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "odt", "ods", "odp", "rtf"
    ));

    /**
     * Create a preview tab for the given content.
     *
     * @param fileName the file name
     * @param content the content (Markdown/text)
     * @param metadata document metadata
     * @param warnings extraction warnings
     * @param document the Document model (nullable)
     * @param isRemote whether this is a remote file
     * @return the created tab
     */
    public static SplitPreviewTab create(String fileName, String content,
                                         DocumentMetadata metadata, List<String> warnings,
                                         Document document, boolean isRemote) {
        return new SplitPreviewTab(fileName, content, metadata, warnings, document, isRemote);
    }

    /**
     * Determine the default view mode for a file.
     */
    public static ViewMode getDefaultViewMode(String fileName, DocumentMetadata metadata) {
        if (isBinaryDocumentFormat(fileName, metadata)) {
            return ViewMode.RENDERED_ONLY;
        }
        return ViewMode.SPLIT;
    }

    /**
     * Check if the file is a binary document format (PDF, DOC, etc.)
     */
    public static boolean isBinaryDocumentFormat(String fileName, DocumentMetadata metadata) {
        // Check MIME type first
        if (metadata != null && metadata.getMimeType() != null) {
            String mime = metadata.getMimeType().toLowerCase();
            if (mime.contains("pdf") || mime.contains("msword") ||
                mime.contains("officedocument") || mime.contains("opendocument")) {
                return true;
            }
        }

        // Fallback to extension
        if (fileName != null) {
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                String ext = fileName.substring(dotIndex + 1).toLowerCase();
                return BINARY_FORMATS.contains(ext);
            }
        }

        return false;
    }

    /**
     * Check if the file is a text file that should be editable.
     */
    public static boolean isEditableTextFile(String fileName, DocumentMetadata metadata) {
        return !isBinaryDocumentFormat(fileName, metadata);
    }

    /**
     * Get the appropriate content for indexing based on file type.
     * For text files, returns the raw content.
     * For binary formats, returns the extracted content.
     *
     * @param rawContent the raw/extracted content
     * @param fileName the file name
     * @param metadata document metadata
     * @return content suitable for indexing
     */
    public static String getContentForIndexing(String rawContent, String fileName, DocumentMetadata metadata) {
        // For both text and binary formats, the content passed here is already
        // the appropriate format (raw for text, extracted for binary)
        return rawContent;
    }
}

