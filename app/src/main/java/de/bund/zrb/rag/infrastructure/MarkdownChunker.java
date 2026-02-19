package de.bund.zrb.rag.infrastructure;

import de.bund.zrb.rag.config.RagConfig;
import de.bund.zrb.rag.model.Chunk;
import de.bund.zrb.rag.port.Chunker;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of Chunker that splits text into chunks.
 * Respects Markdown heading boundaries when possible.
 */
public class MarkdownChunker implements Chunker {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n\\n+");

    private final int chunkSizeChars;
    private final int overlapChars;

    public MarkdownChunker() {
        this(RagConfig.defaults());
    }

    public MarkdownChunker(RagConfig config) {
        this.chunkSizeChars = config.getChunkSizeChars();
        this.overlapChars = config.getOverlapChars();
    }

    @Override
    public List<Chunk> chunk(String text, String documentId, String sourceName, String mimeType) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        List<Chunk> chunks = new ArrayList<>();
        int position = 0;
        int offset = 0;

        while (offset < text.length()) {
            int endOffset = Math.min(offset + chunkSizeChars, text.length());

            // Try to find a good break point (paragraph or sentence)
            if (endOffset < text.length()) {
                int breakPoint = findBreakPoint(text, offset, endOffset);
                if (breakPoint > offset) {
                    endOffset = breakPoint;
                }
            }

            String chunkText = text.substring(offset, endOffset).trim();

            if (!chunkText.isEmpty()) {
                Chunk chunk = Chunk.builder()
                        .documentId(documentId)
                        .sourceName(sourceName)
                        .mimeType(mimeType)
                        .position(position)
                        .text(chunkText)
                        .startOffset(offset)
                        .endOffset(endOffset)
                        .build();
                chunks.add(chunk);
                position++;
            }

            // Move forward with overlap
            offset = endOffset - overlapChars;
            if (offset <= chunks.get(chunks.size() - 1).getStartOffset()) {
                offset = endOffset; // Avoid infinite loop
            }
        }

        return chunks;
    }

    @Override
    public List<Chunk> chunkMarkdown(String markdown, String documentId, String sourceName, String mimeType) {
        if (markdown == null || markdown.isEmpty()) {
            return new ArrayList<>();
        }

        List<Chunk> chunks = new ArrayList<>();

        // Find all headings
        List<HeadingInfo> headings = findHeadings(markdown);

        if (headings.isEmpty()) {
            // No headings, fall back to simple chunking
            return chunk(markdown, documentId, sourceName, mimeType);
        }

        int position = 0;
        String currentHeading = null;

        // Process sections between headings
        for (int i = 0; i < headings.size(); i++) {
            HeadingInfo heading = headings.get(i);
            int sectionStart = heading.endOffset;
            int sectionEnd = (i + 1 < headings.size()) ? headings.get(i + 1).startOffset : markdown.length();

            currentHeading = heading.text;
            String sectionText = markdown.substring(sectionStart, sectionEnd).trim();

            if (sectionText.isEmpty()) {
                continue;
            }

            // If section fits in one chunk, create it
            if (sectionText.length() <= chunkSizeChars) {
                Chunk chunk = Chunk.builder()
                        .documentId(documentId)
                        .sourceName(sourceName)
                        .mimeType(mimeType)
                        .position(position)
                        .text(sectionText)
                        .heading(currentHeading)
                        .startOffset(sectionStart)
                        .endOffset(sectionEnd)
                        .build();
                chunks.add(chunk);
                position++;
            } else {
                // Split large section into multiple chunks
                List<Chunk> sectionChunks = chunkSection(sectionText, documentId, sourceName, mimeType,
                        position, currentHeading, sectionStart);
                chunks.addAll(sectionChunks);
                position += sectionChunks.size();
            }
        }

        // Handle text before first heading
        if (!headings.isEmpty() && headings.get(0).startOffset > 0) {
            String preHeadingText = markdown.substring(0, headings.get(0).startOffset).trim();
            if (!preHeadingText.isEmpty()) {
                List<Chunk> preChunks = chunkSection(preHeadingText, documentId, sourceName, mimeType,
                        0, null, 0);
                // Insert at beginning and adjust positions
                for (Chunk c : chunks) {
                    // Need to rebuild with adjusted position
                }
                chunks.addAll(0, preChunks);
            }
        }

        // Reassign positions
        for (int i = 0; i < chunks.size(); i++) {
            Chunk c = chunks.get(i);
            chunks.set(i, Chunk.builder()
                    .chunkId(documentId + "_" + i)
                    .documentId(c.getDocumentId())
                    .sourceName(c.getSourceName())
                    .mimeType(c.getMimeType())
                    .position(i)
                    .text(c.getText())
                    .heading(c.getHeading())
                    .startOffset(c.getStartOffset())
                    .endOffset(c.getEndOffset())
                    .build());
        }

        return chunks;
    }

    private List<Chunk> chunkSection(String text, String documentId, String sourceName, String mimeType,
                                      int startPosition, String heading, int globalOffset) {
        List<Chunk> chunks = new ArrayList<>();
        int position = startPosition;
        int offset = 0;

        while (offset < text.length()) {
            int endOffset = Math.min(offset + chunkSizeChars, text.length());

            // Try to find a good break point
            if (endOffset < text.length()) {
                int breakPoint = findBreakPoint(text, offset, endOffset);
                if (breakPoint > offset) {
                    endOffset = breakPoint;
                }
            }

            String chunkText = text.substring(offset, endOffset).trim();

            if (!chunkText.isEmpty()) {
                Chunk chunk = Chunk.builder()
                        .documentId(documentId)
                        .sourceName(sourceName)
                        .mimeType(mimeType)
                        .position(position)
                        .text(chunkText)
                        .heading(heading)
                        .startOffset(globalOffset + offset)
                        .endOffset(globalOffset + endOffset)
                        .build();
                chunks.add(chunk);
                position++;
            }

            // Move forward with overlap
            int nextOffset = endOffset - overlapChars;
            if (nextOffset <= offset) {
                nextOffset = endOffset;
            }
            offset = nextOffset;
        }

        return chunks;
    }

    private int findBreakPoint(String text, int start, int end) {
        // Look for paragraph break
        int paragraphBreak = text.lastIndexOf("\n\n", end);
        if (paragraphBreak > start + (chunkSizeChars / 2)) {
            return paragraphBreak + 2;
        }

        // Look for sentence end
        for (int i = end - 1; i > start + (chunkSizeChars / 2); i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && i + 1 < text.length() && Character.isWhitespace(text.charAt(i + 1))) {
                return i + 1;
            }
        }

        // Look for any newline
        int newline = text.lastIndexOf('\n', end);
        if (newline > start + (chunkSizeChars / 2)) {
            return newline + 1;
        }

        // Look for space
        int space = text.lastIndexOf(' ', end);
        if (space > start + (chunkSizeChars / 2)) {
            return space + 1;
        }

        return end;
    }

    private List<HeadingInfo> findHeadings(String markdown) {
        List<HeadingInfo> headings = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(markdown);

        while (matcher.find()) {
            int level = matcher.group(1).length();
            String text = matcher.group(2).trim();
            headings.add(new HeadingInfo(level, text, matcher.start(), matcher.end()));
        }

        return headings;
    }

    private static class HeadingInfo {
        final int level;
        final String text;
        final int startOffset;
        final int endOffset;

        HeadingInfo(int level, String text, int startOffset, int endOffset) {
            this.level = level;
            this.text = text;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }
    }
}

