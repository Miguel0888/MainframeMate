package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.help.HelpContentProvider;
import de.bund.zrb.ui.lock.LockerStyle;
import de.bund.zrb.ui.settings.FormBuilder;
import de.bund.zrb.util.PasswordMethod;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class GeneralSettingsPanel extends AbstractSettingsPanel {

    private final JComboBox<String> fontCombo;
    private final JComboBox<Integer> fontSizeCombo;
    private final JSpinner marginSpinner;
    private final JCheckBox compareByDefaultBox;
    private final JCheckBox showHelpIconsBox;
    private final JCheckBox enableSound;
    private final JTextField defaultWorkflow;
    private final JSpinner workflowTimeoutSpinner;
    private final JSpinner importDelaySpinner;
    private final JList<String> supportedFileList;
    private final JCheckBox enableLock;
    private final JSpinner lockDelay;
    private final JSpinner lockPre;
    private final JComboBox<LockerStyle> lockStyleBox;
    private final JComboBox<PasswordMethod> passwordMethodBox;
    private final JTextField keepassKpScriptField;
    private final JTextField keepassDatabaseField;
    private final JTextField keepassEntryTitleField;
    private final JPanel keepassConfigPanel;
    private final JCheckBox historyEnabledBox;
    private final JSpinner historyMaxVersionsSpinner;
    private final JSpinner historyMaxAgeDaysSpinner;

    public GeneralSettingsPanel(Component parent) {
        super("general", "Allgemein");
        FormBuilder fb = new FormBuilder();

        fontCombo = new JComboBox<>(new String[]{"Monospaced", "Consolas", "Courier New", "Menlo", "Dialog"});
        fontCombo.setSelectedItem(settings.editorFont);
        fb.addRowHelp("Editor-Schriftart:", fontCombo, HelpContentProvider.HelpTopic.SETTINGS_GENERAL);

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

        fb.addSection("App-Design");

        lockStyleBox = new JComboBox<>(LockerStyle.values());
        lockStyleBox.setSelectedIndex(Math.max(0, Math.min(LockerStyle.values().length - 1, settings.lockStyle)));
        fb.addRow("Design:", lockStyleBox);

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

        fb.addSection("Sicherheit");

        passwordMethodBox = new JComboBox<>(PasswordMethod.values());
        PasswordMethod currentMethod = PasswordMethod.WINDOWS_DPAPI;
        try {
            if (settings.passwordMethod != null && !settings.passwordMethod.isEmpty()) {
                currentMethod = PasswordMethod.valueOf(settings.passwordMethod);
            }
        } catch (IllegalArgumentException ignore) { /* keep default */ }
        passwordMethodBox.setSelectedItem(currentMethod);
        fb.addRow("Passwort-Verschlüsselung:", passwordMethodBox);
        fb.addInfo("<html><small>"
                + "<b>Windows DPAPI (JNA):</b> Verschlüsselung durch Windows — an den Benutzer gebunden. "
                + "Kann auf gehärteten Systemen (Win 11 / AppLocker) blockiert werden.<br>"
                + "<b>Windows DPAPI (PowerShell):</b> Gleiche DPAPI-Sicherheit, aber ohne JNA. "
                + "Benötigt <code>powershell.exe</code> — kann bei restriktiver Execution Policy blockiert sein.<br>"
                + "<b>Java AES-256:</b> Plattformunabhängig, reines Java — Master-Key in <code>~/.mainframemate/.master.key</code>.<br>"
                + "<b>KeePass:</b> Passwörter werden in einer KeePass-Datenbank gespeichert. "
                + "Benötigt <code>KPScript.exe</code> (KeePass 2.x)."
                + "</small></html>");

        // ── KeePass configuration (shown/hidden based on selected method) ──
        keepassKpScriptField = new JTextField(safe(settings.keepassKpScriptPath), 30);
        keepassDatabaseField = new JTextField(safe(settings.keepassDatabasePath), 30);
        keepassEntryTitleField = new JTextField(safe(settings.keepassEntryTitle), 20);

        keepassConfigPanel = new JPanel(new java.awt.GridLayout(0, 1, 2, 2));
        keepassConfigPanel.setBorder(BorderFactory.createTitledBorder("KeePass-Konfiguration"));

        JPanel row1 = new JPanel(new java.awt.BorderLayout(4, 0));
        row1.add(new JLabel("KPScript.exe:"), java.awt.BorderLayout.WEST);
        row1.add(keepassKpScriptField, java.awt.BorderLayout.CENTER);
        JButton browseKpScript = new JButton("…");
        browseKpScript.addActionListener(e -> browseFile(keepassKpScriptField, "KPScript.exe", "exe"));
        row1.add(browseKpScript, java.awt.BorderLayout.EAST);
        keepassConfigPanel.add(row1);

        JPanel row2 = new JPanel(new java.awt.BorderLayout(4, 0));
        row2.add(new JLabel("Datenbank (.kdbx):"), java.awt.BorderLayout.WEST);
        row2.add(keepassDatabaseField, java.awt.BorderLayout.CENTER);
        JButton browseDb = new JButton("…");
        browseDb.addActionListener(e -> browseFile(keepassDatabaseField, "KeePass-Datenbank", "kdbx"));
        row2.add(browseDb, java.awt.BorderLayout.EAST);
        keepassConfigPanel.add(row2);

        JPanel row3 = new JPanel(new java.awt.BorderLayout(4, 0));
        row3.add(new JLabel("Eintragstitel:"), java.awt.BorderLayout.WEST);
        row3.add(keepassEntryTitleField, java.awt.BorderLayout.CENTER);
        keepassConfigPanel.add(row3);

        fb.addWide(keepassConfigPanel);

        // Toggle KeePass config visibility
        keepassConfigPanel.setVisible(currentMethod == PasswordMethod.KEEPASS);
        passwordMethodBox.addActionListener(e ->
                keepassConfigPanel.setVisible(passwordMethodBox.getSelectedItem() == PasswordMethod.KEEPASS));


        fb.addSection("Bildschirmsperre");

        enableLock = new JCheckBox("Bildschirmsperre aktivieren");
        enableLock.setSelected(settings.lockEnabled);
        fb.addWide(enableLock);

        lockDelay = new JSpinner(new SpinnerNumberModel(settings.lockDelay, 0, Integer.MAX_VALUE, 100));
        fb.addRow("Sperre nach (ms):", lockDelay);

        lockPre = new JSpinner(new SpinnerNumberModel(settings.lockPrenotification, 0, Integer.MAX_VALUE, 100));
        fb.addRow("Ankündigung (ms):", lockPre);


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

        installPanel(fb);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.editorFont = (String) fontCombo.getSelectedItem();
        s.editorFontSize = java.util.Optional.ofNullable(fontSizeCombo.getEditor().getItem())
                .map(Object::toString)
                .map(str -> { try { return Integer.parseInt(str); } catch (NumberFormatException e) { return null; } })
                .orElse(12);
        s.marginColumn = (Integer) marginSpinner.getValue();
        s.compareByDefault = compareByDefaultBox.isSelected();
        s.showHelpIcons = showHelpIconsBox.isSelected();
        s.soundEnabled = enableSound.isSelected();
        s.defaultWorkflow = defaultWorkflow.getText();
        s.workflowTimeout = ((Number) workflowTimeoutSpinner.getValue()).longValue();
        s.importDelay = (Integer) importDelaySpinner.getValue();
        DefaultListModel<String> model = (DefaultListModel<String>) supportedFileList.getModel();
        List<String> extensions = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) extensions.add(model.get(i));
        s.supportedFiles = extensions;
        s.lockEnabled = enableLock.isSelected();
        s.lockDelay = (Integer) lockDelay.getValue();
        s.lockPrenotification = (Integer) lockPre.getValue();
        s.lockStyle = lockStyleBox.getSelectedIndex();
        PasswordMethod selectedMethod = (PasswordMethod) passwordMethodBox.getSelectedItem();
        s.passwordMethod = selectedMethod != null ? selectedMethod.name() : PasswordMethod.WINDOWS_DPAPI.name();
        s.keepassKpScriptPath = keepassKpScriptField.getText().trim();
        s.keepassDatabasePath = keepassDatabaseField.getText().trim();
        s.keepassEntryTitle = keepassEntryTitleField.getText().trim();
        s.historyEnabled = historyEnabledBox.isSelected();
        s.historyMaxVersionsPerFile = ((Number) historyMaxVersionsSpinner.getValue()).intValue();
        s.historyMaxAgeDays = ((Number) historyMaxAgeDaysSpinner.getValue()).intValue();
    }

    private void browseFile(JTextField target, String description, String extension) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(description + " auswählen");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                description + " (*." + extension + ")", extension));
        String current = target.getText().trim();
        if (!current.isEmpty()) {
            java.io.File f = new java.io.File(current);
            if (f.getParentFile() != null && f.getParentFile().isDirectory()) {
                fc.setCurrentDirectory(f.getParentFile());
            }
        }
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            target.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}

