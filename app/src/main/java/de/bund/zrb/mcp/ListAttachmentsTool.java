package de.bund.zrb.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.chat.attachment.ChatAttachment;
import de.bund.zrb.chat.attachment.ChatAttachmentStore;
import de.bund.zrb.rag.service.RagService;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP Tool for listing all current chat attachments.
 * Returns attachment metadata including ID, name, MIME type, and indexing status.
 */
public class ListAttachmentsTool implements McpTool {

    private final MainframeContext context;

    public ListAttachmentsTool(MainframeContext context) {
        this.context = context;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<>();
        // No required parameters

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(properties, Collections.emptyList());

        Map<String, Object> example = new LinkedHashMap<>();

        return new ToolSpec(
                "list_attachments",
                "Listet alle aktuell angehängten Dokumente im Chat auf. " +
                "Gibt Attachment-IDs, Namen, MIME-Typen, Warnungen und Indexierungsstatus zurück.",
                inputSchema,
                example
        );
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        JsonObject response = new JsonObject();

        try {
            ChatAttachmentStore store = ChatAttachmentStore.getInstance();
            RagService ragService = RagService.getInstance();

            JsonArray attachments = new JsonArray();

            for (ChatAttachment attachment : store.getAllAttachments()) {
                JsonObject attObj = new JsonObject();
                attObj.addProperty("id", attachment.getId());
                attObj.addProperty("name", attachment.getName());

                String mimeType = attachment.getDocument() != null
                        && attachment.getDocument().getMetadata() != null
                        ? attachment.getDocument().getMetadata().getMimeType()
                        : "unknown";
                attObj.addProperty("mimeType", mimeType != null ? mimeType : "unknown");

                int warningsCount = attachment.getWarnings() != null ? attachment.getWarnings().size() : 0;
                attObj.addProperty("warningsCount", warningsCount);

                boolean indexed = ragService.isIndexed(attachment.getId());
                attObj.addProperty("indexed", indexed);

                if (indexed) {
                    RagService.IndexedDocument indexedDoc = ragService.getIndexedDocument(attachment.getId());
                    if (indexedDoc != null) {
                        attObj.addProperty("chunkCount", indexedDoc.chunkCount);
                    }
                }

                attachments.add(attObj);
            }

            response.addProperty("status", "success");
            response.addProperty("toolName", "list_attachments");
            response.addProperty("count", attachments.size());
            response.add("attachments", attachments);

        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("toolName", "list_attachments");
            response.addProperty("message", e.getMessage());
        }

        return new McpToolResponse(response, resultVar, null);
    }
}

