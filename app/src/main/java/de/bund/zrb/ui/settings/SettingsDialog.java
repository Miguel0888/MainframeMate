package de.bund.zrb.ui.settings;

import de.bund.zrb.ftp.*;
import de.bund.zrb.model.*;
import de.bund.zrb.ui.components.ComboBoxHelper;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.util.ExecutableLauncher;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class SettingsDialog {

    private static JButton removeRowButton;
    private static JButton addRowButton;
    private static JPanel colorButtons;
    private static ColorOverrideTableModel colorModel;
    private static JCheckBox hexDumpBox;
    private static JComboBox<FtpTransferMode> modeBox;
    private static JComboBox<FtpFileStructure> structureBox;
    private static JComboBox<FtpTextFormat> formatBox;
    private static JComboBox<FtpFileType> typeBox;
    private static JButton openFolderButton;
    private static JCheckBox autoConnectBox;
    private static JCheckBox hideLoginBox;
    private static JCheckBox enableSound;
    private static JSpinner marginSpinner;
    private static JComboBox<String> paddingBox;
    private static JComboBox<String> endMarkerBox;
    private static JCheckBox stripFinalNewlineBox;
    private static JComboBox<String> lineEndingBox;
    private static JComboBox<Integer> fontSizeCombo;
    private static JComboBox<String> fontCombo;
    private static JComboBox<String> encodingCombo;
    private static Settings settings;
    private static JTable colorTable;
    private static JTextField ollamaUrlField;
    private static JTextField ollamaModelField;
    private static JTextField ollamaKeepAliveField;
    private static JCheckBox wrapJsonBox;
    private static JCheckBox prettyJsonBox;
    private static JTextField defaultWorkflow;

    private static JComboBox<String> aiEditorFontCombo;
    private static JComboBox<String> aiEditorFontSizeCombo;
    private static JSpinner aiEditorHeightSpinner;
    private static JTextArea aiToolPrefix;
    private static JTextArea aiToolPostfix;
    private static JComboBox<AiProvider> providerCombo;
    private static JCheckBox llamaEnabledBox;
    private static JTextField llamaBinaryField;
    private static JTextField llamaModelField;
    private static JSpinner llamaPortSpinner;
    private static JSpinner llamaThreadsSpinner;
    private static JSpinner llamaContextSpinner;
    private static JTextField llamaTempField;
    private static JCheckBox llamaStreamingBox;
    private static JSpinner importDelaySpinner;
    private static JList<String> supportedFileList;
    private static JSpinner lockDelay;
    private static JSpinner lockPre;
    private static JCheckBox enableLock;
    private static JCheckBox enableLockRetroStyle;
    private static JCheckBox compareByDefaultBox;

    public static void show(Component parent, FtpManager ftpManager) {
        JTabbedPane tabs = new JTabbedPane();

        JPanel generalContent = new JPanel(new GridBagLayout());
        JScrollPane generalPanel = new JScrollPane(generalContent);
        JPanel colorContent = new JPanel(new BorderLayout());
        JScrollPane colorPanel = new JScrollPane(colorContent);
        JPanel transformContent = new JPanel(new GridBagLayout());
        JScrollPane transformPanel = new JScrollPane(transformContent);
        JPanel connectContent = new JPanel(new GridBagLayout());
        JScrollPane connectPanel = new JScrollPane(connectContent);
        JPanel aiContent = new JPanel(new GridBagLayout());
        JScrollPane aiPanel = new JScrollPane(aiContent);

        tabs.addTab("Allgemein", generalPanel);
        tabs.addTab("Farbzuordnung", colorPanel);
        tabs.addTab("Datenumwandlung", transformPanel);
        tabs.addTab("FTP-Verbindung", connectPanel);
        tabs.add("KI", aiPanel);

        settings = SettingsHelper.load();

        createGeneralContent(generalContent, parent);
        createTransformContent(transformContent);
        createConnectContent(connectContent);
        createColorContent(colorContent, parent);
        createAiContent(aiContent);

        showAndApply(parent, ftpManager, tabs);
    }

    private static void createGeneralContent(JPanel generalContent, Component parent) {
        GridBagConstraints gbcGeneral = createDefaultGbc();

        // Schriftart-Auswahl
        gbcGeneral.gridwidth = 2;
        generalContent.add(new JLabel("Editor-Schriftart:"), gbcGeneral);
        gbcGeneral.gridy++;

        fontCombo = new JComboBox<>(new String[] {
                "Monospaced", "Consolas", "Courier New", "Menlo", "Dialog"
        });
        fontCombo.setSelectedItem(settings.editorFont);
        generalContent.add(fontCombo, gbcGeneral);
        gbcGeneral.gridy++;

        // Schriftgröße
        gbcGeneral.gridwidth = 2;
        generalContent.add(new JLabel("Editor-Schriftgröße:"), gbcGeneral);
        gbcGeneral.gridy++;
        fontSizeCombo = new JComboBox<>(new Integer[] {
                10, 11, 12, 13, 14, 16, 18, 20, 24, 28, 32, 36, 48, 72
        });
        fontSizeCombo.setEditable(true);
        fontSizeCombo.setSelectedItem(settings.editorFontSize);
        generalContent.add(fontSizeCombo, gbcGeneral);
        gbcGeneral.gridy++;

        // JSON-Formatierungsoptionen
        compareByDefaultBox = new JCheckBox("Vergleich automatisch einblenden");
        compareByDefaultBox.setSelected(settings.compareByDefault);
        generalContent.add(compareByDefaultBox, gbcGeneral);
        gbcGeneral.gridy++;

        // Marker-Linie (z. B. bei Spalte 80)
        gbcGeneral.gridwidth = 2;
        generalContent.add(new JLabel("Vertikale Markierung bei Spalte (0 = aus):"), gbcGeneral);
        gbcGeneral.gridy++;
        marginSpinner = new JSpinner(new SpinnerNumberModel(
                Math.max(0, settings.marginColumn),  // sicherstellen, dass 0 erlaubt ist
                0, 200, 1
        ));
        generalContent.add(marginSpinner, gbcGeneral);
        gbcGeneral.gridy++;
        gbcGeneral.gridwidth = 1;

        // Login-Dialog unterdrücken
        hideLoginBox = new JCheckBox("Login-Fenster verbergen (wenn Passwort gespeichert)");
        hideLoginBox.setSelected(settings.hideLoginDialog);
        generalContent.add(hideLoginBox, gbcGeneral);
        gbcGeneral.gridy++;

        // Login beim Start (new Session), falls Bookmarks nicht verwendet werden
        autoConnectBox = new JCheckBox("Automatisch verbinden (beim Start)");
        autoConnectBox.setSelected(settings.autoConnect);
        generalContent.add(autoConnectBox, gbcGeneral);
        gbcGeneral.gridy++;

        // Sounds abspielen
        enableSound = new JCheckBox("Sounds abspielen");
        enableSound.setSelected(settings.soundEnabled);
        generalContent.add(enableSound, gbcGeneral);
        gbcGeneral.gridy++;

        // Default Worklow
        gbcGeneral.gridwidth = 2;
        generalContent.add(new JLabel("Default Workflow:"), gbcGeneral);
        gbcGeneral.gridy++;
        defaultWorkflow = new JTextField("Standard Workflow:");
        defaultWorkflow.setText(settings.defaultWorkflow);
        generalContent.add(defaultWorkflow, gbcGeneral);
        gbcGeneral.gridy++;

        // Import-Verzögerung
        gbcGeneral.gridwidth = 2;
        generalContent.add(new JLabel("Import-Verzögerung (in Sekunden):"), gbcGeneral);
        gbcGeneral.gridy++;
        importDelaySpinner = new JSpinner(new SpinnerNumberModel(settings.importDelay, 0, 60, 1));
        generalContent.add(importDelaySpinner, gbcGeneral);
        gbcGeneral.gridy++;

        // Unterstützte Dateiendungen
        generalContent.add(new JLabel("Unterstützte Dateiendungen:"), gbcGeneral);
        gbcGeneral.gridy++;
        DefaultListModel<String> fileListModel = new DefaultListModel<>();
        for (String ext : settings.supportedFiles) fileListModel.addElement(ext);
        supportedFileList = new JList<>(fileListModel);
        supportedFileList.setVisibleRowCount(4);
        JScrollPane fileScrollPane = new JScrollPane(supportedFileList);
        generalContent.add(fileScrollPane, gbcGeneral);
        gbcGeneral.gridy++;

        // Buttons zum Bearbeiten der Liste
        JPanel fileButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addExtButton = new JButton("➕");
        JButton removeExtButton = new JButton("➖");

        addExtButton.addActionListener(e -> {
            String ext = JOptionPane.showInputDialog(parent, "Neue Dateiendung eingeben (mit Punkt):", ".xyz");
            if (ext != null && !ext.trim().isEmpty()) {
                fileListModel.addElement(ext.trim());
            }
        });

        removeExtButton.addActionListener(e -> {
            int index = supportedFileList.getSelectedIndex();
            if (index >= 0) {
                fileListModel.remove(index);
            }
        });

        fileButtonPanel.add(addExtButton);
        fileButtonPanel.add(removeExtButton);
        generalContent.add(fileButtonPanel, gbcGeneral);
        gbcGeneral.gridy++;

        // Screen Lock
        // Delay
        gbcGeneral.gridwidth = 2;
        generalContent.add(new JLabel("Bildschirmsperre nach (ms):"), gbcGeneral);
        gbcGeneral.gridy++;
        lockDelay = new JSpinner(new SpinnerNumberModel(settings.lockDelay, 0, Integer.MAX_VALUE, 100));
        generalContent.add(lockDelay, gbcGeneral);
        gbcGeneral.gridy++;

        // Pre-Notification
        gbcGeneral.gridwidth = 2;
        generalContent.add(new JLabel("Sperrankündigung (in ms):"), gbcGeneral);
        gbcGeneral.gridy++;
        lockPre = new JSpinner(new SpinnerNumberModel(settings.lockPrenotification, 0, Integer.MAX_VALUE, 100));
        generalContent.add(lockPre, gbcGeneral);
        gbcGeneral.gridy++;

        // Lock Disabled
        enableLock = new JCheckBox("Bildschirmsperre aktivieren");
        enableLock.setSelected(settings.lockEnabled);
        generalContent.add(enableLock, gbcGeneral);
        gbcGeneral.gridy++;

        // Lock Style
        enableLockRetroStyle = new JCheckBox("Retro-Sperrdesign");
        enableLockRetroStyle.setSelected(settings.lockRetro);
        generalContent.add(enableLockRetroStyle, gbcGeneral);
        gbcGeneral.gridy++;

        // User Profile Folder
        openFolderButton = new JButton("\uD83D\uDCC1");
        openFolderButton.setToolTipText("Einstellungsordner öffnen");
        openFolderButton.setMargin(new Insets(0, 5, 0, 5));
        openFolderButton.setFocusable(false);
        openFolderButton.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(SettingsHelper.getSettingsFolder());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(parent, "Ordner konnte nicht geöffnet werden:\n" + ex.getMessage());
            }
        });
        generalContent.add(openFolderButton, gbcGeneral);
        gbcGeneral.gridy++;
    }

    private static void createTransformContent(JPanel expertContent) {
        GridBagConstraints gbcTransform = createDefaultGbc();

        // Zeichensatz-Auswahl
        encodingCombo = new JComboBox<>();
        List<String> encodings = SettingsHelper.SUPPORTED_ENCODINGS;
        encodings.forEach(encodingCombo::addItem);
        String currentEncoding = settings.encoding != null ? settings.encoding : "windows-1252";
        encodingCombo.setSelectedItem(currentEncoding);
        addEncodingSelector(expertContent, gbcTransform, encodingCombo);

        // Zeilenumbruch
        expertContent.add(new JLabel("Zeilenumbruch des Servers:"), gbcTransform);
        gbcTransform.gridy++;
        lineEndingBox = LineEndingOption.createLineEndingComboBox(settings.lineEnding);
        expertContent.add(lineEndingBox, gbcTransform);
        gbcTransform.gridy++;

        stripFinalNewlineBox = new JCheckBox("Letzten Zeilenumbruch ausblenden (falls vorhanden)");
        stripFinalNewlineBox.setSelected(settings.removeFinalNewline);
        expertContent.add(stripFinalNewlineBox, gbcTransform);
        gbcTransform.gridy++;

        // Dateiende
        expertContent.add(new JLabel("Datei-Ende-Kennung (z. B. FF02, leer = aus):"), gbcTransform);
        gbcTransform.gridy++;
        endMarkerBox = FileEndingOption.createEndMarkerComboBox(settings.fileEndMarker);
        expertContent.add(endMarkerBox, gbcTransform);
        gbcTransform.gridy++;

        // Padding
        expertContent.add(new JLabel("Padding Byte (z. B. 00, leer = aus):"), gbcTransform);
        gbcTransform.gridy++;
        paddingBox = PaddingOption.createPaddingComboBox(settings.padding);
        expertContent.add(paddingBox, gbcTransform);
        gbcTransform.gridy++;
    }

    private static void createConnectContent(JPanel expertContent) {
        GridBagConstraints gbcConnect = createDefaultGbc();

        // FTP-Transferoptionen (TYPE, FORMAT, STRUCTURE, MODE)
        expertContent.add(new JLabel("FTP Datei-Typ (TYPE):"), gbcConnect);
        gbcConnect.gridy++;
        typeBox = ComboBoxHelper.createComboBoxWithNullOption(
                FtpFileType.class, settings.ftpFileType, "Standard"
        );
        expertContent.add(typeBox, gbcConnect);
        gbcConnect.gridy++;

        expertContent.add(new JLabel("FTP Text-Format (FORMAT):"), gbcConnect);
        gbcConnect.gridy++;
        formatBox = ComboBoxHelper.createComboBoxWithNullOption(
                FtpTextFormat.class, settings.ftpTextFormat, "Standard"
        );
        expertContent.add(formatBox, gbcConnect);
        gbcConnect.gridy++;

        expertContent.add(new JLabel("FTP Dateistruktur (STRUCTURE):"), gbcConnect);
        gbcConnect.gridy++;
        structureBox = ComboBoxHelper.createComboBoxWithNullOption(
                FtpFileStructure.class, settings.ftpFileStructure, "Automatisch"
        );
        expertContent.add(structureBox, gbcConnect);
        gbcConnect.gridy++;

        expertContent.add(new JLabel("FTP Übertragungsmodus (MODE):"), gbcConnect);
        gbcConnect.gridy++;
        modeBox = ComboBoxHelper.createComboBoxWithNullOption(
                FtpTransferMode.class, settings.ftpTransferMode, "Standard"
        );
        expertContent.add(modeBox, gbcConnect);
        gbcConnect.gridy++;

        hexDumpBox = new JCheckBox("Hexdump in Konsole anzeigen (Debugzwecke)");
        hexDumpBox.setSelected(settings.enableHexDump);
        expertContent.add(hexDumpBox, gbcConnect);
        gbcConnect.gridy++;
    }

    private static void createColorContent(JPanel colorContent, Component parent) {
        // Farbüberschreibungen für Feldnamen
        colorContent.add(new JLabel("Farbüberschreibungen für Feldnamen:"), BorderLayout.NORTH);

        colorModel = new ColorOverrideTableModel(settings.fieldColorOverrides);
        colorTable = new JTable(colorModel);
//        colorTable.setPreferredScrollableViewportSize(new Dimension(400, 150));
//        colorTable.setRowHeight(22);
        colorTable.getColumnModel().getColumn(1).setCellEditor(new ColorCellEditor());
        colorTable.getColumnModel().getColumn(1).setCellRenderer((table, value, isSelected, hasFocus, row, col) -> {
            JLabel label = new JLabel(value != null ? value.toString() : "");
            label.setOpaque(true);
            label.setBackground(parseHexColor(String.valueOf(value), Color.WHITE));
            return label;
        });
        colorTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    // open color chooser does not work cause of missing double click event
                }
                else {
                    int row = colorTable.rowAtPoint(e.getPoint());
                    int column = colorTable.columnAtPoint(e.getPoint());
                    if (column == 1) {
                        colorTable.editCellAt(row, column);
                    }
                }
            }
        });

        colorTable.setFillsViewportHeight(true);
        colorTable.setPreferredScrollableViewportSize(new Dimension(300, 100));

        colorButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addRowButton = new JButton("➕");
        addRowButton.addActionListener(e -> {
            String key = JOptionPane.showInputDialog(parent, "Feldname eingeben:");
            if (key != null && !key.trim().isEmpty()) {
                colorModel.addEntry(key.trim().toUpperCase(), "#00AA00");
            }
        });
        removeRowButton = new JButton("➖");
        removeRowButton.addActionListener(e -> {
            int selected = colorTable.getSelectedRow();
            if (selected >= 0) {
                colorModel.removeEntry(selected);
            }
        });

        colorButtons.add(addRowButton);
        colorButtons.add(removeRowButton);

        JPanel innerColorPanel = new JPanel(new BorderLayout());
        innerColorPanel.add(new JScrollPane(colorTable), BorderLayout.CENTER);
        innerColorPanel.add(colorButtons, BorderLayout.SOUTH);

        colorContent.add(innerColorPanel, BorderLayout.CENTER);
        colorContent.add(colorButtons, BorderLayout.SOUTH);
    }

    private static void createAiContent(JPanel aiContent) {
        GridBagConstraints gbc = createDefaultGbc();

        // Tool-Prefix
        aiContent.add(new JLabel("KI-Anweisung vor dem JSON-Format (Prefix):"), gbc);
        gbc.gridy++;
        aiToolPrefix = new JTextArea(3, 30);
        aiToolPrefix.setLineWrap(true);
        aiToolPrefix.setWrapStyleWord(true);
        aiToolPrefix.setText(settings.aiConfig.getOrDefault("toolPrefix", "Gib den JSON-Aufruf (nur den Tool Call) aus – ohne zusätzliche Beschreibung oder Wiederholung der Tool-Spezifikation.\n"));
        aiContent.add(new JScrollPane(aiToolPrefix), gbc);
        gbc.gridy++;

        // Tool-Postfix
        aiContent.add(new JLabel("KI-Anweisung nach dem JSON-Format (Postfix):"), gbc);
        gbc.gridy++;
        aiToolPostfix = new JTextArea(2, 30);
        aiToolPostfix.setLineWrap(true);
        aiToolPostfix.setWrapStyleWord(true);
        aiToolPostfix.setText(settings.aiConfig.getOrDefault("toolPostfix", ""));
        aiContent.add(new JScrollPane(aiToolPostfix), gbc);
        gbc.gridy++;

        // Editor-Schriftart
        aiContent.add(new JLabel("KI-Editor Schriftart:"), gbc);
        gbc.gridy++;
        aiEditorFontCombo = new JComboBox<>(new String[] {
                "Monospaced", "Consolas", "Courier New", "Dialog", "Menlo"
        });
        aiEditorFontCombo.setSelectedItem(settings.aiConfig.getOrDefault("editor.font", "Monospaced"));
        aiContent.add(aiEditorFontCombo, gbc);
        gbc.gridy++;

        // Schriftgröße
        aiContent.add(new JLabel("KI-Editor Schriftgröße:"), gbc);
        gbc.gridy++;
        aiEditorFontSizeCombo = new JComboBox<>(new String[] {
                "10", "11", "12", "13", "14", "16", "18", "20", "24", "28", "32"
        });
        aiEditorFontSizeCombo.setEditable(true);
        aiEditorFontSizeCombo.setSelectedItem(settings.aiConfig.getOrDefault("editor.fontSize", "12"));
        aiContent.add(aiEditorFontSizeCombo, gbc);
        gbc.gridy++;

        // Editorhöhe
        aiContent.add(new JLabel("Editor-Höhe (in Zeilen):"), gbc);
        gbc.gridy++;
        aiEditorHeightSpinner = new JSpinner(new SpinnerNumberModel(
                Integer.parseInt(settings.aiConfig.getOrDefault("editor.lines", "3")), 1, 1000, 1
        ));
        aiContent.add(aiEditorHeightSpinner, gbc);
        gbc.gridy++;

        // JSON-Formatierungsoptionen
        wrapJsonBox = new JCheckBox("JSON als Markdown-Codeblock einrahmen");
        wrapJsonBox.setSelected(Boolean.parseBoolean(settings.aiConfig.getOrDefault("wrapjson", "true")));
        aiContent.add(wrapJsonBox, gbc);
        gbc.gridy++;

        prettyJsonBox = new JCheckBox("JSON schön formatieren (Pretty-Print)");
        prettyJsonBox.setSelected(Boolean.parseBoolean(settings.aiConfig.getOrDefault("prettyjson", "true")));
        aiContent.add(prettyJsonBox, gbc);
        gbc.gridy++;

        providerCombo = new JComboBox<>(AiProvider.values());
        aiContent.add(new JLabel("KI-Provider:"), gbc);
        gbc.gridy++;
        aiContent.add(providerCombo, gbc);
        gbc.gridy++;

        JPanel providerOptionsPanel = new JPanel(new CardLayout());
        aiContent.add(providerOptionsPanel, gbc);
        gbc.gridy++;

        // Panels für Provider
        JPanel disabledPanel = new JPanel();
        JPanel ollamaPanel = new JPanel(new GridBagLayout());
        JPanel localAiPanel = new JPanel(new GridBagLayout());

        providerOptionsPanel.add(disabledPanel, AiProvider.DISABLED.name());
        providerOptionsPanel.add(ollamaPanel, AiProvider.OLLAMA.name());
        providerOptionsPanel.add(localAiPanel, AiProvider.LOCAL_AI.name());

        // OLLAMA-Felder
        GridBagConstraints gbcOllama = createDefaultGbc();
        ollamaPanel.add(new JLabel("OLLAMA URL:"), gbcOllama);
        gbcOllama.gridy++;
        ollamaUrlField = new JTextField(30);
        ollamaPanel.add(ollamaUrlField, gbcOllama);
        gbcOllama.gridy++;
        ollamaPanel.add(new JLabel("Modellname:"), gbcOllama);
        gbcOllama.gridy++;
        ollamaModelField = new JTextField(20);
        ollamaPanel.add(ollamaModelField, gbcOllama);
        gbcOllama.gridy++;
        ollamaPanel.add(new JLabel("Modell beibehalten für (z. B. 30m, 0, -1):"), gbcOllama);
        gbcOllama.gridy++;
        ollamaKeepAliveField = new JTextField(20);
        ollamaPanel.add(ollamaKeepAliveField, gbcOllama);
        gbcOllama.gridy++;

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        // LOCAL_AI-Felder (optional, hier nur ein Hinweistext)
        GridBagConstraints gbcLocal = createDefaultGbc();
        localAiPanel.add(new JLabel("Konfiguration für LocalAI folgt."), gbcLocal);

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        // LLAMA_CPP_SERVER-Felder
        JPanel llamaCppServerPanel = new JPanel(new GridBagLayout());
        providerOptionsPanel.add(llamaCppServerPanel, AiProvider.LLAMA_CPP_SERVER.name());
        GridBagConstraints gbcLlama = createDefaultGbc();

        llamaStreamingBox = new JCheckBox("Streaming aktiviert");
        llamaStreamingBox.setSelected(Boolean.parseBoolean(settings.aiConfig.getOrDefault("llama.streaming", "true")));
        llamaCppServerPanel.add(llamaStreamingBox, gbcLlama);
        gbcLlama.gridx = 0;
        gbcLlama.gridy++;

        llamaEnabledBox = new JCheckBox("llama.cpp Server beim Start starten");
        llamaEnabledBox.setSelected(Boolean.parseBoolean(settings.aiConfig.getOrDefault("llama.enabled", "false")));
        llamaCppServerPanel.add(llamaEnabledBox, gbcLlama);
        gbcLlama.gridy++;

        llamaCppServerPanel.add(new JLabel("Pfad zur llama-server Binary:"), gbcLlama);
        gbcLlama.gridy++;
        llamaBinaryField = new JTextField(settings.aiConfig.getOrDefault("llama.binary", "C:/llamacpp/llama-server"), 30);
        llamaCppServerPanel.add(llamaBinaryField, gbcLlama);
        gbcLlama.gridy++;

        JButton extractDriverButton = new JButton("🔄 Entpacken, falls fehlt");
        llamaCppServerPanel.add(extractDriverButton, gbcLlama);
        gbcLlama.gridy++;

        extractDriverButton.addActionListener(e -> {
            String path = llamaBinaryField.getText().trim();
            if (path.isEmpty()) {
                JOptionPane.showMessageDialog(aiContent, "Bitte gib den Zielpfad an.", "Pfad fehlt", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Vorschlagswert für den Hash (aus Launcher-Klasse oder config)
            final String defaultHash = ExecutableLauncher.getHash();

            String inputHash = (String) JOptionPane.showInputDialog(
                    aiContent,
                    "Gib den erwarteten SHA-256-Hash der Binary an:",
                    "Hashprüfung vor Entpacken",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    defaultHash
            );

            if (inputHash == null || inputHash.trim().isEmpty()) {
                JOptionPane.showMessageDialog(aiContent, "Hashprüfung abgebrochen – keine Datei entpackt.", "Abbruch", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            File target = new File(path);
            ExecutableLauncher launcher = new ExecutableLauncher();
            try {
                launcher.extractTo(target, inputHash.trim());
                JOptionPane.showMessageDialog(aiContent, "Binary wurde erfolgreich extrahiert und verifiziert:\n" + path, "Erfolg", JOptionPane.INFORMATION_MESSAGE);
            } catch (SecurityException se) {
                JOptionPane.showMessageDialog(aiContent, "Hash stimmt nicht:\n" + se.getMessage(), "Sicherheitswarnung", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(aiContent, "Fehler beim Extrahieren:\n" + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        });



        llamaCppServerPanel.add(new JLabel("Modellpfad (.gguf):"), gbcLlama);
        gbcLlama.gridy++;
        llamaModelField = new JTextField(settings.aiConfig.getOrDefault("llama.model", "models/mistral.gguf"), 30);
        llamaCppServerPanel.add(llamaModelField, gbcLlama);
        gbcLlama.gridy++;

        llamaCppServerPanel.add(new JLabel("Port (z. B. 8080):"), gbcLlama);
        gbcLlama.gridy++;
        llamaPortSpinner = new JSpinner(new SpinnerNumberModel(
                Integer.parseInt(settings.aiConfig.getOrDefault("llama.port", "8080")),
                1024, 65535, 1));
        llamaCppServerPanel.add(llamaPortSpinner, gbcLlama);
        gbcLlama.gridy++;

        llamaCppServerPanel.add(new JLabel("Threads (z. B. 6):"), gbcLlama);
        gbcLlama.gridy++;
        llamaThreadsSpinner = new JSpinner(new SpinnerNumberModel(
                Integer.parseInt(settings.aiConfig.getOrDefault("llama.threads", "4")),
                1, 64, 1));
        llamaCppServerPanel.add(llamaThreadsSpinner, gbcLlama);
        gbcLlama.gridy++;

        llamaCppServerPanel.add(new JLabel("Kontextgröße (Tokens):"), gbcLlama);
        gbcLlama.gridy++;
        llamaContextSpinner = new JSpinner(new SpinnerNumberModel(
                Integer.parseInt(settings.aiConfig.getOrDefault("llama.context", "2048")),
                512, 8192, 64));
        llamaCppServerPanel.add(llamaContextSpinner, gbcLlama);
        gbcLlama.gridy++;

        llamaCppServerPanel.add(new JLabel("Temperatur (z. B. 0.7):"), gbcLlama);
        gbcLlama.gridy++;
        llamaTempField = new JTextField(settings.aiConfig.getOrDefault("llama.temp", "0.7"), 5);
        llamaCppServerPanel.add(llamaTempField, gbcLlama);
        gbcLlama.gridy++;

        // Dynamisches Deaktivieren bei "Server starten"
        List<Component> llamaConfigFields = Arrays.asList(
                llamaBinaryField, llamaModelField, llamaPortSpinner,
                llamaThreadsSpinner, llamaContextSpinner, llamaTempField
        );
        llamaEnabledBox.addActionListener(e -> {
            boolean enabled = llamaEnabledBox.isSelected();
            for (Component comp : llamaConfigFields) {
                comp.setEnabled(enabled);
            }
        });
        boolean initiallyEnabled = llamaEnabledBox.isSelected();
        for (Component comp : llamaConfigFields) {
            comp.setEnabled(initiallyEnabled);
        }

        // Initiale Werte aus Settings
        String providerName = settings.aiConfig.getOrDefault("provider", "DISABLED");
        AiProvider selectedProvider = AiProvider.valueOf(providerName);
        providerCombo.setSelectedItem(selectedProvider);

        ollamaUrlField.setText(settings.aiConfig.getOrDefault("ollama.url", "http://localhost:11434/api/generate"));
        ollamaModelField.setText(settings.aiConfig.getOrDefault("ollama.model", "custom-modell"));
        ollamaKeepAliveField.setText(settings.aiConfig.getOrDefault("ollama.keepalive", "10m"));

        // Umschalten je nach Provider
        providerCombo.addActionListener(e -> {
            AiProvider selected = (AiProvider) providerCombo.getSelectedItem();
            CardLayout cl = (CardLayout) providerOptionsPanel.getLayout();
            cl.show(providerOptionsPanel, selected.name());
        });

        ((CardLayout) providerOptionsPanel.getLayout()).show(providerOptionsPanel, selectedProvider.name());
    }


    private static void showAndApply(Component parent, FtpManager ftpManager, JTabbedPane tabs) {
        // Dialog formatieren
        JPanel container = new JPanel(new BorderLayout());
        container.add(tabs, BorderLayout.CENTER);

        // Bildschirmhöhe holen
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int height = (int) (screenSize.height * 0.8);  // 80% der Bildschirmhöhe
        int width = 600;  // feste Breite

        container.setPreferredSize(new Dimension(width, height));

        // Dialog anzeigen
        int result = JOptionPane.showConfirmDialog(parent, container, "Einstellungen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            settings.encoding = (String) encodingCombo.getSelectedItem();
            settings.editorFont = (String) fontCombo.getSelectedItem();
            settings.editorFontSize = Optional.ofNullable(fontSizeCombo.getEditor().getItem())
                    .map(Object::toString)
                    .map(s -> {
                        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
                    })
                    .orElse(12);
            settings.compareByDefault = compareByDefaultBox.isSelected();
            settings.lineEnding = LineEndingOption.normalizeInput(lineEndingBox.getSelectedItem());
            settings.removeFinalNewline = stripFinalNewlineBox.isSelected();
            settings.fileEndMarker = FileEndingOption.normalizeInput(endMarkerBox.getSelectedItem());
            settings.padding = PaddingOption.normalizeInput(paddingBox.getSelectedItem());
            settings.marginColumn = (Integer) marginSpinner.getValue();
            settings.hideLoginDialog = hideLoginBox.isSelected();
            settings.soundEnabled = enableSound.isSelected();
            settings.autoConnect = autoConnectBox.isSelected();
            settings.ftpFileType = ComboBoxHelper.getSelectedEnumValue(typeBox, FtpFileType.class);
            settings.ftpTextFormat = ComboBoxHelper.getSelectedEnumValue(formatBox, FtpTextFormat.class);
            settings.ftpFileStructure = ComboBoxHelper.getSelectedEnumValue(structureBox, FtpFileStructure.class);
            settings.ftpTransferMode = ComboBoxHelper.getSelectedEnumValue(modeBox, FtpTransferMode.class);
            settings.enableHexDump = hexDumpBox.isSelected();
            settings.defaultWorkflow = defaultWorkflow.getText();
            settings.importDelay = (Integer) importDelaySpinner.getValue();
            DefaultListModel<String> model = (DefaultListModel<String>) supportedFileList.getModel();
            List<String> extensions = new ArrayList<>();
            for (int i = 0; i < model.size(); i++) {
                extensions.add(model.get(i));
            }
            settings.supportedFiles = extensions;
            settings.lockDelay = (Integer) lockDelay.getValue();
            settings.lockPrenotification = (Integer) lockPre.getValue();
            settings.lockEnabled = enableLock.isSelected();
            settings.lockRetro = enableLockRetroStyle.isSelected();

            settings.aiConfig.put("editor.font", aiEditorFontCombo.getSelectedItem().toString());
            settings.aiConfig.put("editor.fontSize", aiEditorFontSizeCombo.getSelectedItem().toString());
            settings.aiConfig.put("editor.lines", aiEditorHeightSpinner.getValue().toString());
            settings.aiConfig.put("toolPrefix", aiToolPrefix.getText().trim()); // default: "Gib den Aufruf als JSON im folgenden Format aus:\n"
            settings.aiConfig.put("toolPostfix", aiToolPostfix.getText().trim()); // default empty
            settings.aiConfig.put("provider", providerCombo.getSelectedItem().toString());
            settings.aiConfig.put("ollama.url", ollamaUrlField.getText().trim());
            settings.aiConfig.put("ollama.model", ollamaModelField.getText().trim());
            settings.aiConfig.put("ollama.keepalive", ollamaKeepAliveField.getText().trim());
            settings.aiConfig.put("llama.enabled", String.valueOf(llamaEnabledBox.isSelected()));
            settings.aiConfig.put("llama.binary", llamaBinaryField.getText().trim());
            settings.aiConfig.put("llama.model", llamaModelField.getText().trim());
            settings.aiConfig.put("llama.port", llamaPortSpinner.getValue().toString());
            settings.aiConfig.put("llama.threads", llamaThreadsSpinner.getValue().toString());
            settings.aiConfig.put("llama.context", llamaContextSpinner.getValue().toString());
            settings.aiConfig.put("llama.temp", llamaTempField.getText().trim());
            settings.aiConfig.put("llama.streaming", String.valueOf(llamaStreamingBox.isSelected()));
            settings.aiConfig.put("wrapjson", String.valueOf(wrapJsonBox.isSelected()));
            settings.aiConfig.put("prettyjson", String.valueOf(prettyJsonBox.isSelected()));

            SettingsHelper.save(settings);

            ftpManager.getClient().setControlEncoding(settings.encoding);

//            JOptionPane.showMessageDialog(parent,
//                    "Einstellungen wurden gespeichert.",
//                    "Info", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static GridBagConstraints createDefaultGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        return gbc;
    }

    private static void addEncodingSelector(JPanel panel, GridBagConstraints gbc, JComboBox<String> encodingCombo) {
        gbc.gridwidth = 2;
        panel.add(new JLabel("Zeichenkodierung:"), gbc);
        gbc.gridy++;
        panel.add(encodingCombo, gbc);
        gbc.gridy++;
        gbc.gridwidth = 1;
    }

    private static Color parseHexColor(String hex, Color fallback) {
        if (hex == null) return fallback;
        try {
            return Color.decode(hex);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }


    private static class ColorOverrideTableModel extends AbstractTableModel {
        private final java.util.List<String> keys;
        private final Map<String, String> colorMap;

        public ColorOverrideTableModel(Map<String, String> colorMap) {
            this.colorMap = colorMap;
            this.keys = new ArrayList<>(colorMap.keySet());
        }

        @Override
        public int getRowCount() {
            return keys.size();
        }

        @Override
        public int getColumnCount() {
            return 2; // name + farbe
        }

        @Override
        public String getColumnName(int col) {
            return col == 0 ? "Feldname" : "Farbe (Hex)";
        }

        @Override
        public Object getValueAt(int row, int col) {
            String key = keys.get(row);
            return col == 0 ? key : colorMap.get(key);
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col == 1) {
                colorMap.put(keys.get(row), value.toString());
            }
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col == 1;
        }

        public void addEntry(String name, String color) {
            colorMap.put(name, color);
            keys.add(name);
            fireTableDataChanged();
        }

        public void removeEntry(int rowIndex) {
            if (rowIndex >= 0 && rowIndex < keys.size()) {
                colorMap.remove(keys.get(rowIndex));
                keys.remove(rowIndex);
                fireTableDataChanged();
            }
        }
    }

    private static class ColorCellEditor extends AbstractCellEditor implements TableCellEditor {

        private final JButton button = new JButton();
        private String currentColorHex;

        public ColorCellEditor() {
            button.addActionListener(e -> {
                Color initialColor = parseHexColor(currentColorHex, Color.BLACK);
                Color selectedColor = JColorChooser.showDialog(button, "Farbe wählen", initialColor);
                if (selectedColor != null) {
                    currentColorHex = "#" + Integer.toHexString(selectedColor.getRGB()).substring(2).toUpperCase();
                    button.setBackground(selectedColor);
                    fireEditingStopped();
                }
            });
        }

        @Override
        public Object getCellEditorValue() {
            return currentColorHex;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
                                                     int row, int column) {
            currentColorHex = String.valueOf(value);
            button.setBackground(parseHexColor(currentColorHex, Color.BLACK));
            return button;
        }

        private Color parseHexColor(String hex, Color fallback) {
            try {
                return Color.decode(hex);
            } catch (Exception e) {
                return fallback;
            }
        }
    }


}
