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
            "Du bist ein hilfreicher Assistent in einer Editor-Anwendung mit Zugriff auf Tools:\n" +
                    "- 'read_file': Liest Dateiinhalt oder listet ein Verzeichnis (ohne Tab zu öffnen).\n" +
                    "- 'open_file': Öffnet eine Datei oder ein Verzeichnis als Tab.\n\n" +
                    "WANN du Tools nutzt:\n" +
                    "- NUR wenn der Nutzer explizit nach Dateien, Verzeichnissen, Inhalten oder Aktionen fragt.\n" +
                    "- Bei Smalltalk, Fragen, Erklärungen oder allgemeinen Gesprächen: KEINE Tools nutzen, " +
                    "einfach normal antworten.\n\n" +
                    "WIE du Tools nutzt (wenn nötig):\n" +
                    "1. Erst das Verzeichnis listen (read_file mit Verzeichnispfad), um zu sehen was da ist.\n" +
                    "2. Dann gezielt nur die relevanten Dateien lesen – NICHT alle Dateien blind öffnen.\n" +
                    "3. Erzeuge pro Schritt EINEN Tool-Call als JSON: {\"name\":\"read_file\",\"input\":{\"path\":\"...\"}}\n" +
                    "4. Wenn du die gesuchte Information gefunden hast, STOPPE und antworte dem Nutzer.\n" +
                    "5. Lies eine Datei NIEMALS zweimal. Merke dir, was du bereits gelesen hast.\n" +
                    "6. Bei Fehlern: analysiere die Meldung und versuche einen korrigierten Aufruf.\n\n" +
                    "WICHTIG: Wenn die Aufgabe keine Dateioperationen erfordert, antworte direkt OHNE Tool-Call."
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

