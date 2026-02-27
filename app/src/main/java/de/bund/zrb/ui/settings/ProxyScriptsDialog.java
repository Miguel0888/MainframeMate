package de.bund.zrb.ui.settings;

import de.bund.zrb.ingestion.ui.MarkdownRenderer;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Modal dialog that shows the Ollama proxy README (rendered as HTML)
 * and all proxy scripts in a tabbed, read-only view with syntax highlighting.
 */
public final class ProxyScriptsDialog extends JDialog {

    private static final String RESOURCE_BASE = "/proxy/";

    /** Resource files displayed as tabs – order matters. */
    private static final String[][] TABS = {
            {"README",               "PROXY_README.md"},
            {"Proxy Server",         "proxy_ollama.js"},
            {"Client Beispiel",      "proxy_ollama_client_example.js"},
            {"Browser E2E Modul",    "proxy_ollama_e2e_browser.js"},
    };

    private final JTabbedPane tabs;

    public ProxyScriptsDialog(Window owner) {
        super(owner, "Ollama Proxy — Dokumentation & Scripte", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(780, 560));
        setPreferredSize(new Dimension(920, 700));

        tabs = new JTabbedPane(JTabbedPane.TOP);
        for (String[] entry : TABS) {
            String title = entry[0];
            String resource = entry[1];
            String content = loadResource(resource);

            if (resource.endsWith(".md")) {
                JEditorPane mdPane = createMarkdownPane(content);
                JScrollPane scroll = new JScrollPane(mdPane);
                scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
                tabs.addTab(title, scroll);
            } else {
                // RSyntaxTextArea with its own RTextScrollPane (includes line numbers)
                RSyntaxTextArea codeArea = createCodeArea(content);
                RTextScrollPane scroll = new RTextScrollPane(codeArea);
                scroll.setLineNumbersEnabled(true);
                scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
                tabs.addTab(title, scroll);
            }
        }

        // Button bar
        JButton copyBtn = new JButton("Tab-Inhalt kopieren");
        copyBtn.setToolTipText("Kopiert den Quelltext des aktuellen Tabs in die Zwischenablage");
        copyBtn.addActionListener(e -> copyCurrentTab());

        JButton closeBtn = new JButton("Schließen");
        closeBtn.addActionListener(e -> dispose());

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttonBar.add(copyBtn);
        buttonBar.add(closeBtn);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(tabs, BorderLayout.CENTER);
        getContentPane().add(buttonBar, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    // ─── copy helper ───

    private void copyCurrentTab() {
        int idx = tabs.getSelectedIndex();
        if (idx < 0) return;

        String resource = TABS[idx][1];
        // Always copy the raw source (markdown or JS) – not the rendered HTML
        String raw = loadResource(resource);
        java.awt.datatransfer.StringSelection sel =
                new java.awt.datatransfer.StringSelection(raw);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);

        JOptionPane.showMessageDialog(this,
                "Inhalt von \"" + TABS[idx][0] + "\" wurde in die Zwischenablage kopiert.",
                "Kopiert", JOptionPane.INFORMATION_MESSAGE);
    }

    // ─── view factories ───

    /**
     * Renders Markdown as HTML using the project's MarkdownRenderer
     * and displays it in a JEditorPane (HTML).
     */
    private static JEditorPane createMarkdownPane(String markdownSource) {
        MarkdownRenderer renderer = new MarkdownRenderer();
        String bodyHtml = renderer.render(markdownSource);

        String html = "<html><head><style>\n"
                + "body { font-family: 'Segoe UI', Arial, Helvetica, sans-serif; "
                + "       font-size: 11pt; margin: 10px 14px; line-height: 1.5; }\n"
                + "h1 { font-size: 16pt; border-bottom: 1px solid #ccc; padding-bottom: 4px; }\n"
                + "h2 { font-size: 13pt; margin-top: 18px; }\n"
                + "h3 { font-size: 11pt; margin-top: 14px; }\n"
                + "pre { background: #f4f4f4; padding: 8px; font-family: 'Consolas', monospace; font-size: 10pt; }\n"
                + "code { background: #f0f0f0; font-family: 'Consolas', monospace; font-size: 10pt; }\n"
                + "table { border-collapse: collapse; margin: 8px 0; }\n"
                + "th, td { border: 1px solid #bbb; padding: 4px 8px; }\n"
                + "th { background: #e8e8e8; }\n"
                + "blockquote { border-left: 3px solid #ccc; margin: 4px 0; padding-left: 12px; color: #555; }\n"
                + "ul, ol { margin: 4px 0 4px 20px; }\n"
                + "li { margin: 2px 0; }\n"
                + "strong { font-weight: bold; }\n"
                + "em { font-style: italic; }\n"
                + "</style></head><body>\n"
                + bodyHtml
                + "\n</body></html>";

        JEditorPane pane = new JEditorPane();
        pane.setContentType("text/html");
        pane.setText(html);
        pane.setEditable(false);
        pane.setCaretPosition(0);
        pane.setBorder(new EmptyBorder(4, 4, 4, 4));

        // Make links clickable (open in default browser)
        pane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) {
                try {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ignored) { /* best-effort */ }
            }
        });

        return pane;
    }

    /**
     * Read-only RSyntaxTextArea with JavaScript syntax highlighting.
     */
    private static RSyntaxTextArea createCodeArea(String content) {
        RSyntaxTextArea area = new RSyntaxTextArea(content);
        area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        area.setCodeFoldingEnabled(true);
        area.setEditable(false);
        area.setCaretPosition(0);
        area.setLineWrap(false);
        area.setTabSize(2);
        area.setFont(new Font("Consolas", Font.PLAIN, 13));
        area.setHighlightCurrentLine(false);
        return area;
    }

    // ─── resource loader ───

    private static String loadResource(String name) {
        String path = RESOURCE_BASE + name;
        try (InputStream is = ProxyScriptsDialog.class.getResourceAsStream(path)) {
            if (is == null) {
                return "// ⚠ Resource not found: " + path;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            return "// ⚠ Error loading resource " + path + ": " + e.getMessage();
        }
    }
}

