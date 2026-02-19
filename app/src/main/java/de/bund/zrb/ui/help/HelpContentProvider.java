package de.bund.zrb.ui.help;

import javax.swing.*;
import java.awt.*;

/**
 * Zentrale Registry für Hilfetexte in der Anwendung.
 * Bietet Methoden zum Anzeigen von Hilfe-Dialogen und Popups.
 */
public class HelpContentProvider {

    public enum HelpTopic {
        MAIN_TABS("Datei-Tabs",
                "<html><body style='width: 300px; padding: 10px;'>" +
                "<h3>📁 Datei-Tabs</h3>" +
                "<p>Hier werden geöffnete Dateien und Verzeichnisse als Tabs angezeigt.</p>" +
                "<ul>" +
                "<li><b>Rechtsklick</b> auf einen Tab öffnet ein Kontextmenü</li>" +
                "<li><b>×</b>-Button schließt den Tab</li>" +
                "<li><b>Drag & Drop</b> von Dateien zum Öffnen</li>" +
                "</ul>" +
                "<p><i>Tipp: Mit Strg+S speichern, Strg+W schließen.</i></p>" +
                "</body></html>"),

        RIGHT_DRAWER("Seitenleiste rechts",
                "<html><body style='width: 300px; padding: 10px;'>" +
                "<h3>🔧 Seitenleiste</h3>" +
                "<p>Die rechte Seitenleiste enthält:</p>" +
                "<ul>" +
                "<li><b>📋 Workflow</b> – Automatisierte Abläufe starten</li>" +
                "<li><b>💬 Chat</b> – KI-gestützte Unterhaltung</li>" +
                "</ul>" +
                "<p><i>Klicken Sie auf die Tabs, um zwischen den Ansichten zu wechseln.</i></p>" +
                "</body></html>"),

        CHAT("Chat-Funktion",
                "<html><body style='width: 300px; padding: 10px;'>" +
                "<h3>💬 Chat</h3>" +
                "<p>Kommunizieren Sie mit dem KI-Assistenten.</p>" +
                "<ul>" +
                "<li><b>Modell behalten</b> – Hält das Modell im Speicher</li>" +
                "<li><b>Kontext merken</b> – Behält den Gesprächsverlauf</li>" +
                "<li><b>＋</b> – Neue Chat-Session erstellen</li>" +
                "</ul>" +
                "<p><i>Tipp: Anhänge können per Drag & Drop hinzugefügt werden.</i></p>" +
                "</body></html>"),

        SETTINGS_GENERAL("Allgemeine Einstellungen",
                "<html><body style='width: 300px; padding: 10px;'>" +
                "<h3>⚙️ Allgemeine Einstellungen</h3>" +
                "<p>Konfigurieren Sie grundlegende Anwendungsoptionen:</p>" +
                "<ul>" +
                "<li>Schriftart und -größe</li>" +
                "<li>Encoding</li>" +
                "<li>Auto-Connect</li>" +
                "<li>Passwort speichern</li>" +
                "</ul>" +
                "</body></html>"),

        SETTINGS_COLORS("Farbzuordnung",
                "<html><body style='width: 300px; padding: 10px;'>" +
                "<h3>🎨 Farbzuordnung</h3>" +
                "<p>Definieren Sie Farben für verschiedene Satzarten:</p>" +
                "<ul>" +
                "<li>Doppelklick zum Bearbeiten</li>" +
                "<li>Zeilen hinzufügen/entfernen</li>" +
                "</ul>" +
                "</body></html>"),

        SETTINGS_TRANSFORM("Datenumwandlung",
                "<html><body style='width: 300px; padding: 10px;'>" +
                "<h3>🔄 Datenumwandlung</h3>" +
                "<p>Einstellungen für die Konvertierung von Daten:</p>" +
                "<ul>" +
                "<li>Zeilenende-Format</li>" +
                "<li>Padding und Endmarker</li>" +
                "<li>JSON-Formatierung</li>" +
                "</ul>" +
                "</body></html>"),

        SETTINGS_FTP("FTP-Verbindung",
                "<html><body style='width: 300px; padding: 10px;'>" +
                "<h3>🌐 FTP-Verbindung</h3>" +
                "<p>Konfigurieren Sie die Mainframe-Verbindung:</p>" +
                "<ul>" +
                "<li>Host und Benutzer</li>" +
                "<li>Transfer-Modus</li>" +
                "<li>Dateistruktur und -typ</li>" +
                "</ul>" +
                "</body></html>"),

        SETTINGS_AI("KI-Einstellungen",
                "<html><body style='width: 350px; padding: 10px;'>" +
                "<h3>🤖 KI-Chat Einstellungen</h3>" +
                "<p>Konfigurieren Sie den Provider für den Chat-Assistenten.</p>" +
                "<hr>" +
                "<h4>📡 Verfügbare Provider</h4>" +
                "<ul>" +
                "<li><b>Ollama</b> – Lokales LLM, keine Cloud erforderlich</li>" +
                "<li><b>Cloud</b> – OpenAI, Claude, Perplexity, Grok, Gemini</li>" +
                "<li><b>LlamaCpp</b> – Direkte GGUF-Modell-Ausführung</li>" +
                "<li><b>LocalAI</b> – Ollama-kompatible API</li>" +
                "<li><b>Custom</b> – Selbstgehostete Server mit erweiterten Optionen</li>" +
                "</ul>" +
                "<hr>" +
                "<h4>🔧 Custom-Provider</h4>" +
                "<p>Für selbstgehostete LLM-Server mit speziellen Anforderungen:</p>" +
                "<ul>" +
                "<li>Beliebige API-URL und Authentifizierung</li>" +
                "<li>Benutzerdefinierte Header</li>" +
                "<li>Anpassbare Timeouts und Retries</li>" +
                "<li>SSL-Verifizierung deaktivierbar</li>" +
                "<li>Ollama- oder OpenAI-Antwortformat</li>" +
                "</ul>" +
                "<p>💡 Proxy wird automatisch aus dem Proxy-Tab verwendet.</p>" +
                "</body></html>"),

        SETTINGS_PROXY("Proxy-Einstellungen",
                "<html><body style='width: 300px; padding: 10px;'>" +
                "<h3>🔒 Proxy-Einstellungen</h3>" +
                "<p>Konfigurieren Sie Netzwerk-Proxy:</p>" +
                "<ul>" +
                "<li>Manuell oder automatisch (PAC)</li>" +
                "<li>Host und Port</li>" +
                "<li>Test-Funktion</li>" +
                "</ul>" +
                "</body></html>"),

        SETTINGS_RAG("RAG & Embedding-Einstellungen",
                "<html><body style='width: 350px; padding: 10px;'>" +
                "<h3>🔗 RAG (Retrieval-Augmented Generation)</h3>" +
                "<p>RAG verbessert KI-Antworten durch Zugriff auf Ihre Dokumente.</p>" +
                "<hr>" +
                "<h4>📊 Was sind Embeddings?</h4>" +
                "<p>Embeddings wandeln Text in numerische Vektoren um. Diese erfassen " +
                "die <b>Bedeutung</b> von Wörtern und Sätzen, nicht nur deren Buchstaben.</p>" +
                "<p>Ähnliche Konzepte haben ähnliche Vektoren – so findet das System " +
                "relevante Passagen, auch wenn sie andere Wörter verwenden.</p>" +
                "<hr>" +
                "<h4>⚙️ Separates Modell</h4>" +
                "<p>Für Embeddings kann ein <b>anderes Modell</b> als für Chat verwendet werden:</p>" +
                "<ul>" +
                "<li><b>Ollama</b> – Lokale Modelle wie nomic-embed-text</li>" +
                "<li><b>Cloud</b> – OpenAI text-embedding-3-small</li>" +
                "</ul>" +
                "<p>Embedding-Modelle sind spezialisiert und oft kleiner/schneller als Chat-Modelle.</p>" +
                "<hr>" +
                "<h4>💡 Empfehlungen</h4>" +
                "<ul>" +
                "<li>Für lokale Nutzung: Ollama mit nomic-embed-text</li>" +
                "<li>Für beste Qualität: OpenAI text-embedding-3-small</li>" +
                "<li>Embeddings können auf CPU berechnet werden</li>" +
                "</ul>" +
                "</body></html>"),

        WORKFLOW("Workflow-Panel",
                "<html><body style='width: 300px; padding: 10px;'>" +
                "<h3>📋 Workflows</h3>" +
                "<p>Automatisieren Sie wiederkehrende Aufgaben:</p>" +
                "<ul>" +
                "<li>Workflow-Dateien auswählen und ausführen</li>" +
                "<li>Parameter bearbeiten</li>" +
                "<li>Schritte überwachen</li>" +
                "</ul>" +
                "</body></html>"),

        ATTACHMENTS("Anhänge & Dokumentenkontext",
                "<html><body style='width: 350px; padding: 10px;'>" +
                "<h3>📎 Anhänge im Chat</h3>" +
                "<p>Anhänge erweitern den Kontext des KI-Assistenten und ermöglichen " +
                "gezielte Rückfragen zu Ihren Dokumenten.</p>" +
                "<hr>" +
                "<h4>🔍 Indexierung</h4>" +
                "<p>Beim Hinzufügen werden Dokumente automatisch analysiert:</p>" +
                "<ul>" +
                "<li><b>Volltextindex</b> – Der gesamte Text wird durchsuchbar gemacht. " +
                "Schlüsselwörter und Phrasen können sofort gefunden werden.</li>" +
                "<li><b>Semantische Vektoren</b> – Der Inhalt wird in mathematische " +
                "Repräsentationen umgewandelt, die Bedeutungsähnlichkeiten erfassen. " +
                "So findet der Assistent auch konzeptuell verwandte Passagen.</li>" +
                "</ul>" +
                "<hr>" +
                "<h4>🤖 KI-Zugriff auf Anhänge</h4>" +
                "<p>Der Assistent kann bei Bedarf folgende Aktionen ausführen:</p>" +
                "<ul>" +
                "<li><b>Anhänge auflisten</b> – Übersicht aller verfügbaren Dokumente " +
                "mit Metadaten (Name, Typ, Größe).</li>" +
                "<li><b>Semantisch suchen</b> – Findet relevante Textpassagen basierend " +
                "auf der Bedeutung Ihrer Frage, nicht nur exakten Wörtern.</li>" +
                "<li><b>Textabschnitte lesen</b> – Greift auf spezifische Bereiche zu, " +
                "um Details nachzuschlagen oder zu zitieren.</li>" +
                "<li><b>Dokumentfenster öffnen</b> – Liest größere zusammenhängende " +
                "Abschnitte für tiefere Analyse.</li>" +
                "</ul>" +
                "<hr>" +
                "<h4>💡 Tipps</h4>" +
                "<ul>" +
                "<li>Fügen Sie relevante Dokumente vor Ihrer Frage hinzu</li>" +
                "<li>Der Assistent fragt automatisch Details nach, wenn nötig</li>" +
                "<li>Große Dokumente werden in Abschnitte unterteilt</li>" +
                "<li>⚠️ zeigt Warnungen bei der Verarbeitung an</li>" +
                "</ul>" +
                "</body></html>"),

        // Technische Hilfe für Datenumwandlungs-Einstellungen
        TRANSFORM_ENCODING("Zeichenkodierung (technisch)",
                "<html><body style='width: 380px; padding: 10px;'>" +
                "<h3>🔤 Zeichenkodierung</h3>" +
                "<p>Bestimmt, wie Bytes in Text umgewandelt werden.</p>" +
                "<hr>" +
                "<h4>📍 Verwendung im Code</h4>" +
                "<ul>" +
                "<li><b>FTPClient.setControlEncoding()</b> – Setzt das Encoding für FTP-Befehle</li>" +
                "<li><b>FilePayload.getCharset()</b> – Wird beim Lesen/Schreiben verwendet</li>" +
                "<li><b>RecordStructureCodec</b> – Konvertiert Bytes ↔ String</li>" +
                "</ul>" +
                "<hr>" +
                "<h4>⚙️ Typische Werte</h4>" +
                "<ul>" +
                "<li><b>ISO-8859-1</b> – Standard für Mainframe (Latin-1, 8-Bit)</li>" +
                "<li><b>UTF-8</b> – Moderne Systeme, Unicode</li>" +
                "<li><b>Cp1252</b> – Windows Western European</li>" +
                "</ul>" +
                "<p>⚠️ Bei falschem Encoding erscheinen Umlaute als Sonderzeichen.</p>" +
                "</body></html>"),

        TRANSFORM_LINE_ENDING("Zeilenumbruch des Servers (technisch)",
                "<html><body style='width: 400px; padding: 10px;'>" +
                "<h3>↵ Zeilenumbruch (Record Marker)</h3>" +
                "<p>Hex-Sequenz, die auf dem Mainframe das Ende eines Records markiert.</p>" +
                "<hr>" +
                "<h4>📍 Verwendung im Code</h4>" +
                "<p><b>RecordStructureCodec.decodeForEditor():</b></p>" +
                "<pre style='background:#f0f0f0;padding:5px;'>// Beim Lesen (Server → Editor):\n" +
                "byte[] recordMarker = parseHex(settings.lineEnding);\n" +
                "transformed = replaceBytes(bytes, recordMarker, \"\\n\");</pre>" +
                "<p><b>RecordStructureCodec.encodeForRemote():</b></p>" +
                "<pre style='background:#f0f0f0;padding:5px;'>// Beim Speichern (Editor → Server):\n" +
                "String[] lines = text.split(\"\\n\", -1);\n" +
                "for (line : lines) {\n" +
                "    out.write(line.getBytes());\n" +
                "    out.write(recordMarker); // FF01\n" +
                "}</pre>" +
                "<hr>" +
                "<h4>⚙️ Standard: FF01</h4>" +
                "<p>MVS/z/OS verwendet typischerweise <code>0xFF 0x01</code> als Record-Delimiter " +
                "bei RECORD_STRUCTURE FTP-Transfers.</p>" +
                "<p>Im Editor wird daraus ein normaler Zeilenumbruch <code>\\n</code> (0x0A).</p>" +
                "</body></html>"),

        TRANSFORM_STRIP_NEWLINE("Letzten Zeilenumbruch ausblenden (technisch)",
                "<html><body style='width: 380px; padding: 10px;'>" +
                "<h3>✂️ Letzten Zeilenumbruch entfernen</h3>" +
                "<p>Entfernt den abschließenden Newline nach der Record-Marker-Konvertierung.</p>" +
                "<hr>" +
                "<h4>📍 Verwendung im Code</h4>" +
                "<p><b>RecordStructureCodec.decodeForEditor():</b></p>" +
                "<pre style='background:#f0f0f0;padding:5px;'>// Am Ende der Dekodierung:\n" +
                "if (settings.removeFinalNewline) {\n" +
                "    if (transformed[length-1] == 0x0A) {\n" +
                "        transformed = Arrays.copyOf(\n" +
                "            transformed, length - 1);\n" +
                "    }\n" +
                "}</pre>" +
                "<hr>" +
                "<h4>⚙️ Warum?</h4>" +
                "<p>Jeder Record endet mit <code>FF01</code>, das zu <code>\\n</code> wird. " +
                "Ohne diese Option hätte der Text im Editor eine Extra-Leerzeile am Ende.</p>" +
                "<p>⚠️ Prüft direkt auf Byte <code>0x0A</code> – funktioniert für ISO-8859-1/UTF-8, " +
                "aber nicht für EBCDIC.</p>" +
                "</body></html>"),

        TRANSFORM_EOF_MARKER("Datei-Ende-Kennung (technisch)",
                "<html><body style='width: 400px; padding: 10px;'>" +
                "<h3>🏁 EOF-Marker (End of File)</h3>" +
                "<p>Hex-Sequenz, die das logische Dateiende auf dem Mainframe markiert.</p>" +
                "<hr>" +
                "<h4>📍 Verwendung im Code</h4>" +
                "<p><b>Beim Lesen (Server → Editor):</b></p>" +
                "<pre style='background:#f0f0f0;padding:5px;'>// RecordStructureCodec.decodeForEditor():\n" +
                "byte[] endMarker = parseHex(settings.fileEndMarker);\n" +
                "if (endsWith(transformed, endMarker)) {\n" +
                "    // Entferne EOF-Marker am Ende\n" +
                "    transformed = Arrays.copyOf(\n" +
                "        transformed, length - endMarker.length);\n" +
                "}</pre>" +
                "<p><b>Beim Speichern (Editor → Server):</b></p>" +
                "<pre style='background:#f0f0f0;padding:5px;'>// RecordStructureCodec.encodeForRemote():\n" +
                "// Nach allen Records:\n" +
                "out.write(endMarker); // FF02</pre>" +
                "<hr>" +
                "<h4>⚙️ Standard: FF02</h4>" +
                "<p>Typisch für MVS Record-Struktur. Wird nur am <b>Ende</b> der Datei " +
                "geprüft/angehängt, nie in der Mitte.</p>" +
                "<p>Leer = deaktiviert (kein EOF-Marker).</p>" +
                "</body></html>"),

        TRANSFORM_PADDING("Padding Byte (technisch)",
                "<html><body style='width: 400px; padding: 10px;'>" +
                "<h3>📦 Padding (Füllbyte)</h3>" +
                "<p>Byte, das beim FTP-Download <b>komplett entfernt</b> wird.</p>" +
                "<hr>" +
                "<h4>📍 Verwendung im Code</h4>" +
                "<p><b>CommonsNetFtpFileService.readAllBytes():</b></p>" +
                "<pre style='background:#f0f0f0;padding:5px;'>// Während des FTP-Downloads:\n" +
                "while ((read = in.read(buffer)) != -1) {\n" +
                "    for (int i = 0; i &lt; read; i++) {\n" +
                "        if (buffer[i] != padding) { // z.B. 0x00\n" +
                "            out.write(buffer[i]);\n" +
                "        }\n" +
                "        // Padding-Bytes werden übersprungen!\n" +
                "    }\n" +
                "}</pre>" +
                "<hr>" +
                "<h4>⚙️ Standard: 00</h4>" +
                "<p>Mainframe füllt Records auf LRECL (Logical Record Length) mit " +
                "<code>0x00</code> auf. Diese Füllbytes werden beim Lesen entfernt.</p>" +
                "<p>⚠️ Wichtig: Wird <b>überall</b> im Stream entfernt, nicht nur am Ende!</p>" +
                "<p>Leer = deaktiviert (keine Padding-Entfernung).</p>" +
                "</body></html>");

        private final String title;
        private final String htmlContent;

        HelpTopic(String title, String htmlContent) {
            this.title = title;
            this.htmlContent = htmlContent;
        }

        public String getTitle() {
            return title;
        }

        public String getHtmlContent() {
            return htmlContent;
        }
    }

    /**
     * Zeigt einen Hilfe-Dialog für das gegebene Thema.
     */
    public static void showHelpDialog(Component parent, HelpTopic topic) {
        JEditorPane editorPane = new JEditorPane("text/html", topic.getHtmlContent());
        editorPane.setEditable(false);
        editorPane.setBackground(UIManager.getColor("Panel.background"));

        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setPreferredSize(new Dimension(350, 250));
        scrollPane.setBorder(null);

        JOptionPane.showMessageDialog(
                parent,
                scrollPane,
                "Hilfe: " + topic.getTitle(),
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    /**
     * Zeigt ein leichtgewichtiges Popup für das gegebene Thema.
     */
    public static void showHelpPopup(Component invoker, HelpTopic topic) {
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x1E88E5), 1),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        JEditorPane editorPane = new JEditorPane("text/html", topic.getHtmlContent());
        editorPane.setEditable(false);
        editorPane.setBackground(UIManager.getColor("Panel.background"));
        editorPane.setBorder(null);

        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setPreferredSize(new Dimension(320, 200));
        scrollPane.setBorder(null);

        popup.add(scrollPane);

        // Popup unter dem Button anzeigen
        int x = 0;
        int y = invoker.getHeight();
        if (invoker.getLocationOnScreen().x + 320 > Toolkit.getDefaultToolkit().getScreenSize().width) {
            x = invoker.getWidth() - 320;
        }
        popup.show(invoker, x, y);
    }
}

