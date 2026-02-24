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
    private static JSpinner ftpRetryMaxAttemptsSpinner;
    private static JSpinner ftpRetryBackoffSpinner;
    private static JComboBox<String> ftpRetryStrategyCombo;
    private static JSpinner ftpRetryMaxBackoffSpinner;
    private static JCheckBox ftpRetryOnTimeoutBox;
    private static JCheckBox ftpRetryOnTransientIoBox;
    private static JTextField ftpRetryOnReplyCodesField;
    private static JCheckBox ftpUseLoginAsHlqBox;
    private static JTextField ftpCustomHlqField;
    private static JButton openFolderButton;
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

    // Local History
    private static JCheckBox historyEnabledBox;
    private static JSpinner historyMaxVersionsSpinner;
    private static JSpinner historyMaxAgeDaysSpinner;

    private static JSpinner ndvPortSpinner;
    private static JTextField ndvDefaultLibraryField;
    private static JTextField ndvLibPathField;

    private static JCheckBox proxyEnabledBox;
    private static JComboBox<String> proxyModeBox;
    private static JTextField proxyHostField;
    private static JSpinner proxyPortSpinner;
    private static JCheckBox proxyNoProxyLocalBox;
    private static RSyntaxTextArea proxyPacScriptArea;
    private static JTextField proxyTestUrlField;
    private static JButton proxyTestButton;
    private static RagSettingsPanel ragSettingsPanel;

    // Mail Settings
    public static final int TAB_INDEX_MAILS = 9;
    private static JTextField mailPathField;
    private static JTextField mailContainerClassesField;
    private static JList<String> mailWhitelistJList;

    // Debug Settings
    public static final int TAB_INDEX_DEBUG = 10;
    private static JComboBox<String> globalLogLevelCombo;
    private static final String[] LOG_LEVELS = {"OFF", "SEVERE", "WARNING", "INFO", "FINE", "FINER", "FINEST", "ALL"};
    private static final String[] LOG_CATEGORIES = {
            de.bund.zrb.util.AppLogger.MAIL,
            de.bund.zrb.util.AppLogger.STAR,
            de.bund.zrb.util.AppLogger.FTP,
            de.bund.zrb.util.AppLogger.NDV,
            de.bund.zrb.util.AppLogger.TOOL,
            de.bund.zrb.util.AppLogger.INDEX,
            de.bund.zrb.util.AppLogger.SEARCH,
            de.bund.zrb.util.AppLogger.RAG,
            de.bund.zrb.util.AppLogger.UI
    };
    private static final java.util.Map<String, JComboBox<String>> categoryLevelCombos = new java.util.LinkedHashMap<>();

    public static void show(Component parent) {
        show(parent, 0);
    }

    public static void show(Component parent, int initialTabIndex) {
        settings = SettingsHelper.load();

        JPanel generalContent = new JPanel(new GridBagLayout());
        JPanel colorContent = new JPanel(new BorderLayout());
        JPanel transformContent = new JPanel(new GridBagLayout());
        JPanel connectContent = new JPanel(new GridBagLayout());
        JPanel aiContent = new JPanel(new GridBagLayout());
        ragSettingsPanel = new RagSettingsPanel();
        JPanel proxyContent = new JPanel(new GridBagLayout());
        JPanel ndvContent = new JPanel(new GridBagLayout());
        JPanel mailContent = new JPanel(new GridBagLayout());
        JPanel debugContent = new JPanel(new GridBagLayout());

        createGeneralContent(generalContent, parent);
        createTransformContent(transformContent);
        createConnectContent(connectContent);
        createNdvContent(ndvContent);
        createColorContent(colorContent, parent);
        createAiContent(aiContent);
        createProxyContent(proxyContent, parent);
        createMailContent(mailContent);
        createDebugContent(debugContent);

        List<SettingsCategory> categories = new ArrayList<>();
        categories.add(simpleCategory("general",   "Allgemein",        generalContent));
        categories.add(simpleCategory("colors",    "Farbzuordnung",    colorContent));
        categories.add(simpleCategory("transform", "Datenumwandlung",  transformContent));
        categories.add(simpleCategory("ftp",       "FTP-Verbindung",   connectContent));
        categories.add(simpleCategory("ndv",       "NDV-Verbindung",   ndvContent));
        categories.add(simpleCategory("ai",        "KI",               aiContent));
        categories.add(simpleCategory("rag",       "RAG",              ragSettingsPanel));
        categories.add(simpleCategory("proxy",     "Proxy",            proxyContent));
        categories.add(simpleCategory("mcp",       "MCP Registry",     new McpRegistryPanel()));
        categories.add(simpleCategory("mails",     "Mails",            mailContent));
        categories.add(simpleCategory("debug",     "Debug",            debugContent));
        categories.set(0, withApply(categories.get(0), SettingsDialog::applyAllSettings));

        List<JButton> leftButtons = new ArrayList<>();
        JButton folderBtn = new JButton("App-Ordner öffnen");
        folderBtn.addActionListener(e -> {
            try { Desktop.getDesktop().open(SettingsHelper.getSettingsFolder()); }
            catch (IOException ex) { JOptionPane.showMessageDialog(parent, "Ordner konnte nicht geöffnet werden:\n" + ex.getMessage()); }
        });
        leftButtons.add(folderBtn);

        Window ownerWindow = (parent instanceof Window) ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        OutlookStyleSettingsDialog dlg = new OutlookStyleSettingsDialog(ownerWindow, "Einstellungen", categories, leftButtons);
        if (initialTabIndex >= 0 && initialTabIndex < categories.size()) dlg.selectCategory(initialTabIndex);
        dlg.setVisible(true);
    }

    private static SettingsCategory simpleCategory(String id, String title, JComponent component) {
        return new SettingsCategory() {
            @Override public String getId() { return id; }
            @Override public String getTitle() { return title; }
            @Override public JComponent getComponent() { return component; }
        };
    }

    private static SettingsCategory withApply(SettingsCategory delegate, Runnable applyAction) {
        return new SettingsCategory() {
            @Override public String getId() { return delegate.getId(); }
            @Override public String getTitle() { return delegate.getTitle(); }
            @Override public JComponent getComponent() { return delegate.getComponent(); }
            @Override public void validate() { delegate.validate(); }
            @Override public void apply() { delegate.apply(); applyAction.run(); }
        };
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

        // ===== Local History =====
        generalContent.add(new JLabel("── Lokale Historie ──"), gbcGeneral);
        gbcGeneral.gridy++;

        historyEnabledBox = new JCheckBox("Lokale Historie aktivieren");
        historyEnabledBox.setSelected(settings.historyEnabled);
        generalContent.add(historyEnabledBox, gbcGeneral);
        gbcGeneral.gridy++;

        generalContent.add(new JLabel("Max. Versionen pro Datei:"), gbcGeneral);
        gbcGeneral.gridy++;
        historyMaxVersionsSpinner = new JSpinner(new SpinnerNumberModel(
                settings.historyMaxVersionsPerFile, 1, 10000, 10));
        generalContent.add(historyMaxVersionsSpinner, gbcGeneral);
        gbcGeneral.gridy++;

        generalContent.add(new JLabel("Max. Alter (Tage):"), gbcGeneral);
        gbcGeneral.gridy++;
        historyMaxAgeDaysSpinner = new JSpinner(new SpinnerNumberModel(
                settings.historyMaxAgeDays, 1, 3650, 10));
        generalContent.add(historyMaxAgeDaysSpinner, gbcGeneral);
        gbcGeneral.gridy++;

        JButton pruneNowButton = new JButton("🧹 Historie jetzt bereinigen");
        pruneNowButton.addActionListener(e -> {
            int maxVer = ((Number) historyMaxVersionsSpinner.getValue()).intValue();
            int maxAge = ((Number) historyMaxAgeDaysSpinner.getValue()).intValue();
            de.bund.zrb.history.LocalHistoryService.getInstance().prune(maxVer, maxAge);
            JOptionPane.showMessageDialog(parent, "Bereinigung abgeschlossen.",
                    "Lokale Historie", JOptionPane.INFORMATION_MESSAGE);
        });
        generalContent.add(pruneNowButton, gbcGeneral);
        gbcGeneral.gridy++;

        JButton clearAllHistoryButton = new JButton("🗑️ Gesamte Historie löschen");
        clearAllHistoryButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(parent,
                    "Wirklich die gesamte lokale Historie für alle Backends löschen?",
                    "Historie löschen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                de.bund.zrb.history.LocalHistoryService svc = de.bund.zrb.history.LocalHistoryService.getInstance();
                svc.clearBackend(de.bund.zrb.ui.VirtualBackendType.LOCAL);
                svc.clearBackend(de.bund.zrb.ui.VirtualBackendType.FTP);
                svc.clearBackend(de.bund.zrb.ui.VirtualBackendType.NDV);
                JOptionPane.showMessageDialog(parent, "Gesamte Historie gelöscht.",
                        "Lokale Historie", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        generalContent.add(clearAllHistoryButton, gbcGeneral);
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

        // Zeichensatz-Auswahl mit Info-Icon
        encodingCombo = new JComboBox<>();
        List<String> encodings = SettingsHelper.SUPPORTED_ENCODINGS;
        encodings.forEach(encodingCombo::addItem);
        String currentEncoding = settings.encoding != null ? settings.encoding : "windows-1252";
        encodingCombo.setSelectedItem(currentEncoding);
        addLabelWithInfoIcon(expertContent, gbcTransform, "Zeichenkodierung:",
                HelpContentProvider.HelpTopic.TRANSFORM_ENCODING);
        expertContent.add(encodingCombo, gbcTransform);
        gbcTransform.gridy++;

        // Zeilenumbruch mit Info-Icon
        addLabelWithInfoIcon(expertContent, gbcTransform, "Zeilenumbruch des Servers:",
                HelpContentProvider.HelpTopic.TRANSFORM_LINE_ENDING);
        lineEndingBox = LineEndingOption.createLineEndingComboBox(settings.lineEnding);
        expertContent.add(lineEndingBox, gbcTransform);
        gbcTransform.gridy++;

        // Checkbox mit Info-Icon
        JPanel stripNewlinePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        stripFinalNewlineBox = new JCheckBox("Letzten Zeilenumbruch ausblenden");
        stripFinalNewlineBox.setSelected(settings.removeFinalNewline);
        stripNewlinePanel.add(stripFinalNewlineBox);
        HelpButton stripNewlineInfoBtn = createInfoHelpButton(HelpContentProvider.HelpTopic.TRANSFORM_STRIP_NEWLINE);
        stripNewlinePanel.add(stripNewlineInfoBtn);
        expertContent.add(stripNewlinePanel, gbcTransform);
        gbcTransform.gridy++;

        // Dateiende mit Info-Icon
        addLabelWithInfoIcon(expertContent, gbcTransform, "Datei-Ende-Kennung (z. B. FF02, leer = aus):",
                HelpContentProvider.HelpTopic.TRANSFORM_EOF_MARKER);
        endMarkerBox = FileEndingOption.createEndMarkerComboBox(settings.fileEndMarker);
        expertContent.add(endMarkerBox, gbcTransform);
        gbcTransform.gridy++;

        // Padding mit Info-Icon
        addLabelWithInfoIcon(expertContent, gbcTransform, "Padding Byte (z. B. 00, leer = aus):",
                HelpContentProvider.HelpTopic.TRANSFORM_PADDING);
        paddingBox = PaddingOption.createPaddingComboBox(settings.padding);
        expertContent.add(paddingBox, gbcTransform);
        gbcTransform.gridy++;
    }

    /**
     * Erstellt ein Label mit Info-Icon für technische Hilfe.
     */
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


        // FTP-Transferoptionen (TYPE, FORMAT, STRUCTURE, MODE) mit Info-Icons
        addLabelWithInfoIcon(expertContent, gbcConnect, "FTP Datei-Typ (TYPE):",
                HelpContentProvider.HelpTopic.FTP_FILE_TYPE);
        typeBox = ComboBoxHelper.createComboBoxWithNullOption(
                FtpFileType.class, settings.ftpFileType, "Standard"
        );
        expertContent.add(typeBox, gbcConnect);
        gbcConnect.gridy++;

        addLabelWithInfoIcon(expertContent, gbcConnect, "FTP Text-Format (FORMAT):",
                HelpContentProvider.HelpTopic.FTP_TEXT_FORMAT);
        formatBox = ComboBoxHelper.createComboBoxWithNullOption(
                FtpTextFormat.class, settings.ftpTextFormat, "Standard"
        );
        expertContent.add(formatBox, gbcConnect);
        gbcConnect.gridy++;

        addLabelWithInfoIcon(expertContent, gbcConnect, "FTP Dateistruktur (STRUCTURE):",
                HelpContentProvider.HelpTopic.FTP_FILE_STRUCTURE);
        structureBox = ComboBoxHelper.createComboBoxWithNullOption(
                FtpFileStructure.class, settings.ftpFileStructure, "Automatisch"
        );
        expertContent.add(structureBox, gbcConnect);
        gbcConnect.gridy++;

        addLabelWithInfoIcon(expertContent, gbcConnect, "FTP Übertragungsmodus (MODE):",
                HelpContentProvider.HelpTopic.FTP_TRANSFER_MODE);
        modeBox = ComboBoxHelper.createComboBoxWithNullOption(
                FtpTransferMode.class, settings.ftpTransferMode, "Standard"
        );
        expertContent.add(modeBox, gbcConnect);
        gbcConnect.gridy++;

        // Hexdump Checkbox mit Info-Icon
        JPanel hexDumpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        hexDumpBox = new JCheckBox("Hexdump in Konsole anzeigen");
        hexDumpBox.setSelected(settings.enableHexDump);
        hexDumpPanel.add(hexDumpBox);
        HelpButton hexDumpInfoBtn = createInfoHelpButton(HelpContentProvider.HelpTopic.FTP_HEX_DUMP);
        hexDumpPanel.add(hexDumpInfoBtn);
        expertContent.add(hexDumpPanel, gbcConnect);
        gbcConnect.gridy++;

        // Trennlinie für Timeouts
        expertContent.add(new JLabel(" "), gbcConnect);
        gbcConnect.gridy++;
        expertContent.add(new JLabel("─── FTP Timeouts (0 = deaktiviert) ───"), gbcConnect);
        gbcConnect.gridy++;

        // Connect Timeout
        addLabelWithInfoIcon(expertContent, gbcConnect, "Connect Timeout (ms):",
                HelpContentProvider.HelpTopic.FTP_TIMEOUT_CONNECT);
        ftpConnectTimeoutSpinner = new JSpinner(new SpinnerNumberModel(
                settings.ftpConnectTimeoutMs, 0, 300_000, 1000));
        expertContent.add(ftpConnectTimeoutSpinner, gbcConnect);
        gbcConnect.gridy++;

        // Control Timeout
        addLabelWithInfoIcon(expertContent, gbcConnect, "Control Timeout (ms):",
                HelpContentProvider.HelpTopic.FTP_TIMEOUT_CONTROL);
        ftpControlTimeoutSpinner = new JSpinner(new SpinnerNumberModel(
                settings.ftpControlTimeoutMs, 0, 300_000, 1000));
        expertContent.add(ftpControlTimeoutSpinner, gbcConnect);
        gbcConnect.gridy++;

        // Data Timeout
        addLabelWithInfoIcon(expertContent, gbcConnect, "Data Timeout (ms):",
                HelpContentProvider.HelpTopic.FTP_TIMEOUT_DATA);
        ftpDataTimeoutSpinner = new JSpinner(new SpinnerNumberModel(
                settings.ftpDataTimeoutMs, 0, 300_000, 1000));
        expertContent.add(ftpDataTimeoutSpinner, gbcConnect);
        gbcConnect.gridy++;

        // Trennlinie für Retries
        expertContent.add(new JLabel(" "), gbcConnect);
        gbcConnect.gridy++;
        expertContent.add(new JLabel("─── FTP Retries ───"), gbcConnect);
        gbcConnect.gridy++;

        // Max Attempts
        addLabelWithInfoIcon(expertContent, gbcConnect, "Maximale Versuche:",
                HelpContentProvider.HelpTopic.FTP_RETRY_MAX_ATTEMPTS);
        ftpRetryMaxAttemptsSpinner = new JSpinner(new SpinnerNumberModel(
                settings.ftpRetryMaxAttempts, 1, 10, 1));
        expertContent.add(ftpRetryMaxAttemptsSpinner, gbcConnect);
        gbcConnect.gridy++;

        // Backoff
        addLabelWithInfoIcon(expertContent, gbcConnect, "Wartezeit zwischen Versuchen (ms):",
                HelpContentProvider.HelpTopic.FTP_RETRY_BACKOFF);
        ftpRetryBackoffSpinner = new JSpinner(new SpinnerNumberModel(
                settings.ftpRetryBackoffMs, 0, 60_000, 500));
        expertContent.add(ftpRetryBackoffSpinner, gbcConnect);
        gbcConnect.gridy++;

        // Backoff Strategy
        addLabelWithInfoIcon(expertContent, gbcConnect, "Backoff-Strategie:",
                HelpContentProvider.HelpTopic.FTP_RETRY_STRATEGY);
        ftpRetryStrategyCombo = new JComboBox<>(new String[]{"FIXED", "EXPONENTIAL"});
        ftpRetryStrategyCombo.setSelectedItem(settings.ftpRetryBackoffStrategy);
        expertContent.add(ftpRetryStrategyCombo, gbcConnect);
        gbcConnect.gridy++;

        // Max Backoff (nur bei EXPONENTIAL relevant)
        expertContent.add(new JLabel("Max Backoff bei EXPONENTIAL (ms, 0=unbegrenzt):"), gbcConnect);
        gbcConnect.gridy++;
        ftpRetryMaxBackoffSpinner = new JSpinner(new SpinnerNumberModel(
                settings.ftpRetryMaxBackoffMs, 0, 300_000, 1000));
        expertContent.add(ftpRetryMaxBackoffSpinner, gbcConnect);
        gbcConnect.gridy++;

        // Retry on Timeout Checkbox mit Info-Icon
        JPanel retryOnTimeoutPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        ftpRetryOnTimeoutBox = new JCheckBox("Retry bei Timeout-Fehlern");
        ftpRetryOnTimeoutBox.setSelected(settings.ftpRetryOnTimeout);
        retryOnTimeoutPanel.add(ftpRetryOnTimeoutBox);
        retryOnTimeoutPanel.add(Box.createHorizontalStrut(5));
        retryOnTimeoutPanel.add(createInfoHelpButton(HelpContentProvider.HelpTopic.FTP_RETRY_ON_TIMEOUT));
        expertContent.add(retryOnTimeoutPanel, gbcConnect);
        gbcConnect.gridy++;

        // Retry on Transient IO Checkbox mit Info-Icon
        JPanel retryOnIoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        ftpRetryOnTransientIoBox = new JCheckBox("Retry bei IO-Fehlern (Connection Reset etc.)");
        ftpRetryOnTransientIoBox.setSelected(settings.ftpRetryOnTransientIo);
        retryOnIoPanel.add(ftpRetryOnTransientIoBox);
        retryOnIoPanel.add(Box.createHorizontalStrut(5));
        retryOnIoPanel.add(createInfoHelpButton(HelpContentProvider.HelpTopic.FTP_RETRY_ON_IO));
        expertContent.add(retryOnIoPanel, gbcConnect);
        gbcConnect.gridy++;

        // Retry on Reply Codes
        addLabelWithInfoIcon(expertContent, gbcConnect, "Retry bei FTP Reply Codes (kommasepariert):",
                HelpContentProvider.HelpTopic.FTP_RETRY_ON_CODES);
        ftpRetryOnReplyCodesField = new JTextField(settings.ftpRetryOnReplyCodes, 20);
        ftpRetryOnReplyCodesField.setToolTipText("z.B. 421,425,426");
        expertContent.add(ftpRetryOnReplyCodesField, gbcConnect);
        gbcConnect.gridy++;

        // Trennlinie für Initial HLQ
        expertContent.add(new JLabel(" "), gbcConnect);
        gbcConnect.gridy++;
        expertContent.add(new JLabel("─── Initial HLQ (Startverzeichnis) ───"), gbcConnect);
        gbcConnect.gridy++;

        // Checkbox: Login-Name als HLQ verwenden
        ftpUseLoginAsHlqBox = new JCheckBox("Login-Namen als HLQ verwenden");
        ftpUseLoginAsHlqBox.setSelected(settings.ftpUseLoginAsHlq);
        ftpUseLoginAsHlqBox.setToolTipText("Wenn aktiviert, wird der Benutzername als initialer HLQ verwendet (wie IBM-Client)");
        expertContent.add(ftpUseLoginAsHlqBox, gbcConnect);
        gbcConnect.gridy++;

        // Custom HLQ Textfeld
        expertContent.add(new JLabel("Benutzerdefinierter HLQ (falls Checkbox aus):"), gbcConnect);
        gbcConnect.gridy++;
        ftpCustomHlqField = new JTextField(settings.ftpCustomHlq != null ? settings.ftpCustomHlq : "", 20);
        ftpCustomHlqField.setToolTipText("z.B. USERID oder PROD.DATA - wird nur verwendet, wenn Checkbox deaktiviert");
        expertContent.add(ftpCustomHlqField, gbcConnect);
        gbcConnect.gridy++;

        // Enable/Disable Logik für das Textfeld
        ftpCustomHlqField.setEnabled(!settings.ftpUseLoginAsHlq);
        ftpUseLoginAsHlqBox.addActionListener(e -> {
            ftpCustomHlqField.setEnabled(!ftpUseLoginAsHlqBox.isSelected());
        });
    }


    private static void createNdvContent(JPanel ndvContent) {
        GridBagConstraints gbc = createDefaultGbc();

        ndvContent.add(new JLabel("NDV Port:"), gbc);
        gbc.gridy++;
        ndvPortSpinner = new JSpinner(new SpinnerNumberModel(settings.ndvPort, 1, 65535, 1));
        ndvPortSpinner.setToolTipText("TCP-Port des NDV-Servers (Standard: 8011)");
        ndvContent.add(ndvPortSpinner, gbc);
        gbc.gridy++;

        ndvContent.add(new JLabel("Default-Bibliothek (optional):"), gbc);
        gbc.gridy++;
        ndvDefaultLibraryField = new JTextField(settings.ndvDefaultLibrary != null ? settings.ndvDefaultLibrary : "", 20);
        ndvDefaultLibraryField.setToolTipText("z.B. ABAK-T – wird beim Verbinden automatisch geöffnet (leer = keine)");
        ndvContent.add(ndvDefaultLibraryField, gbc);
        gbc.gridy++;

        // ─── NDV Bibliotheken (JARs) ───
        ndvContent.add(new JLabel(" "), gbc);
        gbc.gridy++;
        ndvContent.add(new JLabel("─── NDV-Bibliotheken (JARs) ───"), gbc);
        gbc.gridy++;

        ndvContent.add(new JLabel("Pfad zu NDV-JARs (leer = Standard):"), gbc);
        gbc.gridy++;

        String defaultLibDir = de.bund.zrb.ndv.NdvLibLoader.getLibDir().getAbsolutePath();
        ndvLibPathField = new JTextField(settings.ndvLibPath != null && !settings.ndvLibPath.isEmpty()
                ? settings.ndvLibPath : "", 24);
        ndvLibPathField.setToolTipText("Standard: " + defaultLibDir);
        ndvContent.add(ndvLibPathField, gbc);
        gbc.gridy++;

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JButton openLibFolderButton = new JButton("📂 Lib-Ordner öffnen");
        openLibFolderButton.setToolTipText("Öffnet den Ordner, in dem die NDV-JARs erwartet werden");
        openLibFolderButton.addActionListener(e -> {
            String customPath = ndvLibPathField.getText().trim();
            java.io.File libDir;
            if (!customPath.isEmpty()) {
                libDir = new java.io.File(customPath);
            } else {
                libDir = de.bund.zrb.ndv.NdvLibLoader.getLibDir();
            }
            // Create directory if it doesn't exist
            if (!libDir.exists()) {
                libDir.mkdirs();
            }
            try {
                java.awt.Desktop.getDesktop().open(libDir);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(ndvContent,
                        "Ordner konnte nicht geöffnet werden:\n" + libDir.getAbsolutePath()
                                + "\n\n" + ex.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        });
        buttonPanel.add(openLibFolderButton);
        ndvContent.add(buttonPanel, gbc);
        gbc.gridy++;

        // Status info
        gbc.gridy++;
        boolean available = de.bund.zrb.ndv.NdvLibLoader.isAvailable();
        JLabel statusLabel = new JLabel(available
                ? "✅ NDV-Bibliotheken gefunden"
                : "⚠ NDV-JARs nicht gefunden in: " + defaultLibDir);
        statusLabel.setForeground(available ? new Color(0, 128, 0) : new Color(200, 100, 0));
        ndvContent.add(statusLabel, gbc);
        gbc.gridy++;

        // Info
        gbc.gridy++;
        JLabel infoLabel = new JLabel("<html><small>Server-Adresse und Benutzer werden unter Einstellungen → Server verwaltet.<br>"
                + "NDV verwendet dieselben Zugangsdaten wie FTP.<br><br>"
                + "Benötigte JARs (von NaturalONE/Software AG):<br>"
                + "• com.softwareag.naturalone.natural.ndvserveraccess_*.jar<br>"
                + "• com.softwareag.naturalone.natural.auxiliary_*.jar</small></html>");
        infoLabel.setForeground(Color.GRAY);
        ndvContent.add(infoLabel, gbc);
    }

    private static void createMailContent(JPanel mailContent) {
        GridBagConstraints gbc = createDefaultGbc();

        mailContent.add(new JLabel("Mail-Speicherort (OST-Ordner):"), gbc);
        gbc.gridy++;

        // Calculate default path
        String defaultPath = de.bund.zrb.ui.commands.ConnectMailMenuCommand.getDefaultOutlookPath();
        String currentValue = settings.mailStorePath;
        if (currentValue == null || currentValue.trim().isEmpty()) {
            currentValue = defaultPath;
        }

        mailPathField = new JTextField(currentValue, 30);
        mailPathField.setToolTipText("Pfad zum Ordner mit Outlook-Datendateien (OST/PST)");
        mailContent.add(mailPathField, gbc);
        gbc.gridy++;

        JButton browseButton = new JButton("📂 Ordner auswählen…");
        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(mailPathField.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Mail-Speicherort auswählen");
            if (chooser.showOpenDialog(mailContent) == JFileChooser.APPROVE_OPTION) {
                mailPathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        mailContent.add(browseButton, gbc);
        gbc.gridy++;

        // Container Classes
        gbc.gridy++;
        mailContent.add(new JLabel("Mail-ContainerClasses (kommasepariert):"), gbc);
        gbc.gridy++;

        String ccValue = settings.mailContainerClasses;
        if (ccValue == null || ccValue.trim().isEmpty()) {
            ccValue = de.bund.zrb.mail.model.MailboxCategory.getMailContainerClassesAsString();
        }
        mailContainerClassesField = new JTextField(ccValue, 30);
        mailContainerClassesField.setToolTipText(
                "Outlook-ContainerClasses, die als E-Mail-Ordner gelten (Standard: IPF.Note,IPF.Imap)");
        mailContent.add(mailContainerClassesField, gbc);
        gbc.gridy++;

        // ─── HTML-Whitelist ───
        gbc.gridy++;
        mailContent.add(new JLabel("─── HTML-Whitelist ───"), gbc);
        gbc.gridy++;

        mailContent.add(new JLabel("Absender, deren Mails immer in HTML geöffnet werden:"), gbc);
        gbc.gridy++;

        DefaultListModel<String> whitelistModel = new DefaultListModel<>();
        if (settings.mailHtmlWhitelistedSenders != null) {
            for (String sender : settings.mailHtmlWhitelistedSenders) {
                whitelistModel.addElement(sender);
            }
        }
        mailWhitelistJList = new JList<>(whitelistModel);
        mailWhitelistJList.setVisibleRowCount(5);
        JScrollPane whitelistScroll = new JScrollPane(mailWhitelistJList);
        mailContent.add(whitelistScroll, gbc);
        gbc.gridy++;

        JPanel wlButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton wlRemoveButton = new JButton("➖ Entfernen");
        wlRemoveButton.addActionListener(e -> {
            int idx = mailWhitelistJList.getSelectedIndex();
            if (idx >= 0) whitelistModel.removeElementAt(idx);
        });
        wlButtonPanel.add(wlRemoveButton);
        JButton wlClearButton = new JButton("🗑 Alle entfernen");
        wlClearButton.addActionListener(e -> whitelistModel.clear());
        wlButtonPanel.add(wlClearButton);
        mailContent.add(wlButtonPanel, gbc);
        gbc.gridy++;

        // Info
        gbc.gridy++;
        JLabel infoMailLabel = new JLabel("<html><small>"
                + "Gib den Ordner an, in dem Outlook die Datendateien (.ost/.pst) speichert.<br><br>"
                + "In Outlook findest du den Pfad unter:<br>"
                + "Datei → Kontoeinstellungen → Kontoeinstellungen → Datendateien<br><br>"
                + "Standard: " + defaultPath + "<br><br>"
                + "<b>Mail-ContainerClasses:</b> Bestimmt, welche Ordnertypen als E-Mails erkannt werden.<br>"
                + "IMAP-Konten verwenden <code>IPF.Imap</code>, Exchange/POP3 verwenden <code>IPF.Note</code>.<br>"
                + "Standard: <code>IPF.Note,IPF.Imap</code><br><br>"
                + "<b>HTML-Whitelist:</b> Mails von diesen Absendern werden automatisch in HTML angezeigt.<br>"
                + "Neue Einträge per Rechtsklick auf eine geöffnete Mail hinzufügen."
                + "</small></html>");
        infoMailLabel.setForeground(Color.GRAY);
        mailContent.add(infoMailLabel, gbc);
    }

    private static void createDebugContent(JPanel debugContent) {
        GridBagConstraints gbc = createDefaultGbc();

        // ── Global log level ──
        debugContent.add(new JLabel("Globales Log-Level:"), gbc);
        gbc.gridy++;

        globalLogLevelCombo = new JComboBox<>(LOG_LEVELS);
        globalLogLevelCombo.setSelectedItem(settings.logLevel != null ? settings.logLevel : "INFO");
        globalLogLevelCombo.setToolTipText(
                "Bestimmt die Mindest-Stufe für alle Log-Ausgaben. "
                + "INFO = normal, FINE = Debug, FINEST = alles.");
        debugContent.add(globalLogLevelCombo, gbc);
        gbc.gridy++;

        debugContent.add(Box.createVerticalStrut(10), gbc);
        gbc.gridy++;

        // ── Per-category overrides ──
        debugContent.add(new JLabel("<html><b>Kategorie-Log-Level</b> (überschreibt global):</html>"), gbc);
        gbc.gridy++;

        debugContent.add(new JLabel("<html><small>Leerlassen = globales Level verwenden</small></html>"), gbc);
        gbc.gridy++;

        String[] catLevelsWithDefault = {"(global)", "OFF", "SEVERE", "WARNING", "INFO", "FINE", "FINER", "FINEST", "ALL"};
        categoryLevelCombos.clear();

        for (String cat : LOG_CATEGORIES) {
            JPanel row = new JPanel(new java.awt.BorderLayout(8, 0));
            row.add(new JLabel(cat + ":"), java.awt.BorderLayout.WEST);
            JComboBox<String> combo = new JComboBox<>(catLevelsWithDefault);
            // Load current value
            String current = settings.logCategoryLevels != null
                    ? settings.logCategoryLevels.get(cat) : null;
            combo.setSelectedItem(current != null && !current.isEmpty() ? current : "(global)");
            row.add(combo, java.awt.BorderLayout.CENTER);
            categoryLevelCombos.put(cat, combo);
            debugContent.add(row, gbc);
            gbc.gridy++;
        }

        debugContent.add(Box.createVerticalStrut(10), gbc);
        gbc.gridy++;

        // Info text
        JLabel infoLabel = new JLabel("<html><small>"
                + "<b>Log-Level-Stufen (aufsteigend detailliert):</b><br>"
                + "OFF → keine Ausgabe<br>"
                + "SEVERE → nur schwere Fehler<br>"
                + "WARNING → Warnungen + Fehler<br>"
                + "INFO → normale Meldungen (Standard)<br>"
                + "FINE → Debug-Ausgaben (z.B. Tool-Aufrufe, Ordnernavigation)<br>"
                + "FINER → detailliertes Debug<br>"
                + "FINEST → maximale Ausgabe (z.B. jede einzelne Mail)<br>"
                + "ALL → alles<br><br>"
                + "Änderungen werden sofort nach Speichern wirksam."
                + "</small></html>");
        infoLabel.setForeground(Color.GRAY);
        debugContent.add(infoLabel, gbc);
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

        ollamaUrlField.setText(settings.aiConfig.getOrDefault("ollama.url", "http://localhost:11434/api/chat"));
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

    /** Collect all values from the static UI fields and persist. */
    private static void applyAllSettings() {
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

            settings.ftpFileType = ComboBoxHelper.getSelectedEnumValue(typeBox, FtpFileType.class);
            settings.ftpTextFormat = ComboBoxHelper.getSelectedEnumValue(formatBox, FtpTextFormat.class);
            settings.ftpFileStructure = ComboBoxHelper.getSelectedEnumValue(structureBox, FtpFileStructure.class);
            settings.ftpTransferMode = ComboBoxHelper.getSelectedEnumValue(modeBox, FtpTransferMode.class);
            settings.enableHexDump = hexDumpBox.isSelected();

            // FTP Timeouts
            settings.ftpConnectTimeoutMs = ((Number) ftpConnectTimeoutSpinner.getValue()).intValue();
            settings.ftpControlTimeoutMs = ((Number) ftpControlTimeoutSpinner.getValue()).intValue();
            settings.ftpDataTimeoutMs = ((Number) ftpDataTimeoutSpinner.getValue()).intValue();

            // FTP Retries
            settings.ftpRetryMaxAttempts = ((Number) ftpRetryMaxAttemptsSpinner.getValue()).intValue();
            settings.ftpRetryBackoffMs = ((Number) ftpRetryBackoffSpinner.getValue()).intValue();
            settings.ftpRetryBackoffStrategy = (String) ftpRetryStrategyCombo.getSelectedItem();
            settings.ftpRetryMaxBackoffMs = ((Number) ftpRetryMaxBackoffSpinner.getValue()).intValue();
            settings.ftpRetryOnTimeout = ftpRetryOnTimeoutBox.isSelected();
            settings.ftpRetryOnTransientIo = ftpRetryOnTransientIoBox.isSelected();
            settings.ftpRetryOnReplyCodes = ftpRetryOnReplyCodesField.getText().trim();

            // FTP Initial HLQ
            settings.ftpUseLoginAsHlq = ftpUseLoginAsHlqBox.isSelected();
            settings.ftpCustomHlq = ftpCustomHlqField.getText().trim();

            // NDV Settings
            settings.ndvPort = ((Number) ndvPortSpinner.getValue()).intValue();
            settings.ndvDefaultLibrary = ndvDefaultLibraryField.getText().trim();
            settings.ndvLibPath = ndvLibPathField.getText().trim();

            // Mail Settings
            settings.mailStorePath = mailPathField.getText().trim();
            settings.mailContainerClasses = mailContainerClassesField.getText().trim();
            de.bund.zrb.mail.model.MailboxCategory.setMailContainerClasses(settings.mailContainerClasses);

            // HTML Whitelist
            settings.mailHtmlWhitelistedSenders = new java.util.HashSet<>();
            if (mailWhitelistJList != null) {
                DefaultListModel<String> wlModel = (DefaultListModel<String>) mailWhitelistJList.getModel();
                for (int i = 0; i < wlModel.size(); i++) {
                    settings.mailHtmlWhitelistedSenders.add(wlModel.getElementAt(i));
                }
            }

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

            // Local History
            settings.historyEnabled = historyEnabledBox.isSelected();
            settings.historyMaxVersionsPerFile = ((Number) historyMaxVersionsSpinner.getValue()).intValue();
            settings.historyMaxAgeDays = ((Number) historyMaxAgeDaysSpinner.getValue()).intValue();

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

            // Debug / Logging
            settings.logLevel = (String) globalLogLevelCombo.getSelectedItem();
            settings.logCategoryLevels.clear();
            for (java.util.Map.Entry<String, JComboBox<String>> entry : categoryLevelCombos.entrySet()) {
                String selected = (String) entry.getValue().getSelectedItem();
                if (selected != null && !"(global)".equals(selected)) {
                    settings.logCategoryLevels.put(entry.getKey(), selected);
                }
            }

            SettingsHelper.save(settings);

            // Apply log levels immediately
            de.bund.zrb.util.AppLogger.applySettings();
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
