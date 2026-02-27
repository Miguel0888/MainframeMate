package de.bund.zrb.mcpserver.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.research.*;
import de.bund.zrb.mcpserver.tool.McpServerTool;
import de.bund.zrb.mcpserver.tool.ToolResult;

import java.util.*;
import java.util.logging.Logger;

/**
 * MCP Tool that returns external links collected during the research session.
 * <p>
 * External links are those pointing outside the domain boundary of the research
 * seed URL. They are collected automatically when pages are visited (interactively
 * or via background crawling) and can serve as inspiration for further research.
 * <p>
 * The optional {@code domain} parameter filters results to a specific domain.
 * Without a filter, returns a summary grouped by domain with counts.
 */
public class ResearchExternalLinksTool implements McpServerTool {

    private static final Logger LOG = Logger.getLogger(ResearchExternalLinksTool.class.getName());

    @Override
    public String name() {
        return "research_external_links";
    }

    @Override
    public String description() {
        return "Returns external links collected during the current research session. "
             + "External links are URLs pointing outside the domain being researched. "
             + "Use optional parameter 'domain' to filter by a specific external domain "
             + "(e.g. 'reuters.com'). Without filter, returns a domain summary.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject domain = new JsonObject();
        domain.addProperty("type", "string");
        domain.addProperty("description",
                "Optional: filter external links by this domain (e.g. 'reuters.com'). "
              + "If omitted, returns a summary of all domains.");
        props.add("domain", domain);

        schema.add("properties", props);
        // No required fields
        schema.add("required", new JsonArray());
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, BrowserSession session) {
        ResearchSession rs = ResearchSessionManager.getInstance().get(session);
        if (rs == null) {
            return ToolResult.error("Keine aktive Research-Session. Starte zuerst eine Navigation mit research_navigate.");
        }

        ExternalLinkCollector collector = rs.getExternalLinks();
        String domainFilter = null;
        if (params != null && params.has("domain") && params.get("domain").isJsonPrimitive()) {
            domainFilter = params.get("domain").getAsString().trim().toLowerCase();
        }

        if (domainFilter != null && !domainFilter.isEmpty()) {
            // ── Filtered: return links for a specific domain ──
            return buildFilteredResponse(collector, domainFilter);
        } else {
            // ── Summary: all domains with counts ──
            return buildSummaryResponse(collector);
        }
    }

    private ToolResult buildSummaryResponse(ExternalLinkCollector collector) {
        if (collector.getTotalCount() == 0) {
            return ToolResult.text("Keine externen Links gesammelt. Besuche zuerst Seiten mit research_navigate.");
        }

        Set<String> domains = collector.getAllDomains();
        StringBuilder sb = new StringBuilder();
        sb.append("📊 Externe Links: ").append(collector.getTotalCount())
          .append(" URLs auf ").append(domains.size()).append(" Domains\n\n");

        // Group and count by domain
        Map<String, Integer> domainCounts = new TreeMap<>();
        for (Map.Entry<String, List<ExternalLinkCollector.ExternalLink>> entry : collector.getAll().entrySet()) {
            for (ExternalLinkCollector.ExternalLink link : entry.getValue()) {
                if (link.domain != null) {
                    domainCounts.merge(link.domain, 1, Integer::sum);
                }
            }
        }

        sb.append("Domains (nutze research_external_links mit domain=\"...\" für Details):\n");
        for (Map.Entry<String, Integer> entry : domainCounts.entrySet()) {
            sb.append("  • ").append(entry.getKey()).append(" (").append(entry.getValue()).append(" Links)\n");
        }

        return ToolResult.text(sb.toString());
    }

    private ToolResult buildFilteredResponse(ExternalLinkCollector collector, String domain) {
        List<ExternalLinkCollector.ExternalLink> links = collector.getByDomain(domain);
        if (links.isEmpty()) {
            return ToolResult.text("Keine externen Links für Domain '" + domain + "' gefunden.\n"
                    + "Verfügbare Domains: " + collector.getAllDomains());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🔗 Externe Links für '").append(domain).append("': ").append(links.size()).append(" Treffer\n\n");

        JsonArray linksJson = new JsonArray();
        for (ExternalLinkCollector.ExternalLink link : links) {
            sb.append("  • ").append(link.label.isEmpty() ? link.url : link.label)
              .append("\n    → ").append(link.url).append("\n");

            JsonObject j = new JsonObject();
            j.addProperty("url", link.url);
            j.addProperty("label", link.label);
            linksJson.add(j);
        }

        ToolResult result = ToolResult.text(sb.toString());
        JsonObject json = new JsonObject();
        json.addProperty("domain", domain);
        json.addProperty("count", links.size());
        json.add("links", linksJson);
        result.addText(json.toString());
        return result;
    }
}
