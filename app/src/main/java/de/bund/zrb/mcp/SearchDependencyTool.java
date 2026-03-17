package de.bund.zrb.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.service.LuceneDependencyIndex;
import de.bund.zrb.service.NaturalDependencyGraph.CallerInfo;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.*;

/**
 * MCP Tool for querying the Natural dependency graph.
 * <p>
 * Allows the AI to answer questions like:
 * <ul>
 *   <li>"Which programs call MYSUBPROG?"</li>
 *   <li>"What does PROG1 depend on?"</li>
 *   <li>"Search dependencies for CALLNAT in library MYLIB"</li>
 * </ul>
 * <p>
 * Backed by the Lucene-based {@link LuceneDependencyIndex} for fast persistent queries.
 */
public class SearchDependencyTool implements McpTool {

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<String, ToolSpec.Property>();

        properties.put("query", new ToolSpec.Property("string",
                "Programmname oder Suchtext. Beispiel: 'MYSUBPROG' um Aufrufer zu finden, "
                + "oder 'CALLNAT MYSUBPROG MYLIB' f\u00fcr Volltextsuche."));
        properties.put("mode", new ToolSpec.Property("string",
                "Suchmodus: 'callers' (wer ruft dieses Programm auf?), "
                + "'search' (Volltextsuche in Abh\u00e4ngigkeiten). Standard: 'search'."));
        properties.put("library", new ToolSpec.Property("string",
                "Optionaler Bibliotheksname, um die Suche einzugrenzen."));
        properties.put("maxResults", new ToolSpec.Property("integer",
                "Maximale Anzahl Ergebnisse (Standard: 20)."));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(
                properties, Collections.singletonList("query"));

        Map<String, Object> example = new LinkedHashMap<String, Object>();
        example.put("query", "MYSUBPROG");
        example.put("mode", "callers");
        example.put("library", "MYLIB");

        return new ToolSpec(
                "search_dependencies",
                "Durchsucht den Natural-Abh\u00e4ngigkeitsgraph. "
                + "Findet Aufrufer eines Programms (passive XRefs) oder durchsucht alle "
                + "Abh\u00e4ngigkeitsbeschreibungen per Volltextsuche. "
                + "Daten sind verf\u00fcgbar, nachdem eine Bibliothek im NDV-Browser ge\u00f6ffnet wurde.",
                inputSchema,
                example
        );
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        JsonObject response = new JsonObject();

        try {
            String query = input.has("query") && !input.get("query").isJsonNull()
                    ? input.get("query").getAsString().trim() : "";
            String mode = input.has("mode") && !input.get("mode").isJsonNull()
                    ? input.get("mode").getAsString() : "search";
            String library = input.has("library") && !input.get("library").isJsonNull()
                    ? input.get("library").getAsString() : null;
            int maxResults = 20;
            if (input.has("maxResults") && !input.get("maxResults").isJsonNull()) {
                maxResults = Math.min(input.get("maxResults").getAsInt(), 100);
                if (maxResults < 1) maxResults = 20;
            }

            if (query.isEmpty()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Parameter 'query' ist erforderlich.");
                return new McpToolResponse(response, resultVar, null);
            }

            LuceneDependencyIndex index = LuceneDependencyIndex.getInstance();
            List<String> cached = index.listCachedLibraries();

            if (cached.isEmpty()) {
                response.addProperty("status", "no_data");
                response.addProperty("message",
                        "Kein Dependency-Graph im Cache. Bitte \u00f6ffnen Sie zuerst eine Natural-Bibliothek "
                        + "im NDV-Browser, damit der Graph aufgebaut wird.");
                return new McpToolResponse(response, resultVar, null);
            }

            response.addProperty("status", "ok");

            JsonArray cachedLibs = new JsonArray();
            for (String lib : cached) {
                cachedLibs.add(lib);
            }
            response.add("cachedLibraries", cachedLibs);

            if ("callers".equals(mode)) {
                List<CallerInfo> callers = index.findCallers(query, library);
                response.addProperty("count", callers.size());
                response.addProperty("queryType", "callers");

                JsonArray hits = new JsonArray();
                for (CallerInfo caller : callers) {
                    JsonObject hit = new JsonObject();
                    hit.addProperty("caller", caller.getCallerName());
                    hit.addProperty("kind", caller.getReferenceKind().getCode());
                    hit.addProperty("line", caller.getLineNumber());
                    hit.addProperty("display", caller.getDisplayText());
                    hits.add(hit);
                }
                response.add("results", hits);

                StringBuilder sb = new StringBuilder();
                sb.append("Aufrufer von '").append(query).append("'");
                if (library != null) sb.append(" in ").append(library);
                sb.append(": ").append(callers.size()).append(" gefunden.\n");
                for (CallerInfo c : callers) {
                    sb.append("  \u2022 ").append(c.getDisplayText()).append("\n");
                }
                return new McpToolResponse(response, resultVar, sb.toString());

            } else {
                List<LuceneDependencyIndex.DependencySearchResult> results =
                        index.search(query, maxResults);
                response.addProperty("count", results.size());
                response.addProperty("queryType", "fulltext");

                JsonArray hits = new JsonArray();
                for (LuceneDependencyIndex.DependencySearchResult r : results) {
                    JsonObject hit = new JsonObject();
                    hit.addProperty("library", r.getLibrary());
                    hit.addProperty("caller", r.getCaller());
                    hit.addProperty("callee", r.getCallee());
                    hit.addProperty("kind", r.getKind());
                    hit.addProperty("text", r.getText());
                    hit.addProperty("score", r.getScore());
                    hits.add(hit);
                }
                response.add("results", hits);

                StringBuilder sb = new StringBuilder();
                sb.append("Dependency-Suche '").append(query).append("': ")
                  .append(results.size()).append(" Treffer.\n");
                for (LuceneDependencyIndex.DependencySearchResult r : results) {
                    sb.append("  \u2022 [").append(r.getLibrary()).append("] ")
                      .append(r.getCaller()).append(" \u2192 ").append(r.getCallee())
                      .append(" (").append(r.getKind()).append(")\n");
                }
                return new McpToolResponse(response, resultVar, sb.toString());
            }

        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Fehler: " + e.getMessage());
            return new McpToolResponse(response, resultVar, null);
        }
    }
}
