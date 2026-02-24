package de.bund.zrb.ui.settings;

import de.bund.zrb.files.ftpconfig.FtpFileStructure;
import de.bund.zrb.files.ftpconfig.FtpFileType;
import de.bund.zrb.files.ftpconfig.FtpTextFormat;
import de.bund.zrb.files.ftpconfig.FtpTransferMode;
import de.bund.zrb.model.*;
import de.bund.zrb.ui.components.ChatMode;
import de.bund.zrb.ui.components.ComboBoxHelper;

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

        JPanel generalContent = createGeneralContent(parent);
        JPanel colorContent = createColorContent(parent);
        JPanel transformContent = createTransformContent();
        JPanel connectContent = createConnectContent();
        JPanel ndvContent = createNdvContent();
        JPanel aiContent = createAiContent();
        ragSettingsPanel = new RagSettingsPanel();
        JPanel proxyContent = createProxyContent(parent);
        JPanel mailContent = createMailContent();
        JPanel debugContent = createDebugContent();

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



    private static JPanel createGeneralContent(Component parent) {
        FormBuilder fb = new FormBuilder();

        fontCombo = new JComboBox<>(new String[]{"Monospaced", "Consolas", "Courier New", "Menlo", "Dialog"});
        fontCombo.setSelectedItem(settings.editorFont);
        fb.addRow("Editor-Schriftart:", fontCombo);

        fontSizeCombo = new JComboBox<>(new Integer[]{10, 11, 12, 13, 14, 16, 18, 20, 24, 28, 32, 36, 48, 72});
        fontSizeCombo.setEditable(true);
        fontSizeCombo.setSelectedItem(settings.editorFontSize);
        fb.addRow("Editor-Schriftgröße:", fontSizeCombo);

        marginSpinner = new JSpinner(new SpinnerNumberModel(Math.max(0, settings.marginColumn), 0, 200, 1));
        fb.addRow("Markierung bei Spalte (0=aus):", marginSpinner);

        fb.addSeparator();

        compareByDefaultBox = new JCheckBox("Vergleich automatisch einblenden");
        compareByDefaultBox.setSelected(settings.compareByDefault);
        fb.addWide(compareByDefaultBox);

        showHelpIconsBox = new JCheckBox("Hilfe-Icons anzeigen");
        showHelpIconsBox.setSelected(settings.showHelpIcons);
        showHelpIconsBox.setToolTipText("Deaktivieren für erfahrene Benutzer");
        fb.addWide(showHelpIconsBox);

        enableSound = new JCheckBox("Sounds abspielen");
        enableSound.setSelected(settings.soundEnabled);
        fb.addWide(enableSound);

        fb.addSection("Workflow");

        defaultWorkflow = new JTextField(settings.defaultWorkflow);
        fb.addRow("Default Workflow:", defaultWorkflow);

        workflowTimeoutSpinner = new JSpinner(new SpinnerNumberModel(settings.workflowTimeout, 100, 300_000, 500));
        fb.addRow("Workflow Timeout (ms):", workflowTimeoutSpinner);

        importDelaySpinner = new JSpinner(new SpinnerNumberModel(settings.importDelay, 0, 60, 1));
        fb.addRow("Import-Verzögerung (s):", importDelaySpinner);

        fb.addSection("Dateiendungen");

        DefaultListModel<String> fileListModel = new DefaultListModel<>();
        for (String ext : settings.supportedFiles) fileListModel.addElement(ext);
        supportedFileList = new JList<>(fileListModel);
        supportedFileList.setVisibleRowCount(4);
        fb.addWide(new JScrollPane(supportedFileList));

        JButton addExtButton = new JButton("➕ Hinzufügen");
        addExtButton.addActionListener(e -> {
            String ext = JOptionPane.showInputDialog(parent, "Neue Dateiendung eingeben (mit Punkt):", ".xyz");
            if (ext != null && !ext.trim().isEmpty()) fileListModel.addElement(ext.trim());
        });
        JButton removeExtButton = new JButton("➖ Entfernen");
        removeExtButton.addActionListener(e -> {
            int index = supportedFileList.getSelectedIndex();
            if (index >= 0) fileListModel.remove(index);
        });
        fb.addButtons(addExtButton, removeExtButton);

        fb.addSection("Bildschirmsperre");

        enableLock = new JCheckBox("Bildschirmsperre aktivieren");
        enableLock.setSelected(settings.lockEnabled);
        fb.addWide(enableLock);

        lockDelay = new JSpinner(new SpinnerNumberModel(settings.lockDelay, 0, Integer.MAX_VALUE, 100));
        fb.addRow("Sperre nach (ms):", lockDelay);

        lockPre = new JSpinner(new SpinnerNumberModel(settings.lockPrenotification, 0, Integer.MAX_VALUE, 100));
        fb.addRow("Ankündigung (ms):", lockPre);

        lockStyleBox = new JComboBox<>(LockerStyle.values());
        lockStyleBox.setSelectedIndex(Math.max(0, Math.min(LockerStyle.values().length - 1, settings.lockStyle)));
        fb.addRow("Design:", lockStyleBox);

        fb.addSection("Lokale Historie");

        historyEnabledBox = new JCheckBox("Lokale Historie aktivieren");
        historyEnabledBox.setSelected(settings.historyEnabled);
        fb.addWide(historyEnabledBox);

        historyMaxVersionsSpinner = new JSpinner(new SpinnerNumberModel(settings.historyMaxVersionsPerFile, 1, 10000, 10));
        fb.addRow("Max. Versionen pro Datei:", historyMaxVersionsSpinner);

        historyMaxAgeDaysSpinner = new JSpinner(new SpinnerNumberModel(settings.historyMaxAgeDays, 1, 3650, 10));
        fb.addRow("Max. Alter (Tage):", historyMaxAgeDaysSpinner);

        JButton pruneNowButton = new JButton("🧹 Bereinigen");
        pruneNowButton.addActionListener(e -> {
            de.bund.zrb.history.LocalHistoryService.getInstance().prune(
                    ((Number) historyMaxVersionsSpinner.getValue()).intValue(),
                    ((Number) historyMaxAgeDaysSpinner.getValue()).intValue());
            JOptionPane.showMessageDialog(parent, "Bereinigung abgeschlossen.", "Lokale Historie", JOptionPane.INFORMATION_MESSAGE);
        });
        JButton clearAllHistoryButton = new JButton("🗑️ Alles löschen");
        clearAllHistoryButton.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(parent, "Gesamte lokale Historie löschen?",
                    "Historie löschen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
                de.bund.zrb.history.LocalHistoryService svc = de.bund.zrb.history.LocalHistoryService.getInstance();
                svc.clearBackend(de.bund.zrb.ui.VirtualBackendType.LOCAL);
                svc.clearBackend(de.bund.zrb.ui.VirtualBackendType.FTP);
                svc.clearBackend(de.bund.zrb.ui.VirtualBackendType.NDV);
                JOptionPane.showMessageDialog(parent, "Gesamte Historie gelöscht.", "Lokale Historie", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        fb.addButtons(pruneNowButton, clearAllHistoryButton);

        return fb.getPanel();
    }

    private static JPanel createTransformContent() {
        FormBuilder fb = new FormBuilder();

        encodingCombo = new JComboBox<>();
        SettingsHelper.SUPPORTED_ENCODINGS.forEach(encodingCombo::addItem);
        encodingCombo.setSelectedItem(settings.encoding != null ? settings.encoding : "windows-1252");
        fb.addRow("Zeichenkodierung:", encodingCombo);

        lineEndingBox = LineEndingOption.createLineEndingComboBox(settings.lineEnding);
        fb.addRow("Zeilenumbruch des Servers:", lineEndingBox);

        stripFinalNewlineBox = new JCheckBox("Letzten Zeilenumbruch ausblenden");
        stripFinalNewlineBox.setSelected(settings.removeFinalNewline);
        fb.addWide(stripFinalNewlineBox);

        endMarkerBox = FileEndingOption.createEndMarkerComboBox(settings.fileEndMarker);
        fb.addRow("Datei-Ende-Kennung:", endMarkerBox);

        paddingBox = PaddingOption.createPaddingComboBox(settings.padding);
        fb.addRow("Padding Byte:", paddingBox);

        return fb.getPanel();
    }

    private static JPanel createConnectContent() {
        FormBuilder fb = new FormBuilder();

        typeBox = ComboBoxHelper.createComboBoxWithNullOption(FtpFileType.class, settings.ftpFileType, "Standard");
        fb.addRow("FTP Datei-Typ (TYPE):", typeBox);

        formatBox = ComboBoxHelper.createComboBoxWithNullOption(FtpTextFormat.class, settings.ftpTextFormat, "Standard");
        fb.addRow("FTP Text-Format:", formatBox);

        structureBox = ComboBoxHelper.createComboBoxWithNullOption(FtpFileStructure.class, settings.ftpFileStructure, "Automatisch");
        fb.addRow("FTP Dateistruktur:", structureBox);

        modeBox = ComboBoxHelper.createComboBoxWithNullOption(FtpTransferMode.class, settings.ftpTransferMode, "Standard");
        fb.addRow("FTP Übertragungsmodus:", modeBox);

        hexDumpBox = new JCheckBox("Hexdump in Konsole anzeigen");
        hexDumpBox.setSelected(settings.enableHexDump);
        fb.addWide(hexDumpBox);

        fb.addSection("FTP Timeouts (0 = deaktiviert)");

        ftpConnectTimeoutSpinner = new JSpinner(new SpinnerNumberModel(settings.ftpConnectTimeoutMs, 0, 300_000, 1000));
        fb.addRow("Connect Timeout (ms):", ftpConnectTimeoutSpinner);

        ftpControlTimeoutSpinner = new JSpinner(new SpinnerNumberModel(settings.ftpControlTimeoutMs, 0, 300_000, 1000));
        fb.addRow("Control Timeout (ms):", ftpControlTimeoutSpinner);

        ftpDataTimeoutSpinner = new JSpinner(new SpinnerNumberModel(settings.ftpDataTimeoutMs, 0, 300_000, 1000));
        fb.addRow("Data Timeout (ms):", ftpDataTimeoutSpinner);

        fb.addSection("FTP Retries");

        ftpRetryMaxAttemptsSpinner = new JSpinner(new SpinnerNumberModel(settings.ftpRetryMaxAttempts, 1, 10, 1));
        fb.addRow("Maximale Versuche:", ftpRetryMaxAttemptsSpinner);

        ftpRetryBackoffSpinner = new JSpinner(new SpinnerNumberModel(settings.ftpRetryBackoffMs, 0, 60_000, 500));
        fb.addRow("Wartezeit (ms):", ftpRetryBackoffSpinner);

        ftpRetryStrategyCombo = new JComboBox<>(new String[]{"FIXED", "EXPONENTIAL"});
        ftpRetryStrategyCombo.setSelectedItem(settings.ftpRetryBackoffStrategy);
        fb.addRow("Backoff-Strategie:", ftpRetryStrategyCombo);

        ftpRetryMaxBackoffSpinner = new JSpinner(new SpinnerNumberModel(settings.ftpRetryMaxBackoffMs, 0, 300_000, 1000));
        fb.addRow("Max Backoff (ms):", ftpRetryMaxBackoffSpinner);

        ftpRetryOnTimeoutBox = new JCheckBox("Retry bei Timeout-Fehlern");
        ftpRetryOnTimeoutBox.setSelected(settings.ftpRetryOnTimeout);
        fb.addWide(ftpRetryOnTimeoutBox);

        ftpRetryOnTransientIoBox = new JCheckBox("Retry bei IO-Fehlern (Connection Reset etc.)");
        ftpRetryOnTransientIoBox.setSelected(settings.ftpRetryOnTransientIo);
        fb.addWide(ftpRetryOnTransientIoBox);

        ftpRetryOnReplyCodesField = new JTextField(settings.ftpRetryOnReplyCodes, 20);
        ftpRetryOnReplyCodesField.setToolTipText("z.B. 421,425,426");
        fb.addRow("Reply Codes (kommasep.):", ftpRetryOnReplyCodesField);

        fb.addSection("Initial HLQ (Startverzeichnis)");

        ftpUseLoginAsHlqBox = new JCheckBox("Login-Namen als HLQ verwenden");
        ftpUseLoginAsHlqBox.setSelected(settings.ftpUseLoginAsHlq);
        fb.addWide(ftpUseLoginAsHlqBox);

        ftpCustomHlqField = new JTextField(settings.ftpCustomHlq != null ? settings.ftpCustomHlq : "", 20);
        ftpCustomHlqField.setToolTipText("z.B. USERID oder PROD.DATA");
        ftpCustomHlqField.setEnabled(!settings.ftpUseLoginAsHlq);
        fb.addRow("Benutzerdefinierter HLQ:", ftpCustomHlqField);

        ftpUseLoginAsHlqBox.addActionListener(e -> ftpCustomHlqField.setEnabled(!ftpUseLoginAsHlqBox.isSelected()));

        return fb.getPanel();
    }

    private static JPanel createNdvContent() {
        FormBuilder fb = new FormBuilder();

        ndvPortSpinner = new JSpinner(new SpinnerNumberModel(settings.ndvPort, 1, 65535, 1));
        ndvPortSpinner.setToolTipText("Standard: 8011");
        fb.addRow("NDV Port:", ndvPortSpinner);

        ndvDefaultLibraryField = new JTextField(settings.ndvDefaultLibrary != null ? settings.ndvDefaultLibrary : "", 20);
        ndvDefaultLibraryField.setToolTipText("z.B. ABAK-T");
        fb.addRow("Default-Bibliothek:", ndvDefaultLibraryField);

        fb.addSection("NDV-Bibliotheken (JARs)");

        String defaultLibDir = de.bund.zrb.ndv.NdvLibLoader.getLibDir().getAbsolutePath();
        ndvLibPathField = new JTextField(settings.ndvLibPath != null && !settings.ndvLibPath.isEmpty() ? settings.ndvLibPath : "", 24);
        ndvLibPathField.setToolTipText("Standard: " + defaultLibDir);
        JButton openLibFolderButton = new JButton("📂 Öffnen");
        openLibFolderButton.addActionListener(e -> {
            String customPath = ndvLibPathField.getText().trim();
            java.io.File libDir = !customPath.isEmpty() ? new java.io.File(customPath) : de.bund.zrb.ndv.NdvLibLoader.getLibDir();
            if (!libDir.exists()) libDir.mkdirs();
            try { java.awt.Desktop.getDesktop().open(libDir); }
            catch (Exception ex) { JOptionPane.showMessageDialog(null, "Fehler: " + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE); }
        });
        fb.addRowWithButton("Pfad zu NDV-JARs:", ndvLibPathField, openLibFolderButton);

        boolean available = de.bund.zrb.ndv.NdvLibLoader.isAvailable();
        JLabel statusLabel = new JLabel(available ? "✅ NDV-Bibliotheken gefunden" : "⚠ Nicht gefunden in: " + defaultLibDir);
        statusLabel.setForeground(available ? new Color(0, 128, 0) : new Color(200, 100, 0));
        fb.addWide(statusLabel);

        fb.addInfo("Benötigte JARs: ndvserveraccess_*.jar, auxiliary_*.jar (NaturalONE)");

        return fb.getPanel();
    }

    private static JPanel createMailContent() {
        FormBuilder fb = new FormBuilder();

        String defaultPath = de.bund.zrb.ui.commands.ConnectMailMenuCommand.getDefaultOutlookPath();
        String currentValue = settings.mailStorePath;
        if (currentValue == null || currentValue.trim().isEmpty()) currentValue = defaultPath;
        mailPathField = new JTextField(currentValue, 30);
        JButton browseButton = new JButton("📂");
        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(mailPathField.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                mailPathField.setText(chooser.getSelectedFile().getAbsolutePath());
        });
        fb.addRowWithButton("Mail-Speicherort:", mailPathField, browseButton);

        String ccValue = settings.mailContainerClasses;
        if (ccValue == null || ccValue.trim().isEmpty())
            ccValue = de.bund.zrb.mail.model.MailboxCategory.getMailContainerClassesAsString();
        mailContainerClassesField = new JTextField(ccValue, 30);
        mailContainerClassesField.setToolTipText("Standard: IPF.Note,IPF.Imap");
        fb.addRow("ContainerClasses:", mailContainerClassesField);

        fb.addSection("HTML-Whitelist");
        fb.addInfo("Absender, deren Mails immer in HTML geöffnet werden.");

        DefaultListModel<String> whitelistModel = new DefaultListModel<>();
        if (settings.mailHtmlWhitelistedSenders != null)
            for (String sender : settings.mailHtmlWhitelistedSenders) whitelistModel.addElement(sender);
        mailWhitelistJList = new JList<>(whitelistModel);
        mailWhitelistJList.setVisibleRowCount(5);
        fb.addWide(new JScrollPane(mailWhitelistJList));

        JButton wlRemoveButton = new JButton("➖ Entfernen");
        wlRemoveButton.addActionListener(e -> { int idx = mailWhitelistJList.getSelectedIndex(); if (idx >= 0) whitelistModel.removeElementAt(idx); });
        JButton wlClearButton = new JButton("🗑 Alle entfernen");
        wlClearButton.addActionListener(e -> whitelistModel.clear());
        fb.addButtons(wlRemoveButton, wlClearButton);

        return fb.getPanel();
    }

    private static JPanel createDebugContent() {
        FormBuilder fb = new FormBuilder();

        globalLogLevelCombo = new JComboBox<>(LOG_LEVELS);
        globalLogLevelCombo.setSelectedItem(settings.logLevel != null ? settings.logLevel : "INFO");
        globalLogLevelCombo.setToolTipText("INFO = normal, FINE = Debug, FINEST = alles");
        fb.addRow("Globales Log-Level:", globalLogLevelCombo);

        fb.addSection("Kategorie-Log-Level");
        fb.addInfo("Überschreibt das globale Level für einzelne Kategorien.");

        String[] catLevelsWithDefault = {"(global)", "OFF", "SEVERE", "WARNING", "INFO", "FINE", "FINER", "FINEST", "ALL"};
        categoryLevelCombos.clear();
        for (String cat : LOG_CATEGORIES) {
            JComboBox<String> combo = new JComboBox<>(catLevelsWithDefault);
            String current = settings.logCategoryLevels != null ? settings.logCategoryLevels.get(cat) : null;
            combo.setSelectedItem(current != null && !current.isEmpty() ? current : "(global)");
            categoryLevelCombos.put(cat, combo);
            fb.addRow(cat + ":", combo);
        }

        return fb.getPanel();
    }

    private static JPanel createColorContent(Component parent) {
        FormBuilder fb = new FormBuilder();

        colorModel = new ColorOverrideTableModel(settings.fieldColorOverrides);
        colorTable = new JTable(colorModel);
        colorTable.getColumnModel().getColumn(1).setCellEditor(new ColorCellEditor());
        colorTable.getColumnModel().getColumn(1).setCellRenderer((table, value, isSelected, hasFocus, row, col) -> {
            JLabel label = new JLabel(value != null ? value.toString() : "");
            label.setOpaque(true);
            label.setBackground(parseHexColor(String.valueOf(value), Color.WHITE));
            return label;
        });
        colorTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) {
                    int row = colorTable.rowAtPoint(e.getPoint());
                    int column = colorTable.columnAtPoint(e.getPoint());
                    if (column == 1) colorTable.editCellAt(row, column);
                }
            }
        });
        colorTable.setFillsViewportHeight(true);
        colorTable.setPreferredScrollableViewportSize(new Dimension(300, 150));
        fb.addWideGrow(new JScrollPane(colorTable));

        addRowButton = new JButton("➕ Hinzufügen");
        addRowButton.addActionListener(e -> {
            String key = JOptionPane.showInputDialog(parent, "Feldname eingeben:");
            if (key != null && !key.trim().isEmpty()) colorModel.addEntry(key.trim().toUpperCase(), "#00AA00");
        });
        removeRowButton = new JButton("➖ Entfernen");
        removeRowButton.addActionListener(e -> { int s = colorTable.getSelectedRow(); if (s >= 0) colorModel.removeEntry(s); });
        fb.addButtons(addRowButton, removeRowButton);

        return fb.getPanel();
    }

    private static JPanel createAiContent() {
        FormBuilder fb = new FormBuilder();

        aiModeCombo = new JComboBox<>(ChatMode.values());
        aiModeCombo.setSelectedItem(ChatMode.AGENT);
        fb.addRow("Mode für Tool-Contract:", aiModeCombo);

        aiToolPrefix = new JTextArea(3, 30);
        aiToolPrefix.setLineWrap(true); aiToolPrefix.setWrapStyleWord(true);
        fb.addRow("KI-Prefix:", new JScrollPane(aiToolPrefix));

        aiToolPostfix = new JTextArea(2, 30);
        aiToolPostfix.setLineWrap(true); aiToolPostfix.setWrapStyleWord(true);
        fb.addRow("KI-Postfix:", new JScrollPane(aiToolPostfix));

        aiResetToolContractButton = new JButton("Auf Default zurücksetzen");
        aiResetToolContractButton.addActionListener(e -> resetModeToolContractToDefault((ChatMode) aiModeCombo.getSelectedItem()));
        fb.addButtons(aiResetToolContractButton);

        aiLanguageCombo = new JComboBox<>(new String[]{"Deutsch (Standard)", "Keine Vorgabe", "Englisch"});
        String languageSetting = settings.aiConfig.getOrDefault("assistant.language", "de").trim().toLowerCase();
        if ("".equals(languageSetting) || "none".equals(languageSetting)) aiLanguageCombo.setSelectedItem("Keine Vorgabe");
        else if ("en".equals(languageSetting) || "english".equals(languageSetting)) aiLanguageCombo.setSelectedItem("Englisch");
        else aiLanguageCombo.setSelectedItem("Deutsch (Standard)");
        fb.addRow("Antwortsprache:", aiLanguageCombo);

        final ChatMode[] previousMode = {(ChatMode) aiModeCombo.getSelectedItem()};
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

        fb.addSection("KI-Editor");

        aiEditorFontCombo = new JComboBox<>(new String[]{"Monospaced", "Consolas", "Courier New", "Dialog", "Menlo"});
        aiEditorFontCombo.setSelectedItem(settings.aiConfig.getOrDefault("editor.font", "Monospaced"));
        fb.addRow("Schriftart:", aiEditorFontCombo);

        aiEditorFontSizeCombo = new JComboBox<>(new String[]{"10","11","12","13","14","16","18","20","24","28","32"});
        aiEditorFontSizeCombo.setEditable(true);
        aiEditorFontSizeCombo.setSelectedItem(settings.aiConfig.getOrDefault("editor.fontSize", "12"));
        fb.addRow("Schriftgröße:", aiEditorFontSizeCombo);

        aiEditorHeightSpinner = new JSpinner(new SpinnerNumberModel(Integer.parseInt(settings.aiConfig.getOrDefault("editor.lines", "3")), 1, 1000, 1));
        fb.addRow("Editor-Höhe (Zeilen):", aiEditorHeightSpinner);

        wrapJsonBox = new JCheckBox("JSON als Markdown-Codeblock einrahmen");
        wrapJsonBox.setSelected(Boolean.parseBoolean(settings.aiConfig.getOrDefault("wrapjson", "true")));
        fb.addWide(wrapJsonBox);

        prettyJsonBox = new JCheckBox("JSON schön formatieren (Pretty-Print)");
        prettyJsonBox.setSelected(Boolean.parseBoolean(settings.aiConfig.getOrDefault("prettyjson", "true")));
        fb.addWide(prettyJsonBox);

        fb.addSection("KI-Provider");

        providerCombo = new JComboBox<>();
        providerCombo.addItem(AiProvider.DISABLED); providerCombo.addItem(AiProvider.OLLAMA);
        providerCombo.addItem(AiProvider.CLOUD); providerCombo.addItem(AiProvider.LOCAL_AI);
        providerCombo.addItem(AiProvider.LLAMA_CPP_SERVER);
        fb.addRow("Provider:", providerCombo);

        JPanel providerOptionsPanel = new JPanel(new CardLayout());
        providerOptionsPanel.add(new JPanel(), AiProvider.DISABLED.name());

        FormBuilder fbOllama = new FormBuilder();
        ollamaUrlField = new JTextField(30); fbOllama.addRow("URL:", ollamaUrlField);
        ollamaModelField = new JTextField(20); fbOllama.addRow("Modellname:", ollamaModelField);
        ollamaKeepAliveField = new JTextField(20); fbOllama.addRow("Beibehalten für:", ollamaKeepAliveField);
        providerOptionsPanel.add(fbOllama.getPanel(), AiProvider.OLLAMA.name());

        FormBuilder fbCloud = new FormBuilder();
        cloudProviderField = new JComboBox<>(new String[]{"OPENAI","CLAUDE","PERPLEXITY","GROK","GEMINI"});
        fbCloud.addRow("Cloud-Anbieter:", cloudProviderField);
        cloudApiKeyField = new JTextField(30); fbCloud.addRow("API Key:", cloudApiKeyField);
        cloudApiUrlField = new JTextField(30); fbCloud.addRow("API URL:", cloudApiUrlField);
        cloudModelField = new JTextField(30); fbCloud.addRow("Modell:", cloudModelField);
        cloudAuthHeaderField = new JTextField(30); fbCloud.addRow("Auth Header:", cloudAuthHeaderField);
        cloudAuthPrefixField = new JTextField(30); fbCloud.addRow("Auth Prefix:", cloudAuthPrefixField);
        cloudApiVersionField = new JTextField(30); fbCloud.addRow("Anthropic-Version:", cloudApiVersionField);
        cloudOrgField = new JTextField(30); fbCloud.addRow("Organisation:", cloudOrgField);
        cloudProjectField = new JTextField(30); fbCloud.addRow("Projekt:", cloudProjectField);
        JButton cloudResetButton = new JButton("Defaults zurücksetzen");
        fbCloud.addButtons(cloudResetButton);
        providerOptionsPanel.add(fbCloud.getPanel(), AiProvider.CLOUD.name());

        FormBuilder fbLocal = new FormBuilder();
        fbLocal.addInfo("Konfiguration für LocalAI folgt.");
        providerOptionsPanel.add(fbLocal.getPanel(), AiProvider.LOCAL_AI.name());

        FormBuilder fbLlama = new FormBuilder();
        llamaStreamingBox = new JCheckBox("Streaming aktiviert");
        llamaStreamingBox.setSelected(Boolean.parseBoolean(settings.aiConfig.getOrDefault("llama.streaming", "true")));
        fbLlama.addWide(llamaStreamingBox);
        llamaEnabledBox = new JCheckBox("llama.cpp Server beim Start starten");
        llamaEnabledBox.setSelected(Boolean.parseBoolean(settings.aiConfig.getOrDefault("llama.enabled", "false")));
        fbLlama.addWide(llamaEnabledBox);
        llamaBinaryField = new JTextField(settings.aiConfig.getOrDefault("llama.binary", "C:/llamacpp/llama-server"), 30);
        JButton extractDriverButton = new JButton("🔄 Entpacken");
        extractDriverButton.addActionListener(e -> {
            String path = llamaBinaryField.getText().trim();
            if (path.isEmpty()) { JOptionPane.showMessageDialog(null, "Bitte Zielpfad angeben.", "Pfad fehlt", JOptionPane.WARNING_MESSAGE); return; }
            String inputHash = (String) JOptionPane.showInputDialog(null, "SHA-256-Hash:", "Hashprüfung", JOptionPane.PLAIN_MESSAGE, null, null, ExecutableLauncher.getHash());
            if (inputHash == null || inputHash.trim().isEmpty()) return;
            try { new ExecutableLauncher().extractTo(new File(path), inputHash.trim());
                JOptionPane.showMessageDialog(null, "Binary extrahiert:\n" + path, "Erfolg", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) { JOptionPane.showMessageDialog(null, "Fehler:\n" + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE); }
        });
        fbLlama.addRowWithButton("Binary-Pfad:", llamaBinaryField, extractDriverButton);
        llamaModelField = new JTextField(settings.aiConfig.getOrDefault("llama.model", "models/mistral.gguf"), 30);
        fbLlama.addRow("Modellpfad (.gguf):", llamaModelField);
        llamaPortSpinner = new JSpinner(new SpinnerNumberModel(Integer.parseInt(settings.aiConfig.getOrDefault("llama.port", "8080")), 1024, 65535, 1));
        fbLlama.addRow("Port:", llamaPortSpinner);
        llamaThreadsSpinner = new JSpinner(new SpinnerNumberModel(Integer.parseInt(settings.aiConfig.getOrDefault("llama.threads", "4")), 1, 64, 1));
        fbLlama.addRow("Threads:", llamaThreadsSpinner);
        llamaContextSpinner = new JSpinner(new SpinnerNumberModel(Integer.parseInt(settings.aiConfig.getOrDefault("llama.context", "2048")), 512, 8192, 64));
        fbLlama.addRow("Kontextgröße:", llamaContextSpinner);
        llamaTempField = new JTextField(settings.aiConfig.getOrDefault("llama.temp", "0.7"), 5);
        fbLlama.addRow("Temperatur:", llamaTempField);
        providerOptionsPanel.add(fbLlama.getPanel(), AiProvider.LLAMA_CPP_SERVER.name());

        List<Component> llamaConfigFields = Arrays.asList(llamaBinaryField, llamaModelField, llamaPortSpinner, llamaThreadsSpinner, llamaContextSpinner, llamaTempField);
        llamaEnabledBox.addActionListener(e -> { boolean en = llamaEnabledBox.isSelected(); for (Component c : llamaConfigFields) c.setEnabled(en); });
        for (Component c : llamaConfigFields) c.setEnabled(llamaEnabledBox.isSelected());

        fb.addWide(providerOptionsPanel);

        String providerName = settings.aiConfig.getOrDefault("provider", "DISABLED");
        AiProvider selectedProvider;
        try { selectedProvider = AiProvider.valueOf(providerName); } catch (IllegalArgumentException ex) { selectedProvider = AiProvider.DISABLED; }
        providerCombo.setSelectedItem(selectedProvider);

        ollamaUrlField.setText(settings.aiConfig.getOrDefault("ollama.url", "http://localhost:11434/api/chat"));
        ollamaModelField.setText(settings.aiConfig.getOrDefault("ollama.model", "custom-modell"));
        ollamaKeepAliveField.setText(settings.aiConfig.getOrDefault("ollama.keepalive", "10m"));

        String initialCloudVendor = settings.aiConfig.getOrDefault("cloud.vendor", "OPENAI");
        if ("CLOUD".equalsIgnoreCase(initialCloudVendor)) initialCloudVendor = "CLAUDE";
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
            applyCloudVendorDefaults(true); cloudApiKeyField.setText("");
            if (!"OPENAI".equals(cloudProviderField.getSelectedItem())) { cloudOrgField.setText(""); cloudProjectField.setText(""); }
        });

        providerCombo.addActionListener(e -> ((CardLayout) providerOptionsPanel.getLayout()).show(providerOptionsPanel, ((AiProvider) providerCombo.getSelectedItem()).name()));
        ((CardLayout) providerOptionsPanel.getLayout()).show(providerOptionsPanel, selectedProvider.name());

        return fb.getPanel();
    }

    private static JPanel createProxyContent(Component parent) {
        FormBuilder fb = new FormBuilder();

        proxyEnabledBox = new JCheckBox("Proxy aktivieren");
        proxyEnabledBox.setSelected(settings.proxyEnabled);
        fb.addWide(proxyEnabledBox);

        proxyModeBox = new JComboBox<>(new String[]{"WINDOWS_PAC", "MANUAL"});
        proxyModeBox.setSelectedItem(settings.proxyMode == null ? "WINDOWS_PAC" : settings.proxyMode);
        fb.addRow("Proxy-Modus:", proxyModeBox);

        proxyHostField = new JTextField(settings.proxyHost == null ? "" : settings.proxyHost, 24);
        fb.addRow("Proxy Host:", proxyHostField);

        proxyPortSpinner = new JSpinner(new SpinnerNumberModel(settings.proxyPort, 0, 65535, 1));
        fb.addRow("Proxy Port:", proxyPortSpinner);

        proxyNoProxyLocalBox = new JCheckBox("Lokale Ziele niemals über Proxy");
        proxyNoProxyLocalBox.setSelected(settings.proxyNoProxyLocal);
        fb.addWide(proxyNoProxyLocalBox);

        fb.addSection("PAC/WPAD Script");

        proxyPacScriptArea = new RSyntaxTextArea(12, 60);
        proxyPacScriptArea.setSyntaxEditingStyle("text/powershell");
        proxyPacScriptArea.setCodeFoldingEnabled(true);
        proxyPacScriptArea.setText(settings.proxyPacScript == null ? ProxyDefaults.DEFAULT_PAC_SCRIPT : settings.proxyPacScript);
        fb.addWideGrow(new RTextScrollPane(proxyPacScriptArea));

        proxyTestUrlField = new JTextField(settings.proxyTestUrl == null ? ProxyDefaults.DEFAULT_TEST_URL : settings.proxyTestUrl, 30);
        proxyTestButton = new JButton("Testen");
        proxyTestButton.addActionListener(e -> {
            String testUrl = proxyTestUrlField.getText().trim();
            if (testUrl.isEmpty()) { JOptionPane.showMessageDialog(parent, "Bitte Test-URL eingeben.", "Proxy Test", JOptionPane.WARNING_MESSAGE); return; }
            ProxyResolver.ProxyResolution result = ProxyResolver.testPacScript(testUrl, proxyPacScriptArea.getText());
            String msg = result.isDirect() ? "DIRECT (" + result.getReason() + ")" : result.getProxy().address() + " (" + result.getReason() + ")";
            JOptionPane.showMessageDialog(parent, msg, "Proxy Test", JOptionPane.INFORMATION_MESSAGE);
        });
        fb.addRowWithButton("Test-URL:", proxyTestUrlField, proxyTestButton);

        return fb.getPanel();
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
        if (clearOptionalFields && !isOpenAi) { cloudOrgField.setText(""); cloudProjectField.setText(""); }
    }

    private static String cloudDefaultForVendor(String vendor, String key) {
        switch (vendor) {
            case "PERPLEXITY": if ("url".equals(key)) return "https://api.perplexity.ai/chat/completions"; if ("model".equals(key)) return "sonar"; break;
            case "GROK": if ("url".equals(key)) return "https://api.x.ai/v1/chat/completions"; if ("model".equals(key)) return "grok-2-latest"; break;
            case "GEMINI": if ("url".equals(key)) return "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"; if ("model".equals(key)) return "gemini-2.0-flash"; break;
            case "CLAUDE":
                if ("url".equals(key)) return "https://api.anthropic.com/v1/messages";
                if ("model".equals(key)) return "claude-3-5-sonnet-latest";
                if ("authHeader".equals(key)) return "x-api-key";
                if ("authPrefix".equals(key)) return "";
                if ("anthropicVersion".equals(key)) return "2023-06-01";
                break;
            case "OPENAI": default:
                if ("url".equals(key)) return "https://api.openai.com/v1/chat/completions";
                if ("model".equals(key)) return "gpt-4o-mini";
                break;
        }
        if ("authHeader".equals(key)) return "Authorization";
        if ("authPrefix".equals(key)) return "Bearer";
        if ("anthropicVersion".equals(key)) return "2023-06-01";
        return "";
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
