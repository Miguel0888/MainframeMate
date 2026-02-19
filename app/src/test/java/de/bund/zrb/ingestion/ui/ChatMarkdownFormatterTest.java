package de.bund.zrb.ingestion.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChatMarkdownFormatter.
 */
class ChatMarkdownFormatterTest {

    private ChatMarkdownFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new ChatMarkdownFormatter();
    }

    @Test
    void renderToHtml_rendersHeading() {
        String markdown = "# Hello World";

        String html = formatter.renderToHtml(markdown);

        assertTrue(html.contains("<h1>"));
        assertTrue(html.contains("Hello World"));
    }

    @Test
    void renderToHtml_rendersParagraph() {
        String markdown = "This is a paragraph.";

        String html = formatter.renderToHtml(markdown);

        assertTrue(html.contains("<p>"));
        assertTrue(html.contains("This is a paragraph."));
    }

    @Test
    void renderToHtml_rendersCodeBlock() {
        String markdown = "```java\nSystem.out.println();\n```";

        String html = formatter.renderToHtml(markdown);

        assertTrue(html.contains("<code"));
        assertTrue(html.contains("System.out.println()"));
    }

    @Test
    void renderToHtml_escapesDangerousHtml() {
        String markdown = "<script>alert('XSS')</script>";

        String html = formatter.renderToHtml(markdown);

        // Should be escaped, not executable
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;") || html.contains("script"));
    }

    @Test
    void renderToHtml_rendersBold() {
        String markdown = "This is **bold** text.";

        String html = formatter.renderToHtml(markdown);

        assertTrue(html.contains("<strong>bold</strong>") || html.contains("<b>bold</b>"));
    }

    @Test
    void renderToHtml_rendersList() {
        String markdown = "- Item 1\n- Item 2";

        String html = formatter.renderToHtml(markdown);

        assertTrue(html.contains("<ul>"));
        assertTrue(html.contains("<li>"));
    }

    @Test
    void renderToHtml_returnsEmptyForNull() {
        String html = formatter.renderToHtml(null);

        assertEquals("", html);
    }

    @Test
    void renderToHtml_returnsEmptyForEmpty() {
        String html = formatter.renderToHtml("");

        assertEquals("", html);
    }

    @Test
    void containsMarkdown_detectsHeadings() {
        assertTrue(formatter.containsMarkdown("# Heading"));
        assertTrue(formatter.containsMarkdown("## Heading"));
        assertTrue(formatter.containsMarkdown("### Heading"));
    }

    @Test
    void containsMarkdown_detectsCodeBlocks() {
        assertTrue(formatter.containsMarkdown("```\ncode\n```"));
    }

    @Test
    void containsMarkdown_detectsBold() {
        assertTrue(formatter.containsMarkdown("This is **bold**"));
    }

    @Test
    void containsMarkdown_detectsLinks() {
        assertTrue(formatter.containsMarkdown("[link](http://example.com)"));
    }

    @Test
    void containsMarkdown_returnsFalseForPlainText() {
        assertFalse(formatter.containsMarkdown("Just plain text."));
    }

    @Test
    void extractCodeBlocks_extractsSingleBlock() {
        String markdown = "Text\n```java\ncode here\n```\nMore text";

        String[] blocks = formatter.extractCodeBlocks(markdown);

        assertEquals(1, blocks.length);
        assertEquals("code here", blocks[0]);
    }

    @Test
    void extractCodeBlocks_extractsMultipleBlocks() {
        String markdown = "```python\nprint()\n```\n\n```java\nSystem.out.println();\n```";

        String[] blocks = formatter.extractCodeBlocks(markdown);

        assertEquals(2, blocks.length);
    }

    @Test
    void extractCodeBlocks_returnsEmptyForNoBlocks() {
        String markdown = "No code blocks here.";

        String[] blocks = formatter.extractCodeBlocks(markdown);

        assertEquals(0, blocks.length);
    }

    @Test
    void renderToHtmlWithWrapper_addsDiv() {
        String markdown = "Test";

        String html = formatter.renderToHtmlWithWrapper(markdown, "chat-message");

        assertTrue(html.startsWith("<div class=\"chat-message\">"));
        assertTrue(html.endsWith("</div>"));
    }

    @Test
    void singleton_returnsInstance() {
        ChatMarkdownFormatter instance = ChatMarkdownFormatter.getInstance();

        assertNotNull(instance);
        assertSame(instance, ChatMarkdownFormatter.getInstance());
    }
}

