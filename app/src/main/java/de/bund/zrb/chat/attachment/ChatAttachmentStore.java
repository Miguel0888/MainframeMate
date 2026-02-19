package de.bund.zrb.chat.attachment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory store for chat attachments.
 * Attachments are stored separately from the chat transcript to avoid bloating messages.
 * The chat transcript only stores attachment IDs.
 */
public class ChatAttachmentStore {

    private static final ChatAttachmentStore INSTANCE = new ChatAttachmentStore();

    private final Map<String, ChatAttachment> attachments = new LinkedHashMap<>();
    private final int maxAttachments;

    public ChatAttachmentStore() {
        this(100); // Default max attachments
    }

    public ChatAttachmentStore(int maxAttachments) {
        this.maxAttachments = maxAttachments;
    }

    /**
     * Get the singleton instance.
     */
    public static ChatAttachmentStore getInstance() {
        return INSTANCE;
    }

    /**
     * Store an attachment and return its ID.
     */
    public synchronized String store(ChatAttachment attachment) {
        if (attachment == null) {
            throw new IllegalArgumentException("Attachment cannot be null");
        }

        // Evict oldest if at capacity
        if (attachments.size() >= maxAttachments) {
            String oldestId = attachments.keySet().iterator().next();
            attachments.remove(oldestId);
        }

        attachments.put(attachment.getId(), attachment);
        return attachment.getId();
    }

    /**
     * Get an attachment by ID.
     */
    public synchronized ChatAttachment get(String id) {
        return attachments.get(id);
    }

    /**
     * Get multiple attachments by IDs.
     */
    public synchronized List<ChatAttachment> getAll(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChatAttachment> result = new ArrayList<>();
        for (String id : ids) {
            ChatAttachment att = attachments.get(id);
            if (att != null) {
                result.add(att);
            }
        }
        return result;
    }

    /**
     * Remove an attachment by ID.
     */
    public synchronized ChatAttachment remove(String id) {
        return attachments.remove(id);
    }

    /**
     * Check if an attachment exists.
     */
    public synchronized boolean contains(String id) {
        return attachments.containsKey(id);
    }

    /**
     * Get all attachment IDs.
     */
    public synchronized List<String> getAllIds() {
        return new ArrayList<>(attachments.keySet());
    }

    /**
     * Get all attachments.
     */
    public synchronized List<ChatAttachment> getAllAttachments() {
        return new ArrayList<>(attachments.values());
    }

    /**
     * Get the number of stored attachments.
     */
    public synchronized int size() {
        return attachments.size();
    }

    /**
     * Clear all attachments.
     */
    public synchronized void clear() {
        attachments.clear();
    }

    /**
     * Clear attachments older than the given timestamp.
     */
    public synchronized int clearOlderThan(long timestamp) {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, ChatAttachment> entry : attachments.entrySet()) {
            if (entry.getValue().getCreatedAt() < timestamp) {
                toRemove.add(entry.getKey());
            }
        }
        for (String id : toRemove) {
            attachments.remove(id);
        }
        return toRemove.size();
    }
}

