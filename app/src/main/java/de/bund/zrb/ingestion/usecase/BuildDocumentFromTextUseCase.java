package de.bund.zrb.ingestion.usecase;

import de.bund.zrb.ingestion.model.ExtractionResult;
import de.bund.zrb.ingestion.model.document.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Use case for building a Document from extracted text.
 * Provides simple heuristics to identify structure in plaintext.
 */
public class BuildDocumentFromTextUseCase {

    // Patterns for structure detection
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^\\d+[.)\\s]\\s*(.+)$");
    private static final Pattern UNORDERED_LIST_PATTERN = Pattern.compile("^[-*+]\\s+(.+)$");
    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile("^```(\\w*)$");
    private static final Pattern QUOTE_PATTERN = Pattern.compile("^>\\s*(.*)$");

    /**
     * Build a minimal Document from an ExtractionResult.
     * Just wraps the plaintext in a ParagraphBlock.
     */
    public Document buildMinimal(ExtractionResult result) {
        if (result == null || !result.isSuccess() || result.getPlainText() == null) {
            return Document.builder().build();
        }

        DocumentMetadata.Builder metaBuilder = DocumentMetadata.builder();
        if (result.getMetadata() != null) {
            metaBuilder.attributes(result.getMetadata());
            String title = result.getMetadata().get("title");
            if (title != null) {
                metaBuilder.sourceName(title);
            }
        }

        return Document.builder()
                .metadata(metaBuilder.build())
                .paragraph(result.getPlainText())
                .build();
    }

    /**
     * Build a Document with structure detection from Markdown-like text.
     */
    public Document buildWithStructure(String text) {
        return buildWithStructure(text, null);
    }

    /**
     * Build a Document with structure detection from Markdown-like text.
     */
    public Document buildWithStructure(String text, DocumentMetadata metadata) {
        if (text == null || text.isEmpty()) {
            return Document.builder().metadata(metadata).build();
        }

        Document.Builder builder = Document.builder().metadata(metadata);
        String[] lines = text.split("\n");

        StringBuilder currentParagraph = new StringBuilder();
        List<String> currentList = null;
        boolean currentListOrdered = false;
        boolean inCodeBlock = false;
        String codeLanguage = "";
        StringBuilder codeContent = new StringBuilder();

        for (String line : lines) {
            // Handle code blocks
            Matcher codeFenceMatcher = CODE_FENCE_PATTERN.matcher(line);
            if (codeFenceMatcher.matches()) {
                if (inCodeBlock) {
                    // End code block
                    builder.code(codeLanguage, codeContent.toString().trim());
                    codeContent = new StringBuilder();
                    codeLanguage = "";
                    inCodeBlock = false;
                } else {
                    // Flush current paragraph/list
                    flushParagraph(builder, currentParagraph);
                    flushList(builder, currentList, currentListOrdered);
                    currentList = null;

                    // Start code block
                    inCodeBlock = true;
                    codeLanguage = codeFenceMatcher.group(1);
                }
                continue;
            }

            if (inCodeBlock) {
                codeContent.append(line).append("\n");
                continue;
            }

            // Handle headings
            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.matches()) {
                flushParagraph(builder, currentParagraph);
                flushList(builder, currentList, currentListOrdered);
                currentList = null;

                int level = headingMatcher.group(1).length();
                String headingText = headingMatcher.group(2).trim();
                builder.heading(level, headingText);
                continue;
            }

            // Handle quotes
            Matcher quoteMatcher = QUOTE_PATTERN.matcher(line);
            if (quoteMatcher.matches()) {
                flushParagraph(builder, currentParagraph);
                flushList(builder, currentList, currentListOrdered);
                currentList = null;

                builder.quote(quoteMatcher.group(1));
                continue;
            }

            // Handle ordered lists
            Matcher orderedMatcher = ORDERED_LIST_PATTERN.matcher(line);
            if (orderedMatcher.matches()) {
                flushParagraph(builder, currentParagraph);
                if (currentList == null || !currentListOrdered) {
                    flushList(builder, currentList, currentListOrdered);
                    currentList = new ArrayList<>();
                    currentListOrdered = true;
                }
                currentList.add(orderedMatcher.group(1).trim());
                continue;
            }

            // Handle unordered lists
            Matcher unorderedMatcher = UNORDERED_LIST_PATTERN.matcher(line);
            if (unorderedMatcher.matches()) {
                flushParagraph(builder, currentParagraph);
                if (currentList == null || currentListOrdered) {
                    flushList(builder, currentList, currentListOrdered);
                    currentList = new ArrayList<>();
                    currentListOrdered = false;
                }
                currentList.add(unorderedMatcher.group(1).trim());
                continue;
            }

            // Handle blank lines
            if (line.trim().isEmpty()) {
                flushParagraph(builder, currentParagraph);
                flushList(builder, currentList, currentListOrdered);
                currentList = null;
                continue;
            }

            // Regular text - add to paragraph
            if (currentParagraph.length() > 0) {
                currentParagraph.append(" ");
            }
            currentParagraph.append(line.trim());
        }

        // Flush remaining content
        if (inCodeBlock) {
            builder.code(codeLanguage, codeContent.toString().trim());
        }
        flushParagraph(builder, currentParagraph);
        flushList(builder, currentList, currentListOrdered);

        return builder.build();
    }

    private void flushParagraph(Document.Builder builder, StringBuilder paragraph) {
        if (paragraph.length() > 0) {
            builder.paragraph(paragraph.toString());
            paragraph.setLength(0);
        }
    }

    private void flushList(Document.Builder builder, List<String> items, boolean ordered) {
        if (items != null && !items.isEmpty()) {
            builder.list(ordered, items);
        }
    }
}

