package de.bund.zrb.websearch.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.zrb.bund.newApi.bot.Agent;
import de.zrb.bund.newApi.bot.AgentSession;

import java.util.logging.Logger;

/**
 * Session for the Recherche Agent.
 *
 * <p>Handles tool-result formatting (extracting links as plain text for small LLMs)
 * and follow-up message generation specific to the research workflow.</p>
 */
public class RechercheAgentSession implements AgentSession {

    private static final Logger LOG = Logger.getLogger(RechercheAgentSession.class.getName());

    private final RechercheAgent agent;

    public RechercheAgentSession(RechercheAgent agent) {
        this.agent = agent;
    }

    @Override
    public Agent getAgent() {
        return agent;
    }

    @Override
    public String buildFollowUpMessage(String toolResultJson, String originalUserRequest) {
        return "Wähle eine URL aus den LINKS oben und rufe research_navigate auf. NUR JSON.";
    }

    @Override
    public String formatToolResult(String toolResultJson) {
        try {
            JsonObject aggregated = JsonParser.parseString(toolResultJson).getAsJsonObject();
            if (!aggregated.has("results") || !aggregated.get("results").isJsonArray()) {
                return null; // fallback to default
            }

            JsonArray results = aggregated.getAsJsonArray("results");
            StringBuilder plain = new StringBuilder();

            for (JsonElement rEl : results) {
                if (!rEl.isJsonObject()) continue;
                JsonObject r = rEl.getAsJsonObject();

                // Append the result text (page content)
                if (r.has("result") && !r.get("result").isJsonNull()) {
                    plain.append(r.get("result").getAsString()).append("\n");
                }
                // Append links as plain text list
                if (r.has("links") && r.get("links").isJsonArray()) {
                    plain.append("\nLINKS:\n");
                    for (JsonElement linkEl : r.getAsJsonArray("links")) {
                        if (linkEl.isJsonObject()) {
                            JsonObject link = linkEl.getAsJsonObject();
                            String label = link.has("label") ? link.get("label").getAsString() : "";
                            String url = link.has("url") ? link.get("url").getAsString() : "";
                            plain.append("- ").append(label).append(": ").append(url).append("\n");
                        }
                    }
                }
            }

            String result = plain.toString().trim();
            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            LOG.fine("[RechercheAgentSession] Could not format tool result: " + e.getMessage());
            return null; // fallback to default JSON format
        }
    }

    @Override
    public void close() {
        // No session-specific resources to release
    }
}

