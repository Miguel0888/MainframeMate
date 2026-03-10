package de.bund.zrb.ui.settings;

import de.bund.zrb.helper.SentenceTypeSettingsHelper;
import de.zrb.bund.newApi.sentence.SentenceDefinition;
import de.zrb.bund.newApi.sentence.SentenceMeta;
import de.zrb.bund.newApi.sentence.SentenceTypeSpec;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Map;

public class SentenceTypeSettingsDialog {

    private static SentenceTypeSpec sentenceTypeSpec;
    private static SentenceTableModel tableModel;

    public static void show(Component parent) {
        sentenceTypeSpec = SentenceTypeSettingsHelper.loadSentenceTypes();
        tableModel = new SentenceTableModel();

        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setPreferredScrollableViewportSize(new Dimension(600, 300));
        table.getTableHeader().setToolTipText("Pfade: durch Semikolon getrennt. Pattern: regulärer Ausdruck.");

        JButton addButton = new JButton("➕ Satzart");
        addButton.addActionListener(e -> {
            SentenceTypeEditor editor = new SentenceTypeEditor();
            editor.originalKey = ""; // wichtig beim Neuanlegen

            if (showEditorDialog(parent, editor, "Neue Satzart")) {
                SentenceDefinition def = editor.getDefinition();
                String newKey = editor.getKey();

                if (def != null && newKey != null && !newKey.isEmpty()) {
                    boolean exists = sentenceTypeSpec.getDefinitions().keySet().stream()
                            .anyMatch(existingKey -> existingKey.equalsIgnoreCase(newKey));

                    if (exists) {
                        JOptionPane.showMessageDialog(parent,
                                "Ein Eintrag mit dem Namen \"" + newKey + "\" existiert bereits (Groß-/Kleinschreibung ignoriert).",
                                "Fehler beim Speichern", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    sentenceTypeSpec.getDefinitions().put(newKey, def);
                    tableModel.fireTableDataChanged();
                    SentenceTypeSettingsHelper.saveSentenceTypes(sentenceTypeSpec);
                }
            }
        });

        JButton addFileTypeButton = new JButton("➕ Dateityp");
        addFileTypeButton.addActionListener(e -> {
            SentenceTypeEditor editor = new SentenceTypeEditor();
            editor.originalKey = "";
            editor.setFileTypeMode(true);

            if (showEditorDialog(parent, editor, "Neuer Dateityp")) {
                SentenceDefinition def = editor.getDefinition();
                if (def != null) {
                    def.setCategory("filetype");
                }
                String newKey = editor.getKey();

                if (def != null && newKey != null && !newKey.isEmpty()) {
                    boolean exists = sentenceTypeSpec.getDefinitions().keySet().stream()
                            .anyMatch(existingKey -> existingKey.equalsIgnoreCase(newKey));

                    if (exists) {
                        JOptionPane.showMessageDialog(parent,
                                "Ein Eintrag mit dem Namen \"" + newKey + "\" existiert bereits (Groß-/Kleinschreibung ignoriert).",
                                "Fehler beim Speichern", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    sentenceTypeSpec.getDefinitions().put(newKey, def);
                    tableModel.fireTableDataChanged();
                    SentenceTypeSettingsHelper.saveSentenceTypes(sentenceTypeSpec);
                }
            }
        });

        JButton editButton = new JButton("✏ Bearbeiten");
        editButton.addActionListener(e -> {
            int selected = table.getSelectedRow();
            if (selected >= 0) {
                String key = (String) tableModel.getValueAt(selected, 0);
                SentenceDefinition existingDef = sentenceTypeSpec.getDefinitions().get(key);
                SentenceTypeEditor editor = new SentenceTypeEditor();
                editor.setFileTypeMode(existingDef.isFileType());
                editor.setData(key, existingDef);
                editor.originalKey = key; // Originalschlüssel merken

                String title = existingDef.isFileType() ? "Dateityp bearbeiten" : "Satzart bearbeiten";
                if (showEditorDialog(parent, editor, title)) {
                    String newKey = editor.getKey();
                    SentenceDefinition def = editor.getDefinition();

                    if (def != null && newKey != null && !newKey.isEmpty()) {
                        // Preserve category
                        if (existingDef.isFileType()) {
                            def.setCategory("filetype");
                        }
                        // Falls umbenannt, alten Key entfernen
                        if (!newKey.equals(editor.originalKey)) {
                            sentenceTypeSpec.getDefinitions().remove(editor.originalKey);
                        }

                        sentenceTypeSpec.getDefinitions().put(newKey, def);
                        tableModel.fireTableDataChanged();
                        SentenceTypeSettingsHelper.saveSentenceTypes(sentenceTypeSpec);
                    }
                }
            }
        });

        JButton removeButton = new JButton("❌ Entfernen");
        removeButton.addActionListener(e -> {
            int selected = table.getSelectedRow();
            if (selected >= 0) {
                String key = (String) tableModel.getValueAt(selected, 0);
                sentenceTypeSpec.getDefinitions().remove(key);
                tableModel.fireTableDataChanged();
                SentenceTypeSettingsHelper.saveSentenceTypes(sentenceTypeSpec);
            }
        });

        JButton addFileTypesButton = new JButton("📁 Dateitypen hinzufügen");
        addFileTypesButton.setToolTipText("Fügt Standard-Dateitypen hinzu (PDF, MD, JCL, COBOL, NATURAL, WORD, EXCEL, OUTLOOK MAIL)");
        addFileTypesButton.addActionListener(e -> {
            addDefaultFileTypes();
            tableModel.fireTableDataChanged();
            SentenceTypeSettingsHelper.saveSentenceTypes(sentenceTypeSpec);
        });

        JButton removeFileTypesButton = new JButton("🗑 Dateitypen entfernen");
        removeFileTypesButton.setToolTipText("Entfernt alle Dateityp- und Sprach-Einträge (behält Satzarten)");
        removeFileTypesButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(parent,
                    "Alle Dateityp- und Sprach-Einträge wirklich entfernen?\nSatzarten bleiben erhalten.",
                    "Dateitypen entfernen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                sentenceTypeSpec.getDefinitions().entrySet()
                        .removeIf(entry -> entry.getValue().isFileType());
                tableModel.fireTableDataChanged();
                SentenceTypeSettingsHelper.saveSentenceTypes(sentenceTypeSpec);
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(addButton);
        buttonPanel.add(addFileTypeButton);
        buttonPanel.add(editButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(addFileTypesButton);
        buttonPanel.add(removeFileTypesButton);

        JPanel container = new JPanel(new BorderLayout());
        container.add(new JScrollPane(table), BorderLayout.CENTER);
        container.add(buttonPanel, BorderLayout.SOUTH);
        container.setPreferredSize(new Dimension(850, 400));

        int result = JOptionPane.showConfirmDialog(parent, container, "Verwaltung der Satzarten",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            SentenceTypeSettingsHelper.saveSentenceTypes(sentenceTypeSpec);
        }
    }

    private static boolean showEditorDialog(Component parent, SentenceTypeEditor editor, String title) {
        int result = JOptionPane.showConfirmDialog(
                parent,
                editor,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        return result == JOptionPane.OK_OPTION;
    }

    /**
     * Adds default file type definitions if they don't already exist.
     * Includes document types (PDF, WORD, ...) and programming languages (JCL, COBOL, Java, ...).
     */
    private static void addDefaultFileTypes() {
        // name, pathPattern, extensions, transferMode, syntaxStyle, detectionScript
        String[][] defaults = {
                // Document types (rendered)
                {"PDF",          ".*\\.pdf$",              "pdf",           "binary",  "", ""},
                {"MD",           ".*\\.md$",               "md,markdown",   "ascii",   "", ""},
                {"WORD",         ".*\\.(doc|docx)$",        "doc,docx",      "binary",  "", ""},
                {"EXCEL",        ".*\\.(xls|xlsx)$",        "xls,xlsx",      "binary",  "", ""},
                {"OUTLOOK MAIL", ".*\\.(msg|eml)$",         "msg,eml",       "binary",  "", ""},
                // Mainframe programming languages (with content-based detection scripts)
                {"JCL",          ".*\\.(jcl|proc|prc)$",   "jcl,proc,prc",  "ascii",   "text/properties",
                        "import java.util.List;\n"
                        + "import java.util.function.Function;\n"
                        + "public class Expr_DetectJCL implements Function<List<String>, String> {\n"
                        + "    public String apply(List<String> args) {\n"
                        + "        if (args.isEmpty()) return \"false\";\n"
                        + "        String content = args.get(0);\n"
                        + "        String[] lines = content.split(\"\\\\r?\\\\n\", 30);\n"
                        + "        int hits = 0;\n"
                        + "        for (String line : lines) {\n"
                        + "            if (line.startsWith(\"//\") && !line.startsWith(\"///\")) hits++;\n"
                        + "        }\n"
                        + "        return hits >= 3 ? \"true\" : \"false\";\n"
                        + "    }\n"
                        + "}\n"},
                {"COBOL",        ".*\\.(cbl|cob|cobol)$",  "cbl,cob,cobol", "ascii",   "text/plain",
                        "import java.util.List;\n"
                        + "import java.util.function.Function;\n"
                        + "public class Expr_DetectCOBOL implements Function<List<String>, String> {\n"
                        + "    public String apply(List<String> args) {\n"
                        + "        if (args.isEmpty()) return \"false\";\n"
                        + "        String content = args.get(0);\n"
                        + "        String upper = content.toUpperCase();\n"
                        + "        int hits = 0;\n"
                        + "        if (upper.contains(\"IDENTIFICATION DIVISION\")) hits++;\n"
                        + "        if (upper.contains(\"PROCEDURE DIVISION\")) hits++;\n"
                        + "        if (upper.contains(\"DATA DIVISION\")) hits++;\n"
                        + "        if (upper.contains(\"ENVIRONMENT DIVISION\")) hits++;\n"
                        + "        if (upper.contains(\"WORKING-STORAGE SECTION\")) hits++;\n"
                        + "        if (upper.contains(\"PROGRAM-ID\")) hits++;\n"
                        + "        return hits >= 1 ? \"true\" : \"false\";\n"
                        + "    }\n"
                        + "}\n"},
                {"NATURAL",      "",                        "",              "ascii",
                        de.bund.zrb.ui.syntax.MainframeSyntaxSupport.SYNTAX_STYLE_NATURAL,
                        "import java.util.List;\n"
                        + "import java.util.function.Function;\n"
                        + "public class Expr_DetectNATURAL implements Function<List<String>, String> {\n"
                        + "    public String apply(List<String> args) {\n"
                        + "        if (args.isEmpty()) return \"false\";\n"
                        + "        String content = args.get(0);\n"
                        + "        String[] lines = content.split(\"\\\\r?\\\\n\", 40);\n"
                        + "        int hits = 0;\n"
                        + "        for (String line : lines) {\n"
                        + "            String t = line.trim().toUpperCase();\n"
                        + "            if (t.startsWith(\"DEFINE DATA\") || t.startsWith(\"END-DEFINE\")\n"
                        + "                || t.startsWith(\"CALLNAT \") || t.startsWith(\"LOCAL USING\")\n"
                        + "                || t.startsWith(\"PARAMETER USING\") || t.startsWith(\"INPUT USING MAP\")\n"
                        + "                || t.startsWith(\"DECIDE ON\") || t.startsWith(\"FETCH RETURN\")) {\n"
                        + "                hits++;\n"
                        + "            }\n"
                        + "        }\n"
                        + "        return hits >= 2 ? \"true\" : \"false\";\n"
                        + "    }\n"
                        + "}\n"},
                // Common programming languages
                {"Java",         ".*\\.java$",             "java",          "ascii",   "text/java", ""},
                {"Python",       ".*\\.py$",               "py",            "ascii",   "text/python", ""},
                {"JavaScript",   ".*\\.js$",               "js",            "ascii",   "text/javascript", ""},
                {"TypeScript",   ".*\\.ts$",               "ts",            "ascii",   "text/typescript", ""},
                {"JSON",         ".*\\.json$",             "json",          "ascii",   "text/json", ""},
                {"XML",          ".*\\.xml$",              "xml",           "ascii",   "text/xml", ""},
                {"HTML",         ".*\\.html?$",            "html,htm",      "ascii",   "text/html", ""},
                {"SQL",          ".*\\.sql$",              "sql",           "ascii",   "text/sql", ""},
                {"YAML",         ".*\\.ya?ml$",            "yaml,yml",      "ascii",   "text/yaml", ""},
                {"Shell",        ".*\\.(sh|bash)$",        "sh,bash",       "ascii",   "text/unix", ""},
                {"Batch",        ".*\\.(bat|cmd)$",        "bat,cmd",       "ascii",   "text/bat", ""},
                {"Groovy",       ".*\\.(groovy|gradle)$",  "groovy,gradle", "ascii",   "text/groovy", ""},
        };

        for (String[] def : defaults) {
            String key = def[0];
            String pattern = def[1];
            String extensions = def[2];
            String transfer = def[3];
            String syntaxStyle = def[4];
            String detectionScript = def.length > 5 ? def[5] : "";

            boolean exists = sentenceTypeSpec.getDefinitions().keySet().stream()
                    .anyMatch(existingKey -> existingKey.equalsIgnoreCase(key));
            if (exists) continue;

            SentenceDefinition sd = new SentenceDefinition();
            sd.setCategory("filetype");

            SentenceMeta meta = new SentenceMeta();
            meta.setPathPattern(pattern);
            meta.setTransferMode(transfer);

            if (!extensions.isEmpty()) {
                java.util.List<String> extList = new java.util.ArrayList<>();
                for (String ext : extensions.split(",")) {
                    extList.add(ext.trim());
                }
                meta.setExtensions(extList);
            }

            if (syntaxStyle != null && !syntaxStyle.isEmpty()) {
                meta.setSyntaxStyle(syntaxStyle);
            }

            if (detectionScript != null && !detectionScript.isEmpty()) {
                meta.setDetectionScript(detectionScript);
            }

            sd.setMeta(meta);

            sentenceTypeSpec.getDefinitions().put(key, sd);
        }
    }

    private static class SentenceTableModel extends AbstractTableModel {
        private final String[] columns = {"Name", "Typ", "Endungen", "Transfer", "Syntax", "Erkennung", "Pfade", "Pattern", "Feldzahl"};

        @Override
        public int getRowCount() {
            if (sentenceTypeSpec == null || sentenceTypeSpec.getDefinitions() == null) {
                return 0;
            }
            return sentenceTypeSpec.getDefinitions().size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            if (sentenceTypeSpec == null || sentenceTypeSpec.getDefinitions() == null) {
                return "";
            }

            int index = 0;
            for (Map.Entry<String, SentenceDefinition> entry : sentenceTypeSpec.getDefinitions().entrySet()) {
                if (index == row) {
                    SentenceDefinition def = entry.getValue();
                    SentenceMeta meta = def.getMeta();
                    switch (column) {
                        case 0: return entry.getKey();
                        case 1:
                            if (!def.isFileType()) return "📝 Satzart";
                            if (meta != null && meta.hasSyntaxStyle()) return "💻 Sprache";
                            return "📁 Dateityp";
                        case 2:
                            // Endungen
                            if (meta != null && meta.getExtensions() != null && !meta.getExtensions().isEmpty()) {
                                return String.join(", ", meta.getExtensions());
                            }
                            return "";
                        case 3:
                            // Transfer
                            if (!def.isFileType()) return "—";
                            return meta != null && meta.isBinaryTransfer() ? "Binary" : "ASCII";
                        case 4:
                            // Syntax
                            if (meta != null && meta.hasSyntaxStyle()) return meta.getSyntaxStyle();
                            return "";
                        case 5:
                            // Detection script
                            if (meta != null && meta.hasDetectionScript()) return "✅ Script";
                            return "";
                        case 6:
                            List<String> paths = meta != null ? meta.getPaths() : null;
                            return paths != null ? String.join(";", paths) : "";
                        case 7:
                            return meta != null ? meta.getPathPattern() : "";
                        case 8:
                            if (def.isFileType()) return "—";
                            return def.getFields() != null ? def.getFields().size() : 0;
                        default: return "";
                    }
                }
                index++;
            }
            return "";
        }

    }

}