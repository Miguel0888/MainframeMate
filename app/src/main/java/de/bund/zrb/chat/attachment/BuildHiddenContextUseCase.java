package de.bund.zrb.chat.attachment;

import java.util.List;
import java.util.logging.Logger;

/**
 * Use case for building the hidden context from attachments.
 * This context is injected into the LLM request but NOT shown in the chat UI.
 */
public class BuildHiddenContextUseCase {

    private static final Logger LOG = Logger.getLogger(BuildHiddenContextUseCase.class.getName());

    private final ChatAttachmentStore store;
    private final AttachmentContextBuilder contextBuilder;

    public BuildHiddenContextUseCase() {
        this(ChatAttachmentStore.getInstance(), new AttachmentContextBuilder());
    }

    public BuildHiddenContextUseCase(ChatAttachmentStore store, AttachmentContextBuilder contextBuilder) {
        this.store = store;
        this.contextBuilder = contextBuilder;
    }

    /**
     * Build hidden context from a list of attachment IDs.
     *
     * @param attachmentIds the IDs of attachments to include
     * @return the build result containing the context string and metadata
     */
    public AttachmentContextBuilder.BuildResult execute(List<String> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return new AttachmentContextBuilder.BuildResult("", 0, 0, false);
        }

        long startTime = System.currentTimeMillis();

        // Retrieve attachments from store
        List<ChatAttachment> attachments = store.getAll(attachmentIds);

        if (attachments.isEmpty()) {
            LOG.warning("No attachments found for IDs: " + attachmentIds);
            return new AttachmentContextBuilder.BuildResult("", 0, 0, false);
        }

        // Build context
        AttachmentContextBuilder.BuildResult result = contextBuilder.build(attachments);

        long duration = System.currentTimeMillis() - startTime;
        LOG.info(String.format("Built hidden context: %d attachments, %d chars, %d truncated, %dms",
                result.getAttachmentCount(),
                result.getContext().length(),
                result.getTruncatedCount(),
                duration));

        if (result.hasTruncations()) {
            LOG.warning("Attachments were truncated due to size limits");
        }

        return result;
    }

    /**
     * Build hidden context from attachments directly (for testing).
     */
    public AttachmentContextBuilder.BuildResult executeWithAttachments(List<ChatAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return new AttachmentContextBuilder.BuildResult("", 0, 0, false);
        }
        return contextBuilder.build(attachments);
    }
}

