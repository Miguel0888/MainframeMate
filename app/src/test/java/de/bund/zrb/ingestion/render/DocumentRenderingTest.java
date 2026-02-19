package de.bund.zrb.ingestion.render;

import de.bund.zrb.ingestion.infrastructure.render.RendererRegistry;
import de.bund.zrb.ingestion.model.document.*;
import de.bund.zrb.ingestion.port.render.DocumentRenderer;
import de.bund.zrb.ingestion.port.render.RenderFormat;
import de.bund.zrb.ingestion.usecase.BuildDocumentFromTextUseCase;
import de.bund.zrb.ingestion.usecase.RenderDocumentUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Document Model and Renderers.
 */
class DocumentRenderingTest {

    private RendererRegistry registry;
    private RenderDocumentUseCase renderUseCase;
    private BuildDocumentFromTextUseCase buildUseCase;

    @BeforeEach
    void setUp() {
        registry = RendererRegistry.createDefault();
        renderUseCase = new RenderDocumentUseCase(registry);
        buildUseCase = new BuildDocumentFromTextUseCase();
    }

    // ========== Document Model Tests ==========

    @Test
    void document_builder_createsDocument() {
        Document doc = Document.builder()
                .heading(1, "Title")
                .paragraph("Some text")
                .build();

        assertEquals(2, doc.getBlockCount());
        assertFalse(doc.isEmpty());
    }

    @Test
    void document_fromText_createsSingleParagraph() {
        Document doc = Document.fromText("Hello World");

        assertEquals(1, doc.getBlockCount());
        assertTrue(doc.getBlocks().get(0) instanceof ParagraphBlock);
    }

    @Test
    void headingBlock_validatesLevel() {
        assertThrows(IllegalArgumentException.class, () -> new HeadingBlock(0, "Test"));
        assertThrows(IllegalArgumentException.class, () -> new HeadingBlock(7, "Test"));

        HeadingBlock valid = new HeadingBlock(3, "Test");
        assertEquals(3, valid.getLevel());
    }

    @Test
    void listBlock_isImmutable() {
        ListBlock list = new ListBlock(false, Arrays.asList("a", "b"));

        assertThrows(UnsupportedOperationException.class, () -> list.getItems().add("c"));
    }

    @Test
    void documentMetadata_builder_works() {
        DocumentMetadata meta = DocumentMetadata.builder()
                .sourceName("test.pdf")
                .mimeType("application/pdf")
                .pageCount(10)
                .attribute("author", "John")
                .build();

        assertEquals("test.pdf", meta.getSourceName());
        assertEquals("application/pdf", meta.getMimeType());
        assertEquals(Integer.valueOf(10), meta.getPageCount());
        assertEquals("John", meta.getAttribute("author"));
    }

    // ========== Markdown Renderer Tests ==========

    @Test
    void markdownRenderer_rendersHeading() {
        Document doc = Document.builder()
                .heading(2, "Test Heading")
                .build();

        String result = renderUseCase.renderToMarkdown(doc);

        assertTrue(result.startsWith("## Test Heading"));
    }

    @Test
    void markdownRenderer_rendersParagraph() {
        Document doc = Document.builder()
                .paragraph("This is a test paragraph.")
                .build();

        String result = renderUseCase.renderToMarkdown(doc);

        assertEquals("This is a test paragraph.", result.trim());
    }

    @Test
    void markdownRenderer_rendersList() {
        Document doc = Document.builder()
                .list(false, Arrays.asList("Item 1", "Item 2", "Item 3"))
                .build();

        String result = renderUseCase.renderToMarkdown(doc);

        assertTrue(result.contains("- Item 1"));
        assertTrue(result.contains("- Item 2"));
        assertTrue(result.contains("- Item 3"));
    }

    @Test
    void markdownRenderer_rendersOrderedList() {
        Document doc = Document.builder()
                .list(true, Arrays.asList("First", "Second"))
                .build();

        String result = renderUseCase.renderToMarkdown(doc);

        assertTrue(result.contains("1. First"));
        assertTrue(result.contains("2. Second"));
    }

    @Test
    void markdownRenderer_rendersCodeBlock() {
        Document doc = Document.builder()
                .code("java", "public class Test {}")
                .build();

        String result = renderUseCase.renderToMarkdown(doc);

        assertTrue(result.contains("```java"));
        assertTrue(result.contains("public class Test {}"));
        assertTrue(result.contains("```"));
    }

    @Test
    void markdownRenderer_rendersQuote() {
        Document doc = Document.builder()
                .quote("A wise quote")
                .build();

        String result = renderUseCase.renderToMarkdown(doc);

        assertTrue(result.contains("> A wise quote"));
    }

    @Test
    void markdownRenderer_rendersTable() {
        Document doc = Document.builder()
                .table(
                        Arrays.asList("Name", "Age"),
                        Arrays.asList(
                                Arrays.asList("Alice", "30"),
                                Arrays.asList("Bob", "25")
                        )
                )
                .build();

        String result = renderUseCase.renderToMarkdown(doc);

        assertTrue(result.contains("| Name | Age |"));
        assertTrue(result.contains("| --- | --- |"));
        assertTrue(result.contains("| Alice | 30 |"));
    }

    // ========== Plaintext Renderer Tests ==========

    @Test
    void plaintextRenderer_rendersCompact() {
        Document doc = Document.builder()
                .heading(1, "Title")
                .paragraph("Content here.")
                .build();

        String result = renderUseCase.renderToPlaintext(doc);

        assertTrue(result.contains("TITLE"));
        assertTrue(result.contains("Content here."));
        // Should not contain Markdown syntax
        assertFalse(result.contains("#"));
    }

    @Test
    void plaintextRenderer_rendersCodeWithoutFences() {
        Document doc = Document.builder()
                .code("java", "int x = 5;")
                .build();

        String result = renderUseCase.renderToPlaintext(doc);

        assertTrue(result.contains("int x = 5;"));
        assertFalse(result.contains("```"));
    }

    // ========== HTML Renderer Tests ==========

    @Test
    void htmlRenderer_escapesSpecialChars() {
        Document doc = Document.builder()
                .paragraph("<script>alert('XSS')</script>")
                .build();

        String result = renderUseCase.renderToHtml(doc);

        assertTrue(result.contains("&lt;script&gt;"));
        assertFalse(result.contains("<script>"));
    }

    @Test
    void htmlRenderer_rendersHeadingTags() {
        Document doc = Document.builder()
                .heading(2, "Title")
                .build();

        String result = renderUseCase.renderToHtml(doc);

        assertTrue(result.contains("<h2>Title</h2>"));
    }

    @Test
    void htmlRenderer_rendersList() {
        Document doc = Document.builder()
                .list(false, Arrays.asList("One", "Two"))
                .build();

        String result = renderUseCase.renderToHtml(doc);

        assertTrue(result.contains("<ul>"));
        assertTrue(result.contains("<li>One</li>"));
        assertTrue(result.contains("<li>Two</li>"));
        assertTrue(result.contains("</ul>"));
    }

    @Test
    void htmlRenderer_rendersOrderedList() {
        Document doc = Document.builder()
                .list(true, Arrays.asList("A", "B"))
                .build();

        String result = renderUseCase.renderToHtml(doc);

        assertTrue(result.contains("<ol>"));
        assertTrue(result.contains("</ol>"));
    }

    @Test
    void htmlRenderer_rendersCodeBlock() {
        Document doc = Document.builder()
                .code("python", "print('hello')")
                .build();

        String result = renderUseCase.renderToHtml(doc);

        assertTrue(result.contains("<pre>"));
        assertTrue(result.contains("<code class=\"language-python\">"));
        assertTrue(result.contains("print(&#39;hello&#39;)"));
    }

    @Test
    void htmlRenderer_rendersTable() {
        Document doc = Document.builder()
                .table(
                        Arrays.asList("Col1"),
                        Arrays.asList(Arrays.asList("Data"))
                )
                .build();

        String result = renderUseCase.renderToHtml(doc);

        assertTrue(result.contains("<table>"));
        assertTrue(result.contains("<th>Col1</th>"));
        assertTrue(result.contains("<td>Data</td>"));
        assertTrue(result.contains("</table>"));
    }

    // ========== BuildDocumentFromText Tests ==========

    @Test
    void buildWithStructure_detectsHeadings() {
        String text = "# Title\n\nSome content.";

        Document doc = buildUseCase.buildWithStructure(text);

        assertEquals(2, doc.getBlockCount());
        assertTrue(doc.getBlocks().get(0) instanceof HeadingBlock);
        HeadingBlock heading = (HeadingBlock) doc.getBlocks().get(0);
        assertEquals(1, heading.getLevel());
        assertEquals("Title", heading.getText());
    }

    @Test
    void buildWithStructure_detectsLists() {
        String text = "- Item 1\n- Item 2\n- Item 3";

        Document doc = buildUseCase.buildWithStructure(text);

        assertEquals(1, doc.getBlockCount());
        assertTrue(doc.getBlocks().get(0) instanceof ListBlock);
        ListBlock list = (ListBlock) doc.getBlocks().get(0);
        assertFalse(list.isOrdered());
        assertEquals(3, list.getItems().size());
    }

    @Test
    void buildWithStructure_detectsCodeBlocks() {
        String text = "```java\nSystem.out.println();\n```";

        Document doc = buildUseCase.buildWithStructure(text);

        assertEquals(1, doc.getBlockCount());
        assertTrue(doc.getBlocks().get(0) instanceof CodeBlock);
        CodeBlock code = (CodeBlock) doc.getBlocks().get(0);
        assertEquals("java", code.getLanguage());
    }

    @Test
    void buildWithStructure_detectsQuotes() {
        String text = "> This is a quote";

        Document doc = buildUseCase.buildWithStructure(text);

        assertEquals(1, doc.getBlockCount());
        assertTrue(doc.getBlocks().get(0) instanceof QuoteBlock);
    }
}

