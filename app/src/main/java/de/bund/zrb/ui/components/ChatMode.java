package de.bund.zrb.ui.components;

/**
 * Chat modes similar to GitHub Copilot: control how the assistant behaves.
 */
public enum ChatMode {

    /**
     * Default Q&A mode. Short, direct Antworten, Tools nur punktuell.
     */
    ASK(
            "Ask",
            "Stellt Fragen und bekommt direkte Antworten. Kurze, präzise Erklärungen, Tools nur bei Bedarf.",
            "Du bist ein hilfreicher Assistent in einer Mainframe- und Dateibrowser-Anwendung. " +
                    "Antworte kurz und präzise. Nutze Tools nur, wenn der Nutzer dich explizit " +
                    "nach Dateien, Inhalten oder konkreten Aktionen fragt. Wenn du Tools nutzt, " +
                    "halte die Anzahl der Tool-Aufrufe gering und erkläre das Ergebnis verständlich."
    ),

    /**
     * Edit mode: Fokus auf Änderungen an bestehendem Code/Text.
     */
    EDIT(
            "Edit",
            "Hilft beim Ändern von bestehendem Text/Code. Fokus auf Diff-artige Vorschläge.",
            "Du bist ein Assistent für Text- und Code-Änderungen. Der Nutzer zeigt dir bestehenden Inhalt " +
                    "(z.B. aus dem Editor) und beschreibt Änderungswünsche. Antworte mit konkreten, " +
                    "lokal begrenzten Änderungsvorschlägen. Wenn sinnvoll, zitiere nur die relevanten " +
                    "Ausschnitte statt alles zu wiederholen. Nutze Tools, um Dateien zu lesen, wenn dir " +
                    "Kontext fehlt."
    ),

    /**
     * Plan mode: erstellt mehrstufige Pläne, keine großen Änderungen direkt.
     */
    PLAN(
            "Plan",
            "Erstellt mehrstufige Umsetzungspläne, bevor etwas geändert wird.",
            "Du bist ein Planungs-Assistent. Deine Hauptaufgabe ist es, mehrstufige, realistische " +
                    "Pläne zu entwerfen (z.B. für Refactorings, Features oder Analysen). Bevor du " +
                    "Konkreten Code vorschlägst, skizziere immer zuerst einen Plan mit nummerierten " +
                    "Schritten. Nutze Tools, um Codebasis und Dateien zu inspizieren, bevor du den Plan " +
                    "finalisierst. Führe selbst keine großen Änderungen aus – liefere primär den Plan."
    ),

    /**
     * Agent mode: darf Tools iterativ und autonom nutzen, bis das Ziel erreicht ist.
     */
    AGENT(
            "Agent",
            "Handelt wie ein Agent: nutzt Tools iterativ, bis die Aufgabe erledigt ist.",
            "Du bist ein autonomer Agent in einer Editor-Anwendung. Du kannst Tools wie 'open_file' " +
                    "und 'read_file' beliebig oft nutzen, um Dateien/Verzeichnisse zu inspizieren. " +
                    "Dein Ziel ist es, die Nutzeraufgabe vollständig zu erledigen, nicht nur einen " +
                    "ersten Versuch zu liefern.\n\n" +
                    "Richtlinien:\n" +
                    "- Wenn der Nutzer nach Inhalten in Dateien/Verzeichnissen fragt, nutze systematisch 'read_file'.\n" +
                    "- Du darfst mehrere Tool-Aufrufe hintereinander erzeugen.\n" +
                    "- Formatiere Tool-Aufrufe immer als gültiges JSON-Objekt: { \"name\": \"TOOLNAME\", \"input\": { ... } }.\n" +
                    "- Wenn ein Tool einen Fehler liefert, analysiere die Fehlermeldung und versuche einen korrigierten Aufruf.\n" +
                    "- Antworte dem Nutzer erst dann final, wenn du genug Informationen aus den Tools gesammelt hast, " +
                    "um seine Frage belastbar zu beantworten."
    );

    private final String label;
    private final String tooltip;
    private final String systemPrompt;

    ChatMode(String label, String tooltip, String systemPrompt) {
        this.label = label;
        this.tooltip = tooltip;
        this.systemPrompt = systemPrompt;
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

    @Override
    public String toString() {
        return label;
    }
}

