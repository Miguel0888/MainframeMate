package de.bund.zrb.chat.attachment;

import de.bund.zrb.ingestion.infrastructure.render.RendererRegistry;
import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.port.render.RenderFormat;
import de.bund.zrb.ingestion.usecase.RenderDocumentUseCase;

import java.util.List;

/**
 * Builds the hidden context string from attachments.
 * The hidden context is injected into the LLM request but NOT shown in the chat UI.
 *
 * Format:
 * --- ATTACHED DOCUMENTS ---
 * Documents: [1] filename.pdf, [2] report.docx
 *
 * [BEGIN ATTACHMENT 1: filename.pdf]
 * <markdown content>
 * [END ATTACHMENT 1]
 *
 * [BEGIN ATTACHMENT 2: report.docx]
 * <markdown content>
 * [END ATTACHMENT 2]
 * --- END ATTACHED DOCUMENTS ---
 */
public class AttachmentContextBuilder {

    private final RenderDocumentUseCase renderUseCase;
    private final AttachmentConfig config;

    public AttachmentContextBuilder() {
        this(AttachmentConfig.defaults());
    }

    public AttachmentContextBuilder(AttachmentConfig config) {
        this.config = config;
        this.renderUseCase = new RenderDocumentUseCase(RendererRegistry.createDefault());
    }

    /**
     * Build the hidden context from a list of attachments.
     *
     * @param attachments the attachments to include
     * @return the hidden context string
     */
    public BuildResult build(List<ChatAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return new BuildResult("", 0, 0, false);
        }

        // Limit number of attachments
        int effectiveCount = Math.min(attachments.size(), config.getMaxAttachmentsPerMessage());
        boolean wasLimited = attachments.size() > config.getMaxAttachmentsPerMessage();

        StringBuilder context = new StringBuilder();
        context.append("--- ATTACHED DOCUMENTS ---\n");

        // Document list header
        context.append("Documents: ");
        for (int i = 0; i < effectiveCount; i++) {
            if (i > 0) context.append(", ");
            context.append("[").append(i + 1).append("] ");
            context.append(attachments.get(i).getName());
        }
        if (wasLimited) {
            context.append(" (").append(attachments.size() - effectiveCount).append(" weitere ausgelassen)");
        }
        context.append("\n\n");

        // Track total chars for limiting
        int totalChars = context.length();
        int truncatedCount = 0;

        // Render each attachment
        for (int i = 0; i < effectiveCount; i++) {
            ChatAttachment attachment = attachments.get(i);
            String name = attachment.getName() != null ? attachment.getName() : "Dokument " + (i + 1);

            // Render document to Markdown
            String markdown = renderAttachment(attachment);

            // Check per-document limit
            boolean docTruncated = false;
            if (markdown.length() > config.getMaxAttachmentCharsPerDoc()) {
                markdown = truncate(markdown, config.getMaxAttachmentCharsPerDoc());
                docTruncated = true;
                truncatedCount++;
            }

            // Check total limit
            int remainingChars = config.getMaxAttachmentCharsTotal() - totalChars;
            if (markdown.length() > remainingChars) {
                if (remainingChars > 200) {
                    markdown = truncate(markdown, remainingChars - 100);
                    docTruncated = true;
                    truncatedCount++;
                } else {
                    // Skip remaining attachments
                    context.append("[ATTACHMENT ").append(i + 1).append(": ").append(name)
                           .append(" - ausgelassen wegen Kontextlimit]\n\n");
                    wasLimited = true;
                    continue;
                }
            }

            // Build attachment block
            context.append("[BEGIN ATTACHMENT ").append(i + 1).append(": ").append(name);
            if (attachment.hasWarnings()) {
                context.append(" (").append(attachment.getWarningsCount()).append(" Warnungen)");
            }
            if (docTruncated) {
                context.append(" (gekürzt)");
            }
            context.append("]\n");
            context.append(markdown);
            if (!markdown.endsWith("\n")) {
                context.append("\n");
            }
            context.append("[END ATTACHMENT ").append(i + 1).append("]\n\n");

            totalChars = context.length();
        }

        context.append("--- END ATTACHED DOCUMENTS ---");

        return new BuildResult(context.toString(), effectiveCount, truncatedCount, wasLimited);
    }

    private String renderAttachment(ChatAttachment attachment) {
        Document document = attachment.getDocument();
        if (document == null || document.isEmpty()) {
            return "(Kein Inhalt)";
        }

        try {
            return renderUseCase.renderToMarkdown(document);
        } catch (Exception e) {
            return "(Fehler beim Rendern: " + e.getMessage() + ")";
        }
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }

        switch (config.getTruncateStrategy()) {
            case TAIL_WITH_MARKER:
                return "[...Anfang ausgelassen...]\n" + text.substring(text.length() - maxLength + 30);

            case HEAD_TAIL_SUMMARY:
                int halfLen = (maxLength - 50) / 2;
                return text.substring(0, halfLen) +
                       "\n[...Mitte ausgelassen (" + (text.length() - maxLength) + " Zeichen)...]\n" +
                       text.substring(text.length() - halfLen);

            case HEAD_WITH_MARKER:
            default:
                return text.substring(0, maxLength - 30) + "\n[...Ende ausgelassen...]";
        }
    }

    /**
     * Result of building the hidden context.
     */
    public static class BuildResult {
        private final String context;
        private final int attachmentCount;
        private final int truncatedCount;
        private final boolean wasLimited;

        public BuildResult(String context, int attachmentCount, int truncatedCount, boolean wasLimited) {
            this.context = context;
            this.attachmentCount = attachmentCount;
            this.truncatedCount = truncatedCount;
            this.wasLimited = wasLimited;
        }

        public String getContext() {
            return context;
        }

        public int getAttachmentCount() {
            return attachmentCount;
        }

        public int getTruncatedCount() {
            return truncatedCount;
        }

        public boolean wasLimited() {
            return wasLimited;
        }

        public boolean hasTruncations() {
            return truncatedCount > 0 || wasLimited;
        }

        public boolean isEmpty() {
            return context == null || context.isEmpty() || attachmentCount == 0;
        }
    }
}

