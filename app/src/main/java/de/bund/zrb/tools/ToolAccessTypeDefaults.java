package de.bund.zrb.tools;

/** Derive fallback access type for tools without explicit policy. */
public final class ToolAccessTypeDefaults {

    private ToolAccessTypeDefaults() {
    }

    public static ToolAccessType resolveDefault(String toolName) {
        if (toolName == null) {
            return ToolAccessType.READ;
        }
        String n = toolName.toLowerCase();

        // Explicit READ tools (search, navigation, doc retrieval, browser interaction)
        if (n.equals("search_index") || n.equals("search_file")
                || n.equals("research_search") || n.equals("research_doc_get")
                || n.equals("research_resource_get") || n.equals("research_navigate")
                || n.equals("research_queue_status") || n.equals("research_external_links")
                || n.equals("web_cache_status")
                || n.equals("web_click") || n.equals("web_type")
                || n.equals("web_scroll") || n.equals("web_select")
                || n.equals("web_eval") || n.equals("web_screenshot")
                || n.equals("grep_search") || n.equals("read_file")
                || n.equals("stat_path") || n.equals("describe_tool")
                || n.equals("list_attachments") || n.equals("search_attachments")
                || n.equals("read_chunks") || n.equals("read_document_window")
                || n.equals("clock_timer")) {
            return ToolAccessType.READ;
        }

        // Pattern-based WRITE detection
        if (n.contains("write") || n.contains("set_") || n.contains("save") || n.contains("delete")
                || n.contains("update") || n.contains("create") || n.contains("import")
                || n.equals("open_file")
                || n.equals("web_cache_add_urls") || n.equals("web_archive_snapshot")) {
            return ToolAccessType.WRITE;
        }
        return ToolAccessType.READ;
    }
}
