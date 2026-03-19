package de.bund.zrb.websearch.agent;

import de.zrb.bund.newApi.bot.Agent;
import de.zrb.bund.newApi.bot.AgentContext;
import de.zrb.bund.newApi.bot.AgentSession;
import de.zrb.bund.newApi.mcp.McpTool;

import java.util.List;

/**
 * The Recherche Agent – provides systematic web research capabilities.
 *
 * <p>This agent is registered via the WebSearch plugin and uses the core
 * Browser API for navigation. All research-specific logic (URL tracking,
 * link extraction, archive integration) stays in the plugin.</p>
 */
public class RechercheAgent implements Agent {

    private final List<McpTool> tools;

    public RechercheAgent(List<McpTool> tools) {
        this.tools = tools;
    }

    @Override
    public String getId() {
        return "recherche";
    }

    @Override
    public String getDisplayName() {
        return "Recherche";
    }

    @Override
    public String getDescription() {
        return "Durchsucht Webseiten systematisch und archiviert Inhalte für die spätere Suche.";
    }

    @Override
    public String getSystemPrompt() {
        return "Du bist ein autonomer Web-Recherche-Agent.\n" +
                "Tools: research_navigate (URL aufrufen, 'back'/'forward' für History), " +
                "research_external_links (gesammelte externe Links abrufen).\n\n" +
                "ABLAUF:\n" +
                "1. Du bekommst das TOOL_RESULT mit Seiteninhalt und einer Liste von unbesuchten internen URLs.\n" +
                "2. KOPIERE eine passende URL DIREKT aus der Link-Liste im TOOL_RESULT.\n" +
                "3. Rufe research_navigate auf mit dieser URL als target.\n" +
                "4. Interne Unterseiten werden automatisch im Hintergrund archiviert.\n" +
                "5. Wiederhole bis du genug relevante Seiten besucht hast, dann fasse zusammen.\n\n" +
                "EXTERNE LINKS:\n" +
                "- Externe Links (andere Domains) erscheinen NICHT in der Linkliste.\n" +
                "- Nutze research_external_links um gesammelte externe Links abzurufen.\n" +
                "- Du kannst nach Domain filtern (z.B. domain=\"reuters.com\").\n\n" +
                "ZWINGEND:\n" +
                "- NUR URLs verwenden, die in der Link-Liste des TOOL_RESULT stehen.\n" +
                "- Erfinde KEINE eigenen URLs oder Pfade.\n" +
                "- NIEMALS dieselbe URL zweimal aufrufen (du bekommst einen Fehler).\n" +
                "- Pro Antwort GENAU EIN JSON-Tool-Call. KEIN Text davor oder danach.\n" +
                "- Format: {\"name\":\"research_navigate\",\"input\":{\"target\":\"<URL aus der Liste>\"}}\n" +
                "- FRAGE NIEMALS den Nutzer. Handle AUTONOM.\n" +
                "- Antworte auf Deutsch.";
    }

    @Override
    public List<McpTool> getTools() {
        return tools;
    }

    @Override
    public AgentSession createSession(AgentContext context) {
        return new RechercheAgentSession(this);
    }

    @Override
    public boolean isAutonomous() {
        return true;
    }
}

