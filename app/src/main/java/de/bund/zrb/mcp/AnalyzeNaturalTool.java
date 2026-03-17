package de.bund.zrb.mcp;

import com.google.gson.JsonObject;
import de.bund.zrb.service.NaturalAnalysisService;
import de.bund.zrb.service.NdvSourceCacheService;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.util.*;

/**
 * MCP Tool that gives the AI a comprehensive analysis of a Natural program.
 * <p>
 * Unlike {@link SearchDependencyTool} (which searches the Lucene index),
 * this tool performs a live analysis of the source code and returns:
 * <ul>
 *   <li>All dependencies (active XRefs): CALLNAT, FETCH, PERFORM, INCLUDE, USING, MAP, DB</li>
 *   <li>Passive XRefs (who calls this) — from the library dependency graph</li>
 *   <li>Call chain (callees + callers) — recursive hierarchy tree</li>
 *   <li>Entry point detection, unresolved references</li>
 * </ul>
 * <p>
 * Requires either:
 * <ol>
 *   <li>The object name + library (source is fetched from the in-memory NDV cache)</li>
 *   <li>Or raw source code passed directly in the {@code source} parameter</li>
 * </ol>
 */
public class AnalyzeNaturalTool implements McpTool {

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<String, ToolSpec.Property>();

        properties.put("objectName", new ToolSpec.Property("string",
                "Name des Natural-Objekts (z.B. 'MYPROG'). "
                + "Wenn 'library' angegeben ist, wird der Quellcode aus dem NDV-Cache geladen."));
        properties.put("library", new ToolSpec.Property("string",
                "Natural-Bibliothek (z.B. 'MYLIB'). Zusammen mit objectName wird "
                + "der Quellcode automatisch aus dem Cache geladen."));
        properties.put("source", new ToolSpec.Property("string",
                "Optionaler Natural-Quellcode. Falls angegeben, wird dieser direkt analysiert "
                + "statt ihn aus dem Cache zu laden."));
        properties.put("includeCallChain", new ToolSpec.Property("boolean",
                "Ob die Call Hierarchy (Aufrufkette) mitgeliefert werden soll. Standard: true."));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(
                properties, Collections.singletonList("objectName"));

        Map<String, Object> example = new LinkedHashMap<String, Object>();
        example.put("objectName", "MYPROG");
        example.put("library", "MYLIB");
        example.put("includeCallChain", true);

        return new ToolSpec(
                "analyze_natural",
                "Analysiert ein Natural-Programm und liefert alle Abh\u00e4ngigkeiten, "
                + "Aufrufhierarchie und Querverweise. Ideal um Fragen \u00fcber die Struktur, "
                + "Verwendung und Beziehungen eines Programms zu beantworten.",
                inputSchema,
                example
        );
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        JsonObject response = new JsonObject();

        try {
            String objectName = input.has("objectName") && !input.get("objectName").isJsonNull()
                    ? input.get("objectName").getAsString().trim() : "";
            String library = input.has("library") && !input.get("library").isJsonNull()
                    ? input.get("library").getAsString().trim() : null;
            String source = input.has("source") && !input.get("source").isJsonNull()
                    ? input.get("source").getAsString() : null;
            boolean includeCallChain = !input.has("includeCallChain")
                    || input.get("includeCallChain").isJsonNull()
                    || input.get("includeCallChain").getAsBoolean();

            if (objectName.isEmpty()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Parameter 'objectName' ist erforderlich.");
                return new McpToolResponse(response, resultVar, null);
            }

            NaturalAnalysisService analysisService = NaturalAnalysisService.getInstance();

            // Resolve source code: from parameter, from NDV cache, or fail
            if (source == null || source.isEmpty()) {
                if (library != null && !library.isEmpty()) {
                    source = NdvSourceCacheService.getInstance()
                            .getCachedSource(library, objectName);
                }
                if (source == null || source.isEmpty()) {
                    response.addProperty("status", "no_source");
                    response.addProperty("message",
                            "Quellcode f\u00fcr '" + objectName + "' nicht im Cache gefunden. "
                            + "Bitte \u00f6ffnen Sie das Programm zuerst im NDV-Browser oder geben Sie "
                            + "den Quellcode im Parameter 'source' an.");
                    return new McpToolResponse(response, resultVar, null);
                }
            }

            // Build comprehensive AI summary
            String depSummary = analysisService.buildAiDependencySummary(source, objectName, library);

            String callChainSummary = "";
            if (includeCallChain && library != null) {
                callChainSummary = analysisService.buildAiCallChainSummary(library, objectName);
            }

            response.addProperty("status", "ok");
            response.addProperty("objectName", objectName.toUpperCase());
            if (library != null) response.addProperty("library", library.toUpperCase());
            response.addProperty("sourceLines", source.split("\\r?\\n").length);

            // Structured dependency data
            de.bund.zrb.service.NaturalDependencyService.DependencyResult result =
                    analysisService.analyzeDependencies(source, objectName);
            response.addProperty("totalDependencies", result.getTotalCount());
            response.addProperty("externalCalls", result.getExternalCallTargets().size());
            response.addProperty("dataAreas", result.getDataAreaTargets().size());
            response.addProperty("copycodes", result.getCopycodeTargets().size());

            // Known libraries
            List<String> knownLibs = analysisService.getKnownLibraries();
            if (!knownLibs.isEmpty()) {
                com.google.gson.JsonArray libs = new com.google.gson.JsonArray();
                for (String lib : knownLibs) libs.add(lib);
                response.add("knownLibraries", libs);
            }

            // Full text for AI consumption
            StringBuilder fullText = new StringBuilder();
            fullText.append(depSummary);
            if (!callChainSummary.isEmpty()) {
                fullText.append("\n").append(callChainSummary);
            }

            return new McpToolResponse(response, resultVar, fullText.toString());

        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Analyse-Fehler: " + e.getMessage());
            return new McpToolResponse(response, resultVar, null);
        }
    }
}

