package de.bund.zrb.ui.filetab;

import de.bund.zrb.model.Settings;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.ui.filetab.event.CaretMovedEvent;
import de.bund.zrb.ui.filetab.event.EditorContentChangedEvent;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.undo.UndoManager;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class EditorPanel extends JPanel {

    // Extension to RSyntaxTextArea syntax style mapping
    private static final Map<String, String> SYNTAX_STYLES = new HashMap<>();
    static {
        SYNTAX_STYLES.put("java", SyntaxConstants.SYNTAX_STYLE_JAVA);
        SYNTAX_STYLES.put("py", SyntaxConstants.SYNTAX_STYLE_PYTHON);
        SYNTAX_STYLES.put("js", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        SYNTAX_STYLES.put("ts", SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT);
        SYNTAX_STYLES.put("json", SyntaxConstants.SYNTAX_STYLE_JSON);
        SYNTAX_STYLES.put("xml", SyntaxConstants.SYNTAX_STYLE_XML);
        SYNTAX_STYLES.put("html", SyntaxConstants.SYNTAX_STYLE_HTML);
        SYNTAX_STYLES.put("htm", SyntaxConstants.SYNTAX_STYLE_HTML);
        SYNTAX_STYLES.put("css", SyntaxConstants.SYNTAX_STYLE_CSS);
        SYNTAX_STYLES.put("sql", SyntaxConstants.SYNTAX_STYLE_SQL);
        SYNTAX_STYLES.put("sh", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
        SYNTAX_STYLES.put("bash", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
        SYNTAX_STYLES.put("bat", SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH);
        SYNTAX_STYLES.put("cmd", SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH);
        SYNTAX_STYLES.put("ps1", SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH);
        SYNTAX_STYLES.put("rb", SyntaxConstants.SYNTAX_STYLE_RUBY);
        SYNTAX_STYLES.put("php", SyntaxConstants.SYNTAX_STYLE_PHP);
        SYNTAX_STYLES.put("c", SyntaxConstants.SYNTAX_STYLE_C);
        SYNTAX_STYLES.put("cpp", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS);
        SYNTAX_STYLES.put("h", SyntaxConstants.SYNTAX_STYLE_C);
        SYNTAX_STYLES.put("hpp", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS);
        SYNTAX_STYLES.put("go", SyntaxConstants.SYNTAX_STYLE_GO);
        SYNTAX_STYLES.put("yml", SyntaxConstants.SYNTAX_STYLE_YAML);
        SYNTAX_STYLES.put("yaml", SyntaxConstants.SYNTAX_STYLE_YAML);
        SYNTAX_STYLES.put("md", SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
        SYNTAX_STYLES.put("markdown", SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
        SYNTAX_STYLES.put("properties", SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE);
        SYNTAX_STYLES.put("ini", SyntaxConstants.SYNTAX_STYLE_INI);
        SYNTAX_STYLES.put("csv", SyntaxConstants.SYNTAX_STYLE_CSV);
        SYNTAX_STYLES.put("groovy", SyntaxConstants.SYNTAX_STYLE_GROOVY);
        SYNTAX_STYLES.put("gradle", SyntaxConstants.SYNTAX_STYLE_GROOVY);
        SYNTAX_STYLES.put("scala", SyntaxConstants.SYNTAX_STYLE_SCALA);
        SYNTAX_STYLES.put("kotlin", SyntaxConstants.SYNTAX_STYLE_KOTLIN);
        SYNTAX_STYLES.put("kt", SyntaxConstants.SYNTAX_STYLE_KOTLIN);
        SYNTAX_STYLES.put("lua", SyntaxConstants.SYNTAX_STYLE_LUA);
        SYNTAX_STYLES.put("perl", SyntaxConstants.SYNTAX_STYLE_PERL);
        SYNTAX_STYLES.put("pl", SyntaxConstants.SYNTAX_STYLE_PERL);
    }

    private boolean suppressChangeEvents = false;
    private int originalContentHash;

    private final RSyntaxTextArea textArea = new RSyntaxTextArea();
    private final UndoManager undoManager = new UndoManager();

    private final JButton undoButton = new JButton("↶");
    private final JButton redoButton = new JButton("↷");

    public EditorPanel() {
        super(new BorderLayout());

        initTextArea();
        textArea.getDocument().addUndoableEditListener(undoManager);

        RTextScrollPane scrollPane = new RTextScrollPane(textArea);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.setFoldIndicatorEnabled(true);

        add(scrollPane, BorderLayout.CENTER);
    }

    private int getCaretLine() {
        try {
            int pos = textArea.getCaretPosition();
            return textArea.getLineOfOffset(pos);
        } catch (Exception ex) {
            return 0;
        }
    }

    private void initTextArea() {
        Settings settings = SettingsHelper.load();
        Font font = new Font(settings.editorFont, Font.PLAIN, settings.editorFontSize);
        textArea.setFont(font);
        textArea.setSyntaxEditingStyle("text/plain");
        textArea.setAntiAliasingEnabled(true);
        textArea.setCodeFoldingEnabled(true);
        textArea.setHighlightCurrentLine(true);
        textArea.setTabSize(4);
        textArea.setLineWrap(false);
        textArea.setPaintTabLines(true);
        if (settings.marginColumn > 0) {
            textArea.setMarginLineEnabled(true);
            textArea.setMarginLinePosition(settings.marginColumn);
        }
    }

    public RSyntaxTextArea getTextArea() {
        return textArea;
    }

    public UndoManager getUndoManager() {
        return undoManager;
    }

    public JButton getUndoButton() {
        return undoButton;
    }

    public JButton getRedoButton() {
        return redoButton;
    }

    public void undo() {
        if (undoManager.canUndo()) undoManager.undo();
    }

    public void redo() {
        if (undoManager.canRedo()) undoManager.redo();
    }

    public void resetUndoHistory() {
        undoManager.discardAllEdits();
    }

    public void bindEvents(FileTabEventDispatcher dispatcher) {
        // Ergänzter DocumentListener für direkte Textänderungserkennung (inkl. Undo)
        textArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                handleChange();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                handleChange();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                // Ignorieren, da nicht relevant für plain text
            }

            private void handleChange() {
                if (!suppressChangeEvents) {
                    boolean changed = textArea.getText().hashCode() != originalContentHash;
                    dispatcher.publish(new EditorContentChangedEvent(changed));
                }
            }
        });

        getTextArea().addCaretListener(e -> {
            int caretLine = getCaretLine();
            dispatcher.publish(new CaretMovedEvent(caretLine));
        });

    }

    public void setTextSilently(String content) {
        suppressChangeEvents = true;
        try {
            textArea.setText(content);
            resetUndoHistory(); // damit "Neu" auch wirklich "unverändert" ist
            originalContentHash = content != null ? content.hashCode() : 0;
        } finally {
            suppressChangeEvents = false;
        }
    }

    /**
     * Apply syntax highlighting based on file path/name.
     * This enables code highlighting for known file types without changing the actual content.
     *
     * @param filePath the file path or name to detect syntax from
     */
    public void applySyntaxHighlighting(String filePath) {
        String syntaxStyle = detectSyntaxStyle(filePath);
        textArea.setSyntaxEditingStyle(syntaxStyle);
    }

    /**
     * Detect RSyntaxTextArea syntax style based on file extension.
     * Returns SYNTAX_STYLE_NONE for unknown types.
     */
    private String detectSyntaxStyle(String path) {
        if (path == null) return SyntaxConstants.SYNTAX_STYLE_NONE;

        // Extract filename from path
        String name = path;
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            name = path.substring(lastSlash + 1);
        }

        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            String ext = name.substring(dotIndex + 1).toLowerCase();
            String style = SYNTAX_STYLES.get(ext);
            if (style != null) {
                return style;
            }
        }
        return SyntaxConstants.SYNTAX_STYLE_NONE;
    }

    /**
     * Get the current syntax style being used.
     */
    public String getCurrentSyntaxStyle() {
        return textArea.getSyntaxEditingStyle();
    }

    /**
     * Set syntax style directly.
     */
    public void setSyntaxStyle(String syntaxStyle) {
        textArea.setSyntaxEditingStyle(syntaxStyle != null ? syntaxStyle : SyntaxConstants.SYNTAX_STYLE_NONE);
    }
}
