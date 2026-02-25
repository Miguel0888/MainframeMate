package de.bund.zrb.ui.components;

import de.bund.zrb.tools.ToolAccessType;

import java.util.EnumSet;

/** Chat modes similar to GitHub Copilot: control how the assistant behaves. */
public enum ChatMode {

    ASK(
            "Ask",
            "Stellt Fragen und bekommt direkte Antworten. Tools nur bei Bedarf.",
            "Du bist ein hilfreicher Assistent in einer Mainframe- und Dateibrowser-Anwendung. " +
                    "Antworte kurz und präzise. Wenn der Nutzer nach Dateien, Inhalten oder Aktionen fragt, " +
                    "kannst du Tools verwenden, aber bleibe sparsam.",
            false,
            EnumSet.noneOf(ToolAccessType.class),
            "",
            ""
    ),

    EDIT(
            "Edit",
            "Hilft beim Ändern von bestehendem Text/Code.",
            "Du bist ein Assistent für Text- und Code-Änderungen. Liefere konkrete, lokal begrenzte Vorschläge. " +
                    "Nutze Tools, um Kontext zu lesen, wenn er fehlt. Schreibende Tools nur, wenn der Nutzer es explizit will.",
            true,
            EnumSet.of(ToolAccessType.READ, ToolAccessType.WRITE),
            defaultToolPrefix(),
            ""
    ),

    PLAN(
            "Plan",
            "Erstellt mehrstufige Umsetzungspläne, bevor etwas geändert wird.",
            "Du bist ein Planungs-Assistent. Skizziere immer zuerst einen Plan mit nummerierten Schritten. " +
                    "Nutze Tools, um die Codebasis zu inspizieren, bevor du den Plan finalisierst. " +
                    "Führe selbst keine großen Änderungen aus – liefere primär den Plan.",
            true,
            EnumSet.of(ToolAccessType.READ),
            defaultToolPrefix(),
            ""
    ),

    AGENT(
            "Agent",
            "Handelt wie ein Agent: nutzt Tools iterativ, bis die Aufgabe erledigt ist.",
            "Du bist ein autonomer Agent. Deine Aufgabe ist es, den Auftrag des Nutzers VOLLSTÄNDIG zu erledigen.\n" +
                    "REGELN:\n" +
                    "1. Nutze so viele Tool-Calls wie nötig, bis die Aufgabe erledigt ist. Höre NICHT nach einem Schritt auf.\n" +
                    "2. Erfinde NIEMALS Daten, Links oder Fakten. Nutze IMMER Tools, um echte Informationen zu bekommen.\n" +
                    "3. Web-Recherche: Nutze research_navigate mit einer URL, einem Link-ID (m0..mN) oder einer History-Aktion (back/forward/reload).\n" +
                    "4. Der Nutzer kann dich jederzeit unterbrechen. Bis dahin: arbeite weiter.\n" +
                    "5. Antworte dem Nutzer erst, wenn du ALLE nötigen Informationen gesammelt hast.\n" +
                    "6. Pro Antwort genau EINEN Tool-Call als reines JSON-Objekt. KEIN Text vor oder nach dem JSON.\n" +
                    "   Korrektes Format: {\"name\":\"tool_name\",\"input\":{\"param\":\"value\"}}\n" +
                    "   FALSCH: Text gefolgt von JSON, oder JSON mit toolName/toolInput statt name/input.\n" +
                    "7. Wenn ein Tool-Ergebnis zurückkommt, mache sofort den nächsten Tool-Call – frage NICHT den Nutzer.\n" +
                    "   Du darfst NIEMALS den Nutzer fragen was als nächstes zu tun ist. Handle autonom.\n" +
                    "8. research_navigate liefert dir automatisch den Seitentext und ein Menü mit nummerierten Links.\n" +
                    "   Wähle einen Link per ID (z.B. 'm3') oder navigiere per URL.\n" +
                    "9. Antworte auf Deutsch.\n" +
                    "10. WICHTIG: Schreibe KEINEN erklärenden Text wenn du einen Tool-Call machen willst. NUR das JSON.\n" +
                    "11. Wenn du 'laut denkst' oder dein Vorgehen beschreiben willst, tu es NICHT. " +
                    "Mache stattdessen direkt den Tool-Call.\n" +
                    "12. Du hast volle Autonomie. Der Nutzer erwartet, dass du SELBSTÄNDIG arbeitest.\n" +
                    "13. Rufe NIEMALS dieselbe URL zweimal auf. Nutze die Link-IDs (m0, m1, ...) aus der Antwort.",
            true,
            EnumSet.of(ToolAccessType.READ, ToolAccessType.WRITE),
            defaultToolPrefix(),
            ""
    ),

    RECHERCHE(
            "Recherche",
            "Durchsucht Webseiten systematisch und archiviert Inhalte für die spätere Suche.",
            "Du bist ein Web-Recherche-Agent. Du nutzt Tools um Webseiten zu besuchen und Informationen zu finden.\n\n" +
                    "WORKFLOW (genau diese Reihenfolge):\n" +
                    "Schritt 1: research_session_start aufrufen (NUR EINMAL! Danach NIE wieder!)\n" +
                    "Schritt 2: research_navigate mit der Ziel-URL aufrufen (z.B. https://de.yahoo.com)\n" +
                    "Schritt 3: Excerpt lesen → interessante Links per Link-ID (m0, m3, ...) anklicken via research_navigate\n" +
                    "Schritt 4: Ergebnisse zusammenfassen und dem Nutzer auf Deutsch antworten\n\n" +
                    "WICHTIGE REGELN:\n" +
                    "- research_session_start wird GENAU EINMAL aufgerufen. Wenn die Antwort 'session is ready' sagt → SOFORT zu Schritt 2.\n" +
                    "- research_navigate ist dein EINZIGES Navigations-Tool. Es akzeptiert:\n" +
                    "  * Eine URL: research_navigate mit target='https://de.yahoo.com/nachrichten/...'\n" +
                    "  * Eine Link-ID: research_navigate mit target='m3' (aus der Link-Liste)\n" +
                    "  * Einen relativen Pfad: research_navigate mit target='/nachrichten/politik/'\n" +
                    "  * History-Aktionen: research_navigate mit target='back', 'forward' oder 'reload'\n" +
                    "- Rufe NIEMALS dieselbe URL zweimal auf! Nutze die Link-IDs aus der Antwort.\n" +
                    "- Besuche 3-5 Artikel, dann fasse zusammen.\n" +
                    "- Pro Antwort genau EINEN Tool-Call als JSON.\n" +
                    "- Antworte auf Deutsch.",
            true,
            EnumSet.of(ToolAccessType.READ, ToolAccessType.WRITE),
            defaultToolPrefix(),
            ""
    );

    private final String label;
    private final String tooltip;
    private final String systemPrompt;
    private final boolean toolAware;
    private final EnumSet<ToolAccessType> allowedToolAccess;
    private final String defaultToolPrefix;
    private final String defaultToolPostfix;

    ChatMode(String label,
             String tooltip,
             String systemPrompt,
             boolean toolAware,
             EnumSet<ToolAccessType> allowedToolAccess,
             String defaultToolPrefix,
             String defaultToolPostfix) {
        this.label = label;
        this.tooltip = tooltip;
        this.systemPrompt = systemPrompt;
        this.toolAware = toolAware;
        this.allowedToolAccess = allowedToolAccess;
        this.defaultToolPrefix = defaultToolPrefix;
        this.defaultToolPostfix = defaultToolPostfix;
    }

    public String getLabel() {
        return label;
    }

    public String getTooltip() {
        return tooltip;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public boolean isToolAware() {
        return toolAware;
    }

    public EnumSet<ToolAccessType> getAllowedToolAccess() {
        return allowedToolAccess;
    }

    public String getDefaultToolPrefix() {
        return defaultToolPrefix;
    }

    public String getDefaultToolPostfix() {
        return defaultToolPostfix;
    }

    private static String defaultToolPrefix() {
        return "TOOL-CALL-REGELN:\n" +
                "- Wenn du ein Tool benutzen willst, antworte AUSSCHLIESSLICH mit dem JSON-Tool-Call.\n" +
                "- KEIN Text, KEINE Erklärung, KEIN Markdown vor oder nach dem JSON.\n" +
                "- Format: {\"name\":\"tool_name\",\"input\":{...}}\n" +
                "- Wenn du kein Tool brauchst, antworte normal mit Text.\n" +
                "- Mische NIEMALS Text und Tool-Call in einer Antwort.\n" +
                "- Schreibe KEINE JSON-Fragmente wie {\"ref\":\"n1\"} oder {\"url\":\"...\"} als Text.\n" +
                "  Solche Fragmente sind KEIN gültiger Tool-Call.\n" +
                "- Denke NICHT laut nach über Tool-Calls. Mache den Tool-Call einfach direkt.";
    }

    @Override
    public String toString() {
        return label;
    }
}
