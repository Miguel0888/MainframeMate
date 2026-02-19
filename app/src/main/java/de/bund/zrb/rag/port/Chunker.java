package de.bund.zrb.rag.port;

import de.bund.zrb.rag.model.Chunk;

import java.util.List;

/**
 * Port interface for chunking documents.
 */
public interface Chunker {

    /**
     * Split text into chunks.
     *
     * @param text the text to chunk
     * @param documentId the document ID
     * @param sourceName the source name
     * @param mimeType the MIME type
     * @return list of chunks
     */
    List<Chunk> chunk(String text, String documentId, String sourceName, String mimeType);

    /**
     * Split Markdown text into chunks, respecting heading boundaries.
     *
     * @param markdown the Markdown text
     * @param documentId the document ID
     * @param sourceName the source name
     * @param mimeType the MIME type
     * @return list of chunks
     */
    List<Chunk> chunkMarkdown(String markdown, String documentId, String sourceName, String mimeType);
}

