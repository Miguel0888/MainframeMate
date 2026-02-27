package de.bund.zrb.tools;

import java.util.*;

/**
 * Assigns display categories to tools for the UI.
 * Categories are used in the ModeToolsetDialog to group tools visually.
 */
public final class ToolCategory {

    /** Display order: categories appear in this order in the UI. */
    public static final List<String> ORDERED_CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            "Suche",
            "Dateisystem",
            "Web-Research",
            "Browser-Interaktion",
            "Archiv",
            "Sonstiges"
    ));

    private static final Map<String, String> TOOL_TO_CATEGORY = new LinkedHashMap<>();
    static {
        // ── Suche ──
        cat("search_index",         "Suche");
        cat("search_file",          "Suche");
        cat("grep_search",          "Suche");
        cat("search_attachments",   "Suche");

        // ── Dateisystem ──
        cat("open_resource",        "Dateisystem");
        cat("read_resource",        "Dateisystem");
        cat("read_file",            "Dateisystem");
        cat("stat_path",            "Dateisystem");
        cat("read_chunks",          "Dateisystem");
        cat("read_document_window", "Dateisystem");
        cat("list_attachments",     "Dateisystem");

        // ── Web-Research ──
        cat("research_navigate",    "Web-Research");
        cat("research_search",      "Web-Research");
        cat("research_doc_get",     "Web-Research");
        cat("research_resource_get","Web-Research");
        cat("research_queue_add",   "Web-Research");
        cat("research_queue_status","Web-Research");
        cat("research_external_links","Web-Research");

        // ── Browser-Interaktion ──
        cat("web_click",            "Browser-Interaktion");
        cat("web_type",             "Browser-Interaktion");
        cat("web_select",           "Browser-Interaktion");
        cat("web_scroll",           "Browser-Interaktion");
        cat("web_eval",             "Browser-Interaktion");
        cat("web_screenshot",       "Browser-Interaktion");

        // ── Archiv ──
        cat("web_cache_status",     "Archiv");
        cat("web_cache_add_urls",   "Archiv");
        cat("web_archive_snapshot", "Archiv");

        // ── Sonstiges ──
        cat("clock_timer",          "Sonstiges");
        cat("filter_column",        "Sonstiges");
        cat("set_variable",         "Sonstiges");
        cat("describe_tool",        "Sonstiges");
    }

    private static void cat(String toolName, String category) {
        TOOL_TO_CATEGORY.put(toolName, category);
    }

    /**
     * Returns the category for a tool, or "Sonstiges" if unknown.
     */
    public static String getCategory(String toolName) {
        return TOOL_TO_CATEGORY.getOrDefault(toolName, "Sonstiges");
    }

    /**
     * Returns all known tool → category mappings.
     */
    public static Map<String, String> getAll() {
        return Collections.unmodifiableMap(TOOL_TO_CATEGORY);
    }

    private ToolCategory() {}
}
