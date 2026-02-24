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
                    "3. Web-Recherche: Navigiere zu Seiten, lies den Text mit web_read_page, " +
                    "klicke auf Artikel-Links, lies deren Inhalt, und fasse erst am Ende alles zusammen.\n" +
                    "4. Der Nutzer kann dich jederzeit unterbrechen. Bis dahin: arbeite weiter.\n" +
                    "5. Antworte dem Nutzer erst, wenn du ALLE nötigen Informationen gesammelt hast.\n" +
                    "6. Pro Antwort genau EINEN Tool-Call als reines JSON. KEIN Text vor oder nach dem JSON.\n" +
                    "7. Wenn ein Tool-Ergebnis zurückkommt, mache sofort den nächsten Tool-Call – frage NICHT den Nutzer.\n" +
                    "8. Wenn du eine Webseite navigiert hast und den Text brauchst, rufe IMMER web_read_page auf.\n" +
                    "9. Antworte auf Deutsch.",
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
                "- Mische NIEMALS Text und Tool-Call in einer Antwort.";
    }

    @Override
    public String toString() {
        return label;
    }
}
