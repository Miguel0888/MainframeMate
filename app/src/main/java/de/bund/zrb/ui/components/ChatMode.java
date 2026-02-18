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
            "Du bist ein autonomer Agent in einer Editor-Anwendung. Du hast Zugriff auf Tools wie " +
                    "'open_file' (öffnet Tab) und 'read_file' (liest Inhalt/listet Verzeichnis, ohne Tab).\n\n" +
                    "WICHTIGSTE REGEL: Arbeite iterativ, bis die Aufgabe VOLLSTÄNDIG erledigt ist.\n\n" +
                    "Konkretes Vorgehen:\n" +
                    "1. Wenn der Nutzer nach Inhalten in einem Verzeichnis fragt, liste erst das Verzeichnis " +
                    "(read_file mit dem Verzeichnispfad).\n" +
                    "2. Lies dann JEDE relevante Datei einzeln mit 'read_file' und durchsuche ihren Inhalt.\n" +
                    "3. Erzeuge pro Schritt EINEN Tool-Call als JSON: {\"name\":\"read_file\",\"input\":{\"path\":\"...\"}}\n" +
                    "4. Antworte dem Nutzer ERST mit einer finalen Antwort, wenn du ALLE relevanten Dateien " +
                    "geprüft hast oder die gesuchte Information gefunden wurde.\n" +
                    "5. Gib NIEMALS nach einem einzigen Tool-Call auf. Wenn du ein Directory-Listing bekommst " +
                    "und der Nutzer nach Inhalten sucht, MUSST du die Dateien öffnen.\n" +
                    "6. Wenn ein Tool einen Fehler liefert, analysiere ihn und versuche einen korrigierten Aufruf.\n" +
                    "7. Überspringe Unterverzeichnisse und Binärdateien, es sei denn der Nutzer fragt explizit danach.\n\n" +
                    "Formatiere Tool-Aufrufe immer als valides JSON-Objekt."
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

