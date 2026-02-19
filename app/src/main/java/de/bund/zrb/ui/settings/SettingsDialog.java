package de.bund.zrb.ui.settings;

import de.bund.zrb.files.ftpconfig.FtpFileStructure;
import de.bund.zrb.files.ftpconfig.FtpFileType;
import de.bund.zrb.files.ftpconfig.FtpTextFormat;
import de.bund.zrb.files.ftpconfig.FtpTransferMode;
import de.bund.zrb.model.*;
import de.bund.zrb.ui.components.ChatMode;
import de.bund.zrb.ui.components.ComboBoxHelper;
import de.bund.zrb.ui.components.HelpButton;
import de.bund.zrb.ui.components.TabbedPaneWithHelpOverlay;
import de.bund.zrb.ui.help.HelpContentProvider;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.ui.lock.LockerStyle;
import de.bund.zrb.util.ExecutableLauncher;
import de.bund.zrb.util.WindowsCryptoUtil;
import de.bund.zrb.net.ProxyDefaults;
import de.bund.zrb.net.ProxyResolver;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

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
    private static JSpinner ftpConnectTimeoutSpinner;
    private static JSpinner ftpControlTimeoutSpinner;
    private static JSpinner ftpDataTimeoutSpinner;
    private static JButton openFolderButton;
    private static JCheckBox autoConnectBox;
    private static JCheckBox savePasswordBox;
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
    private static JComboBox<String> cloudProviderField;
    private static JTextField cloudApiKeyField;
    private static JTextField cloudApiUrlField;
    private static JTextField cloudModelField;
    private static JTextField cloudAuthHeaderField;
    private static JTextField cloudAuthPrefixField;
    private static JTextField cloudApiVersionField;
    private static JTextField cloudOrgField;
    private static JTextField cloudProjectField;
    private static JCheckBox wrapJsonBox;
    private static JCheckBox prettyJsonBox;
    private static JTextField defaultWorkflow;
    private static JSpinner workflowTimeoutSpinner;

    private static JComboBox<String> aiEditorFontCombo;
    private static JComboBox<String> aiEditorFontSizeCombo;
    private static JSpinner aiEditorHeightSpinner;
    private static JTextArea aiToolPrefix;
    private static JTextArea aiToolPostfix;
    private static JComboBox<ChatMode> aiModeCombo;
    private static JButton aiResetToolContractButton;
    private static JComboBox<String> aiLanguageCombo;
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
    private static JCheckBox showHelpIconsBox;
    private static JComboBox<LockerStyle> lockStyleBox;

    private static JTextField hostField;
    private static JTextField userField;
    private static JPasswordField passwordField;
    private static JButton clearPasswordButton;
    private static boolean clearPasswordRequested;

    private static JCheckBox proxyEnabledBox;
    private static JComboBox<String> proxyModeBox;
    private static JTextField proxyHostField;
    private static JSpinner proxyPortSpinner;
    private static JCheckBox proxyNoProxyLocalBox;
    private static RSyntaxTextArea proxyPacScriptArea;
    private static JTextField proxyTestUrlField;
    private static JButton proxyTestButton;
    private static RagSettingsPanel ragSettingsPanel;

    public static void show(Component parent) {
        show(parent, 0);
    }

    public static void show(Component parent, int initialTabIndex) {
        // Allow calling settings without an active FTP manager.
        // Any actions requiring a live connection must be disabled/guarded.

        TabbedPaneWithHelpOverlay tabs = new TabbedPaneWithHelpOverlay();

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
        ragSettingsPanel = new RagSettingsPanel();
        JPanel proxyContent = new JPanel(new GridBagLayout());
        JScrollPane proxyPanel = new JScrollPane(proxyContent);

        tabs.addTab("Allgemein", generalPanel);
        tabs.addTab("Farbzuordnung", colorPanel);
        tabs.addTab("Datenumwandlung", transformPanel);
        tabs.addTab("FTP-Verbindung", connectPanel);
        tabs.addTab("KI", aiPanel);
        tabs.addTab("RAG", ragSettingsPanel);
        tabs.addTab("Proxy", proxyPanel);

        // Hilfe-Button mit kontextsensitiver Hilfe je nach ausgewähltem Tab
        HelpButton helpButton = new HelpButton("Hilfe zu Einstellungen");
        helpButton.addActionListener(e -> {
            int selectedTab = tabs.getSelectedIndex();
            HelpContentProvider.HelpTopic topic;
            switch (selectedTab) {
                case 0: topic = HelpContentProvider.HelpTopic.SETTINGS_GENERAL; break;
                case 1: topic = HelpContentProvider.HelpTopic.SETTINGS_COLORS; break;
                case 2: topic = HelpContentProvider.HelpTopic.SETTINGS_TRANSFORM; break;
                case 3: topic = HelpContentProvider.HelpTopic.SETTINGS_FTP; break;
                case 4: topic = HelpContentProvider.HelpTopic.SETTINGS_AI; break;
                case 5: topic = HelpContentProvider.HelpTopic.SETTINGS_RAG; break;
                case 6: topic = HelpContentProvider.HelpTopic.SETTINGS_PROXY; break;
                default: topic = HelpContentProvider.HelpTopic.SETTINGS_GENERAL;
            }
            HelpContentProvider.showHelpPopup((Component) e.getSource(), topic);
        });
        tabs.setHelpComponent(helpButton);

        if (initialTabIndex >= 0 && initialTabIndex < tabs.getTabCount()) {
            tabs.setSelectedIndex(initialTabIndex);
        }

        settings = SettingsHelper.load();
        clearPasswordRequested = false;

        createGeneralContent(generalContent, parent);
        createTransformContent(transformContent);
        createConnectContent(connectContent);
        createColorContent(colorContent, parent);
        createAiContent(aiContent);
        createProxyContent(proxyContent, parent);

        showAndApply(parent, tabs);
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

        // Hilfe-Icons anzeigen
        showHelpIconsBox = new JCheckBox("Hilfe-Icons anzeigen");
        showHelpIconsBox.setSelected(settings.showHelpIcons);
        showHelpIconsBox.setToolTipText("Deaktivieren für erfahrene Benutzer");
        generalContent.add(showHelpIconsBox, gbcGeneral);
        gbcGeneral.gridy++;

        // Marker-Linie (z. B. bei Spalte 80)
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

        // Login beim Start (new Session), falls Bookmarks nicht verwendet werden
        autoConnectBox = new JCheckBox("Passwort nur einmal eingeben");
        autoConnectBox.setSelected(settings.autoConnect);
        generalContent.add(autoConnectBox, gbcGeneral);
        gbcGeneral.gridy++;

        // Login-Dialog unterdrücken
        savePasswordBox = new JCheckBox("Passwort verschlüsselt speichern");
        savePasswordBox.setSelected(settings.savePassword);
        generalContent.add(savePasswordBox, gbcGeneral);
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

        // Workflow-Timeout
        gbcGeneral.gridwidth = 2;
        generalContent.add(new JLabel("Workflow Timeout (in ms):"), gbcGeneral);
        gbcGeneral.gridy++;
        workflowTimeoutSpinner = new JSpinner(new SpinnerNumberModel(settings.workflowTimeout, 100, 300_000, 500));
        generalContent.add(workflowTimeoutSpinner, gbcGeneral);
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
        generalContent.add(new JLabel("Design der Bildschirmsperre:"), gbcGeneral);
        gbcGeneral.gridy++;

        lockStyleBox = new JComboBox<>(LockerStyle.values());
        lockStyleBox.setSelectedIndex(Math.max(0, Math.min(LockerStyle.values().length - 1, settings.lockStyle)));
        generalContent.add(lockStyleBox, gbcGeneral);
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

        // Zeichensatz-Auswahl mit Info-Icon neben dem Dropdown
        expertContent.add(new JLabel("Zeichenkodierung:"), gbcTransform);
        gbcTransform.gridy++;
        encodingCombo = new JComboBox<>();
        List<String> encodings = SettingsHelper.SUPPORTED_ENCODINGS;
        encodings.forEach(encodingCombo::addItem);
        String currentEncoding = settings.encoding != null ? settings.encoding : "windows-1252";
        encodingCombo.setSelectedItem(currentEncoding);
        expertContent.add(createFieldWithHelpIcon(encodingCombo,
                HelpContentProvider.HelpTopic.TRANSFORM_ENCODING), gbcTransform);
        gbcTransform.gridy++;

        // Zeilenumbruch mit Info-Icon neben dem Dropdown
        expertContent.add(new JLabel("Zeilenumbruch des Servers:"), gbcTransform);
        gbcTransform.gridy++;
        lineEndingBox = LineEndingOption.createLineEndingComboBox(settings.lineEnding);
        expertContent.add(createFieldWithHelpIcon(lineEndingBox,
                HelpContentProvider.HelpTopic.TRANSFORM_LINE_ENDING), gbcTransform);
        gbcTransform.gridy++;

        // Checkbox mit Info-Icon rechts daneben
        stripFinalNewlineBox = new JCheckBox("Letzten Zeilenumbruch ausblenden");
        stripFinalNewlineBox.setSelected(settings.removeFinalNewline);
        expertContent.add(createFieldWithHelpIcon(stripFinalNewlineBox,
                HelpContentProvider.HelpTopic.TRANSFORM_STRIP_NEWLINE), gbcTransform);
        gbcTransform.gridy++;

        // Dateiende mit Info-Icon neben dem Dropdown
        expertContent.add(new JLabel("Datei-Ende-Kennung (z. B. FF02, leer = aus):"), gbcTransform);
        gbcTransform.gridy++;
        endMarkerBox = FileEndingOption.createEndMarkerComboBox(settings.fileEndMarker);
        expertContent.add(createFieldWithHelpIcon(endMarkerBox,
                HelpContentProvider.HelpTopic.TRANSFORM_EOF_MARKER), gbcTransform);
        gbcTransform.gridy++;

        // Padding mit Info-Icon neben dem Dropdown
        expertContent.add(new JLabel("Padding Byte (z. B. 00, leer = aus):"), gbcTransform);
        gbcTransform.gridy++;
        paddingBox = PaddingOption.createPaddingComboBox(settings.padding);
        expertContent.add(createFieldWithHelpIcon(paddingBox,
                HelpContentProvider.HelpTopic.TRANSFORM_PADDING), gbcTransform);
        gbcTransform.gridy++;
    }

    /**
     * Erstellt ein Panel mit dem Eingabefeld und einem Info-Icon rechts daneben.
     */
    private static JPanel createFieldWithHelpIcon(JComponent field, HelpContentProvider.HelpTopic helpTopic) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panel.add(field);
        panel.add(Box.createHorizontalStrut(5));
        HelpButton infoBtn = createInfoHelpButton(helpTopic);
        panel.add(infoBtn);
        return panel;
    }

    /**
     * Erstellt ein Label mit Info-Icon für technische Hilfe.
     * @deprecated Verwende createFieldWithHelpIcon() stattdessen
     */
    @Deprecated
    private static void addLabelWithInfoIcon(JPanel panel, GridBagConstraints gbc,
                                              String labelText, HelpContentProvider.HelpTopic helpTopic) {
        JPanel labelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        labelPanel.add(new JLabel(labelText));
        labelPanel.add(Box.createHorizontalStrut(5));
        HelpButton infoBtn = createInfoHelpButton(helpTopic);
        labelPanel.add(infoBtn);
        panel.add(labelPanel, gbc);
        gbc.gridy++;
    }

    /**
     * Erstellt einen blauen Fragezeichen-HelpButton für technische Hilfe.
     */
    private static HelpButton createInfoHelpButton(HelpContentProvider.HelpTopic helpTopic) {
        HelpButton helpBtn = new HelpButton("Technische Details anzeigen");
        helpBtn.setVisible(settings.showHelpIcons);
        helpBtn.addActionListener(e -> HelpContentProvider.showHelpPopup((Component) e.getSource(), helpTopic));
        return helpBtn;
    }

    private static void createConnectContent(JPanel expertContent) {
        GridBagConstraints gbcConnect = createDefaultGbc();

        // Server / Credentials
        expertContent.add(new JLabel("Server (Host):"), gbcConnect);
        gbcConnect.gridy++;
        hostField = new JTextField(settings.host == null ? "" : settings.host, 24);
        expertContent.add(hostField, gbcConnect);
        gbcConnect.gridy++;

        expertContent.add(new JLabel("Benutzer:"), gbcConnect);
        gbcConnect.gridy++;
        userField = new JTextField(settings.user == null ? "" : settings.user, 24);
        expertContent.add(userField, gbcConnect);
        gbcConnect.gridy++;

        expertContent.add(new JLabel("Passwort (optional):"), gbcConnect);
        gbcConnect.gridy++;
        passwordField = new JPasswordField(24);
        expertContent.add(passwordField, gbcConnect);
        gbcConnect.gridy++;

        clearPasswordButton = new JButton("Passwort löschen");
        clearPasswordButton.addActionListener(e -> {
            passwordField.setText("");
            clearPasswordRequested = true;
        });
        expertContent.add(clearPasswordButton, gbcConnect);
        gbcConnect.gridy++;

        // FTP-Transferoptionen (TYPE, FORMAT, STRUCTURE, MODE) mit Info-Icons neben Dropdowns
        expertContent.add(new JLabel("FTP Datei-Typ (TYPE):"), gbcConnect);
        gbcConnect.gridy++;
        typeBox = ComboBoxHelper.createComboBoxWithNullOption(
                FtpFileType.class, settings.ftpFileType, "Standard"
        );
        expertContent.add(createFieldWithHelpIcon(typeBox,
                HelpContentProvider.HelpTopic.FTP_FILE_TYPE), gbcConnect);
        gbcConnect.gridy++;

        expertContent.add(new JLabel("FTP Text-Format (FORMAT):"), gbcConnect);
        gbcConnect.gridy++;
        formatBox = ComboBoxHelper.createComboBoxWithNullOption(
                FtpTextFormat.class, settings.ftpTextFormat, "Standard"
        );
        expertContent.add(createFieldWithHelpIcon(formatBox,
                HelpContentProvider.HelpTopic.FTP_TEXT_FORMAT), gbcConnect);
        gbcConnect.gridy++;

        expertContent.add(new JLabel("FTP Dateistruktur (STRUCTURE):"), gbcConnect);
        gbcConnect.gridy++;
        structureBox = ComboBoxHelper.createComboBoxWithNullOption(
                FtpFileStructure.class, settings.ftpFileStructure, "Automatisch"
        );
        expertContent.add(createFieldWithHelpIcon(structureBox,
                HelpContentProvider.HelpTopic.FTP_FILE_STRUCTURE), gbcConnect);
        gbcConnect.gridy++;

        expertContent.add(new JLabel("FTP Übertragungsmodus (MODE):"), gbcConnect);
        gbcConnect.gridy++;
        modeBox = ComboBoxHelper.createComboBoxWithNullOption(
                FtpTransferMode.class, settings.ftpTransferMode, "Standard"
        );
        expertContent.add(createFieldWithHelpIcon(modeBox,
                HelpContentProvider.HelpTopic.FTP_TRANSFER_MODE), gbcConnect);
        gbcConnect.gridy++;

        // Hexdump Checkbox mit Info-Icon rechts daneben
        hexDumpBox = new JCheckBox("Hexdump in Konsole anzeigen");
        hexDumpBox.setSelected(settings.enableHexDump);
        expertContent.add(createFieldWithHelpIcon(hexDumpBox,
                HelpContentProvider.HelpTopic.FTP_HEX_DUMP), gbcConnect);
        gbcConnect.gridy++;

        // Trennlinie für Timeouts
        expertContent.add(new JLabel(" "), gbcConnect);
        gbcConnect.gridy++;
        expertContent.add(new JLabel("─── FTP Timeouts (0 = deaktiviert) ───"), gbcConnect);
        gbcConnect.gridy++;

        // Connect Timeout mit Info-Icon neben dem Spinner
        expertContent.add(new JLabel("Connect Timeout (ms):"), gbcConnect);
        gbcConnect.gridy++;
        ftpConnectTimeoutSpinner = new JSpinner(new SpinnerNumberModel(
                settings.ftpConnectTimeoutMs, 0, 300_000, 1000));
        expertContent.add(createFieldWithHelpIcon(ftpConnectTimeoutSpinner,
                HelpContentProvider.HelpTopic.FTP_TIMEOUT_CONNECT), gbcConnect);
        gbcConnect.gridy++;

        // Control Timeout mit Info-Icon neben dem Spinner
        expertContent.add(new JLabel("Control Timeout (ms):"), gbcConnect);
        gbcConnect.gridy++;
        ftpControlTimeoutSpinner = new JSpinner(new SpinnerNumberModel(
                settings.ftpControlTimeoutMs, 0, 300_000, 1000));
        expertContent.add(createFieldWithHelpIcon(ftpControlTimeoutSpinner,
                HelpContentProvider.HelpTopic.FTP_TIMEOUT_CONTROL), gbcConnect);
        gbcConnect.gridy++;

        // Data Timeout mit Info-Icon neben dem Spinner
        expertContent.add(new JLabel("Data Timeout (ms):"), gbcConnect);
        gbcConnect.gridy++;
        ftpDataTimeoutSpinner = new JSpinner(new SpinnerNumberModel(
                settings.ftpDataTimeoutMs, 0, 300_000, 1000));
        expertContent.add(createFieldWithHelpIcon(ftpDataTimeoutSpinner,
                HelpContentProvider.HelpTopic.FTP_TIMEOUT_DATA), gbcConnect);
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

        aiContent.add(new JLabel("Mode für Tool-Contract:"), gbc);
        gbc.gridy++;
        aiModeCombo = new JComboBox<>(ChatMode.values());
        aiModeCombo.setSelectedItem(ChatMode.AGENT);
        aiContent.add(aiModeCombo, gbc);
        gbc.gridy++;

        aiResetToolContractButton = new JButton("Tool-Contract auf Mode-Default zurücksetzen");
        aiResetToolContractButton.addActionListener(e -> resetModeToolContractToDefault((ChatMode) aiModeCombo.getSelectedItem()));
        aiContent.add(aiResetToolContractButton, gbc);
        gbc.gridy++;

        aiContent.add(new JLabel("KI-Anweisung vor Tool-Calls (Prefix):"), gbc);
        gbc.gridy++;
        aiToolPrefix = new JTextArea(3, 30);
        aiToolPrefix.setLineWrap(true);
        aiToolPrefix.setWrapStyleWord(true);
        aiContent.add(new JScrollPane(aiToolPrefix), gbc);
        gbc.gridy++;

        aiContent.add(new JLabel("KI-Anweisung nach Tool-Calls (Postfix):"), gbc);
        gbc.gridy++;
        aiToolPostfix = new JTextArea(2, 30);
        aiToolPostfix.setLineWrap(true);
        aiToolPostfix.setWrapStyleWord(true);
        aiContent.add(new JScrollPane(aiToolPostfix), gbc);
        gbc.gridy++;

        aiContent.add(new JLabel("Antwortsprache (optional):"), gbc);
        gbc.gridy++;
        aiLanguageCombo = new JComboBox<>(new String[] {"Deutsch (Standard)", "Keine Vorgabe", "Englisch"});
        String languageSetting = settings.aiConfig.getOrDefault("assistant.language", "de").trim().toLowerCase();
        if ("".equals(languageSetting) || "none".equals(languageSetting)) {
            aiLanguageCombo.setSelectedItem("Keine Vorgabe");
        } else if ("en".equals(languageSetting) || "english".equals(languageSetting)) {
            aiLanguageCombo.setSelectedItem("Englisch");
        } else {
            aiLanguageCombo.setSelectedItem("Deutsch (Standard)");
        }
        aiContent.add(aiLanguageCombo, gbc);
        gbc.gridy++;

        final ChatMode[] previousMode = new ChatMode[] {(ChatMode) aiModeCombo.getSelectedItem()};
        loadModeToolContract(previousMode[0]);
        aiModeCombo.addActionListener(e -> {
            ChatMode newMode = (ChatMode) aiModeCombo.getSelectedItem();
            if (previousMode[0] != null) {
                settings.aiConfig.put("toolPrefix." + previousMode[0].name(), aiToolPrefix.getText().trim());
                settings.aiConfig.put("toolPostfix." + previousMode[0].name(), aiToolPostfix.getText().trim());
            }
            loadModeToolContract(newMode);
            previousMode[0] = newMode;
        });

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

        providerCombo = new JComboBox<>();
        providerCombo.addItem(AiProvider.DISABLED);
        providerCombo.addItem(AiProvider.OLLAMA);
        providerCombo.addItem(AiProvider.CLOUD);
        providerCombo.addItem(AiProvider.LOCAL_AI);
        providerCombo.addItem(AiProvider.LLAMA_CPP_SERVER);
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
        JPanel cloudPanel = new JPanel(new GridBagLayout());
        JPanel localAiPanel = new JPanel(new GridBagLayout());

        providerOptionsPanel.add(disabledPanel, AiProvider.DISABLED.name());
        providerOptionsPanel.add(ollamaPanel, AiProvider.OLLAMA.name());
        providerOptionsPanel.add(cloudPanel, AiProvider.CLOUD.name());
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

        // CLOUD-Felder
        GridBagConstraints gbcCloud = createDefaultGbc();
        cloudPanel.add(new JLabel("Cloud-Anbieter:"), gbcCloud);
        gbcCloud.gridy++;
        cloudProviderField = new JComboBox<>(new String[]{"OPENAI", "CLAUDE", "PERPLEXITY", "GROK", "GEMINI"});
        cloudPanel.add(cloudProviderField, gbcCloud);
        gbcCloud.gridy++;

        cloudPanel.add(new JLabel("API Key:"), gbcCloud);
        gbcCloud.gridy++;
        cloudApiKeyField = new JTextField(30);
        cloudPanel.add(cloudApiKeyField, gbcCloud);
        gbcCloud.gridy++;

        cloudPanel.add(new JLabel("API URL:"), gbcCloud);
        gbcCloud.gridy++;
        cloudApiUrlField = new JTextField(30);
        cloudPanel.add(cloudApiUrlField, gbcCloud);
        gbcCloud.gridy++;

        cloudPanel.add(new JLabel("Modell:"), gbcCloud);
        gbcCloud.gridy++;
        cloudModelField = new JTextField(30);
        cloudPanel.add(cloudModelField, gbcCloud);
        gbcCloud.gridy++;

        cloudPanel.add(new JLabel("Auth Header:"), gbcCloud);
        gbcCloud.gridy++;
        cloudAuthHeaderField = new JTextField(30);
        cloudPanel.add(cloudAuthHeaderField, gbcCloud);
        gbcCloud.gridy++;

        cloudPanel.add(new JLabel("Auth Prefix (z. B. Bearer):"), gbcCloud);
        gbcCloud.gridy++;
        cloudAuthPrefixField = new JTextField(30);
        cloudPanel.add(cloudAuthPrefixField, gbcCloud);
        gbcCloud.gridy++;
        cloudPanel.add(new JLabel("Anthropic-Version (nur Claude):"), gbcCloud);
        gbcCloud.gridy++;
        cloudApiVersionField = new JTextField(30);
        cloudPanel.add(cloudApiVersionField, gbcCloud);
        gbcCloud.gridy++;


        cloudPanel.add(new JLabel("OpenAI Organisation (optional):"), gbcCloud);
        gbcCloud.gridy++;
        cloudOrgField = new JTextField(30);
        cloudPanel.add(cloudOrgField, gbcCloud);
        gbcCloud.gridy++;

        cloudPanel.add(new JLabel("OpenAI Projekt (optional):"), gbcCloud);
        gbcCloud.gridy++;
        cloudProjectField = new JTextField(30);
        cloudPanel.add(cloudProjectField, gbcCloud);
        gbcCloud.gridy++;

        JButton cloudResetButton = new JButton("Defaults zurücksetzen");
        cloudPanel.add(cloudResetButton, gbcCloud);
        gbcCloud.gridy++;

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
        AiProvider selectedProvider;
        try {
            selectedProvider = AiProvider.valueOf(providerName);
        } catch (IllegalArgumentException ex) {
            selectedProvider = AiProvider.DISABLED;
        }
        providerCombo.setSelectedItem(selectedProvider);

        ollamaUrlField.setText(settings.aiConfig.getOrDefault("ollama.url", "http://localhost:11434/api/generate"));
        ollamaModelField.setText(settings.aiConfig.getOrDefault("ollama.model", "custom-modell"));
        ollamaKeepAliveField.setText(settings.aiConfig.getOrDefault("ollama.keepalive", "10m"));

        String initialCloudVendor = settings.aiConfig.getOrDefault("cloud.vendor", "OPENAI");
        if ("CLOUD".equalsIgnoreCase(initialCloudVendor)) {
            initialCloudVendor = "CLAUDE";
        }
        cloudProviderField.setSelectedItem(initialCloudVendor);
        applyCloudVendorDefaults(false);
        cloudApiKeyField.setText(settings.aiConfig.getOrDefault("cloud.apikey", ""));
        cloudApiUrlField.setText(settings.aiConfig.getOrDefault("cloud.url", cloudDefaultForVendor(initialCloudVendor, "url")));
        cloudModelField.setText(settings.aiConfig.getOrDefault("cloud.model", cloudDefaultForVendor(initialCloudVendor, "model")));
        cloudAuthHeaderField.setText(settings.aiConfig.getOrDefault("cloud.authHeader", cloudDefaultForVendor(initialCloudVendor, "authHeader")));
        cloudAuthPrefixField.setText(settings.aiConfig.getOrDefault("cloud.authPrefix", cloudDefaultForVendor(initialCloudVendor, "authPrefix")));
        cloudOrgField.setText(settings.aiConfig.getOrDefault("cloud.organization", ""));
        cloudProjectField.setText(settings.aiConfig.getOrDefault("cloud.project", ""));
        cloudApiVersionField.setText(settings.aiConfig.getOrDefault("cloud.anthropicVersion", "2023-06-01"));

        cloudProviderField.addActionListener(e -> applyCloudVendorDefaults(true));
        cloudResetButton.addActionListener(e -> {
            String selectedVendor = (String) cloudProviderField.getSelectedItem();
            applyCloudVendorDefaults(true);
            cloudApiKeyField.setText("");
            if (!"OPENAI".equals(selectedVendor)) {
                cloudOrgField.setText("");
                cloudProjectField.setText("");
            }
        });

        // Umschalten je nach Provider
        providerCombo.addActionListener(e -> {
            AiProvider selected = (AiProvider) providerCombo.getSelectedItem();
            CardLayout cl = (CardLayout) providerOptionsPanel.getLayout();
            cl.show(providerOptionsPanel, selected.name());
        });

        ((CardLayout) providerOptionsPanel.getLayout()).show(providerOptionsPanel, selectedProvider.name());
    }

    private static void applyCloudVendorDefaults(boolean clearOptionalFields) {
        String vendor = Objects.toString(cloudProviderField.getSelectedItem(), "OPENAI");
        cloudApiUrlField.setText(cloudDefaultForVendor(vendor, "url"));
        cloudModelField.setText(cloudDefaultForVendor(vendor, "model"));
        cloudAuthHeaderField.setText(cloudDefaultForVendor(vendor, "authHeader"));
        cloudAuthPrefixField.setText(cloudDefaultForVendor(vendor, "authPrefix"));
        cloudApiVersionField.setText(cloudDefaultForVendor(vendor, "anthropicVersion"));

        boolean isOpenAi = "OPENAI".equals(vendor);
        boolean isClaude = "CLAUDE".equals(vendor);
        cloudOrgField.setEnabled(isOpenAi);
        cloudProjectField.setEnabled(isOpenAi);
        cloudApiVersionField.setEnabled(isClaude);
        if (clearOptionalFields && !isOpenAi) {
            cloudOrgField.setText("");
            cloudProjectField.setText("");
        }
    }

    private static String cloudDefaultForVendor(String vendor, String key) {
        switch (vendor) {
            case "PERPLEXITY":
                if ("url".equals(key)) return "https://api.perplexity.ai/chat/completions";
                if ("model".equals(key)) return "sonar";
                break;
            case "GROK":
                if ("url".equals(key)) return "https://api.x.ai/v1/chat/completions";
                if ("model".equals(key)) return "grok-2-latest";
                break;
            case "GEMINI":
                if ("url".equals(key)) return "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
                if ("model".equals(key)) return "gemini-2.0-flash";
                break;
            case "CLAUDE":
                if ("url".equals(key)) return "https://api.anthropic.com/v1/messages";
                if ("model".equals(key)) return "claude-3-5-sonnet-latest";
                if ("authHeader".equals(key)) return "x-api-key";
                if ("authPrefix".equals(key)) return "";
                if ("anthropicVersion".equals(key)) return "2023-06-01";
                break;
            case "OPENAI":
            default:
                if ("url".equals(key)) return "https://api.openai.com/v1/chat/completions";
                if ("model".equals(key)) return "gpt-4o-mini";
                break;
        }

        if ("authHeader".equals(key)) return "Authorization";
        if ("authPrefix".equals(key)) return "Bearer";
        if ("anthropicVersion".equals(key)) return "2023-06-01";
        return "";
    }

    private static void createProxyContent(JPanel proxyContent, Component parent) {
        GridBagConstraints gbc = createDefaultGbc();

        proxyEnabledBox = new JCheckBox("Proxy aktivieren");
        proxyEnabledBox.setSelected(settings.proxyEnabled);
        proxyContent.add(proxyEnabledBox, gbc);
        gbc.gridy++;

        proxyContent.add(new JLabel("Proxy-Modus:"), gbc);
        gbc.gridy++;
        proxyModeBox = new JComboBox<>(new String[] { "WINDOWS_PAC", "MANUAL" });
        proxyModeBox.setSelectedItem(settings.proxyMode == null ? "WINDOWS_PAC" : settings.proxyMode);
        proxyContent.add(proxyModeBox, gbc);
        gbc.gridy++;

        proxyContent.add(new JLabel("Hinweis: PAC/WPAD wird nicht geparst. Der effektive Proxy wird per PowerShell ermittelt."), gbc);
        gbc.gridy++;

        proxyContent.add(new JLabel("Proxy Host (manuell):"), gbc);
        gbc.gridy++;
        proxyHostField = new JTextField(settings.proxyHost == null ? "" : settings.proxyHost, 24);
        proxyContent.add(proxyHostField, gbc);
        gbc.gridy++;

        proxyContent.add(new JLabel("Proxy Port (manuell):"), gbc);
        gbc.gridy++;
        proxyPortSpinner = new JSpinner(new SpinnerNumberModel(settings.proxyPort, 0, 65535, 1));
        proxyContent.add(proxyPortSpinner, gbc);
        gbc.gridy++;

        proxyNoProxyLocalBox = new JCheckBox("Lokale Ziele niemals über Proxy");
        proxyNoProxyLocalBox.setSelected(settings.proxyNoProxyLocal);
        proxyContent.add(proxyNoProxyLocalBox, gbc);
        gbc.gridy++;

        proxyContent.add(new JLabel("PAC/WPAD Script (PowerShell):"), gbc);
        gbc.gridy++;
        proxyPacScriptArea = new RSyntaxTextArea(12, 60);
        proxyPacScriptArea.setSyntaxEditingStyle("text/powershell");
        proxyPacScriptArea.setCodeFoldingEnabled(true);
        proxyPacScriptArea.setText(settings.proxyPacScript == null ? ProxyDefaults.DEFAULT_PAC_SCRIPT : settings.proxyPacScript);
        proxyContent.add(new RTextScrollPane(proxyPacScriptArea), gbc);
        gbc.gridy++;

        proxyContent.add(new JLabel("Test-URL:"), gbc);
        gbc.gridy++;
        proxyTestUrlField = new JTextField(settings.proxyTestUrl == null ? ProxyDefaults.DEFAULT_TEST_URL : settings.proxyTestUrl, 30);
        proxyContent.add(proxyTestUrlField, gbc);
        gbc.gridy++;

        proxyTestButton = new JButton("Proxy testen");
        proxyTestButton.addActionListener(e -> {
            String testUrl = proxyTestUrlField.getText().trim();
            if (testUrl.isEmpty()) {
                JOptionPane.showMessageDialog(parent, "Bitte eine Test-URL eingeben.", "Proxy Test", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String script = proxyPacScriptArea.getText();
            ProxyResolver.ProxyResolution result = ProxyResolver.testPacScript(testUrl, script);
            String msg = result.isDirect()
                    ? "DIRECT (" + result.getReason() + ")"
                    : result.getProxy().address().toString() + " (" + result.getReason() + ")";
            JOptionPane.showMessageDialog(parent, msg, "Proxy Test", JOptionPane.INFORMATION_MESSAGE);
        });
        proxyContent.add(proxyTestButton, gbc);
    }

    private static void showAndApply(Component parent, JComponent tabs) {
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
            settings.soundEnabled = enableSound.isSelected();
            settings.savePassword = savePasswordBox.isSelected();
            settings.autoConnect = autoConnectBox.isSelected();

            String hostInput = hostField != null ? hostField.getText().trim() : "";
            String userInput = userField != null ? userField.getText().trim() : "";
            if (!hostInput.isEmpty()) {
                settings.host = hostInput;
            }
            if (!userInput.isEmpty()) {
                settings.user = userInput;
            }

            if (passwordField != null) {
                char[] passChars = passwordField.getPassword();
                if (passChars != null && passChars.length > 0) {
                    if (settings.savePassword) {
                        settings.encryptedPassword = WindowsCryptoUtil.encrypt(new String(passChars));
                    } else {
                        settings.encryptedPassword = null;
                    }
                    clearPasswordRequested = false;
                } else if (clearPasswordRequested) {
                    settings.encryptedPassword = null;
                    clearPasswordRequested = false;
                }
            }

            settings.ftpFileType = ComboBoxHelper.getSelectedEnumValue(typeBox, FtpFileType.class);
            settings.ftpTextFormat = ComboBoxHelper.getSelectedEnumValue(formatBox, FtpTextFormat.class);
            settings.ftpFileStructure = ComboBoxHelper.getSelectedEnumValue(structureBox, FtpFileStructure.class);
            settings.ftpTransferMode = ComboBoxHelper.getSelectedEnumValue(modeBox, FtpTransferMode.class);
            settings.enableHexDump = hexDumpBox.isSelected();

            // FTP Timeouts
            settings.ftpConnectTimeoutMs = ((Number) ftpConnectTimeoutSpinner.getValue()).intValue();
            settings.ftpControlTimeoutMs = ((Number) ftpControlTimeoutSpinner.getValue()).intValue();
            settings.ftpDataTimeoutMs = ((Number) ftpDataTimeoutSpinner.getValue()).intValue();

            settings.defaultWorkflow = defaultWorkflow.getText();
            settings.workflowTimeout = ((Number) workflowTimeoutSpinner.getValue()).longValue();
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
            settings.lockStyle = lockStyleBox.getSelectedIndex();
            settings.showHelpIcons = showHelpIconsBox.isSelected();

            settings.aiConfig.put("editor.font", aiEditorFontCombo.getSelectedItem().toString());
            settings.aiConfig.put("editor.fontSize", aiEditorFontSizeCombo.getSelectedItem().toString());
            settings.aiConfig.put("editor.lines", aiEditorHeightSpinner.getValue().toString());
            ChatMode selectedMode = aiModeCombo != null && aiModeCombo.getSelectedItem() != null
                    ? (ChatMode) aiModeCombo.getSelectedItem()
                    : ChatMode.AGENT;
            settings.aiConfig.put("toolPrefix." + selectedMode.name(), aiToolPrefix.getText().trim());
            settings.aiConfig.put("toolPostfix." + selectedMode.name(), aiToolPostfix.getText().trim());
            settings.aiConfig.remove("toolPrefix");
            settings.aiConfig.remove("toolPostfix");
            String selectedLanguage = Objects.toString(aiLanguageCombo.getSelectedItem(), "Deutsch (Standard)");
            if ("Keine Vorgabe".equals(selectedLanguage)) {
                settings.aiConfig.put("assistant.language", "none");
            } else if ("Englisch".equals(selectedLanguage)) {
                settings.aiConfig.put("assistant.language", "en");
            } else {
                settings.aiConfig.put("assistant.language", "de");
            }
            settings.aiConfig.put("provider", providerCombo.getSelectedItem().toString());
            settings.aiConfig.put("ollama.url", ollamaUrlField.getText().trim());
            settings.aiConfig.put("ollama.model", ollamaModelField.getText().trim());
            settings.aiConfig.put("ollama.keepalive", ollamaKeepAliveField.getText().trim());
            settings.aiConfig.put("cloud.vendor", Objects.toString(cloudProviderField.getSelectedItem(), "OPENAI"));
            settings.aiConfig.put("cloud.apikey", cloudApiKeyField.getText().trim());
            settings.aiConfig.put("cloud.url", cloudApiUrlField.getText().trim());
            settings.aiConfig.put("cloud.model", cloudModelField.getText().trim());
            settings.aiConfig.put("cloud.authHeader", cloudAuthHeaderField.getText().trim());
            settings.aiConfig.put("cloud.authPrefix", cloudAuthPrefixField.getText().trim());
            settings.aiConfig.put("cloud.anthropicVersion", cloudApiVersionField.getText().trim());
            settings.aiConfig.put("cloud.organization", cloudOrgField.getText().trim());
            settings.aiConfig.put("cloud.project", cloudProjectField.getText().trim());
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

            settings.proxyEnabled = proxyEnabledBox.isSelected();
            settings.proxyMode = Objects.toString(proxyModeBox.getSelectedItem(), "WINDOWS_PAC");
            settings.proxyHost = proxyHostField.getText().trim();
            settings.proxyPort = ((Number) proxyPortSpinner.getValue()).intValue();
            settings.proxyNoProxyLocal = proxyNoProxyLocalBox.isSelected();
            settings.proxyPacScript = proxyPacScriptArea.getText();
            settings.proxyTestUrl = proxyTestUrlField.getText().trim();

            // RAG/Embedding-Einstellungen vom Panel übernehmen
            if (ragSettingsPanel != null) {
                ragSettingsPanel.saveToSettings(settings);
            }

            SettingsHelper.save(settings);


//            JOptionPane.showMessageDialog(parent,
//                    "Einstellungen wurden gespeichert.",
//                    "Info", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static void resetModeToolContractToDefault(ChatMode mode) {
        ChatMode resolved = mode != null ? mode : ChatMode.AGENT;
        aiToolPrefix.setText(resolved.getDefaultToolPrefix());
        aiToolPostfix.setText(resolved.getDefaultToolPostfix());
    }

    private static void loadModeToolContract(ChatMode mode) {
        ChatMode resolved = mode != null ? mode : ChatMode.AGENT;
        String modePrefixKey = "toolPrefix." + resolved.name();
        String modePostfixKey = "toolPostfix." + resolved.name();

        String prefix = settings.aiConfig.getOrDefault(modePrefixKey, resolved.getDefaultToolPrefix());
        String postfix = settings.aiConfig.getOrDefault(modePostfixKey, resolved.getDefaultToolPostfix());

        aiToolPrefix.setText(prefix);
        aiToolPostfix.setText(postfix);
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
