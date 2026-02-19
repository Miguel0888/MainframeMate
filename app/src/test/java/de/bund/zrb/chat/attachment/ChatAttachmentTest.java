package de.bund.zrb.chat.attachment;

import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Chat Attachment system.
 */
class ChatAttachmentTest {

    private ChatAttachmentStore store;
    private AttachmentContextBuilder contextBuilder;
    private BuildHiddenContextUseCase buildHiddenContextUseCase;

    @BeforeEach
    void setUp() {
        store = new ChatAttachmentStore();
        contextBuilder = new AttachmentContextBuilder();
        buildHiddenContextUseCase = new BuildHiddenContextUseCase(store, contextBuilder);
    }

    // ========== ChatAttachment Tests ==========

    @Test
    void chatAttachment_buildsCorrectly() {
        Document doc = Document.builder()
                .paragraph("Test content")
                .build();

        ChatAttachment attachment = ChatAttachment.builder()
                .name("test.txt")
                .sourcePath("/path/to/test.txt")
                .document(doc)
                .warnings(Arrays.asList("Warning 1"))
                .build();

        assertNotNull(attachment.getId());
        assertEquals("test.txt", attachment.getName());
        assertEquals("/path/to/test.txt", attachment.getSourcePath());
        assertNotNull(attachment.getDocument());
        assertTrue(attachment.hasWarnings());
        assertEquals(1, attachment.getWarningsCount());
    }

    @Test
    void chatAttachment_displayLabel_formatsCorrectly() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .mimeType("application/pdf")
                .build();
        Document doc = Document.builder()
                .metadata(metadata)
                .paragraph("Content")
                .build();

        ChatAttachment attachment = ChatAttachment.builder()
                .name("report.pdf")
                .document(doc)
                .build();

        String label = attachment.getDisplayLabel();
        assertTrue(label.contains("report.pdf"));
        assertTrue(label.contains("pdf"));
    }

    // ========== ChatAttachmentStore Tests ==========

    @Test
    void store_storesAndRetrievesAttachment() {
        ChatAttachment attachment = ChatAttachment.builder()
                .name("test.txt")
                .document(Document.fromText("Content"))
                .build();

        String id = store.store(attachment);

        assertNotNull(id);
        assertEquals(attachment, store.get(id));
        assertTrue(store.contains(id));
    }

    @Test
    void store_removesAttachment() {
        ChatAttachment attachment = ChatAttachment.builder()
                .name("test.txt")
                .document(Document.fromText("Content"))
                .build();

        String id = store.store(attachment);
        store.remove(id);

        assertNull(store.get(id));
        assertFalse(store.contains(id));
    }

    @Test
    void store_getAllReturnsMultiple() {
        ChatAttachment att1 = ChatAttachment.builder()
                .name("file1.txt")
                .document(Document.fromText("Content 1"))
                .build();
        ChatAttachment att2 = ChatAttachment.builder()
                .name("file2.txt")
                .document(Document.fromText("Content 2"))
                .build();

        store.store(att1);
        store.store(att2);

        List<ChatAttachment> all = store.getAll(Arrays.asList(att1.getId(), att2.getId()));
        assertEquals(2, all.size());
    }

    // ========== AttachmentContextBuilder Tests ==========

    @Test
    void contextBuilder_buildsContextFromSingleAttachment() {
        Document doc = Document.builder()
                .heading(1, "Title")
                .paragraph("This is the content.")
                .build();

        ChatAttachment attachment = ChatAttachment.builder()
                .name("document.md")
                .document(doc)
                .build();

        AttachmentContextBuilder.BuildResult result = contextBuilder.build(Arrays.asList(attachment));

        assertFalse(result.isEmpty());
        assertEquals(1, result.getAttachmentCount());
        assertTrue(result.getContext().contains("ATTACHED DOCUMENTS"));
        assertTrue(result.getContext().contains("document.md"));
        assertTrue(result.getContext().contains("Title"));
        assertTrue(result.getContext().contains("This is the content"));
    }

    @Test
    void contextBuilder_buildsContextFromMultipleAttachments() {
        ChatAttachment att1 = ChatAttachment.builder()
                .name("file1.txt")
                .document(Document.fromText("Content of file 1"))
                .build();
        ChatAttachment att2 = ChatAttachment.builder()
                .name("file2.txt")
                .document(Document.fromText("Content of file 2"))
                .build();

        AttachmentContextBuilder.BuildResult result = contextBuilder.build(Arrays.asList(att1, att2));

        assertEquals(2, result.getAttachmentCount());
        assertTrue(result.getContext().contains("file1.txt"));
        assertTrue(result.getContext().contains("file2.txt"));
        assertTrue(result.getContext().contains("ATTACHMENT 1"));
        assertTrue(result.getContext().contains("ATTACHMENT 2"));
    }

    @Test
    void contextBuilder_truncatesLongContent() {
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            longContent.append("This is a very long line of text that will be repeated. ");
        }

        Document doc = Document.fromText(longContent.toString());
        ChatAttachment attachment = ChatAttachment.builder()
                .name("large.txt")
                .document(doc)
                .build();

        AttachmentConfig config = new AttachmentConfig()
                .setMaxAttachmentCharsPerDoc(1000);
        AttachmentContextBuilder builder = new AttachmentContextBuilder(config);

        AttachmentContextBuilder.BuildResult result = builder.build(Arrays.asList(attachment));

        assertTrue(result.hasTruncations());
        assertTrue(result.getTruncatedCount() > 0);
        assertTrue(result.getContext().contains("ausgelassen") || result.getContext().contains("gekürzt"));
    }

    @Test
    void contextBuilder_returnsEmptyForNoAttachments() {
        AttachmentContextBuilder.BuildResult result = contextBuilder.build(Arrays.asList());

        assertTrue(result.isEmpty());
        assertEquals(0, result.getAttachmentCount());
    }

    // ========== BuildHiddenContextUseCase Tests ==========

    @Test
    void buildHiddenContextUseCase_buildsContextFromStoredAttachments() {
        Document doc = Document.builder()
                .paragraph("Test paragraph content")
                .build();

        ChatAttachment attachment = ChatAttachment.builder()
                .name("test.md")
                .document(doc)
                .build();

        store.store(attachment);

        AttachmentContextBuilder.BuildResult result = buildHiddenContextUseCase.execute(
                Arrays.asList(attachment.getId())
        );

        assertFalse(result.isEmpty());
        assertTrue(result.getContext().contains("test.md"));
        assertTrue(result.getContext().contains("Test paragraph content"));
    }

    @Test
    void buildHiddenContextUseCase_returnsEmptyForMissingIds() {
        AttachmentContextBuilder.BuildResult result = buildHiddenContextUseCase.execute(
                Arrays.asList("non-existent-id")
        );

        assertTrue(result.isEmpty());
    }

    // ========== Integration Tests ==========

    @Test
    void fullWorkflow_attachAndBuildContext() {
        // Simulate attaching a document
        Document doc = Document.builder()
                .heading(2, "Report Title")
                .paragraph("This is the executive summary.")
                .code("java", "System.out.println(\"Hello\");")
                .build();

        ChatAttachment attachment = ChatAttachment.builder()
                .name("report.md")
                .document(doc)
                .build();

        // Store it
        store.store(attachment);

        // Build context
        AttachmentContextBuilder.BuildResult result = buildHiddenContextUseCase.execute(
                Arrays.asList(attachment.getId())
        );

        // Verify
        String context = result.getContext();
        assertTrue(context.contains("--- ATTACHED DOCUMENTS ---"));
        assertTrue(context.contains("report.md"));
        assertTrue(context.contains("Report Title"));
        assertTrue(context.contains("executive summary"));
        assertTrue(context.contains("System.out.println"));
        assertTrue(context.contains("--- END ATTACHED DOCUMENTS ---"));
    }
}

